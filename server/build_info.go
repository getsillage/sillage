package server

import (
	"strings"

	"github.com/labstack/echo/v5"
)

const currentAPIVersion = "v1"

// BuildInfo identifies the running server and its API generation. It is public operational
// metadata: never place secrets or environment-specific identifiers here.
type BuildInfo struct {
	Version    string
	Revision   string
	APIVersion string
}

func normalizeBuildInfo(info BuildInfo) BuildInfo {
	info.Version = strings.TrimSpace(info.Version)
	if info.Version == "" {
		info.Version = "dev"
	}
	info.Revision = strings.TrimSpace(info.Revision)
	if info.Revision == "" {
		info.Revision = "unknown"
	}
	info.APIVersion = strings.TrimSpace(info.APIVersion)
	if info.APIVersion == "" {
		info.APIVersion = currentAPIVersion
	}
	return info
}

func (s *Server) buildInfoMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c *echo.Context) error {
			header := c.Response().Header()
			header.Set("X-Sillage-Version", s.Build.Version)
			header.Set("X-Sillage-Revision", s.Build.Revision)
			header.Set("X-Sillage-API-Version", s.Build.APIVersion)
			return next(c)
		}
	}
}

func buildInfoJSON(info BuildInfo) map[string]any {
	return map[string]any{
		"serverVersion":  info.Version,
		"serverRevision": info.Revision,
		"apiVersion":     info.APIVersion,
	}
}

func noStore(c *echo.Context) {
	c.Response().Header().Set("Cache-Control", "no-store")
	c.Response().Header().Set("Pragma", "no-cache")
	c.Response().Header().Set("Expires", "0")
}
