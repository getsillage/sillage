package server_test

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"testing"

	"github.com/getsillage/sillage/store"
)

func TestMemoCRUDAndSyncPull(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

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
	version = int64(updated["version"].(float64))

	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":setFavorited", map[string]any{
		"expectedVersion": version,
		"favorited":       true,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("favorite memo status = %d body=%s", res.Code, res.Body.String())
	}
	favorited := decodeMemoResponse(t, res.Body.Bytes())
	if favorited["favoritedAt"] == nil {
		t.Fatal("favorited memo must include favoritedAt")
	}
	version = int64(favorited["version"].(float64))

	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":setArchived", map[string]any{
		"expectedVersion": version,
		"archived":        true,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("archive memo status = %d body=%s", res.Code, res.Body.String())
	}
	archived := decodeMemoResponse(t, res.Body.Bytes())
	if archived["archivedAt"] == nil {
		t.Fatal("archived memo must include archivedAt")
	}
	version = int64(archived["version"].(float64))

	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":generate-summary", nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"status":"complete"`) || !strings.Contains(res.Body.String(), `"inputTokens":11`) {
		t.Fatalf("generate summary action status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPatch, "/api/v1/memos/"+memoID, map[string]any{
		"content":         "过期写入",
		"entryDate":       "2026-06-26",
		"expectedVersion": 2,
	}, bearer(token))
	if res.Code != http.StatusConflict || !strings.Contains(res.Body.String(), "version_conflict") {
		t.Fatalf("stale update status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodDelete, "/api/v1/memos/"+memoID+"?expectedVersion="+strconv.FormatInt(version, 10), nil, bearer(token))
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

func TestMemoCanonicalBoolActionsUseBodyAndCAS(t *testing.T) {
	tests := []struct {
		name      string
		action    string
		field     string
		timestamp string
	}{
		{name: "favorited", action: "setFavorited", field: "favorited", timestamp: "favoritedAt"},
		{name: "archived", action: "setArchived", field: "archived", timestamp: "archivedAt"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			srv := newTestServer(t)
			token := initializeAndToken(t, srv)
			res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
				"content":   "canonical bool action",
				"entryDate": "2026-06-26",
			}, bearer(token))
			if res.Code != http.StatusOK {
				t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
			}
			created := decodeMemoResponse(t, res.Body.Bytes())
			memoID := created["id"].(string)

			res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":"+tt.action, map[string]any{
				"expectedVersion": 1,
				tt.field:          true,
			}, bearer(token))
			if res.Code != http.StatusOK {
				t.Fatalf("set true status = %d body=%s", res.Code, res.Body.String())
			}
			enabled := decodeMemoResponse(t, res.Body.Bytes())
			if enabled[tt.timestamp] == nil || int64(enabled["version"].(float64)) != 2 {
				t.Fatalf("set true memo = %#v", enabled)
			}

			res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":"+tt.action, map[string]any{
				"expectedVersion": 2,
				tt.field:          false,
			}, bearer(token))
			if res.Code != http.StatusOK {
				t.Fatalf("set false status = %d body=%s", res.Code, res.Body.String())
			}
			disabled := decodeMemoResponse(t, res.Body.Bytes())
			if disabled[tt.timestamp] != nil || int64(disabled["version"].(float64)) != 3 {
				t.Fatalf("set false memo = %#v", disabled)
			}

			res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":"+tt.action, map[string]any{
				"expectedVersion": 1,
				tt.field:          true,
			}, bearer(token))
			if res.Code != http.StatusConflict || !strings.Contains(res.Body.String(), "version_conflict") {
				t.Fatalf("stale action status/body = %d %s", res.Code, res.Body.String())
			}
		})
	}
}

func TestMemoMutationsRequirePositiveExpectedVersion(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "version guarded memo",
		"entryDate": "2026-06-26",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
	}
	created := decodeMemoResponse(t, res.Body.Bytes())
	memoID := created["id"].(string)
	basePath := "/api/v1/memos/" + memoID

	tests := []struct {
		name   string
		method string
		path   string
		body   any
	}{
		{name: "patch missing", method: http.MethodPatch, path: basePath, body: map[string]any{"content": "changed"}},
		{name: "patch zero", method: http.MethodPatch, path: basePath, body: map[string]any{"content": "changed", "expectedVersion": 0}},
		{name: "patch invalid", method: http.MethodPatch, path: basePath, body: map[string]any{"content": "changed", "expectedVersion": "invalid"}},
		{name: "delete missing", method: http.MethodDelete, path: basePath},
		{name: "delete zero", method: http.MethodDelete, path: basePath + "?expectedVersion=0"},
		{name: "delete invalid", method: http.MethodDelete, path: basePath + "?expectedVersion=invalid"},
		{name: "canonical missing", method: http.MethodPost, path: basePath + ":setFavorited", body: map[string]any{"favorited": true}},
		{name: "canonical zero", method: http.MethodPost, path: basePath + ":setFavorited", body: map[string]any{"favorited": true, "expectedVersion": 0}},
		{name: "canonical invalid", method: http.MethodPost, path: basePath + ":setFavorited", body: map[string]any{"favorited": true, "expectedVersion": "invalid"}},
		{name: "favorite value missing", method: http.MethodPost, path: basePath + ":setFavorited", body: map[string]any{"expectedVersion": 1}},
		{name: "archive value missing", method: http.MethodPost, path: basePath + ":setArchived", body: map[string]any{"expectedVersion": 1}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			res := doJSON(t, srv, tt.method, tt.path, tt.body, bearer(token))
			if res.Code != http.StatusBadRequest {
				t.Fatalf("status = %d, want 400; body=%s", res.Code, res.Body.String())
			}
		})
	}

	res = doJSON(t, srv, http.MethodGet, basePath, nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("get memo status = %d body=%s", res.Code, res.Body.String())
	}
	unchanged := decodeMemoResponse(t, res.Body.Bytes())
	if unchanged["content"] != "version guarded memo" || int64(unchanged["version"].(float64)) != 1 || unchanged["favoritedAt"] != nil {
		t.Fatalf("memo changed after rejected mutations: %#v", unchanged)
	}
}

func TestMemoPinActionsAreRemoved(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "memo without pin actions",
		"entryDate": "2026-06-26",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
	}
	memoID := decodeMemoResponse(t, res.Body.Bytes())["id"].(string)
	for _, action := range []string{"setPinned", "pin", "unpin"} {
		res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":"+action, map[string]any{
			"expectedVersion": 1,
			"pinned":          true,
		}, bearer(token))
		if res.Code != http.StatusNotFound {
			t.Fatalf("%s status = %d, want 404; body=%s", action, res.Code, res.Body.String())
		}
	}
}

func TestMemoListAndSyncMaxLimitLookahead(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	ctx := context.Background()
	account, err := srv.Store.GetAccountByUsername(ctx, "felix")
	if err != nil {
		t.Fatalf("GetAccountByUsername() error = %v", err)
	}
	for i := 0; i < store.MaxMemoListLimit+1; i++ {
		if _, err := srv.Store.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account.ID,
			Content:   "memo " + strconv.Itoa(i),
			EntryDate: "2026-06-26",
		}); err != nil {
			t.Fatalf("CreateMemo(%d) error = %v", i, err)
		}
	}

	decodeList := func(resBody []byte) struct {
		Memos      []map[string]any `json:"memos"`
		NextCursor string           `json:"nextCursor"`
	} {
		t.Helper()
		var payload struct {
			Memos      []map[string]any `json:"memos"`
			NextCursor string           `json:"nextCursor"`
		}
		if err := json.Unmarshal(resBody, &payload); err != nil {
			t.Fatalf("decode list response: %v", err)
		}
		return payload
	}

	res := doJSON(t, srv, http.MethodGet, "/api/v1/memos?limit=500", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("first list status = %d body=%s", res.Code, res.Body.String())
	}
	first := decodeList(res.Body.Bytes())
	if len(first.Memos) != store.MaxMemoListLimit || first.NextCursor == "" {
		t.Fatalf("first list len/cursor = %d/%q", len(first.Memos), first.NextCursor)
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/memos?limit=500&cursor="+url.QueryEscape(first.NextCursor), nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("second list status = %d body=%s", res.Code, res.Body.String())
	}
	second := decodeList(res.Body.Bytes())
	if len(second.Memos) != 1 || second.NextCursor != "" {
		t.Fatalf("second list len/cursor = %d/%q", len(second.Memos), second.NextCursor)
	}

	decodeSync := func(resBody []byte) struct {
		Memos      []map[string]any `json:"memos"`
		NextCursor string           `json:"nextCursor"`
		HasMore    bool             `json:"hasMore"`
	} {
		t.Helper()
		var payload struct {
			Memos      []map[string]any `json:"memos"`
			NextCursor string           `json:"nextCursor"`
			HasMore    bool             `json:"hasMore"`
		}
		if err := json.Unmarshal(resBody, &payload); err != nil {
			t.Fatalf("decode sync response: %v", err)
		}
		return payload
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync?limit=500", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("first sync status = %d body=%s", res.Code, res.Body.String())
	}
	firstSync := decodeSync(res.Body.Bytes())
	if len(firstSync.Memos) != store.MaxSyncPageLimit || !firstSync.HasMore || firstSync.NextCursor == "" {
		t.Fatalf("first sync len/hasMore/cursor = %d/%t/%q", len(firstSync.Memos), firstSync.HasMore, firstSync.NextCursor)
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync?limit=500&cursor="+url.QueryEscape(firstSync.NextCursor), nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("second sync status = %d body=%s", res.Code, res.Body.String())
	}
	secondSync := decodeSync(res.Body.Bytes())
	if len(secondSync.Memos) != store.MaxSyncPageLimit || !secondSync.HasMore || secondSync.NextCursor == "" {
		t.Fatalf("second sync len/hasMore/cursor = %d/%t/%q", len(secondSync.Memos), secondSync.HasMore, secondSync.NextCursor)
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync?limit=500&cursor="+url.QueryEscape(secondSync.NextCursor), nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("third sync status = %d body=%s", res.Code, res.Body.String())
	}
	thirdSync := decodeSync(res.Body.Bytes())
	if len(thirdSync.Memos) != store.MaxMemoListLimit+1-(2*store.MaxSyncPageLimit) || thirdSync.HasMore {
		t.Fatalf("third sync len/hasMore = %d/%t", len(thirdSync.Memos), thirdSync.HasMore)
	}
}

func TestMemoListIsChronologicalAndAcceptsLegacyCursors(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	ctx := context.Background()
	account, err := srv.Store.GetAccountByUsername(ctx, "felix")
	if err != nil {
		t.Fatalf("GetAccountByUsername() error = %v", err)
	}

	byDate := make(map[string]*store.Memo)
	for _, entryDate := range []string{"2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05"} {
		memo, err := srv.Store.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account.ID,
			Content:   "entry " + entryDate,
			EntryDate: entryDate,
		})
		if err != nil {
			t.Fatalf("CreateMemo(%s) error = %v", entryDate, err)
		}
		byDate[entryDate] = memo
	}
	type listPayload struct {
		Memos []struct {
			ID string `json:"id"`
		} `json:"memos"`
		NextCursor string `json:"nextCursor"`
	}
	readPage := func(path string) listPayload {
		t.Helper()
		res := doJSON(t, srv, http.MethodGet, path, nil, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("GET %s status = %d body=%s", path, res.Code, res.Body.String())
		}
		var payload listPayload
		if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
			t.Fatalf("decode list page: %v", err)
		}
		return payload
	}

	var got []string
	cursor := ""
	for pageNumber := 0; ; pageNumber++ {
		path := "/api/v1/memos?limit=2"
		if cursor != "" {
			path += "&cursor=" + url.QueryEscape(cursor)
		}
		page := readPage(path)
		for _, memo := range page.Memos {
			got = append(got, memo.ID)
		}
		if pageNumber == 0 {
			decoded, err := base64.RawURLEncoding.DecodeString(page.NextCursor)
			if err != nil {
				t.Fatalf("decode next cursor: %v", err)
			}
			var marker struct {
				Version int             `json:"version"`
				Pinned  json.RawMessage `json:"pinned"`
			}
			if err := json.Unmarshal(decoded, &marker); err != nil {
				t.Fatalf("decode cursor JSON: %v", err)
			}
			if marker.Version != 2 || marker.Pinned != nil {
				t.Fatalf("new cursor marker = %#v", marker)
			}
		}
		cursor = page.NextCursor
		if cursor == "" {
			break
		}
	}
	want := []string{
		byDate["2026-01-05"].ID,
		byDate["2026-01-04"].ID,
		byDate["2026-01-03"].ID,
		byDate["2026-01-02"].ID,
		byDate["2026-01-01"].ID,
	}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("paged ids = %v, want %v", got, want)
	}

	legacyDateLast := byDate["2026-01-04"]
	wantLegacyDate := []string{
		byDate["2026-01-03"].ID,
		byDate["2026-01-02"].ID,
		byDate["2026-01-01"].ID,
	}
	legacyDateJSON, err := json.Marshal(map[string]any{
		"entryDate": legacyDateLast.EntryDate,
		"createdAt": legacyDateLast.CreatedAt,
		"id":        legacyDateLast.ID,
	})
	if err != nil {
		t.Fatalf("encode legacy date cursor: %v", err)
	}
	legacyDatePage := readPage("/api/v1/memos?limit=10&cursor=" + url.QueryEscape(base64.RawURLEncoding.EncodeToString(legacyDateJSON)))
	legacyDateIDs := make([]string, 0, len(legacyDatePage.Memos))
	for _, memo := range legacyDatePage.Memos {
		legacyDateIDs = append(legacyDateIDs, memo.ID)
	}
	if strings.Join(legacyDateIDs, ",") != strings.Join(wantLegacyDate, ",") {
		t.Fatalf("legacy date cursor ids = %v, want %v", legacyDateIDs, wantLegacyDate)
	}

	favorited := true
	for _, entryDate := range []string{"2026-01-01", "2026-01-02"} {
		memo := byDate[entryDate]
		updated, err := srv.Store.UpdateMemo(ctx, &store.UpdateMemo{
			ID:              memo.ID,
			CreatorID:       account.ID,
			ExpectedVersion: memo.Version,
			Favorited:       &favorited,
		})
		if err != nil {
			t.Fatalf("favorite memo %s: %v", entryDate, err)
		}
		byDate[entryDate] = updated
	}
	legacyLast := byDate["2026-01-02"]
	legacyJSON, err := json.Marshal(map[string]any{
		"version":   1,
		"pinned":    true,
		"entryDate": legacyLast.EntryDate,
		"createdAt": legacyLast.CreatedAt,
		"id":        legacyLast.ID,
	})
	if err != nil {
		t.Fatalf("encode legacy pinned cursor: %v", err)
	}
	legacyCursor := base64.RawURLEncoding.EncodeToString(legacyJSON)
	var legacyIDs []string
	for legacyCursor != "" {
		page := readPage("/api/v1/memos?limit=2&cursor=" + url.QueryEscape(legacyCursor))
		for _, memo := range page.Memos {
			legacyIDs = append(legacyIDs, memo.ID)
		}
		if page.NextCursor != "" {
			payload, decodeErr := base64.RawURLEncoding.DecodeString(page.NextCursor)
			if decodeErr != nil {
				t.Fatalf("decode continued v1 cursor: %v", decodeErr)
			}
			var marker struct {
				Version int   `json:"version"`
				Pinned  *bool `json:"pinned"`
			}
			if err := json.Unmarshal(payload, &marker); err != nil || marker.Version != 1 || marker.Pinned == nil {
				t.Fatalf("continued cursor = %#v, error = %v", marker, err)
			}
		}
		legacyCursor = page.NextCursor
	}
	wantLegacy := []string{
		byDate["2026-01-01"].ID,
		byDate["2026-01-05"].ID,
		byDate["2026-01-04"].ID,
		byDate["2026-01-03"].ID,
	}
	if strings.Join(legacyIDs, ",") != strings.Join(wantLegacy, ",") {
		t.Fatalf("legacy pinned cursor ids = %v, want %v", legacyIDs, wantLegacy)
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
				"pinned":    true,
			},
		}},
	}
	res := doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", pushCreate, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"status":"applied"`) || !strings.Contains(res.Body.String(), `"favoritedAt":`) {
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

	pushMissingBaseVersion := map[string]any{
		"changes": []map[string]any{{
			"mutationId":   "m-update-missing-base",
			"resourceType": "memo",
			"resourceId":   "01800000-0000-7000-8000-000000000001",
			"action":       "update",
			"memo": map[string]any{
				"id":        "01800000-0000-7000-8000-000000000001",
				"content":   "缺少版本的同步更新",
				"entryDate": "2026-06-26",
			},
		}},
	}
	res = doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", pushMissingBaseVersion, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"reason":"missing_base_version"`) {
		t.Fatalf("sync missing base version status/body = %d %s", res.Code, res.Body.String())
	}
}

