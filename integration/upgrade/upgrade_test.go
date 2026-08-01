//go:build upgrade_drill

package upgrade_test

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
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"testing"
	"time"

	_ "modernc.org/sqlite"
)

const (
	upgradeUsername       = "upgrade-drill"
	upgradePassword       = "upgrade-drill-password"
	upgradeMemoMarker     = "upgradedrillmarker"
	upgradeAttachmentBody = "Sillage cross-version upgrade attachment\n"
	upgradeAIKey          = "upgrade-drill-api-key"
)

var upgradeHTTPClient = &http.Client{Timeout: 10 * time.Second}

type upgradeReport struct {
	FromTag                 string `json:"fromTag"`
	CandidateVersion        string `json:"candidateVersion"`
	SourceSchemaVersion     string `json:"sourceSchemaVersion"`
	TargetSchemaVersion     string `json:"targetSchemaVersion"`
	SchemaChanged           bool   `json:"schemaChanged"`
	BackupIntegrity         bool   `json:"backupIntegrity"`
	UpgradeToReadyMS        int64  `json:"upgradeToReadyMs"`
	UpgradedIntegrity       bool   `json:"upgradedIntegrity"`
	SchemaMigrated          bool   `json:"schemaMigrated"`
	SignIn                  bool   `json:"signIn"`
	Record                  bool   `json:"record"`
	Search                  bool   `json:"search"`
	Attachment              bool   `json:"attachment"`
	AISettings              bool   `json:"aiSettings"`
	AIKeyDecryption         bool   `json:"aiKeyDecryption"`
	SyncBootstrap           bool   `json:"syncBootstrap"`
	OldBinaryRejectedSchema bool   `json:"oldBinaryRejectedSchema"`
	RollbackIntegrity       bool   `json:"rollbackIntegrity"`
	RollbackReady           bool   `json:"rollbackReady"`
	Result                  string `json:"result"`
}

type processInstance struct {
	baseURL string
	command *exec.Cmd
	done    chan error
	logs    *bytes.Buffer
}

type memoDTO struct {
	ID      string `json:"id"`
	Content string `json:"content"`
}

type attachmentDTO struct {
	UID string `json:"uid"`
	URL string `json:"url"`
}

