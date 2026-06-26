package db

import (
	"fmt"

	"github.com/miofelix/sillage/internal/profile"
	"github.com/miofelix/sillage/store"
	"github.com/miofelix/sillage/store/db/sqlite"
)

func NewDBDriver(p *profile.Profile) (store.Driver, error) {
	switch p.Driver {
	case profile.DriverSQLite:
		return sqlite.New(p)
	default:
		return nil, fmt.Errorf("unsupported database driver %q", p.Driver)
	}
}
