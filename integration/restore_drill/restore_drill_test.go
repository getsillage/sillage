//go:build restore_drill

package restore_drill_test

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"mime/multipart"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/internal/secret"
	"github.com/getsillage/sillage/server"
	"github.com/getsillage/sillage/store"
	storedb "github.com/getsillage/sillage/store/db"

	_ "modernc.org/sqlite"
)

const (
	drillUsername       = "restore-drill"
	drillPassword       = "restore-drill-password"
	drillMemoMarker     = "restoredrillmarker"
	postBackupMarker    = "postbackupmarker"
	drillAttachmentBody = "Sillage restore drill attachment\n"
	drillAIKey          = "restore-drill-api-key"
)

var drillHTTPClient = &http.Client{Timeout: 10 * time.Second}

type drillReport struct {
	BackupTimestamp   string `json:"backupTimestamp"`
	RestoreToReadyMS  int64  `json:"restoreToReadyMs"`
	BackupIntegrity   bool   `json:"backupIntegrity"`
	RestoredIntegrity bool   `json:"restoredIntegrity"`
	SignIn            bool   `json:"signIn"`
	Record            bool   `json:"record"`
	Search            bool   `json:"search"`
	Attachment        bool   `json:"attachment"`
	AISettings        bool   `json:"aiSettings"`
	AIKeyDecryption   bool   `json:"aiKeyDecryption"`
	SyncBootstrap     bool   `json:"syncBootstrap"`
	RollbackPreserved bool   `json:"rollbackPreserved"`
	Result            string `json:"result"`
}

type runningInstance struct {
	baseURL string
	cancel  context.CancelFunc
	server  *server.Server
}

type memoDTO struct {
	ID      string `json:"id"`
	Content string `json:"content"`
	Version int64  `json:"version"`
}

type attachmentDTO struct {
	UID      string `json:"uid"`
	URL      string `json:"url"`
	Filename string `json:"filename"`
}

