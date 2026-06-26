package server

import (
	"net/http"

	"github.com/labstack/echo/v5"
)

func (s *Server) handleSyncPull(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	result, err := s.pullSync(
		c.Request().Context(),
		account.ID,
		c.QueryParam("cursor"),
		parseLimit(c.QueryParam("limit"), 200),
	)
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "同步读取失败")
	}
	return c.JSON(http.StatusOK, map[string]any{
		"memos":            memoDTOs(result.Memos),
		"attachments":      attachmentDTOs(result.Attachments),
		"memoAi":           memoAIDTOs(result.MemoAI),
		"askConversations": askConversationDTOs(result.AskConversations),
		"askMessages":      askMessageDTOs(result.AskMessages),
		"cursor":           result.Cursor,
		"nextCursor":       result.NextCursor,
		"hasMore":          result.HasMore,
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
	results, err := s.pushSync(c.Request().Context(), account.ID, req.Changes)
	if err != nil {
		if err == errTooManyChanges {
			return apiError(c, http.StatusBadRequest, "too_many_changes", "一次最多提交 200 条变更")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "同步写入失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"results": syncResultDTOs(results)})
}

func syncResultDTOs(results []syncResult) []map[string]any {
	dtos := make([]map[string]any, 0, len(results))
	for _, result := range results {
		dtos = append(dtos, syncResultDTO(result))
	}
	return dtos
}

func syncResultDTO(result syncResult) map[string]any {
	dto := map[string]any{
		"mutationId":   result.MutationID,
		"resourceType": result.ResourceType,
		"resourceId":   result.ResourceID,
		"status":       result.Status,
	}
	if result.Reason != "" {
		dto["reason"] = result.Reason
	}
	if result.Message != "" {
		dto["message"] = result.Message
	}
	if result.Idempotent {
		dto["idempotent"] = true
	}
	if result.Resource != nil {
		dto["resource"] = memoDTO(result.Resource)
	}
	if result.ServerResource != nil {
		dto["serverResource"] = memoDTO(result.ServerResource)
	}
	if result.ClientVersion > 0 {
		dto["clientVersion"] = result.ClientVersion
	}
	if result.ServerVersion > 0 {
		dto["serverVersion"] = result.ServerVersion
	}
	return dto
}
