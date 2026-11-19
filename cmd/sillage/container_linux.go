package main

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"

	"github.com/spf13/cobra"

	"github.com/getsillage/sillage/internal/profile"
)

func registerContainerCommand(root *cobra.Command) {
	root.AddCommand(&cobra.Command{
		Use:                "container-start",
		Hidden:             true,
		DisableFlagParsing: true,
		RunE: func(_ *cobra.Command, args []string) error {
			return startContainer(args)
		},
	})
}

func startContainer(args []string) error {
	// Preserve the former shell entrypoint's command substitution: remove
	// trailing newlines, not spaces that may be part of an existing secret.
	if err := expandFileEnvWithTrim(func(s string) string { return strings.TrimRight(s, "\n") },
		"SILLAGE_DSN", "SESSION_SECRET", "ENCRYPTION_SECRET"); err != nil {
		return err
	}
	self, err := os.Executable()
	if err != nil {
		return fmt.Errorf("locate container executable: %w", err)
	}
	if os.Geteuid() == 0 {
		uid, err := containerID(os.Getenv("SILLAGE_UID"))
		if err != nil {
			return fmt.Errorf("invalid SILLAGE_UID: %w", err)
		}
		gid, err := containerID(os.Getenv("SILLAGE_GID"))
		if err != nil {
			return fmt.Errorf("invalid SILLAGE_GID: %w", err)
		}
		data := os.Getenv("SILLAGE_DATA")
		if data == "" {
			data = profile.DefaultDataDir
		}
		if abs, err := filepath.Abs(data); err != nil || abs == "/" {
			return fmt.Errorf("container data directory must not be the filesystem root")
		}
		if err := prepareContainerData(data, uid, gid); err != nil {
			return fmt.Errorf("prepare container data: %w", err)
		}
		if err := syscall.Setgroups([]int{gid}); err != nil {
			return fmt.Errorf("set container supplementary group: %w", err)
		}
		if err := syscall.Setgid(gid); err != nil {
			return fmt.Errorf("set container GID: %w", err)
		}
		if err := syscall.Setuid(uid); err != nil {
			return fmt.Errorf("set container UID: %w", err)
		}
	}
	// Replace PID 1 instead of leaving a privileged parent waiting for it.
	return syscall.Exec(self, append([]string{self}, args...), os.Environ())
}

func containerID(value string) (int, error) {
	if value == "" {
		return 10001, nil
	}
	id, err := strconv.ParseUint(value, 10, 31)
	if err != nil || id == 0 {
		return 0, fmt.Errorf("expected a positive numeric ID")
	}
	return int(id), nil
}

func prepareContainerData(data string, uid, gid int) error {
	if err := os.MkdirAll(data, 0o755); err != nil {
		return err
	}
	info, err := os.Lstat(filepath.Clean(data))
	if err != nil {
		return err
	}
	if !info.IsDir() {
		return fmt.Errorf("data path must be a real directory")
	}
	root, err := os.OpenRoot(data)
	if err != nil {
		return err
	}
	defer root.Close()
	for _, dir := range []string{"assets", "assets/attachments", ".thumbnail_cache", "runtime"} {
		if err := root.Mkdir(dir, 0o755); err != nil && !os.IsExist(err) {
			return err
		}
		info, err := root.Lstat(dir)
		if err != nil {
			return err
		}
		if !info.IsDir() {
			return fmt.Errorf("%s must be a real directory", dir)
		}
	}
	return fs.WalkDir(root.FS(), ".", func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.Type()&fs.ModeSymlink != 0 {
			return err
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		stat := info.Sys().(*syscall.Stat_t)
		if int(stat.Uid) == uid && int(stat.Gid) == gid {
			return nil
		}
		if info.Mode().IsRegular() && stat.Nlink > 1 {
			return fmt.Errorf("refusing to change ownership of multiply linked file %q", path)
		}
		return root.Lchown(path, uid, gid)
	})
}
