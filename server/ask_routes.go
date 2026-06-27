package server

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"sort"
	"strings"
	"time"
	"unicode"

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
	SourceKind   string `json:"sourceKind"`
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
		SourceKind:     req.SourceKind,
	})
	if err != nil {
		if errors.Is(err, errValidation) {
			return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
		}
		if errors.Is(err, errAIOverloaded) {
			return apiError(c, http.StatusTooManyRequests, "rate_limited", "当前生成任务较多，请稍后再试")
		}
		if errors.Is(err, errAIKeyUnavailable) {
			return apiError(c, http.StatusBadRequest, "key_unavailable", "当前 AI API Key 无法解密，请重新保存")
		}
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话不存在")
		}
		if errors.Is(err, errAINotConfigured) {
			return apiError(c, http.StatusBadRequest, "ai_not_configured", "请先配置并启用一个 AI 档案")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "生成回答失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"messages": askMessageDTOs(result.Messages)})
}

func (s *Server) answerFromMemos(ctx context.Context, accountID, question, scope, sourceKind string, conversationID string) ([]askSourceRef, string, string, error) {
	jobDone, err := s.acquireAskAIJob()
	if err != nil {
		return nil, "", "", err
	}
	defer jobDone()

	memos, err := s.listAskCandidateMemos(ctx, accountID)
	if err != nil {
		return nil, "", "", err
	}
	if len(memos) == 0 {
		return nil, "现有记录不足以判断。可以先写下一些记录，或缩小问题范围后再问。", "", nil
	}
	sort.Slice(memos, func(i, j int) bool {
		if memos[i].EntryDate != memos[j].EntryDate {
			return memos[i].EntryDate > memos[j].EntryDate
		}
		if memos[i].CreatedAt != memos[j].CreatedAt {
			return memos[i].CreatedAt > memos[j].CreatedAt
		}
		return memos[i].ID > memos[j].ID
	})
	sources := selectAskSourceRefs(question, memos, scope)
	if len(sources) == 0 {
		return nil, "现有记录不足以判断。当前范围内没有可引用的记录。", "", nil
	}
	// In summary mode, ground the answer in each source's stored AI summary
	// (distilled) rather than its raw text, falling back to the raw excerpt.
	if isSummarySourceKind(sourceKind) {
		sources = s.applySummaryExcerpts(ctx, sources)
	}

	_, err = s.Store.GetAskConversation(ctx, accountID, conversationID)
	if err != nil {
		return nil, "", "", err
	}
	messages, err := s.Store.ListAskMessages(ctx, conversationID)
	if err != nil {
		return nil, "", "", err
	}
	profiles, err := s.Store.ListAIProfiles(ctx, accountID)
	if err != nil {
		return nil, "", "", err
	}
	profile, err := pickActiveAIProfile(profiles)
	if err != nil {
		return nil, "", "", err
	}

	history := make([]aiProviderMessage, 0, len(messages))
	for _, message := range messages {
		role := strings.ToLower(strings.TrimSpace(message.Role))
		if role != "user" && role != "assistant" {
			continue
		}
		history = append(history, aiProviderMessage{Role: role, Content: message.Content})
	}
	finalPrompt := askUserPrompt(scope, question, sources)
	if len(history) > 0 {
		history = history[:len(history)-1]
	}
	answer, err := s.callAI(ctx, accountID, profile, askSystemPrompt(), append(history, aiProviderMessage{
		Role:    "user",
		Content: finalPrompt,
	}), profile.Temperature, profile.MaxTokens)
	if err != nil {
		return nil, "", "", err
	}
	return sources, answer.Content, profile.Model, nil
}

// isSummarySourceKind reports whether the answer should be grounded in stored
// summaries rather than raw memo text. "records" (or empty) means raw text.
func isSummarySourceKind(kind string) bool {
	switch kind {
	case "summaries", "memo_summary":
		return true
	default:
		return false
	}
}

// applySummaryExcerpts replaces each source's excerpt with the memo's stored AI
// summary when one exists; memos without a summary keep their raw excerpt.
func (s *Server) applySummaryExcerpts(ctx context.Context, sources []askSourceRef) []askSourceRef {
	out := make([]askSourceRef, 0, len(sources))
	for _, source := range sources {
		ref := source
		if ai, err := s.Store.GetMemoAI(ctx, source.MemoID); err == nil && ai.Summary.Valid {
			if summary := strings.TrimSpace(ai.Summary.String); summary != "" {
				ref.Excerpt = excerpt(summary, 200)
			}
		}
		out = append(out, ref)
	}
	return out
}

