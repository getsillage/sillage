package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

type AskConversation struct {
	ID            string
	CreatorID     sql.NullString
	Title         string
	Status        string
	ContextScope  string
	HeadMessageID sql.NullString
	PinnedAt      sql.NullInt64
	ArchivedAt    sql.NullInt64
	CreatedAt     int64
	UpdatedAt     int64
	DeletedAt     sql.NullInt64
}

type AskMessage struct {
	ID             string
	ConversationID string
	Role           string
	Content        string
	ParentID       sql.NullString
	ForkOfID       sql.NullString
	Status         string
	SourceRefs     string
	Model          string
	CreatedAt      int64
	UpdatedAt      int64
	DeletedAt      sql.NullInt64
}

func (s *Store) CreateAskConversation(ctx context.Context, accountID, title, contextScope string) (*AskConversation, error) {
	id, err := uuid.NewV7()
	if err != nil {
		return nil, fmt.Errorf("generate ask conversation id: %w", err)
	}
	now := time.Now().UTC().UnixMilli()
	if contextScope == "" {
		contextScope = "recent_30_days"
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO ask_conversations (id, creator_id, title, context_scope, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?)`, id.String(), accountID, title, contextScope, now, now); err != nil {
		return nil, fmt.Errorf("insert ask conversation: %w", err)
	}
	return s.GetAskConversation(ctx, accountID, id.String())
}

func (s *Store) GetAskConversation(ctx context.Context, accountID, id string) (*AskConversation, error) {
	return scanAskConversation(s.driver.GetDB().QueryRowContext(ctx, askConversationSelect()+`
WHERE id = ? AND creator_id = ? AND deleted_at IS NULL`, id, accountID))
}

func (s *Store) ListAskConversations(ctx context.Context, accountID string, limit int) ([]*AskConversation, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	rows, err := s.driver.GetDB().QueryContext(ctx, askConversationSelect()+`
WHERE creator_id = ? AND deleted_at IS NULL
ORDER BY pinned_at DESC, updated_at DESC, id DESC LIMIT ?`, accountID, limit)
	if err != nil {
		return nil, fmt.Errorf("list ask conversations: %w", err)
	}
	defer rows.Close()
	var conversations []*AskConversation
	for rows.Next() {
		conversation, err := scanAskConversation(rows)
		if err != nil {
			return nil, err
		}
		conversations = append(conversations, conversation)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate ask conversations: %w", err)
	}
	return conversations, nil
}

func (s *Store) CreateAskMessage(ctx context.Context, message *AskMessage) (*AskMessage, error) {
	id := message.ID
	if id == "" {
		generated, err := uuid.NewV7()
		if err != nil {
			return nil, fmt.Errorf("generate ask message id: %w", err)
		}
		id = generated.String()
	}
	now := time.Now().UTC().UnixMilli()
	if message.Status == "" {
		message.Status = "complete"
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO ask_messages (
  id, conversation_id, role, content, parent_id, fork_of_id, status, source_refs,
  model, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		id,
		message.ConversationID,
		message.Role,
		message.Content,
		nullableString(message.ParentID),
		nullableString(message.ForkOfID),
		message.Status,
		message.SourceRefs,
		message.Model,
		now,
		now,
	); err != nil {
		return nil, fmt.Errorf("insert ask message: %w", err)
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
UPDATE ask_conversations
SET head_message_id = ?, updated_at = ?, title = CASE WHEN title = '' THEN ? ELSE title END
WHERE id = ?`,
		id, now, titleFromContent(message.Content), message.ConversationID); err != nil {
		return nil, fmt.Errorf("update ask conversation head: %w", err)
	}
	return s.GetAskMessage(ctx, id)
}

func (s *Store) GetAskMessage(ctx context.Context, id string) (*AskMessage, error) {
	return scanAskMessage(s.driver.GetDB().QueryRowContext(ctx, askMessageSelect()+`
WHERE id = ? AND deleted_at IS NULL`, id))
}

func (s *Store) ListAskMessages(ctx context.Context, conversationID string) ([]*AskMessage, error) {
	rows, err := s.driver.GetDB().QueryContext(ctx, askMessageSelect()+`
WHERE conversation_id = ? AND deleted_at IS NULL
ORDER BY created_at ASC, id ASC`, conversationID)
	if err != nil {
		return nil, fmt.Errorf("list ask messages: %w", err)
	}
	defer rows.Close()
	var messages []*AskMessage
	for rows.Next() {
		message, err := scanAskMessage(rows)
		if err != nil {
			return nil, err
		}
		messages = append(messages, message)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate ask messages: %w", err)
	}
	return messages, nil
}

