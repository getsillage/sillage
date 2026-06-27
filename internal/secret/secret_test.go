package secret_test

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/getsillage/sillage/internal/secret"
)

func TestLoadGeneratesAndPersistsSecrets(t *testing.T) {
	data := t.TempDir()

	first, err := secret.Load(data)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if first.SessionSecret == "" || first.EncryptionSecret == "" {
		t.Fatal("generated secrets must be non-empty")
	}

	info, err := os.Stat(filepath.Join(data, "runtime", "secrets.json"))
	if err != nil {
		t.Fatalf("stat secrets file: %v", err)
	}
	if mode := info.Mode().Perm(); mode != 0o600 {
		t.Fatalf("secrets file mode = %v, want 0600", mode)
	}

	second, err := secret.Load(data)
	if err != nil {
		t.Fatalf("Load() second error = %v", err)
	}
	if second.SessionSecret != first.SessionSecret || second.EncryptionSecret != first.EncryptionSecret {
		t.Fatal("secrets were not persisted across loads")
	}
}
