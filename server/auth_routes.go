package server

import (
	"database/sql"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/miofelix/sillage/server/auth"
	"github.com/miofelix/sillage/store"
)

type authRequest struct {
	Username    string `json:"username"`
	DisplayName string `json:"displayName"`
	Password    string `json:"password"`
}

func (s *Server) registerAuthRoutes(e *echo.Echo) {
	e.GET("/api/v1/auth/bootstrap", s.handleAuthBootstrap)
	e.POST("/api/v1/auth/initialize", s.handleAuthInitialize)
	e.POST("/api/v1/auth/signin", s.handleAuthSignIn)
	e.POST("/api/v1/auth/refresh", s.handleAuthRefresh)
	e.POST("/api/v1/auth/signout", s.handleAuthSignOut)
	e.GET("/api/v1/auth/me", s.handleAuthMe)
}

func (s *Server) handleAuthBootstrap(c *echo.Context) error {
	initialized, err := s.authBootstrap(c.Request().Context())
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "无法读取初始化状态")
	}
	return c.JSON(http.StatusOK, map[string]bool{"initialized": initialized})
}

func (s *Server) handleAuthInitialize(c *echo.Context) error {
	var req authRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}

	account, tokens, err := s.initializeAccount(c.Request().Context(), authInput{
		Username:    req.Username,
		DisplayName: req.DisplayName,
		Password:    req.Password,
	}, c.Request())
	if err != nil {
		switch {
		case errors.Is(err, errValidation):
			return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
		case errors.Is(err, store.ErrAccountExists):
			return apiError(c, http.StatusForbidden, "already_initialized", "这个实例已经初始化")
		default:
			return apiError(c, http.StatusInternalServerError, "internal", "初始化失败")
		}
	}
	auth.SetRefreshCookie(c.Response(), c.Request(), tokens.RefreshToken)
	return c.JSON(http.StatusOK, authResponse(account, tokens))
}

func (s *Server) handleAuthSignIn(c *echo.Context) error {
	var req authRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	account, tokens, err := s.signIn(c.Request().Context(), authInput{
		Username: req.Username,
		Password: req.Password,
	}, c.Request())
	if err != nil {
		switch {
		case errors.Is(err, auth.ErrInvalidCredentials):
			return apiError(c, http.StatusUnauthorized, "invalid_credentials", "账号或密码不正确")
		case errors.Is(err, auth.ErrRateLimited):
			return apiError(c, http.StatusTooManyRequests, "rate_limited", "尝试次数太多，请稍后再试")
		default:
			return apiError(c, http.StatusInternalServerError, "internal", "登录失败")
		}
	}
	auth.SetRefreshCookie(c.Response(), c.Request(), tokens.RefreshToken)
	return c.JSON(http.StatusOK, authResponse(account, tokens))
}

func (s *Server) handleAuthRefresh(c *echo.Context) error {
	refreshToken := auth.RefreshTokenFromCookie(c.Request())
	account, tokens, err := s.refreshAuth(c.Request().Context(), refreshToken, c.Request())
	if err != nil {
		if errors.Is(err, auth.ErrUnauthenticated) || errors.Is(err, sql.ErrNoRows) {
			auth.ClearRefreshCookie(c.Response(), c.Request())
			return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "刷新登录状态失败")
	}
	auth.SetRefreshCookie(c.Response(), c.Request(), tokens.RefreshToken)
	return c.JSON(http.StatusOK, authResponse(account, tokens))
}

func (s *Server) handleAuthSignOut(c *echo.Context) error {
	if err := s.signOut(c.Request().Context(), auth.RefreshTokenFromCookie(c.Request())); err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "退出失败")
	}
	auth.ClearRefreshCookie(c.Response(), c.Request())
	return c.NoContent(http.StatusNoContent)
}

func (s *Server) handleAuthMe(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	return c.JSON(http.StatusOK, map[string]any{"account": accountDTO(account)})
}

func (s *Server) accountFromBearer(c *echo.Context) (*store.Account, error) {
	header := c.Request().Header.Get("Authorization")
	token, ok := strings.CutPrefix(header, "Bearer ")
	if !ok || token == "" {
		return nil, auth.ErrUnauthenticated
	}
	claims, err := s.auth.VerifyAccessToken(token)
	if err != nil {
		return nil, err
	}
	return s.Store.GetAccountByID(c.Request().Context(), claims.AccountID)
}

func authResponse(account *store.Account, tokens *auth.TokenPair) map[string]any {
	return map[string]any{
		"account":     accountDTO(account),
		"accessToken": tokens.AccessToken,
		"expiresAt":   tokens.ExpiresAt.UTC().Format(time.RFC3339),
	}
}

func accountDTO(account *store.Account) map[string]any {
	return map[string]any{
		"id":          account.ID,
		"username":    account.Username,
		"displayName": account.DisplayName,
		"createdAt":   time.UnixMilli(account.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":   time.UnixMilli(account.UpdatedAt).UTC().Format(time.RFC3339),
	}
}

func apiError(c *echo.Context, status int, code, message string) error {
	return c.JSON(status, map[string]any{
		"error": map[string]string{
			"code":    code,
			"message": message,
		},
	})
}
