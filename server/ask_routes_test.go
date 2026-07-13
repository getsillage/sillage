package server_test

import (
	"encoding/json"
	"net/http"
	"net/url"
	"strings"
	"testing"
)

func TestAskConversationSearchArchiveAndIncrementalSync(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	create := func(title string) map[string]any {
		t.Helper()
		res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{"title": title}, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("create conversation status = %d body=%s", res.Code, res.Body.String())
		}
		return decodeAskConversationResponse(t, res.Body.Bytes())
	}
	decodeList := func(path string) []map[string]any {
		t.Helper()
		res := doJSON(t, srv, http.MethodGet, path, nil, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("GET %s status = %d body=%s", path, res.Code, res.Body.String())
		}
		var payload struct {
			Conversations []map[string]any `json:"conversations"`
		}
		if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
			t.Fatalf("decode conversations: %v", err)
		}
		return payload.Conversations
	}

	active := create("当前工作问答")
	toArchive := create("旧项目问答")
	initialSync := doJSON(t, srv, http.MethodGet, "/api/v1/sync", nil, bearer(token))
	if initialSync.Code != http.StatusOK {
		t.Fatalf("initial sync status = %d body=%s", initialSync.Code, initialSync.Body.String())
	}
	var initial struct {
		NextCursor string `json:"nextCursor"`
	}
	if err := json.Unmarshal(initialSync.Body.Bytes(), &initial); err != nil || initial.NextCursor == "" {
		t.Fatalf("decode initial sync cursor = %q err=%v", initial.NextCursor, err)
	}

	archivePath := "/api/v1/ask/conversations/" + toArchive["id"].(string) + ":setArchived"
	res := doJSON(t, srv, http.MethodPost, archivePath, map[string]any{"archived": true}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("archive status = %d body=%s", res.Code, res.Body.String())
	}
	archivedConversation := decodeAskConversationResponse(t, res.Body.Bytes())
	if archivedConversation["archivedAt"] == nil {
		t.Fatalf("archive response = %#v, want archivedAt", archivedConversation)
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations/"+toArchive["id"].(string), nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("get archived conversation status = %d body=%s", res.Code, res.Body.String())
	}
	if got := decodeAskConversationResponse(t, res.Body.Bytes()); got["id"] != toArchive["id"] || got["archivedAt"] == nil {
		t.Fatalf("get archived conversation = %#v", got)
	}

	if got := decodeList("/api/v1/ask/conversations"); len(got) != 1 || got[0]["id"] != active["id"] {
		t.Fatalf("default conversations = %#v, want active only", got)
	}
	if got := decodeList("/api/v1/ask/conversations?query=" + url.QueryEscape("旧项目")); len(got) != 0 {
		t.Fatalf("default archived search = %#v, want empty", got)
	}
	if got := decodeList("/api/v1/ask/conversations?q=" + url.QueryEscape("旧项目") + "&archived=true&limit=1"); len(got) != 1 || got[0]["id"] != toArchive["id"] {
		t.Fatalf("legacy archived search = %#v, want archived conversation", got)
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations?archived=invalid", nil, bearer(token))
	if res.Code != http.StatusBadRequest {
		t.Fatalf("invalid archived status = %d body=%s", res.Code, res.Body.String())
	}

	incremental := doJSON(t, srv, http.MethodGet,
		"/api/v1/sync?cursor="+url.QueryEscape(initial.NextCursor), nil, bearer(token))
	if incremental.Code != http.StatusOK {
		t.Fatalf("incremental sync status = %d body=%s", incremental.Code, incremental.Body.String())
	}
	var syncPayload struct {
		AskConversations []map[string]any `json:"askConversations"`
	}
	if err := json.Unmarshal(incremental.Body.Bytes(), &syncPayload); err != nil {
		t.Fatalf("decode incremental sync: %v", err)
	}
	if len(syncPayload.AskConversations) != 1 || syncPayload.AskConversations[0]["id"] != toArchive["id"] || syncPayload.AskConversations[0]["archivedAt"] == nil {
		t.Fatalf("incremental ask conversations = %#v", syncPayload.AskConversations)
	}

	res = doJSON(t, srv, http.MethodPost, archivePath, map[string]any{"archived": false}, bearer(token))
	if res.Code != http.StatusOK || decodeAskConversationResponse(t, res.Body.Bytes())["archivedAt"] != nil {
		t.Fatalf("restore status/body = %d %s", res.Code, res.Body.String())
	}
	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/missing:setArchived", map[string]any{"archived": true}, bearer(token))
	if res.Code != http.StatusNotFound {
		t.Fatalf("archive missing status = %d body=%s", res.Code, res.Body.String())
	}
	deleted := create("已删除问答")
	if _, err := srv.Store.GetDriver().GetDB().Exec(
		"UPDATE ask_conversations SET deleted_at = updated_at + 1 WHERE id = ?", deleted["id"]); err != nil {
		t.Fatalf("soft-delete conversation: %v", err)
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations/"+deleted["id"].(string), nil, bearer(token))
	if res.Code != http.StatusNotFound {
		t.Fatalf("get deleted conversation status = %d body=%s", res.Code, res.Body.String())
	}
}

func TestAskConversationMessagesSourcesAndSync(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	createMemoForAsk(t, srv, token, "今天和朋友散步，聊到最近睡眠变好了。", "2026-06-26")
	createMemoForAsk(t, srv, token, "这周开始固定早睡，白天精神更稳定。", "2026-06-25")

	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "all",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create conversation status = %d body=%s", res.Code, res.Body.String())
	}
	conversation := decodeAskConversationResponse(t, res.Body.Bytes())
	conversationID := conversation["id"].(string)
	if conversation["contextScope"] != "all" {
		t.Fatalf("context scope = %v, want all", conversation["contextScope"])
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversationID+"/messages", map[string]any{
		"content": "我最近状态有什么变化？",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create ask message status = %d body=%s", res.Code, res.Body.String())
	}
	var messagePayload map[string][]map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &messagePayload); err != nil {
		t.Fatalf("decode ask messages: %v", err)
	}
	messages := messagePayload["messages"]
	if len(messages) != 2 {
		t.Fatalf("messages len = %d, want 2", len(messages))
	}
	if messages[0]["role"] != "user" || messages[1]["role"] != "assistant" {
		t.Fatalf("unexpected roles: %#v", messages)
	}
	if messages[0]["promptVersion"] != "" || messages[1]["promptVersion"] != "ask-answer-v2" {
		t.Fatalf("unexpected prompt versions: %#v", messages)
	}
	answer := messages[1]["content"].(string)
	if !strings.Contains(answer, "睡眠更稳定") {
		t.Fatalf("assistant answer not grounded: %s", answer)
	}
	sourceRefs := messages[1]["sourceRefs"].([]any)
	if len(sourceRefs) == 0 {
		t.Fatalf("assistant message missing source refs: %#v", messages[1])
	}
	firstRef := sourceRefs[0].(map[string]any)
	if firstRef["memoId"] == "" || firstRef["entryDate"] == "" || firstRef["excerpt"] == "" {
		t.Fatalf("invalid source ref: %#v", firstRef)
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations/"+conversationID+"/messages", nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"model":"gpt-test"`) {
		t.Fatalf("list ask messages status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("sync status = %d body=%s", res.Code, res.Body.String())
	}
	var syncPayload map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &syncPayload); err != nil {
		t.Fatalf("decode sync: %v", err)
	}
	if len(syncPayload["askConversations"].([]any)) != 1 {
		t.Fatalf("sync askConversations = %#v", syncPayload["askConversations"])
	}
	if len(syncPayload["askMessages"].([]any)) != 2 {
		t.Fatalf("sync askMessages = %#v", syncPayload["askMessages"])
	}
}

