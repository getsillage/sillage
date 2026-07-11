package store

import (
	"context"
	"database/sql"
	"embed"
	"errors"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"time"
)

//go:embed migration
var migrationFS embed.FS

const (
	latestSchemaFileName = "migration/sqlite/LATEST.sql"
	minimumSchemaVersion = "0.1.0"
	currentSchemaVersion = "0.1.3"
	schemaVersionKey     = "schema_version"
)

// Migrate initializes a fresh SQLite database with the latest schema.
func (s *Store) Migrate(ctx context.Context) error {
	initialized, err := s.driver.IsInitialized(ctx)
	if err != nil {
		return fmt.Errorf("check database initialization: %w", err)
	}
	if initialized {
		version, err := s.GetSchemaVersion(ctx)
		if err != nil {
			return fmt.Errorf("read schema version: %w", err)
		}
		if err := validateSchemaVersion(version); err != nil {
			return err
		}
		if err := s.EnsureCompatSchema(ctx); err != nil {
			return err
		}
		if version == currentSchemaVersion {
			return nil
		}
		if err := s.setSchemaVersion(ctx, currentSchemaVersion); err != nil {
			return err
		}
		slog.Info("database migrated", "from_schema_version", version, "to_schema_version", currentSchemaVersion)
		return nil
	}

	stmt, err := migrationFS.ReadFile(latestSchemaFileName)
	if err != nil {
		return fmt.Errorf("read latest schema: %w", err)
	}

	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin migration transaction: %w", err)
	}
	defer func() {
		if rollbackErr := tx.Rollback(); rollbackErr != nil && !errors.Is(rollbackErr, sql.ErrTxDone) {
			slog.Warn("failed to rollback migration transaction", "error", rollbackErr)
		}
	}()

	if _, err := tx.ExecContext(ctx, string(stmt)); err != nil {
		return fmt.Errorf("execute latest schema: %w", err)
	}
	if err := upsertSchemaVersion(ctx, tx, currentSchemaVersion); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit migration transaction: %w", err)
	}
	slog.Info("database initialized", "schema_version", currentSchemaVersion)
	return nil
}

func validateSchemaVersion(version string) error {
	minimum, err := compareSchemaVersions(version, minimumSchemaVersion)
	if err != nil {
		return fmt.Errorf("parse schema version %q: %w", version, err)
	}
	if minimum < 0 {
		return fmt.Errorf("schema version %q is older than the minimum supported version %s", version, minimumSchemaVersion)
	}
	maximum, err := compareSchemaVersions(version, currentSchemaVersion)
	if err != nil {
		return fmt.Errorf("parse schema version %q: %w", version, err)
	}
	if maximum > 0 {
		return fmt.Errorf("schema version %q is newer than this binary supports (%s)", version, currentSchemaVersion)
	}
	return nil
}

func compareSchemaVersions(left, right string) (int, error) {
	leftParts, err := parseSchemaVersion(left)
	if err != nil {
		return 0, err
	}
	rightParts, err := parseSchemaVersion(right)
	if err != nil {
		return 0, err
	}
	for index := range leftParts {
		if leftParts[index] < rightParts[index] {
			return -1, nil
		}
		if leftParts[index] > rightParts[index] {
			return 1, nil
		}
	}
	return 0, nil
}

func parseSchemaVersion(version string) ([3]int, error) {
	var parsed [3]int
	parts := strings.Split(version, ".")
	if len(parts) != len(parsed) {
		return parsed, fmt.Errorf("expected major.minor.patch")
	}
	for index, part := range parts {
		value, err := strconv.Atoi(part)
		if err != nil || value < 0 {
			return parsed, fmt.Errorf("invalid component %q", part)
		}
		parsed[index] = value
	}
	return parsed, nil
}

