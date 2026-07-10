package store_test

import (
	"context"
	"errors"
	"strings"
	"sync"
	"testing"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/store"
	"github.com/getsillage/sillage/store/db"
)

func newTestStore(t *testing.T) *store.Store {
	t.Helper()
	p := &profile.Profile{Data: t.TempDir()}
	if err := p.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
	driver, err := db.NewDBDriver(p)
	if err != nil {
		t.Fatalf("NewDBDriver() error = %v", err)
	}
	s := store.New(driver, p)
	t.Cleanup(func() { _ = s.Close() })
	if err := s.Migrate(context.Background()); err != nil {
		t.Fatalf("Migrate() error = %v", err)
	}
	return s
}

func newTestAccount(t *testing.T, s *store.Store) string {
	t.Helper()
	account, err := s.CreateAccount(context.Background(), &store.CreateAccount{
		Username:          "felix",
		DisplayName:       "Felix",
		PasswordHash:      "hash",
		PasswordAlgorithm: "test",
	})
	if err != nil {
		t.Fatalf("CreateAccount() error = %v", err)
	}
	return account.ID
}

func seedMemo(t *testing.T, s *store.Store, accountID, content string) *store.Memo {
	t.Helper()
	memo, err := s.CreateMemo(context.Background(), &store.CreateMemo{
		CreatorID: accountID,
		Content:   content,
		EntryDate: "2026-06-27",
	})
	if err != nil {
		t.Fatalf("CreateMemo() error = %v", err)
	}
	return memo
}

func strptr(v string) *string { return &v }

// TestSearchMemosMatchesAndExcludesTombstones covers the FTS/LIKE search path:
// it finds matching content and never returns soft-deleted memos.
func TestSearchMemosMatchesAndExcludesTombstones(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	keep := seedMemo(t, s, account, "今天去爬山看日出")
	seedMemo(t, s, account, "完全无关的内容")
	gone := seedMemo(t, s, account, "爬山笔记需要删除")
	deleted := true
	if _, err := s.UpdateMemo(ctx, &store.UpdateMemo{
		ID:              gone.ID,
		CreatorID:       account,
		ExpectedVersion: gone.Version,
		Deleted:         &deleted,
	}); err != nil {
		t.Fatalf("soft-delete error = %v", err)
	}

	got, err := s.SearchMemos(ctx, &store.SearchMemoOptions{
		AccountID: account,
		Query:     "爬山",
		Limit:     20,
	})
	if err != nil {
		t.Fatalf("SearchMemos() error = %v", err)
	}
	ids := make([]string, 0, len(got))
	for _, m := range got {
		ids = append(ids, m.ID)
	}
	if len(ids) != 1 || ids[0] != keep.ID {
		t.Fatalf("search ids = %v, want only the kept memo %s", ids, keep.ID)
	}

	// Blank query returns nothing (callers fall back to the recent list).
	blank, err := s.SearchMemos(ctx, &store.SearchMemoOptions{AccountID: account, Query: "  "})
	if err != nil {
		t.Fatalf("blank SearchMemos() error = %v", err)
	}
	if len(blank) != 0 {
		t.Fatalf("blank query returned %d memos, want 0", len(blank))
	}
}

func TestSearchMemosFiltersArchivedBeforeLimit(t *testing.T) {
	tests := []struct {
		name    string
		query   string
		content string
	}{
		{name: "fts", query: "needle", content: "shared needle content"},
		{name: "like fallback", query: "共同短语", content: "前缀共同短语后缀"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := newTestStore(t)
			ctx := context.Background()
			account := newTestAccount(t, s)

			current, err := s.CreateMemo(ctx, &store.CreateMemo{
				CreatorID: account,
				Content:   tt.content,
				EntryDate: "2026-06-27",
			})
			if err != nil {
				t.Fatalf("CreateMemo(current) error = %v", err)
			}
			archivedMemo, err := s.CreateMemo(ctx, &store.CreateMemo{
				CreatorID: account,
				Content:   tt.content,
				EntryDate: "2026-06-26",
			})
			if err != nil {
				t.Fatalf("CreateMemo(archived) error = %v", err)
			}
			archived := true
			if _, err := s.UpdateMemo(ctx, &store.UpdateMemo{
				ID:              archivedMemo.ID,
				CreatorID:       account,
				ExpectedVersion: archivedMemo.Version,
				Archived:        &archived,
			}); err != nil {
				t.Fatalf("archive memo error = %v", err)
			}

			got, err := s.SearchMemos(ctx, &store.SearchMemoOptions{
				AccountID: account,
				Query:     tt.query,
				Limit:     1,
				Archived:  &archived,
			})
			if err != nil {
				t.Fatalf("SearchMemos(archived) error = %v", err)
			}
			if len(got) != 1 || got[0].ID != archivedMemo.ID {
				t.Fatalf("archived search = %#v, want memo %s", got, archivedMemo.ID)
			}

			archived = false
			got, err = s.SearchMemos(ctx, &store.SearchMemoOptions{
				AccountID: account,
				Query:     tt.query,
				Limit:     1,
				Archived:  &archived,
			})
			if err != nil {
				t.Fatalf("SearchMemos(current) error = %v", err)
			}
			if len(got) != 1 || got[0].ID != current.ID {
				t.Fatalf("current search = %#v, want memo %s", got, current.ID)
			}
		})
	}
}