func TestLatestStableUpgradeAndRollback(t *testing.T) {
	repoRoot := repositoryRoot(t)
	fromTag := strings.TrimSpace(os.Getenv("SILLAGE_UPGRADE_FROM_TAG"))
	if fromTag == "" {
		t.Fatal("SILLAGE_UPGRADE_FROM_TAG is required")
	}
	candidateVersion := currentAndroidVersion(t, repoRoot)
	report := &upgradeReport{FromTag: fromTag, CandidateVersion: candidateVersion, Result: "failed"}
	defer writeUpgradeReport(t, report)

	root := t.TempDir()
	stableSource := filepath.Join(root, "stable-source")
	extractTag(t, repoRoot, fromTag, stableSource)
	sourceSchemaVersion := schemaVersion(t, stableSource)
	targetSchemaVersion := schemaVersion(t, repoRoot)
	report.SourceSchemaVersion = sourceSchemaVersion
	report.TargetSchemaVersion = targetSchemaVersion
	report.SchemaChanged = sourceSchemaVersion != targetSchemaVersion
	stableBinary := filepath.Join(root, "sillage-stable")
	candidateBinary := filepath.Join(root, "sillage-candidate")
	buildBinary(t, stableSource, stableBinary, strings.TrimPrefix(fromTag, "v"), fromTag)
	buildBinary(t, repoRoot, candidateBinary, candidateVersion, "upgrade-drill")

	mockAI := newMockAIProvider(t)
	dataDir := filepath.Join(root, "data")
	backupDir := filepath.Join(root, "backup-before-upgrade")
	upgradedDir := filepath.Join(root, "data-after-upgrade")

	stable := startBinary(t, stableBinary, dataDir)
	token := initialize(t, stable.baseURL)
	memo := createMemo(t, stable.baseURL, token)
	attachment := uploadAttachment(t, stable.baseURL, token)
	configureAI(t, stable.baseURL, token, mockAI.URL)
	generateSummary(t, stable.baseURL, token, memo.ID)
	stopBinary(t, stable)

	databasePath := filepath.Join(dataDir, "sillage.db")
	requireIntegrity(t, databasePath)
	if err := copyTree(dataDir, backupDir); err != nil {
		t.Fatalf("copy pre-upgrade backup: %v", err)
	}
	requireIntegrity(t, filepath.Join(backupDir, "sillage.db"))
	report.BackupIntegrity = true

	upgradeStarted := time.Now()
	candidate := startBinary(t, candidateBinary, dataDir)
	report.UpgradeToReadyMS = time.Since(upgradeStarted).Milliseconds()
	upgradedToken := signIn(t, candidate.baseURL)
	report.SignIn = true

	if got := getMemo(t, candidate.baseURL, upgradedToken, memo.ID); got.Content != memo.Content {
		t.Fatalf("upgraded memo content = %q, want %q", got.Content, memo.Content)
	}
	report.Record = true
	found := searchMemos(t, candidate.baseURL, upgradedToken, upgradeMemoMarker)
	if len(found) != 1 || found[0].ID != memo.ID {
		t.Fatalf("upgraded search = %#v, want memo %s", found, memo.ID)
	}
	report.Search = true
	if body := getBytes(t, resolveURL(candidate.baseURL, attachment.URL), upgradedToken); string(body) != upgradeAttachmentBody {
		t.Fatalf("upgraded attachment = %q, want %q", body, upgradeAttachmentBody)
	}
	report.Attachment = true
	assertAISettings(t, candidate.baseURL, upgradedToken)
	report.AISettings = true
	generateSummary(t, candidate.baseURL, upgradedToken, memo.ID)
	report.AIKeyDecryption = true
	assertSyncBootstrap(t, candidate.baseURL, upgradedToken, memo.ID, attachment.UID)
	report.SyncBootstrap = true
	stopBinary(t, candidate)

	requireIntegrity(t, databasePath)
	report.UpgradedIntegrity = true
	assertSchemaVersion(t, databasePath, targetSchemaVersion)
	report.SchemaMigrated = true
	if report.SchemaChanged {
		runExpectedSchemaRejection(t, stableBinary, dataDir)
		report.OldBinaryRejectedSchema = true
	}

	if err := os.Rename(dataDir, upgradedDir); err != nil {
		t.Fatalf("preserve upgraded data: %v", err)
	}
	if err := copyTree(backupDir, dataDir); err != nil {
		t.Fatalf("restore pre-upgrade backup: %v", err)
	}
	requireIntegrity(t, filepath.Join(dataDir, "sillage.db"))
	report.RollbackIntegrity = true

	rolledBack := startBinary(t, stableBinary, dataDir)
	rollbackToken := signIn(t, rolledBack.baseURL)
	if got := getMemo(t, rolledBack.baseURL, rollbackToken, memo.ID); got.Content != memo.Content {
		t.Fatalf("rolled-back memo content = %q, want %q", got.Content, memo.Content)
	}
	if body := getBytes(t, resolveURL(rolledBack.baseURL, attachment.URL), rollbackToken); string(body) != upgradeAttachmentBody {
		t.Fatalf("rolled-back attachment = %q, want %q", body, upgradeAttachmentBody)
	}
	assertAISettings(t, rolledBack.baseURL, rollbackToken)
	stopBinary(t, rolledBack)
	report.RollbackReady = true
	report.Result = "passed"
}

func repositoryRoot(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("resolve test source path")
	}
	return filepath.Clean(filepath.Join(filepath.Dir(file), "..", ".."))
}

func currentAndroidVersion(t *testing.T, root string) string {
	t.Helper()
	content, err := os.ReadFile(filepath.Join(root, "apps", "native", "androidApp", "build.gradle.kts"))
	if err != nil {
		t.Fatal(err)
	}
	match := regexp.MustCompile(`versionName\s*=\s*"([^"]+)"`).FindSubmatch(content)
	if len(match) != 2 {
		t.Fatal("Android versionName is missing")
	}
	return string(match[1])
}

