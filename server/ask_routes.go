package server

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/labstack/echo/v5"

	"github.com/getsillage/sillage/store"
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

type askSetArchivedRequest struct {
	Archived bool `json:"archived"`
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
	e.GET("/api/v1/ask/conversations/:conversation", s.handleGetAskConversation)
	e.POST("/api/v1/ask/conversations/:conversation", s.handleAskConversationAction)
	e.GET("/api/v1/ask/conversations/:conversation/messages", s.handleListAskMessages)
	e.POST("/api/v1/ask/conversations/:conversation/messages", s.handleCreateAskMessage)
	e.POST("/api/v1/ask/conversations/:conversation/messages:stream", s.handleStreamAskMessage)
	e.POST("/api/v1/ask/conversations/:conversation/head", s.handleSetAskHead)
}

type askSetHeadRequest struct {
	MessageID string `json:"messageId"`
}

// handleSetAskHead points the conversation's active leaf at the given message,
// persisting which regenerated branch the user is viewing so follow-ups attach
// to it and a reload restores the same branch.
func (s *Server) handleSetAskHead(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req askSetHeadRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	if strings.TrimSpace(req.MessageID) == "" {
		return apiError(c, http.StatusBadRequest, "invalid_field", "messageId 不能为空")
	}
	if err := s.Store.SetAskConversationHead(c.Request().Context(), account.ID, c.Param("conversation"), req.MessageID); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话或消息不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "更新会话失败")
	}
	return c.NoContent(http.StatusNoContent)
}

func (s *Server) handleListAskConversations(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	query := c.QueryParam("query")
	if query == "" {
		query = c.QueryParam("q")
	}
	archived := false
	if raw := c.QueryParam("archived"); raw != "" {
		archived, err = strconv.ParseBool(raw)
		if err != nil {
			return apiError(c, http.StatusBadRequest, "invalid_field", "archived 必须是 true 或 false")
		}
	}
	conversations, err := s.listAskConversations(c.Request().Context(), account.ID, askConversationListInput{
		Limit:    parseLimit(c.QueryParam("limit"), 50),
		Query:    query,
		Archived: archived,
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取问答会话失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"conversations": askConversationDTOs(conversations)})
}

func (s *Server) handleGetAskConversation(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	conversation, err := s.getAskConversation(c.Request().Context(), account.ID, c.Param("conversation"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取问答会话失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"conversation": askConversationDTO(conversation)})
}

func (s *Server) handleAskConversationAction(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	conversationID, action, ok := strings.Cut(c.Param("conversation"), ":")
	if !ok || conversationID == "" || action != "setArchived" {
		return apiError(c, http.StatusNotFound, "not_found", "接口不存在")
	}
	var req askSetArchivedRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	conversation, err := s.setAskConversationArchived(c.Request().Context(), account.ID, conversationID, req.Archived)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "更新问答会话失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"conversation": askConversationDTO(conversation)})
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
			return apiError(c, http.StatusBadRequest, "ai_not_configured", "请先配置一个默认 AI 档案")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "生成回答失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"messages": askMessageDTOs(result.Messages)})
}

// askAnswerPrep is everything needed to call the AI for an answer, gathered
// without making the call.
type askAnswerPrep struct {
	sources  []askSourceRef
	messages []aiProviderMessage
	profile  *store.AIProfile
}

