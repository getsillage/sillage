package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/google/uuid"
)

var ErrVersionConflict = errors.New("memo version conflict")

type Memo struct {
	ID          string
	CreatorID   sql.NullString
	Content     string
	EntryDate   string
	Version     int64
	FavoritedAt sql.NullInt64
	ArchivedAt  sql.NullInt64
	CreatedAt   int64
	UpdatedAt   int64
	DeletedAt   sql.NullInt64
	PurgedAt    sql.NullInt64
}

type CreateMemo struct {
	ID        string
	CreatorID string
	Content   string
	EntryDate string
	Favorited bool
	Archived  bool
}

type UpdateMemo struct {
	ID              string
	CreatorID       string
	ExpectedVersion int64
	Content         *string
	EntryDate       *string
	Favorited       *bool
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
	if create.Favorited {
		memo.FavoritedAt = sql.NullInt64{Int64: now, Valid: true}
	}
	if create.Archived {
		memo.ArchivedAt = sql.NullInt64{Int64: now, Valid: true}
	}
	if _, err := s.db().ExecContext(ctx, `
INSERT INTO memo (id, creator_id, content, entry_date, version, favorited_at, archived_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		memo.ID,
		nullString(create.CreatorID),
		memo.Content,
		memo.EntryDate,
		memo.Version,
		nullableInt(memo.FavoritedAt),
		nullableInt(memo.ArchivedAt),
		memo.CreatedAt,
		memo.UpdatedAt,
	); err != nil {
		return nil, fmt.Errorf("insert memo: %w", err)
	}
	return memo, nil
}

func (s *Store) GetMemo(ctx context.Context, accountID, id string, includeDeleted bool) (*Memo, error) {
	query := `
SELECT id, creator_id, content, entry_date, version, favorited_at, archived_at, created_at, updated_at, deleted_at, purged_at
FROM memo
WHERE id = ? AND creator_id = ?`
	args := []any{id, accountID}
	if !includeDeleted {
		query += " AND deleted_at IS NULL"
	}
	row := s.db().QueryRowContext(ctx, query, args...)
	return scanMemo(row)
}

type ListMemoOptions struct {
	AccountID string
	Limit     int
	// LookaheadPageSize permits exactly one extra internal row for has-more
	// detection without raising the public page limit.
	LookaheadPageSize int
	IncludeDeleted    bool
	Deleted           *bool
	// Sync selects the forward updated_at walk (oldest first) used by sync pull
	// and the Ask candidate scan. Without it, listing is reverse-chronological
	// by entry date.
	Sync           bool
	UpdatedAfter   int64
	UpdatedAfterID string
	Archived       *bool
	Favorited      *bool
	// LegacyFavoritedFirst continues a v1 pinned-first cursor after pinned_at
	// has been migrated to favorited_at. New list requests leave it false.
	LegacyFavoritedFirst bool
	BeforeFavorited      *bool
	BeforeEntryDate      string
	BeforeDeletedAt      int64
	BeforeCreatedAt      int64
	BeforeID             string
}

// MaxMemoListLimit caps a single memo page. Clients paginate with the cursor
// returned alongside the list rather than asking for everything at once.
const MaxMemoListLimit = 500

type SearchMemoOptions struct {
	AccountID string
	Query     string
	Limit     int
	Archived  *bool
	Favorited *bool
	Deleted   *bool
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
SELECT id, creator_id, content, entry_date, version, favorited_at, archived_at, created_at, updated_at, deleted_at, purged_at
FROM memo
WHERE creator_id = ?`
	args := []any{opts.AccountID}
	if !opts.IncludeDeleted {
		if opts.Deleted != nil && *opts.Deleted {
			query += " AND deleted_at IS NOT NULL AND purged_at IS NULL"
		} else {
			query += " AND deleted_at IS NULL"
		}
	}
	if opts.Sync {
		if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
			query += " AND (updated_at > ? OR (updated_at = ? AND id > ?))"
			args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
		}
		query += " ORDER BY updated_at ASC, id ASC LIMIT ?"
	} else {
		query += memoStateFilterClause("", opts.Archived, opts.Favorited)
		deletedOnly := opts.Deleted != nil && *opts.Deleted
		if deletedOnly {
			if opts.BeforeID != "" {
				query += ` AND (deleted_at < ?
	  OR (deleted_at = ? AND id < ?))`
				args = append(args, opts.BeforeDeletedAt, opts.BeforeDeletedAt, opts.BeforeID)
			}
			query += " ORDER BY deleted_at DESC, id DESC LIMIT ?"
		} else if opts.BeforeID != "" {
			if opts.LegacyFavoritedFirst && opts.BeforeFavorited != nil {
				beforeFavorited := 0
				if *opts.BeforeFavorited {
					beforeFavorited = 1
				}
				query += ` AND (
  CASE WHEN favorited_at IS NOT NULL THEN 1 ELSE 0 END < ?
  OR (CASE WHEN favorited_at IS NOT NULL THEN 1 ELSE 0 END = ? AND (
    entry_date < ?
	OR (entry_date = ? AND created_at < ?)
	OR (entry_date = ? AND created_at = ? AND id < ?)
  ))
)`
				args = append(args,
					beforeFavorited, beforeFavorited,
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
		if deletedOnly {
			// The deleted-only branch already selected its lifecycle ordering.
		} else if opts.LegacyFavoritedFirst {
			query += ` ORDER BY CASE WHEN favorited_at IS NOT NULL THEN 1 ELSE 0 END DESC,
  entry_date DESC, created_at DESC, id DESC LIMIT ?`
		} else {
			query += " ORDER BY entry_date DESC, created_at DESC, id DESC LIMIT ?"
		}
	}
	args = append(args, limit)

	rows, err := s.db().QueryContext(ctx, query, args...)
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
	if opts.Deleted != nil && *opts.Deleted {
		return s.searchMemosLike(ctx, opts.AccountID, query, opts.Archived, opts.Favorited, opts.Deleted, limit)
	}
	memos, err := s.searchMemosFTS(ctx, opts.AccountID, query, opts.Archived, opts.Favorited, limit)
	if err == nil && len(memos) >= limit {
		return memos, nil
	}
	fallback, fallbackErr := s.searchMemosLike(ctx, opts.AccountID, query, opts.Archived, opts.Favorited, opts.Deleted, limit)
	if fallbackErr != nil {
		if err != nil {
			return nil, err
		}
		if len(memos) > 0 {
			return memos, nil
		}
		return nil, fallbackErr
	}
	if err != nil {
		return fallback, nil
	}
	// FTS ranks content efficiently, while LIKE also sees stored summaries.
	// Merge both paths so one content hit does not suppress summary-only hits.
	seen := make(map[string]struct{}, limit)
	merged := make([]*Memo, 0, limit)
	for _, group := range [][]*Memo{memos, fallback} {
		for _, memo := range group {
			if memo == nil {
				continue
			}
			if _, duplicate := seen[memo.ID]; duplicate {
				continue
			}
			seen[memo.ID] = struct{}{}
			merged = append(merged, memo)
			if len(merged) == limit {
				return merged, nil
			}
		}
	}
	return merged, nil
}

func (s *Store) searchMemosFTS(ctx context.Context, accountID, query string, archived, favorited *bool, limit int) ([]*Memo, error) {
	sqlQuery := `
SELECT memo.id, memo.creator_id, memo.content, memo.entry_date, memo.version,
  memo.favorited_at, memo.archived_at, memo.created_at, memo.updated_at, memo.deleted_at, memo.purged_at
FROM memo_fts
JOIN memo ON memo.id = memo_fts.memo_id
WHERE memo.creator_id = ? AND memo.deleted_at IS NULL` + memoStateFilterClause("memo.", archived, favorited) + `
  AND memo_fts MATCH ?
ORDER BY rank, memo.entry_date DESC, memo.created_at DESC, memo.id DESC
LIMIT ?`
	rows, err := s.db().QueryContext(ctx, sqlQuery, accountID, ftsQuery(query), limit)
	if err != nil {
		return nil, fmt.Errorf("search memos fts: %w", err)
	}
	defer rows.Close()
	return scanMemos(rows)
}

func (s *Store) searchMemosLike(ctx context.Context, accountID, query string, archived, favorited, deleted *bool, limit int) ([]*Memo, error) {
	like := "%" + escapeLike(query) + "%"
	sqlQuery := `
SELECT memo.id, memo.creator_id, memo.content, memo.entry_date, memo.version,
  memo.favorited_at, memo.archived_at, memo.created_at, memo.updated_at, memo.deleted_at, memo.purged_at
FROM memo
LEFT JOIN memo_ai ON memo_ai.memo_id = memo.id AND memo_ai.deleted_at IS NULL
WHERE memo.creator_id = ?` + memoDeletionFilterClause("memo.", deleted) + memoStateFilterClause("memo.", archived, favorited) + `
  AND (memo.content LIKE ? ESCAPE '\' OR memo_ai.summary LIKE ? ESCAPE '\')
ORDER BY memo.entry_date DESC, memo.created_at DESC, memo.id DESC
LIMIT ?`
	rows, err := s.db().QueryContext(ctx, sqlQuery, accountID, like, like, limit)
	if err != nil {
		return nil, fmt.Errorf("search memos like: %w", err)
	}
	defer rows.Close()
	return scanMemos(rows)
}

func memoDeletionFilterClause(prefix string, deleted *bool) string {
	if deleted != nil && *deleted {
		return " AND " + prefix + "deleted_at IS NOT NULL AND " + prefix + "purged_at IS NULL"
	}
	return " AND " + prefix + "deleted_at IS NULL"
}

func memoStateFilterClause(prefix string, archived, favorited *bool) string {
	clause := ""
	if archived != nil {
		if *archived {
			clause += " AND " + prefix + "archived_at IS NOT NULL"
		} else {
			clause += " AND " + prefix + "archived_at IS NULL"
		}
	}
	if favorited != nil {
		if *favorited {
			clause += " AND " + prefix + "favorited_at IS NOT NULL"
		} else {
			clause += " AND " + prefix + "favorited_at IS NULL"
		}
	}
	return clause
}

func (s *Store) UpdateMemo(ctx context.Context, update *UpdateMemo) (*Memo, error) {
	current, err := s.GetMemo(ctx, update.CreatorID, update.ID, true)
	if err != nil {
		return nil, err
	}
	if current.DeletedAt.Valid || current.PurgedAt.Valid {
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
	favoritedAt := current.FavoritedAt
	if update.Favorited != nil {
		if *update.Favorited {
			favoritedAt = sql.NullInt64{Int64: time.Now().UTC().UnixMilli(), Valid: true}
		} else {
			favoritedAt = sql.NullInt64{}
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
	result, err := s.db().ExecContext(ctx, `
UPDATE memo
SET content = ?, entry_date = ?, version = ?, favorited_at = ?, archived_at = ?, deleted_at = ?, updated_at = ?
WHERE id = ? AND creator_id = ? AND version = ?`,
		content,
		entryDate,
		newVersion,
		nullableInt(favoritedAt),
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

func (s *Store) RestoreMemo(ctx context.Context, accountID, id string, expectedVersion int64) (*Memo, error) {
	current, err := s.GetMemo(ctx, accountID, id, true)
	if err != nil {
		return nil, err
	}
	if !current.DeletedAt.Valid || current.PurgedAt.Valid {
		return nil, sql.ErrNoRows
	}
	if expectedVersion > 0 && current.Version != expectedVersion {
		return nil, &MemoConflictError{ServerMemo: current}
	}
	now := time.Now().UTC().UnixMilli()
	result, err := s.db().ExecContext(ctx, `
UPDATE memo
SET deleted_at = NULL, version = ?, updated_at = ?
WHERE id = ? AND creator_id = ? AND version = ? AND deleted_at IS NOT NULL AND purged_at IS NULL`,
		current.Version+1, now, id, accountID, current.Version)
	if err != nil {
		return nil, fmt.Errorf("restore memo: %w", err)
	}
	return s.memoMutationResult(ctx, accountID, id, current.Version, result)
}

func (s *Store) PurgeMemo(ctx context.Context, accountID, id string, expectedVersion int64) (*Memo, error) {
	if s.tx != nil {
		return s.purgeMemo(ctx, accountID, id, expectedVersion)
	}
	var purged *Memo
	err := s.WithTransaction(ctx, func(txStore *Store) error {
		var err error
		purged, err = txStore.purgeMemo(ctx, accountID, id, expectedVersion)
		return err
	})
	return purged, err
}

func (s *Store) purgeMemo(ctx context.Context, accountID, id string, expectedVersion int64) (*Memo, error) {
	current, err := s.GetMemo(ctx, accountID, id, true)
	if err != nil {
		return nil, err
	}
	if !current.DeletedAt.Valid || current.PurgedAt.Valid {
		return nil, sql.ErrNoRows
	}
	if expectedVersion > 0 && current.Version != expectedVersion {
		return nil, &MemoConflictError{ServerMemo: current}
	}
	now := time.Now().UTC().UnixMilli()
	attachmentUIDs, err := s.memoAttachmentUIDs(ctx, accountID, id, current.Content)
	if err != nil {
		return nil, err
	}
	result, err := s.db().ExecContext(ctx, `
UPDATE memo
SET content = '', entry_date = '1970-01-01', version = ?, favorited_at = NULL,
  archived_at = NULL, updated_at = ?, purged_at = ?
WHERE id = ? AND creator_id = ? AND version = ? AND deleted_at IS NOT NULL AND purged_at IS NULL`,
		current.Version+1, now, now, id, accountID, current.Version)
	if err != nil {
		return nil, fmt.Errorf("purge memo: %w", err)
	}
	purged, err := s.memoMutationResult(ctx, accountID, id, current.Version, result)
	if err != nil {
		return nil, err
	}
	if _, err := s.db().ExecContext(ctx, "DELETE FROM memo_ai WHERE memo_id = ?", id); err != nil {
		return nil, fmt.Errorf("purge memo ai: %w", err)
	}
	if _, err := s.db().ExecContext(ctx, `
UPDATE summaries
SET title = '', content = '', source_memo_ids = '[]', updated_at = ?, deleted_at = COALESCE(deleted_at, ?)
WHERE creator_id = ? AND deleted_at IS NULL AND source_memo_ids LIKE ?`,
		now, now, accountID, "%\""+id+"\"%"); err != nil {
		return nil, fmt.Errorf("purge generated summaries: %w", err)
	}
	if _, err := s.db().ExecContext(ctx, `
UPDATE ask_messages
SET content = '', source_refs = '[]', updated_at = ?, deleted_at = COALESCE(deleted_at, ?)
WHERE deleted_at IS NULL
	  AND source_refs LIKE ?
	  AND conversation_id IN (SELECT id FROM ask_conversations WHERE creator_id = ?)`,
		now, now, "%\"memoId\":\""+id+"\"%", accountID); err != nil {
		return nil, fmt.Errorf("purge ask answers: %w", err)
	}
	for _, uid := range attachmentUIDs {
		if err := s.purgeUnsharedAttachment(ctx, accountID, id, uid, now); err != nil {
			return nil, err
		}
	}
	return purged, nil
}

func (s *Store) memoMutationResult(ctx context.Context, accountID, id string, previousVersion int64, result sql.Result) (*Memo, error) {
	affected, err := result.RowsAffected()
	if err != nil {
		return nil, fmt.Errorf("read memo mutation rows affected: %w", err)
	}
	if affected == 0 {
		latest, getErr := s.GetMemo(ctx, accountID, id, true)
		if getErr != nil {
			return nil, getErr
		}
		if latest.Version != previousVersion {
			return nil, &MemoConflictError{ServerMemo: latest}
		}
		return nil, sql.ErrNoRows
	}
	return s.GetMemo(ctx, accountID, id, true)
}

var attachmentURLPattern = regexp.MustCompile(`/file/attachments/([0-9A-Za-z-]+)/`)

func (s *Store) memoAttachmentUIDs(ctx context.Context, accountID, memoID, content string) ([]string, error) {
	seen := map[string]struct{}{}
	for _, match := range attachmentURLPattern.FindAllStringSubmatch(content, -1) {
		seen[match[1]] = struct{}{}
	}
	rows, err := s.db().QueryContext(ctx, `
SELECT uid FROM attachments
WHERE creator_id = ? AND memo_id = ? AND deleted_at IS NULL`, accountID, memoID)
	if err != nil {
		return nil, fmt.Errorf("list memo attachments for purge: %w", err)
	}
	defer rows.Close()
	for rows.Next() {
		var uid string
		if err := rows.Scan(&uid); err != nil {
			return nil, fmt.Errorf("scan memo attachment for purge: %w", err)
		}
		seen[uid] = struct{}{}
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate memo attachments for purge: %w", err)
	}
	uids := make([]string, 0, len(seen))
	for uid := range seen {
		uids = append(uids, uid)
	}
	return uids, nil
}

func (s *Store) purgeUnsharedAttachment(ctx context.Context, accountID, memoID, uid string, now int64) error {
	var references int
	if err := s.db().QueryRowContext(ctx, `
SELECT COUNT(1)
FROM memo
WHERE creator_id = ? AND id <> ? AND purged_at IS NULL
  AND content LIKE ? ESCAPE '\'`,
		accountID, memoID, "%/file/attachments/"+escapeLike(uid)+"/%").Scan(&references); err != nil {
		return fmt.Errorf("check shared attachment %s: %w", uid, err)
	}
	if references > 0 {
		return nil
	}
	if _, err := s.db().ExecContext(ctx, `
UPDATE attachments
SET deleted_at = COALESCE(deleted_at, ?), updated_at = ?
WHERE creator_id = ? AND uid = ?`, now, now, accountID, uid); err != nil {
		return fmt.Errorf("purge attachment %s: %w", uid, err)
	}
	return nil
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
		&memo.FavoritedAt,
		&memo.ArchivedAt,
		&memo.CreatedAt,
		&memo.UpdatedAt,
		&memo.DeletedAt,
		&memo.PurgedAt,
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