func TestBackupRestoreDrill(t *testing.T) {
	t.Setenv("SESSION_SECRET", "")
	t.Setenv("ENCRYPTION_SECRET", "")
	report := &drillReport{Result: "failed"}
	defer writeReport(t, report)

	root := t.TempDir()
	dataDir := filepath.Join(root, "data")
	backupDir := filepath.Join(root, "backup")
	rollbackDir := filepath.Join(root, "data.before-restore")

	mockAI := newMockAIProvider(t)
	source := startInstance(t, dataDir)
	token := initialize(t, source.baseURL)
	memo := createMemo(t, source.baseURL, token, drillMemoMarker+" sleep improved")
	attachment := uploadAttachment(t, source.baseURL, token)
	configureAI(t, source.baseURL, token, mockAI.URL)
	generateSummary(t, source.baseURL, token, memo.ID)
	stopInstance(t, source)

	requireIntegrity(t, filepath.Join(dataDir, profile.DefaultSQLiteFile))
	report.BackupTimestamp = time.Now().UTC().Format(time.RFC3339)
	if err := copyTree(dataDir, backupDir); err != nil {
		t.Fatalf("create backup copy: %v", err)
	}
	requireIntegrity(t, filepath.Join(backupDir, profile.DefaultSQLiteFile))
	report.BackupIntegrity = true

	// Change the live instance after the recovery point. A correct restore must
	// omit this memo while retaining it in the rollback copy.
	live := startInstance(t, dataDir)
	liveToken := signIn(t, live.baseURL)
	createMemo(t, live.baseURL, liveToken, postBackupMarker+" must remain only in rollback")
	stopInstance(t, live)

	restoreStarted := time.Now()
	if err := restoreWithRollback(dataDir, backupDir, rollbackDir); err != nil {
		t.Fatalf("restore backup: %v", err)
	}
	requireIntegrity(t, filepath.Join(dataDir, profile.DefaultSQLiteFile))
	report.RestoredIntegrity = true
	restored := startInstance(t, dataDir)
	report.RestoreToReadyMS = time.Since(restoreStarted).Milliseconds()
	defer stopInstance(t, restored)

	restoredToken := signIn(t, restored.baseURL)
	report.SignIn = true

	detail := getMemo(t, restored.baseURL, restoredToken, memo.ID)
	if detail.Content != memo.Content {
		t.Fatalf("restored memo content = %q, want %q", detail.Content, memo.Content)
	}
	report.Record = true

	found := searchMemos(t, restored.baseURL, restoredToken, drillMemoMarker)
	if len(found) != 1 || found[0].ID != memo.ID {
		t.Fatalf("restored search = %#v, want memo %s", found, memo.ID)
	}
	if got := searchMemos(t, restored.baseURL, restoredToken, postBackupMarker); len(got) != 0 {
		t.Fatalf("post-backup memo leaked into restored data: %#v", got)
	}
	report.Search = true

	attachmentBody := getBytes(t, restored.baseURL+attachment.URL, restoredToken)
	if string(attachmentBody) != drillAttachmentBody {
		t.Fatalf("restored attachment = %q, want %q", attachmentBody, drillAttachmentBody)
	}
	report.Attachment = true

	settings := getJSON[struct {
		Profiles []struct {
			Name      string `json:"name"`
			HasAPIKey bool   `json:"hasApiKey"`
			Active    bool   `json:"active"`
		} `json:"profiles"`
	}](t, restored.baseURL+"/api/v1/settings/ai", restoredToken)
	if len(settings.Profiles) != 1 || !settings.Profiles[0].HasAPIKey || !settings.Profiles[0].Active {
		t.Fatalf("restored AI settings = %#v", settings.Profiles)
	}
	report.AISettings = true

	generateSummary(t, restored.baseURL, restoredToken, memo.ID)
	report.AIKeyDecryption = true

	sync := getJSON[struct {
		Memos       []memoDTO       `json:"memos"`
		Attachments []attachmentDTO `json:"attachments"`
		MemoAI      []struct {
			MemoID  string `json:"memoId"`
			Summary string `json:"summary"`
		} `json:"memoAi"`
		NextCursor string `json:"nextCursor"`
	}](t, restored.baseURL+"/api/v1/sync?limit=200", restoredToken)
	if !containsMemo(sync.Memos, memo.ID) || !containsAttachment(sync.Attachments, attachment.UID) || !containsMemoAI(sync.MemoAI, memo.ID) || sync.NextCursor == "" {
		t.Fatalf("restored sync bootstrap missing resources: memos=%#v attachments=%#v memoAI=%#v cursor=%q", sync.Memos, sync.Attachments, sync.MemoAI, sync.NextCursor)
	}
	report.SyncBootstrap = true

	requireIntegrity(t, filepath.Join(rollbackDir, profile.DefaultSQLiteFile))
	if !databaseContainsMemo(t, filepath.Join(rollbackDir, profile.DefaultSQLiteFile), postBackupMarker) {
		t.Fatal("rollback copy does not contain the post-backup memo")
	}
	report.RollbackPreserved = true
	report.Result = "passed"
}

func TestFailedRestoreRetainsRollback(t *testing.T) {
	root := t.TempDir()
	dataDir := filepath.Join(root, "data")
	rollbackDir := filepath.Join(root, "rollback")
	if err := os.MkdirAll(dataDir, 0o700); err != nil {
		t.Fatal(err)
	}
	sentinel := filepath.Join(dataDir, "sentinel")
	if err := os.WriteFile(sentinel, []byte("original"), 0o600); err != nil {
		t.Fatal(err)
	}

	err := restoreWithRollback(dataDir, filepath.Join(root, "missing-backup"), rollbackDir)
	if err == nil {
		t.Fatal("restoreWithRollback() succeeded with a missing backup")
	}
	got, readErr := os.ReadFile(filepath.Join(rollbackDir, "sentinel"))
	if readErr != nil || string(got) != "original" {
		t.Fatalf("rollback sentinel = %q, error = %v", got, readErr)
	}
}

