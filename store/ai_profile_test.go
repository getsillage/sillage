package store_test

import (
	"context"
	"testing"

	"github.com/getsillage/sillage/store"
)

func TestUpsertAIProfilePersistsAutoSummary(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)

	key := "envelope"
	created, err := s.UpsertAIProfile(ctx, &store.UpsertAIProfile{
		AccountID:      account,
		Name:           "默认",
		Provider:       "anthropic",
		Model:          "claude-opus-4-8",
		Enabled:        true,
		Active:         true,
		APIKeyEnvelope: &key,
		AutoSummary:    true,
	})
	if err != nil {
		t.Fatalf("UpsertAIProfile() error = %v", err)
	}
	if !created.AutoSummary {
		t.Fatalf("created.AutoSummary = false, want true")
	}

	// Toggle off and confirm it round-trips through a re-read.
	updated, err := s.UpsertAIProfile(ctx, &store.UpsertAIProfile{
		ID:          created.ID,
		AccountID:   account,
		Name:        "默认",
		Provider:    "anthropic",
		Model:       "claude-opus-4-8",
		Enabled:     true,
		Active:      true,
		AutoSummary: false,
	})
	if err != nil {
		t.Fatalf("second UpsertAIProfile() error = %v", err)
	}
	if updated.AutoSummary {
		t.Fatalf("updated.AutoSummary = true, want false")
	}
	// Existing key is preserved when APIKeyEnvelope is nil.
	if !updated.APIKeyEnvelope.Valid {
		t.Fatalf("api key envelope was lost on update")
	}

	got, err := s.GetAIProfile(ctx, account, created.ID)
	if err != nil {
		t.Fatalf("GetAIProfile() error = %v", err)
	}
	if got.AutoSummary {
		t.Fatalf("reloaded AutoSummary = true, want false")
	}
}
