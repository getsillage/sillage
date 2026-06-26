package server

import (
	"github.com/labstack/echo/v5"
	apiv1connect "github.com/miofelix/sillage/proto/gen/api/v1/apiv1connect"
)

func (s *Server) registerConnectRoutes(e *echo.Echo) {
	memoPath, memoHandler := apiv1connect.NewMemoServiceHandler(&memoService{server: s})
	e.Any(memoPath+"*", echo.WrapHandler(memoHandler))
	syncPath, syncHandler := apiv1connect.NewSyncServiceHandler(&syncService{server: s})
	e.Any(syncPath+"*", echo.WrapHandler(syncHandler))
}