func schemaVersion(t *testing.T, root string) string {
	t.Helper()
	content, err := os.ReadFile(filepath.Join(root, "store", "migrator.go"))
	if err != nil {
		t.Fatal(err)
	}
	match := regexp.MustCompile(`currentSchemaVersion\s*=\s*"([^"]+)"`).FindSubmatch(content)
	if len(match) != 2 {
		t.Fatal("currentSchemaVersion is missing")
	}
	return string(match[1])
}

func extractTag(t *testing.T, repoRoot, tag, destination string) {
	t.Helper()
	if err := os.MkdirAll(destination, 0o700); err != nil {
		t.Fatal(err)
	}
	archive := filepath.Join(t.TempDir(), "stable.tar")
	runCommand(t, repoRoot, "git", "archive", "--format=tar", "--output", archive, tag)
	runCommand(t, repoRoot, "tar", "-xf", archive, "-C", destination)
}

func buildBinary(t *testing.T, sourceDir, output, version, revision string) {
	t.Helper()
	runCommand(t, sourceDir, "go", "build", "-trimpath", "-ldflags", fmt.Sprintf("-s -w -X main.version=%s -X main.revision=%s", version, revision), "-o", output, "./cmd/sillage")
}

func runCommand(t *testing.T, directory, name string, args ...string) {
	t.Helper()
	command := exec.Command(name, args...)
	command.Dir = directory
	output, err := command.CombinedOutput()
	if err != nil {
		t.Fatalf("%s %s: %v\n%s", name, strings.Join(args, " "), err, output)
	}
}

func startBinary(t *testing.T, binary, dataDir string) *processInstance {
	t.Helper()
	port := freePort(t)
	logs := &bytes.Buffer{}
	command := exec.Command(binary, "--addr", "127.0.0.1", "--port", fmt.Sprintf("%d", port), "--data", dataDir, "--log-format", "text", "--log-level", "info")
	command.Env = cleanEnvironment()
	command.Stdout = logs
	command.Stderr = logs
	if err := command.Start(); err != nil {
		t.Fatalf("start %s: %v", binary, err)
	}
	instance := &processInstance{baseURL: fmt.Sprintf("http://127.0.0.1:%d", port), command: command, done: make(chan error, 1), logs: logs}
	go func() { instance.done <- command.Wait() }()
	t.Cleanup(func() { stopBinary(t, instance) })
	waitReady(t, instance)
	return instance
}

func cleanEnvironment() []string {
	result := make([]string, 0, len(os.Environ()))
	for _, item := range os.Environ() {
		name := item
		if index := strings.IndexByte(item, '='); index >= 0 {
			name = item[:index]
		}
		if strings.HasPrefix(name, "SILLAGE_") || name == "SESSION_SECRET" || name == "ENCRYPTION_SECRET" {
			continue
		}
		result = append(result, item)
	}
	return result
}

func waitReady(t *testing.T, instance *processInstance) {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		select {
		case err := <-instance.done:
			instance.command = nil
			t.Fatalf("instance exited before readiness: %v\n%s", err, instance.logs.String())
		default:
		}
		response, err := upgradeHTTPClient.Get(instance.baseURL + "/readyz")
		if err == nil {
			_ = response.Body.Close()
			if response.StatusCode == http.StatusOK {
				return
			}
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatalf("instance did not become ready: %s", instance.logs.String())
}

func stopBinary(t *testing.T, instance *processInstance) {
	t.Helper()
	if instance == nil || instance.command == nil || instance.command.Process == nil {
		return
	}
	_ = instance.command.Process.Signal(os.Interrupt)
	select {
	case err := <-instance.done:
		if err != nil {
			t.Errorf("instance shutdown: %v\n%s", err, instance.logs.String())
		}
	case <-time.After(5 * time.Second):
		_ = instance.command.Process.Kill()
		<-instance.done
		t.Errorf("instance required forced shutdown\n%s", instance.logs.String())
	}
	instance.command = nil
}

func runExpectedSchemaRejection(t *testing.T, stableBinary, dataDir string) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	command := exec.CommandContext(ctx, stableBinary, "--addr", "127.0.0.1", "--port", fmt.Sprintf("%d", freePort(t)), "--data", dataDir, "--log-format", "text")
	command.Env = cleanEnvironment()
	output, err := command.CombinedOutput()
	if err == nil {
		t.Fatal("latest stable binary accepted the upgraded database without a rollback backup")
	}
	if ctx.Err() == context.DeadlineExceeded {
		t.Fatalf("latest stable binary did not reject upgraded schema promptly\n%s", output)
	}
	if !bytes.Contains(output, []byte("newer than this binary supports")) {
		t.Fatalf("schema rejection output = %q", output)
	}
}

