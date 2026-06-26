package server

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/miofelix/sillage/store"
)

type askCreateConversationRequest struct {
	Title        string `json:"title"`
	ContextScope string `json:"contextScope"`
}

type askMessageRequest struct {
	Content      string `json:"content"`
	ContextScope string `json:"contextScope"`
	ParentID     string `json:"parentId"`
	ForkOfID     string `json:"forkOfId"`
}

type askSourceRef struct {
	MemoID    string `json:"memoId"`
	EntryDate string `json:"entryDate"`
	Excerpt   string `json:"excerpt"`
	Rank      int    `json:"rank"`
}

func (s *Server) registerAskRoutes(e *echo.Echo) {
	e.GET("/api/v1/ask/conversations", s.handleListAskConversations)
	e.POST("/api/v1/ask/conversations", s.handleCreateAskConversation)
	e.GET("/api/v1/ask/conversations/:conversation/messages", s.handleListAskMessages)
	e.POST("/api/v1/ask/conversations/:conversation/messages", s.handleCreateAskMessage)
}

func (s *Server) handleListAskConversations(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	conversations, err := s.listAskConversations(c.Request().Context(), account.ID, parseLimit(c.QueryParam("limit"), 50))
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取问答会话失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"conversations": askConversationDTOs(conversations)})
}

func (s *Server) handleCreateAskConversation(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req askCreateConversationRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	conversation, err := s.createAskConversation(c.Request().Context(), account.ID, askConversationInput{
		Title:        req.Title,
		ContextScope: req.ContextScope,
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "创建问答会话失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"conversation": askConversationDTO(conversation)})
}

func (s *Server) handleListAskMessages(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	messages, err := s.listAskMessages(c.Request().Context(), account.ID, c.Param("conversation"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取消息失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"messages": askMessageDTOs(messages)})
}

func (s *Server) handleCreateAskMessage(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req askMessageRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	result, err := s.createAskMessage(c.Request().Context(), account.ID, askMessageInput{
		ConversationID: c.Param("conversation"),
		Content:        req.Content,
		ContextScope:   req.ContextScope,
		ParentID:       req.ParentID,
		ForkOfID:       req.ForkOfID,
	})
	if err != nil {
		if errors.Is(err, errValidation) {
			return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
		}
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "生成回答失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"messages": askMessageDTOs(result.Messages)})
}

func (s *Server) answerFromMemos(ctx context.Context, accountID, question, scope string) ([]askSourceRef, string) {
	memos, err := s.Store.ListRecentMemos(ctx, accountID, 30)
	if err != nil || len(memos) == 0 {
		return nil, "现有记录不足以判断。可以先写下一些记录，或缩小问题范围后再问。"
	}
	cutoff := time.Now().AddDate(0, 0, -30).Format("2006-01-02")
	if scope == "recent_7_days" {
		cutoff = time.Now().AddDate(0, 0, -7).Format("2006-01-02")
	}
	var sources []askSourceRef
	for _, memo := range memos {
		if scope != "all" && memo.EntryDate < cutoff {
			continue
		}
		sources = append(sources, askSourceRef{
			MemoID:    memo.ID,
			EntryDate: memo.EntryDate,
			Excerpt:   excerpt(memo.Content, 96),
			Rank:      len(sources) + 1,
		})
		if len(sources) >= 5 {
			break
		}
	}
	if len(sources) == 0 {
		return nil, "现有记录不足以判断。当前范围内没有可引用的记录。"
	}
	var builder strings.Builder
	builder.WriteString("根据当前范围内的记录，可以先看这些来源：\n")
	for _, source := range sources {
		builder.WriteString("- ")
		builder.WriteString(source.EntryDate)
		builder.WriteString("：")
		builder.WriteString(source.Excerpt)
		builder.WriteString("\n")
	}
	builder.WriteString("\n这只是基于记录的整理，不会脱离来源做判断。")
	_ = question
	return sources, builder.String()
}

func askConversationDTOs(conversations []*store.AskConversation) []map[string]any {
	dtos := make([]map[string]any, 0, len(conversations))
	for _, conversation := range conversations {
		dtos = append(dtos, askConversationDTO(conversation))
	}
	return dtos
}

func askConversationDTO(conversation *store.AskConversation) map[string]any {
	return map[string]any{
		"id":            conversation.ID,
		"title":         conversation.Title,
		"status":        conversation.Status,
		"contextScope":  conversation.ContextScope,
		"headMessageId": optionalString(conversation.HeadMessageID),
		"pinnedAt":      optionalTime(conversation.PinnedAt),
		"archivedAt":    optionalTime(conversation.ArchivedAt),
		"createdAt":     time.UnixMilli(conversation.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":     time.UnixMilli(conversation.UpdatedAt).UTC().Format(time.RFC3339),
		"deletedAt":     optionalTime(conversation.DeletedAt),
	}
}

func askMessageDTOs(messages []*store.AskMessage) []map[string]any {
	dtos := make([]map[string]any, 0, len(messages))
	for _, message := range messages {
		dtos = append(dtos, askMessageDTO(message))
	}
	return dtos
}

func askMessageDTO(message *store.AskMessage) map[string]any {
	return map[string]any{
		"id":             message.ID,
		"conversationId": message.ConversationID,
		"role":           message.Role,
		"content":        message.Content,
		"parentId":       optionalString(message.ParentID),
		"forkOfId":       optionalString(message.ForkOfID),
		"status":         message.Status,
		"sourceRefs":     decodeAskSourceRefs(message.SourceRefs),
		"model":          message.Model,
		"createdAt":      time.UnixMilli(message.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":      time.UnixMilli(message.UpdatedAt).UTC().Format(time.RFC3339),
		"deletedAt":      optionalTime(message.DeletedAt),
	}
}

func decodeAskSourceRefs(raw string) any {
	var refs []askSourceRef
	if err := json.Unmarshal([]byte(raw), &refs); err != nil {
		return []askSourceRef{}
	}
	if refs == nil {
		return []askSourceRef{}
	}
	return refs
}

func encodeAskSourceRefs(refs []askSourceRef) string {
	if refs == nil {
		refs = []askSourceRef{}
	}
	payload, err := json.Marshal(refs)
	if err != nil {
		return "[]"
	}
	return string(payload)
}

func excerpt(content string, limit int) string {
	content = strings.TrimSpace(content)
	if len(content) <= limit {
		return content
	}
	return content[:limit] + "..."
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
