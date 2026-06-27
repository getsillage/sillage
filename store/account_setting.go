package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
)

func (s *Store) GetAccountSetting(ctx context.Context, accountID, key string) (string, bool, error) {
	var value string
	err := s.driver.GetDB().QueryRowContext(ctx, `
SELECT value
FROM account_setting
WHERE account_id = ? AND key = ? AND deleted_at IS NULL`, accountID, key).Scan(&value)
	if err == nil {
		return value, true, nil
	}
	if errors.Is(err, sql.ErrNoRows) {
		return "", false, nil
	}
	return "", false, fmt.Errorf("get account setting: %w", err)
}

func (s *Store) PutAccountSetting(ctx context.Context, accountID, key, value string) error {
	now := time.Now().UTC().UnixMilli()
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO account_setting (account_id, key, value, created_at, updated_at, deleted_at)
VALUES (?, ?, ?, ?, ?, NULL)
ON CONFLICT(account_id, key) DO UPDATE SET
  value = excluded.value,
  updated_at = excluded.updated_at,
  deleted_at = NULL`,
		accountID,
		key,
		value,
		now,
		now,
	); err != nil {
		return fmt.Errorf("put account setting: %w", err)
	}
	return nil
}
