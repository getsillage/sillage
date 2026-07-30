package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
)

const SyncMutationRetention = 90 * 24 * time.Hour
const DeletedMemoRetention = 30 * 24 * time.Hour

type MaintenanceStats struct {
	Sessions      int64
	RuntimeValues int64
	SyncMutations int64
}

type AttachmentStorageRef struct {
	StorageRef string
	Deleted    bool
}

// PurgeExpiredMemos scrubs records after the recovery window while retaining
// a minimal tombstone so long-offline clients can still converge.
func (s *Store) PurgeExpiredMemos(ctx context.Context, now time.Time) (int64, error) {
	rows, err := s.driver.GetDB().QueryContext(ctx, `
SELECT id, creator_id, version
FROM memo
WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at <= ? AND creator_id IS NOT NULL
ORDER BY deleted_at ASC, id ASC`, now.Add(-DeletedMemoRetention).UTC().UnixMilli())
	if err != nil {
		return 0, fmt.Errorf("list expired deleted memos: %w", err)
	}
	type expiredMemo struct {
		id        string
		accountID string
		version   int64
	}
	var expired []expiredMemo
	for rows.Next() {
		var item expiredMemo
		if err := rows.Scan(&item.id, &item.accountID, &item.version); err != nil {
			rows.Close()
			return 0, fmt.Errorf("scan expired deleted memo: %w", err)
		}
		expired = append(expired, item)
	}
	if err := rows.Close(); err != nil {
		return 0, fmt.Errorf("close expired deleted memo rows: %w", err)
	}
	if err := rows.Err(); err != nil {
		return 0, fmt.Errorf("iterate expired deleted memos: %w", err)
	}
	var purged int64
	for _, item := range expired {
		if _, err := s.PurgeMemo(ctx, item.accountID, item.id, item.version); err != nil {
			if errors.Is(err, sql.ErrNoRows) || errors.Is(err, ErrVersionConflict) {
				continue
			}
			return purged, fmt.Errorf("purge expired memo %s: %w", item.id, err)
		}
		purged++
	}
	return purged, nil
}

// CleanupEphemeralData removes rows that are no longer part of durable user
// state. Resource tombstones are intentionally excluded because offline clients
// need them to converge after long gaps.
func (s *Store) CleanupEphemeralData(ctx context.Context, now time.Time) (MaintenanceStats, error) {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return MaintenanceStats{}, fmt.Errorf("begin ephemeral cleanup: %w", err)
	}
	defer tx.Rollback()

	stats := MaintenanceStats{}
	stats.Sessions, err = deleteAndCount(ctx, tx, `
DELETE FROM session
WHERE expires_at <= ? OR deleted_at IS NOT NULL`, now.UTC().UnixMilli())
	if err != nil {
		return MaintenanceStats{}, fmt.Errorf("cleanup sessions: %w", err)
	}
	stats.RuntimeValues, err = deleteAndCount(ctx, tx, `
DELETE FROM runtime_kv
WHERE expires_at IS NOT NULL AND expires_at <= ?`, now.UTC().UnixMilli())
	if err != nil {
		return MaintenanceStats{}, fmt.Errorf("cleanup runtime values: %w", err)
	}
	stats.SyncMutations, err = deleteAndCount(ctx, tx, `
DELETE FROM sync_mutation
WHERE created_at < ?`, now.Add(-SyncMutationRetention).UTC().UnixMilli())
	if err != nil {
		return MaintenanceStats{}, fmt.Errorf("cleanup sync mutations: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return MaintenanceStats{}, fmt.Errorf("commit ephemeral cleanup: %w", err)
	}
	return stats, nil
}

func (s *Store) ListAttachmentStorageRefs(ctx context.Context) ([]AttachmentStorageRef, error) {
	rows, err := s.driver.GetDB().QueryContext(ctx, `
SELECT storage_ref, deleted_at
FROM attachments
WHERE storage_type = 'local'`)
	if err != nil {
		return nil, fmt.Errorf("list attachment storage refs: %w", err)
	}
	defer rows.Close()
	refs := make([]AttachmentStorageRef, 0)
	for rows.Next() {
		var ref AttachmentStorageRef
		var deletedAt sql.NullInt64
		if err := rows.Scan(&ref.StorageRef, &deletedAt); err != nil {
			return nil, fmt.Errorf("scan attachment storage ref: %w", err)
		}
		ref.Deleted = deletedAt.Valid
		refs = append(refs, ref)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate attachment storage refs: %w", err)
	}
	return refs, nil
}

func deleteAndCount(ctx context.Context, tx *sql.Tx, query string, args ...any) (int64, error) {
	result, err := tx.ExecContext(ctx, query, args...)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}