func TestSyncPushFavoritedOverridesLegacyPinned(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/sync:push", map[string]any{
		"changes": []map[string]any{{
			"mutationId":   "m-favorite-precedence",
			"resourceType": "memo",
			"resourceId":   "01800000-0000-7000-8000-000000000002",
			"action":       "create",
			"memo": map[string]any{
				"id":        "01800000-0000-7000-8000-000000000002",
				"content":   "规范收藏字段优先",
				"entryDate": "2026-06-27",
				"pinned":    true,
				"favorited": false,
			},
		}},
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("sync create status = %d body=%s", res.Code, res.Body.String())
	}
	var body struct {
		Results []struct {
			Status   string `json:"status"`
			Resource struct {
				FavoritedAt *string `json:"favoritedAt"`
			} `json:"resource"`
		} `json:"results"`
	}
	if err := json.Unmarshal(res.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if len(body.Results) != 1 || body.Results[0].Status != "applied" {
		t.Fatalf("sync results = %+v, want one applied result", body.Results)
	}
	if body.Results[0].Resource.FavoritedAt != nil {
		t.Fatalf("favoritedAt = %q, want nil because canonical favorited=false overrides pinned=true", *body.Results[0].Resource.FavoritedAt)
	}
}

func TestMemoSearchChineseSummaryAndTombstone(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)

	res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "今天和朋友散步，聊到睡眠变好了。",
		"entryDate": "2026-06-26",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
	}
	sleepMemo := decodeMemoResponse(t, res.Body.Bytes())

	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
		"content":   "午饭后整理了屋子。",
		"entryDate": "2026-06-25",
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("create second memo status = %d body=%s", res.Code, res.Body.String())
	}
	summaryMemo := decodeMemoResponse(t, res.Body.Bytes())
	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+summaryMemo["id"].(string)+":generate-summary", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("generate summary status = %d body=%s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/memos?q="+urlQueryEscape("睡眠变好"), nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), sleepMemo["id"].(string)) {
		t.Fatalf("search Chinese phrase status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/memos?q="+urlQueryEscape("整理了屋子"), nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), summaryMemo["id"].(string)) {
		t.Fatalf("search summary/content status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodDelete, "/api/v1/memos/"+sleepMemo["id"].(string)+"?expectedVersion=1", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("delete memo status = %d body=%s", res.Code, res.Body.String())
	}
	res = doJSON(t, srv, http.MethodGet, "/api/v1/memos?q="+urlQueryEscape("睡眠变好"), nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("search after delete status = %d body=%s", res.Code, res.Body.String())
	}
	var payload map[string][]map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode search response: %v", err)
	}
	if len(payload["memos"]) != 0 {
		t.Fatalf("deleted memo should not be searchable: %#v", payload["memos"])
	}
}

