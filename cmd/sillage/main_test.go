package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestExpandFileEnv(t *testing.T) {
	path := filepath.Join(t.TempDir(), "dsn")
	if err := os.WriteFile(path, []byte("custom.db\n"), 0o600); err != nil {
		t.Fatalf("write secret file: %v", err)
	}
	t.Setenv("SILLAGE_DSN", "")
	t.Setenv("SILLAGE_DSN_FILE", path)

	if err := expandFileEnv("SILLAGE_DSN"); err != nil {
		t.Fatalf("expandFileEnv() error = %v", err)
	}
	if got := os.Getenv("SILLAGE_DSN"); got != "custom.db" {
		t.Fatalf("SILLAGE_DSN = %q, want custom.db", got)
	}
	if got := os.Getenv("SILLAGE_DSN_FILE"); got != "" {
		t.Fatalf("SILLAGE_DSN_FILE = %q, want empty", got)
	}
}

func TestExpandFileEnvRejectsBothValueAndFile(t *testing.T) {
	t.Setenv("SESSION_SECRET", "value")
	t.Setenv("SESSION_SECRET_FILE", filepath.Join(t.TempDir(), "secret"))

	if err := expandFileEnv("SESSION_SECRET"); err == nil {
		t.Fatal("expandFileEnv() error = nil, want mutual exclusion error")
	}
}
