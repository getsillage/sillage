package store

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

func (s *Store) RuntimeKVGet(ctx context.Context, namespace, key string) (string, bool, error) {
	var value string
	var expiresAt sql.NullInt64
	err := s.driver.GetDB().QueryRowContext(ctx, `
SELECT value, expires_at
FROM runtime_kv
WHERE namespace = ? AND key = ?`, namespace, key).Scan(&value, &expiresAt)
	if err == sql.ErrNoRows {
		return "", false, nil
	}
	if err != nil {
		return "", false, fmt.Errorf("get runtime kv: %w", err)
	}
	if expiresAt.Valid && expiresAt.Int64 <= time.Now().UTC().UnixMilli() {
		if delErr := s.RuntimeKVDelete(ctx, namespace, key); delErr != nil {
			return "", false, delErr
		}
		return "", false, nil
	}
	return value, true, nil
}

func (s *Store) RuntimeKVPut(ctx context.Context, namespace, key, value string, ttl time.Duration) error {
	var expiresAt any
	if ttl > 0 {
		expiresAt = time.Now().Add(ttl).UTC().UnixMilli()
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO runtime_kv (namespace, key, value, expires_at)
VALUES (?, ?, ?, ?)
ON CONFLICT(namespace, key) DO UPDATE SET value = excluded.value, expires_at = excluded.expires_at`,
		namespace,
		key,
		value,
		expiresAt,
	); err != nil {
		return fmt.Errorf("put runtime kv: %w", err)
	}
	return nil
}

func (s *Store) RuntimeKVDelete(ctx context.Context, namespace, key string) error {
	if _, err := s.driver.GetDB().ExecContext(ctx, "DELETE FROM runtime_kv WHERE namespace = ? AND key = ?", namespace, key); err != nil {
		return fmt.Errorf("delete runtime kv: %w", err)
	}
	return nil
}
