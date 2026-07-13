package server_test

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const (
	mockAIAPIKey         = "mock-key"
	mockAISummaryContent = "mock-summary: 这条记录提到了散步和睡眠。"
	mockAIAnswerContent  = "根据当前范围内的记录，睡眠更稳定。[1]"
	mockAIGeneralContent = "你好！很高兴见到你。"
	mockAIInputTokens    = 11
	mockAIOutputTokens   = 7
	mockAITotalTokens    = 18
)

type mockAIMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type mockAIChatRequest struct {
	Messages []mockAIMessage `json:"messages"`
	Stream   bool            `json:"stream"`
}

type mockAIAnthropicRequest struct {
	System   string          `json:"system"`
	Messages []mockAIMessage `json:"messages"`
}

func newMockAIProvider(t *testing.T) *httptest.Server {
	return newMockAIProviderWithHook(t, nil)
}

func newMockAIProviderWithHook(t *testing.T, hook func(mockAIChatRequest)) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			http.Error(w, "读取请求失败", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")

		switch {
		case strings.HasSuffix(r.URL.Path, "/models"):
			if got := r.Header.Get("Authorization"); got != "Bearer "+mockAIAPIKey {
				http.Error(w, "missing bearer key", http.StatusUnauthorized)
				return
			}
			_ = json.NewEncoder(w).Encode(map[string]any{
				"data": []map[string]any{
					{"id": "gpt-test"},
					{"id": "gpt-test-mini"},
				},
			})
		case strings.HasSuffix(r.URL.Path, "/chat/completions"):
			if got := r.Header.Get("Authorization"); got != "Bearer "+mockAIAPIKey {
				http.Error(w, "missing bearer key", http.StatusUnauthorized)
				return
			}
			var req mockAIChatRequest
			if err := json.Unmarshal(body, &req); err != nil {
				http.Error(w, "invalid json", http.StatusBadRequest)
				return
			}
			if hook != nil {
				hook(req)
			}
			if req.Stream {
				writeMockAIStream(w, mockAIContent(req.Messages))
				return
			}
			writeMockAIResponse(w, mockAIContent(req.Messages))
		case strings.HasSuffix(r.URL.Path, "/messages"):
			if got := r.Header.Get("x-api-key"); got != mockAIAPIKey {
				http.Error(w, "missing api key", http.StatusUnauthorized)
				return
			}
			if got := r.Header.Get("anthropic-version"); got == "" {
				http.Error(w, "missing anthropic version", http.StatusBadRequest)
				return
			}
			var req mockAIAnthropicRequest
			if err := json.Unmarshal(body, &req); err != nil {
				http.Error(w, "invalid json", http.StatusBadRequest)
				return
			}
			writeMockAnthropicResponse(w, mockAIContent(req.Messages))
		default:
			http.NotFound(w, r)
		}
	}))
	t.Cleanup(srv.Close)
	return srv
}

func configureMockAIProfile(t *testing.T, srv http.Handler, token, baseURL string) {
	t.Helper()
	res := doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{{
			"name":        "Mock AI",
			"provider":    "openai",
			"baseUrl":     strings.TrimRight(baseURL, "/"),
			"model":       "gpt-test",
			"temperature": 0.2,
			"maxTokens":   1000,
			"enabled":     true,
			"active":      true,
			"apiKey":      mockAIAPIKey,
		}},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("patch ai settings status = %d body=%s", res.Code, res.Body.String())
	}
}

func mockAIContent(messages []mockAIMessage) string {
	lastUser := ""
	for i := len(messages) - 1; i >= 0; i-- {
		if strings.EqualFold(strings.TrimSpace(messages[i].Role), "user") {
			lastUser = messages[i].Content
			break
		}
	}
	for _, message := range messages {
		if message.Role == "system" && strings.Contains(message.Content, "Sillage 问答路由器") {
			return mockAIRouteContent(lastUser)
		}
	}
	if strings.Contains(lastUser, "生成简洁总结") {
		return mockAISummaryContent
	}
	if strings.Contains(lastUser, "当前问题：\n你好") {
		return mockAIGeneralContent
	}
	if strings.Contains(lastUser, "当前问题：\n法国的首都") {
		return "法国的首都是巴黎。"
	}
	if strings.Contains(lastUser, "当前问题：") && strings.Contains(lastUser, "\n[]\n") {
		return "现有记录不足以判断。"
	}
	if strings.Contains(lastUser, "最早") {
		return "最早的那条记录提到了长期睡眠变化。"
	}
	if strings.Contains(lastUser, "当前问题：") {
		return mockAIAnswerContent
	}
	return "mock-ai-response"
}

func mockAIRouteContent(lastUser string) string {
	switch {
	case strings.Contains(lastUser, "你好"), strings.Contains(lastUser, "法国的首都"):
		return `{"mode":"general","searchQuery":""}`
	case strings.Contains(lastUser, "改善睡眠"):
		return `{"mode":"mixed","searchQuery":"睡眠"}`
	case strings.Contains(lastUser, "中间"):
		return `{"mode":"records","searchQuery":"中间 唯一标记"}`
	case strings.Contains(lastUser, "法语"):
		return `{"mode":"records","searchQuery":"法语 学习"}`
	case strings.Contains(lastUser, "睡眠"), strings.Contains(lastUser, "状态"), strings.Contains(lastUser, "最近"):
		return `{"mode":"records","searchQuery":"睡眠 精神 状态 变化"}`
	default:
		return `{"mode":"records","searchQuery":"记录"}`
	}
}

func writeMockAIResponse(w http.ResponseWriter, content string) {
	_ = json.NewEncoder(w).Encode(map[string]any{
		"choices": []map[string]any{{
			"message": map[string]any{"content": content},
		}},
		"usage": map[string]any{
			"input_tokens":  mockAIInputTokens,
			"output_tokens": mockAIOutputTokens,
			"total_tokens":  mockAITotalTokens,
		},
	})
}

// writeMockAIStream emits the answer as two OpenAI-style SSE chunks so tests can
// verify delta accumulation, then [DONE].
func writeMockAIStream(w http.ResponseWriter, content string) {
	w.Header().Set("Content-Type", "text/event-stream")
	runes := []rune(content)
	mid := len(runes) / 2
	chunks := []string{string(runes[:mid]), string(runes[mid:])}
	for _, chunk := range chunks {
		_, _ = w.Write([]byte("data: " + mustJSON(map[string]any{
			"choices": []map[string]any{{"delta": map[string]any{"content": chunk}}},
		}) + "\n\n"))
	}
	_, _ = w.Write([]byte("data: [DONE]\n\n"))
}

func mustJSON(v any) string {
	payload, _ := json.Marshal(v)
	return string(payload)
}

func writeMockAnthropicResponse(w http.ResponseWriter, content string) {
	_ = json.NewEncoder(w).Encode(map[string]any{
		"content": []map[string]any{{
			"type": "text",
			"text": content,
		}},
		"usage": map[string]any{
			"input_tokens":  mockAIInputTokens,
			"output_tokens": mockAIOutputTokens,
		},
	})
}
