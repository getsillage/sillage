package server_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
)

func TestMemoCRUDAndSyncPull(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "今天开始写新的 memo",
		"entryDate": "2026-06-26",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
	}
	created := decodeMemoResponse(t, res.Body.Bytes())
	memoID := created["id"].(string)
	version := int64(created["version"].(float64))

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/memos/"+memoID, map[string]any{
		"content":         "今天开始写新的 memo，并验证 CAS",
		"entryDate":       "2026-06-26",
		"expectedVersion": version,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("update memo status = %d body=%s", res.Code, res.Body.String())
	}
	updated := decodeMemoResponse(t, res.Body.Bytes())
	if int64(updated["version"].(float64)) != version+1 {
		t.Fatalf("updated version = %v, want %d", updated["version"], version+1)
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/memos/"+memoID, map[string]any{
		"content":         "过期写入",
		"entryDate":       "2026-06-26",
		"expectedVersion": version,
	}, bearer(token))
	if res.Code != http.StatusConflict || !strings.Contains(res.Body.String(), "version_conflict") {
		t.Fatalf("stale update status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodDelete, "/api/v1/memos/"+memoID+"?expectedVersion=2", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("delete memo status = %d body=%s", res.Code, res.Body.String())
	}
	deleted := decodeMemoResponse(t, res.Body.Bytes())
	if deleted["deletedAt"] == nil {
		t.Fatal("deleted memo must include tombstone deletedAt")
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("sync pull status = %d body=%s", res.Code, res.Body.String())
	}
	var syncRes map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &syncRes); err != nil {
		t.Fatalf("decode sync response: %v", err)
	}
	memos := syncRes["memos"].([]any)
	if len(memos) != 1 {
		t.Fatalf("sync memos len = %d, want 1", len(memos))
	}
	syncedMemo := memos[0].(map[string]any)
	if syncedMemo["deletedAt"] == nil {
		t.Fatal("sync must include tombstone")
	}
	if syncRes["cursor"].(string) == "" || syncRes["hasMore"].(bool) {
		t.Fatalf("unexpected sync cursor/hasMore: %#v", syncRes)
	}
}

func TestSyncPushIdempotencyAndConflict(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	pushCreate := map[string]any{
		"changes": []map[string]any{{
			"mutationId":   "m-create-1",
			"resourceType": "memo",
			"resourceId":   "01800000-0000-7000-8000-000000000001",
			"action":       "create",
			"memo": map[string]any{
				"id":        "01800000-0000-7000-8000-000000000001",
				"content":   "离线创建的记录",
				"entryDate": "2026-06-26",
			},
		}},
	}
	res := doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", pushCreate, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"status":"applied"`) {
		t.Fatalf("sync create status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", pushCreate, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"idempotent":true`) {
		t.Fatalf("sync idempotent status/body = %d %s", res.Code, res.Body.String())
	}

	pushUpdate := map[string]any{
		"changes": []map[string]any{{
			"mutationId":   "m-update-1",
			"resourceType": "memo",
			"resourceId":   "01800000-0000-7000-8000-000000000001",
			"action":       "update",
			"baseVersion":  1,
			"memo": map[string]any{
				"id":        "01800000-0000-7000-8000-000000000001",
				"content":   "第一次同步更新",
				"entryDate": "2026-06-26",
			},
		}},
	}
	res = doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", pushUpdate, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"status":"applied"`) {
		t.Fatalf("sync update status/body = %d %s", res.Code, res.Body.String())
	}

	pushConflict := map[string]any{
		"changes": []map[string]any{{
			"mutationId":   "m-update-conflict",
			"resourceType": "memo",
			"resourceId":   "01800000-0000-7000-8000-000000000001",
			"action":       "update",
			"baseVersion":  1,
			"memo": map[string]any{
				"id":        "01800000-0000-7000-8000-000000000001",
				"content":   "过期同步更新",
				"entryDate": "2026-06-26",
			},
		}},
	}
	res = doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", pushConflict, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"status":"conflict"`) {
		t.Fatalf("sync conflict status/body = %d %s", res.Code, res.Body.String())
	}
}

func initializeAndToken(t *testing.T, srv interface {
	ServeHTTP(http.ResponseWriter, *http.Request)
}) string {
	t.Helper()
	res := doJSON(t, srv, http.MethodPost, "/api/v1/auth/initialize", map[string]string{
		"username": "felix",
		"password": "passw0rd!",
	}, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("initialize status = %d body=%s", res.Code, res.Body.String())
	}
	var payload map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode initialize response: %v", err)
	}
	token, ok := payload["accessToken"].(string)
	if !ok || token == "" {
		t.Fatalf("missing access token: %#v", payload)
	}
	return token
}

func bearer(token string) map[string]string {
	return map[string]string{"Authorization": "Bearer " + token}
}

func decodeMemoResponse(t *testing.T, body []byte) map[string]any {
	t.Helper()
	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("decode memo response: %v", err)
	}
	memo, ok := payload["memo"].(map[string]any)
	if !ok {
		t.Fatalf("response missing memo: %#v", payload)
	}
	return memo
}
