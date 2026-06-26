package server_test

import (
	"bytes"
	"encoding/json"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestAttachmentUploadDownloadDeleteAndSync(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	body, contentType := multipartBody(t, "hello.txt", "hello attachment", map[string]string{
		"mutation_id": "att-1",
	})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/attachments", body)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("upload status = %d body=%s", rec.Code, rec.Body.String())
	}
	attachment := decodeAttachmentResponse(t, rec.Body.Bytes())
	uid := attachment["uid"].(string)
	if attachment["filename"].(string) != "hello.txt" {
		t.Fatalf("filename = %v", attachment["filename"])
	}
	if attachment["sha256"] == nil {
		t.Fatal("sha256 should be returned")
	}

	body, contentType = multipartBody(t, "hello.txt", "different bytes ignored by idempotency", map[string]string{
		"mutation_id": "att-1",
	})
	req = httptest.NewRequest(http.MethodPost, "/api/v1/attachments", body)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)
	rec = httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("idempotent upload status = %d body=%s", rec.Code, rec.Body.String())
	}
	again := decodeAttachmentResponse(t, rec.Body.Bytes())
	if again["uid"] != uid {
		t.Fatalf("idempotent upload uid = %v, want %s", again["uid"], uid)
	}

	req = httptest.NewRequest(http.MethodGet, "/file/attachments/"+uid+"/hello.txt", nil)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	rec = httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK || rec.Body.String() != "hello attachment" {
		t.Fatalf("download status/body = %d %q", rec.Code, rec.Body.String())
	}

	req = httptest.NewRequest(http.MethodGet, "/file/attachments/"+uid+"/hello.txt", nil)
	req.Host = "localhost:5231"
	rec = httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("unauthorized download status = %d, want 401", rec.Code)
	}

	res := doJSON(t, srv, http.MethodDelete, "/api/v1/attachments/"+uid, nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("delete attachment status = %d body=%s", res.Code, res.Body.String())
	}
	deleted := decodeAttachmentResponse(t, res.Body.Bytes())
	if deleted["deletedAt"] == nil {
		t.Fatal("deleted attachment should include tombstone")
	}

	req = httptest.NewRequest(http.MethodGet, "/file/attachments/"+uid+"/hello.txt", nil)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	rec = httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("download deleted status = %d, want 404", rec.Code)
	}

	res = doJSON(t, srv, http.MethodGet, "/api/v1/sync", nil, bearer(token))
	if res.Code != http.StatusOK {
		t.Fatalf("sync status = %d body=%s", res.Code, res.Body.String())
	}
	if !strings.Contains(res.Body.String(), `"attachments"`) || !strings.Contains(res.Body.String(), `"deletedAt"`) {
		t.Fatalf("sync response missing attachment tombstone: %s", res.Body.String())
	}
}

func TestAttachmentRejectsUnsafeFilename(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	body, contentType := multipartBody(t, "../evil.txt", "bad", nil)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/attachments", body)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("path components should be sanitized to basename, status=%d body=%s", rec.Code, rec.Body.String())
	}
	attachment := decodeAttachmentResponse(t, rec.Body.Bytes())
	if attachment["filename"].(string) != "evil.txt" {
		t.Fatalf("sanitized filename = %v, want evil.txt", attachment["filename"])
	}
}

func multipartBody(t *testing.T, filename, content string, fields map[string]string) (io.Reader, string) {
	t.Helper()
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	for key, value := range fields {
		if err := writer.WriteField(key, value); err != nil {
			t.Fatalf("write field: %v", err)
		}
	}
	part, err := writer.CreateFormFile("file", filename)
	if err != nil {
		t.Fatalf("create form file: %v", err)
	}
	if _, err := part.Write([]byte(content)); err != nil {
		t.Fatalf("write form file: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}
	return &body, writer.FormDataContentType()
}

func decodeAttachmentResponse(t *testing.T, body []byte) map[string]any {
	t.Helper()
	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("decode attachment response: %v", err)
	}
	attachment, ok := payload["attachment"].(map[string]any)
	if !ok {
		t.Fatalf("response missing attachment: %#v", payload)
	}
	return attachment
}

func TestAttachmentDownloadAuthenticatesViaAccessCookie(t *testing.T) {
	srv := newTestServer(t)

	initRes := doJSON(t, srv, http.MethodPost, "/api/v1/auth/initialize", map[string]string{
		"username": "felix",
		"password": "passw0rd!",
	}, nil)
	if initRes.Code != http.StatusOK {
		t.Fatalf("initialize status = %d body=%s", initRes.Code, initRes.Body.String())
	}
	var initPayload map[string]any
	if err := json.Unmarshal(initRes.Body.Bytes(), &initPayload); err != nil {
		t.Fatalf("decode initialize response: %v", err)
	}
	token, ok := initPayload["accessToken"].(string)
	if !ok || token == "" {
		t.Fatalf("missing access token: %#v", initPayload)
	}
	cookie := findCookie(initRes, "sillage_access")
	if cookie == nil || cookie.Value == "" {
		t.Fatal("initialize should set an access cookie for browser-native requests")
	}

	body, contentType := multipartBody(t, "note.txt", "cookie attachment", map[string]string{
		"mutation_id": "att-cookie-1",
	})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/attachments", body)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("upload status = %d body=%s", rec.Code, rec.Body.String())
	}
	uid := decodeAttachmentResponse(t, rec.Body.Bytes())["uid"].(string)

	// An <img> tag or download link cannot send an Authorization header; it
	// relies on the HttpOnly access cookie the browser attaches automatically.
	req = httptest.NewRequest(http.MethodGet, "/file/attachments/"+uid+"/note.txt", nil)
	req.Host = "localhost:5231"
	req.AddCookie(cookie)
	rec = httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK || rec.Body.String() != "cookie attachment" {
		t.Fatalf("cookie download status/body = %d %q", rec.Code, rec.Body.String())
	}

	// An invalid access cookie must still be rejected.
	req = httptest.NewRequest(http.MethodGet, "/file/attachments/"+uid+"/note.txt", nil)
	req.Host = "localhost:5231"
	req.AddCookie(&http.Cookie{Name: "sillage_access", Value: "not-a-real-token"})
	rec = httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("invalid cookie download status = %d, want 401", rec.Code)
	}
}

func findCookie(res *httptest.ResponseRecorder, name string) *http.Cookie {
	for _, cookie := range res.Result().Cookies() {
		if cookie.Name == name {
			return cookie
		}
	}
	return nil
}
