package profile

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

const (
	DefaultPort       = 5231
	DefaultDataDir    = "/var/opt/sillage"
	DriverSQLite      = "sqlite"
	DefaultSQLiteFile = "sillage.db"
)

// Profile contains the process configuration needed to start Sillage.
type Profile struct {
	Addr        string
	Port        int
	Data        string
	Driver      string
	DSN         string
	MaxUploadMB int
	LogFormat   string
	LogLevel    string
}

// Validate normalizes defaults and creates the persistent directory layout.
func (p *Profile) Validate() error {
	if p.Port == 0 {
		p.Port = DefaultPort
	}
	if p.Driver == "" {
		p.Driver = DriverSQLite
	}
	if p.Driver != DriverSQLite {
		return fmt.Errorf("unsupported database driver %q", p.Driver)
	}
	if p.MaxUploadMB <= 0 {
		p.MaxUploadMB = 30
	}

	dataDir, err := chooseDataDir(p.Data)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dataDir, 0o770); err != nil {
		return fmt.Errorf("create data directory: %w", err)
	}
	dataDir, err = filepath.Abs(dataDir)
	if err != nil {
		return fmt.Errorf("resolve data directory: %w", err)
	}
	p.Data = strings.TrimRight(dataDir, string(filepath.Separator))

	for _, dir := range []string{
		filepath.Join(p.Data, "assets", "attachments"),
		filepath.Join(p.Data, ".thumbnail_cache"),
		filepath.Join(p.Data, "runtime"),
	} {
		if err := os.MkdirAll(dir, 0o770); err != nil {
			return fmt.Errorf("create runtime directory %s: %w", dir, err)
		}
	}

	if p.DSN == "" {
		p.DSN = filepath.Join(p.Data, DefaultSQLiteFile)
	} else if !filepath.IsAbs(p.DSN) {
		p.DSN = filepath.Join(p.Data, p.DSN)
	}
	return nil
}

func chooseDataDir(configured string) (string, error) {
	if configured != "" {
		return configured, nil
	}
	if runtime.GOOS == "windows" {
		programData := os.Getenv("ProgramData")
		if programData == "" {
			return ".", nil
		}
		return filepath.Join(programData, "Sillage"), nil
	}
	if info, err := os.Stat(DefaultDataDir); err == nil && info.IsDir() {
		return DefaultDataDir, nil
	} else if err != nil && !errors.Is(err, os.ErrNotExist) {
		return "", fmt.Errorf("check default data directory: %w", err)
	}
	return ".", nil
}
