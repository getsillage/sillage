package server_test

import (
	"context"
	"errors"
	"net/http/httptest"
	"strings"
	"testing"

	"connectrpc.com/connect"

	apiv1 "github.com/miofelix/sillage/proto/gen/api/v1"
	"github.com/miofelix/sillage/proto/gen/api/v1/apiv1connect"
)

func TestConnectMemoServiceCreateAndList(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewMemoServiceClient(httpServer.Client(), httpServer.URL)
	createReq := connect.NewRequest(&apiv1.CreateMemoRequest{
		Content:   "通过 Connect 创建记录",
		EntryDate: "2026-06-26",
	})
	createReq.Header().Set("Authorization", "Bearer "+token)
	createRes, err := client.CreateMemo(context.Background(), createReq)
	if err != nil {
		t.Fatalf("CreateMemo() error = %v", err)
	}
	if createRes.Msg.GetMemo().GetId() == "" || createRes.Msg.GetMemo().GetContent() != "通过 Connect 创建记录" {
		t.Fatalf("unexpected create memo response: %#v", createRes.Msg.GetMemo())
	}

	listReq := connect.NewRequest(&apiv1.ListMemosRequest{Limit: 20})
	listReq.Header().Set("Authorization", "Bearer "+token)
	listRes, err := client.ListMemos(context.Background(), listReq)
	if err != nil {
		t.Fatalf("ListMemos() error = %v", err)
	}
	if len(listRes.Msg.GetMemos()) != 1 {
		t.Fatalf("ListMemos len = %d, want 1", len(listRes.Msg.GetMemos()))
	}
}

func TestConnectMemoServiceRequiresAuth(t *testing.T) {
	srv := newTestServer(t)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewMemoServiceClient(httpServer.Client(), httpServer.URL)
	_, err := client.ListMemos(context.Background(), connect.NewRequest(&apiv1.ListMemosRequest{}))
	if err == nil {
		t.Fatal("ListMemos() error = nil, want unauthenticated")
	}
	connectErr := new(connect.Error)
	if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeUnauthenticated {
		t.Fatalf("ListMemos() error = %v, want unauthenticated", err)
	}
}

func TestConnectAuthServiceInitializeMeAndSettings(t *testing.T) {
	srv := newTestServer(t)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	authClient := apiv1connect.NewAuthServiceClient(httpServer.Client(), httpServer.URL)
	bootstrapRes, err := authClient.Bootstrap(context.Background(), connect.NewRequest(&apiv1.BootstrapRequest{}))
	if err != nil {
		t.Fatalf("Bootstrap() error = %v", err)
	}
	if bootstrapRes.Msg.GetInitialized() {
		t.Fatal("Bootstrap initialized = true before initialization")
	}

	initRes, err := authClient.Initialize(context.Background(), connect.NewRequest(&apiv1.InitializeRequest{
		Username:    "Felix",
		DisplayName: "Felix",
		Password:    "passw0rd!",
	}))
	if err != nil {
		t.Fatalf("Initialize() error = %v", err)
	}
	token := initRes.Msg.GetAccessToken()
	if token == "" || initRes.Msg.GetAccount().GetUsername() != "felix" {
		t.Fatalf("unexpected initialize response: %#v", initRes.Msg)
	}
	if initRes.Header().Get("Set-Cookie") == "" {
		t.Fatal("Initialize() did not set refresh cookie")
	}

	meReq := connect.NewRequest(&apiv1.MeRequest{})
	meReq.Header().Set("Authorization", "Bearer "+token)
	meRes, err := authClient.Me(context.Background(), meReq)
	if err != nil {
		t.Fatalf("Me() error = %v", err)
	}
	if meRes.Msg.GetAccount().GetId() != initRes.Msg.GetAccount().GetId() {
		t.Fatalf("Me account id = %q, want %q", meRes.Msg.GetAccount().GetId(), initRes.Msg.GetAccount().GetId())
	}

	settingsClient := apiv1connect.NewSettingsServiceClient(httpServer.Client(), httpServer.URL)
	apiKey := "sk-test"
	patchReq := connect.NewRequest(&apiv1.PatchAISettingsRequest{
		Profiles: []*apiv1.AIProfileInput{{
			Name:        "本地测试",
			Provider:    "openai",
			BaseUrl:     "https://api.openai.com/v1",
			Model:       "gpt-test",
			Enabled:     true,
			Active:      true,
			Temperature: 0.2,
			MaxTokens:   1000,
			ApiKey:      &apiKey,
		}},
	})
	patchReq.Header().Set("Authorization", "Bearer "+token)
	patchRes, err := settingsClient.PatchAISettings(context.Background(), patchReq)
	if err != nil {
		t.Fatalf("PatchAISettings() error = %v", err)
	}
	profiles := patchRes.Msg.GetProfiles()
	if len(profiles) != 1 || !profiles[0].GetHasApiKey() || profiles[0].GetKeyUnavailable() {
		t.Fatalf("unexpected profiles after patch: %#v", profiles)
	}
	if strings.Contains(profiles[0].String(), apiKey) {
		t.Fatal("PatchAISettings response leaked API key")
	}

	getReq := connect.NewRequest(&apiv1.GetAISettingsRequest{})
	getReq.Header().Set("Authorization", "Bearer "+token)
	getRes, err := settingsClient.GetAISettings(context.Background(), getReq)
	if err != nil {
		t.Fatalf("GetAISettings() error = %v", err)
	}
	if len(getRes.Msg.GetProfiles()) != 1 || !getRes.Msg.GetProfiles()[0].GetHasApiKey() {
		t.Fatalf("unexpected profiles from get: %#v", getRes.Msg.GetProfiles())
	}

	refreshReq := connect.NewRequest(&apiv1.RefreshRequest{})
	refreshReq.Header().Set("Cookie", initRes.Header().Get("Set-Cookie"))
	refreshRes, err := authClient.Refresh(context.Background(), refreshReq)
	if err != nil {
		t.Fatalf("Refresh() error = %v", err)
	}
	if refreshRes.Msg.GetAccessToken() == "" || refreshRes.Header().Get("Set-Cookie") == "" {
		t.Fatalf("Refresh response missing token or cookie")
	}

	signOutReq := connect.NewRequest(&apiv1.SignOutRequest{})
	signOutReq.Header().Set("Cookie", refreshRes.Header().Get("Set-Cookie"))
	signOutRes, err := authClient.SignOut(context.Background(), signOutReq)
	if err != nil {
		t.Fatalf("SignOut() error = %v", err)
	}
	if cookie := signOutRes.Header().Get("Set-Cookie"); !strings.Contains(cookie, "Max-Age=0") {
		t.Fatalf("SignOut cookie = %q, want cleared cookie", cookie)
	}
}