func TestAskInsufficientRecordsResponse(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "recent_30_days",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create conversation status = %d body=%s", res.Code, res.Body.String())
	}
	conversation := decodeAskConversationResponse(t, res.Body.Bytes())

	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversation["id"].(string)+"/messages", map[string]any{
		"content": "最近有什么变化？",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create ask message status = %d body=%s", res.Code, res.Body.String())
	}
	var payload map[string][]map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode ask messages: %v", err)
	}
	answer := payload["messages"][1]["content"].(string)
	if !strings.Contains(answer, "现有记录不足以判断") {
		t.Fatalf("insufficient answer = %s", answer)
	}
	if refs := payload["messages"][1]["sourceRefs"].([]any); len(refs) != 0 {
		t.Fatalf("source refs len = %d, want 0", len(refs))
	}
}

func TestAskGeneralQuestionDoesNotSendUnrelatedRecords(t *testing.T) {
	assertGeneralQuestionDoesNotSendUnrelatedRecords(
		t,
		"PRIVATE_UNRELATED_RECORD 今天散步后很早就睡了。",
		"你好",
		mockAIGeneralContent,
	)
}

func TestAskGeneralKnowledgeDoesNotMatchCommonRecordWords(t *testing.T) {
	assertGeneralQuestionDoesNotSendUnrelatedRecords(
		t,
		"PRIVATE_ENGLISH_RECORD This is a note about coffee and the morning routine.",
		"法国的首都是哪里？",
		"法国的首都是巴黎。",
	)
}

