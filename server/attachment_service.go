package server

import (
	"context"
	"database/sql"
	"errors"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
)

type attachmentService struct {
	server *Server
}

func (s *attachmentService) GetAttachment(ctx context.Context, req *connect.Request[apiv1.GetAttachmentRequest]) (*connect.Response[apiv1.AttachmentResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	attachment, err := s.server.getAttachment(ctx, account.ID, req.Msg.GetUid())
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, connect.NewError(connect.CodeNotFound, err)
		}
		return nil, connect.NewError(connect.CodeInternal, err)
	}
	return connect.NewResponse(&apiv1.AttachmentResponse{Attachment: attachmentPB(attachment)}), nil
}
