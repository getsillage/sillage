package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
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
	AccountID      string
	Limit          int
	IncludeDeleted bool
	UpdatedAfter   int64
	UpdatedAfterID string
}

func (s *Store) ListMemos(ctx context.Context, opts *ListMemoOptions) ([]*Memo, error) {
	limit := opts.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query := `
SELECT id, creator_id, content, entry_date, version, pinned_at, archived_at, created_at, updated_at, deleted_at
FROM memo
WHERE creator_id = ?`
	args := []any{opts.AccountID}
	if !opts.IncludeDeleted {
		query += " AND deleted_at IS NULL"
	}
	if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
		query += " AND (updated_at > ? OR (updated_at = ? AND id > ?))"
		args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
	}
	query += " ORDER BY updated_at ASC, id ASC LIMIT ?"
	args = append(args, limit)

	rows, err := s.driver.GetDB().QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list memos: %w", err)
	}
	defer rows.Close()

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
	if _, err := s.driver.GetDB().ExecContext(ctx, `
UPDATE memo
SET content = ?, entry_date = ?, version = ?, pinned_at = ?, archived_at = ?, deleted_at = ?, updated_at = ?
WHERE id = ? AND creator_id = ?`,
		content,
		entryDate,
		newVersion,
		nullableInt(pinnedAt),
		nullableInt(archivedAt),
		nullableInt(deletedAt),
		now,
		update.ID,
		update.CreatorID,
	); err != nil {
		return nil, fmt.Errorf("update memo: %w", err)
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