func TestSearchMemosFiltersFavoritesBeforeLimit(t *testing.T) {
	tests := []struct {
		name    string
		query   string
		content string
	}{
		{name: "fts", query: "favorite-needle", content: "shared favorite-needle content"},
		{name: "like fallback", query: "收藏短语", content: "前缀收藏短语后缀"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := newTestStore(t)
			ctx := context.Background()
			account := newTestAccount(t, s)

			regular, err := s.CreateMemo(ctx, &store.CreateMemo{
				CreatorID: account,
				Content:   tt.content,
				EntryDate: "2026-06-27",
			})
			if err != nil {
				t.Fatalf("CreateMemo(regular) error = %v", err)
			}
			favorite, err := s.CreateMemo(ctx, &store.CreateMemo{
				CreatorID: account,
				Content:   tt.content,
				EntryDate: "2026-06-26",
			})
			if err != nil {
				t.Fatalf("CreateMemo(favorite) error = %v", err)
			}
			favorited := true
			if _, err := s.UpdateMemo(ctx, &store.UpdateMemo{
				ID:              favorite.ID,
				CreatorID:       account,
				ExpectedVersion: favorite.Version,
				Favorited:       &favorited,
			}); err != nil {
				t.Fatalf("favorite memo error = %v", err)
			}

			got, err := s.SearchMemos(ctx, &store.SearchMemoOptions{
				AccountID: account,
				Query:     tt.query,
				Limit:     1,
				Favorited: &favorited,
			})
			if err != nil {
				t.Fatalf("SearchMemos(favorite) error = %v", err)
			}
			if len(got) != 1 || got[0].ID != favorite.ID {
				t.Fatalf("favorite search = %#v, want memo %s", got, favorite.ID)
			}

			favorited = false
			got, err = s.SearchMemos(ctx, &store.SearchMemoOptions{
				AccountID: account,
				Query:     tt.query,
				Limit:     1,
				Favorited: &favorited,
			})
			if err != nil {
				t.Fatalf("SearchMemos(regular) error = %v", err)
			}
			if len(got) != 1 || got[0].ID != regular.ID {
				t.Fatalf("regular search = %#v, want memo %s", got, regular.ID)
			}
		})
	}
}

// TestListMemosNewestFirstAndPaginates proves the recent list is
// reverse-chronological by entry date and that its keyset cursor walks every
// page without gaps or overlap.
func TestListMemosNewestFirstAndPaginates(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	dates := []string{"2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05"}
	for _, d := range dates {
		if _, err := s.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account,
			Content:   "entry " + d,
			EntryDate: d,
		}); err != nil {
			t.Fatalf("CreateMemo(%s) error = %v", d, err)
		}
	}

	var got []string
	opts := &store.ListMemoOptions{AccountID: account, Limit: 2}
	for {
		page, err := s.ListMemos(ctx, opts)
		if err != nil {
			t.Fatalf("ListMemos() error = %v", err)
		}
		for _, m := range page {
			got = append(got, m.EntryDate)
		}
		if len(page) < opts.Limit {
			break
		}
		last := page[len(page)-1]
		opts = &store.ListMemoOptions{
			AccountID:       account,
			Limit:           2,
			BeforeEntryDate: last.EntryDate,
			BeforeCreatedAt: last.CreatedAt,
			BeforeID:        last.ID,
		}
	}

	want := "2026-01-05,2026-01-04,2026-01-03,2026-01-02,2026-01-01"
	if strings.Join(got, ",") != want {
		t.Fatalf("paged entry dates = %v, want %s", got, want)
	}
}

