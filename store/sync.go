package store

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

type SyncCursorPosition struct {
	UpdatedAt int64  `json:"updatedAt"`
	ID        string `json:"id"`
}

type SyncMutation struct {
	AccountID    string
	MutationID   string
	ResourceType string
	ResourceID   string
	Result       string
}

func (s *Store) GetSyncMutation(ctx context.Context, accountID, mutationID string) (*SyncMutation, bool, error) {
	var mutation SyncMutation
	err := s.driver.GetDB().QueryRowContext(ctx, `
SELECT account_id, mutation_id, resource_type, resource_id, result
FROM sync_mutation
WHERE account_id = ? AND mutation_id = ?`, accountID, mutationID).Scan(
		&mutation.AccountID,
		&mutation.MutationID,
		&mutation.ResourceType,
		&mutation.ResourceID,
		&mutation.Result,
	)
	if err == sql.ErrNoRows {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, fmt.Errorf("get sync mutation: %w", err)
	}
	return &mutation, true, nil
}

func (s *Store) PutSyncMutation(ctx context.Context, mutation *SyncMutation) error {
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO sync_mutation (account_id, mutation_id, resource_type, resource_id, result, created_at)
VALUES (?, ?, ?, ?, ?, ?)`,
		mutation.AccountID,
		mutation.MutationID,
		mutation.ResourceType,
		mutation.ResourceID,
		mutation.Result,
		time.Now().UTC().UnixMilli(),
	); err != nil {
		return fmt.Errorf("put sync mutation: %w", err)
	}
	return nil
}