func assertGeneralQuestionDoesNotSendUnrelatedRecords(t *testing.T, unrelatedContent, question, wantAnswer string) {
	t.Helper()
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	requests := make(chan mockAIChatRequest, 2)
	mockAI := newMockAIProviderWithHook(t, func(req mockAIChatRequest) {
		requests <- req
	})
	configureMockAIProfile(t, srv, token, mockAI.URL)

	createMemoForAsk(t, srv, token, unrelatedContent, "2026-07-12")
	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "all",
	}, bearer(token))
	conversationID := decodeAskConversationResponse(t, res.Body.Bytes())["id"].(string)

	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversationID+"/messages", map[string]any{
		"content": question,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("general ask status = %d body=%s", res.Code, res.Body.String())
	}
	var payload map[string][]map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode general ask: %v", err)
	}
	answer := payload["messages"][1]
	if answer["content"] != wantAnswer {
		t.Fatalf("general answer = %v, want %q", answer["content"], wantAnswer)
	}
	if refs := answer["sourceRefs"].([]any); len(refs) != 0 {
		t.Fatalf("general answer sources = %#v, want none", refs)
	}

	foundAnswerRequest := false
	for i := 0; i < 2; i++ {
		providerRequest := <-requests
		for _, message := range providerRequest.Messages {
			if strings.Contains(message.Content, unrelatedContent) {
				t.Fatalf("general request sent unrelated record: %s", message.Content)
			}
		}
		last := providerRequest.Messages[len(providerRequest.Messages)-1].Content
		if strings.Contains(last, "候选记录来源") {
			foundAnswerRequest = true
			if !strings.Contains(last, "\n[]\n") {
				t.Fatalf("general answer request sources = %s, want empty JSON array", last)
			}
		}
	}
	if !foundAnswerRequest {
		t.Fatal("general Ask did not make an answer request")
	}
}

func TestAskAllScopeUsesOlderMemosBeyondRecentWindow(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	for i := 0; i < 5; i++ {
		createMemoForAsk(t, srv, token, strings.Repeat("新记录", 12), "2026-06-26")
	}
	createMemoForAsk(t, srv, token, "中间唯一标记 12345，这条记录提到了需要保留的中间事实。", "2025-01-01")
	for i := 0; i < 5; i++ {
		createMemoForAsk(t, srv, token, strings.Repeat("旧记录", 12), "2024-01-01")
	}

	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "all",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create conversation status = %d body=%s", res.Code, res.Body.String())
	}
	conversation := decodeAskConversationResponse(t, res.Body.Bytes())

	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversation["id"].(string)+"/messages", map[string]any{
		"content": "请问中间那条记录说了什么？",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create ask message status = %d body=%s", res.Code, res.Body.String())
	}
	var payload map[string][]map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode ask messages: %v", err)
	}
	answer := payload["messages"][1]["content"].(string)
	if !strings.Contains(answer, "中间") && !strings.Contains(answer, "记录") {
		t.Fatalf("all scope answer too weak: %s", answer)
	}
	refs := payload["messages"][1]["sourceRefs"].([]any)
	foundOldMemo := false
	for _, ref := range refs {
		item := ref.(map[string]any)
		if item["excerpt"] == "中间唯一标记 12345，这条记录提到了需要保留的中间事实。" {
			foundOldMemo = true
			break
		}
	}
	if !foundOldMemo {
		t.Fatalf("all scope source refs missed old memo: %#v", refs)
	}
}

