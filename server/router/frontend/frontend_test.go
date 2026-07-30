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

func TestFrontendCacheControl(t *testing.T) {
	tests := []struct {
		path         string
		servingIndex bool
		want         string
	}{
		{path: "/", servingIndex: true, want: "no-cache"},
		{path: "/settings", servingIndex: true, want: "no-cache"},
		{path: "/assets/app-01234567.js", want: "public, max-age=31536000, immutable"},
		{path: "/favicon.svg", want: "public, max-age=3600"},
	}
	for _, tt := range tests {
		if got := frontendCacheControl(tt.path, tt.servingIndex); got != tt.want {
			t.Fatalf("frontendCacheControl(%q, %t) = %q, want %q", tt.path, tt.servingIndex, got, tt.want)
		}
	}
}
