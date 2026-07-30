package main

import (
	"bytes"
	"context"
	"database/sql"
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"github.com/spf13/viper"

	"github.com/getsillage/sillage/internal/instancelock"
	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/server/auth"
	"github.com/getsillage/sillage/store"
	"github.com/getsillage/sillage/store/db"
)

func TestResetPasswordCommandUpdatesPasswordAndRevokesSessions(t *testing.T) {
	viper.Reset()
	t.Cleanup(viper.Reset)
	p := seedRecoveryAccount(t, "old-password")
	passwordFile := writePasswordFile(t, "new-password\n", 0o600)
	t.Setenv("SILLAGE_DSN", "")
	t.Setenv("SILLAGE_DSN_FILE", "")

	cmd := newRootCommand()
	var output bytes.Buffer
	cmd.SetOut(&output)
	cmd.SetErr(&output)
	cmd.SetArgs([]string{
		"--data", p.Data,
		"admin", "reset-password",
		"--username", "Felix",
		"--password-file", passwordFile,
	})
	if err := cmd.Execute(); err != nil {
		t.Fatalf("reset-password command error = %v; output=%s", err, output.String())
	}
	if !strings.Contains(output.String(), "Password reset for felix") {
		t.Fatalf("reset-password output = %q", output.String())
	}

	s := openRecoveryStore(t, p)
	defer s.Close()
	account, err := s.GetAccountByUsername(context.Background(), "felix")
	if err != nil {
		t.Fatalf("GetAccountByUsername() error = %v", err)
	}
	if ok, err := auth.VerifyPassword(account.PasswordHash, "new-password"); err != nil || !ok {
		t.Fatalf("new password verify ok=%v error=%v", ok, err)
	}
	if ok, err := auth.VerifyPassword(account.PasswordHash, "old-password"); err != nil || ok {
		t.Fatalf("old password verify ok=%v error=%v", ok, err)
	}
	if _, err := s.GetSessionByRefreshToken(context.Background(), "refresh-token"); !errors.Is(err, sql.ErrNoRows) {
		t.Fatalf("revoked session lookup error = %v, want sql.ErrNoRows", err)
	}
}

func TestResetPasswordRejectsRunningInstance(t *testing.T) {
	p := seedRecoveryAccount(t, "old-password")
	passwordFile := writePasswordFile(t, "new-password", 0o600)
	databasePath, err := db.DatabaseFilePath(p)
	if err != nil {
		t.Fatalf("DatabaseFilePath() error = %v", err)
	}
	lock, err := instancelock.Acquire(p.Data, databasePath)
	if err != nil {
		t.Fatalf("Acquire() error = %v", err)
	}
	defer lock.Release()

	_, err = resetPassword(context.Background(), p, "felix", passwordFile)
	if err == nil || !strings.Contains(err.Error(), "stop Sillage") {
		t.Fatalf("resetPassword() error = %v, want running-instance rejection", err)
	}

	s := openRecoveryStore(t, p)
	defer s.Close()
	account, err := s.GetAccountByUsername(context.Background(), "felix")
	if err != nil {
		t.Fatalf("GetAccountByUsername() error = %v", err)
	}
	if ok, err := auth.VerifyPassword(account.PasswordHash, "old-password"); err != nil || !ok {
		t.Fatalf("old password changed after rejected reset: ok=%v error=%v", ok, err)
	}
}

func TestReadPasswordFileSecurity(t *testing.T) {
	if runtime.GOOS != "windows" {
		path := writePasswordFile(t, "long-enough", 0o644)
		if _, err := readPasswordFile(path); err == nil || !strings.Contains(err.Error(), "0600") {
			t.Fatalf("readPasswordFile(broad permissions) error = %v", err)
		}
	}

	target := writePasswordFile(t, "long-enough", 0o600)
	link := filepath.Join(t.TempDir(), "password-link")
	if err := os.Symlink(target, link); err != nil {
		t.Fatalf("Symlink() error = %v", err)
	}
	if _, err := readPasswordFile(link); err == nil || !strings.Contains(err.Error(), "symbolic link") {
		t.Fatalf("readPasswordFile(symlink) error = %v", err)
	}

	multiline := writePasswordFile(t, "long-enough\nsecond-line", 0o600)
	if _, err := readPasswordFile(multiline); err == nil || !strings.Contains(err.Error(), "one line") {
		t.Fatalf("readPasswordFile(multiline) error = %v", err)
	}
}

func TestResetPasswordRefusesToInitializeMissingDatabase(t *testing.T) {
	p := &profile.Profile{Data: t.TempDir()}
	passwordFile := writePasswordFile(t, "new-password", 0o600)
	_, err := resetPassword(context.Background(), p, "felix", passwordFile)
	if err == nil || !strings.Contains(err.Error(), "refusing to initialize") {
		t.Fatalf("resetPassword() error = %v, want missing-database rejection", err)
	}
	if _, statErr := os.Stat(p.DSN); !errors.Is(statErr, os.ErrNotExist) {
		t.Fatalf("database was unexpectedly created: %v", statErr)
	}
}

func seedRecoveryAccount(t *testing.T, password string) *profile.Profile {
	t.Helper()
	p := &profile.Profile{Data: t.TempDir()}
	if err := p.Validate(); err != nil {
		t.Fatalf("profile.Validate() error = %v", err)
	}
	s := openRecoveryStore(t, p)
	hash, err := auth.HashPassword(password)
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}
	account, err := s.CreateAccount(context.Background(), &store.CreateAccount{
		Username:          "felix",
		DisplayName:       "Felix",
		PasswordHash:      hash,
		PasswordAlgorithm: auth.PasswordAlgorithmName,
	})
	if err != nil {
		t.Fatalf("CreateAccount() error = %v", err)
	}
	if _, err := s.CreateSession(context.Background(), &store.CreateSession{
		AccountID:    account.ID,
		RefreshToken: "refresh-token",
		ExpiresAt:    time.Now().Add(time.Hour),
	}); err != nil {
		t.Fatalf("CreateSession() error = %v", err)
	}
	if err := s.Close(); err != nil {
		t.Fatalf("Close() error = %v", err)
	}
	return p
}

func openRecoveryStore(t *testing.T, p *profile.Profile) *store.Store {
	t.Helper()
	driver, err := db.NewDBDriver(p)
	if err != nil {
		t.Fatalf("NewDBDriver() error = %v", err)
	}
	s := store.New(driver, p)
	if err := s.Migrate(context.Background()); err != nil {
		_ = s.Close()
		t.Fatalf("Migrate() error = %v", err)
	}
	return s
}

func writePasswordFile(t *testing.T, value string, mode os.FileMode) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "new-password")
	if err := os.WriteFile(path, []byte(value), mode); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}
	if err := os.Chmod(path, mode); err != nil {
		t.Fatalf("Chmod() error = %v", err)
	}
	return path
}