func startInstance(t *testing.T, dataDir string) *runningInstance {
	t.Helper()
	p := &profile.Profile{
		Addr:        "127.0.0.1",
		Port:        freePort(t),
		Data:        dataDir,
		Driver:      profile.DriverSQLite,
		MaxUploadMB: 2,
	}
	if err := p.Validate(); err != nil {
		t.Fatalf("validate profile: %v", err)
	}
	driver, err := storedb.NewDBDriver(p)
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	storeInstance := store.New(driver, p)
	ctx, cancel := context.WithCancel(context.Background())
	if err := storeInstance.Migrate(ctx); err != nil {
		cancel()
		_ = storeInstance.Close()
		t.Fatalf("migrate database: %v", err)
	}
	secrets, err := secret.Load(p.Data)
	if err != nil {
		cancel()
		_ = storeInstance.Close()
		t.Fatalf("load runtime secrets: %v", err)
	}
	srv, err := server.New(ctx, p, storeInstance, secrets)
	if err != nil {
		cancel()
		_ = storeInstance.Close()
		t.Fatalf("create server: %v", err)
	}
	if err := srv.Start(ctx); err != nil {
		cancel()
		_ = storeInstance.Close()
		t.Fatalf("start server: %v", err)
	}
	instance := &runningInstance{
		baseURL: fmt.Sprintf("http://127.0.0.1:%d", p.Port),
		cancel:  cancel,
		server:  srv,
	}
	t.Cleanup(func() { stopInstance(t, instance) })
	waitReady(t, instance.baseURL)
	return instance
}

func stopInstance(t *testing.T, instance *runningInstance) {
	t.Helper()
	if instance == nil || instance.server == nil {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := instance.server.Shutdown(ctx); err != nil {
		t.Errorf("shutdown instance: %v", err)
	}
	instance.cancel()
	instance.server = nil
}

func waitReady(t *testing.T, baseURL string) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		response, err := drillHTTPClient.Get(baseURL + "/readyz")
		if err == nil {
			_ = response.Body.Close()
			if response.StatusCode == http.StatusOK {
				return
			}
		}
		time.Sleep(25 * time.Millisecond)
	}
	t.Fatalf("instance at %s did not become ready", baseURL)
}

func initialize(t *testing.T, baseURL string) string {
	t.Helper()
	return authRequest(t, baseURL+"/api/v1/auth/initialize", map[string]string{
		"username":    drillUsername,
		"displayName": "Restore Drill",
		"password":    drillPassword,
	})
}

func signIn(t *testing.T, baseURL string) string {
	t.Helper()
	return authRequest(t, baseURL+"/api/v1/auth/signin", map[string]string{
		"username": drillUsername,
		"password": drillPassword,
	})
}

func authRequest(t *testing.T, endpoint string, body any) string {
	t.Helper()
	payload := postJSON[struct {
		AccessToken string `json:"accessToken"`
	}](t, endpoint, "", body)
	if payload.AccessToken == "" {
		t.Fatalf("authentication response from %s omitted accessToken", endpoint)
	}
	return payload.AccessToken
}

func createMemo(t *testing.T, baseURL, token, content string) memoDTO {
	t.Helper()
	payload := postJSON[struct {
		Memo memoDTO `json:"memo"`
	}](t, baseURL+"/api/v1/memos", token, map[string]string{
		"content":   content,
		"entryDate": time.Now().UTC().Format("2006-01-02"),
	})
	return payload.Memo
}

func getMemo(t *testing.T, baseURL, token, memoID string) memoDTO {
	t.Helper()
	payload := getJSON[struct {
		Memo memoDTO `json:"memo"`
	}](t, baseURL+"/api/v1/memos/"+memoID, token)
	return payload.Memo
}

func searchMemos(t *testing.T, baseURL, token, query string) []memoDTO {
	t.Helper()
	payload := getJSON[struct {
		Memos []memoDTO `json:"memos"`
	}](t, baseURL+"/api/v1/memos?query="+query+"&limit=100", token)
	return payload.Memos
}

func uploadAttachment(t *testing.T, baseURL, token string) attachmentDTO {
	t.Helper()
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile("file", "restore-drill.txt")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := io.WriteString(part, drillAttachmentBody); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteField("mutation_id", "restore-drill-attachment"); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}

	request, err := http.NewRequest(http.MethodPost, baseURL+"/api/v1/attachments", &body)
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Authorization", "Bearer "+token)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	response := do(t, request)
	payload := decodeResponse[struct {
		Attachment attachmentDTO `json:"attachment"`
	}](t, response)
	return payload.Attachment
}