func TestListMemosFiltersFavoritesBeforePagination(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	byDate := make(map[string]*store.Memo)
	for _, entryDate := range []string{"2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05"} {
		memo, err := s.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account,
			Content:   "entry " + entryDate,
			EntryDate: entryDate,
		})
		if err != nil {
			t.Fatalf("CreateMemo(%s) error = %v", entryDate, err)
		}
		byDate[entryDate] = memo
	}
	favorited := true
	for _, entryDate := range []string{"2026-01-01", "2026-01-02"} {
		memo := byDate[entryDate]
		updated, err := s.UpdateMemo(ctx, &store.UpdateMemo{
			ID:              memo.ID,
			CreatorID:       account,
			ExpectedVersion: memo.Version,
			Favorited:       &favorited,
		})
		if err != nil {
			t.Fatalf("favorite memo %s: %v", entryDate, err)
		}
		byDate[entryDate] = updated
	}
	archived := true
	for _, entryDate := range []string{"2026-01-01", "2026-01-03"} {
		memo := byDate[entryDate]
		updated, err := s.UpdateMemo(ctx, &store.UpdateMemo{
			ID:              memo.ID,
			CreatorID:       account,
			ExpectedVersion: memo.Version,
			Archived:        &archived,
		})
		if err != nil {
			t.Fatalf("archive memo %s: %v", entryDate, err)
		}
		byDate[entryDate] = updated
	}

	var got []string
	opts := &store.ListMemoOptions{AccountID: account, Limit: 1, Favorited: &favorited}
	for {
		page, err := s.ListMemos(ctx, opts)
		if err != nil {
			t.Fatalf("ListMemos() error = %v", err)
		}
		for _, memo := range page {
			got = append(got, memo.ID)
		}
		if len(page) < opts.Limit {
			break
		}
		last := page[len(page)-1]
		opts = &store.ListMemoOptions{
			AccountID:       account,
			Limit:           1,
			Favorited:       &favorited,
			BeforeEntryDate: last.EntryDate,
			BeforeCreatedAt: last.CreatedAt,
			BeforeID:        last.ID,
		}
	}

	want := []string{
		byDate["2026-01-02"].ID,
		byDate["2026-01-01"].ID,
	}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("paged ids = %v, want %v", got, want)
	}

	listIDs := func(archived, favorited bool) []string {
		t.Helper()
		page, err := s.ListMemos(ctx, &store.ListMemoOptions{
			AccountID: account,
			Limit:     10,
			Archived:  &archived,
			Favorited: &favorited,
		})
		if err != nil {
			t.Fatalf("ListMemos(archived=%t, favorited=%t): %v", archived, favorited, err)
		}
		ids := make([]string, 0, len(page))
		for _, memo := range page {
			ids = append(ids, memo.ID)
		}
		return ids
	}
	if got := listIDs(false, false); strings.Join(got, ",") != strings.Join([]string{byDate["2026-01-05"].ID, byDate["2026-01-04"].ID}, ",") {
		t.Fatalf("unarchived non-favorites = %v", got)
	}
	if got := listIDs(true, false); len(got) != 1 || got[0] != byDate["2026-01-03"].ID {
		t.Fatalf("archived non-favorites = %v", got)
	}
}

func TestListMemosContinuesLegacyFavoriteFirstCursor(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	byDate := make(map[string]*store.Memo)
	for _, entryDate := range []string{"2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05"} {
		memo, err := s.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account,
			Content:   "entry " + entryDate,
			EntryDate: entryDate,
		})
		if err != nil {
			t.Fatalf("CreateMemo(%s) error = %v", entryDate, err)
		}
		byDate[entryDate] = memo
	}
	favorited := true
	for _, entryDate := range []string{"2026-01-01", "2026-01-02"} {
		memo := byDate[entryDate]
		updated, err := s.UpdateMemo(ctx, &store.UpdateMemo{
			ID:              memo.ID,
			CreatorID:       account,
			ExpectedVersion: memo.Version,
			Favorited:       &favorited,
		})
		if err != nil {
			t.Fatalf("favorite memo %s: %v", entryDate, err)
		}
		byDate[entryDate] = updated
	}

	// An old v1 page of size one already returned the newest favorite (01-02).
	last := byDate["2026-01-02"]
	beforeFavorited := true
	opts := &store.ListMemoOptions{
		AccountID:            account,
		Limit:                2,
		LegacyFavoritedFirst: true,
		BeforeFavorited:      &beforeFavorited,
		BeforeEntryDate:      last.EntryDate,
		BeforeCreatedAt:      last.CreatedAt,
		BeforeID:             last.ID,
	}
	var got []string
	for {
		page, err := s.ListMemos(ctx, opts)
		if err != nil {
			t.Fatalf("ListMemos() error = %v", err)
		}
		for _, memo := range page {
			got = append(got, memo.ID)
		}
		if len(page) < opts.Limit {
			break
		}
		last = page[len(page)-1]
		beforeFavorited = last.FavoritedAt.Valid
		opts.BeforeFavorited = &beforeFavorited
		opts.BeforeEntryDate = last.EntryDate
		opts.BeforeCreatedAt = last.CreatedAt
		opts.BeforeID = last.ID
	}
	want := []string{
		byDate["2026-01-01"].ID,
		byDate["2026-01-05"].ID,
		byDate["2026-01-04"].ID,
		byDate["2026-01-03"].ID,
	}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("legacy continuation ids = %v, want %v", got, want)
	}
}

