package store

import (
	"context"
	"database/sql"
	"embed"
	"errors"
	"fmt"
	"log/slog"
	"time"
)

//go:embed migration
var migrationFS embed.FS

const (
	latestSchemaFileName = "migration/sqlite/LATEST.sql"
	currentSchemaVersion = "0.1.0"
	schemaVersionKey     = "schema_version"
)

// Migrate initializes a fresh SQLite database with the latest schema.
func (s *Store) Migrate(ctx context.Context) error {
	initialized, err := s.driver.IsInitialized(ctx)
	if err != nil {
		return fmt.Errorf("check database initialization: %w", err)
	}
	if initialized {
		if _, err := s.GetSchemaVersion(ctx); err != nil {
			return fmt.Errorf("read schema version: %w", err)
		}
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
