package auth

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/getsillage/sillage/store"
)

const (
	PasswordAlgorithmName    = passwordAlgorithm
	TrustedProxyMarkerHeader = "X-Sillage-Trusted-Proxy"
	refreshCookieName        = "sillage_refresh"
	accessCookieName         = "sillage_access"
	accessTokenTTL           = 15 * time.Minute
	refreshTokenTTL          = 30 * 24 * time.Hour
	loginFailureWindow       = 15 * time.Minute
	maxLoginFailures         = 10
)

type Service struct {
	store         *store.Store
	sessionSecret []byte
}

type TokenPair struct {
	AccessToken  string
	RefreshToken string
	ExpiresAt    time.Time
}

type AccessClaims struct {
	AccountID string `json:"account_id"`
	ExpiresAt int64  `json:"expires_at"`
	IssuedAt  int64  `json:"issued_at"`
}

func NewService(store *store.Store, sessionSecret string) *Service {
	return &Service{store: store, sessionSecret: []byte(sessionSecret)}
}

func (s *Service) HasAccount(ctx context.Context) (bool, error) {
	return s.store.HasAccount(ctx)
}

func (s *Service) Initialize(ctx context.Context, username, displayName, password string, r *http.Request) (*store.Account, *TokenPair, error) {
	passwordHash, err := HashPassword(password)
	if err != nil {
		return nil, nil, err
	}
	account, err := s.store.CreateAccount(ctx, &store.CreateAccount{
		Username:          normalizeUsername(username),
		DisplayName:       strings.TrimSpace(displayName),
		PasswordHash:      passwordHash,
		PasswordAlgorithm: PasswordAlgorithmName,
	})
	if err != nil {
		return nil, nil, err
	}
	tokens, err := s.createTokenPair(ctx, account.ID, r)
	if err != nil {
		return nil, nil, err
	}
	return account, tokens, nil
}

func (s *Service) SignIn(ctx context.Context, username, password string, r *http.Request) (*store.Account, *TokenPair, error) {
	username = normalizeUsername(username)
	limited, err := s.isLoginLimited(ctx, username, clientIP(r))
	if err != nil {
		return nil, nil, err
	}
	if limited {
		return nil, nil, ErrRateLimited
	}

	account, err := s.store.GetAccountByUsername(ctx, username)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			s.noteLoginFailure(ctx, username, clientIP(r))
			return nil, nil, ErrInvalidCredentials
		}
		return nil, nil, err
	}

	ok, err := VerifyPassword(account.PasswordHash, password)
	if err != nil {
		return nil, nil, err
	}
	if !ok {
		s.noteLoginFailure(ctx, username, clientIP(r))
		return nil, nil, ErrInvalidCredentials
	}
	if err := s.clearLoginFailure(ctx, username, clientIP(r)); err != nil {
		slog.Warn("clear login failure counter", "error", err)
	}

	tokens, err := s.createTokenPair(ctx, account.ID, r)
	if err != nil {
		return nil, nil, err
	}
	return account, tokens, nil
}

func (s *Service) Refresh(ctx context.Context, refreshToken string, r *http.Request) (*store.Account, *TokenPair, error) {
	if refreshToken == "" {
		return nil, nil, ErrUnauthenticated
	}
	session, err := s.store.GetSessionByRefreshToken(ctx, refreshToken)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil, ErrUnauthenticated
		}
		return nil, nil, err
	}
	account, err := s.store.GetAccountByID(ctx, session.AccountID)
	if err != nil {
		return nil, nil, err
	}

	newRefreshToken := randomToken()
	expiresAt := time.Now().Add(refreshTokenTTL)
	if _, err := s.store.RotateSession(ctx, refreshToken, &store.CreateSession{
		AccountID:    account.ID,
		RefreshToken: newRefreshToken,
		UserAgent:    r.UserAgent(),
		ClientIP:     clientIP(r),
		ExpiresAt:    expiresAt,
	}); err != nil {
		return nil, nil, err
	}
	accessToken, accessExpiresAt, err := s.SignAccessToken(account.ID)
	if err != nil {
		return nil, nil, err
	}
	return account, &TokenPair{AccessToken: accessToken, RefreshToken: newRefreshToken, ExpiresAt: accessExpiresAt}, nil
}

