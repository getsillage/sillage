package server

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestStreamOpenAICompatibleAI(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			t.Errorf("path = %s, want /chat/completions", r.URL.Path)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(
			"data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n" +
				"data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}\n\n" +
				"data: [DONE]\n\n",
		))
	}))
	defer srv.Close()

	var deltas []string
	result, err := streamOpenAICompatibleAI(
		context.Background(), srv.URL, "key", "gpt", "system",
		[]aiProviderMessage{{Role: "user", Content: "hi"}}, 0.3, 100,
		func(d string) { deltas = append(deltas, d) },
	)
	if err != nil {
		t.Fatalf("streamOpenAICompatibleAI() error = %v", err)
	}
	if result.Content != "你好，世界" {
		t.Fatalf("content = %q, want 你好，世界", result.Content)
	}
	if strings.Join(deltas, "|") != "你好|，世界" {
		t.Fatalf("deltas = %v, want [你好 ，世界]", deltas)
	}
}

func TestStreamAnthropicAI(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/messages" {
			t.Errorf("path = %s, want /messages", r.URL.Path)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(
			"event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":5}}}\n\n" +
				"event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"嗯\"}}\n\n" +
				"event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"，好\"}}\n\n" +
				"event: message_delta\ndata: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":3}}\n\n" +
				"event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n",
		))
	}))
	defer srv.Close()

	var deltas []string
	result, err := streamAnthropicAI(
		context.Background(), srv.URL, "key", "claude", "system",
		[]aiProviderMessage{{Role: "user", Content: "hi"}}, 0.3, 100,
		func(d string) { deltas = append(deltas, d) },
	)
	if err != nil {
		t.Fatalf("streamAnthropicAI() error = %v", err)
	}
	if result.Content != "嗯，好" {
		t.Fatalf("content = %q, want 嗯，好", result.Content)
	}
	if result.InputTokens != 5 || result.OutputTokens != 3 || result.TotalTokens != 8 {
		t.Fatalf("usage = in:%d out:%d total:%d, want 5/3/8",
			result.InputTokens, result.OutputTokens, result.TotalTokens)
	}
	if strings.Join(deltas, "|") != "嗯|，好" {
		t.Fatalf("deltas = %v", deltas)
	}
}

func TestStreamProviderStatusError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"error":"bad key"}`))
	}))
	defer srv.Close()

	_, err := streamOpenAICompatibleAI(
		context.Background(), srv.URL, "key", "gpt", "",
		[]aiProviderMessage{{Role: "user", Content: "hi"}}, 0, 0,
		func(string) {},
	)
	if err == nil {
		t.Fatal("expected error on non-2xx status, got nil")
	}
	if !strings.Contains(err.Error(), "401") {
		t.Fatalf("error = %v, want status 401", err)
	}
}
