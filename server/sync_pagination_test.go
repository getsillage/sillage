package server_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"
	"testing"

	"github.com/getsillage/sillage/store"
)

type syncPageItem struct {
	ID     string `json:"id"`
	MemoID string `json:"memoId"`
}

type syncPagePayload struct {
	Attachments      []syncPageItem `json:"attachments"`
	MemoAI           []syncPageItem `json:"memoAi"`
	AskConversations []syncPageItem `json:"askConversations"`
	AskMessages      []syncPageItem `json:"askMessages"`
	NextCursor       string         `json:"nextCursor"`
	HasMore          bool           `json:"hasMore"`
}

func TestSyncPullAllStreamsClampAndLookAheadAtMaxLimit(t *testing.T) {
	const pageSize = store.MaxSyncPageLimit

	srv := newTestServer(t)
	token := initializeAndToken(t, srv)
	ctx := context.Background()
	account, err := srv.Store.GetAccountByUsername(ctx, "felix")
	if err != nil {
		t.Fatalf("GetAccountByUsername() error = %v", err)
	}

	for i := 0; i < pageSize+1; i++ {
		suffix := strconv.Itoa(i)
		memo, err := srv.Store.CreateMemo(ctx, &store.CreateMemo{
			CreatorID: account.ID,
			Content:   "sync memo " + suffix,
			EntryDate: "2026-06-26",
		})
		if err != nil {
			t.Fatalf("CreateMemo(%d) error = %v", i, err)
		}
		if _, err := srv.Store.CreateAttachment(ctx, &store.CreateAttachment{
			CreatorID:   account.ID,
			StorageRef:  "sync/attachment-" + suffix,
			Filename:    "attachment-" + suffix + ".txt",
			ContentType: "text/plain",
			Size:        int64(i + 1),
		}); err != nil {
			t.Fatalf("CreateAttachment(%d) error = %v", i, err)
		}
		if _, err := srv.Store.UpsertMemoAI(ctx, &store.UpsertMemoAI{
			MemoID:        memo.ID,
			Summary:       "summary " + suffix,
			Provider:      "test",
			Model:         "test-model",
			PromptVersion: "test-v1",
			SourceMemoIDs: "[]",
			Status:        "complete",
		}); err != nil {
			t.Fatalf("UpsertMemoAI(%d) error = %v", i, err)
		}
		conversation, err := srv.Store.CreateAskConversation(ctx, account.ID, "conversation "+suffix, "all")
		if err != nil {
			t.Fatalf("CreateAskConversation(%d) error = %v", i, err)
		}
		if _, err := srv.Store.CreateAskMessage(ctx, &store.AskMessage{
			ConversationID: conversation.ID,
			Role:           "user",
			Content:        "message " + suffix,
			SourceRefs:     "[]",
		}); err != nil {
			t.Fatalf("CreateAskMessage(%d) error = %v", i, err)
		}
	}

	readPage := func(t *testing.T, path string) syncPagePayload {
		t.Helper()
		res := doJSON(t, srv, http.MethodGet, path, nil, bearer(token))
		if res.Code != http.StatusOK {
			t.Fatalf("GET %s status = %d body=%s", path, res.Code, res.Body.String())
		}
		var payload syncPagePayload
		if err := json.Unmarshal(res.Body.Bytes(), &payload); err != nil {
			t.Fatalf("decode sync page: %v", err)
		}
		return payload
	}

	assertPages := func(t *testing.T, name string, firstItems, secondItems []syncPageItem) {
		t.Helper()
		if len(firstItems) != pageSize || len(secondItems) != 1 {
			t.Fatalf("%s page sizes = %d/%d, want %d/1", name, len(firstItems), len(secondItems), pageSize)
		}
		seen := make(map[string]struct{}, pageSize+1)
		for _, item := range append(firstItems, secondItems...) {
			id := item.ID
			if id == "" {
				id = item.MemoID
			}
			if id == "" {
				t.Fatalf("%s contains an item without an id", name)
			}
			if _, ok := seen[id]; ok {
				t.Fatalf("%s duplicated id %s across pages", name, id)
			}
			seen[id] = struct{}{}
		}
		if len(seen) != pageSize+1 {
			t.Fatalf("%s unique items = %d, want %d", name, len(seen), pageSize+1)
		}
	}

	for _, requestedLimit := range []int{pageSize, 500} {
		t.Run("limit="+strconv.Itoa(requestedLimit), func(t *testing.T) {
			first := readPage(t, "/api/v1/sync?limit="+strconv.Itoa(requestedLimit))
			if !first.HasMore || first.NextCursor == "" {
				t.Fatalf("first page hasMore/cursor = %t/%q", first.HasMore, first.NextCursor)
			}
			second := readPage(t, "/api/v1/sync?limit="+strconv.Itoa(requestedLimit)+"&cursor="+url.QueryEscape(first.NextCursor))
			if second.HasMore {
				t.Fatal("second page hasMore = true, want false")
			}

			assertPages(t, "attachments", first.Attachments, second.Attachments)
			assertPages(t, "memo AI", first.MemoAI, second.MemoAI)
			assertPages(t, "ask conversations", first.AskConversations, second.AskConversations)
			assertPages(t, "ask messages", first.AskMessages, second.AskMessages)
		})
	}
}
