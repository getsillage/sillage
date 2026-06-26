package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"

	"github.com/miofelix/sillage/internal/profile"
	"github.com/miofelix/sillage/internal/secret"
	"github.com/miofelix/sillage/server"
	"github.com/miofelix/sillage/store"
	"github.com/miofelix/sillage/store/db"
)

func main() {
	if err := newRootCommand().Execute(); err != nil {
		os.Exit(1)
	}
}

func newRootCommand() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "sillage",
		Short: "Self-hosted private memo and AI reflection tool",
		RunE: func(_ *cobra.Command, _ []string) error {
			return run()
		},
	}

	cmd.PersistentFlags().String("addr", "", "HTTP bind address")
	cmd.PersistentFlags().Int("port", profile.DefaultPort, "HTTP bind port")
	cmd.PersistentFlags().String("data", "", "data directory")
	cmd.PersistentFlags().String("dsn", "", "SQLite database path")
	cmd.PersistentFlags().String("driver", profile.DriverSQLite, "database driver")
	cmd.PersistentFlags().String("instance-url", "", "external instance URL")
	cmd.PersistentFlags().String("log-format", "json", "log format: json or text")
	cmd.PersistentFlags().String("log-level", "info", "log level: debug, info, warn, or error")

	mustBindFlag(cmd, "addr")
	mustBindFlag(cmd, "port")
	mustBindFlag(cmd, "data")
	mustBindFlag(cmd, "dsn")
	mustBindFlag(cmd, "driver")
	mustBindFlag(cmd, "instance-url")
	mustBindFlag(cmd, "log-format")
	mustBindFlag(cmd, "log-level")

	viper.SetEnvPrefix("sillage")
	viper.SetEnvKeyReplacer(strings.NewReplacer("-", "_"))
	viper.AutomaticEnv()

	return cmd
}

func mustBindFlag(cmd *cobra.Command, name string) {
	if err := viper.BindPFlag(name, cmd.PersistentFlags().Lookup(name)); err != nil {
		panic(err)
	}
}

func run() error {
	instanceProfile := &profile.Profile{
		Addr:        viper.GetString("addr"),
		Port:        viper.GetInt("port"),
		Data:        viper.GetString("data"),
		Driver:      viper.GetString("driver"),
		DSN:         viper.GetString("dsn"),
		InstanceURL: viper.GetString("instance-url"),
		LogFormat:   viper.GetString("log-format"),
		LogLevel:    viper.GetString("log-level"),
	}
	if err := instanceProfile.Validate(); err != nil {
		return fmt.Errorf("validate profile: %w", err)
	}
	configureLogger(instanceProfile)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	dbDriver, err := db.NewDBDriver(instanceProfile)
	if err != nil {
		return fmt.Errorf("create database driver: %w", err)
	}

	storeInstance := store.New(dbDriver, instanceProfile)
	if err := storeInstance.Migrate(ctx); err != nil {
		_ = storeInstance.Close()
		return fmt.Errorf("migrate database: %w", err)
	}
	secrets, err := secret.Load(instanceProfile.Data)
	if err != nil {
		_ = storeInstance.Close()
		return fmt.Errorf("load runtime secrets: %w", err)
	}

	srv, err := server.New(ctx, instanceProfile, storeInstance, secrets)
	if err != nil {
		_ = storeInstance.Close()
		return fmt.Errorf("create server: %w", err)
	}
	if err := srv.Start(ctx); err != nil {
		_ = storeInstance.Close()
		return fmt.Errorf("start server: %w", err)
	}

	printGreetings(instanceProfile)
	<-ctx.Done()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return fmt.Errorf("shutdown server: %w", err)
	}
	return nil
}

func configureLogger(p *profile.Profile) {
	level := slog.LevelInfo
	switch strings.ToLower(p.LogLevel) {
	case "debug":
		level = slog.LevelDebug
	case "info", "":
		level = slog.LevelInfo
	case "warn":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}

	opts := &slog.HandlerOptions{Level: level}
	var handler slog.Handler
	switch strings.ToLower(p.LogFormat) {
	case "text":
		handler = slog.NewTextHandler(os.Stderr, opts)
	default:
		handler = slog.NewJSONHandler(os.Stderr, opts)
	}
	slog.SetDefault(slog.New(handler))
}

func printGreetings(p *profile.Profile) {
	fmt.Println("Sillage started successfully.")
	fmt.Printf("Data directory: %s\n", p.Data)
	fmt.Printf("Database driver: %s\n", p.Driver)
	fmt.Printf("Database: %s\n", p.DSN)
	fmt.Printf("Access Sillage at: http://localhost:%d\n", p.Port)
}
