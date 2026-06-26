package server_test

import (
	"context"
	"errors"
	"net/http/httptest"
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
