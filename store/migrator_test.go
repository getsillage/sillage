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
	if version != "0.1.4" {
		t.Fatalf("schema version = %q, want 0.1.4", version)
	}
	if !columnExists(t, s, "memo", "favorited_at") {
		t.Fatal("fresh memo schema is missing favorited_at")
	}
	if columnExists(t, s, "memo", "pinned_at") {
		t.Fatal("fresh memo schema must not include legacy pinned_at")
	}
	if !columnExists(t, s, "ask_messages", "prompt_version") {
		t.Fatal("fresh ask message schema is missing prompt_version")
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
CREATE TABLE memo (
  id TEXT PRIMARY KEY,
  creator_id TEXT,
  content TEXT NOT NULL DEFAULT '',
  entry_date TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  pinned_at INTEGER,
  archived_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
INSERT INTO system_setting (key, value, created_at, updated_at) VALUES ('schema_version', '0.1.0', ?, ?);
INSERT INTO account (id, username, password_hash, created_at, updated_at) VALUES ('a1', 'felix', 'hash', ?, ?);
INSERT INTO memo (id, creator_id, content, entry_date, version, pinned_at, created_at, updated_at)
VALUES ('m1', 'a1', 'legacy favorite', '2026-07-10', 1, ?, ?, ?);`,
		now, now, now, now, now, now, now); err != nil {
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
	if !columnExists(t, s, "memo", "favorited_at") {
		t.Fatal("memo favorited_at column was not added")
	}
	memo, err := s.GetMemo(ctx, "a1", "m1", false)
	if err != nil {
		t.Fatalf("GetMemo() after compat migration error = %v", err)
	}
	if !memo.FavoritedAt.Valid || memo.FavoritedAt.Int64 != now {
		t.Fatalf("legacy pinned_at was not migrated: %+v", memo.FavoritedAt)
	}
	var legacyPinned sql.NullInt64
	if err := s.GetDriver().GetDB().QueryRowContext(ctx, "SELECT pinned_at FROM memo WHERE id = 'm1'").Scan(&legacyPinned); err != nil {
		t.Fatalf("read legacy pinned_at after migration: %v", err)
	}
	if legacyPinned.Valid {
		t.Fatalf("legacy pinned_at was not cleared: %+v", legacyPinned)
	}
	favorited := false
	if _, err := s.UpdateMemo(ctx, &store.UpdateMemo{
		ID:              memo.ID,
		CreatorID:       "a1",
		ExpectedVersion: memo.Version,
		Favorited:       &favorited,
	}); err != nil {
		t.Fatalf("cancel favorite after compat migration: %v", err)
	}
	if err := s.Migrate(ctx); err != nil {
		t.Fatalf("second Migrate() error = %v", err)
	}
	version, err := s.GetSchemaVersion(ctx)
	if err != nil {
		t.Fatalf("GetSchemaVersion() after compat migration error = %v", err)
	}
	if version != "0.1.4" {
		t.Fatalf("schema version after compat migration = %q, want 0.1.4", version)
	}
	memo, err = s.GetMemo(ctx, "a1", "m1", false)
	if err != nil {
		t.Fatalf("GetMemo() after second migration error = %v", err)
	}
	if memo.FavoritedAt.Valid {
		t.Fatalf("cancelled favorite was restored after restart: %+v", memo.FavoritedAt)
	}
	if err := s.PutAccountSetting(ctx, "a1", "ai.auto_summary", "true"); err != nil {
		t.Fatalf("PutAccountSetting() after compat migration error = %v", err)
	}
	value, ok, err := s.GetAccountSetting(ctx, "a1", "ai.auto_summary")
	if err != nil || !ok || value != "true" {
		t.Fatalf("GetAccountSetting() = %q, %v, %v; want true, true, nil", value, ok, err)
	}
}

func TestMigrateExistingAskMessagesAddsPromptVersion(t *testing.T) {
	ctx := context.Background()
	p := &profile.Profile{Data: t.TempDir()}
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
CREATE TABLE ask_messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  parent_id TEXT,
  fork_of_id TEXT,
  status TEXT NOT NULL DEFAULT 'complete',
  source_refs TEXT NOT NULL DEFAULT '[]',
  model TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
INSERT INTO system_setting (key, value, created_at, updated_at)
VALUES ('schema_version', '0.1.3', ?, ?);
INSERT INTO ask_messages (
  id, conversation_id, role, content, status, source_refs, model, created_at, updated_at
) VALUES ('legacy-message', 'legacy-conversation', 'assistant', 'legacy answer', 'complete', '[]', 'legacy-model', ?, ?);`,
		now, now, now, now); err != nil {
		t.Fatalf("seed 0.1.3 ask schema: %v", err)
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
	if !columnExists(t, s, "ask_messages", "prompt_version") {
		t.Fatal("ask_messages prompt_version column was not added")
	}
	version, err := s.GetSchemaVersion(ctx)
	if err != nil {
		t.Fatalf("GetSchemaVersion() error = %v", err)
	}
	if version != "0.1.4" {
		t.Fatalf("schema version = %q, want 0.1.4", version)
	}
	legacy, err := s.GetAskMessage(ctx, "legacy-message")
	if err != nil {
		t.Fatalf("GetAskMessage(legacy) error = %v", err)
	}
	if legacy.PromptVersion != "" {
		t.Fatalf("legacy prompt version = %q, want empty", legacy.PromptVersion)
	}

	if _, err := s.GetDriver().GetDB().ExecContext(ctx, `
INSERT INTO ask_messages (
  id, conversation_id, role, content, status, source_refs, model, created_at, updated_at
) VALUES ('default-message', 'legacy-conversation', 'assistant', 'default answer', 'complete', '[]', 'legacy-model', ?, ?)`,
		now, now); err != nil {
		t.Fatalf("insert ask message using prompt_version default: %v", err)
	}
	if _, err := s.GetDriver().GetDB().ExecContext(ctx,
		"UPDATE ask_messages SET prompt_version = 'ask-answer-v2' WHERE id = 'legacy-message'"); err != nil {
		t.Fatalf("set migrated prompt version: %v", err)
	}
	if err := s.Migrate(ctx); err != nil {
		t.Fatalf("second Migrate() error = %v", err)
	}

	legacy, err = s.GetAskMessage(ctx, "legacy-message")
	if err != nil {
		t.Fatalf("GetAskMessage(legacy) after second migration error = %v", err)
	}
	if legacy.PromptVersion != "ask-answer-v2" {
		t.Fatalf("legacy prompt version after second migration = %q, want ask-answer-v2", legacy.PromptVersion)
	}
	defaulted, err := s.GetAskMessage(ctx, "default-message")
	if err != nil {
		t.Fatalf("GetAskMessage(default) error = %v", err)
	}
	if defaulted.PromptVersion != "" {
		t.Fatalf("default prompt version = %q, want empty", defaulted.PromptVersion)
	}
}

func TestMigrateRejectsNewerSchemaVersion(t *testing.T) {
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
		t.Fatalf("initial Migrate() error = %v", err)
	}
	if _, err := s.GetDriver().GetDB().ExecContext(ctx, `
UPDATE system_setting
SET value = '9.0.0'
WHERE key = 'schema_version'`); err != nil {
		t.Fatalf("set future schema version: %v", err)
	}
	if err := s.Migrate(ctx); err == nil {
		t.Fatal("Migrate() error = nil, want unsupported future version error")
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
