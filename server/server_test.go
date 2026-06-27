package server_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
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
	srv, err := server.New(ctx, p, storeInstance, secrets)
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
		if rec.Header().Get("X-Content-Type-Options") != "nosniff" {
			t.Fatalf("GET %s missing nosniff header", path)
		}
		if rec.Header().Get("X-Request-ID") == "" {
			t.Fatalf("GET %s missing request id", path)
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