func (s *Service) SignOut(ctx context.Context, refreshToken string) error {
	if refreshToken == "" {
		return nil
	}
	return s.store.DeleteSessionByRefreshToken(ctx, refreshToken)
}

// ChangePassword verifies the current password, stores a new hash, revokes every
// refresh session for the account, and issues a new token pair for this request
// so the caller remains signed in with the new credentials.
func (s *Service) ChangePassword(ctx context.Context, accountID, currentPassword, newPassword string, r *http.Request) (*store.Account, *TokenPair, error) {
	account, err := s.store.GetAccountByID(ctx, accountID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil, ErrUnauthenticated
		}
		return nil, nil, err
	}
	ok, err := VerifyPassword(account.PasswordHash, currentPassword)
	if err != nil {
		return nil, nil, err
	}
	if !ok {
		return nil, nil, ErrInvalidCredentials
	}
	passwordHash, err := HashPassword(newPassword)
	if err != nil {
		return nil, nil, err
	}
	if err := s.store.UpdateAccountPassword(ctx, account.ID, passwordHash, PasswordAlgorithmName); err != nil {
		return nil, nil, err
	}
	if err := s.store.DeleteSessionsForAccount(ctx, account.ID); err != nil {
		return nil, nil, err
	}
	// Reload so UpdatedAt reflects the password write.
	account, err = s.store.GetAccountByID(ctx, account.ID)
	if err != nil {
		return nil, nil, err
	}
	tokens, err := s.createTokenPair(ctx, account.ID, r)
	if err != nil {
		return nil, nil, err
	}
	return account, tokens, nil
}

func (s *Service) SignAccessToken(accountID string) (string, time.Time, error) {
	now := time.Now().UTC()
	expiresAt := now.Add(accessTokenTTL)
	claims := AccessClaims{AccountID: accountID, IssuedAt: now.Unix(), ExpiresAt: expiresAt.Unix()}
	payload, err := json.Marshal(claims)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("encode claims: %w", err)
	}
	encodedPayload := base64.RawURLEncoding.EncodeToString(payload)
	signature := s.sign(encodedPayload)
	return encodedPayload + "." + signature, expiresAt, nil
}

func (s *Service) VerifyAccessToken(token string) (*AccessClaims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 2 {
		return nil, ErrUnauthenticated
	}
	expected := s.sign(parts[0])
	if subtle.ConstantTimeCompare([]byte(expected), []byte(parts[1])) != 1 {
		return nil, ErrUnauthenticated
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return nil, ErrUnauthenticated
	}
	var claims AccessClaims
	if err := json.Unmarshal(payload, &claims); err != nil {
		return nil, ErrUnauthenticated
	}
	if claims.ExpiresAt <= time.Now().UTC().Unix() {
		return nil, ErrUnauthenticated
	}
	return &claims, nil
}

func (s *Service) createTokenPair(ctx context.Context, accountID string, r *http.Request) (*TokenPair, error) {
	refreshToken := randomToken()
	if _, err := s.store.CreateSession(ctx, &store.CreateSession{
		AccountID:    accountID,
		RefreshToken: refreshToken,
		UserAgent:    r.UserAgent(),
		ClientIP:     clientIP(r),
		ExpiresAt:    time.Now().Add(refreshTokenTTL),
	}); err != nil {
		return nil, err
	}
	accessToken, expiresAt, err := s.SignAccessToken(accountID)
	if err != nil {
		return nil, err
	}
	return &TokenPair{AccessToken: accessToken, RefreshToken: refreshToken, ExpiresAt: expiresAt}, nil
}

