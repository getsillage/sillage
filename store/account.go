package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

type Account struct {
	ID                string
	Username          string
	DisplayName       string
	PasswordHash      string
	PasswordAlgorithm string
	CreatedAt         int64
	UpdatedAt         int64
	DeletedAt         sql.NullInt64
}

type CreateAccount struct {
	Username          string
	DisplayName       string
	PasswordHash      string
	PasswordAlgorithm string
}

var ErrAccountExists = errors.New("account already exists")

func (s *Store) HasAccount(ctx context.Context) (bool, error) {
	var count int
	if err := s.driver.GetDB().QueryRowContext(ctx, "SELECT COUNT(1) FROM account WHERE deleted_at IS NULL").Scan(&count); err != nil {
		return false, fmt.Errorf("count accounts: %w", err)
	}
	return count > 0, nil
}

func (s *Store) CreateAccount(ctx context.Context, create *CreateAccount) (*Account, error) {
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin create account: %w", err)
	}
	defer tx.Rollback()

	var count int
	if err := tx.QueryRowContext(ctx, "SELECT COUNT(1) FROM account WHERE deleted_at IS NULL").Scan(&count); err != nil {
		return nil, fmt.Errorf("count accounts: %w", err)
	}
	if count > 0 {
		return nil, ErrAccountExists
	}

	id, err := uuid.NewV7()
	if err != nil {
		return nil, fmt.Errorf("generate account id: %w", err)
	}
	now := time.Now().UTC().UnixMilli()
	account := &Account{
		ID:                id.String(),
		Username:          create.Username,
		DisplayName:       create.DisplayName,
		PasswordHash:      create.PasswordHash,
		PasswordAlgorithm: create.PasswordAlgorithm,
		CreatedAt:         now,
		UpdatedAt:         now,
	}
	if _, err := tx.ExecContext(ctx, `
INSERT INTO account (id, username, display_name, password_hash, password_algorithm, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?)`,
		account.ID,
		account.Username,
		account.DisplayName,
		account.PasswordHash,
		account.PasswordAlgorithm,
		account.CreatedAt,
		account.UpdatedAt,
	); err != nil {
		return nil, fmt.Errorf("insert account: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit create account: %w", err)
	}
	return account, nil
}

func (s *Store) GetAccountByUsername(ctx context.Context, username string) (*Account, error) {
	row := s.driver.GetDB().QueryRowContext(ctx, `
SELECT id, username, display_name, password_hash, password_algorithm, created_at, updated_at, deleted_at
FROM account
WHERE username = ? AND deleted_at IS NULL`, username)
	return scanAccount(row)
}

func (s *Store) GetAccountByID(ctx context.Context, id string) (*Account, error) {
	row := s.driver.GetDB().QueryRowContext(ctx, `
SELECT id, username, display_name, password_hash, password_algorithm, created_at, updated_at, deleted_at
FROM account
WHERE id = ? AND deleted_at IS NULL`, id)
	return scanAccount(row)
}

func scanAccount(row interface {
	Scan(dest ...any) error
}) (*Account, error) {
	var account Account
	if err := row.Scan(
		&account.ID,
		&account.Username,
		&account.DisplayName,
		&account.PasswordHash,
		&account.PasswordAlgorithm,
		&account.CreatedAt,
		&account.UpdatedAt,
		&account.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan account: %w", err)
	}
	return &account, nil
}
