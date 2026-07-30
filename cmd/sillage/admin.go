package main

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"io"
	"os"
	"runtime"
	"strings"

	"github.com/spf13/cobra"

	"github.com/getsillage/sillage/internal/instancelock"
	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/server/auth"
	"github.com/getsillage/sillage/store"
	"github.com/getsillage/sillage/store/db"
)

func newAdminCommand() *cobra.Command {
	admin := &cobra.Command{
		Use:   "admin",
		Short: "Offline administrative recovery commands",
	}
	admin.AddCommand(newResetPasswordCommand())
	return admin
}

func newResetPasswordCommand() *cobra.Command {
	var username string
	var passwordFile string
	cmd := &cobra.Command{
		Use:   "reset-password",
		Short: "Reset the account password while the Sillage instance is stopped",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := expandFileEnv("SILLAGE_DSN"); err != nil {
				return err
			}
			account, err := resetPassword(cmd.Context(), configuredStorageProfile(), username, passwordFile)
			if err != nil {
				return err
			}
			_, err = fmt.Fprintf(cmd.OutOrStdout(), "Password reset for %s; all refresh sessions were revoked.\n", account.Username)
			return err
		},
	}
	cmd.Flags().StringVar(&username, "username", "", "account username")
	cmd.Flags().StringVar(&passwordFile, "password-file", "", "owner-readable file containing the new password")
	_ = cmd.MarkFlagRequired("username")
	_ = cmd.MarkFlagRequired("password-file")
	return cmd
}

func resetPassword(ctx context.Context, p *profile.Profile, username, passwordFile string) (account *store.Account, returnErr error) {
	username = strings.ToLower(strings.TrimSpace(username))
	if username == "" {
		return nil, fmt.Errorf("username is required")
	}
	password, err := readPasswordFile(passwordFile)
	if err != nil {
		return nil, err
	}
	passwordHash, err := auth.HashPassword(password)
	if err != nil {
		return nil, fmt.Errorf("validate new password: %w", err)
	}
	if err := p.Validate(); err != nil {
		return nil, fmt.Errorf("validate profile: %w", err)
	}
	databasePath, err := db.DatabaseFilePath(p)
	if err != nil {
		return nil, fmt.Errorf("resolve database path: %w", err)
	}
	databaseInfo, err := os.Stat(databasePath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil, fmt.Errorf("database %s does not exist; refusing to initialize a new instance during password recovery", databasePath)
		}
		return nil, fmt.Errorf("inspect database: %w", err)
	}
	if !databaseInfo.Mode().IsRegular() {
		return nil, fmt.Errorf("database %s is not a regular file", databasePath)
	}
	instanceLock, err := instancelock.Acquire(p.Data, databasePath)
	if err != nil {
		if errors.Is(err, instancelock.ErrInUse) {
			return nil, fmt.Errorf("data directory %s is in use; stop Sillage before resetting the password", p.Data)
		}
		return nil, err
	}
	defer func() {
		if err := instanceLock.Release(); returnErr == nil && err != nil {
			returnErr = err
		}
	}()

	driver, err := db.NewDBDriver(p)
	if err != nil {
		return nil, fmt.Errorf("create database driver: %w", err)
	}
	s := store.New(driver, p)
	defer func() {
		if err := s.Close(); returnErr == nil && err != nil {
			returnErr = fmt.Errorf("close database: %w", err)
		}
	}()
	if err := s.Migrate(ctx); err != nil {
		return nil, fmt.Errorf("migrate database: %w", err)
	}
	account, err = s.GetAccountByUsername(ctx, username)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, fmt.Errorf("active account %q was not found", username)
		}
		return nil, fmt.Errorf("read account: %w", err)
	}
	if err := s.UpdateAccountPasswordAndRevokeSessions(ctx, account.ID, passwordHash, auth.PasswordAlgorithmName); err != nil {
		return nil, fmt.Errorf("reset password: %w", err)
	}
	return s.GetAccountByID(ctx, account.ID)
}

func readPasswordFile(path string) (string, error) {
	if strings.TrimSpace(path) == "" {
		return "", fmt.Errorf("password file is required")
	}
	info, err := os.Lstat(path)
	if err != nil {
		return "", fmt.Errorf("inspect password file: %w", err)
	}
	if !info.Mode().IsRegular() {
		return "", fmt.Errorf("password file must be a regular file and not a symbolic link")
	}
	file, err := os.Open(path)
	if err != nil {
		return "", fmt.Errorf("open password file: %w", err)
	}
	defer file.Close()
	openedInfo, err := file.Stat()
	if err != nil {
		return "", fmt.Errorf("stat opened password file: %w", err)
	}
	if !os.SameFile(info, openedInfo) {
		return "", fmt.Errorf("password file changed while it was being opened")
	}
	if runtime.GOOS != "windows" && openedInfo.Mode().Perm()&0o077 != 0 {
		return "", fmt.Errorf("password file permissions %04o are too broad; use mode 0600", openedInfo.Mode().Perm())
	}
	payload, err := io.ReadAll(io.LimitReader(file, auth.MaxPasswordBytes+3))
	if err != nil {
		return "", fmt.Errorf("read password file: %w", err)
	}
	if len(payload) > auth.MaxPasswordBytes+2 {
		return "", fmt.Errorf("password file is too large")
	}
	password := strings.TrimSuffix(string(payload), "\n")
	password = strings.TrimSuffix(password, "\r")
	if strings.ContainsAny(password, "\r\n") {
		return "", fmt.Errorf("password file must contain exactly one line")
	}
	return password, nil
}
