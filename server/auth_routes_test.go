package server_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/miofelix/sillage/internal/profile"
	"github.com/miofelix/sillage/internal/secret"
	"github.com/miofelix/sillage/server"
	"github.com/miofelix/sillage/store"
	"github.com/miofelix/sillage/store/db"
)

func TestAuthInitializeSignInRefreshSignOut(t *testing.T) {
	srv := newTestServer(t)

	res := doJSON(t, srv, http.MethodGet, "/api/v1/auth/bootstrap", nil, nil)
	if res.Code != http.StatusOK || !strings.Contains(res.Body.String(), `"initialized":false`) {
		t.Fatalf("bootstrap before init status/body = %d %s", res.Code, res.Body.String())
	}

	initBody := map[string]string{
		"username":    "Felix",
		"displayName": "Felix",
		"password":    "passw0rd!",
	}
	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/initialize", initBody, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("initialize status = %d body=%s", res.Code, res.Body.String())
	}
	if cookie := refreshCookie(res); cookie == nil || !cookie.HttpOnly {
		t.Fatalf("initialize refresh cookie missing or not httpOnly: %#v", cookie)
	}
	var authRes map[string]any
	if err := json.Unmarshal(res.Body.Bytes(), &authRes); err != nil {
		t.Fatalf("decode auth response: %v", err)
	}
	accessToken, ok := authRes["accessToken"].(string)
	if !ok || accessToken == "" {
		t.Fatalf("accessToken missing from response: %#v", authRes)
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/initialize", initBody, nil)
	if res.Code != http.StatusForbidden {
		t.Fatalf("second initialize status = %d, want 403", res.Code)
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/signin", map[string]string{
		"username": "felix",
		"password": "passw0rd!",
	}, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("signin status = %d body=%s", res.Code, res.Body.String())
	}
	signinCookie := refreshCookie(res)
	if signinCookie == nil {
		t.Fatal("signin did not set refresh cookie")
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/auth/me", nil, map[string]string{
		"Authorization": "Bearer " + accessToken,
	})
	if res.Code != http.StatusOK {
		t.Fatalf("me status = %d body=%s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/refresh", nil, map[string]string{
		"Cookie": signinCookie.Name + "=" + signinCookie.Value,
	})
	if res.Code != http.StatusOK {
		t.Fatalf("refresh status = %d body=%s", res.Code, res.Body.String())
	}
	rotatedCookie := refreshCookie(res)
	if rotatedCookie == nil || rotatedCookie.Value == signinCookie.Value {
		t.Fatal("refresh did not rotate refresh cookie")
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/signout", nil, map[string]string{
		"Cookie": rotatedCookie.Name + "=" + rotatedCookie.Value,
	})
	if res.Code != http.StatusNoContent {
		t.Fatalf("signout status = %d body=%s", res.Code, res.Body.String())
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/refresh", nil, map[string]string{
		"Cookie": rotatedCookie.Name + "=" + rotatedCookie.Value,
	})
	if res.Code != http.StatusUnauthorized {
		t.Fatalf("refresh after signout status = %d, want 401", res.Code)
	}
}

func TestSignInRateLimit(t *testing.T) {
	srv := newTestServer(t)
	initBody := map[string]string{"username": "felix", "password": "passw0rd!"}
	res := doJSON(t, srv, http.MethodPost, "/api/v1/auth/initialize", initBody, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("initialize status = %d body=%s", res.Code, res.Body.String())
	}

	for i := 0; i < 10; i++ {
		res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/signin", map[string]string{
			"username": "felix",
			"password": "wrong",
		}, map[string]string{"X-Forwarded-For": "203.0.113.1"})
		if res.Code != http.StatusUnauthorized {
			t.Fatalf("wrong signin #%d status = %d, want 401", i+1, res.Code)
		}
	}

	res = doJSON(t, srv, http.MethodPost, "/api/v1/auth/signin", map[string]string{
		"username": "felix",
		"password": "wrong",
	}, map[string]string{"X-Forwarded-For": "203.0.113.1"})
	if res.Code != http.StatusTooManyRequests {
		t.Fatalf("rate-limited signin status = %d, want 429", res.Code)
	}
}

func newTestServer(t *testing.T) *server.Server {
	t.Helper()
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
	t.Cleanup(func() { _ = storeInstance.Close() })
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
	return srv
}

func doJSON(t *testing.T, srv http.Handler, method, path string, body any, headers map[string]string) *httptest.ResponseRecorder {
	t.Helper()
	var payload []byte
	if body != nil {
		var err error
		payload, err = json.Marshal(body)
		if err != nil {
			t.Fatalf("marshal body: %v", err)
		}
	}
	req := httptest.NewRequest(method, path, bytes.NewReader(payload))
	req.Host = "localhost:5231"
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	return rec
}

func refreshCookie(res *httptest.ResponseRecorder) *http.Cookie {
	for _, cookie := range res.Result().Cookies() {
		if cookie.Name == "sillage_refresh" {
			return cookie
		}
	}
	return nil
}
