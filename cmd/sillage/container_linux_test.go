package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestContainerID(t *testing.T) {
	for input, want := range map[string]int{"": 10001, "42": 42} {
		if got, err := containerID(input); err != nil || got != want {
			t.Errorf("containerID(%q) = %d, %v; want %d", input, got, err, want)
		}
	}
	for _, input := range []string{"0", "-1", "root", "4294967295"} {
		if _, err := containerID(input); err == nil {
			t.Errorf("containerID(%q) accepted invalid ID", input)
		}
	}
}

func TestPrepareContainerDataDoesNotFollowSymlinks(t *testing.T) {
	target := t.TempDir()
	data := t.TempDir()
	if err := os.Symlink(target, filepath.Join(data, "linked")); err != nil {
		t.Fatal(err)
	}
	if err := prepareContainerData(data, os.Getuid(), os.Getgid()); err != nil {
		t.Fatal(err)
	}
	if files, err := os.ReadDir(target); err != nil || len(files) != 0 {
		t.Fatalf("unexpected contents in symlink target: %v, %v", files, err)
	}
	brokenData := t.TempDir()
	if err := os.Symlink(target, filepath.Join(brokenData, "assets")); err != nil {
		t.Fatal(err)
	}
	if err := prepareContainerData(brokenData, os.Getuid(), os.Getgid()); err == nil || !strings.Contains(err.Error(), "real directory") {
		t.Fatalf("expected required directory symlink to fail, got %v", err)
	}
}

func TestPrepareContainerDataRefusesMultiplyLinkedFileOwnershipChange(t *testing.T) {
	if os.Getuid() != 0 {
		t.Skip("requires root for ownership changes")
	}
	data := t.TempDir()
	outside := filepath.Join(t.TempDir(), "outside")
	if err := os.WriteFile(outside, nil, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Link(outside, filepath.Join(data, "linked")); err != nil {
		t.Fatal(err)
	}
	if err := prepareContainerData(data, 10001, 10001); err == nil || !strings.Contains(err.Error(), "multiply linked") {
		t.Fatalf("expected hard link to fail, got %v", err)
	}
}
