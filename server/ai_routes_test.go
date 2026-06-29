package server_test

import (
	"context"
	"encoding/json"
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

func TestAISettingsAllowsZeroTemperatureAndDefaultsWhenOmitted(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{
			{
				"name":        "Deterministic",
				"provider":    "openai",
				"baseUrl":     "https://api.openai.com/v1",
				"model":       "gpt-test",
				"temperature": 0,
				"maxTokens":   1000,
				"enabled":     true,
				"active":      true,
				"apiKey":      "mock-api-key",
			},
			{
				// temperature omitted -> server default applies.
				"name":     "Defaulted",
				"provider": "openai",
				"baseUrl":  "https://api.openai.com/v1",
				"model":    "gpt-test",
				"enabled":  true,
				"active":   false,
				"apiKey":   "mock-api-key",
			},
		},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("patch ai settings status = %d body=%s", res.Code, res.Body.String())
	}
	var payload struct {
		Profiles []struct {
			Name        string  `json:"name"`
			Temperature float64 `json:"temperature"`
		} `json:"profiles"`
	}
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode ai settings: %v", err)
	}
	if len(payload.Profiles) != 2 {
		t.Fatalf("expected 2 profiles, got %d (%s)", len(payload.Profiles), res.Body.String())
	}
	if payload.Profiles[0].Temperature != 0 {
		t.Fatalf("explicit temperature 0 was not preserved, got %v", payload.Profiles[0].Temperature)
	}
	if payload.Profiles[1].Temperature != 0.3 {
		t.Fatalf("omitted temperature should default to 0.3, got %v", payload.Profiles[1].Temperature)
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

func TestAISettingsPatchDeletesOmittedProfiles(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{
			{
				"name":      "Keep",
				"provider":  "openai",
				"model":     "gpt-keep",
				"enabled":   true,
				"active":    true,
				"maxTokens": 1000,
			},
			{
				"name":      "Delete",
				"provider":  "openai",
				"model":     "gpt-delete",
				"enabled":   true,
				"maxTokens": 1000,
			},
		},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("initial patch ai settings status/body = %d %s", res.Code, res.Body.String())
	}
	var payload map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode initial ai settings: %v", err)
	}
	profiles, ok := payload["profiles"].([]any)
	if !ok || len(profiles) != 2 {
		t.Fatalf("initial profiles = %#v, want 2 profiles", payload["profiles"])
	}
	keep, ok := profiles[0].(map[string]any)
	if !ok {
		t.Fatalf("first profile has unexpected shape: %#v", profiles[0])
	}
	keepID, ok := keep["id"].(string)
	if !ok || keepID == "" {
		t.Fatalf("first profile missing id: %#v", keep)
	}
	deleted, ok := profiles[1].(map[string]any)
	if !ok {
		t.Fatalf("second profile has unexpected shape: %#v", profiles[1])
	}
	deletedID, ok := deleted["id"].(string)
	if !ok || deletedID == "" {
		t.Fatalf("second profile missing id: %#v", deleted)
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{{
			"id":        keepID,
			"name":      "Keep",
			"provider":  "openai",
			"model":     "gpt-keep",
			"enabled":   true,
			"active":    true,
			"maxTokens": 1000,
		}},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("delete patch ai settings status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/settings/ai", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("get ai settings status/body = %d %s", res.Code, res.Body.String())
	}
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode final ai settings: %v", err)
	}
	profiles, ok = payload["profiles"].([]any)
	if !ok || len(profiles) != 1 {
		t.Fatalf("profiles after delete = %#v, want only kept profile", payload["profiles"])
	}
	got, ok := profiles[0].(map[string]any)
	if !ok || got["id"] != keepID || got["name"] != "Keep" {
		t.Fatalf("unexpected profile after delete: %#v", profiles[0])
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{
			{
				"id":        keepID,
				"name":      "Keep",
				"provider":  "openai",
				"model":     "gpt-keep",
				"enabled":   true,
				"active":    true,
				"maxTokens": 1000,
			},
			{
				"id":        deletedID,
				"name":      "Delete",
				"provider":  "openai",
				"model":     "gpt-delete",
				"enabled":   true,
				"maxTokens": 1000,
			},
		},
	}, bearer(token))
	if res.Code != http.StatusBadRequest || !strings.Contains(res.Body.String(), "已被删除") {
		t.Fatalf("patch with deleted profile id status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{
			{
				"id":        keepID,
				"name":      "Keep",
				"provider":  "openai",
				"model":     "gpt-keep",
				"enabled":   true,
				"active":    true,
				"maxTokens": 1000,
			},
			{
				"id":        "client-generated-profile-id",
				"name":      "Client",
				"provider":  "openai",
				"model":     "gpt-client",
				"enabled":   true,
				"maxTokens": 1000,
			},
		},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("patch with new client-generated id status/body = %d %s", res.Code, res.Body.String())
	}
}

func TestAISettingsPatchKeepsOneEnabledDefaultProfile(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPatch, "/api/v1/settings/ai", map[string]any{
		"profiles": []map[string]any{
			{
				"name":      "First",
				"provider":  "openai",
				"model":     "gpt-first",
				"enabled":   false,
				"active":    true,
				"maxTokens": 1000,
			},
			{
				"name":      "Second",
				"provider":  "openai",
				"model":     "gpt-second",
				"enabled":   false,
				"active":    true,
				"maxTokens": 1000,
			},
		},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("patch ai settings status/body = %d %s", res.Code, res.Body.String())
	}
	var payload map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode ai settings: %v", err)
	}
	profiles, ok := payload["profiles"].([]any)
	if !ok || len(profiles) != 2 {
		t.Fatalf("profiles = %#v, want 2 profiles", payload["profiles"])
	}
	activeCount := 0
	for _, item := range profiles {
		profile, ok := item.(map[string]any)
		if !ok {
			t.Fatalf("unexpected profile shape: %#v", item)
		}
		if profile["enabled"] != true {
			t.Fatalf("profile should be enabled: %#v", profile)
		}
		if profile["active"] == true {
			activeCount++
			if profile["name"] != "First" {
				t.Fatalf("first active profile should remain default, got %#v", profile)
			}
		}
	}
	if activeCount != 1 {
		t.Fatalf("active profile count = %d, want 1", activeCount)
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/settings/ai", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("get ai settings status/body = %d %s", res.Code, res.Body.String())
	}
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode persisted ai settings: %v", err)
	}
	profiles, ok = payload["profiles"].([]any)
	if !ok || len(profiles) != 2 {
		t.Fatalf("persisted profiles = %#v, want 2 profiles", payload["profiles"])
	}
	activeCount = 0
	for _, item := range profiles {
		profile, ok := item.(map[string]any)
		if !ok {
			t.Fatalf("unexpected persisted profile shape: %#v", item)
		}
		if profile["enabled"] != true {
			t.Fatalf("persisted profile should be enabled: %#v", profile)
		}
		if profile["active"] == true {
			activeCount++
		}
	}
	if activeCount != 1 {
		t.Fatalf("persisted active profile count = %d, want 1", activeCount)
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
