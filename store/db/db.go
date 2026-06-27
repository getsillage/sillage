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
