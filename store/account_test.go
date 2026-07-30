package store_test

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/getsillage/sillage/server/auth"
	"github.com/getsillage/sillage/store"
)

func TestUpdateAccountPasswordAndRevokeSessions(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)
	for _, token := range []string{"first", "second"} {
		if _, err := s.CreateSession(ctx, &store.CreateSession{
			AccountID: accountID, RefreshToken: token, ExpiresAt: time.Now().Add(time.Hour),
		}); err != nil {
			t.Fatalf("CreateSession(%s) error = %v", token, err)
		}
	}
	hash, err := auth.HashPassword("replacement-password")
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}
	if err := s.UpdateAccountPasswordAndRevokeSessions(ctx, accountID, hash, auth.PasswordAlgorithmName); err != nil {
		t.Fatalf("UpdateAccountPasswordAndRevokeSessions() error = %v", err)
	}
	account, err := s.GetAccountByID(ctx, accountID)
	if err != nil {
		t.Fatalf("GetAccountByID() error = %v", err)
	}
	if ok, err := auth.VerifyPassword(account.PasswordHash, "replacement-password"); err != nil || !ok {
		t.Fatalf("replacement password verify ok=%v error=%v", ok, err)
	}
	for _, token := range []string{"first", "second"} {
		if _, err := s.GetSessionByRefreshToken(ctx, token); !errors.Is(err, sql.ErrNoRows) {
			t.Fatalf("GetSessionByRefreshToken(%s) error = %v, want sql.ErrNoRows", token, err)
		}
	}
}

func TestUpdateAccountPasswordRollsBackWhenSessionRevocationFails(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	accountID := newTestAccount(t, s)
	before, err := s.GetAccountByID(ctx, accountID)
	if err != nil {
		t.Fatalf("GetAccountByID(before) error = %v", err)
	}
	if _, err := s.CreateSession(ctx, &store.CreateSession{
		AccountID: accountID, RefreshToken: "still-active", ExpiresAt: time.Now().Add(time.Hour),
	}); err != nil {
		t.Fatalf("CreateSession() error = %v", err)
	}
	if _, err := s.GetDriver().GetDB().ExecContext(ctx, `
CREATE TRIGGER fail_session_revoke
BEFORE UPDATE ON session
BEGIN
  SELECT RAISE(ABORT, 'forced revoke failure');
END`); err != nil {
		t.Fatalf("create failure trigger: %v", err)
	}
	replacement, err := auth.HashPassword("replacement-password")
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}
	if err := s.UpdateAccountPasswordAndRevokeSessions(ctx, accountID, replacement, auth.PasswordAlgorithmName); err == nil {
		t.Fatal("UpdateAccountPasswordAndRevokeSessions() error = nil, want forced failure")
	}
	after, err := s.GetAccountByID(ctx, accountID)
	if err != nil {
		t.Fatalf("GetAccountByID(after) error = %v", err)
	}
	if after.PasswordHash != before.PasswordHash {
		t.Fatal("password hash changed despite session revocation failure")
	}
	if _, err := s.GetSessionByRefreshToken(ctx, "still-active"); err != nil {
		t.Fatalf("session was revoked despite transaction rollback: %v", err)
	}
}
