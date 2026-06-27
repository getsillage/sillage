package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/store"

	_ "modernc.org/sqlite"
)

type DB struct {
	db *sql.DB
}

func New(profile *profile.Profile) (store.Driver, error) {
	if profile.DSN == "" {
		return nil, fmt.Errorf("sqlite dsn is required")
	}

	sqliteDB, err := sql.Open("sqlite", withPragmas(profile.DSN))
	if err != nil {
		return nil, fmt.Errorf("open sqlite database: %w", err)
	}
	sqliteDB.SetMaxOpenConns(1)

	driver := &DB{db: sqliteDB}
	if err := driver.Ping(context.Background()); err != nil {
		_ = sqliteDB.Close()
		return nil, err
	}
	return driver, nil
}

func withPragmas(dsn string) string {
	separator := "?"
	if strings.Contains(dsn, "?") {
		separator = "&"
	}
	return dsn + separator + "_pragma=foreign_keys(ON)&_pragma=busy_timeout(10000)&_pragma=journal_mode(WAL)&_pragma=mmap_size(0)"
}

func (d *DB) GetDB() *sql.DB {
	return d.db
}

func (d *DB) Close() error {
	return d.db.Close()
}

func (d *DB) Ping(ctx context.Context) error {
	if err := d.db.PingContext(ctx); err != nil {
		return fmt.Errorf("ping sqlite database: %w", err)
	}
	return nil
}

func (d *DB) IsInitialized(ctx context.Context) (bool, error) {
	var exists bool
	err := d.db.QueryRowContext(ctx, "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'system_setting')").Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("check sqlite schema: %w", err)
	}
	return exists, nil
}