func configureAI(t *testing.T, baseURL, token, providerURL string) {
	t.Helper()
	requestJSON[map[string]any](t, http.MethodPatch, baseURL+"/api/v1/settings/ai", token, map[string]any{
		"profiles": []map[string]any{{
			"name":        "Restore Drill AI",
			"provider":    "openai",
			"baseUrl":     providerURL,
			"model":       "restore-drill-model",
			"temperature": 0.2,
			"maxTokens":   1000,
			"enabled":     true,
			"active":      true,
			"apiKey":      drillAIKey,
		}},
	})
}

func generateSummary(t *testing.T, baseURL, token, memoID string) {
	t.Helper()
	payload := postJSON[struct {
		AI struct {
			Summary string `json:"summary"`
		} `json:"ai"`
	}](t, baseURL+"/api/v1/memos/"+memoID+":generate-summary", token, nil)
	if payload.AI.Summary != "restore-drill summary" {
		t.Fatalf("summary = %q, want restore-drill summary", payload.AI.Summary)
	}
}

func newMockAIProvider(t *testing.T) *httptest.Server {
	t.Helper()
	provider := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Header.Get("Authorization") != "Bearer "+drillAIKey {
			http.Error(response, "missing key", http.StatusUnauthorized)
			return
		}
		if !strings.HasSuffix(request.URL.Path, "/chat/completions") {
			http.NotFound(response, request)
			return
		}
		response.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(response).Encode(map[string]any{
			"choices": []map[string]any{{"message": map[string]string{"content": "restore-drill summary"}}},
			"usage":   map[string]int{"input_tokens": 4, "output_tokens": 3, "total_tokens": 7},
		})
	}))
	t.Cleanup(provider.Close)
	return provider
}

func postJSON[T any](t *testing.T, endpoint, token string, body any) T {
	t.Helper()
	return requestJSON[T](t, http.MethodPost, endpoint, token, body)
}

func requestJSON[T any](t *testing.T, method, endpoint, token string, body any) T {
	t.Helper()
	payload, err := json.Marshal(body)
	if err != nil {
		t.Fatal(err)
	}
	request, err := http.NewRequest(method, endpoint, bytes.NewReader(payload))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Content-Type", "application/json")
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	return decodeResponse[T](t, do(t, request))
}

func getJSON[T any](t *testing.T, endpoint, token string) T {
	t.Helper()
	request, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		t.Fatal(err)
	}
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	return decodeResponse[T](t, do(t, request))
}

func getBytes(t *testing.T, endpoint, token string) []byte {
	t.Helper()
	request, err := http.NewRequest(http.MethodGet, endpoint, nil)
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Authorization", "Bearer "+token)
	response := do(t, request)
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(response.Body)
		t.Fatalf("GET %s status = %d body=%s", endpoint, response.StatusCode, body)
	}
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err)
	}
	return body
}

func do(t *testing.T, request *http.Request) *http.Response {
	t.Helper()
	response, err := drillHTTPClient.Do(request)
	if err != nil {
		t.Fatalf("%s %s: %v", request.Method, request.URL, err)
	}
	return response
}

func decodeResponse[T any](t *testing.T, response *http.Response) T {
	t.Helper()
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		t.Fatalf("%s %s status = %d body=%s", response.Request.Method, response.Request.URL, response.StatusCode, body)
	}
	var payload T
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatalf("decode %s %s: %v body=%s", response.Request.Method, response.Request.URL, err, body)
	}
	return payload
}

func restoreWithRollback(dataDir, backupDir, rollbackDir string) error {
	if _, err := os.Stat(rollbackDir); !os.IsNotExist(err) {
		if err == nil {
			return fmt.Errorf("rollback path already exists: %s", rollbackDir)
		}
		return fmt.Errorf("check rollback path: %w", err)
	}
	if err := os.Rename(dataDir, rollbackDir); err != nil {
		return fmt.Errorf("preserve current data as rollback: %w", err)
	}
	if err := copyTree(backupDir, dataDir); err != nil {
		return fmt.Errorf("copy backup into restore path (rollback retained at %s): %w", rollbackDir, err)
	}
	return nil
}

