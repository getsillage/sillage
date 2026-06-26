package server

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/labstack/echo/v5"

	"github.com/miofelix/sillage/store"
)

type syncPushRequest struct {
	Changes []syncChange `json:"changes"`
}

type syncChange struct {
	MutationID     string          `json:"mutationId"`
	ResourceType   string          `json:"resourceType"`
	ResourceID     string          `json:"resourceId"`
	Action         string          `json:"action"`
	BaseVersion    int64           `json:"baseVersion"`
	LocalChangedAt string          `json:"localChangedAt"`
	Memo           json.RawMessage `json:"memo"`
}

type syncMemoPayload struct {
	ID        string `json:"id"`
	Content   string `json:"content"`
	EntryDate string `json:"entryDate"`
	Pinned    *bool  `json:"pinned"`
	Archived  *bool  `json:"archived"`
}

func (s *Server) handleSyncPull(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	cursor := decodeSyncCursor(c.QueryParam("cursor"))
	limit := parseLimit(c.QueryParam("limit"), 200)
	memos, err := s.Store.ListMemos(c.Request().Context(), &store.ListMemoOptions{
		AccountID:      account.ID,
		Limit:          limit + 1,
		IncludeDeleted: true,
		UpdatedAfter:   cursor.Memo.UpdatedAt,
		UpdatedAfterID: cursor.Memo.ID,
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "同步读取失败")
	}
	attachments, err := s.Store.ListAttachments(c.Request().Context(), &store.ListAttachmentOptions{
		AccountID:      account.ID,
		Limit:          limit + 1,
		IncludeDeleted: true,
		UpdatedAfter:   cursor.Attachment.UpdatedAt,
		UpdatedAfterID: cursor.Attachment.ID,
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "同步读取失败")
	}
	memoHasMore := len(memos) > limit
	if memoHasMore {
		memos = memos[:limit]
	}
	attachmentHasMore := len(attachments) > limit
	if attachmentHasMore {
		attachments = attachments[:limit]
	}
	if len(memos) > 0 {
		last := memos[len(memos)-1]
		cursor.Memo = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(attachments) > 0 {
		last := attachments[len(attachments)-1]
		cursor.Attachment = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	return c.JSON(http.StatusOK, map[string]any{
		"memos":       memoDTOs(memos),
		"attachments": attachmentDTOs(attachments),
		"cursor":      encodeSyncCursor(cursor),
		"nextCursor":  encodeSyncCursor(cursor),
		"hasMore":     memoHasMore || attachmentHasMore,
	})
}

func (s *Server) handleSyncPush(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req syncPushRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	if len(req.Changes) > 200 {
		return apiError(c, http.StatusBadRequest, "too_many_changes", "一次最多提交 200 条变更")
	}
	results := make([]map[string]any, 0, len(req.Changes))
	for _, change := range req.Changes {
		results = append(results, s.applySyncChange(c, account.ID, change))
	}
	return c.JSON(http.StatusOK, map[string]any{"results": results})
}

func (s *Server) applySyncChange(c *echo.Context, accountID string, change syncChange) map[string]any {
	if change.MutationID == "" {
		return syncRejected(change, "missing_mutation_id", "mutationId 不能为空")
	}
	if previous, ok, err := s.Store.GetSyncMutation(c.Request().Context(), accountID, change.MutationID); err == nil && ok {
		var result map[string]any
		if json.Unmarshal([]byte(previous.Result), &result) == nil {
			result["idempotent"] = true
			return result
		}
	} else if err != nil {
		return syncRejected(change, "internal", "读取幂等状态失败")
	}
	if change.ResourceType != "memo" {
		result := syncRejected(change, "unsupported_resource", "暂不支持该资源类型")
		s.persistSyncResult(c, accountID, change, result)
		return result
	}

	var payload syncMemoPayload
	if len(change.Memo) > 0 {
		if err := json.Unmarshal(change.Memo, &payload); err != nil {
			result := syncRejected(change, "invalid_field", "memo payload 格式不正确")
			s.persistSyncResult(c, accountID, change, result)
			return result
		}
	}
	if payload.ID == "" {
		payload.ID = change.ResourceID
	}

	var memo *store.Memo
	var err error
	switch change.Action {
	case "create":
		if validateErr := validateMemoFields(payload.Content, payload.EntryDate); validateErr != nil {
			result := syncRejected(change, "invalid_field", validateErr.Error())
			s.persistSyncResult(c, accountID, change, result)
			return result
		}
		memo, err = s.Store.CreateMemo(c.Request().Context(), &store.CreateMemo{
			ID:        payload.ID,
			CreatorID: accountID,
			Content:   payload.Content,
			EntryDate: payload.EntryDate,
		})
	case "update":
		memo, err = s.Store.UpdateMemo(c.Request().Context(), &store.UpdateMemo{
			ID:              payload.ID,
			CreatorID:       accountID,
			ExpectedVersion: change.BaseVersion,
			Content:         &payload.Content,
			EntryDate:       &payload.EntryDate,
			Pinned:          payload.Pinned,
			Archived:        payload.Archived,
		})
	case "delete":
		deleted := true
		memo, err = s.Store.UpdateMemo(c.Request().Context(), &store.UpdateMemo{
			ID:              payload.ID,
			CreatorID:       accountID,
			ExpectedVersion: change.BaseVersion,
			Deleted:         &deleted,
		})
	default:
		result := syncRejected(change, "unsupported_action", "暂不支持该同步动作")
		s.persistSyncResult(c, accountID, change, result)
		return result
	}

	var conflict *store.MemoConflictError
	var result map[string]any
	if errors.As(err, &conflict) {
		result = map[string]any{
			"mutationId":     change.MutationID,
			"resourceType":   change.ResourceType,
			"resourceId":     change.ResourceID,
			"status":         "conflict",
			"reason":         "version_conflict",
			"clientVersion":  change.BaseVersion,
			"serverVersion":  conflict.ServerMemo.Version,
			"serverResource": memoDTO(conflict.ServerMemo),
		}
	} else if err != nil {
		result = syncRejected(change, "rejected", err.Error())
	} else {
		result = syncApplied(change, memo)
	}
	s.persistSyncResult(c, accountID, change, result)
	return result
}

func (s *Server) persistSyncResult(c *echo.Context, accountID string, change syncChange, result map[string]any) {
	payload, err := json.Marshal(result)
	if err != nil {
		return
	}
	_ = s.Store.PutSyncMutation(c.Request().Context(), &store.SyncMutation{
		AccountID:    accountID,
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   change.ResourceID,
		Result:       string(payload),
	})
}

func syncApplied(change syncChange, memo *store.Memo) map[string]any {
	return map[string]any{
		"mutationId":   change.MutationID,
		"resourceType": change.ResourceType,
		"resourceId":   memo.ID,
		"status":       "applied",
		"resource":     memoDTO(memo),
	}
}

func syncRejected(change syncChange, reason, message string) map[string]any {
	return map[string]any{
		"mutationId":   change.MutationID,
		"resourceType": change.ResourceType,
		"resourceId":   change.ResourceID,
		"status":       "rejected",
		"reason":       reason,
		"message":      message,
	}
}
