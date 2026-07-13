package server_test

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
	"github.com/getsillage/sillage/proto/gen/api/v1/apiv1connect"
	"github.com/getsillage/sillage/store"
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

func TestConnectMemoServicePaginates(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewMemoServiceClient(httpServer.Client(), httpServer.URL)
	for _, entryDate := range []string{"2026-06-24", "2026-06-25", "2026-06-26"} {
		req := connect.NewRequest(&apiv1.CreateMemoRequest{
			Content:   "page " + entryDate,
			EntryDate: entryDate,
		})
		req.Header().Set("Authorization", "Bearer "+token)
		if _, err := client.CreateMemo(context.Background(), req); err != nil {
			t.Fatalf("CreateMemo(%s) error = %v", entryDate, err)
		}
	}

	list := func(cursor string) *apiv1.ListMemosResponse {
		t.Helper()
		req := connect.NewRequest(&apiv1.ListMemosRequest{Limit: 2, Cursor: cursor})
		req.Header().Set("Authorization", "Bearer "+token)
		res, err := client.ListMemos(context.Background(), req)
		if err != nil {
			t.Fatalf("ListMemos(cursor=%q) error = %v", cursor, err)
		}
		return res.Msg
	}

	first := list("")
	if len(first.GetMemos()) != 2 || first.GetNextCursor() == "" {
		t.Fatalf("first page = %#v", first)
	}
	if first.GetMemos()[0].GetEntryDate() != "2026-06-26" || first.GetMemos()[1].GetEntryDate() != "2026-06-25" {
		t.Fatalf("first page dates = %q, %q", first.GetMemos()[0].GetEntryDate(), first.GetMemos()[1].GetEntryDate())
	}

	second := list(first.GetNextCursor())
	if len(second.GetMemos()) != 1 || second.GetMemos()[0].GetEntryDate() != "2026-06-24" || second.GetNextCursor() != "" {
		t.Fatalf("second page = %#v", second)
	}
}

func TestConnectMemoListAndSearchThreePartitions(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewMemoServiceClient(httpServer.Client(), httpServer.URL)
	create := func(entryDate string) *apiv1.Memo {
		t.Helper()
		req := connect.NewRequest(&apiv1.CreateMemoRequest{
			Content:   "shared Connect filter content",
			EntryDate: entryDate,
		})
		req.Header().Set("Authorization", "Bearer "+token)
		res, err := client.CreateMemo(context.Background(), req)
		if err != nil {
			t.Fatalf("CreateMemo() error = %v", err)
		}
		return res.Msg.GetMemo()
	}

	favoritedMemo := create("2026-06-28")
	archivedMemo := create("2026-06-27")
	current := create("2026-06-26")
	favoriteReq := connect.NewRequest(&apiv1.SetMemoFavoritedRequest{
		Id:              favoritedMemo.GetId(),
		ExpectedVersion: favoritedMemo.GetVersion(),
		Favorited:       true,
	})
	favoriteReq.Header().Set("Authorization", "Bearer "+token)
	favoriteRes, err := client.SetMemoFavorited(context.Background(), favoriteReq)
	if err != nil {
		t.Fatalf("SetMemoFavorited() error = %v", err)
	}
	if favoriteRes.Msg.GetMemo().GetFavoritedTime() == nil {
		t.Fatalf("favorited memo = %#v, want favorited time", favoriteRes.Msg.GetMemo())
	}
	archiveReq := connect.NewRequest(&apiv1.SetMemoArchivedRequest{
		Id:              archivedMemo.GetId(),
		ExpectedVersion: archivedMemo.GetVersion(),
		Archived:        true,
	})
	archiveReq.Header().Set("Authorization", "Bearer "+token)
	if _, err := client.SetMemoArchived(context.Background(), archiveReq); err != nil {
		t.Fatalf("SetMemoArchived() error = %v", err)
	}

	trueValue := true
	falseValue := false
	assertListID := func(query string, archived, favorited *bool, wantID string) {
		t.Helper()
		req := connect.NewRequest(&apiv1.ListMemosRequest{
			Limit:     1,
			Query:     query,
			Archived:  archived,
			Favorited: favorited,
		})
		if query != "" {
			req.Msg.Cursor = "ignored-for-search"
		}
		req.Header().Set("Authorization", "Bearer "+token)
		res, err := client.ListMemos(context.Background(), req)
		if err != nil {
			t.Fatalf("ListMemos(query=%q archived=%v favorited=%v) error = %v", query, archived, favorited, err)
		}
		if len(res.Msg.GetMemos()) != 1 || res.Msg.GetMemos()[0].GetId() != wantID {
			t.Fatalf("ListMemos(query=%q archived=%v favorited=%v) = %#v, want %s", query, archived, favorited, res.Msg.GetMemos(), wantID)
		}
		if query != "" && res.Msg.GetNextCursor() != "" {
			t.Fatalf("search next cursor = %q, want empty", res.Msg.GetNextCursor())
		}
	}

	for _, query := range []string{"", "Connect"} {
		assertListID(query, &falseValue, &falseValue, current.GetId())
		assertListID(query, &trueValue, &falseValue, archivedMemo.GetId())
		assertListID(query, nil, &trueValue, favoritedMemo.GetId())
	}
}