type ListAskSyncOptions struct {
	AccountID      string
	Limit          int
	UpdatedAfter   int64
	UpdatedAfterID string
}

func (s *Store) ListAskConversationsForSync(ctx context.Context, opts *ListAskSyncOptions) ([]*AskConversation, error) {
	return s.listAskConversationsByUpdated(ctx, opts)
}

func (s *Store) ListAskMessagesForSync(ctx context.Context, opts *ListAskSyncOptions) ([]*AskMessage, error) {
	limit := opts.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query := askMessageSelect() + `
JOIN ask_conversations ON ask_conversations.id = ask_messages.conversation_id
WHERE ask_conversations.creator_id = ?`
	args := []any{opts.AccountID}
	if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
		query += " AND (ask_messages.updated_at > ? OR (ask_messages.updated_at = ? AND ask_messages.id > ?))"
		args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
	}
	query += " ORDER BY ask_messages.updated_at ASC, ask_messages.id ASC LIMIT ?"
	args = append(args, limit)
	rows, err := s.driver.GetDB().QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list ask messages sync: %w", err)
	}
	defer rows.Close()
	var messages []*AskMessage
	for rows.Next() {
		message, err := scanAskMessage(rows)
		if err != nil {
			return nil, err
		}
		messages = append(messages, message)
	}
	return messages, rows.Err()
}

func (s *Store) listAskConversationsByUpdated(ctx context.Context, opts *ListAskSyncOptions) ([]*AskConversation, error) {
	limit := opts.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query := askConversationSelect() + " WHERE creator_id = ?"
	args := []any{opts.AccountID}
	if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
		query += " AND (updated_at > ? OR (updated_at = ? AND id > ?))"
		args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
	}
	query += " ORDER BY updated_at ASC, id ASC LIMIT ?"
	args = append(args, limit)
	rows, err := s.driver.GetDB().QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list ask conversations sync: %w", err)
	}
	defer rows.Close()
	var conversations []*AskConversation
	for rows.Next() {
		conversation, err := scanAskConversation(rows)
		if err != nil {
			return nil, err
		}
		conversations = append(conversations, conversation)
	}
	return conversations, rows.Err()
}

func askConversationSelect() string {
	return `
SELECT id, creator_id, title, status, context_scope, head_message_id, pinned_at,
  archived_at, created_at, updated_at, deleted_at
FROM ask_conversations `
}

func askMessageSelect() string {
	return `
SELECT ask_messages.id, ask_messages.conversation_id, ask_messages.role,
  ask_messages.content, ask_messages.parent_id, ask_messages.fork_of_id,
  ask_messages.status, ask_messages.source_refs, ask_messages.model,
  ask_messages.created_at, ask_messages.updated_at, ask_messages.deleted_at
FROM ask_messages `
}

func scanAskConversation(row interface {
	Scan(dest ...any) error
}) (*AskConversation, error) {
	var conversation AskConversation
	if err := row.Scan(
		&conversation.ID,
		&conversation.CreatorID,
		&conversation.Title,
		&conversation.Status,
		&conversation.ContextScope,
		&conversation.HeadMessageID,
		&conversation.PinnedAt,
		&conversation.ArchivedAt,
		&conversation.CreatedAt,
		&conversation.UpdatedAt,
		&conversation.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan ask conversation: %w", err)
	}
	return &conversation, nil
}

func scanAskMessage(row interface {
	Scan(dest ...any) error
}) (*AskMessage, error) {
	var message AskMessage
	if err := row.Scan(
		&message.ID,
		&message.ConversationID,
		&message.Role,
		&message.Content,
		&message.ParentID,
		&message.ForkOfID,
		&message.Status,
		&message.SourceRefs,
		&message.Model,
		&message.CreatedAt,
		&message.UpdatedAt,
		&message.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan ask message: %w", err)
	}
	return &message, nil
}

func titleFromContent(content string) string {
	if len(content) <= 24 {
		return content
	}
	return content[:24]
}
