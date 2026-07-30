package server

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/labstack/echo/v5"

	"github.com/getsillage/sillage/server/auth"
)

func TestTrustedProxyHeadersMiddlewareRejectsUntrustedForwardingHeaders(t *testing.T) {
	got := serveProxyHeaders(t, []string{"127.0.0.1/32"}, "198.51.100.8:4321")
	if got.forwardedFor != "" || got.forwardedProto != "" || got.marker != "" {
		t.Fatalf("untrusted forwarded headers survived: %+v", got)
	}
}

func TestTrustedProxyHeadersMiddlewareAcceptsConfiguredProxy(t *testing.T) {
	got := serveProxyHeaders(t, []string{"198.51.100.0/24"}, "198.51.100.8:4321")
	if got.forwardedFor != "203.0.113.9" || got.forwardedProto != "https" || got.marker != "1" {
		t.Fatalf("trusted forwarded headers = %+v", got)
	}
}

type proxyHeaderResult struct {
	forwardedFor   string
	forwardedProto string
	marker         string
}

func serveProxyHeaders(t *testing.T, cidrs []string, remoteAddr string) proxyHeaderResult {
	t.Helper()
	e := echo.New()
	var got proxyHeaderResult
	e.Use(trustedProxyHeadersMiddleware(cidrs))
	e.GET("/", func(c *echo.Context) error {
		got = proxyHeaderResult{
			forwardedFor:   c.Request().Header.Get("X-Forwarded-For"),
			forwardedProto: c.Request().Header.Get("X-Forwarded-Proto"),
			marker:         c.Request().Header.Get(auth.TrustedProxyMarkerHeader),
		}
		return c.NoContent(http.StatusNoContent)
	})
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.RemoteAddr = remoteAddr
	req.Header.Set("X-Forwarded-For", "203.0.113.9")
	req.Header.Set("X-Forwarded-Proto", "https")
	req.Header.Set(auth.TrustedProxyMarkerHeader, "1")
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("status = %d", rec.Code)
	}
	return got
}