func initialize(t *testing.T, baseURL string) string {
	t.Helper()
	return authRequest(t, baseURL+"/api/v1/auth/initialize", map[string]string{"username": upgradeUsername, "displayName": "Upgrade Drill", "password": upgradePassword})
}

func signIn(t *testing.T, baseURL string) string {
	t.Helper()
	return authRequest(t, baseURL+"/api/v1/auth/signin", map[string]string{"username": upgradeUsername, "password": upgradePassword})
}

func authRequest(t *testing.T, endpoint string, body any) string {
	t.Helper()
	payload := requestJSON[struct {
		AccessToken string `json:"accessToken"`
	}](t, http.MethodPost, endpoint, "", body)
	if payload.AccessToken == "" {
		t.Fatalf("authentication response from %s omitted accessToken", endpoint)
	}
	return payload.AccessToken
}

func createMemo(t *testing.T, baseURL, token string) memoDTO {
	t.Helper()
	payload := requestJSON[struct {
		Memo memoDTO `json:"memo"`
	}](t, http.MethodPost, baseURL+"/api/v1/memos", token, map[string]string{"content": upgradeMemoMarker + " survives upgrade and rollback", "entryDate": time.Now().UTC().Format("2006-01-02")})
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
	part, err := writer.CreateFormFile("file", "upgrade-drill.txt")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := io.WriteString(part, upgradeAttachmentBody); err != nil {
		t.Fatal(err)
	}
	if err := writer.WriteField("mutation_id", "upgrade-drill-attachment"); err != nil {
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
	requestJSON[map[string]any](t, http.MethodPatch, baseURL+"/api/v1/settings/ai", token, map[string]any{"profiles": []map[string]any{{"name": "Upgrade Drill AI", "provider": "openai", "baseUrl": providerURL, "model": "upgrade-drill-model", "temperature": 0.2, "maxTokens": 1000, "enabled": true, "active": true, "apiKey": upgradeAIKey}}})
}

func assertAISettings(t *testing.T, baseURL, token string) {
	t.Helper()
	settings := getJSON[struct {
		Profiles []struct {
			Name      string `json:"name"`
			HasAPIKey bool   `json:"hasApiKey"`
			Active    bool   `json:"active"`
		} `json:"profiles"`
	}](t, baseURL+"/api/v1/settings/ai", token)
	if len(settings.Profiles) != 1 || settings.Profiles[0].Name != "Upgrade Drill AI" || !settings.Profiles[0].HasAPIKey || !settings.Profiles[0].Active {
		t.Fatalf("AI settings after transition = %#v", settings.Profiles)
	}
}

func generateSummary(t *testing.T, baseURL, token, memoID string) {
	t.Helper()
	payload := requestJSON[struct {
		AI struct {
			Summary string `json:"summary"`
		} `json:"ai"`
	}](t, http.MethodPost, baseURL+"/api/v1/memos/"+memoID+":generate-summary", token, nil)
	if payload.AI.Summary != "upgrade-drill summary" {
		t.Fatalf("summary = %q, want upgrade-drill summary", payload.AI.Summary)
	}
}

func assertSyncBootstrap(t *testing.T, baseURL, token, memoID, attachmentUID string) {
	t.Helper()
	sync := getJSON[struct {
		Memos       []memoDTO       `json:"memos"`
		Attachments []attachmentDTO `json:"attachments"`
		MemoAI      []struct {
			MemoID string `json:"memoId"`
		} `json:"memoAi"`
		NextCursor string `json:"nextCursor"`
	}](t, baseURL+"/api/v1/sync?limit=200", token)
	if !containsMemo(sync.Memos, memoID) || !containsAttachment(sync.Attachments, attachmentUID) || !containsMemoAI(sync.MemoAI, memoID) || sync.NextCursor == "" {
		t.Fatalf("sync bootstrap missing resources: %#v", sync)
	}
}

func newMockAIProvider(t *testing.T) *httptest.Server {
	t.Helper()
	provider := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Header.Get("Authorization") != "Bearer "+upgradeAIKey {
			http.Error(response, "missing key", http.StatusUnauthorized)
			return
		}
		if !strings.HasSuffix(request.URL.Path, "/chat/completions") {
			http.NotFound(response, request)
			return
		}
		response.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(response).Encode(map[string]any{"choices": []map[string]any{{"message": map[string]string{"content": "upgrade-drill summary"}}}, "usage": map[string]int{"input_tokens": 4, "output_tokens": 3, "total_tokens": 7}})
	}))
	t.Cleanup(provider.Close)
	return provider
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
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusOK {
		t.Fatalf("GET %s status = %d body=%s", endpoint, response.StatusCode, body)
	}
	return body
}

