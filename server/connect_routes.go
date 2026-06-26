package server

import (
	"github.com/labstack/echo/v5"
	apiv1connect "github.com/miofelix/sillage/proto/gen/api/v1/apiv1connect"
)

func (s *Server) registerConnectRoutes(e *echo.Echo) {
	path, handler := apiv1connect.NewMemoServiceHandler(&memoService{server: s})
	e.Any(path+"*", echo.WrapHandler(handler))
}