func (s *Store) EnsureCompatSchema(ctx context.Context) error {
	if err := s.ensureAccountSettingTable(ctx); err != nil {
		return err
	}
	if err := s.ensureAIProfileCompat(ctx); err != nil {
		return err
	}
	if err := s.ensureMemoFavoritedCompat(ctx); err != nil {
		return err
	}
	return nil
}

func (s *Store) setSchemaVersion(ctx context.Context, version string) error {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin schema version update: %w", err)
	}
	defer func() {
		if rollbackErr := tx.Rollback(); rollbackErr != nil && !errors.Is(rollbackErr, sql.ErrTxDone) {
			slog.Warn("failed to rollback schema version update", "error", rollbackErr)
		}
	}()
	if err := upsertSchemaVersion(ctx, tx, version); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit schema version update: %w", err)
	}
	return nil
}

func (s *Store) ensureMemoFavoritedCompat(ctx context.Context) error {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin memo favorite compat migration: %w", err)
	}
	defer func() {
		if rollbackErr := tx.Rollback(); rollbackErr != nil && !errors.Is(rollbackErr, sql.ErrTxDone) {
			slog.Warn("failed to rollback memo favorite compat migration", "error", rollbackErr)
		}
	}()

	exists, err := tableExists(ctx, tx, "memo")
	if err != nil {
		return err
	}
	if !exists {
		return tx.Commit()
	}
	hasFavorited, err := tableColumnExists(ctx, tx, "memo", "favorited_at")
	if err != nil {
		return err
	}
	if !hasFavorited {
		if _, err := tx.ExecContext(ctx, "ALTER TABLE memo ADD COLUMN favorited_at INTEGER"); err != nil {
			return fmt.Errorf("ensure memo favorited_at column: %w", err)
		}
	}
	hasPinned, err := tableColumnExists(ctx, tx, "memo", "pinned_at")
	if err != nil {
		return err
	}
	if hasPinned {
		if _, err := tx.ExecContext(ctx, `
UPDATE memo
SET favorited_at = COALESCE(favorited_at, pinned_at), pinned_at = NULL
WHERE pinned_at IS NOT NULL`); err != nil {
			return fmt.Errorf("migrate pinned memos to favorites: %w", err)
		}
	}
	if _, err := tx.ExecContext(ctx, "CREATE INDEX IF NOT EXISTS idx_memo_favorited_at ON memo (favorited_at)"); err != nil {
		return fmt.Errorf("ensure memo favorited index: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit memo favorite compat migration: %w", err)
	}
	return nil
}

func (s *Store) ensureAccountSettingTable(ctx context.Context) error {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin account_setting compat migration: %w", err)
	}
	defer func() {
		if rollbackErr := tx.Rollback(); rollbackErr != nil && !errors.Is(rollbackErr, sql.ErrTxDone) {
			slog.Warn("failed to rollback account_setting compat migration", "error", rollbackErr)
		}
	}()
	if _, err := tx.ExecContext(ctx, `
CREATE TABLE IF NOT EXISTS account_setting (
  account_id TEXT NOT NULL,
  key TEXT NOT NULL,
  value TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  PRIMARY KEY (account_id, key),
  FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
)`); err != nil {
		return fmt.Errorf("ensure account_setting table: %w", err)
	}
	if _, err := tx.ExecContext(ctx, "CREATE INDEX IF NOT EXISTS idx_account_setting_updated_id ON account_setting (updated_at, account_id, key)"); err != nil {
		return fmt.Errorf("ensure account_setting updated index: %w", err)
	}
	if _, err := tx.ExecContext(ctx, "CREATE INDEX IF NOT EXISTS idx_account_setting_deleted_at ON account_setting (deleted_at)"); err != nil {
		return fmt.Errorf("ensure account_setting deleted index: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit account_setting compat migration: %w", err)
	}
	return nil
}

