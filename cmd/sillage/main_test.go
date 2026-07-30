package main

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/spf13/viper"

	"github.com/getsillage/sillage/internal/profile"
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

func TestSecureNetworkDefaultsAndTrustedProxyEnv(t *testing.T) {
	viper.Reset()
	t.Cleanup(viper.Reset)
	t.Setenv("SILLAGE_TRUSTED_PROXY", "127.0.0.1/32,::1/128")
	_ = newRootCommand()

	if got := viper.GetString("addr"); got != profile.DefaultAddr {
		t.Fatalf("default addr = %q, want %q", got, profile.DefaultAddr)
	}
	want := []string{"127.0.0.1/32", "::1/128"}
	got := trustedProxyCIDRs()
	if len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("trusted proxy env = %v, want %v", got, want)
	}
}

func TestExpandFileEnvRejectsBothValueAndFile(t *testing.T) {
	t.Setenv("SESSION_SECRET", "value")
	t.Setenv("SESSION_SECRET_FILE", filepath.Join(t.TempDir(), "secret"))

	if err := expandFileEnv("SESSION_SECRET"); err == nil {
		t.Fatal("expandFileEnv() error = nil, want mutual exclusion error")
	}
}