func do(t *testing.T, request *http.Request) *http.Response {
	t.Helper()
	response, err := upgradeHTTPClient.Do(request)
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

func resolveURL(baseURL, value string) string {
	if strings.HasPrefix(value, "http://") || strings.HasPrefix(value, "https://") {
		return value
	}
	return strings.TrimRight(baseURL, "/") + "/" + strings.TrimLeft(value, "/")
}

func requireIntegrity(t *testing.T, databasePath string) {
	t.Helper()
	database, err := sql.Open("sqlite", databasePath)
	if err != nil {
		t.Fatal(err)
	}
	defer database.Close()
	var result string
	if err := database.QueryRow("PRAGMA integrity_check").Scan(&result); err != nil {
		t.Fatal(err)
	}
	if result != "ok" {
		t.Fatalf("SQLite integrity_check = %q", result)
	}
	rows, err := database.Query("PRAGMA foreign_key_check")
	if err != nil {
		t.Fatal(err)
	}
	defer rows.Close()
	if rows.Next() {
		t.Fatal("SQLite foreign_key_check reported violations")
	}
}

func assertSchemaVersion(t *testing.T, databasePath, expected string) {
	t.Helper()
	database, err := sql.Open("sqlite", databasePath)
	if err != nil {
		t.Fatal(err)
	}
	defer database.Close()
	var version string
	if err := database.QueryRow("SELECT value FROM system_setting WHERE key = 'schema_version'").Scan(&version); err != nil {
		t.Fatal(err)
	}
	if version != expected {
		t.Fatalf("schema version = %q, want %q", version, expected)
	}
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

func containsMemo(items []memoDTO, id string) bool {
	for _, item := range items {
		if item.ID == id {
			return true
		}
	}
	return false
}

func containsAttachment(items []attachmentDTO, uid string) bool {
	for _, item := range items {
		if item.UID == uid {
			return true
		}
	}
	return false
}

func containsMemoAI(items []struct {
	MemoID string `json:"memoId"`
}, memoID string) bool {
	for _, item := range items {
		if item.MemoID == memoID {
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

func writeUpgradeReport(t *testing.T, report *upgradeReport) {
	t.Helper()
	if t.Failed() {
		report.Result = "failed"
	}
	payload, err := json.MarshalIndent(report, "", "  ")
	if err != nil {
		t.Errorf("encode upgrade drill report: %v", err)
		return
	}
	t.Logf("upgrade drill report:\n%s", payload)
	path := os.Getenv("SILLAGE_UPGRADE_DRILL_REPORT")
	if path == "" {
		return
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Errorf("create upgrade report directory: %v", err)
		return
	}
	if err := os.WriteFile(path, append(payload, '\n'), 0o600); err != nil {
		t.Errorf("write upgrade report: %v", err)
	}
}
