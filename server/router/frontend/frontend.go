package frontend

import (
	"embed"
	"io/fs"
	"net/http"
	"strings"

	"github.com/labstack/echo/v5"
)

//go:embed dist
var distFS embed.FS

func Register(e *echo.Echo) {
	subFS, err := fs.Sub(distFS, "dist")
	if err != nil {
		registerFallback(e)
		return
	}
	fileServer := http.FileServer(http.FS(subFS))
	e.GET("/*", func(c *echo.Context) error {
		path := c.Request().URL.Path
		if shouldSkip(path) {
			return echo.ErrNotFound
		}
		if path == "/" || !hasFileExtension(path) {
			if _, err := subFS.Open("index.html"); err == nil {
				c.Request().URL.Path = "/"
				fileServer.ServeHTTP(c.Response(), c.Request())
				return nil
			}
		}
		fileServer.ServeHTTP(c.Response(), c.Request())
		return nil
	})
}

func registerFallback(e *echo.Echo) {
	e.GET("/*", func(c *echo.Context) error {
		if shouldSkip(c.Request().URL.Path) {
			return echo.ErrNotFound
		}
		return c.HTML(http.StatusOK, `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Sillage</title></head><body><main style="font-family:system-ui,sans-serif;max-width:680px;margin:64px auto;padding:0 20px"><h1>Sillage</h1><p>前端构建产物尚未生成。请运行 <code>pnpm --dir web build</code>。</p></main></body></html>`)
	})
}

func shouldSkip(path string) bool {
	return strings.HasPrefix(path, "/api/") ||
		strings.HasPrefix(path, "/file/") ||
		strings.HasPrefix(path, "/sillage.api.v1")
}

func hasFileExtension(path string) bool {
	lastSlash := strings.LastIndex(path, "/")
	lastDot := strings.LastIndex(path, ".")
	return lastDot > lastSlash
}