func TestConnectSyncServicePushAndPull(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewSyncServiceClient(httpServer.Client(), httpServer.URL)
	pushReq := connect.NewRequest(&apiv1.PushSyncRequest{
		Changes: []*apiv1.SyncChange{{
			MutationId:   "connect-create-1",
			ResourceType: "memo",
			ResourceId:   "01800000-0000-7000-8000-000000000101",
			Action:       "create",
			Memo: &apiv1.SyncMemoPayload{
				Id:        "01800000-0000-7000-8000-000000000101",
				Content:   "Connect 同步创建的记录",
				EntryDate: "2026-06-26",
			},
		}},
	})
	pushReq.Header().Set("Authorization", "Bearer "+token)
	pushRes, err := client.PushSync(context.Background(), pushReq)
	if err != nil {
		t.Fatalf("PushSync() error = %v", err)
	}
	if got := pushRes.Msg.GetResults()[0].GetStatus(); got != "applied" {
		t.Fatalf("PushSync status = %q, want applied", got)
	}

	pushReq = connect.NewRequest(pushReq.Msg)
	pushReq.Header().Set("Authorization", "Bearer "+token)
	pushRes, err = client.PushSync(context.Background(), pushReq)
	if err != nil {
		t.Fatalf("PushSync(idempotent) error = %v", err)
	}
	if !pushRes.Msg.GetResults()[0].GetIdempotent() {
		t.Fatal("PushSync duplicate must be idempotent")
	}

	pullReq := connect.NewRequest(&apiv1.PullSyncRequest{Limit: 20})
	pullReq.Header().Set("Authorization", "Bearer "+token)
	pullRes, err := client.PullSync(context.Background(), pullReq)
	if err != nil {
		t.Fatalf("PullSync() error = %v", err)
	}
	if len(pullRes.Msg.GetMemos()) != 1 {
		t.Fatalf("PullSync memos len = %d, want 1", len(pullRes.Msg.GetMemos()))
	}
	if pullRes.Msg.GetCursor() == "" || pullRes.Msg.GetHasMore() {
		t.Fatalf("unexpected PullSync cursor/hasMore: cursor=%q hasMore=%v", pullRes.Msg.GetCursor(), pullRes.Msg.GetHasMore())
	}
}
