package memo

import (
	"context"
	"database/sql"
	"errors"
	"testing"

	"github.com/getsillage/sillage/store"
)

type fakeRepository struct {
	listMemos   func(context.Context, *store.ListMemoOptions) ([]*store.Memo, error)
	searchMemos func(context.Context, *store.SearchMemoOptions) ([]*store.Memo, error)
	createMemo  func(context.Context, *store.CreateMemo) (*store.Memo, error)
	getMemo     func(context.Context, string, string, bool) (*store.Memo, error)
	updateMemo  func(context.Context, *store.UpdateMemo) (*store.Memo, error)
	getMemoAI   func(context.Context, string) (*store.MemoAI, error)
}

func (f *fakeRepository) ListMemos(ctx context.Context, opts *store.ListMemoOptions) ([]*store.Memo, error) {
	return f.listMemos(ctx, opts)
}

func (f *fakeRepository) SearchMemos(ctx context.Context, opts *store.SearchMemoOptions) ([]*store.Memo, error) {
	return f.searchMemos(ctx, opts)
}

func (f *fakeRepository) CreateMemo(ctx context.Context, input *store.CreateMemo) (*store.Memo, error) {
	return f.createMemo(ctx, input)
}

func (f *fakeRepository) GetMemo(ctx context.Context, accountID, id string, includeDeleted bool) (*store.Memo, error) {
	return f.getMemo(ctx, accountID, id, includeDeleted)
}

func (f *fakeRepository) UpdateMemo(ctx context.Context, input *store.UpdateMemo) (*store.Memo, error) {
	return f.updateMemo(ctx, input)
}

func (f *fakeRepository) GetMemoAI(ctx context.Context, memoID string) (*store.MemoAI, error) {
	return f.getMemoAI(ctx, memoID)
}

func TestServiceCreateValidatesAndSchedules(t *testing.T) {
	created := 0
	repository := &fakeRepository{
		createMemo: func(_ context.Context, input *store.CreateMemo) (*store.Memo, error) {
			created++
			if input.CreatorID != "account-1" || input.Content != "记录正文" || input.EntryDate != "2026-07-11" {
				t.Fatalf("CreateMemo input = %#v", input)
			}
			return &store.Memo{ID: "memo-1"}, nil
		},
	}
	var scheduledAccount, scheduledMemo string
	service := NewService(repository, func(accountID, memoID string) {
		scheduledAccount = accountID
		scheduledMemo = memoID
	})

	if _, err := service.Create(context.Background(), "account-1", CreateInput{EntryDate: "2026-07-11"}); !errors.Is(err, ErrValidation) {
		t.Fatalf("Create() validation error = %v", err)
	}
	if created != 0 {
		t.Fatalf("CreateMemo calls after invalid input = %d", created)
	}

	memo, err := service.Create(context.Background(), "account-1", CreateInput{
		Content:   "记录正文",
		EntryDate: "2026-07-11",
	})
	if err != nil {
		t.Fatalf("Create() error = %v", err)
	}
	if memo.ID != "memo-1" || scheduledAccount != "account-1" || scheduledMemo != "memo-1" {
		t.Fatalf("Create() memo/schedule = %#v %q %q", memo, scheduledAccount, scheduledMemo)
	}
}

func TestServiceListBuildsOpaqueCursor(t *testing.T) {
	var calls []*store.ListMemoOptions
	repository := &fakeRepository{
		listMemos: func(_ context.Context, opts *store.ListMemoOptions) ([]*store.Memo, error) {
			copy := *opts
			calls = append(calls, &copy)
			if len(calls) == 1 {
				return []*store.Memo{
					{ID: "memo-2", EntryDate: "2026-07-11", CreatedAt: 20},
					{ID: "memo-1", EntryDate: "2026-07-10", CreatedAt: 10},
				}, nil
			}
			return []*store.Memo{{ID: "memo-1", EntryDate: "2026-07-10", CreatedAt: 10}}, nil
		},
	}
	service := NewService(repository, nil)

	first, err := service.List(context.Background(), "account-1", ListInput{Limit: 1})
	if err != nil {
		t.Fatalf("List() first page error = %v", err)
	}
	if len(first.Memos) != 1 || first.Memos[0].ID != "memo-2" || first.NextCursor == "" {
		t.Fatalf("List() first page = %#v", first)
	}
	second, err := service.List(context.Background(), "account-1", ListInput{Limit: 1, Cursor: first.NextCursor})
	if err != nil {
		t.Fatalf("List() second page error = %v", err)
	}
	if len(second.Memos) != 1 || second.Memos[0].ID != "memo-1" || second.NextCursor != "" {
		t.Fatalf("List() second page = %#v", second)
	}
	if len(calls) != 2 {
		t.Fatalf("ListMemos calls = %d, want 2", len(calls))
	}
	if calls[1].BeforeEntryDate != "2026-07-11" || calls[1].BeforeCreatedAt != 20 || calls[1].BeforeID != "memo-2" {
		t.Fatalf("ListMemos second options = %#v", calls[1])
	}
}

func TestServiceUpdateRejectsInvalidMutations(t *testing.T) {
	updated := 0
	repository := &fakeRepository{
		updateMemo: func(_ context.Context, input *store.UpdateMemo) (*store.Memo, error) {
			updated++
			return &store.Memo{ID: input.ID, Version: input.ExpectedVersion + 1}, nil
		},
	}
	service := NewService(repository, nil)

	for _, input := range []UpdateInput{
		{ID: "memo-1"},
		{ID: "memo-1", ExpectedVersion: 1, Content: stringPointer("")},
		{ID: "memo-1", ExpectedVersion: 1, EntryDate: stringPointer("2026/07/11")},
	} {
		if _, err := service.Update(context.Background(), "account-1", input); !errors.Is(err, ErrValidation) {
			t.Fatalf("Update(%#v) error = %v", input, err)
		}
	}
	if updated != 0 {
		t.Fatalf("UpdateMemo calls after invalid input = %d", updated)
	}
}

func TestServiceGetTreatsMissingSummaryAsOptional(t *testing.T) {
	repository := &fakeRepository{
		getMemo: func(_ context.Context, accountID, id string, includeDeleted bool) (*store.Memo, error) {
			if accountID != "account-1" || id != "memo-1" || includeDeleted {
				t.Fatalf("GetMemo args = %q %q %t", accountID, id, includeDeleted)
			}
			return &store.Memo{ID: id}, nil
		},
		getMemoAI: func(context.Context, string) (*store.MemoAI, error) {
			return nil, sql.ErrNoRows
		},
	}
	service := NewService(repository, nil)
	detail, err := service.Get(context.Background(), "account-1", "memo-1")
	if err != nil || detail.Memo.ID != "memo-1" || detail.AI != nil {
		t.Fatalf("Get() detail/error = %#v %v", detail, err)
	}

	readErr := errors.New("read failed")
	repository.getMemoAI = func(context.Context, string) (*store.MemoAI, error) {
		return nil, readErr
	}
	if _, err := service.Get(context.Background(), "account-1", "memo-1"); !errors.Is(err, ErrSummaryRead) || !errors.Is(err, readErr) {
		t.Fatalf("Get() summary error = %v", err)
	}
}

func stringPointer(value string) *string {
	return &value
}
