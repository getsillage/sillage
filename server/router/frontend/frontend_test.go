package frontend

import (
	"io/fs"
	"testing"
	"testing/fstest"
)

func TestFrontendFilesRequiresBuiltIndex(t *testing.T) {
	_, err := frontendFiles(fstest.MapFS{
		"dist_placeholder.txt": {Data: []byte("placeholder")},
	})
	if err == nil {
		t.Fatal("frontendFiles() succeeded without dist/index.html")
	}
}

func TestFrontendFilesReturnsBuiltDirectory(t *testing.T) {
	got, err := frontendFiles(fstest.MapFS{
		"dist/index.html":    {Data: []byte("index")},
		"dist/assets/app.js": {Data: []byte("app")},
	})
	if err != nil {
		t.Fatalf("frontendFiles() error = %v", err)
	}

	index, err := fs.ReadFile(got, "index.html")
	if err != nil {
		t.Fatalf("ReadFile(index.html) error = %v", err)
	}
	if string(index) != "index" {
		t.Fatalf("index.html = %q, want %q", index, "index")
	}
}
