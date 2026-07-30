package store

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/getsillage/sillage/internal/profile"
)

// Store aggregates database-backed services.
type Store struct {
	driver  Driver
	profile *profile.Profile
	tx      *sql.Tx
}

// MaxSyncPageLimit caps each resource stream returned by one sync pull.
const MaxSyncPageLimit = 200

func New(driver Driver, profile *profile.Profile) *Store {
	return &Store{driver: driver, profile: profile}
}

func (s *Store) GetDriver() Driver {
	return s.driver
}

func (s *Store) GetDataDir() string {
	return s.profile.Data
}

func (s *Store) Ready(ctx context.Context) error {
	if err := s.driver.Ping(ctx); err != nil {
		return err
	}
	_, err := s.GetSchemaVersion(ctx)
	return err
}

func (s *Store) Close() error {
	return s.driver.Close()
}

type dbRunner interface {
	ExecContext(context.Context, string, ...any) (sql.Result, error)
	QueryContext(context.Context, string, ...any) (*sql.Rows, error)
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

func (s *Store) db() dbRunner {
	if s.tx != nil {
		return s.tx
	}
	return s.driver.GetDB()
}

// WithTransaction runs fn against a transaction-scoped Store. Memo and sync
// mutation methods use the same transaction so an idempotency result can never
// commit separately from the resource write it describes.
func (s *Store) WithTransaction(ctx context.Context, fn func(*Store) error) error {
	if s.tx != nil {
		return fmt.Errorf("nested store transactions are not supported")
	}
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin store transaction: %w", err)
	}
	defer tx.Rollback()
	txStore := &Store{driver: s.driver, profile: s.profile, tx: tx}
	if err := fn(txStore); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit store transaction: %w", err)
	}
	return nil
}

func isPageLookahead(limit, pageSize, maxPageSize int) bool {
	return pageSize > 0 && pageSize <= maxPageSize && limit == pageSize+1
}
