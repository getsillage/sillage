//go:build scale_acceptance

package scale_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"testing"
	"time"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/internal/secret"
	"github.com/getsillage/sillage/server"
	"github.com/getsillage/sillage/store"
	storedb "github.com/getsillage/sillage/store/db"
)

const (
	activeMemoCount  = 10_000
	deletedMemoCount = 2_000
	interactiveLimit = 2 * time.Second
	totalLimit       = 60 * time.Second
)

type authPayload struct {
	AccessToken string `json:"accessToken"`
	Account     struct {
		ID string `json:"id"`
	} `json:"account"`
}

type memoDTO struct {
	ID string `json:"id"`
}

type memoPage struct {
	Memos      []memoDTO `json:"memos"`
	NextCursor string    `json:"nextCursor"`
	HasMore    bool      `json:"hasMore"`
}

func TestLongTermPersonalUseScale(t *testing.T) {
	started := time.Now()
	previousLogger := slog.Default()
	slog.SetDefault(slog.New(slog.NewTextHandler(io.Discard, nil)))
	t.Cleanup(func() { slog.SetDefault(previousLogger) })

	ctx := context.Background()
	dataDir := t.TempDir()
	p := &profile.Profile{
		Addr:        "127.0.0.1",
		Port:        5231,
		Data:        dataDir,
		Driver:      profile.DriverSQLite,
		DSN:         filepath.Join(dataDir, profile.DefaultSQLiteFile),
		MaxUploadMB: 30,
	}
	if err := p.Validate(); err != nil {
		t.Fatalf("validate profile: %v", err)
	}
	driver, err := storedb.NewDBDriver(p)
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	storeInstance := store.New(driver, p)
	t.Cleanup(func() {
		if err := storeInstance.Close(); err != nil {
			t.Errorf("close store: %v", err)
		}
	})
	if err := storeInstance.Migrate(ctx); err != nil {
		t.Fatalf("migrate database: %v", err)
	}
	secrets, err := secret.Load(dataDir)
	if err != nil {
		t.Fatalf("load secrets: %v", err)
	}
	srv, err := server.New(ctx, p, storeInstance, secrets)
	if err != nil {
		t.Fatalf("create server: %v", err)
	}
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := &http.Client{Timeout: 5 * time.Second}
	auth := initialize(t, client, httpServer.URL)
	seedMemos(t, ctx, storeInstance, auth.Account.ID)

	assertIntegrity(t, ctx, storeInstance)
	activeIDs := traverseMemoPages(t, client, httpServer.URL, auth.AccessToken, false)
	if len(activeIDs) != activeMemoCount {
		t.Fatalf("active traversal returned %d records, want %d", len(activeIDs), activeMemoCount)
	}

	searchURL := httpServer.URL + "/api/v1/memos?limit=50&query=" + url.QueryEscape("scaleunique09999")
	search := timedGET[memoPage](t, client, searchURL, auth.AccessToken, "selective search")
	if len(search.Memos) != 1 || search.Memos[0].ID != "memo-09999" {
		t.Fatalf("selective search returned %#v", search.Memos)
	}

	deleted := timedGET[memoPage](
		t,
		client,
		httpServer.URL+"/api/v1/memos?deleted=true&limit=500",
		auth.AccessToken,
		"recently deleted first page",
	)
	if len(deleted.Memos) != 500 || deleted.NextCursor == "" {
		t.Fatalf("deleted first page = %d records, cursor=%q", len(deleted.Memos), deleted.NextCursor)
	}

	syncIDs := traverseSync(t, client, httpServer.URL, auth.AccessToken)
	if len(syncIDs) != activeMemoCount+deletedMemoCount {
		t.Fatalf(
			"sync traversal returned %d records, want %d",
			len(syncIDs),
			activeMemoCount+deletedMemoCount,
		)
	}

	if elapsed := time.Since(started); elapsed > totalLimit {
		t.Fatalf("scale acceptance took %s, limit %s", elapsed.Round(time.Millisecond), totalLimit)
	} else {
		t.Logf(
			"scale acceptance passed: %d active, %d deleted, %s total",
			activeMemoCount,
			deletedMemoCount,
			elapsed.Round(time.Millisecond),
		)
	}
}

