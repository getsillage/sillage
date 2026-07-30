package server

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/getsillage/sillage/store"
)

func TestCleanupAttachmentDirectory(t *testing.T) {
	directory := t.TempDir()
	now := time.Date(2026, 7, 30, 12, 0, 0, 0, time.UTC)
	for _, name := range []string{"active", "shared", "deleted", "old-orphan", "recent-orphan"} {
		path := filepath.Join(directory, name)
		if err := os.WriteFile(path, []byte(name), 0o600); err != nil {
			t.Fatalf("write %s: %v", name, err)
		}
		age := now.Add(-48 * time.Hour)
		if name == "recent-orphan" {
			age = now.Add(-time.Hour)
		}
		if err := os.Chtimes(path, age, age); err != nil {
			t.Fatalf("age %s: %v", name, err)
		}
	}
	refs := []store.AttachmentStorageRef{
		{StorageRef: "assets/attachments/active"},
		{StorageRef: "assets/attachments/shared"},
		{StorageRef: "assets/attachments/shared", Deleted: true},
		{StorageRef: "assets/attachments/deleted", Deleted: true},
		{StorageRef: "assets/attachments/nested/ignored", Deleted: true},
	}
	removed, err := cleanupAttachmentDirectory(context.Background(), directory, refs, now, 24*time.Hour)
	if err != nil {
		t.Fatalf("cleanupAttachmentDirectory() error = %v", err)
	}
	if removed != 2 {
		t.Fatalf("removed = %d, want 2", removed)
	}
	for name, wantExists := range map[string]bool{
		"active": true, "shared": true, "deleted": false, "old-orphan": false, "recent-orphan": true,
	} {
		_, err := os.Stat(filepath.Join(directory, name))
		if (err == nil) != wantExists {
			t.Fatalf("%s exists=%v error=%v, want exists=%v", name, err == nil, err, wantExists)
		}
	}
}

func TestCleanupAttachmentDirectorySkipsDirectoriesAndSymlinks(t *testing.T) {
	directory := t.TempDir()
	now := time.Date(2026, 7, 30, 12, 0, 0, 0, time.UTC)
	nested := filepath.Join(directory, "nested")
	if err := os.Mkdir(nested, 0o700); err != nil {
		t.Fatalf("mkdir nested: %v", err)
	}
	target := filepath.Join(t.TempDir(), "target")
	if err := os.WriteFile(target, []byte("keep"), 0o600); err != nil {
		t.Fatalf("write symlink target: %v", err)
	}
	link := filepath.Join(directory, "link")
	if err := os.Symlink(target, link); err != nil {
		t.Fatalf("create symlink: %v", err)
	}

	removed, err := cleanupAttachmentDirectory(context.Background(), directory, nil, now, 0)
	if err != nil {
		t.Fatalf("cleanupAttachmentDirectory() error = %v", err)
	}
	if removed != 0 {
		t.Fatalf("removed = %d, want 0", removed)
	}
	for _, path := range []string{nested, link, target} {
		if _, err := os.Lstat(path); err != nil {
			t.Fatalf("protected path %s was removed: %v", path, err)
		}
	}
}

func TestCleanupAttachmentDirectoryHonorsCancellation(t *testing.T) {
	directory := t.TempDir()
	if err := os.WriteFile(filepath.Join(directory, "old-orphan"), []byte("keep"), 0o600); err != nil {
		t.Fatalf("write old orphan: %v", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	removed, err := cleanupAttachmentDirectory(ctx, directory, nil, time.Now().UTC(), 0)
	if err != context.Canceled {
		t.Fatalf("cleanupAttachmentDirectory() error = %v, want context.Canceled", err)
	}
	if removed != 0 {
		t.Fatalf("removed = %d, want 0", removed)
	}
}