func (s *Store) ensureAIProfileCompat(ctx context.Context) error {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin ai_profile compat migration: %w", err)
	}
	defer func() {
		if rollbackErr := tx.Rollback(); rollbackErr != nil && !errors.Is(rollbackErr, sql.ErrTxDone) {
			slog.Warn("failed to rollback ai_profile compat migration", "error", rollbackErr)
		}
	}()

	exists, err := tableExists(ctx, tx, "ai_profile")
	if err != nil {
		return err
	}
	if !exists {
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit empty ai_profile compat migration: %w", err)
		}
		return nil
	}

	for _, column := range []struct {
		name string
		sql  string
	}{
		{name: "api_key_envelope", sql: "ALTER TABLE ai_profile ADD COLUMN api_key_envelope TEXT"},
		{name: "key_unavailable", sql: "ALTER TABLE ai_profile ADD COLUMN key_unavailable INTEGER NOT NULL DEFAULT 0"},
		{name: "auto_summary", sql: "ALTER TABLE ai_profile ADD COLUMN auto_summary INTEGER NOT NULL DEFAULT 0"},
		{name: "deleted_at", sql: "ALTER TABLE ai_profile ADD COLUMN deleted_at INTEGER"},
	} {
		hasColumn, err := tableColumnExists(ctx, tx, "ai_profile", column.name)
		if err != nil {
			return err
		}
		if !hasColumn {
			if _, err := tx.ExecContext(ctx, column.sql); err != nil {
				return fmt.Errorf("ensure ai_profile %s column: %w", column.name, err)
			}
		}
	}
	if _, err := tx.ExecContext(ctx, "CREATE INDEX IF NOT EXISTS idx_ai_profile_account_id ON ai_profile (account_id)"); err != nil {
		return fmt.Errorf("ensure ai_profile account index: %w", err)
	}
	if _, err := tx.ExecContext(ctx, "CREATE INDEX IF NOT EXISTS idx_ai_profile_updated_id ON ai_profile (updated_at, id)"); err != nil {
		return fmt.Errorf("ensure ai_profile updated index: %w", err)
	}
	if _, err := tx.ExecContext(ctx, "CREATE INDEX IF NOT EXISTS idx_ai_profile_deleted_at ON ai_profile (deleted_at)"); err != nil {
		return fmt.Errorf("ensure ai_profile deleted index: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit ai_profile compat migration: %w", err)
	}
	return nil
}

func tableExists(ctx context.Context, tx *sql.Tx, table string) (bool, error) {
	var exists bool
	if err := tx.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?)", table).Scan(&exists); err != nil {
		return false, fmt.Errorf("check %s table: %w", table, err)
	}
	return exists, nil
}

func tableColumnExists(ctx context.Context, tx *sql.Tx, table, column string) (bool, error) {
	rows, err := tx.QueryContext(ctx, "PRAGMA table_info("+table+")")
	if err != nil {
		return false, fmt.Errorf("read %s columns: %w", table, err)
	}
	defer rows.Close()
	for rows.Next() {
		var cid int
		var name, columnType string
		var notNull, pk int
		var defaultValue any
		if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &pk); err != nil {
			return false, fmt.Errorf("scan %s column: %w", table, err)
		}
		if name == column {
			return true, nil
		}
	}
	if err := rows.Err(); err != nil {
		return false, fmt.Errorf("iterate %s columns: %w", table, err)
	}
	return false, nil
}

func (s *Store) GetSchemaVersion(ctx context.Context) (string, error) {
	var value string
	err := s.driver.GetDB().QueryRowContext(ctx, "SELECT value FROM system_setting WHERE key = ?", schemaVersionKey).Scan(&value)
	if err != nil {
		return "", fmt.Errorf("schema version not found: %w", err)
	}
	return value, nil
}

func upsertSchemaVersion(ctx context.Context, tx *sql.Tx, version string) error {
	now := time.Now().UTC().UnixMilli()
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO system_setting (key, value, created_at, updated_at)
VALUES (?, ?, ?, ?)
ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
		schemaVersionKey,
		version,
		now,
		now,
	); err != nil {
		return fmt.Errorf("upsert schema version: %w", err)
	}
	return nil
}
