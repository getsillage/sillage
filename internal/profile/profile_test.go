package profile

import (
	"os"
	"path/filepath"
	"testing"
)

func TestValidateCreatesExplicitDataLayout(t *testing.T) {
	data := t.TempDir()
	p := &Profile{Data: data}

	if err := p.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}

	if p.Driver != DriverSQLite {
		t.Fatalf("Driver = %q, want %q", p.Driver, DriverSQLite)
	}
	if p.Port != DefaultPort {
		t.Fatalf("Port = %d, want %d", p.Port, DefaultPort)
	}
	if p.DSN != filepath.Join(data, DefaultSQLiteFile) {
		t.Fatalf("DSN = %q, want default database inside data dir", p.DSN)
	}
	if p.MaxUploadMB != 30 {
		t.Fatalf("MaxUploadMB = %d, want 30", p.MaxUploadMB)
	}

	for _, rel := range []string{"assets/attachments", ".thumbnail_cache", "runtime"} {
		if info, err := os.Stat(filepath.Join(data, rel)); err != nil || !info.IsDir() {
			t.Fatalf("expected directory %s to exist, stat err = %v", rel, err)
		}
	}
}

func TestValidateRejectsNonSQLiteDriver(t *testing.T) {
	p := &Profile{Data: t.TempDir(), Driver: "postgres"}

	if err := p.Validate(); err == nil {
		t.Fatal("Validate() error = nil, want unsupported driver error")
	}
}
