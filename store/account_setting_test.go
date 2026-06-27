package store_test

import (
	"context"
	"testing"
)

func TestAccountSettingRoundTrip(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	if _, ok, err := s.GetAccountSetting(ctx, account, "ai.auto_summary"); err != nil || ok {
		t.Fatalf("GetAccountSetting before put = ok %v err %v, want false nil", ok, err)
	}

	if err := s.PutAccountSetting(ctx, account, "ai.auto_summary", "true"); err != nil {
		t.Fatalf("PutAccountSetting() error = %v", err)
	}
	value, ok, err := s.GetAccountSetting(ctx, account, "ai.auto_summary")
	if err != nil {
		t.Fatalf("GetAccountSetting() error = %v", err)
	}
	if !ok || value != "true" {
		t.Fatalf("GetAccountSetting() = %q, %v; want true, true", value, ok)
	}

	if err := s.PutAccountSetting(ctx, account, "ai.auto_summary", "false"); err != nil {
		t.Fatalf("second PutAccountSetting() error = %v", err)
	}
	value, ok, err = s.GetAccountSetting(ctx, account, "ai.auto_summary")
	if err != nil {
		t.Fatalf("second GetAccountSetting() error = %v", err)
	}
	if !ok || value != "false" {
		t.Fatalf("second GetAccountSetting() = %q, %v; want false, true", value, ok)
	}
}