func copyTree(source, destination string) error {
	info, err := os.Stat(source)
	if err != nil {
		return err
	}
	if !info.IsDir() {
		return fmt.Errorf("source is not a directory: %s", source)
	}
	return filepath.WalkDir(source, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		relative, err := filepath.Rel(source, path)
		if err != nil {
			return err
		}
		target := filepath.Join(destination, relative)
		entryInfo, err := entry.Info()
		if err != nil {
			return err
		}
		if entryInfo.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("refusing to copy symlink: %s", path)
		}
		if entry.IsDir() {
			return os.MkdirAll(target, entryInfo.Mode().Perm())
		}
		if !entryInfo.Mode().IsRegular() {
			return fmt.Errorf("refusing to copy non-regular file: %s", path)
		}
		return copyFile(path, target, entryInfo.Mode().Perm())
	})
}

func copyFile(source, destination string, mode fs.FileMode) error {
	input, err := os.Open(source)
	if err != nil {
		return err
	}
	defer input.Close()
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_EXCL|os.O_WRONLY, mode)
	if err != nil {
		return err
	}
	if _, err := io.Copy(output, input); err != nil {
		_ = output.Close()
		return err
	}
	return output.Close()
}

func requireIntegrity(t *testing.T, databasePath string) {
	t.Helper()
	database, err := sql.Open("sqlite", databasePath)
	if err != nil {
		t.Fatalf("open SQLite database %s: %v", databasePath, err)
	}
	defer database.Close()
	var result string
	if err := database.QueryRow("PRAGMA integrity_check").Scan(&result); err != nil {
		t.Fatalf("SQLite integrity_check %s: %v", databasePath, err)
	}
	if result != "ok" {
		t.Fatalf("SQLite integrity_check %s = %q", databasePath, result)
	}
	rows, err := database.Query("PRAGMA foreign_key_check")
	if err != nil {
		t.Fatalf("SQLite foreign_key_check %s: %v", databasePath, err)
	}
	defer rows.Close()
	if rows.Next() {
		t.Fatalf("SQLite foreign_key_check %s reported violations", databasePath)
	}
}

func databaseContainsMemo(t *testing.T, databasePath, marker string) bool {
	t.Helper()
	database, err := sql.Open("sqlite", databasePath)
	if err != nil {
		t.Fatal(err)
	}
	defer database.Close()
	var count int
	if err := database.QueryRow("SELECT COUNT(1) FROM memo WHERE content LIKE ?", "%"+marker+"%").Scan(&count); err != nil {
		t.Fatal(err)
	}
	return count > 0
}

func containsMemo(memos []memoDTO, id string) bool {
	for _, memo := range memos {
		if memo.ID == id {
			return true
		}
	}
	return false
}

func containsAttachment(attachments []attachmentDTO, uid string) bool {
	for _, attachment := range attachments {
		if attachment.UID == uid {
			return true
		}
	}
	return false
}

func containsMemoAI(items []struct {
	MemoID  string `json:"memoId"`
	Summary string `json:"summary"`
}, memoID string) bool {
	for _, item := range items {
		if item.MemoID == memoID && item.Summary == "restore-drill summary" {
			return true
		}
	}
	return false
}

func freePort(t *testing.T) int {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	return listener.Addr().(*net.TCPAddr).Port
}

func writeReport(t *testing.T, report *drillReport) {
	t.Helper()
	if t.Failed() {
		report.Result = "failed"
	}
	payload, err := json.MarshalIndent(report, "", "  ")
	if err != nil {
		t.Errorf("encode restore drill report: %v", err)
		return
	}
	t.Logf("restore drill report:\n%s", payload)
	path := os.Getenv("SILLAGE_RESTORE_DRILL_REPORT")
	if path == "" {
		return
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Errorf("create restore drill report directory: %v", err)
		return
	}
	if err := os.WriteFile(path, append(payload, '\n'), 0o600); err != nil {
		t.Errorf("write restore drill report: %v", err)
	}
}