// prepareAskAnswer asks the active model to route the question, selects sources
// for record-backed modes, and builds the answer prompt + history. Callers hold
// an Ask job slot across this routing call and the following answer call.
// historyParentID is the message the new answer's question follows (i.e. the
// question's own parent); history is the ancestor chain ending there, which is
// correct for linear, branched, and regenerated turns alike.
func (s *Server) prepareAskAnswer(ctx context.Context, accountID, question, scope, sourceKind, conversationID, historyParentID string) (*askAnswerPrep, error) {
	if _, err := s.Store.GetAskConversation(ctx, accountID, conversationID); err != nil {
		return nil, err
	}
	messages, err := s.Store.ListAskMessages(ctx, conversationID)
	if err != nil {
		return nil, err
	}
	profiles, err := s.Store.ListAIProfiles(ctx, accountID)
	if err != nil {
		return nil, err
	}
	profile, err := pickActiveAIProfile(profiles)
	if err != nil {
		return nil, err
	}

	history := askAncestorHistory(messages, historyParentID)
	routerMessages := append([]aiProviderMessage{}, history...)
	routerMessages = append(routerMessages, aiProviderMessage{Role: "user", Content: askRouterUserPrompt(question)})
	routeResult, err := s.callAI(
		ctx,
		accountID,
		profile,
		askRouterSystemPrompt(),
		routerMessages,
		profile.Temperature,
		askRouterMaxTokens,
	)
	if err != nil {
		return nil, err
	}
	decision := parseAskRouteDecision(routeResult.Content, question)

	sources := make([]askSourceRef, 0)
	if decision.Mode != askRouteGeneral {
		memos, err := s.listAskCandidateMemos(ctx, accountID)
		if err != nil {
			return nil, err
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
		sources = selectAskSourceRefs(decision.SearchQuery, memos, scope)
		// In summary mode, ground the answer in each source's stored AI summary
		// (distilled) rather than its raw text, falling back to the raw excerpt.
		if isSummarySourceKind(sourceKind) {
			sources = s.applySummaryExcerpts(ctx, sources)
		}
	}

	history = append(history, aiProviderMessage{Role: "user", Content: askUserPrompt(scope, question, decision.Mode, sources)})
	return &askAnswerPrep{sources: sources, messages: history, profile: profile}, nil
}

// askAncestorHistory walks the message tree from leafID up to the root via
// parent links and returns the chain root-first as provider messages. This is
// the conversation context preceding a new answer, independent of sibling
// branches created by regeneration.
func askAncestorHistory(messages []*store.AskMessage, leafID string) []aiProviderMessage {
	byID := make(map[string]*store.AskMessage, len(messages))
	for _, message := range messages {
		byID[message.ID] = message
	}
	chain := make([]aiProviderMessage, 0, len(messages))
	seen := make(map[string]struct{})
	for id := leafID; id != ""; {
		node, ok := byID[id]
		if !ok {
			break
		}
		if _, dup := seen[id]; dup {
			break // defensive against a cyclic parent link
		}
		seen[id] = struct{}{}
		role := strings.ToLower(strings.TrimSpace(node.Role))
		if role == "user" || role == "assistant" {
			chain = append(chain, aiProviderMessage{Role: role, Content: node.Content})
		}
		if !node.ParentID.Valid {
			break
		}
		id = node.ParentID.String
	}
	// Collected leaf-first; reverse to root-first.
	for i, j := 0, len(chain)-1; i < j; i, j = i+1, j-1 {
		chain[i], chain[j] = chain[j], chain[i]
	}
	return chain
}

// handleStreamAskMessage answers a question over Server-Sent Events, delivering
// the answer token-by-token. Events: "start" (the user message + sources),
// "delta" (a text chunk), "done" (the persisted assistant message), "error".
// Pre-stream failures (auth, validation, no AI) are normal HTTP errors; once the
// stream opens, failures arrive as an "error" event. A client disconnect (stop)
// cancels the request context and the partial answer is persisted.
func (s *Server) handleStreamAskMessage(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req askMessageRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	reqCtx := c.Request().Context()
	conversation, err := s.Store.GetAskConversation(reqCtx, account.ID, c.Param("conversation"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "会话不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取会话失败")
	}

	turn, err := s.resolveAskTurn(reqCtx, conversation, askMessageInput{
		ConversationID: conversation.ID,
		Content:        req.Content,
		ParentID:       req.ParentID,
		ForkOfID:       req.ForkOfID,
	})
	if err != nil {
		status, code, message := memoHTTPStatus(err)
		return apiError(c, status, code, message)
	}

	scope := firstNonEmpty(req.ContextScope, conversation.ContextScope)
	jobDone, jobErr := s.acquireAskAIJob()
	if jobErr != nil {
		return apiError(c, http.StatusTooManyRequests, "rate_limited", "当前生成任务较多，请稍后再试")
	}
	defer jobDone()
	prep, err := s.prepareAskAnswer(reqCtx, account.ID, turn.question.Content, scope, req.SourceKind, conversation.ID, nullStringValue(turn.question.ParentID))
	if err != nil {
		status, code, message := memoHTTPStatus(err)
		return apiError(c, status, code, message)
	}

	emit := sseEmitter(c)
	// On a regenerate there is no new user message; send the existing question
	// so the client knows which turn this answers, plus a regenerate flag.
	_ = emit("start", map[string]any{
		"userMessage": askMessageDTO(turn.question),
		"sources":     prep.sources,
		"regenerate":  turn.newUser == nil,
	})

	answer := ""
	modelName := prep.profile.Model
	streamErr := error(nil)
	var builder strings.Builder
	result, callErr := s.callAIStream(reqCtx, account.ID, prep.profile, askSystemPrompt(), prep.messages, prep.profile.Temperature, prep.profile.MaxTokens, func(delta string) {
		builder.WriteString(delta)
		_ = emit("delta", map[string]any{"text": delta})
	})
	switch {
	case callErr == nil:
		answer = result.Content
	case reqCtx.Err() != nil:
		// Client stopped: keep whatever streamed so far.
		answer = strings.TrimSpace(builder.String())
	default:
		streamErr = callErr
	}

	if streamErr != nil {
		_ = emit("error", map[string]any{"message": "生成回答失败"})
		return nil
	}
	if answer == "" {
		answer = "（未生成内容）"
	}
	usedSources := citedAskSourceRefs(answer, prep.sources)

	// Persist with a fresh context so a stop (cancelled reqCtx) still saves the
	// partial answer.
	assistantMessage, err := s.Store.CreateAskMessage(context.Background(), &store.AskMessage{
		ConversationID: conversation.ID,
		Role:           "assistant",
		Content:        answer,
		ParentID:       sql.NullString{String: turn.question.ID, Valid: true},
		ForkOfID:       sql.NullString{String: turn.forkOfID, Valid: turn.forkOfID != ""},
		Status:         "complete",
		SourceRefs:     encodeAskSourceRefs(usedSources),
		Model:          modelName,
		PromptVersion:  askPromptVersion,
	})
	if err != nil {
		_ = emit("error", map[string]any{"message": "保存回答失败"})
		return nil
	}
	_ = emit("done", map[string]any{"message": askMessageDTO(assistantMessage)})
	return nil
}

// sseEmitter prepares the response for Server-Sent Events and returns a function
// that writes one event and flushes it immediately.
func sseEmitter(c *echo.Context) func(event string, data any) error {
	res := c.Response()
	header := res.Header()
	header.Set("Content-Type", "text/event-stream")
	header.Set("Cache-Control", "no-cache")
	header.Set("Connection", "keep-alive")
	header.Set("X-Accel-Buffering", "no")
	res.WriteHeader(http.StatusOK)
	flusher, _ := res.(http.Flusher)
	if flusher != nil {
		flusher.Flush()
	}
	return func(event string, data any) error {
		payload, err := json.Marshal(data)
		if err != nil {
			return err
		}
		if _, err := fmt.Fprintf(res, "event: %s\ndata: %s\n\n", event, payload); err != nil {
			return err
		}
		if flusher != nil {
			flusher.Flush()
		}
		return nil
	}
}

func (s *Server) answerFromMemos(ctx context.Context, accountID, question, scope, sourceKind, conversationID, historyParentID string) ([]askSourceRef, string, string, error) {
	jobDone, err := s.acquireAskAIJob()
	if err != nil {
		return nil, "", "", err
	}
	defer jobDone()
	prep, err := s.prepareAskAnswer(ctx, accountID, question, scope, sourceKind, conversationID, historyParentID)
	if err != nil {
		return nil, "", "", err
	}
	answer, err := s.callAI(ctx, accountID, prep.profile, askSystemPrompt(), prep.messages, prep.profile.Temperature, prep.profile.MaxTokens)
	if err != nil {
		return nil, "", "", err
	}
	return citedAskSourceRefs(answer.Content, prep.sources), answer.Content, prep.profile.Model, nil
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
			Sync:           true,
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
		score := askMemoScore(question, terms, memo)
		if score <= 0 {
			continue
		}
		scored = append(scored, scoredMemo{memo: memo, score: score})
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
			Excerpt:   askRelevantExcerpt(memo.Content, question, terms, 96),
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
	return score
}

func askRelevantExcerpt(content, question string, terms []string, limit int) string {
	content = strings.TrimSpace(content)
	if content == "" || limit <= 0 {
		return ""
	}

	candidates := append([]string{strings.TrimSpace(question)}, terms...)
	bestIndex := -1
	bestLength := 0
	for _, candidate := range candidates {
		index, length := foldedRuneIndex(content, candidate)
		if index >= 0 && (length > bestLength || length == bestLength && (bestIndex < 0 || index < bestIndex)) {
			bestIndex = index
			bestLength = length
		}
	}
	if bestIndex < 0 {
		return excerpt(content, limit)
	}

	runes := []rune(content)
	if len(runes) <= limit {
		return content
	}
	start := bestIndex - limit/3
	if start < 0 {
		start = 0
	}
	end := start + limit
	if end > len(runes) {
		end = len(runes)
		start = end - limit
		if start < 0 {
			start = 0
		}
	}
	result := string(runes[start:end])
	if start > 0 {
		result = "..." + result
	}
	if end < len(runes) {
		result += "..."
	}
	return result
}

func foldedRuneIndex(content, term string) (int, int) {
	haystack := []rune(strings.ToLower(content))
	needle := []rune(strings.ToLower(strings.TrimSpace(term)))
	if len(needle) == 0 || len(needle) > len(haystack) {
		return -1, 0
	}
	for i := 0; i+len(needle) <= len(haystack); i++ {
		matched := true
		for j := range needle {
			if haystack[i+j] != needle[j] {
				matched = false
				break
			}
		}
		if matched {
			return i, len(needle)
		}
	}
	return -1, 0
}

var askCitationPattern = regexp.MustCompile(`\[([1-9][0-9]*)\]`)

func citedAskSourceRefs(answer string, candidates []askSourceRef) []askSourceRef {
	byRank := make(map[int]askSourceRef, len(candidates))
	for _, source := range candidates {
		byRank[source.Rank] = source
	}
	seen := make(map[int]struct{}, len(candidates))
	refs := make([]askSourceRef, 0, len(candidates))
	for _, match := range askCitationPattern.FindAllStringSubmatch(answer, -1) {
		rank, err := strconv.Atoi(match[1])
		if err != nil {
			continue
		}
		source, ok := byRank[rank]
		if !ok {
			continue
		}
		if _, duplicate := seen[rank]; duplicate {
			continue
		}
		seen[rank] = struct{}{}
		refs = append(refs, source)
	}
	return refs
}

func askQueryTerms(question string) []string {
	question = strings.TrimSpace(strings.ToLower(question))
	if question == "" {
		return nil
	}

	seen := make(map[string]struct{})
	add := func(term string) {
		term = strings.TrimSpace(strings.ToLower(term))
		if utf8.RuneCountInString(term) < 2 {
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

	if strings.IndexFunc(question, func(r rune) bool { return r > unicode.MaxASCII }) >= 0 {
		runes := []rune(question)
		for i := 0; i < len(runes); i++ {
			if i+1 < len(runes) && containsNonASCII(runes[i:i+2]) {
				add(string(runes[i : i+2]))
			}
			if i+2 < len(runes) && containsNonASCII(runes[i:i+3]) {
				add(string(runes[i : i+3]))
			}
		}
	}

	terms := make([]string, 0, len(seen))
	for term := range seen {
		terms = append(terms, term)
	}
	sort.Slice(terms, func(i, j int) bool { return terms[i] < terms[j] })
	return terms
}

func containsNonASCII(runes []rune) bool {
	for _, r := range runes {
		if r > unicode.MaxASCII {
			return true
		}
	}
	return false
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
		"promptVersion":  message.PromptVersion,
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
