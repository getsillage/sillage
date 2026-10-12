package store_test

import (
	"context"
	"database/sql"
	"testing"

	"github.com/getsillage/sillage/store"
)

func TestWithTransactionRollsBackRelatedMemoWrites(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)
	if _, err := s.CreateMemo(ctx, &store.CreateMemo{ID: "existing", CreatorID: accountID, Content: "existing", EntryDate: "2026-07-30"}); err != nil {
		t.Fatal(err)
	}
	err := s.WithTransaction(ctx, func(txStore *store.Store) error {
		if _, err := txStore.CreateMemo(ctx, &store.CreateMemo{ID: "rolled-back-memo", CreatorID: accountID, Content: "must roll back", EntryDate: "2026-07-30"}); err != nil {
			return err
		}
		_, err := txStore.CreateMemo(ctx, &store.CreateMemo{ID: "existing", CreatorID: accountID, Content: "duplicate", EntryDate: "2026-07-30"})
		return err
	})
	if err == nil {
		t.Fatal("WithTransaction() error = nil, want duplicate record failure")
	}
	if _, err := s.GetMemo(ctx, accountID, "rolled-back-memo", true); err != sql.ErrNoRows {
		t.Fatalf("rolled-back memo lookup error = %v, want sql.ErrNoRows", err)
	}
}

func TestWithTransactionCommitsRelatedMemoWrites(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)
	err := s.WithTransaction(ctx, func(txStore *store.Store) error {
		for _, id := range []string{"first", "second"} {
			if _, err := txStore.CreateMemo(ctx, &store.CreateMemo{ID: id, CreatorID: accountID, Content: "committed", EntryDate: "2026-07-30"}); err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		t.Fatalf("WithTransaction() error = %v", err)
	}
	for _, id := range []string{"first", "second"} {
		if _, err := s.GetMemo(ctx, accountID, id, true); err != nil {
			t.Fatalf("committed memo lookup error = %v", err)
		}
	}
}
