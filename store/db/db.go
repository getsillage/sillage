package db

import (
	"fmt"

	"github.com/getsillage/sillage/internal/profile"
	"github.com/getsillage/sillage/store"
	"github.com/getsillage/sillage/store/db/sqlite"
)

func NewDBDriver(p *profile.Profile) (store.Driver, error) {
	switch p.Driver {
	case profile.DriverSQLite:
		return sqlite.New(p)
	default:
		return nil, fmt.Errorf("unsupported database driver %q", p.Driver)
	}
}

// DatabaseFilePath returns the filesystem path behind the configured driver,
// excluding connection query parameters used by the SQLite DSN.
func DatabaseFilePath(p *profile.Profile) (string, error) {
	switch p.Driver {
	case profile.DriverSQLite:
		return sqlite.DatabaseFilePath(p.DSN)
	default:
		return "", fmt.Errorf("unsupported database driver %q", p.Driver)
	}
}
