package instancelock

import (
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

func TestAcquireIsExclusiveAndReleasable(t *testing.T) {
	dataDir := t.TempDir()
	if err := os.Mkdir(filepath.Join(dataDir, "runtime"), 0o700); err != nil {
		t.Fatalf("mkdir runtime: %v", err)
	}
	databasePath := filepath.Join(dataDir, "sillage.db")
	first, err := Acquire(dataDir, databasePath)
	if err != nil {
		t.Fatalf("Acquire(first) error = %v", err)
	}
	defer first.Release()

	owner, err := os.ReadFile(filepath.Join(dataDir, "runtime", "instance.lock"))
	if err != nil {
		t.Fatalf("read instance lock: %v", err)
	}
	if strings.TrimSpace(string(owner)) != strconv.Itoa(os.Getpid()) {
		t.Fatalf("instance lock owner = %q, want pid %d", owner, os.Getpid())
	}
	if _, err := Acquire(dataDir, databasePath); !errors.Is(err, ErrInUse) {
		t.Fatalf("Acquire(second) error = %v, want ErrInUse", err)
	}

	if err := first.Release(); err != nil {
		t.Fatalf("Release(first) error = %v", err)
	}
	second, err := Acquire(dataDir, databasePath)
	if err != nil {
		t.Fatalf("Acquire(after release) error = %v", err)
	}
	if err := second.Release(); err != nil {
		t.Fatalf("Release(second) error = %v", err)
	}
}

func TestAcquireLocksExternalDatabaseAcrossDataDirectories(t *testing.T) {
	root := t.TempDir()
	databasePath := filepath.Join(root, "external.db")
	dataA := filepath.Join(root, "data-a")
	dataB := filepath.Join(root, "data-b")
	for _, dataDir := range []string{dataA, dataB} {
		if err := os.MkdirAll(filepath.Join(dataDir, "runtime"), 0o700); err != nil {
			t.Fatalf("mkdir runtime: %v", err)
		}
	}
	first, err := Acquire(dataA, databasePath)
	if err != nil {
		t.Fatalf("Acquire(first) error = %v", err)
	}
	defer first.Release()
	if _, err := Acquire(dataB, databasePath); !errors.Is(err, ErrInUse) {
		t.Fatalf("Acquire(shared database) error = %v, want ErrInUse", err)
	}
}
