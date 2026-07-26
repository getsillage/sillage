package server

// Sync push/pull application logic shared by the REST and Connect transports.
// Transport-level handlers live in sync_routes.go and sync_service.go.

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"

	memoapp "github.com/getsillage/sillage/server/memo"
	"github.com/getsillage/sillage/store"
)

type syncPushRequest struct {
	Changes []syncChange `json:"changes"`
}

type syncChange struct {
	MutationID     string           `json:"mutationId"`
	ResourceType   string           `json:"resourceType"`
	ResourceID     string           `json:"resourceId"`
	Action         string           `json:"action"`
	BaseVersion    int64            `json:"baseVersion"`
	LocalChangedAt string           `json:"localChangedAt"`
	Memo           *syncMemoPayload `json:"memo,omitempty"`
}

type syncMemoPayload struct {
	ID        string `json:"id"`
	Content   string `json:"content"`
	EntryDate string `json:"entryDate"`
	Pinned    *bool  `json:"pinned"`
	Archived  *bool  `json:"archived"`
	Favorited *bool  `json:"favorited"`
}

func (p syncMemoPayload) favoritedValue() *bool {
	if p.Favorited != nil {
		return p.Favorited
	}
	return p.Pinned
}

type syncPullResult struct {
	Memos            []*store.Memo
	Attachments      []*store.Attachment
	MemoAI           []*store.MemoAI
	AskConversations []*store.AskConversation
	AskMessages      []*store.AskMessage
	Cursor           string
	NextCursor       string
	HasMore          bool
}

type syncResult struct {
	MutationID     string      `json:"mutationId"`
	ResourceType   string      `json:"resourceType"`
	ResourceID     string      `json:"resourceId"`
	Status         string      `json:"status"`
	Reason         string      `json:"reason,omitempty"`
	Message        string      `json:"message,omitempty"`
	Idempotent     bool        `json:"idempotent,omitempty"`
	Resource       *store.Memo `json:"-"`
	ServerResource *store.Memo `json:"-"`
	ClientVersion  int64       `json:"clientVersion,omitempty"`
	ServerVersion  int64       `json:"serverVersion,omitempty"`
}

