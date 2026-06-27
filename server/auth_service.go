package server

import (
	"context"
	"database/sql"
	"errors"
	"net/http"
	"net/http/httptest"
	"time"

	"connectrpc.com/connect"
	"google.golang.org/protobuf/types/known/emptypb"

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
	return connect.NewResponse(&apiv1.BootstrapResponse{Initialized: initialized}), nil
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

func requestFromConnectHeader(header http.Header) *http.Request {
	req := httptest.NewRequest(http.MethodPost, "/", nil)
	req.Host = header.Get("X-Forwarded-Host")
	if req.Host == "" {
		req.Host = "localhost:5231"
	}
	req.Header = header.Clone()
	return req
}

func setRefreshCookieHeader(header http.Header, req *http.Request, token string) {
	rec := httptest.NewRecorder()
	auth.SetRefreshCookie(rec, req, token)
	copySetCookie(header, rec)
}

func clearRefreshCookieHeader(header http.Header, req *http.Request) {
	rec := httptest.NewRecorder()
	auth.ClearRefreshCookie(rec, req)
	copySetCookie(header, rec)
}

func copySetCookie(header http.Header, rec *httptest.ResponseRecorder) {
	for _, value := range rec.Result().Header.Values("Set-Cookie") {
		header.Add("Set-Cookie", value)
	}
}
