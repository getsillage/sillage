package server

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/http"
	"time"

	"connectrpc.com/connect"
	"google.golang.org/protobuf/types/known/emptypb"

	"github.com/getsillage/sillage/internal/profile"
	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
	"github.com/getsillage/sillage/server/auth"
	"github.com/getsillage/sillage/store"
)

type authService struct {
	server *Server
}

func (s *authService) Bootstrap(ctx context.Context, _ *connect.Request[apiv1.BootstrapRequest]) (*connect.Response[apiv1.BootstrapResponse], error) {
	initialized, err := s.server.authBootstrap(ctx)
	if err != nil {
		return nil, connect.NewError(connect.CodeInternal, err)
	}
	return connect.NewResponse(&apiv1.BootstrapResponse{
		Initialized:               initialized,
		ServerVersion:             s.server.Build.Version,
		ServerRevision:            s.server.Build.Revision,
		ApiVersion:                s.server.Build.APIVersion,
		MinimumAndroidVersionCode: s.server.Build.MinimumAndroidVersionCode,
	}), nil
}

func (s *authService) Initialize(ctx context.Context, req *connect.Request[apiv1.InitializeRequest]) (*connect.Response[apiv1.AuthResponse], error) {
	httpReq := requestFromConnectHeader(req.Header())
	account, tokens, err := s.server.initializeAccount(ctx, authInput{
		Username:    req.Msg.GetUsername(),
		DisplayName: req.Msg.GetDisplayName(),
		Password:    req.Msg.GetPassword(),
	}, httpReq)
	if err != nil {
		return nil, authConnectError(err)
	}
	res := connect.NewResponse(authResponsePB(account, tokens))
	setRefreshCookieHeader(res.Header(), httpReq, tokens.RefreshToken)
	return res, nil
}

func (s *authService) SignIn(ctx context.Context, req *connect.Request[apiv1.SignInRequest]) (*connect.Response[apiv1.AuthResponse], error) {
	httpReq := requestFromConnectHeader(req.Header())
	account, tokens, err := s.server.signIn(ctx, authInput{
		Username: req.Msg.GetUsername(),
		Password: req.Msg.GetPassword(),
	}, httpReq)
	if err != nil {
		return nil, authConnectError(err)
	}
	res := connect.NewResponse(authResponsePB(account, tokens))
	setRefreshCookieHeader(res.Header(), httpReq, tokens.RefreshToken)
	return res, nil
}

func (s *authService) Refresh(ctx context.Context, req *connect.Request[apiv1.RefreshRequest]) (*connect.Response[apiv1.AuthResponse], error) {
	httpReq := requestFromConnectHeader(req.Header())
	account, tokens, err := s.server.refreshAuth(ctx, auth.RefreshTokenFromCookie(httpReq), httpReq)
	if err != nil {
		resErr := authConnectError(err)
		if connect.CodeOf(resErr) == connect.CodeUnauthenticated {
			res := connect.NewError(connect.CodeUnauthenticated, auth.ErrUnauthenticated)
			return nil, res
		}
		return nil, resErr
	}
	res := connect.NewResponse(authResponsePB(account, tokens))
	setRefreshCookieHeader(res.Header(), httpReq, tokens.RefreshToken)
	return res, nil
}

func (s *authService) SignOut(ctx context.Context, req *connect.Request[apiv1.SignOutRequest]) (*connect.Response[emptypb.Empty], error) {
	httpReq := requestFromConnectHeader(req.Header())
	if err := s.server.signOut(ctx, auth.RefreshTokenFromCookie(httpReq)); err != nil {
		return nil, connect.NewError(connect.CodeInternal, err)
	}
	res := connect.NewResponse(&emptypb.Empty{})
	clearRefreshCookieHeader(res.Header(), httpReq)
	return res, nil
}

func (s *authService) Me(ctx context.Context, req *connect.Request[apiv1.MeRequest]) (*connect.Response[apiv1.MeResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	return connect.NewResponse(&apiv1.MeResponse{Account: accountPB(account)}), nil
}

func (s *authService) ChangePassword(ctx context.Context, req *connect.Request[apiv1.ChangePasswordRequest]) (*connect.Response[apiv1.AuthResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	httpReq := requestFromConnectHeader(req.Header())
	updated, tokens, err := s.server.changePassword(ctx, account.ID, changePasswordInput{
		CurrentPassword: req.Msg.GetCurrentPassword(),
		NewPassword:     req.Msg.GetNewPassword(),
	}, httpReq)
	if err != nil {
		return nil, authConnectError(err)
	}
	res := connect.NewResponse(authResponsePB(updated, tokens))
	setRefreshCookieHeader(res.Header(), httpReq, tokens.RefreshToken)
	auth.SetAccessCookie(headerOnlyResponseWriter{res.Header()}, httpReq, tokens.AccessToken)
	return res, nil
}

func authResponsePB(account *store.Account, tokens *auth.TokenPair) *apiv1.AuthResponse {
	return &apiv1.AuthResponse{
		Account:     accountPB(account),
		AccessToken: tokens.AccessToken,
		ExpiresAt:   tokens.ExpiresAt.UTC().Format(time.RFC3339),
	}
}

func authConnectError(err error) error {
	switch {
	case errors.Is(err, errValidation):
		return connect.NewError(connect.CodeInvalidArgument, err)
	case errors.Is(err, store.ErrAccountExists):
		return connect.NewError(connect.CodePermissionDenied, err)
	case errors.Is(err, auth.ErrInvalidCredentials), errors.Is(err, auth.ErrUnauthenticated), errors.Is(err, sql.ErrNoRows):
		return connect.NewError(connect.CodeUnauthenticated, err)
	case errors.Is(err, auth.ErrRateLimited):
		return connect.NewError(connect.CodeResourceExhausted, err)
	default:
		return connect.NewError(connect.CodeInternal, err)
	}
}

// requestFromConnectHeader adapts a Connect call's headers into an
// *http.Request so the cookie helpers can derive the Secure flag the same way
// they do for plain REST requests.
func requestFromConnectHeader(header http.Header) *http.Request {
	host := header.Get("X-Forwarded-Host")
	if host == "" {
		host = fmt.Sprintf("localhost:%d", profile.DefaultPort)
	}
	return &http.Request{
		Method: http.MethodPost,
		Host:   host,
		Header: header.Clone(),
	}
}

func setRefreshCookieHeader(header http.Header, req *http.Request, token string) {
	auth.SetRefreshCookie(headerOnlyResponseWriter{header}, req, token)
}

func clearRefreshCookieHeader(header http.Header, req *http.Request) {
	auth.ClearRefreshCookie(headerOnlyResponseWriter{header}, req)
}

// headerOnlyResponseWriter lets http.SetCookie-based helpers write Set-Cookie
// values straight onto a Connect response header. Body and status are never
// used by those helpers.
type headerOnlyResponseWriter struct {
	header http.Header
}

func (w headerOnlyResponseWriter) Header() http.Header { return w.header }

func (w headerOnlyResponseWriter) Write(b []byte) (int, error) { return len(b), nil }

func (w headerOnlyResponseWriter) WriteHeader(int) {}