func TestConnectMemoMutationRequiresExpectedVersion(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewMemoServiceClient(httpServer.Client(), httpServer.URL)
	createReq := connect.NewRequest(&apiv1.CreateMemoRequest{
		Content:   "version guarded Connect memo",
		EntryDate: "2026-06-26",
	})
	createReq.Header().Set("Authorization", "Bearer "+token)
	createRes, err := client.CreateMemo(context.Background(), createReq)
	if err != nil {
		t.Fatalf("CreateMemo() error = %v", err)
	}

	updateReq := connect.NewRequest(&apiv1.SetMemoFavoritedRequest{
		Id:        createRes.Msg.GetMemo().GetId(),
		Favorited: true,
	})
	updateReq.Header().Set("Authorization", "Bearer "+token)
	_, err = client.SetMemoFavorited(context.Background(), updateReq)
	if err == nil {
		t.Fatal("SetMemoFavorited() error = nil, want invalid argument")
	}
	connectErr := new(connect.Error)
	if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeInvalidArgument {
		t.Fatalf("SetMemoFavorited() error = %v, want invalid argument", err)
	}
	if !strings.Contains(connectErr.Message(), "expectedVersion") {
		t.Fatalf("SetMemoFavorited() message = %q", connectErr.Message())
	}

	updateReq = connect.NewRequest(&apiv1.SetMemoFavoritedRequest{
		Id:              createRes.Msg.GetMemo().GetId(),
		ExpectedVersion: 1,
		Favorited:       true,
	})
	updateReq.Header().Set("Authorization", "Bearer "+token)
	updateRes, err := client.SetMemoFavorited(context.Background(), updateReq)
	if err != nil {
		t.Fatalf("SetMemoFavorited() error = %v", err)
	}
	if updateRes.Msg.GetMemo().GetVersion() != 2 || updateRes.Msg.GetMemo().GetFavoritedTime() == nil {
		t.Fatalf("favorited memo = %#v", updateRes.Msg.GetMemo())
	}

	staleReq := connect.NewRequest(&apiv1.SetMemoFavoritedRequest{
		Id:              createRes.Msg.GetMemo().GetId(),
		ExpectedVersion: 1,
		Favorited:       false,
	})
	staleReq.Header().Set("Authorization", "Bearer "+token)
	_, err = client.SetMemoFavorited(context.Background(), staleReq)
	connectErr = new(connect.Error)
	if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeAborted {
		t.Fatalf("SetMemoFavorited(stale) error = %v, want aborted", err)
	}

	getReq := connect.NewRequest(&apiv1.GetMemoRequest{Id: createRes.Msg.GetMemo().GetId()})
	getReq.Header().Set("Authorization", "Bearer "+token)
	getRes, err := client.GetMemo(context.Background(), getReq)
	if err != nil {
		t.Fatalf("GetMemo() error = %v", err)
	}
	if getRes.Msg.GetMemo().GetVersion() != 2 || getRes.Msg.GetMemo().GetFavoritedTime() == nil {
		t.Fatalf("memo changed after stale mutation: %#v", getRes.Msg.GetMemo())
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
	apiKey := "mock-api-key"
	autoSummary := true
	patchReq := connect.NewRequest(&apiv1.PatchAISettingsRequest{
		AutoSummary: &autoSummary,
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
	if !patchRes.Msg.GetAutoSummary() {
		t.Fatal("PatchAISettings response did not preserve global auto_summary")
	}
	if strings.Contains(profiles[0].String(), apiKey) {
		t.Fatal("PatchAISettings response leaked API key")
	}

	disableAutoSummaryReq := connect.NewRequest(&apiv1.SetAIAutoSummaryRequest{AutoSummary: false})
	disableAutoSummaryReq.Header().Set("Authorization", "Bearer "+token)
	disableAutoSummaryRes, err := settingsClient.SetAIAutoSummary(context.Background(), disableAutoSummaryReq)
	if err != nil {
		t.Fatalf("SetAIAutoSummary(false) error = %v", err)
	}
	if disableAutoSummaryRes.Msg.GetAutoSummary() {
		t.Fatal("SetAIAutoSummary(false) response remained enabled")
	}

	enableAutoSummaryReq := connect.NewRequest(&apiv1.SetAIAutoSummaryRequest{AutoSummary: true})
	enableAutoSummaryReq.Header().Set("Authorization", "Bearer "+token)
	enableAutoSummaryRes, err := settingsClient.SetAIAutoSummary(context.Background(), enableAutoSummaryReq)
	if err != nil {
		t.Fatalf("SetAIAutoSummary(true) error = %v", err)
	}
	if !enableAutoSummaryRes.Msg.GetAutoSummary() {
		t.Fatal("SetAIAutoSummary(true) response remained disabled")
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
	if !getRes.Msg.GetAutoSummary() {
		t.Fatal("GetAISettings response did not include global auto_summary")
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
	favorited := true
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
				Favorited: &favorited,
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
	if got := pushRes.Msg.GetResults()[0].GetResource(); got.GetFavoritedTime() == nil || got.GetVersion() != 1 {
		t.Fatalf("PushSync resource = %#v, want favorited time at version 1", got)
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
	account, err := srv.Store.GetAccountByUsername(context.Background(), "felix")
	if err != nil {
		t.Fatalf("GetAccountByUsername() error = %v", err)
	}
	conversation, err := srv.Store.CreateAskConversation(context.Background(), account.ID, "sync ask", "all")
	if err != nil {
		t.Fatalf("CreateAskConversation() error = %v", err)
	}
	if _, err := srv.Store.CreateAskMessage(context.Background(), &store.AskMessage{
		ConversationID: conversation.ID,
		Role:           "assistant",
		Content:        "sync answer",
		SourceRefs:     "[]",
		Model:          "gpt-test",
		PromptVersion:  "ask-answer-v2",
	}); err != nil {
		t.Fatalf("CreateAskMessage() error = %v", err)
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
	if pullRes.Msg.GetMemos()[0].GetFavoritedTime() == nil {
		t.Fatalf("PullSync memo = %#v, want favorited time", pullRes.Msg.GetMemos()[0])
	}
	if got := pullRes.Msg.GetAskMessages(); len(got) != 1 || got[0].GetPromptVersion() != "ask-answer-v2" {
		t.Fatalf("PullSync ask messages = %#v, want prompt version ask-answer-v2", got)
	}
	if pullRes.Msg.GetCursor() == "" || pullRes.Msg.GetHasMore() {
		t.Fatalf("unexpected PullSync cursor/hasMore: cursor=%q hasMore=%v", pullRes.Msg.GetCursor(), pullRes.Msg.GetHasMore())
	}
}

func TestConnectAskServiceGroundedMessages(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	mockAI := newMockAIProvider(t)
	configureMockAIProfile(t, srv, token, mockAI.URL)
	createMemoForAsk(t, srv, token, "今天散步后睡眠更稳定。", "2026-06-26")
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewAskServiceClient(httpServer.Client(), httpServer.URL)
	createReq := connect.NewRequest(&apiv1.CreateAskConversationRequest{ContextScope: "all"})
	createReq.Header().Set("Authorization", "Bearer "+token)
	createRes, err := client.CreateAskConversation(context.Background(), createReq)
	if err != nil {
		t.Fatalf("CreateAskConversation() error = %v", err)
	}
	conversationID := createRes.Msg.GetConversation().GetId()
	if conversationID == "" || createRes.Msg.GetConversation().GetContextScope() != "all" {
		t.Fatalf("unexpected conversation: %#v", createRes.Msg.GetConversation())
	}

	messageReq := connect.NewRequest(&apiv1.CreateAskMessageRequest{
		ConversationId: conversationID,
		Content:        "睡眠有什么变化？",
	})
	messageReq.Header().Set("Authorization", "Bearer "+token)
	messageRes, err := client.CreateAskMessage(context.Background(), messageReq)
	if err != nil {
		t.Fatalf("CreateAskMessage() error = %v", err)
	}
	messages := messageRes.Msg.GetMessages()
	if len(messages) != 2 || messages[0].GetRole() != "user" || messages[1].GetRole() != "assistant" {
		t.Fatalf("unexpected ask messages: %#v", messages)
	}
	if len(messages[1].GetSourceRefs()) == 0 || !strings.Contains(messages[1].GetContent(), "根据当前范围内的记录") {
		t.Fatalf("assistant answer is not grounded: %#v", messages[1])
	}
	if messages[1].GetModel() != "gpt-test" {
		t.Fatalf("assistant model = %q, want gpt-test", messages[1].GetModel())
	}
	if messages[0].GetPromptVersion() != "" || messages[1].GetPromptVersion() != "ask-answer-v2" {
		t.Fatalf("ask prompt versions = %q/%q, want empty/ask-answer-v2", messages[0].GetPromptVersion(), messages[1].GetPromptVersion())
	}

	listReq := connect.NewRequest(&apiv1.ListAskMessagesRequest{ConversationId: conversationID})
	listReq.Header().Set("Authorization", "Bearer "+token)
	listRes, err := client.ListAskMessages(context.Background(), listReq)
	if err != nil {
		t.Fatalf("ListAskMessages() error = %v", err)
	}
	if len(listRes.Msg.GetMessages()) != 2 {
		t.Fatalf("ListAskMessages len = %d, want 2", len(listRes.Msg.GetMessages()))
	}
	if got := listRes.Msg.GetMessages()[1].GetPromptVersion(); got != "ask-answer-v2" {
		t.Fatalf("ListAskMessages assistant prompt version = %q, want ask-answer-v2", got)
	}
}

func TestConnectAskServiceSearchAndArchive(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewAskServiceClient(httpServer.Client(), httpServer.URL)
	create := func(title string) *apiv1.AskConversation {
		t.Helper()
		req := connect.NewRequest(&apiv1.CreateAskConversationRequest{Title: title})
		req.Header().Set("Authorization", "Bearer "+token)
		res, err := client.CreateAskConversation(context.Background(), req)
		if err != nil {
			t.Fatalf("CreateAskConversation(%q) error = %v", title, err)
		}
		return res.Msg.GetConversation()
	}

	active := create("Connect 当前问答")
	archived := create("Connect 归档问答")
	archiveReq := connect.NewRequest(&apiv1.SetAskConversationArchivedRequest{
		ConversationId: archived.GetId(),
		Archived:       true,
	})
	archiveReq.Header().Set("Authorization", "Bearer "+token)
	archiveRes, err := client.SetAskConversationArchived(context.Background(), archiveReq)
	if err != nil {
		t.Fatalf("SetAskConversationArchived() error = %v", err)
	}
	if archiveRes.Msg.GetConversation().GetArchivedTime() == nil {
		t.Fatalf("archived conversation = %#v, want archived time", archiveRes.Msg.GetConversation())
	}
	getReq := connect.NewRequest(&apiv1.GetAskConversationRequest{ConversationId: archived.GetId()})
	getReq.Header().Set("Authorization", "Bearer "+token)
	getRes, err := client.GetAskConversation(context.Background(), getReq)
	if err != nil {
		t.Fatalf("GetAskConversation(archived) error = %v", err)
	}
	if got := getRes.Msg.GetConversation(); got.GetId() != archived.GetId() || got.GetArchivedTime() == nil {
		t.Fatalf("GetAskConversation(archived) = %#v", got)
	}

	listReq := connect.NewRequest(&apiv1.ListAskConversationsRequest{Query: "Connect"})
	listReq.Header().Set("Authorization", "Bearer "+token)
	listRes, err := client.ListAskConversations(context.Background(), listReq)
	if err != nil {
		t.Fatalf("ListAskConversations(default) error = %v", err)
	}
	if got := listRes.Msg.GetConversations(); len(got) != 1 || got[0].GetId() != active.GetId() {
		t.Fatalf("default conversations = %#v, want active only", got)
	}

	archivedOnly := true
	listReq = connect.NewRequest(&apiv1.ListAskConversationsRequest{
		Query:    "归档问答",
		Archived: &archivedOnly,
	})
	listReq.Header().Set("Authorization", "Bearer "+token)
	listRes, err = client.ListAskConversations(context.Background(), listReq)
	if err != nil {
		t.Fatalf("ListAskConversations(archived) error = %v", err)
	}
	if got := listRes.Msg.GetConversations(); len(got) != 1 || got[0].GetId() != archived.GetId() {
		t.Fatalf("archived conversations = %#v, want archived only", got)
	}

	missingReq := connect.NewRequest(&apiv1.SetAskConversationArchivedRequest{
		ConversationId: "missing",
		Archived:       true,
	})
	missingReq.Header().Set("Authorization", "Bearer "+token)
	_, err = client.SetAskConversationArchived(context.Background(), missingReq)
	connectErr := new(connect.Error)
	if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeNotFound {
		t.Fatalf("SetAskConversationArchived(missing) error = %v, want not found", err)
	}

	const otherAccountID = "01800000-0000-7000-8000-000000000098"
	now := time.Now().UTC().UnixMilli()
	if _, err := srv.Store.GetDriver().GetDB().Exec(`
INSERT INTO account (id, username, display_name, password_hash, password_algorithm, created_at, updated_at)
VALUES (?, 'connect-other', 'Other', 'hash', 'test', ?, ?)`, otherAccountID, now, now); err != nil {
		t.Fatalf("insert second account: %v", err)
	}
	otherConversation, err := srv.Store.CreateAskConversation(context.Background(), otherAccountID, "其他账号问答", "all")
	if err != nil {
		t.Fatalf("CreateAskConversation(other account) error = %v", err)
	}
	getReq = connect.NewRequest(&apiv1.GetAskConversationRequest{ConversationId: otherConversation.ID})
	getReq.Header().Set("Authorization", "Bearer "+token)
	_, err = client.GetAskConversation(context.Background(), getReq)
	connectErr = new(connect.Error)
	if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeNotFound {
		t.Fatalf("GetAskConversation(other account) error = %v, want not found", err)
	}
}

func TestConnectAttachmentServiceGet(t *testing.T) {
	srv := newTestServer(t)
	token := initializeAndToken(t, srv)

	body, contentType := multipartBody(t, "hello.txt", "hello attachment", nil)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/attachments", body)
	req.Host = "localhost:5231"
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", contentType)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("upload status = %d body=%s", rec.Code, rec.Body.String())
	}
	uploaded := decodeAttachmentResponse(t, rec.Body.Bytes())
	uid := uploaded["uid"].(string)

	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)
	client := apiv1connect.NewAttachmentServiceClient(httpServer.Client(), httpServer.URL)
	getReq := connect.NewRequest(&apiv1.GetAttachmentRequest{Uid: uid})
	getReq.Header().Set("Authorization", "Bearer "+token)
	getRes, err := client.GetAttachment(context.Background(), getReq)
	if err != nil {
		t.Fatalf("GetAttachment() error = %v", err)
	}
	if getRes.Msg.GetAttachment().GetUid() != uid || getRes.Msg.GetAttachment().GetFilename() != "hello.txt" {
		t.Fatalf("unexpected attachment: %#v", getRes.Msg.GetAttachment())
	}
}