func (s *Server) pullSync(ctx context.Context, accountID, rawCursor string, limit int) (*syncPullResult, error) {
	cursor := decodeSyncCursor(rawCursor)
	limit = normalizeSyncPageLimit(limit)

	memos, err := s.Store.ListMemos(ctx, &store.ListMemoOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		IncludeDeleted:    true,
		Sync:              true,
		UpdatedAfter:      cursor.Memo.UpdatedAt,
		UpdatedAfterID:    cursor.Memo.ID,
	})
	if err != nil {
		return nil, err
	}
	attachments, err := s.Store.ListAttachments(ctx, &store.ListAttachmentOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		IncludeDeleted:    true,
		UpdatedAfter:      cursor.Attachment.UpdatedAt,
		UpdatedAfterID:    cursor.Attachment.ID,
	})
	if err != nil {
		return nil, err
	}
	memoAI, err := s.Store.ListMemoAI(ctx, &store.ListMemoAIOptions{
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		UpdatedAfter:      cursor.MemoAI.UpdatedAt,
		UpdatedAfterID:    cursor.MemoAI.ID,
	})
	if err != nil {
		return nil, err
	}
	askConversations, err := s.Store.ListAskConversationsForSync(ctx, &store.ListAskSyncOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		UpdatedAfter:      cursor.AskConversation.UpdatedAt,
		UpdatedAfterID:    cursor.AskConversation.ID,
	})
	if err != nil {
		return nil, err
	}
	askMessages, err := s.Store.ListAskMessagesForSync(ctx, &store.ListAskSyncOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		UpdatedAfter:      cursor.AskMessage.UpdatedAt,
		UpdatedAfterID:    cursor.AskMessage.ID,
	})
	if err != nil {
		return nil, err
	}

	memoHasMore := len(memos) > limit
	if memoHasMore {
		memos = memos[:limit]
	}
	attachmentHasMore := len(attachments) > limit
	if attachmentHasMore {
		attachments = attachments[:limit]
	}
	memoAIHasMore := len(memoAI) > limit
	if memoAIHasMore {
		memoAI = memoAI[:limit]
	}
	askConversationHasMore := len(askConversations) > limit
	if askConversationHasMore {
		askConversations = askConversations[:limit]
	}
	askMessageHasMore := len(askMessages) > limit
	if askMessageHasMore {
		askMessages = askMessages[:limit]
	}

	if len(memos) > 0 {
		last := memos[len(memos)-1]
		cursor.Memo = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(attachments) > 0 {
		last := attachments[len(attachments)-1]
		cursor.Attachment = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(memoAI) > 0 {
		last := memoAI[len(memoAI)-1]
		cursor.MemoAI = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.MemoID}
	}
	if len(askConversations) > 0 {
		last := askConversations[len(askConversations)-1]
		cursor.AskConversation = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(askMessages) > 0 {
		last := askMessages[len(askMessages)-1]
		cursor.AskMessage = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}

	encodedCursor := encodeSyncCursor(cursor)
	return &syncPullResult{
		Memos:            memos,
		Attachments:      attachments,
		MemoAI:           memoAI,
		AskConversations: askConversations,
		AskMessages:      askMessages,
		Cursor:           encodedCursor,
		NextCursor:       encodedCursor,
		HasMore:          memoHasMore || attachmentHasMore || memoAIHasMore || askConversationHasMore || askMessageHasMore,
	}, nil
}

func (s *Server) pushSync(ctx context.Context, accountID string, changes []syncChange) ([]syncResult, error) {
	if len(changes) > 200 {
		return nil, errTooManyChanges
	}
	results := make([]syncResult, 0, len(changes))
	for _, change := range changes {
		results = append(results, s.applySyncChange(ctx, accountID, change))
	}
	return results, nil
}

func (s *Server) applySyncChange(ctx context.Context, accountID string, change syncChange) syncResult {
	if change.MutationID == "" {
		return syncRejected(change, "missing_mutation_id", "mutationId 不能为空")
	}
	if previous, ok, err := s.Store.GetSyncMutation(ctx, accountID, change.MutationID); err == nil && ok {
		result, err := s.storedSyncResult(ctx, accountID, previous)
		if err == nil {
			return result
		}
		return syncRejected(change, "internal", "读取幂等状态失败")
	} else if err != nil {
		return syncRejected(change, "internal", "读取幂等状态失败")
	}
	if change.ResourceType != "memo" {
		return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "unsupported_resource", "暂不支持该资源类型"))
	}

	payload := syncMemoPayload{}
	if change.Memo != nil {
		payload = *change.Memo
	}
	if payload.ID == "" {
		payload.ID = change.ResourceID
	}

	var memo *store.Memo
	var err error
	favorited := payload.favoritedValue()
	switch change.Action {
	case "create":
		memo, err = s.memos.Create(ctx, accountID, memoapp.CreateInput{
			ID:        payload.ID,
			Content:   payload.Content,
			EntryDate: payload.EntryDate,
			Favorited: favorited != nil && *favorited,
			Archived:  payload.Archived != nil && *payload.Archived,
		})
	case "update":
		if change.BaseVersion <= 0 {
			return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "missing_base_version", "baseVersion 必须大于 0"))
		}
		if validateErr := memoapp.ValidateFields(payload.Content, payload.EntryDate); validateErr != nil {
			return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "invalid_field", validateErr.Error()))
		}
		memo, err = s.memos.Update(ctx, accountID, memoapp.UpdateInput{
			ID:              payload.ID,
			ExpectedVersion: change.BaseVersion,
			Content:         &payload.Content,
			EntryDate:       &payload.EntryDate,
			Favorited:       favorited,
			Archived:        payload.Archived,
		})
	case "delete":
		if change.BaseVersion <= 0 {
			return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "missing_base_version", "baseVersion 必须大于 0"))
		}
		memo, err = s.memos.Delete(ctx, accountID, payload.ID, change.BaseVersion)
	default:
		return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "unsupported_action", "暂不支持该同步动作"))
	}

	var conflict *store.MemoConflictError
	var result syncResult
	switch {
	case errors.As(err, &conflict):
		result = syncConflict(change, conflict.ServerMemo)
	case isValidationError(err):
		result = syncRejected(change, "invalid_field", err.Error())
	case err != nil:
		slog.Warn("sync change rejected", "action", change.Action, "resource", change.ResourceID, "error", err)
		result = syncRejected(change, "rejected", "同步该条记录失败，请重试")
	default:
		result = syncApplied(change, memo)
	}
	return s.finishSyncChange(ctx, accountID, change, result)
}