func TestMemoListAndSearchThreePartitions(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	create := func(entryDate string) map[string]any {
		t.Helper()
		res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
			"content":   "shared filterable content",
			"entryDate": entryDate,
		}, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("create memo status = %d body=%s", res.Code, res.Body.String())
		}
		return decodeMemoResponse(t, res.Body.Bytes())
	}

	favorited := create("2026-06-28")
	archived := create("2026-06-27")
	current := create("2026-06-26")
	res := doJSON(
		t,
		srv,
		http.MethodPost,
		"/api/v1/memos/"+favorited["id"].(string)+":setFavorited",
		map[string]any{"expectedVersion": 1, "favorited": true},
		bearer(token),
	)
	if res.Code != http.StatusOK {
		t.Fatalf("favorite memo status = %d body=%s", res.Code, res.Body.String())
	}
	res = doJSON(
		t,
		srv,
		http.MethodPost,
		"/api/v1/memos/"+archived["id"].(string)+":setArchived",
		map[string]any{"expectedVersion": 1, "archived": true},
		bearer(token),
	)
	if res.Code != http.StatusOK {
		t.Fatalf("archive memo status = %d body=%s", res.Code, res.Body.String())
	}

	tests := []struct {
		name   string
		filter string
		wantID string
	}{
		{name: "unarchived", filter: "archived=false&favorited=false", wantID: current["id"].(string)},
		{name: "archived", filter: "archived=true&favorited=false", wantID: archived["id"].(string)},
		{name: "favorited", filter: "favorited=true", wantID: favorited["id"].(string)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			for _, query := range []string{"", "query=filterable&"} {
				path := "/api/v1/memos?" + query + "limit=1&" + tt.filter
				res := doJSON(t, srv, http.MethodGet, path, nil, bearer(token))
				if res.Code != http.StatusOK {
					t.Fatalf("GET %s status = %d body=%s", path, res.Code, res.Body.String())
				}
				var payload struct {
					Memos []map[string]any `json:"memos"`
				}
				if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
					t.Fatalf("decode list response: %v", err)
				}
				if len(payload.Memos) != 1 || payload.Memos[0]["id"] != tt.wantID {
					t.Fatalf("GET %s memos = %#v, want %s", path, payload.Memos, tt.wantID)
				}
			}
		})
	}

	for _, path := range []string{
		"/api/v1/memos?archived=invalid",
		"/api/v1/memos?query=filterable&favorited=invalid",
	} {
		res := doJSON(t, srv, http.MethodGet, path, nil, bearer(token))
		if res.Code != http.StatusBadRequest {
			t.Fatalf("GET %s status = %d, want 400; body=%s", path, res.Code, res.Body.String())
		}
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

func urlQueryEscape(value string) string {
	return url.QueryEscape(value)
}