func TestListMemosDateTuplePredicate(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	byDate := make(map[string]*store.Memo)
	for _, entryDate := range []string{"2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05"} {
		memo, err := s.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account,
			Content:   "entry " + entryDate,
			EntryDate: entryDate,
		})
		if err != nil {
			t.Fatalf("CreateMemo(%s) error = %v", entryDate, err)
		}
		byDate[entryDate] = memo
	}
	last := byDate["2026-01-04"]
	page, err := s.ListMemos(ctx, &store.ListMemoOptions{
		AccountID:       account,
		Limit:           10,
		BeforeEntryDate: last.EntryDate,
		BeforeCreatedAt: last.CreatedAt,
		BeforeID:        last.ID,
	})
	if err != nil {
		t.Fatalf("ListMemos() error = %v", err)
	}
	got := make([]string, 0, len(page))
	for _, memo := range page {
		got = append(got, memo.ID)
	}
	want := []string{byDate["2026-01-03"].ID, byDate["2026-01-02"].ID}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("legacy cursor ids = %v, want %v", got, want)
	}
}

// TestUpdateMemoStaleVersionConflict locks in that an update carrying a stale
// expected version is rejected as a conflict rather than clobbering the row.
func TestUpdateMemoStaleVersionConflict(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)
	memo := seedMemo(t, s, account, "v1")

	updated, err := s.UpdateMemo(ctx, &store.UpdateMemo{
		ID:              memo.ID,
		CreatorID:       account,
		ExpectedVersion: memo.Version,
		Content:         strptr("v2"),
	})
	if err != nil {
		t.Fatalf("first UpdateMemo() error = %v", err)
	}
	if updated.Version != memo.Version+1 {
		t.Fatalf("version = %d, want %d", updated.Version, memo.Version+1)
	}

	// Re-using the now-stale version must conflict and expose the server state.
	_, err = s.UpdateMemo(ctx, &store.UpdateMemo{
		ID:              memo.ID,
		CreatorID:       account,
		ExpectedVersion: memo.Version,
		Content:         strptr("v3"),
	})
	var conflict *store.MemoConflictError
	if !errors.As(err, &conflict) {
		t.Fatalf("stale update error = %v, want MemoConflictError", err)
	}
	if conflict.ServerMemo == nil || conflict.ServerMemo.Content != "v2" {
		t.Fatalf("conflict server memo = %+v, want content v2", conflict.ServerMemo)
	}
}

// TestUpdateMemoConcurrentSingleWinner proves the version guard is atomic: with
// many concurrent updates all reading the same base version, exactly one wins
// and the rest conflict — no lost updates.
func TestUpdateMemoConcurrentSingleWinner(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)
	memo := seedMemo(t, s, account, "base")

	const writers = 8
	var wg sync.WaitGroup
	var mu sync.Mutex
	var successes, conflicts int
	wg.Add(writers)
	for i := 0; i < writers; i++ {
		go func(n int) {
			defer wg.Done()
			_, err := s.UpdateMemo(ctx, &store.UpdateMemo{
				ID:              memo.ID,
				CreatorID:       account,
				ExpectedVersion: memo.Version, // every writer races from the same base
				Content:         strptr("concurrent"),
			})
			mu.Lock()
			defer mu.Unlock()
			var conflict *store.MemoConflictError
			switch {
			case err == nil:
				successes++
			case errors.As(err, &conflict):
				conflicts++
			default:
				t.Errorf("unexpected error = %v", err)
			}
		}(i)
	}
	wg.Wait()

	if successes != 1 {
		t.Fatalf("successes = %d, want exactly 1", successes)
	}
	if conflicts != writers-1 {
		t.Fatalf("conflicts = %d, want %d", conflicts, writers-1)
	}

	final, err := s.GetMemo(ctx, account, memo.ID, false)
	if err != nil {
		t.Fatalf("GetMemo() error = %v", err)
	}
	if final.Version != memo.Version+1 {
		t.Fatalf("final version = %d, want %d (no lost updates)", final.Version, memo.Version+1)
	}
}
