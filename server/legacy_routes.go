package server

import (
	"net/http"

	"github.com/labstack/echo/v5"
)

func registerLegacyRemovedRoutes(e *echo.Echo) {
	for _, path := range []string{
		"/download-backup",
		"/api/backup",
		"/api/backups",
	} {
		e.GET(path, legacyRemovedRoute)
		e.POST(path, legacyRemovedRoute)
	}
}

func legacyRemovedRoute(c *echo.Context) error {
	return apiError(c, http.StatusNotFound, "not_found", "接口不存在")
}
