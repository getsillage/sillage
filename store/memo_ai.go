package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
)

type MemoAI struct {
	MemoID        string
	Summary       sql.NullString
	Sentiment     sql.NullString
	Provider      string
	Model         string
	ProfileID     string
	PromptVersion string
	SourceMemoIDs string
	Status        string
	ErrorCode     sql.NullString
	StartedAt     sql.NullInt64
	FinishedAt    sql.NullInt64
	InputTokens   int64
	OutputTokens  int64
	TotalTokens   int64
	CreatedAt     int64
	UpdatedAt     int64
	DeletedAt     sql.NullInt64
}

type UpsertMemoAI struct {
	MemoID        string
	Summary       string
	Sentiment     string
	Provider      string
	Model         string
	ProfileID     string
	PromptVersion string
	SourceMemoIDs string
	Status        string
	ErrorCode     string
	StartedAt     *int64
	FinishedAt    *int64
	InputTokens   int64
	OutputTokens  int64
	TotalTokens   int64
}

func (s *Store) UpsertMemoAI(ctx context.Context, upsert *UpsertMemoAI) (*MemoAI, error) {
	now := time.Now().UTC().UnixMilli()
	startedAt := now
	if upsert.StartedAt != nil {
		startedAt = *upsert.StartedAt
	}
	finishedAt := sql.NullInt64{}
	if upsert.FinishedAt != nil {
		finishedAt = sql.NullInt64{Int64: *upsert.FinishedAt, Valid: true}
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO memo_ai (
  memo_id, summary, sentiment, provider, model, profile_id, prompt_version,
  source_memo_ids, status, error_code, started_at, finished_at,
  input_tokens, output_tokens, total_tokens, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(memo_id) DO UPDATE SET
  summary = CASE WHEN excluded.summary = '' THEN memo_ai.summary ELSE excluded.summary END,
  sentiment = CASE WHEN excluded.sentiment = '' THEN memo_ai.sentiment ELSE excluded.sentiment END,
  provider = excluded.provider,
  model = excluded.model,
  profile_id = excluded.profile_id,
  prompt_version = excluded.prompt_version,
  source_memo_ids = excluded.source_memo_ids,
  status = excluded.status,
  error_code = excluded.error_code,
  started_at = excluded.started_at,
  finished_at = excluded.finished_at,
  input_tokens = excluded.input_tokens,
  output_tokens = excluded.output_tokens,
  total_tokens = excluded.total_tokens,
  updated_at = excluded.updated_at,
  deleted_at = NULL`,
		upsert.MemoID,
		nullString(upsert.Summary),
		nullString(upsert.Sentiment),
		upsert.Provider,
		upsert.Model,
		upsert.ProfileID,
		upsert.PromptVersion,
		upsert.SourceMemoIDs,
		upsert.Status,
		nullString(upsert.ErrorCode),
		startedAt,
		finishedAt,
		upsert.InputTokens,
		upsert.OutputTokens,
		upsert.TotalTokens,
		now,
		now,
	); err != nil {
		return nil, fmt.Errorf("upsert memo ai: %w", err)
	}
	return s.GetMemoAI(ctx, upsert.MemoID)
}

func (s *Store) GetMemoAI(ctx context.Context, memoID string) (*MemoAI, error) {
	return scanMemoAI(s.driver.GetDB().QueryRowContext(ctx, memoAISelect()+`
WHERE memo_id = ? AND deleted_at IS NULL`, memoID))
}

type ListMemoAIOptions struct {
	Limit          int
	UpdatedAfter   int64
	UpdatedAfterID string
}

func (s *Store) ListMemoAI(ctx context.Context, opts *ListMemoAIOptions) ([]*MemoAI, error) {
	limit := opts.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query := memoAISelect() + " WHERE 1 = 1"
	args := []any{}
	if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
		query += " AND (updated_at > ? OR (updated_at = ? AND memo_id > ?))"
		args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
	}
	query += " ORDER BY updated_at ASC, memo_id ASC LIMIT ?"
	args = append(args, limit)
	rows, err := s.driver.GetDB().QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list memo ai: %w", err)
	}
	defer rows.Close()

	var items []*MemoAI
	for rows.Next() {
		item, err := scanMemoAI(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate memo ai: %w", err)
	}
	return items, nil
}

func scanMemoAI(row interface {
	Scan(dest ...any) error
}) (*MemoAI, error) {
	var ai MemoAI
	if err := row.Scan(
		&ai.MemoID,
		&ai.Summary,
		&ai.Sentiment,
		&ai.Provider,
		&ai.Model,
		&ai.ProfileID,
		&ai.PromptVersion,
		&ai.SourceMemoIDs,
		&ai.Status,
		&ai.ErrorCode,
		&ai.StartedAt,
		&ai.FinishedAt,
		&ai.InputTokens,
		&ai.OutputTokens,
		&ai.TotalTokens,
		&ai.CreatedAt,
		&ai.UpdatedAt,
		&ai.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan memo ai: %w", err)
	}
	return &ai, nil
}

func memoAISelect() string {
	return `
SELECT memo_id, summary, sentiment, provider, model, profile_id, prompt_version,
  source_memo_ids, status, error_code, started_at, finished_at,
  input_tokens, output_tokens, total_tokens, created_at, updated_at, deleted_at
FROM memo_ai `
}
