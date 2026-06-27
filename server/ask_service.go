package server

import (
	"context"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
)

type askService struct {
	server *Server
}

func (s *askService) ListAskConversations(ctx context.Context, req *connect.Request[apiv1.ListAskConversationsRequest]) (*connect.Response[apiv1.ListAskConversationsResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	conversations, err := s.server.listAskConversations(ctx, account.ID, int(req.Msg.GetLimit()))
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.ListAskConversationsResponse{Conversations: make([]*apiv1.AskConversation, 0, len(conversations))}
	for _, conversation := range conversations {
		res.Conversations = append(res.Conversations, askConversationPB(conversation))
	}
	return connect.NewResponse(res), nil
}

func (s *askService) CreateAskConversation(ctx context.Context, req *connect.Request[apiv1.CreateAskConversationRequest]) (*connect.Response[apiv1.AskConversationResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	conversation, err := s.server.createAskConversation(ctx, account.ID, askConversationInput{
		Title:        req.Msg.GetTitle(),
		ContextScope: req.Msg.GetContextScope(),
	})
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.AskConversationResponse{Conversation: askConversationPB(conversation)}), nil
}

func (s *askService) ListAskMessages(ctx context.Context, req *connect.Request[apiv1.ListAskMessagesRequest]) (*connect.Response[apiv1.ListAskMessagesResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	messages, err := s.server.listAskMessages(ctx, account.ID, req.Msg.GetConversationId())
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.ListAskMessagesResponse{Messages: make([]*apiv1.AskMessage, 0, len(messages))}
	for _, message := range messages {
		res.Messages = append(res.Messages, askMessagePB(message))
	}
	return connect.NewResponse(res), nil
}

func (s *askService) CreateAskMessage(ctx context.Context, req *connect.Request[apiv1.CreateAskMessageRequest]) (*connect.Response[apiv1.CreateAskMessageResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	result, err := s.server.createAskMessage(ctx, account.ID, askMessageInput{
		ConversationID: req.Msg.GetConversationId(),
		Content:        req.Msg.GetContent(),
		ContextScope:   req.Msg.GetContextScope(),
		ParentID:       req.Msg.GetParentId(),
		ForkOfID:       req.Msg.GetForkOfId(),
		SourceKind:     req.Msg.GetSourceKind(),
	})
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.CreateAskMessageResponse{Messages: make([]*apiv1.AskMessage, 0, len(result.Messages))}
	for _, message := range result.Messages {
		res.Messages = append(res.Messages, askMessagePB(message))
	}
	return connect.NewResponse(res), nil
}
