package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"strconv"
	"time"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

func newHealthcheckCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "healthcheck",
		Short: "Check readiness on the local HTTP port",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			port := viper.GetInt("port")
			if port < 1 || port > 65535 {
				return fmt.Errorf("invalid healthcheck port: %d", port)
			}
			return checkReadiness(cmd.Context(), net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
		},
	}
}

func checkReadiness(ctx context.Context, address string) error {
	client := http.Client{
		Timeout:       2 * time.Second,
		Transport:     &http.Transport{DisableKeepAlives: true},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "http://"+address+"/readyz", nil)
	if err != nil {
		return err
	}
	res, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("readiness request: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return fmt.Errorf("readiness returned HTTP %d", res.StatusCode)
	}
	var result struct{ Status string }
	if err := json.NewDecoder(io.LimitReader(res.Body, 1024)).Decode(&result); err != nil || result.Status != "ready" {
		return fmt.Errorf("readiness response did not confirm ready status")
	}
	return nil
}
