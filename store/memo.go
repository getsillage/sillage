package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
)

var ErrVersionConflict = errors.New("memo version conflict")

type Memo struct {
	ID         string
	CreatorID  sql.NullString
	Content    string
	EntryDate  string
	Version    int64
	PinnedAt   sql.NullInt64
	ArchivedAt sql.NullInt64
	CreatedAt  int64
	UpdatedAt  int64
	DeletedAt  sql.NullInt64
}

type CreateMemo struct {
	ID        string
	CreatorID string
	Content   string
	EntryDate string
}

type UpdateMemo struct {
	ID              string
	CreatorID       string
	ExpectedVersion int64
	Content         *string
	EntryDate       *string
	Pinned          *bool
	Archived        *bool
	Deleted         *bool
}

type MemoConflictError struct {
	ServerMemo *Memo
}

func (e *MemoConflictError) Error() string {
	return ErrVersionConflict.Error()
}

func (e *MemoConflictError) Unwrap() error {
	return ErrVersionConflict
}

func (s *Store) CreateMemo(ctx context.Context, create *CreateMemo) (*Memo, error) {
	id := create.ID
	if id == "" {
		generated, err := uuid.NewV7()
		if err != nil {
			return nil, fmt.Errorf("generate memo id: %w", err)
		}
		id = generated.String()
	}
	now := time.Now().UTC().UnixMilli()
	memo := &Memo{
		ID:        id,
		CreatorID: sql.NullString{String: create.CreatorID, Valid: create.CreatorID != ""},
		Content:   create.Content,
		EntryDate: create.EntryDate,
		Version:   1,
		CreatedAt: now,
		UpdatedAt: now,
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO memo (id, creator_id, content, entry_date, version, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?)`,
		memo.ID,
		nullString(create.CreatorID),
		memo.Content,
		memo.EntryDate,
		memo.Version,
		memo.CreatedAt,
		memo.UpdatedAt,
	); err != nil {
		return nil, fmt.Errorf("insert memo: %w", err)
	}
	return memo, nil
}

func (s *Store) GetMemo(ctx context.Context, accountID, id string, includeDeleted bool) (*Memo, error) {
	query := `
SELECT id, creator_id, content, entry_date, version, pinned_at, archived_at, created_at, updated_at, deleted_at
FROM memo
WHERE id = ? AND creator_id = ?`
	args := []any{id, accountID}
	if !includeDeleted {
		query += " AND deleted_at IS NULL"
	}
	row := s.driver.GetDB().QueryRowContext(ctx, query, args...)
	return scanMemo(row)
}

type ListMemoOptions struct {
	AccountID string
	Limit     int
	// LookaheadPageSize permits exactly one extra internal row for has-more
	// detection without raising the public page limit.
	LookaheadPageSize int
	IncludeDeleted    bool
	// Sync selects the forward updated_at walk (oldest first) used by sync pull
	// and the Ask candidate scan. Without it, listing is pinned-first and then
	// reverse-chronological by entry date.
	Sync           bool
	UpdatedAfter   int64
	UpdatedAfterID string
	// Pinned-first keyset cursor for "load older" pages. BeforePinned is nil for
	// legacy cursors, which retain the old date-tuple-only predicate.
	BeforePinned    *bool
	BeforeEntryDate string
	BeforeCreatedAt int64
	BeforeID        string
}

// MaxMemoListLimit caps a single memo page. Clients paginate with the cursor
// returned alongside the list rather than asking for everything at once.
const MaxMemoListLimit = 500

type SearchMemoOptions struct {
	AccountID string
	Query     string
	Limit     int
	Archived  *bool
}

func (s *Store) ListMemos(ctx context.Context, opts *ListMemoOptions) ([]*Memo, error) {
	limit := opts.Limit
	if limit <= 0 {
		limit = 50
	}
	if limit > MaxMemoListLimit && !isPageLookahead(limit, opts.LookaheadPageSize, MaxMemoListLimit) {
		limit = MaxMemoListLimit
	}
	query := `
SELECT id, creator_id, content, entry_date, version, pinned_at, archived_at, created_at, updated_at, deleted_at
FROM memo
WHERE creator_id = ?`
	args := []any{opts.AccountID}
	if !opts.IncludeDeleted {
		query += " AND deleted_at IS NULL"
	}
	if opts.Sync {
		if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
			query += " AND (updated_at > ? OR (updated_at = ? AND id > ?))"
			args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
		}
		query += " ORDER BY updated_at ASC, id ASC LIMIT ?"
	} else {
		if opts.BeforeID != "" {
			if opts.BeforePinned != nil {
				beforePinned := 0
				if *opts.BeforePinned {
					beforePinned = 1
				}
				query += ` AND (
  CASE WHEN pinned_at IS NOT NULL THEN 1 ELSE 0 END < ?
  OR (CASE WHEN pinned_at IS NOT NULL THEN 1 ELSE 0 END = ? AND (
    entry_date < ?
	OR (entry_date = ? AND created_at < ?)
	OR (entry_date = ? AND created_at = ? AND id < ?)
  ))
)`
				args = append(args,
					beforePinned, beforePinned,
					opts.BeforeEntryDate,
					opts.BeforeEntryDate, opts.BeforeCreatedAt,
					opts.BeforeEntryDate, opts.BeforeCreatedAt, opts.BeforeID,
				)
			} else {
				query += ` AND (entry_date < ?
	  OR (entry_date = ? AND created_at < ?)
	  OR (entry_date = ? AND created_at = ? AND id < ?))`
				args = append(args,
					opts.BeforeEntryDate,
					opts.BeforeEntryDate, opts.BeforeCreatedAt,
					opts.BeforeEntryDate, opts.BeforeCreatedAt, opts.BeforeID,
				)
			}
		}
		query += ` ORDER BY CASE WHEN pinned_at IS NOT NULL THEN 1 ELSE 0 END DESC,
  entry_date DESC, created_at DESC, id DESC LIMIT ?`
	}
	args = append(args, limit)

	rows, err := s.driver.GetDB().QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list memos: %w", err)
	}
	defer rows.Close()
	return scanMemos(rows)
}

func (s *Store) SearchMemos(ctx context.Context, opts *SearchMemoOptions) ([]*Memo, error) {
	query := strings.TrimSpace(opts.Query)
	if query == "" {
		return nil, nil
	}
	limit := opts.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	memos, err := s.searchMemosFTS(ctx, opts.AccountID, query, opts.Archived, limit)
	if err == nil && len(memos) > 0 {
		return memos, nil
	}
	fallback, fallbackErr := s.searchMemosLike(ctx, opts.AccountID, query, opts.Archived, limit)
	if fallbackErr != nil {
		if err != nil {
			return nil, err
		}
		return nil, fallbackErr
	}
	return fallback, nil
}

func (s *Store) searchMemosFTS(ctx context.Context, accountID, query string, archived *bool, limit int) ([]*Memo, error) {
	sqlQuery := `
SELECT memo.id, memo.creator_id, memo.content, memo.entry_date, memo.version,
  memo.pinned_at, memo.archived_at, memo.created_at, memo.updated_at, memo.deleted_at
FROM memo_fts
JOIN memo ON memo.id = memo_fts.memo_id
WHERE memo.creator_id = ? AND memo.deleted_at IS NULL` + memoArchivedSearchClause(archived) + `
  AND memo_fts MATCH ?
ORDER BY rank, memo.entry_date DESC, memo.created_at DESC, memo.id DESC
LIMIT ?`
	rows, err := s.driver.GetDB().QueryContext(ctx, sqlQuery, accountID, ftsQuery(query), limit)
	if err != nil {
		return nil, fmt.Errorf("search memos fts: %w", err)
	}
	defer rows.Close()
	return scanMemos(rows)
}

func (s *Store) searchMemosLike(ctx context.Context, accountID, query string, archived *bool, limit int) ([]*Memo, error) {
	like := "%" + escapeLike(query) + "%"
	sqlQuery := `
SELECT memo.id, memo.creator_id, memo.content, memo.entry_date, memo.version,
  memo.pinned_at, memo.archived_at, memo.created_at, memo.updated_at, memo.deleted_at
FROM memo
LEFT JOIN memo_ai ON memo_ai.memo_id = memo.id AND memo_ai.deleted_at IS NULL
WHERE memo.creator_id = ? AND memo.deleted_at IS NULL` + memoArchivedSearchClause(archived) + `
  AND (memo.content LIKE ? ESCAPE '\' OR memo_ai.summary LIKE ? ESCAPE '\')
ORDER BY memo.entry_date DESC, memo.created_at DESC, memo.id DESC
LIMIT ?`
	rows, err := s.driver.GetDB().QueryContext(ctx, sqlQuery, accountID, like, like, limit)
	if err != nil {
		return nil, fmt.Errorf("search memos like: %w", err)
	}
	defer rows.Close()
	return scanMemos(rows)
}

func memoArchivedSearchClause(archived *bool) string {
	if archived == nil {
		return ""
	}
	if *archived {
		return " AND memo.archived_at IS NOT NULL"
	}
	return " AND memo.archived_at IS NULL"
}

func (s *Store) UpdateMemo(ctx context.Context, update *UpdateMemo) (*Memo, error) {
	current, err := s.GetMemo(ctx, update.CreatorID, update.ID, true)
	if err != nil {
		return nil, err
	}
	if current.DeletedAt.Valid && (update.Deleted == nil || !*update.Deleted) {
		return nil, sql.ErrNoRows
	}
	if update.ExpectedVersion > 0 && current.Version != update.ExpectedVersion {
		return nil, &MemoConflictError{ServerMemo: current}
	}

	content := current.Content
	if update.Content != nil {
		content = *update.Content
	}
	entryDate := current.EntryDate
	if update.EntryDate != nil {
		entryDate = *update.EntryDate
	}
	pinnedAt := current.PinnedAt
	if update.Pinned != nil {
		if *update.Pinned {
			pinnedAt = sql.NullInt64{Int64: time.Now().UTC().UnixMilli(), Valid: true}
		} else {
			pinnedAt = sql.NullInt64{}
		}
	}
	archivedAt := current.ArchivedAt
	if update.Archived != nil {
		if *update.Archived {
			archivedAt = sql.NullInt64{Int64: time.Now().UTC().UnixMilli(), Valid: true}
		} else {
			archivedAt = sql.NullInt64{}
		}
	}
	deletedAt := current.DeletedAt
	if update.Deleted != nil {
		if *update.Deleted {
			deletedAt = sql.NullInt64{Int64: time.Now().UTC().UnixMilli(), Valid: true}
		} else {
			deletedAt = sql.NullInt64{}
		}
	}

	now := time.Now().UTC().UnixMilli()
	newVersion := current.Version + 1
	// Guard the version inside the UPDATE so the read-check-write is atomic:
	// a concurrent writer that bumped the version between our GetMemo and here
	// changes WHERE version, leaving RowsAffected == 0 instead of clobbering it.
	result, err := s.driver.GetDB().ExecContext(ctx, `
UPDATE memo
SET content = ?, entry_date = ?, version = ?, pinned_at = ?, archived_at = ?, deleted_at = ?, updated_at = ?
WHERE id = ? AND creator_id = ? AND version = ?`,
		content,
		entryDate,
		newVersion,
		nullableInt(pinnedAt),
		nullableInt(archivedAt),
		nullableInt(deletedAt),
		now,
		update.ID,
		update.CreatorID,
		current.Version,
	)
	if err != nil {
		return nil, fmt.Errorf("update memo: %w", err)
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return nil, fmt.Errorf("update memo rows affected: %w", err)
	}
	if affected == 0 {
		// The row vanished or its version moved under us. Re-read to report an
		// accurate conflict (or not-found) rather than a silent lost update.
		latest, getErr := s.GetMemo(ctx, update.CreatorID, update.ID, true)
		if getErr != nil {
			return nil, getErr
		}
		return nil, &MemoConflictError{ServerMemo: latest}
	}
	return s.GetMemo(ctx, update.CreatorID, update.ID, true)
}

func scanMemo(row interface {
	Scan(dest ...any) error
}) (*Memo, error) {
	var memo Memo
	if err := row.Scan(
		&memo.ID,
		&memo.CreatorID,
		&memo.Content,
		&memo.EntryDate,
		&memo.Version,
		&memo.PinnedAt,
		&memo.ArchivedAt,
		&memo.CreatedAt,
		&memo.UpdatedAt,
		&memo.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan memo: %w", err)
	}
	return &memo, nil
}

func scanMemos(rows *sql.Rows) ([]*Memo, error) {
	var memos []*Memo
	for rows.Next() {
		memo, err := scanMemo(rows)
		if err != nil {
			return nil, err
		}
		memos = append(memos, memo)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate memos: %w", err)
	}
	return memos, nil
}

func ftsQuery(query string) string {
	fields := strings.Fields(query)
	if len(fields) == 0 {
		return query
	}
	for i, field := range fields {
		fields[i] = `"` + strings.ReplaceAll(field, `"`, `""`) + `"`
	}
	return strings.Join(fields, " ")
}

func escapeLike(value string) string {
	value = strings.ReplaceAll(value, `\`, `\\`)
	value = strings.ReplaceAll(value, `%`, `\%`)
	value = strings.ReplaceAll(value, `_`, `\_`)
	return value
}

func nullString(value string) any {
	if value == "" {
		return nil
	}
	return value
}

func nullableInt(value sql.NullInt64) any {
	if !value.Valid {
		return nil
	}
	return value.Int64
}
