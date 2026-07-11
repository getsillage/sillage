package store_test

import (
	"context"
	"database/sql"
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

func TestDeleteAIProfilesExceptClearsKeyEnvelope(t *testing.T) {
	s := newTestStore(t)
	ctx := context.Background()
	account := newTestAccount(t, s)
	key := "encrypted-envelope"
	profile, err := s.UpsertAIProfile(ctx, &store.UpsertAIProfile{
		AccountID:      account,
		Name:           "默认",
		Provider:       "openai",
		Enabled:        true,
		Active:         true,
		APIKeyEnvelope: &key,
	})
	if err != nil {
		t.Fatalf("UpsertAIProfile() error = %v", err)
	}
	if err := s.DeleteAIProfilesExcept(ctx, account, nil); err != nil {
		t.Fatalf("DeleteAIProfilesExcept() error = %v", err)
	}
	var envelope sql.NullString
	if err := s.GetDriver().GetDB().QueryRowContext(ctx, `
SELECT api_key_envelope FROM ai_profile WHERE id = ?`, profile.ID).Scan(&envelope); err != nil {
		t.Fatalf("read deleted profile envelope: %v", err)
	}
	if envelope.Valid {
		t.Fatalf("deleted profile envelope = %q, want NULL", envelope.String)
	}
}
