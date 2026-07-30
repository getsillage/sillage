package store_test

import (
	"context"
	"database/sql"
	"testing"

	"github.com/getsillage/sillage/store"
)

func TestWithTransactionRollsBackMemoWhenSyncResultFails(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)
	mutation := &store.SyncMutation{
		AccountID:    accountID,
		MutationID:   "duplicate-mutation",
		ResourceType: "memo",
		ResourceID:   "existing",
		Result:       `{"status":"applied"}`,
	}
	if err := s.PutSyncMutation(ctx, mutation); err != nil {
		t.Fatalf("seed PutSyncMutation() error = %v", err)
	}

	err := s.WithTransaction(ctx, func(txStore *store.Store) error {
		if _, err := txStore.CreateMemo(ctx, &store.CreateMemo{
			ID:        "rolled-back-memo",
			CreatorID: accountID,
			Content:   "must roll back",
			EntryDate: "2026-07-30",
		}); err != nil {
			return err
		}
		return txStore.PutSyncMutation(ctx, mutation)
	})
	if err == nil {
		t.Fatal("WithTransaction() error = nil, want duplicate mutation failure")
	}
	if _, err := s.GetMemo(ctx, accountID, "rolled-back-memo", true); err != sql.ErrNoRows {
		t.Fatalf("rolled-back memo lookup error = %v, want sql.ErrNoRows", err)
	}
}

func TestWithTransactionCommitsMemoAndSyncResultTogether(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)

	err := s.WithTransaction(ctx, func(txStore *store.Store) error {
		if _, err := txStore.CreateMemo(ctx, &store.CreateMemo{
			ID:        "committed-memo",
			CreatorID: accountID,
			Content:   "committed",
			EntryDate: "2026-07-30",
		}); err != nil {
			return err
		}
		return txStore.PutSyncMutation(ctx, &store.SyncMutation{
			AccountID:    accountID,
			MutationID:   "committed-mutation",
			ResourceType: "memo",
			ResourceID:   "committed-memo",
			Result:       `{"status":"applied"}`,
		})
	})
	if err != nil {
		t.Fatalf("WithTransaction() error = %v", err)
	}
	if _, err := s.GetMemo(ctx, accountID, "committed-memo", true); err != nil {
		t.Fatalf("committed memo lookup error = %v", err)
	}
	if _, ok, err := s.GetSyncMutation(ctx, accountID, "committed-mutation"); err != nil || !ok {
		t.Fatalf("committed mutation lookup ok=%v error=%v", ok, err)
	}
}
