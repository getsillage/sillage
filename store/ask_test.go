package store_test

import (
	"context"
	"testing"

	"github.com/miofelix/sillage/store"
)

func TestAskConversationAndMessageLifecycle(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	conv, err := s.CreateAskConversation(ctx, account, "", "all")
	if err != nil {
		t.Fatalf("CreateAskConversation() error = %v", err)
	}
	if conv.ContextScope != "all" {
		t.Fatalf("scope = %q, want all", conv.ContextScope)
	}

	user, err := s.CreateAskMessage(ctx, &store.AskMessage{
		ConversationID: conv.ID,
		Role:           "user",
		Content:        "我最近怎么样？",
		SourceRefs:     "[]",
	})
	if err != nil {
		t.Fatalf("CreateAskMessage(user) error = %v", err)
	}
	assistant, err := s.CreateAskMessage(ctx, &store.AskMessage{
		ConversationID: conv.ID,
		Role:           "assistant",
		Content:        "你最近睡得更好。",
		SourceRefs:     "[]",
		Model:          "gpt-test",
	})
	if err != nil {
		t.Fatalf("CreateAskMessage(assistant) error = %v", err)
	}

	messages, err := s.ListAskMessages(ctx, conv.ID)
	if err != nil {
		t.Fatalf("ListAskMessages() error = %v", err)
	}
	if len(messages) != 2 || messages[0].ID != user.ID || messages[1].ID != assistant.ID {
		t.Fatalf("messages = %d, want [user, assistant] in order", len(messages))
	}

	// The conversation head advances to the latest message and the title is
	// seeded from the first message content.
	reloaded, err := s.GetAskConversation(ctx, account, conv.ID)
	if err != nil {
		t.Fatalf("GetAskConversation() error = %v", err)
	}
	if !reloaded.HeadMessageID.Valid || reloaded.HeadMessageID.String != assistant.ID {
		t.Fatalf("head = %+v, want %s", reloaded.HeadMessageID, assistant.ID)
	}
	if reloaded.Title == "" {
		t.Fatalf("title was not seeded from first message")
	}

	list, err := s.ListAskConversations(ctx, account, 50)
	if err != nil {
		t.Fatalf("ListAskConversations() error = %v", err)
	}
	if len(list) != 1 {
		t.Fatalf("conversations = %d, want 1", len(list))
	}
}

func TestSyncMutationIdempotencyRoundTrip(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	if _, ok, err := s.GetSyncMutation(ctx, account, "mut-1"); err != nil || ok {
		t.Fatalf("GetSyncMutation before put = ok:%v err:%v, want not found", ok, err)
	}

	if err := s.PutSyncMutation(ctx, &store.SyncMutation{
		AccountID:    account,
		MutationID:   "mut-1",
		ResourceType: "memo",
		ResourceID:   "memo-1",
		Result:       `{"status":"applied"}`,
	}); err != nil {
		t.Fatalf("PutSyncMutation() error = %v", err)
	}

	got, ok, err := s.GetSyncMutation(ctx, account, "mut-1")
	if err != nil || !ok {
		t.Fatalf("GetSyncMutation after put = ok:%v err:%v", ok, err)
	}
	if got.Result != `{"status":"applied"}` || got.ResourceID != "memo-1" {
		t.Fatalf("stored mutation = %+v", got)
	}
}
