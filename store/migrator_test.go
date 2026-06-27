package store_test

import (
	"context"
	"database/sql"
	"path/filepath"
	"testing"
	"time"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/store"
	"github.com/getsillage/sillage/store/db"
	_ "modernc.org/sqlite"
)

func TestMigrateFreshInstall(t *testing.T) {
	ctx := context.Background()
	p := &profile.Profile{Data: t.TempDir()}
	if err := p.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
	driver, err := db.NewDBDriver(p)
	if err != nil {
		t.Fatalf("NewDBDriver() error = %v", err)
	}
	s := store.New(driver, p)
	defer s.Close()

	if err := s.Migrate(ctx); err != nil {
		t.Fatalf("Migrate() error = %v", err)
	}

	version, err := s.GetSchemaVersion(ctx)
	if err != nil {
		t.Fatalf("GetSchemaVersion() error = %v", err)
	}
	if version == "" {
		t.Fatal("schema version is empty")
	}

	for _, table := range []string{
		"system_setting",
		"account",
		"account_setting",
		"memo",
		"attachments",
		"summaries",
		"ask_conversations",
		"ask_messages",
		"memo_fts",
		"runtime_kv",
	} {
		if !tableExists(t, s, table) {
			t.Fatalf("expected table %s to exist", table)
		}
	}
	for _, table := range []string{"tag", "memo_relation", "reaction", "memo_share"} {
		if tableExists(t, s, table) {
			t.Fatalf("table %s must not exist in Sillage schema", table)
		}
	}

	if filepath.Base(p.DSN) != profile.DefaultSQLiteFile {
		t.Fatalf("database file = %q, want %q", filepath.Base(p.DSN), profile.DefaultSQLiteFile)
	}
}

func TestSQLitePragmas(t *testing.T) {
	ctx := context.Background()
	p := &profile.Profile{Data: t.TempDir()}
	if err := p.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
	driver, err := db.NewDBDriver(p)
	if err != nil {
		t.Fatalf("NewDBDriver() error = %v", err)
	}
	s := store.New(driver, p)
	defer s.Close()

	var foreignKeys int
	if err := s.GetDriver().GetDB().QueryRowContext(ctx, "PRAGMA foreign_keys").Scan(&foreignKeys); err != nil {
		t.Fatalf("read foreign_keys pragma: %v", err)
	}
	if foreignKeys != 1 {
		t.Fatalf("foreign_keys = %d, want 1", foreignKeys)
	}

	var busyTimeout int
	if err := s.GetDriver().GetDB().QueryRowContext(ctx, "PRAGMA busy_timeout").Scan(&busyTimeout); err != nil {
		t.Fatalf("read busy_timeout pragma: %v", err)
	}
	if busyTimeout != 10000 {
		t.Fatalf("busy_timeout = %d, want 10000", busyTimeout)
	}
}

func TestMigrateExistingDatabaseAddsAccountSetting(t *testing.T) {
	ctx := context.Background()
	dataDir := t.TempDir()
	p := &profile.Profile{Data: dataDir}
	if err := p.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
	seedDB, err := sql.Open("sqlite", p.DSN)
	if err != nil {
		t.Fatalf("open seed db: %v", err)
	}
	now := time.Now().UTC().UnixMilli()
	if _, err := seedDB.ExecContext(ctx, `
CREATE TABLE system_setting (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE TABLE account (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL DEFAULT '',
  password_hash TEXT NOT NULL,
  password_algorithm TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE TABLE ai_profile (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  name TEXT NOT NULL,
  provider TEXT NOT NULL,
  base_url TEXT NOT NULL DEFAULT '',
  model TEXT NOT NULL DEFAULT '',
  temperature REAL NOT NULL DEFAULT 0.3,
  max_tokens INTEGER NOT NULL DEFAULT 1000,
  enabled INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 0,
  api_key_envelope TEXT,
  key_unavailable INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
INSERT INTO system_setting (key, value, created_at, updated_at) VALUES ('schema_version', '0.1.0', ?, ?);
INSERT INTO account (id, username, password_hash, created_at, updated_at) VALUES ('a1', 'felix', 'hash', ?, ?);`,
		now, now, now, now); err != nil {
		t.Fatalf("seed old schema: %v", err)
	}
	if err := seedDB.Close(); err != nil {
		t.Fatalf("close seed db: %v", err)
	}

	driver, err := db.NewDBDriver(p)
	if err != nil {
		t.Fatalf("NewDBDriver() error = %v", err)
	}
	s := store.New(driver, p)
	defer s.Close()

	if err := s.Migrate(ctx); err != nil {
		t.Fatalf("Migrate() error = %v", err)
	}
	if !tableExists(t, s, "account_setting") {
		t.Fatal("account_setting table was not added")
	}
	if !columnExists(t, s, "ai_profile", "auto_summary") {
		t.Fatal("ai_profile auto_summary column was not added")
	}
	if err := s.PutAccountSetting(ctx, "a1", "ai.auto_summary", "true"); err != nil {
		t.Fatalf("PutAccountSetting() after compat migration error = %v", err)
	}
	value, ok, err := s.GetAccountSetting(ctx, "a1", "ai.auto_summary")
	if err != nil || !ok || value != "true" {
		t.Fatalf("GetAccountSetting() = %q, %v, %v; want true, true, nil", value, ok, err)
	}
}

func tableExists(t *testing.T, s *store.Store, name string) bool {
	t.Helper()
	var exists bool
	if err := s.GetDriver().GetDB().QueryRow("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?)", name).Scan(&exists); err != nil {
		t.Fatalf("tableExists(%s) query error = %v", name, err)
	}
	return exists
}

func columnExists(t *testing.T, s *store.Store, table, column string) bool {
	t.Helper()
	rows, err := s.GetDriver().GetDB().Query("PRAGMA table_info(" + table + ")")
	if err != nil {
		t.Fatalf("columnExists(%s.%s) query error = %v", table, column, err)
	}
	defer rows.Close()
	for rows.Next() {
		var cid int
		var name, columnType string
		var notNull, pk int
		var defaultValue any
		if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &pk); err != nil {
			t.Fatalf("columnExists(%s.%s) scan error = %v", table, column, err)
		}
		if name == column {
			return true
		}
	}
	if err := rows.Err(); err != nil {
		t.Fatalf("columnExists(%s.%s) rows error = %v", table, column, err)
	}
	return false
}
