package main

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestHealthcheckRequiresReadiness(t *testing.T) {
	for _, tc := range []struct {
		name string
		code int
		body string
		ok   bool
	}{
		{"ready", http.StatusOK, `{"status":"ready"}`, true},
		{"not ready", http.StatusServiceUnavailable, `{"status":"ready"}`, false},
		{"redirect", http.StatusFound, `{"status":"ready"}`, false},
		{"different state", http.StatusOK, `{"status":"starting"}`, false},
		{"malformed", http.StatusOK, `status: ready`, false},
		{"oversized", http.StatusOK, strings.Repeat(" ", 1024) + `{"status":"ready"}`, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
				if req.Method != http.MethodGet || req.URL.Path != "/readyz" {
					t.Errorf("unexpected request: %s %s", req.Method, req.URL.Path)
					w.WriteHeader(http.StatusNotFound)
					return
				}
				w.Header().Set("Location", "/unexpected")
				w.WriteHeader(tc.code)
				_, _ = fmt.Fprint(w, tc.body)
			}))
			defer srv.Close()
			err := checkReadiness(t.Context(), srv.Listener.Addr().String())
			if (err == nil) != tc.ok {
				t.Fatalf("checkReadiness() = %v; want success = %v", err, tc.ok)
			}
		})
	}
}
