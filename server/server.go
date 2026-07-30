package server

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/labstack/echo/v5"
	"github.com/labstack/echo/v5/middleware"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/internal/secret"
	"github.com/getsillage/sillage/server/auth"
	memoapp "github.com/getsillage/sillage/server/memo"
	"github.com/getsillage/sillage/server/router/frontend"
	"github.com/getsillage/sillage/store"
)

const shutdownTimeout = 10 * time.Second

type Server struct {
	Profile *profile.Profile
	Store   *store.Store
	Secrets *secret.Secrets

	echoServer *echo.Echo
	httpServer *http.Server
	auth       *auth.Service
	memos      *memoapp.Service
	memoAIJobs chan struct{}
	askAIJobs  chan struct{}

	maintenanceCancel context.CancelFunc
	maintenanceWG     sync.WaitGroup
}

func New(_ context.Context, p *profile.Profile, s *store.Store, secrets *secret.Secrets) (*Server, error) {
	e := echo.New()
	e.Use(middleware.Recover())
	e.Use(trustedProxyHeadersMiddleware(p.TrustedProxyCIDRs))
	e.Use(middleware.BodyLimitWithConfig(middleware.BodyLimitConfig{
		LimitBytes: maxRequestBodyBytes,
		Skipper: func(c *echo.Context) bool {
			return c.Request().Method == http.MethodPost && c.Request().URL.Path == "/api/v1/attachments"
		},
	}))
	e.Use(securityHeadersMiddleware())
	e.Use(requestLogMiddleware())

	server := &Server{
		Profile:    p,
		Store:      s,
		Secrets:    secrets,
		echoServer: e,
		auth:       auth.NewService(s, secrets.SessionSecret),
		memoAIJobs: make(chan struct{}, 2),
		askAIJobs:  make(chan struct{}, 2),
	}
	server.memos = memoapp.NewService(s, server.maybeScheduleAutoSummary)

	e.GET("/healthz", func(c *echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{"status": "ok"})
	})
	e.GET("/readyz", func(c *echo.Context) error {
		ctx, cancel := context.WithTimeout(c.Request().Context(), 2*time.Second)
		defer cancel()
		if err := s.Ready(ctx); err != nil {
			return c.JSON(http.StatusServiceUnavailable, map[string]string{
				"status": "not_ready",
				"error":  err.Error(),
			})
		}
		return c.JSON(http.StatusOK, map[string]string{"status": "ready"})
	})
	server.registerAuthRoutes(e)
	server.registerMemoRoutes(e)
	server.registerAttachmentRoutes(e)
	server.registerAIRoutes(e)
	server.registerAskRoutes(e)
	server.registerConnectRoutes(e)
	registerLegacyRemovedRoutes(e)
	frontend.Register(e)

	return server, nil
}

func (s *Server) Start(ctx context.Context) error {
	address := fmt.Sprintf("%s:%d", s.Profile.Addr, s.Profile.Port)
	listener, err := net.Listen("tcp", address)
	if err != nil {
		return fmt.Errorf("listen on %s: %w", address, err)
	}

	s.httpServer = &http.Server{
		Handler:           s.echoServer,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       2 * time.Minute,
		WriteTimeout:      5 * time.Minute,
		IdleTimeout:       2 * time.Minute,
		MaxHeaderBytes:    1 << 20,
	}
	go func() {
		if err := s.httpServer.Serve(listener); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("http server stopped unexpectedly", "error", err)
		}
	}()
	s.startMaintenance(ctx)
	return nil
}

func (s *Server) Shutdown(ctx context.Context) error {
	s.stopMaintenance()
	if s.httpServer == nil {
		return s.Store.Close()
	}
	if deadline, ok := ctx.Deadline(); !ok || time.Until(deadline) > shutdownTimeout {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, shutdownTimeout)
		defer cancel()
	}
	shutdownErr := s.httpServer.Shutdown(ctx)
	closeErr := s.Store.Close()
	if shutdownErr != nil {
		return shutdownErr
	}
	return closeErr
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	s.echoServer.ServeHTTP(w, r)
}

func securityHeadersMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c *echo.Context) error {
			c.Response().Header().Set("X-Content-Type-Options", "nosniff")
			c.Response().Header().Set("X-Frame-Options", "DENY")
			c.Response().Header().Set("Content-Security-Policy", "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'; object-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; media-src 'self' blob:; worker-src 'self' blob:")
			c.Response().Header().Set("Referrer-Policy", "no-referrer")
			c.Response().Header().Set("Permissions-Policy", "camera=(), geolocation=(), microphone=(), payment=(), usb=()")
			return next(c)
		}
	}
}

func requestLogMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c *echo.Context) error {
			start := time.Now()
			requestID := c.Request().Header.Get("X-Request-ID")
			if requestID == "" {
				requestID = newRequestID()
			}
			c.Response().Header().Set("X-Request-ID", requestID)

			err := next(c)
			if err != nil {
				c.Echo().HTTPErrorHandler(c, err)
			}
			status := http.StatusOK
			if response, ok := c.Response().(*echo.Response); ok && response.Status != 0 {
				status = response.Status
			}
			if status == 0 {
				status = http.StatusOK
			}

			slog.Info("http request",
				"request_id", requestID,
				"method", c.Request().Method,
				"path", c.Request().URL.Path,
				"status", status,
				"duration_ms", time.Since(start).Milliseconds(),
				"client_ip", c.RealIP(),
			)
			return nil
		}
	}
}

func newRequestID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return fmt.Sprintf("%d", time.Now().UTC().UnixNano())
	}
	return hex.EncodeToString(b[:])
}