func TestAskStreamMessageDeliversSSEEvents(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	createMemoForAsk(t, srv, token, "今天和朋友散步，聊到最近睡眠变好了。", "2026-06-26")

	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "all",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create conversation status = %d body=%s", res.Code, res.Body.String())
	}
	conversationID := decodeAskConversationResponse(t, res.Body.Bytes())["id"].(string)

	res = doJSON(t, srv, http.MethodPost,
		"/api/v1/ask/conversations/"+conversationID+"/messages:stream",
		map[string]any{"content": "我最近状态有什么变化？"}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("stream status = %d body=%s", res.Code, res.Body.String())
	}
	if ct := res.Header().Get("Content-Type"); !strings.HasPrefix(ct, "text/event-stream") {
		t.Fatalf("content-type = %q, want text/event-stream", ct)
	}

	events := parseSSE(res.Body.String())
	if len(events["start"]) != 1 {
		t.Fatalf("want exactly 1 start event, got %d", len(events["start"]))
	}
	if len(events["delta"]) < 1 {
		t.Fatalf("want at least 1 delta event, got %d", len(events["delta"]))
	}
	if len(events["done"]) != 1 {
		t.Fatalf("want exactly 1 done event, got %d", len(events["done"]))
	}

	// Deltas reassemble into the mock answer; the done event carries the
	// persisted assistant message.
	var streamed strings.Builder
	for _, d := range events["delta"] {
		var delta struct {
			Text string `json:"text"`
		}
		if err := json.Unmarshal([]byte(d), &delta); err != nil {
			t.Fatalf("decode delta: %v", err)
		}
		streamed.WriteString(delta.Text)
	}
	if streamed.String() != mockAIAnswerContent {
		t.Fatalf("streamed = %q, want %q", streamed.String(), mockAIAnswerContent)
	}

	var done struct {
		Message map[string]any `json:"message"`
	}
	if err := json.Unmarshal([]byte(events["done"][0]), &done); err != nil {
		t.Fatalf("decode done: %v", err)
	}
	if done.Message["content"] != mockAIAnswerContent {
		t.Fatalf("done content = %v, want %q", done.Message["content"], mockAIAnswerContent)
	}
	if done.Message["promptVersion"] != "ask-answer-v2" {
		t.Fatalf("done promptVersion = %v", done.Message["promptVersion"])
	}

	// The assistant message is persisted.
	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations/"+conversationID+"/messages", nil, bearer(token))
	var payload map[string][]map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode messages: %v", err)
	}
	if len(payload["messages"]) != 2 {
		t.Fatalf("persisted messages = %d, want 2", len(payload["messages"]))
	}
}

func TestAskGeneralStreamStartSourcesIsEmptyArray(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "all",
	}, bearer(token))
	conversationID := decodeAskConversationResponse(t, res.Body.Bytes())["id"].(string)

	res = doJSON(t, srv, http.MethodPost,
		"/api/v1/ask/conversations/"+conversationID+"/messages:stream",
		map[string]any{"content": "你好"}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("stream status = %d body=%s", res.Code, res.Body.String())
	}
	events := parseSSE(res.Body.String())
	if len(events["start"]) != 1 {
		t.Fatalf("want exactly 1 start event, got %d", len(events["start"]))
	}
	var start map[string]json.RawMessage
	if err := json.Unmarshal([]byte(events["start"][0]), &start); err != nil {
		t.Fatalf("decode start: %v", err)
	}
	if got := string(start["sources"]); got != "[]" {
		t.Fatalf("start sources = %s, want []", got)
	}
}

