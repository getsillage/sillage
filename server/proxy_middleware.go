package server

import (
	"net"
	"net/http"
	"net/netip"
	"strings"

	"github.com/labstack/echo/v5"

	"github.com/getsillage/sillage/server/auth"
)

const maxRequestBodyBytes = 8 << 20

var forwardedHeaders = []string{
	"Forwarded",
	"X-Forwarded-For",
	"X-Forwarded-Host",
	"X-Forwarded-Proto",
	"X-Real-IP",
}

func trustedProxyHeadersMiddleware(raw []string) echo.MiddlewareFunc {
	prefixes := make([]netip.Prefix, 0, len(raw))
	for _, value := range raw {
		if prefix, err := netip.ParsePrefix(strings.TrimSpace(value)); err == nil {
			prefixes = append(prefixes, prefix)
		}
	}

	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c *echo.Context) error {
			req := c.Request()
			if !isTrustedProxy(req, prefixes) {
				for _, header := range forwardedHeaders {
					req.Header.Del(header)
				}
				req.Header.Del(auth.TrustedProxyMarkerHeader)
				return next(c)
			}
			req.Header.Set(auth.TrustedProxyMarkerHeader, "1")
			return next(c)
		}
	}
}

func isTrustedProxy(req *http.Request, prefixes []netip.Prefix) bool {
	if len(prefixes) == 0 {
		return false
	}
	host := req.RemoteAddr
	if parsed, _, err := net.SplitHostPort(host); err == nil {
		host = parsed
	}
	addr, err := netip.ParseAddr(strings.TrimSpace(host))
	if err != nil {
		return false
	}
	for _, prefix := range prefixes {
		if prefix.Contains(addr) {
			return true
		}
	}
	return false
}
