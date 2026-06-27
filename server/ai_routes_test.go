package server_test

import (
	"net/http"
	"strings"
	"testing"
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
