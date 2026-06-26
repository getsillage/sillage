package store_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/miofelix/sillage/internal/profile"
	"github.com/miofelix/sillage/store"
	"github.com/miofelix/sillage/store/db"
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

func tableExists(t *testing.T, s *store.Store, name string) bool {
	t.Helper()
	var exists bool
	if err := s.GetDriver().GetDB().QueryRow("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?)", name).Scan(&exists); err != nil {
		t.Fatalf("tableExists(%s) query error = %v", name, err)
	}
	return exists
}