func initialize(t *testing.T, client *http.Client, baseURL string) authPayload {
	t.Helper()
	body, err := json.Marshal(map[string]string{
		"username":    "scale-acceptance",
		"displayName": "Scale Acceptance",
		"password":    "scale-acceptance-password",
	})
	if err != nil {
		t.Fatal(err)
	}
	request, err := http.NewRequest(http.MethodPost, baseURL+"/api/v1/auth/initialize", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := client.Do(request)
	if err != nil {
		t.Fatalf("initialize: %v", err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		payload, _ := io.ReadAll(response.Body)
		t.Fatalf("initialize status %d: %s", response.StatusCode, payload)
	}
	var payload authPayload
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode initialize response: %v", err)
	}
	if payload.AccessToken == "" || payload.Account.ID == "" {
		t.Fatalf("initialize omitted credentials: %#v", payload)
	}
	return payload
}

func seedMemos(t *testing.T, ctx context.Context, storeInstance *store.Store, accountID string) {
	t.Helper()
	tx, err := storeInstance.GetDriver().GetDB().BeginTx(ctx, nil)
	if err != nil {
		t.Fatalf("begin scale seed: %v", err)
	}
	defer tx.Rollback()
	statement, err := tx.PrepareContext(ctx, `
INSERT INTO memo (
  id, creator_id, content, entry_date, version, favorited_at, archived_at,
  created_at, updated_at, deleted_at, purged_at
) VALUES (?, ?, ?, ?, 1, NULL, NULL, ?, ?, ?, NULL)`)
	if err != nil {
		t.Fatalf("prepare scale seed: %v", err)
	}
	defer statement.Close()

	baseTimestamp := int64(1_700_000_000_000)
	for index := 0; index < activeMemoCount+deletedMemoCount; index += 1 {
		createdAt := baseTimestamp + int64(index)
		updatedAt := baseTimestamp + int64(index/25)
		entryDate := time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC).
			AddDate(0, 0, index%2190).
			Format("2006-01-02")
		content := fmt.Sprintf(
			"Long-term scale record %05d with searchable marker scaleunique%05d and ordinary journal text.",
			index,
			index,
		)
		var deletedAt any
		if index >= activeMemoCount {
			deletedAt = baseTimestamp + int64(index-activeMemoCount)
		}
		if _, err := statement.ExecContext(
			ctx,
			fmt.Sprintf("memo-%05d", index),
			accountID,
			content,
			entryDate,
			createdAt,
			updatedAt,
			deletedAt,
		); err != nil {
			t.Fatalf("insert scale memo %d: %v", index, err)
		}
	}
	if err := tx.Commit(); err != nil {
		t.Fatalf("commit scale seed: %v", err)
	}
}

func traverseMemoPages(
	t *testing.T,
	client *http.Client,
	baseURL string,
	accessToken string,
	deleted bool,
) map[string]struct{} {
	t.Helper()
	seen := make(map[string]struct{})
	cursor := ""
	for page := 0; ; page += 1 {
		endpoint := fmt.Sprintf("%s/api/v1/memos?limit=500&deleted=%t", baseURL, deleted)
		if cursor != "" {
			endpoint += "&cursor=" + url.QueryEscape(cursor)
		}
		payload := timedGET[memoPage](t, client, endpoint, accessToken, fmt.Sprintf("memo page %d", page+1))
		for _, memo := range payload.Memos {
			if _, duplicate := seen[memo.ID]; duplicate {
				t.Fatalf("memo traversal returned duplicate %s", memo.ID)
			}
			seen[memo.ID] = struct{}{}
		}
		if payload.NextCursor == "" {
			return seen
		}
		if payload.NextCursor == cursor {
			t.Fatalf("memo traversal cursor did not advance: %q", cursor)
		}
		cursor = payload.NextCursor
	}
}

func traverseSync(t *testing.T, client *http.Client, baseURL, accessToken string) map[string]struct{} {
	t.Helper()
	seen := make(map[string]struct{})
	cursor := ""
	for page := 0; ; page += 1 {
		endpoint := baseURL + "/api/v1/sync?limit=200"
		if cursor != "" {
			endpoint += "&cursor=" + url.QueryEscape(cursor)
		}
		payload := timedGET[memoPage](t, client, endpoint, accessToken, fmt.Sprintf("sync page %d", page+1))
		for _, memo := range payload.Memos {
			if _, duplicate := seen[memo.ID]; duplicate {
				t.Fatalf("sync traversal returned duplicate %s", memo.ID)
			}
			seen[memo.ID] = struct{}{}
		}
		if !payload.HasMore {
			return seen
		}
		if payload.NextCursor == "" {
			t.Fatal("sync traversal reported more data without a next cursor")
		}
		if payload.NextCursor == cursor {
			t.Fatalf("sync cursor did not advance: %q", cursor)
		}
		cursor = payload.NextCursor
	}
}

func timedGET[T any](
	t *testing.T,
	client *http.Client,
	endpoint string,
	accessToken string,
	operation string,
) T {
	t.Helper()
	started := time.Now()
	request, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Authorization", "Bearer "+accessToken)
	response, err := client.Do(request)
	if err != nil {
		t.Fatalf("%s: %v", operation, err)
	}
	defer response.Body.Close()
	elapsed := time.Since(started)
	if elapsed > interactiveLimit {
		t.Fatalf("%s took %s, limit %s", operation, elapsed.Round(time.Millisecond), interactiveLimit)
	}
	if response.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("%s status %d: %s", operation, response.StatusCode, body)
	}
	var payload T
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		t.Fatalf("decode %s: %v", operation, err)
	}
	return payload
}

func assertIntegrity(t *testing.T, ctx context.Context, storeInstance *store.Store) {
	t.Helper()
	started := time.Now()
	var result string
	if err := storeInstance.GetDriver().GetDB().QueryRowContext(ctx, "PRAGMA integrity_check").Scan(&result); err != nil {
		t.Fatalf("integrity check: %v", err)
	}
	if result != "ok" {
		t.Fatalf("integrity check = %q", result)
	}
	if elapsed := time.Since(started); elapsed > interactiveLimit {
		t.Fatalf("integrity check took %s, limit %s", elapsed.Round(time.Millisecond), interactiveLimit)
	}
}
