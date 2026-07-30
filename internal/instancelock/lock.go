package instancelock

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
)

var ErrInUse = errors.New("sillage storage is already in use")

// Lock is a set of advisory, process-scoped locks for one Sillage instance.
// Lock files remain on disk after release; the operating system releases the
// actual locks automatically if the process exits unexpectedly.
type Lock struct {
	files []*os.File
}

// Acquire locks the data directory and, when supplied, the SQLite database
// path. The database-side lock prevents two configurations with different data
// directories from pointing at the same external DSN concurrently.
func Acquire(dataDir string, databasePaths ...string) (*Lock, error) {
	paths := []string{filepath.Join(dataDir, "runtime", "instance.lock")}
	for _, databasePath := range databasePaths {
		if databasePath != "" {
			paths = append(paths, canonicalPath(databasePath)+".sillage.lock")
		}
	}
	sort.Strings(paths)
	lock := &Lock{}
	previous := ""
	for _, path := range paths {
		if path == previous {
			continue
		}
		previous = path
		file, err := acquireFile(path)
		if err != nil {
			_ = lock.Release()
			return nil, err
		}
		lock.files = append(lock.files, file)
	}
	return lock, nil
}

func acquireFile(path string) (*os.File, error) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600)
	if err != nil {
		return nil, fmt.Errorf("open instance lock %s: %w", path, err)
	}
	if err := file.Chmod(0o600); err != nil {
		_ = file.Close()
		return nil, fmt.Errorf("restrict instance lock permissions: %w", err)
	}
	if err := lockFile(file); err != nil {
		_ = file.Close()
		if isLockBusy(err) {
			return nil, fmt.Errorf("%w: %s", ErrInUse, path)
		}
		return nil, fmt.Errorf("acquire instance lock: %w", err)
	}
	if err := file.Truncate(0); err != nil {
		_ = unlockFile(file)
		_ = file.Close()
		return nil, fmt.Errorf("truncate instance lock: %w", err)
	}
	if _, err := file.WriteAt([]byte(strconv.Itoa(os.Getpid())+"\n"), 0); err != nil {
		_ = unlockFile(file)
		_ = file.Close()
		return nil, fmt.Errorf("write instance lock owner: %w", err)
	}
	return file, nil
}

func (l *Lock) Release() error {
	if l == nil || len(l.files) == 0 {
		return nil
	}
	var releaseErr error
	for index := len(l.files) - 1; index >= 0; index-- {
		file := l.files[index]
		if err := unlockFile(file); err != nil && releaseErr == nil {
			releaseErr = fmt.Errorf("release instance lock: %w", err)
		}
		if err := file.Close(); err != nil && releaseErr == nil {
			releaseErr = fmt.Errorf("close instance lock: %w", err)
		}
	}
	l.files = nil
	return releaseErr
}

func canonicalPath(path string) string {
	abs, err := filepath.Abs(path)
	if err != nil {
		return filepath.Clean(path)
	}
	if resolved, err := filepath.EvalSymlinks(abs); err == nil {
		return resolved
	}
	parent := filepath.Dir(abs)
	if resolvedParent, err := filepath.EvalSymlinks(parent); err == nil {
		return filepath.Join(resolvedParent, filepath.Base(abs))
	}
	return abs
}
