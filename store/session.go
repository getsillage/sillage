package store

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

type Session struct {
	ID               string
	AccountID        string
	RefreshTokenHash string
	UserAgent        string
	ClientIP         string
	ExpiresAt        int64
	CreatedAt        int64
	UpdatedAt        int64
	DeletedAt        sql.NullInt64
}

type CreateSession struct {
	AccountID    string
	RefreshToken string
	UserAgent    string
	ClientIP     string
	ExpiresAt    time.Time
}

func HashRefreshToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

func (s *Store) CreateSession(ctx context.Context, create *CreateSession) (*Session, error) {
	id, err := uuid.NewV7()
	if err != nil {
		return nil, fmt.Errorf("generate session id: %w", err)
	}
	now := time.Now().UTC().UnixMilli()
	session := &Session{
		ID:               id.String(),
		AccountID:        create.AccountID,
		RefreshTokenHash: HashRefreshToken(create.RefreshToken),
		UserAgent:        create.UserAgent,
		ClientIP:         create.ClientIP,
		ExpiresAt:        create.ExpiresAt.UTC().UnixMilli(),
		CreatedAt:        now,
		UpdatedAt:        now,
	}
	if _, err := s.driver.GetDB().ExecContext(ctx, `
INSERT INTO session (id, account_id, refresh_token_hash, user_agent, client_ip, expires_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		session.ID,
		session.AccountID,
		session.RefreshTokenHash,
		session.UserAgent,
		session.ClientIP,
		session.ExpiresAt,
		session.CreatedAt,
		session.UpdatedAt,
	); err != nil {
		return nil, fmt.Errorf("insert session: %w", err)
	}
	return session, nil
}

func (s *Store) GetSessionByRefreshToken(ctx context.Context, token string) (*Session, error) {
	row := s.driver.GetDB().QueryRowContext(ctx, `
SELECT id, account_id, refresh_token_hash, user_agent, client_ip, expires_at, created_at, updated_at, deleted_at
FROM session
WHERE refresh_token_hash = ? AND deleted_at IS NULL AND expires_at > ?`,
		HashRefreshToken(token),
		time.Now().UTC().UnixMilli(),
	)
	var session Session
	if err := row.Scan(
		&session.ID,
		&session.AccountID,
		&session.RefreshTokenHash,
		&session.UserAgent,
		&session.ClientIP,
		&session.ExpiresAt,
		&session.CreatedAt,
		&session.UpdatedAt,
		&session.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan session: %w", err)
	}
	return &session, nil
}

func (s *Store) DeleteSessionByRefreshToken(ctx context.Context, token string) error {
	now := time.Now().UTC().UnixMilli()
	if _, err := s.driver.GetDB().ExecContext(ctx, `
UPDATE session
SET deleted_at = ?, updated_at = ?
WHERE refresh_token_hash = ? AND deleted_at IS NULL`,
		now,
		now,
		HashRefreshToken(token),
	); err != nil {
		return fmt.Errorf("delete session: %w", err)
	}
	return nil
}

func (s *Store) RotateSession(ctx context.Context, oldToken string, create *CreateSession) (*Session, error) {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin rotate session: %w", err)
	}
	defer tx.Rollback()

	now := time.Now().UTC().UnixMilli()
	if _, err := tx.ExecContext(ctx, `
UPDATE session
SET deleted_at = ?, updated_at = ?
WHERE refresh_token_hash = ? AND deleted_at IS NULL`,
		now,
		now,
		HashRefreshToken(oldToken),
	); err != nil {
		return nil, fmt.Errorf("delete old session: %w", err)
	}

	id, err := uuid.NewV7()
	if err != nil {
		return nil, fmt.Errorf("generate session id: %w", err)
	}
	session := &Session{
		ID:               id.String(),
		AccountID:        create.AccountID,
		RefreshTokenHash: HashRefreshToken(create.RefreshToken),
		UserAgent:        create.UserAgent,
		ClientIP:         create.ClientIP,
		ExpiresAt:        create.ExpiresAt.UTC().UnixMilli(),
		CreatedAt:        now,
		UpdatedAt:        now,
	}
	if _, err := tx.ExecContext(ctx, `
INSERT INTO session (id, account_id, refresh_token_hash, user_agent, client_ip, expires_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		session.ID,
		session.AccountID,
		session.RefreshTokenHash,
		session.UserAgent,
		session.ClientIP,
		session.ExpiresAt,
		session.CreatedAt,
		session.UpdatedAt,
	); err != nil {
		return nil, fmt.Errorf("insert rotated session: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit rotate session: %w", err)
	}
	return session, nil
}
