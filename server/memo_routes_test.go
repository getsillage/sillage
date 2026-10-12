package server_test

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/getsillage/sillage/store"
)

func TestMemoCRUDAndLifecycle(t *testing.T) {
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
	if deleted["purgedAt"] != nil || deleted["content"] == "" {
		t.Fatalf("deleted memo must remain recoverable: %#v", deleted)
	}
	version = int64(deleted["version"].(float64))

	res = doJSON(t, srv, http.MethodGet, "/api/v1/memos?deleted=true", nil, bearer(token))
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), memoID) {
		t.Fatalf("recently deleted list status/body = %d %s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":restore", map[string]any{
		"expectedVersion": version,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("restore memo status = %d body=%s", res.Code, res.Body.String())
	}
	restored := decodeMemoResponse(t, res.Body.Bytes())
	if restored["deletedAt"] != nil {
		t.Fatalf("restored memo still deleted: %#v", restored)
	}
	version = int64(restored["version"].(float64))

	res = doJSON(t, srv, http.MethodDelete, "/api/v1/memos/"+memoID+"?expectedVersion="+strconv.FormatInt(version, 10), nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("delete restored memo status = %d body=%s", res.Code, res.Body.String())
	}
	deleted = decodeMemoResponse(t, res.Body.Bytes())
	version = int64(deleted["version"].(float64))
	res = doJSON(t, srv, http.MethodPost, "/api/v1/memos/"+memoID+":purge", map[string]any{
		"expectedVersion": version,
	}, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("purge memo status = %d body=%s", res.Code, res.Body.String())
	}
	purged := decodeMemoResponse(t, res.Body.Bytes())
	if purged["purgedAt"] == nil || purged["content"] != "" {
		t.Fatalf("purged memo was not scrubbed: %#v", purged)
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

func TestMemoListMaxLimitLookahead(t *testing.T) {
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
			if marker.Version != 3 || marker.Pinned != nil {
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

func TestRecentlyDeletedUsesDeletionOrderAndLifecycleCursor(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	type seededMemo struct {
		id        string
		deletedAt int64
		favorite  bool
	}
	seeded := []seededMemo{
		{deletedAt: time.Date(2026, 7, 30, 1, 0, 0, 0, time.UTC).UnixMilli()},
		{deletedAt: time.Date(2026, 7, 30, 3, 0, 0, 0, time.UTC).UnixMilli(), favorite: true},
		{deletedAt: time.Date(2026, 7, 30, 2, 0, 0, 0, time.UTC).UnixMilli()},
	}
	entryDates := []string{"2030-01-01", "2020-01-01", "2025-01-01"}
	for index := range seeded {
		res := doJSON(t, srv, http.MethodPost, "/api/v1/memos", map[string]any{
			"content":   fmt.Sprintf("deleted order %d", index),
			"entryDate": entryDates[index],
		}, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("create memo %d status = %d body=%s", index, res.Code, res.Body.String())
		}
		seeded[index].id = decodeMemoResponse(t, res.Body.Bytes())["id"].(string)
		var favoriteAt any
		if seeded[index].favorite {
			favoriteAt = seeded[index].deletedAt - 1
		}
		if _, err := srv.Store.GetDriver().GetDB().ExecContext(
			context.Background(),
			"UPDATE memo SET favorited_at = ?, deleted_at = ?, updated_at = ?, version = version + 1 WHERE id = ?",
			favoriteAt,
			seeded[index].deletedAt,
			seeded[index].deletedAt,
			seeded[index].id,
		); err != nil {
			t.Fatalf("seed deleted memo %d: %v", index, err)
		}
	}

	readPage := func(path string) struct {
		Memos []struct {
			ID string `json:"id"`
		} `json:"memos"`
		NextCursor string `json:"nextCursor"`
	} {
		t.Helper()
		res := doJSON(t, srv, http.MethodGet, path, nil, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("list recently deleted status = %d body=%s", res.Code, res.Body.String())
		}
		var page struct {
			Memos []struct {
				ID string `json:"id"`
			} `json:"memos"`
			NextCursor string `json:"nextCursor"`
		}
		if err := json.Unmarshal(res.Body.Bytes(), &page); err != nil {
			t.Fatal(err)
		}
		return page
	}

	first := readPage("/api/v1/memos?limit=2&deleted=true&favorited=false")
	if len(first.Memos) != 2 || first.Memos[0].ID != seeded[1].id || first.Memos[1].ID != seeded[2].id {
		t.Fatalf("first recently deleted page = %+v", first.Memos)
	}
	decoded, err := base64.RawURLEncoding.DecodeString(first.NextCursor)
	if err != nil {
		t.Fatal(err)
	}
	var marker struct {
		Version   int   `json:"version"`
		Deleted   bool  `json:"deleted"`
		DeletedAt int64 `json:"deletedAt"`
	}
	if err := json.Unmarshal(decoded, &marker); err != nil {
		t.Fatal(err)
	}
	if marker.Version != 3 || !marker.Deleted || marker.DeletedAt != seeded[2].deletedAt {
		t.Fatalf("recently deleted cursor = %+v", marker)
	}
	second := readPage("/api/v1/memos?limit=2&deleted=true&favorited=false&cursor=" + url.QueryEscape(first.NextCursor))
	if len(second.Memos) != 1 || second.Memos[0].ID != seeded[0].id || second.NextCursor != "" {
		t.Fatalf("second recently deleted page = %+v cursor=%q", second.Memos, second.NextCursor)
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
