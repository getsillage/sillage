package store

import (
	"context"

	"github.com/getsillage/sillage/internal/profile"
)

// Store aggregates database-backed services.
type Store struct {
	driver  Driver
	profile *profile.Profile
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

func isPageLookahead(limit, pageSize, maxPageSize int) bool {
	return pageSize > 0 && pageSize <= maxPageSize && limit == pageSize+1
}