func TestAskRegenerateForksAnswerAndSetHead(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)
	createMemoForAsk(t, srv, token, "今天睡得好，精神不错。", "2026-06-26")

	res := doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations", map[string]any{
		"contextScope": "all",
	}, bearer(token))
	conversationID := decodeAskConversationResponse(t, res.Body.Bytes())["id"].(string)

	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversationID+"/messages",
		map[string]any{"content": "我最近怎么样？"}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("first message status = %d body=%s", res.Code, res.Body.String())
	}
	var first map[string][]map[string]any
	_ = json.Unmarshal(res.Body.Bytes(), &first)
	if len(first["messages"]) != 2 {
		t.Fatalf("first turn messages = %d, want 2", len(first["messages"]))
	}
	assistant := first["messages"][1]
	assistantID := assistant["id"].(string)

	// Regenerate: forkOfId set, empty content — no new user message, one new
	// assistant sibling.
	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversationID+"/messages",
		map[string]any{"forkOfId": assistantID}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("regenerate status = %d body=%s", res.Code, res.Body.String())
	}
	var regen map[string][]map[string]any
	_ = json.Unmarshal(res.Body.Bytes(), &regen)
	if len(regen["messages"]) != 1 {
		t.Fatalf("regenerate returned %d messages, want 1 (assistant only)", len(regen["messages"]))
	}
	variant := regen["messages"][0]
	if variant["role"] != "assistant" {
		t.Fatalf("regenerated role = %v, want assistant", variant["role"])
	}
	if variant["forkOfId"] != assistantID {
		t.Fatalf("forkOfId = %v, want %s", variant["forkOfId"], assistantID)
	}
	if variant["parentId"] != assistant["parentId"] {
		t.Fatalf("variant parentId = %v, want same question %v", variant["parentId"], assistant["parentId"])
	}

	// The conversation now holds 3 messages: U1, A1, A1'.
	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations/"+conversationID+"/messages", nil, bearer(token))
	var all map[string][]map[string]any
	_ = json.Unmarshal(res.Body.Bytes(), &all)
	if len(all["messages"]) != 3 {
		t.Fatalf("messages after regenerate = %d, want 3", len(all["messages"]))
	}

	// setHead switches the active leaf back to the first answer.
	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversationID+"/head",
		map[string]any{"messageId": assistantID}, bearer(token))
	if res.Code != http.StatusNoContent {
		t.Fatalf("setHead status = %d body=%s", res.Code, res.Body.String())
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/ask/conversations", nil, bearer(token))
	if !strings.Contains(res.Body.String(), `"headMessageId":"`+assistantID+`"`) {
		t.Fatalf("head not switched: %s", res.Body.String())
	}

	// setHead rejects a message from outside the conversation.
	res = doJSON(t, srv, http.MethodPost, "/api/v1/ask/conversations/"+conversationID+"/head",
		map[string]any{"messageId": "does-not-exist"}, bearer(token))
	if res.Code != http.StatusNotFound {
		t.Fatalf("setHead bad id status = %d, want 404", res.Code)
	}
}

// parseSSE groups an SSE response body's data payloads by event name.
func parseSSE(body string) map[string][]string {
	out := map[string][]string{}
	for _, block := range strings.Split(body, "\n\n") {
		event := "message"
		data := ""
		for _, line := range strings.Split(block, "\n") {
			if strings.HasPrefix(line, "event:") {
				event = strings.TrimSpace(strings.TrimPrefix(line, "event:"))
			} else if strings.HasPrefix(line, "data:") {
				data += strings.TrimSpace(strings.TrimPrefix(line, "data:"))
			}
		}
		if data != "" {
			out[event] = append(out[event], data)
		}
	}
	return out
}

func createMemoForAsk(t *testing.T, srv interface {
	ServeHTTP(http.ResponseWriter, *http.Request)
}, token, content, entryDate string) {
	t.Helper()
	res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   content,
		"entryDate": entryDate,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
	}
}

func decodeAskConversationResponse(t *testing.T, body []byte) map[string]any {
	t.Helper()
	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("decode ask conversation response: %v", err)
	}
	conversation, ok := payload["conversation"].(map[string]any)
	if !ok {
		t.Fatalf("response missing conversation: %#v", payload)
	}
	return conversation
}
