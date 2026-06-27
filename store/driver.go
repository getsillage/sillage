package store

import (
	"context"
	"database/sql"
)

// Driver is the small database boundary used by the store package.
type Driver interface {
	GetDB() *sql.DB
	Close() error
	Ping(ctx context.Context) error
	IsInitialized(ctx context.Context) (bool, error)
}