func (s *Server) listAskCandidateMemos(ctx context.Context, accountID string) ([]*store.Memo, error) {
	const pageSize = 200

	memos := make([]*store.Memo, 0, pageSize)
	var updatedAfter int64
	var updatedAfterID string
	for {
		page, err := s.Store.ListMemos(ctx, &store.ListMemoOptions{
			AccountID:      accountID,
			Limit:          pageSize,
			UpdatedAfter:   updatedAfter,
			UpdatedAfterID: updatedAfterID,
		})
		if err != nil {
			return nil, err
		}
		memos = append(memos, page...)
		if len(page) < pageSize {
			return memos, nil
		}
		last := page[len(page)-1]
		updatedAfter = last.UpdatedAt
		updatedAfterID = last.ID
	}
}

func selectAskSourceRefs(question string, memos []*store.Memo, scope string) []askSourceRef {
	const sourceLimit = 5

	terms := askQueryTerms(question)
	cutoff := ""
	if scope != "all" {
		cutoff = askScopeCutoff(scope)
	}

	type scoredMemo struct {
		memo  *store.Memo
		score int
	}

	scored := make([]scoredMemo, 0, len(memos))
	for _, memo := range memos {
		if memo == nil || memo.ID == "" {
			continue
		}
		if cutoff != "" && memo.EntryDate < cutoff {
			continue
		}
		scored = append(scored, scoredMemo{
			memo:  memo,
			score: askMemoScore(question, terms, memo),
		})
	}

	sort.Slice(scored, func(i, j int) bool {
		if scored[i].score != scored[j].score {
			return scored[i].score > scored[j].score
		}
		if scored[i].memo.EntryDate != scored[j].memo.EntryDate {
			return scored[i].memo.EntryDate > scored[j].memo.EntryDate
		}
		if scored[i].memo.CreatedAt != scored[j].memo.CreatedAt {
			return scored[i].memo.CreatedAt > scored[j].memo.CreatedAt
		}
		return scored[i].memo.ID > scored[j].memo.ID
	})

	limit := sourceLimit
	if len(scored) < limit {
		limit = len(scored)
	}
	sources := make([]askSourceRef, 0, limit)
	for i := 0; i < limit; i++ {
		memo := scored[i].memo
		sources = append(sources, askSourceRef{
			MemoID:    memo.ID,
			EntryDate: memo.EntryDate,
			Excerpt:   excerpt(memo.Content, 96),
			Rank:      i + 1,
		})
	}
	return sources
}

func askScopeCutoff(scope string) string {
	switch scope {
	case "recent_7_days":
		return time.Now().AddDate(0, 0, -7).Format("2006-01-02")
	default:
		return time.Now().AddDate(0, 0, -30).Format("2006-01-02")
	}
}

func askMemoScore(question string, terms []string, memo *store.Memo) int {
	content := strings.ToLower(strings.TrimSpace(memo.Content))
	if content == "" {
		return 0
	}
	score := 0
	if q := strings.ToLower(strings.TrimSpace(question)); q != "" && strings.Contains(content, q) {
		score += 100
	}
	for _, term := range terms {
		if term == "" {
			continue
		}
		if strings.Contains(content, term) {
			score += 10 + len(term)
		}
	}
	if strings.Contains(content, memo.EntryDate) {
		score += 5
	}
	return score
}

func askQueryTerms(question string) []string {
	question = strings.TrimSpace(strings.ToLower(question))
	if question == "" {
		return nil
	}

	seen := make(map[string]struct{})
	add := func(term string) {
		term = strings.TrimSpace(strings.ToLower(term))
		if len(term) < 2 {
			return
		}
		if _, ok := seen[term]; ok {
			return
		}
		seen[term] = struct{}{}
	}

	for _, field := range strings.FieldsFunc(question, func(r rune) bool {
		return unicode.IsSpace(r) || strings.ContainsRune("，。！？；：、,.!?;:()[]{}<>\"'`", r)
	}) {
		add(field)
	}

	runes := []rune(question)
	for i := 0; i < len(runes); i++ {
		if i+1 < len(runes) {
			add(string(runes[i : i+2]))
		}
		if i+2 < len(runes) {
			add(string(runes[i : i+3]))
		}
	}

	terms := make([]string, 0, len(seen))
	for term := range seen {
		terms = append(terms, term)
	}
	sort.Slice(terms, func(i, j int) bool { return terms[i] < terms[j] })
	return terms
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
	runes := []rune(content)
	if len(runes) <= limit {
		return content
	}
	return string(runes[:limit]) + "..."
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
