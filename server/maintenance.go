package server

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"path/filepath"
	"time"

	"github.com/getsillage/sillage/store"
)

const (
	maintenanceInterval   = 6 * time.Hour
	orphanAttachmentGrace = 24 * time.Hour
)

func (s *Server) startMaintenance(ctx context.Context) {
	maintenanceCtx, cancel := context.WithCancel(ctx)
	s.maintenanceCancel = cancel
	s.runMaintenance(maintenanceCtx, time.Now().UTC())
	s.maintenanceWG.Add(1)
	go func() {
		defer s.maintenanceWG.Done()
		ticker := time.NewTicker(maintenanceInterval)
		defer ticker.Stop()
		for {
			select {
			case <-maintenanceCtx.Done():
				return
			case now := <-ticker.C:
				s.runMaintenance(maintenanceCtx, now.UTC())
			}
		}
	}()
}

func (s *Server) stopMaintenance() {
	if s.maintenanceCancel != nil {
		s.maintenanceCancel()
	}
	s.maintenanceWG.Wait()
}

func (s *Server) runMaintenance(ctx context.Context, now time.Time) {
	purged, err := s.Store.PurgeExpiredMemos(ctx, now)
	if err != nil {
		slog.Warn("purge expired deleted memos", "error", err)
	} else if purged > 0 {
		slog.Info("purged expired deleted memos", "purged", purged)
	}
	stats, err := s.Store.CleanupEphemeralData(ctx, now)
	if err != nil {
		slog.Warn("cleanup ephemeral data", "error", err)
	} else if stats.Sessions+stats.RuntimeValues+stats.SyncMutations > 0 {
		slog.Info("cleaned ephemeral data",
			"sessions", stats.Sessions,
			"runtime_values", stats.RuntimeValues,
			"sync_mutations", stats.SyncMutations,
		)
	}
	refs, err := s.Store.ListAttachmentStorageRefs(ctx)
	if err != nil {
		slog.Warn("list attachment storage refs for cleanup", "error", err)
		return
	}
	removed, err := cleanupAttachmentDirectory(ctx, filepath.Join(s.Profile.Data, "assets", "attachments"), refs, now, orphanAttachmentGrace)
	if err != nil {
		slog.Warn("cleanup attachment files", "error", err)
	} else if removed > 0 {
		slog.Info("cleaned attachment files", "removed", removed)
	}
}

func cleanupAttachmentDirectory(ctx context.Context, directory string, refs []store.AttachmentStorageRef, now time.Time, grace time.Duration) (int, error) {
	active := make(map[string]struct{})
	deleted := make(map[string]struct{})
	for _, ref := range refs {
		clean := filepath.Clean(ref.StorageRef)
		if filepath.Dir(clean) != filepath.Join("assets", "attachments") {
			continue
		}
		name := filepath.Base(clean)
		if ref.Deleted {
			deleted[name] = struct{}{}
		} else {
			active[name] = struct{}{}
		}
	}
	entries, err := os.ReadDir(directory)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return 0, nil
		}
		return 0, err
	}
	removed := 0
	for _, entry := range entries {
		if err := ctx.Err(); err != nil {
			return removed, err
		}
		if !entry.Type().IsRegular() {
			continue
		}
		name := entry.Name()
		if _, ok := active[name]; ok {
			continue
		}
		_, isDeleted := deleted[name]
		if !isDeleted {
			info, err := entry.Info()
			if err != nil {
				return removed, err
			}
			if info.ModTime().After(now.Add(-grace)) {
				continue
			}
		}
		if err := os.Remove(filepath.Join(directory, name)); err != nil && !errors.Is(err, os.ErrNotExist) {
			return removed, err
		}
		removed++
	}
	return removed, nil
}
