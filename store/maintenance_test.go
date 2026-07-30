package store_test

import (
	"context"
	"testing"
	"time"

	"github.com/getsillage/sillage/store"
)

func TestCleanupEphemeralData(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)
	now := time.Date(2026, 7, 30, 12, 0, 0, 0, time.UTC)

	for _, expiresAt := range []time.Time{now.Add(-time.Minute), now.Add(time.Hour)} {
		if _, err := s.CreateSession(ctx, &store.CreateSession{
			AccountID:    accountID,
			RefreshToken: expiresAt.String(),
			ExpiresAt:    expiresAt,
		}); err != nil {
			t.Fatalf("CreateSession() error = %v", err)
		}
	}
	if _, err := s.GetDriver().GetDB().ExecContext(ctx, `
INSERT INTO runtime_kv (namespace, key, value, expires_at)
VALUES ('test', 'expired', '1', ?), ('test', 'current', '1', ?)`,
		now.Add(-time.Minute).UnixMilli(), now.Add(time.Hour).UnixMilli()); err != nil {
		t.Fatalf("seed runtime_kv: %v", err)
	}
	for _, mutationID := range []string{"old", "current"} {
		if err := s.PutSyncMutation(ctx, &store.SyncMutation{
			AccountID: accountID, MutationID: mutationID, ResourceType: "memo", Result: `{}`,
		}); err != nil {
			t.Fatalf("PutSyncMutation(%s) error = %v", mutationID, err)
		}
	}
	if _, err := s.GetDriver().GetDB().ExecContext(ctx, `
UPDATE sync_mutation SET created_at = ? WHERE mutation_id = 'old'`,
		now.Add(-store.SyncMutationRetention-time.Hour).UnixMilli()); err != nil {
		t.Fatalf("age sync mutation: %v", err)
	}

	stats, err := s.CleanupEphemeralData(ctx, now)
	if err != nil {
		t.Fatalf("CleanupEphemeralData() error = %v", err)
	}
	if stats.Sessions != 1 || stats.RuntimeValues != 1 || stats.SyncMutations != 1 {
		t.Fatalf("CleanupEphemeralData() stats = %+v", stats)
	}
	for table, want := range map[string]int{"session": 1, "runtime_kv": 1, "sync_mutation": 1} {
		var count int
		if err := s.GetDriver().GetDB().QueryRowContext(ctx, "SELECT COUNT(1) FROM "+table).Scan(&count); err != nil {
			t.Fatalf("count %s: %v", table, err)
		}
		if count != want {
			t.Fatalf("%s rows = %d, want %d", table, count, want)
		}
	}
}
