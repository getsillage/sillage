package server_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
)

func TestAskConversationMessagesSourcesAndSync(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

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
	answer := messages[1]["content"].(string)
	if !strings.Contains(answer, "根据当前范围内的记录") || !strings.Contains(answer, "睡眠") {
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
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), "local-grounded-answer") {
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
