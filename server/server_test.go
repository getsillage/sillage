package server_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/internal/secret"
	"github.com/getsillage/sillage/server"
	"github.com/getsillage/sillage/store"
	"github.com/getsillage/sillage/store/db"
)

func TestHealthzAndReadyz(t *testing.T) {
	ctx := context.Background()
	p := &profile.Profile{Data: t.TempDir()}
	if err := p.Validate(); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
	driver, err := db.NewDBDriver(p)
	if err != nil {
		t.Fatalf("NewDBDriver() error = %v", err)
	}
	storeInstance := store.New(driver, p)
	defer storeInstance.Close()
	if err := storeInstance.Migrate(ctx); err != nil {
		t.Fatalf("Migrate() error = %v", err)
	}
	secrets, err := secret.Load(p.Data)
	if err != nil {
		t.Fatalf("secret.Load() error = %v", err)
	}
	srv, err := server.NewWithBuildInfo(ctx, p, storeInstance, secrets, server.BuildInfo{
		Version:                   "v0.3.0-test",
		Revision:                  "0123456789abcdef",
		APIVersion:                "v1",
		MinimumAndroidVersionCode: 9,
	})
	if err != nil {
		t.Fatalf("server.New() error = %v", err)
	}

	for _, path := range []string{"/healthz", "/readyz"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		rec := httptest.NewRecorder()
		srv.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatalf("GET %s status = %d, want 200; body=%s", path, rec.Code, rec.Body.String())
		}
		for header, want := range map[string]string{
			"X-Content-Type-Options": "nosniff",
			"X-Frame-Options":        "DENY",
			"Referrer-Policy":        "no-referrer",
			"X-Sillage-Version":      "v0.3.0-test",
			"X-Sillage-Revision":     "0123456789abcdef",
			"X-Sillage-API-Version":  "v1",
		} {
			if got := rec.Header().Get(header); got != want {
				t.Fatalf("GET %s header %s = %q, want %q", path, header, got, want)
			}
		}
		if rec.Header().Get("Content-Security-Policy") == "" || rec.Header().Get("Permissions-Policy") == "" {
			t.Fatalf("GET %s missing browser security policy headers", path)
		}
		if rec.Header().Get("X-Request-ID") == "" {
			t.Fatalf("GET %s missing request id", path)
		}
		if got := rec.Header().Get("X-Sillage-Min-Android-Version-Code"); got != "9" {
			t.Fatalf("GET %s minimum Android version code = %q, want 9", path, got)
		}

		var body map[string]string
		if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
			t.Fatalf("GET %s JSON decode error = %v", path, err)
		}
		if body["status"] == "" {
			t.Fatalf("GET %s response has empty status", path)
		}
	}
}

func TestGeneralRequestBodyLimit(t *testing.T) {
	srv := newTestServer(t)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/signin", strings.NewReader(strings.Repeat("x", (8<<20)+1)))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("oversized request status = %d, want 413; body=%s", rec.Code, rec.Body.String())
	}
}

func TestLegacyBackupRoutesReturn404(t *testing.T) {
	srv := newTestServer(t)

	for _, path := range []string{"/download-backup", "/api/backup", "/api/backups"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		rec := httptest.NewRecorder()
		srv.ServeHTTP(rec, req)
		if rec.Code != http.StatusNotFound {
			t.Fatalf("GET %s status = %d, want 404; body=%s", path, rec.Code, rec.Body.String())
		}
	}
}
