package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

type Attachment struct {
	ID             string
	UID            string
	CreatorID      sql.NullString
	MemoID         sql.NullString
	StorageType    string
	StorageRef     string
	Filename       string
	ContentType    string
	Size           int64
	SHA256         sql.NullString
	Width          sql.NullInt64
	Height         sql.NullInt64
	Status         string
	MutationID     sql.NullString
	IdempotencyKey sql.NullString
	CreatedAt      int64
	UpdatedAt      int64
	DeletedAt      sql.NullInt64
}

type CreateAttachment struct {
	CreatorID      string
	MemoID         string
	StorageRef     string
	Filename       string
	ContentType    string
	Size           int64
	SHA256         string
	MutationID     string
	IdempotencyKey string
}

func (s *Store) CreateAttachment(ctx context.Context, create *CreateAttachment) (*Attachment, error) {
	if create.MutationID != "" {
		if attachment, ok, err := s.GetAttachmentByMutationID(ctx, create.CreatorID, create.MutationID); err != nil {
			return nil, err
		} else if ok {
			return attachment, nil
		}
	}
	if create.IdempotencyKey != "" {
		if attachment, ok, err := s.GetAttachmentByIdempotencyKey(ctx, create.CreatorID, create.IdempotencyKey); err != nil {
			return nil, err
		} else if ok {
			return attachment, nil
		}
	}

	id, err := uuid.NewV7()
	if err != nil {
		return nil, fmt.Errorf("generate attachment id: %w", err)
	}
	uid, err := uuid.NewV7()
	if err != nil {
		return nil, fmt.Errorf("generate attachment uid: %w", err)
	}
	now := time.Now().UTC().UnixMilli()
	attachment := &Attachment{
		ID:             id.String(),
		UID:            uid.String(),
		CreatorID:      sql.NullString{String: create.CreatorID, Valid: create.CreatorID != ""},
		MemoID:         sql.NullString{String: create.MemoID, Valid: create.MemoID != ""},
		StorageType:    "local",
		StorageRef:     create.StorageRef,
		Filename:       create.Filename,
		ContentType:    create.ContentType,
		Size:           create.Size,
		SHA256:         sql.NullString{String: create.SHA256, Valid: create.SHA256 != ""},
		Status:         "stored",
		MutationID:     sql.NullString{String: create.MutationID, Valid: create.MutationID != ""},
		IdempotencyKey: sql.NullString{String: create.IdempotencyKey, Valid: create.IdempotencyKey != ""},
		CreatedAt:      now,
		UpdatedAt:      now,
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO attachments (
  id, uid, creator_id, memo_id, storage_type, storage_ref, filename, content_type,
  size, sha256, status, mutation_id, idempotency_key, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		attachment.ID,
		attachment.UID,
		nullableString(attachment.CreatorID),
		nullableString(attachment.MemoID),
		attachment.StorageType,
		attachment.StorageRef,
		attachment.Filename,
		attachment.ContentType,
		attachment.Size,
		nullableString(attachment.SHA256),
		attachment.Status,
		nullableString(attachment.MutationID),
		nullableString(attachment.IdempotencyKey),
		attachment.CreatedAt,
		attachment.UpdatedAt,
	); err != nil {
		return nil, fmt.Errorf("insert attachment: %w", err)
	}
	return attachment, nil
}

func (s *Store) GetAttachmentByUID(ctx context.Context, accountID, uid string, includeDeleted bool) (*Attachment, error) {
	query := attachmentSelectBase() + " WHERE uid = ? AND creator_id = ?"
	args := []any{uid, accountID}
	if !includeDeleted {
		query += " AND deleted_at IS NULL"
	}
	return scanAttachment(s.driver.GetDB().QueryRowContext(ctx, query, args...))
}

func (s *Store) GetAttachmentByMutationID(ctx context.Context, accountID, mutationID string) (*Attachment, bool, error) {
	attachment, err := scanAttachment(s.driver.GetDB().QueryRowContext(ctx, attachmentSelectBase()+`
WHERE creator_id = ? AND mutation_id = ?`, accountID, mutationID))
	if err == sql.ErrNoRows {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}
	return attachment, true, nil
}

func (s *Store) GetAttachmentByIdempotencyKey(ctx context.Context, accountID, key string) (*Attachment, bool, error) {
	attachment, err := scanAttachment(s.driver.GetDB().QueryRowContext(ctx, attachmentSelectBase()+`
WHERE creator_id = ? AND idempotency_key = ?`, accountID, key))
	if err == sql.ErrNoRows {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, err
	}
	return attachment, true, nil
}

type ListAttachmentOptions struct {
	AccountID      string
	Limit          int
	IncludeDeleted bool
	UpdatedAfter   int64
	UpdatedAfterID string
}

func (s *Store) ListAttachments(ctx context.Context, opts *ListAttachmentOptions) ([]*Attachment, error) {
	limit := opts.Limit
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query := attachmentSelectBase() + " WHERE creator_id = ?"
	args := []any{opts.AccountID}
	if !opts.IncludeDeleted {
		query += " AND deleted_at IS NULL"
	}
	if opts.UpdatedAfter > 0 || opts.UpdatedAfterID != "" {
		query += " AND (updated_at > ? OR (updated_at = ? AND id > ?))"
		args = append(args, opts.UpdatedAfter, opts.UpdatedAfter, opts.UpdatedAfterID)
	}
	query += " ORDER BY updated_at ASC, id ASC LIMIT ?"
	args = append(args, limit)

	rows, err := s.driver.GetDB().QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list attachments: %w", err)
	}
	defer rows.Close()

	var attachments []*Attachment
	for rows.Next() {
		attachment, err := scanAttachment(rows)
		if err != nil {
			return nil, err
		}
		attachments = append(attachments, attachment)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate attachments: %w", err)
	}
	return attachments, nil
}

func (s *Store) DeleteAttachment(ctx context.Context, accountID, uid string) (*Attachment, error) {
	now := time.Now().UTC().UnixMilli()
	result, err := s.driver.GetDB().ExecContext(ctx, `
UPDATE attachments
SET deleted_at = ?, updated_at = ?
WHERE uid = ? AND creator_id = ? AND deleted_at IS NULL`, now, now, uid, accountID)
	if err != nil {
		return nil, fmt.Errorf("delete attachment: %w", err)
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return nil, fmt.Errorf("read deleted attachment rows: %w", err)
	}
	if affected == 0 {
		return nil, sql.ErrNoRows
	}
	return s.GetAttachmentByUID(ctx, accountID, uid, true)
}

func attachmentSelectBase() string {
	return `
SELECT id, uid, creator_id, memo_id, storage_type, storage_ref, filename, content_type,
  size, sha256, width, height, status, mutation_id, idempotency_key, created_at, updated_at, deleted_at
FROM attachments`
}

func scanAttachment(row interface {
	Scan(dest ...any) error
}) (*Attachment, error) {
	var attachment Attachment
	if err := row.Scan(
		&attachment.ID,
		&attachment.UID,
		&attachment.CreatorID,
		&attachment.MemoID,
		&attachment.StorageType,
		&attachment.StorageRef,
		&attachment.Filename,
		&attachment.ContentType,
		&attachment.Size,
		&attachment.SHA256,
		&attachment.Width,
		&attachment.Height,
		&attachment.Status,
		&attachment.MutationID,
		&attachment.IdempotencyKey,
		&attachment.CreatedAt,
		&attachment.UpdatedAt,
		&attachment.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan attachment: %w", err)
	}
	return &attachment, nil
}

func nullableString(value sql.NullString) any {
	if !value.Valid {
		return nil
	}
	return value.String
}
