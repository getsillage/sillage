package server

import (
	apiv1connect "github.com/getsillage/sillage/proto/gen/api/v1/apiv1connect"
	"github.com/labstack/echo/v5"
)

func (s *Server) registerConnectRoutes(e *echo.Echo) {
	askPath, askHandler := apiv1connect.NewAskServiceHandler(&askService{server: s})
	e.Any(askPath+"*", echo.WrapHandler(askHandler))
	attachmentPath, attachmentHandler := apiv1connect.NewAttachmentServiceHandler(&attachmentService{server: s})
	e.Any(attachmentPath+"*", echo.WrapHandler(attachmentHandler))
	authPath, authHandler := apiv1connect.NewAuthServiceHandler(&authService{server: s})
	e.Any(authPath+"*", echo.WrapHandler(authHandler))
	memoPath, memoHandler := apiv1connect.NewMemoServiceHandler(&memoService{server: s})
	e.Any(memoPath+"*", echo.WrapHandler(memoHandler))
	settingsPath, settingsHandler := apiv1connect.NewSettingsServiceHandler(&settingsService{server: s})
	e.Any(settingsPath+"*", echo.WrapHandler(settingsHandler))
	syncPath, syncHandler := apiv1connect.NewSyncServiceHandler(&syncService{server: s})
	e.Any(syncPath+"*", echo.WrapHandler(syncHandler))
}
