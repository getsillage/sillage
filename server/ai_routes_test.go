package server_test

import (
	"context"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestAISettingsEncryptKeyAndHidePlaintext(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{{
			"name":        "OpenAI",
			"provider":    "openai",
			"baseUrl":     "https://api.openai.com/v1",
			"model":       "gpt-test",
			"temperature": 0.2,
			"maxTokens":   1000,
			"enabled":     true,
			"active":      true,
			"apiKey":      "mock-api-key",
		}},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("patch ai settings status = %d body=%s", res.Code, res.Body.String())
	}
	if strings.Contains(res.Body.String(), "mock-api-key") || !strings.Contains(res.Body.String(), `"hasApiKey":true`) {
		t.Fatalf("ai settings leaked key or missed hasApiKey: %s", res.Body.String())
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/settings/ai", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("get ai settings status = %d body=%s", res.Code, res.Body.String())
	}
	if strings.Contains(res.Body.String(), "mock-api-key") || !strings.Contains(res.Body.String(), `"hasApiKey":true`) {
		t.Fatalf("ai settings get leaked key or missed hasApiKey: %s", res.Body.String())
	}
}

func TestAISettingsGlobalAutoSummaryAndModels(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/settings/ai:models", map[string]any{
		"provider": "openai",
		"baseUrl":  mockAI.URL,
		"apiKey":   mockAIAPIKey,
	}, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"gpt-test"`) {
		t.Fatalf("list ai models status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"autoSummary": true,
		"profiles": []map[string]any{{
			"name":        "Mock AI",
			"provider":    "openai",
			"baseUrl":     strings.TrimRight(mockAI.URL, "/"),
			"model":       "gpt-test",
			"temperature": 0.2,
			"maxTokens":   1000,
			"enabled":     true,
			"active":      true,
			"apiKey":      mockAIAPIKey,
		}},
	}, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"autoSummary":true`) {
		t.Fatalf("patch ai settings status/body = %d %s", res.Code, res.Body.String())
	}

	memoRes := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "今天散步之后睡得更好，想让系统自动总结。",
		"entryDate": "2026-06-26",
	}, bearer(token))
	if memoRes.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", memoRes.Code, memoRes.Body.String())
	}
	memo := decodeMemoResponse(t, memoRes.Body.Bytes())
	memoID := memo["id"].(string)

	deadline := time.Now().Add(2 * time.Second)
	for {
		ai, err := srv.Store.GetMemoAI(context.Background(), memoID)
		if err == nil && ai.Summary.Valid && strings.Contains(ai.Summary.String, "mock-summary") {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("auto summary was not written for memo %s; last err=%v ai=%#v", memoID, err, ai)
		}
		time.Sleep(20 * time.Millisecond)
	}
}

func TestAISettingsTestsUnsavedProfile(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/settings/ai:test", map[string]any{
		"provider":    "openai",
		"baseUrl":     mockAI.URL,
		"model":       "gpt-test",
		"temperature": 0.2,
		"maxTokens":   1000,
		"apiKey":      mockAIAPIKey,
	}, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"model":"gpt-test"`) {
		t.Fatalf("test unsaved ai profile status/body = %d %s", res.Code, res.Body.String())
	}
}

func TestAISettingsLegacyPatchKeepsGlobalAutoSummary(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"autoSummary": true,
		"profiles": []map[string]any{{
			"name":     "Mock AI",
			"provider": "openai",
			"model":    "gpt-test",
			"enabled":  true,
			"active":   true,
		}},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("initial patch ai settings status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{{
			"name":     "Mock AI",
			"provider": "openai",
			"model":    "gpt-test",
			"enabled":  true,
			"active":   true,
		}},
	}, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"autoSummary":true`) {
		t.Fatalf("legacy patch should keep auto summary status/body = %d %s", res.Code, res.Body.String())
	}
}

func TestMemoSummaryAndKeyUnavailable(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	memoRes := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "这是一条需要总结的记录，内容足够短，所以本地总结会直接返回这一段。",
		"entryDate": "2026-06-26",
	}, bearer(token))
	if memoRes.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", memoRes.Code, memoRes.Body.String())
	}
	memo := decodeMemoResponse(t, memoRes.Body.Bytes())
	res := doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memo["id"].(string)+":generate-summary", nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"summary"`) || !strings.Contains(res.Body.String(), `"inputTokens":11`) {
		t.Fatalf("generate summary status/body = %d %s", res.Code, res.Body.String())
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync", nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"memoAi"`) || !strings.Contains(res.Body.String(), `"inputTokens":11`) {
		t.Fatalf("sync memo ai status/body = %d %s", res.Code, res.Body.String())
	}

	srv.Secrets.EncryptionSecret = "different-secret"
	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memo["id"].(string)+":generate-summary", nil, bearer(token))
	if res.Code != http.StatusBadRequest || !strings.Contains(res.Body.String(), "key_unavailable") {
		t.Fatalf("wrong secret summary status/body = %d %s", res.Code, res.Body.String())
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/settings/ai", nil, bearer(token))
	if !strings.Contains(res.Body.String(), `"keyUnavailable":true`) {
		t.Fatalf("keyUnavailable not reflected in settings: %s", res.Body.String())
	}
}
