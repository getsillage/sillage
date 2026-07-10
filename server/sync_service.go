package server

import (
	"context"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
)

type syncService struct {
	server *Server
}

func (s *syncService) PullSync(ctx context.Context, req *connect.Request[apiv1.PullSyncRequest]) (*connect.Response[apiv1.PullSyncResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	result, err := s.server.pullSync(ctx, account.ID, req.Msg.GetCursor(), int(req.Msg.GetLimit()))
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.PullSyncResponse{
		Memos:            make([]*apiv1.Memo, 0, len(result.Memos)),
		Attachments:      make([]*apiv1.Attachment, 0, len(result.Attachments)),
		MemoAi:           make([]*apiv1.MemoAI, 0, len(result.MemoAI)),
		AskConversations: make([]*apiv1.AskConversation, 0, len(result.AskConversations)),
		AskMessages:      make([]*apiv1.AskMessage, 0, len(result.AskMessages)),
		Cursor:           result.Cursor,
		NextCursor:       result.NextCursor,
		HasMore:          result.HasMore,
	}
	for _, memo := range result.Memos {
		res.Memos = append(res.Memos, memoPB(memo))
	}
	for _, attachment := range result.Attachments {
		res.Attachments = append(res.Attachments, attachmentPB(attachment))
	}
	for _, ai := range result.MemoAI {
		res.MemoAi = append(res.MemoAi, memoAIPB(ai))
	}
	for _, conversation := range result.AskConversations {
		res.AskConversations = append(res.AskConversations, askConversationPB(conversation))
	}
	for _, message := range result.AskMessages {
		res.AskMessages = append(res.AskMessages, askMessagePB(message))
	}
	return connect.NewResponse(res), nil
}

func (s *syncService) PushSync(ctx context.Context, req *connect.Request[apiv1.PushSyncRequest]) (*connect.Response[apiv1.PushSyncResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	results, err := s.server.pushSync(ctx, account.ID, syncChangesFromPB(req.Msg.GetChanges()))
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.PushSyncResponse{Results: make([]*apiv1.SyncResult, 0, len(results))}
	for _, result := range results {
		res.Results = append(res.Results, syncResultPB(result))
	}
	return connect.NewResponse(res), nil
}

func syncChangesFromPB(changes []*apiv1.SyncChange) []syncChange {
	items := make([]syncChange, 0, len(changes))
	for _, change := range changes {
		if change == nil {
			continue
		}
		var memo *syncMemoPayload
		if pbMemo := change.GetMemo(); pbMemo != nil {
			memo = &syncMemoPayload{
				ID:        pbMemo.GetId(),
				Content:   pbMemo.GetContent(),
				EntryDate: pbMemo.GetEntryDate(),
				Pinned:    pbMemo.Pinned,
				Archived:  pbMemo.Archived,
				Favorited: pbMemo.Favorited,
			}
		}
		items = append(items, syncChange{
			MutationID:     change.GetMutationId(),
			ResourceType:   change.GetResourceType(),
			ResourceID:     change.GetResourceId(),
			Action:         change.GetAction(),
			BaseVersion:    change.GetBaseVersion(),
			LocalChangedAt: change.GetLocalChangedAt(),
			Memo:           memo,
		})
	}
	return items
}