// finishSyncChange persists the per-mutation result for idempotency and returns
// it. If persistence fails we must NOT report the change as applied: the client
// would receive success yet a retry of the same mutationId would re-execute it.
// We surface an internal rejection so the client retries the whole change.
func (s *Server) finishSyncChange(ctx context.Context, accountID string, change syncChange, result syncResult) syncResult {
	if err := s.persistSyncResult(ctx, accountID, change, result); err != nil {
		return syncRejected(change, "internal", "保存同步状态失败，请重试")
	}
	return result
}

func (s *Server) storedSyncResult(ctx context.Context, accountID string, mutation *store.SyncMutation) (syncResult, error) {
	var result syncResult
	if err := json.Unmarshal([]byte(mutation.Result), &result); err != nil {
		return syncResult{}, err
	}
	result.Idempotent = true
	if result.ResourceType == "memo" && result.ResourceID != "" {
		switch result.Status {
		case "applied":
			memo, err := s.Store.GetMemo(ctx, accountID, result.ResourceID, true)
			if err == nil {
				result.Resource = memo
			}
		case "conflict":
			memo, err := s.Store.GetMemo(ctx, accountID, result.ResourceID, true)
			if err == nil {
				result.ServerResource = memo
			}
		}
	}
	return result, nil
}

func (s *Server) persistSyncResult(ctx context.Context, accountID string, change syncChange, result syncResult) error {
	payload, err := json.Marshal(result)
	if err != nil {
		return fmt.Errorf("marshal sync result: %w", err)
	}
	if err := s.Store.PutSyncMutation(ctx, &store.SyncMutation{
		AccountID:    accountID,
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   result.ResourceID,
		Result:       string(payload),
	}); err != nil {
		return fmt.Errorf("persist sync mutation: %w", err)
	}
	return nil
}

func syncApplied(change syncChange, memo *store.Memo) syncResult {
	resourceID := change.ResourceID
	if memo != nil {
		resourceID = memo.ID
	}
	return syncResult{
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   resourceID,
		Status:       "applied",
		Resource:     memo,
	}
}

func syncConflict(change syncChange, memo *store.Memo) syncResult {
	serverVersion := int64(0)
	resourceID := change.ResourceID
	if memo != nil {
		serverVersion = memo.Version
		resourceID = memo.ID
	}
	return syncResult{
		MutationID:     change.MutationID,
		ResourceType:   change.ResourceType,
		ResourceID:     resourceID,
		Status:         "conflict",
		Reason:         "version_conflict",
		ClientVersion:  change.BaseVersion,
		ServerVersion:  serverVersion,
		ServerResource: memo,
	}
}

func syncRejected(change syncChange, reason, message string) syncResult {
	return syncResult{
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   change.ResourceID,
		Status:       "rejected",
		Reason:       reason,
		Message:      message,
	}
}

type syncCursor struct {
	Memo            store.SyncCursorPosition `json:"memo"`
	Attachment      store.SyncCursorPosition `json:"attachment"`
	MemoAI          store.SyncCursorPosition `json:"memoAi"`
	AskConversation store.SyncCursorPosition `json:"askConversation"`
	AskMessage      store.SyncCursorPosition `json:"askMessage"`
}

func decodeSyncCursor(raw string) syncCursor {
	if raw == "" {
		return syncCursor{}
	}
	payload, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return syncCursor{}
	}
	var cursor syncCursor
	if err := json.Unmarshal(payload, &cursor); err != nil {
		return syncCursor{}
	}
	return cursor
}

func encodeSyncCursor(cursor syncCursor) string {
	payload, err := json.Marshal(cursor)
	if err != nil {
		return ""
	}
	return base64.RawURLEncoding.EncodeToString(payload)
}