func (s *Service) sign(payload string) string {
	mac := hmac.New(sha256.New, s.sessionSecret)
	mac.Write([]byte(payload))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func (s *Service) isLoginLimited(ctx context.Context, username, ip string) (bool, error) {
	value, ok, err := s.store.RuntimeKVGet(ctx, "login_failure", username+"|"+ip)
	if err != nil || !ok {
		return false, err
	}
	count, err := strconv.Atoi(value)
	if err != nil {
		slog.Warn("parse login failure counter", "value", value, "error", err)
		return false, nil
	}
	return count >= maxLoginFailures, nil
}

// noteLoginFailure advances the rate-limit counter. Failing to record must not
// change the sign-in outcome, but it silently disables brute-force protection,
// so it is always logged.
func (s *Service) noteLoginFailure(ctx context.Context, username, ip string) {
	if err := s.recordLoginFailure(ctx, username, ip); err != nil {
		slog.Warn("record login failure counter", "error", err)
	}
}

func (s *Service) recordLoginFailure(ctx context.Context, username, ip string) error {
	key := username + "|" + ip
	value, ok, err := s.store.RuntimeKVGet(ctx, "login_failure", key)
	if err != nil {
		return err
	}
	count := 0
	if ok {
		parsed, err := strconv.Atoi(value)
		if err != nil {
			slog.Warn("parse login failure counter", "value", value, "error", err)
		} else {
			count = parsed
		}
	}
	return s.store.RuntimeKVPut(ctx, "login_failure", key, strconv.Itoa(count+1), loginFailureWindow)
}

func (s *Service) clearLoginFailure(ctx context.Context, username, ip string) error {
	return s.store.RuntimeKVDelete(ctx, "login_failure", username+"|"+ip)
}

func normalizeUsername(username string) string {
	return strings.ToLower(strings.TrimSpace(username))
}

func randomToken() string {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(fmt.Errorf("generate token: %w", err))
	}
	return base64.RawURLEncoding.EncodeToString(b[:])
}

func clientIP(r *http.Request) string {
	if r.Header.Get(TrustedProxyMarkerHeader) == "1" {
		if forwardedFor := r.Header.Get("X-Forwarded-For"); forwardedFor != "" {
			first := strings.TrimSpace(strings.Split(forwardedFor, ",")[0])
			if first != "" {
				if parsed := net.ParseIP(first); parsed != nil {
					return parsed.String()
				}
			}
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil {
		return host
	}
	if r.RemoteAddr != "" {
		return r.RemoteAddr
	}
	return "unknown"
}

func SetRefreshCookie(w http.ResponseWriter, r *http.Request, token string) {
	http.SetCookie(w, &http.Cookie{
		Name:     refreshCookieName,
		Value:    token,
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		Secure:   shouldUseSecureCookie(r),
		Expires:  time.Now().Add(refreshTokenTTL),
		MaxAge:   int(refreshTokenTTL.Seconds()),
	})
}

func ClearRefreshCookie(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{
		Name:     refreshCookieName,
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		Secure:   shouldUseSecureCookie(r),
		Expires:  time.Unix(0, 0),
		MaxAge:   -1,
	})
}

func RefreshTokenFromCookie(r *http.Request) string {
	cookie, err := r.Cookie(refreshCookieName)
	if err != nil {
		return ""
	}
	return cookie.Value
}

// SetAccessCookie mirrors the access token into an HttpOnly cookie so that
// browser-native requests (img tags, download links) that cannot send an
// Authorization header can still be authenticated. It shares the access
// token's lifetime and is refreshed alongside it.
func SetAccessCookie(w http.ResponseWriter, r *http.Request, token string) {
	http.SetCookie(w, &http.Cookie{
		Name:     accessCookieName,
		Value:    token,
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		Secure:   shouldUseSecureCookie(r),
		Expires:  time.Now().Add(accessTokenTTL),
		MaxAge:   int(accessTokenTTL.Seconds()),
	})
}

func ClearAccessCookie(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{
		Name:     accessCookieName,
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		Secure:   shouldUseSecureCookie(r),
		Expires:  time.Unix(0, 0),
		MaxAge:   -1,
	})
}

func AccessTokenFromCookie(r *http.Request) string {
	cookie, err := r.Cookie(accessCookieName)
	if err != nil {
		return ""
	}
	return cookie.Value
}

func shouldUseSecureCookie(r *http.Request) bool {
	if r.TLS != nil {
		return true
	}
	if r.Header.Get(TrustedProxyMarkerHeader) == "1" && strings.EqualFold(strings.TrimSpace(r.Header.Get("X-Forwarded-Proto")), "https") {
		return true
	}
	return false
}

func NewID() string {
	id, err := uuid.NewV7()
	if err != nil {
		panic(fmt.Errorf("generate id: %w", err))
	}
	return id.String()
}
