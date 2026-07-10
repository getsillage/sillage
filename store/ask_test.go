package store_test

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/getsillage/sillage/store"
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

	list, err := s.ListAskConversations(ctx, &store.ListAskConversationOptions{AccountID: account, Limit: 50})
	if err != nil {
		t.Fatalf("ListAskConversations() error = %v", err)
	}
	if len(list) != 1 {
		t.Fatalf("conversations = %d, want 1", len(list))
	}
}

func TestListAskConversationsSearchArchiveAndIsolation(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	createConversation := func(title string) *store.AskConversation {
		t.Helper()
		conversation, err := s.CreateAskConversation(ctx, account, title, "all")
		if err != nil {
			t.Fatalf("CreateAskConversation(%q) error = %v", title, err)
		}
		return conversation
	}
	createMessage := func(conversationID, content string) *store.AskMessage {
		t.Helper()
		message, err := s.CreateAskMessage(ctx, &store.AskMessage{
			ConversationID: conversationID,
			Role:           "assistant",
			Content:        content,
			SourceRefs:     "[]",
		})
		if err != nil {
			t.Fatalf("CreateAskMessage(%q) error = %v", content, err)
		}
		return message
	}
	list := func(query string, archived bool, limit int) []*store.AskConversation {
		t.Helper()
		conversations, err := s.ListAskConversations(ctx, &store.ListAskConversationOptions{
			AccountID: account,
			Query:     query,
			Archived:  archived,
			Limit:     limit,
		})
		if err != nil {
			t.Fatalf("ListAskConversations(%q, %t) error = %v", query, archived, err)
		}
		return conversations
	}

	titleMatch := createConversation("当前项目复盘")
	messageMatch := createConversation("其他会话")
	createMessage(messageMatch.ID, "专属回答 100%_完成")
	noise := createConversation("无关会话")
	createMessage(noise.ID, "专属回答 100XX完成")
	deletedMessageConversation := createConversation("删除消息测试")
	deletedMessage := createMessage(deletedMessageConversation.ID, "已删除线索")
	if _, err := s.GetDriver().GetDB().ExecContext(ctx,
		"UPDATE ask_messages SET deleted_at = ?, updated_at = ? WHERE id = ?",
		time.Now().UTC().UnixMilli(), time.Now().UTC().UnixMilli(), deletedMessage.ID); err != nil {
		t.Fatalf("soft-delete ask message: %v", err)
	}
	archived := createConversation("归档项目复盘")
	archived, err := s.SetAskConversationArchived(ctx, account, archived.ID, true)
	if err != nil {
		t.Fatalf("SetAskConversationArchived(true) error = %v", err)
	}
	repeatedArchive, err := s.SetAskConversationArchived(ctx, account, archived.ID, true)
	if err != nil {
		t.Fatalf("SetAskConversationArchived(repeated true) error = %v", err)
	}
	if repeatedArchive.ArchivedAt != archived.ArchivedAt || repeatedArchive.UpdatedAt != archived.UpdatedAt {
		t.Fatalf(
			"repeated archive timestamps = archived:%+v updated:%d, want archived:%+v updated:%d",
			repeatedArchive.ArchivedAt,
			repeatedArchive.UpdatedAt,
			archived.ArchivedAt,
			archived.UpdatedAt,
		)
	}

	const otherAccount = "01800000-0000-7000-8000-000000000099"
	now := time.Now().UTC().UnixMilli()
	if _, err := s.GetDriver().GetDB().ExecContext(ctx, `
INSERT INTO account (id, username, display_name, password_hash, password_algorithm, created_at, updated_at)
VALUES (?, 'other', 'Other', 'hash', 'test', ?, ?)`, otherAccount, now, now); err != nil {
		t.Fatalf("insert second account: %v", err)
	}
	if _, err := s.CreateAskConversation(ctx, otherAccount, "当前项目复盘", "all"); err != nil {
		t.Fatalf("CreateAskConversation(other account) error = %v", err)
	}

	if got := list("当前项目", false, 50); len(got) != 1 || got[0].ID != titleMatch.ID {
		t.Fatalf("title search = %#v, want only %s", got, titleMatch.ID)
	}
	if got := list("%_", false, 50); len(got) != 1 || got[0].ID != messageMatch.ID {
		t.Fatalf("escaped wildcard search = %#v, want only %s", got, messageMatch.ID)
	}
	if got := list("已删除线索", false, 50); len(got) != 0 {
		t.Fatalf("deleted message search = %#v, want empty", got)
	}
	if got := list("项目复盘", true, 1); len(got) != 1 || got[0].ID != archived.ID {
		t.Fatalf("archived search = %#v, want only %s", got, archived.ID)
	}
	if got := list("归档项目", false, 50); len(got) != 0 {
		t.Fatalf("default archived filter = %#v, want empty", got)
	}

	restored, err := s.SetAskConversationArchived(ctx, account, archived.ID, false)
	if err != nil {
		t.Fatalf("SetAskConversationArchived(false) error = %v", err)
	}
	if restored.ArchivedAt.Valid {
		t.Fatalf("restored archivedAt = %+v, want null", restored.ArchivedAt)
	}
	repeatedRestore, err := s.SetAskConversationArchived(ctx, account, archived.ID, false)
	if err != nil {
		t.Fatalf("SetAskConversationArchived(repeated false) error = %v", err)
	}
	if repeatedRestore.ArchivedAt.Valid || repeatedRestore.UpdatedAt != restored.UpdatedAt {
		t.Fatalf(
			"repeated restore timestamps = archived:%+v updated:%d, want archived:null updated:%d",
			repeatedRestore.ArchivedAt,
			repeatedRestore.UpdatedAt,
			restored.UpdatedAt,
		)
	}
	if got := list("归档项目", false, 50); len(got) != 1 || got[0].ID != archived.ID {
		t.Fatalf("restored search = %#v, want only %s", got, archived.ID)
	}

	if _, err := s.SetAskConversationArchived(ctx, account, "missing", true); !errors.Is(err, sql.ErrNoRows) {
		t.Fatalf("missing SetAskConversationArchived() error = %v, want sql.ErrNoRows", err)
	}
	if _, err := s.SetAskConversationArchived(ctx, otherAccount, titleMatch.ID, true); !errors.Is(err, sql.ErrNoRows) {
		t.Fatalf("cross-account SetAskConversationArchived() error = %v, want sql.ErrNoRows", err)
	}
	if _, err := s.GetDriver().GetDB().ExecContext(ctx,
		"UPDATE ask_conversations SET deleted_at = ? WHERE id = ?", now, titleMatch.ID); err != nil {
		t.Fatalf("soft-delete ask conversation: %v", err)
	}
	if _, err := s.SetAskConversationArchived(ctx, account, titleMatch.ID, true); !errors.Is(err, sql.ErrNoRows) {
		t.Fatalf("deleted SetAskConversationArchived() error = %v, want sql.ErrNoRows", err)
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
