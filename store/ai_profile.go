package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

type AIProfile struct {
	ID             string
	AccountID      string
	Name           string
	Provider       string
	BaseURL        string
	Model          string
	Temperature    float64
	MaxTokens      int64
	Enabled        bool
	Active         bool
	APIKeyEnvelope sql.NullString
	KeyUnavailable bool
	AutoSummary    bool
	CreatedAt      int64
	UpdatedAt      int64
	DeletedAt      sql.NullInt64
}

type UpsertAIProfile struct {
	ID             string
	AccountID      string
	Name           string
	Provider       string
	BaseURL        string
	Model          string
	Temperature    float64
	MaxTokens      int64
	Enabled        bool
	Active         bool
	APIKeyEnvelope *string
	KeyUnavailable bool
	AutoSummary    bool
}

func (s *Store) UpsertAIProfile(ctx context.Context, upsert *UpsertAIProfile) (*AIProfile, error) {
	id := upsert.ID
	if id == "" {
		generated, err := uuid.NewV7()
		if err != nil {
			return nil, fmt.Errorf("generate ai profile id: %w", err)
		}
		id = generated.String()
	}
	now := time.Now().UTC().UnixMilli()
	tx, err := s.driver.GetDB().BeginTx(ctx, nil)
	if err != nil {
		return nil, fmt.Errorf("begin ai profile upsert: %w", err)
	}
	defer tx.Rollback()

	if upsert.Active {
		if _, err := tx.ExecContext(ctx, "UPDATE ai_profile SET active = 0, updated_at = ? WHERE account_id = ?", now, upsert.AccountID); err != nil {
			return nil, fmt.Errorf("clear active ai profile: %w", err)
		}
	}

	existing, err := scanAIProfile(tx.QueryRowContext(ctx, aiProfileSelect()+`
WHERE id = ? AND account_id = ?`, id, upsert.AccountID))
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return nil, err
	}
	apiKeyEnvelope := sql.NullString{}
	if existing != nil {
		apiKeyEnvelope = existing.APIKeyEnvelope
	}
	if upsert.APIKeyEnvelope != nil {
		apiKeyEnvelope = sql.NullString{String: *upsert.APIKeyEnvelope, Valid: *upsert.APIKeyEnvelope != ""}
	}

	if existing == nil {
		if _, err := tx.ExecContext(ctx, `
INSERT INTO ai_profile (
  id, account_id, name, provider, base_url, model, temperature, max_tokens,
  enabled, active, api_key_envelope, key_unavailable, auto_summary, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
			id,
			upsert.AccountID,
			upsert.Name,
			upsert.Provider,
			upsert.BaseURL,
			upsert.Model,
			upsert.Temperature,
			upsert.MaxTokens,
			boolInt(upsert.Enabled),
			boolInt(upsert.Active),
			nullableString(apiKeyEnvelope),
			boolInt(upsert.KeyUnavailable),
			boolInt(upsert.AutoSummary),
			now,
			now,
		); err != nil {
			return nil, fmt.Errorf("insert ai profile: %w", err)
		}
	} else {
		if _, err := tx.ExecContext(ctx, `
UPDATE ai_profile
SET name = ?, provider = ?, base_url = ?, model = ?, temperature = ?, max_tokens = ?,
  enabled = ?, active = ?, api_key_envelope = ?, key_unavailable = ?, auto_summary = ?, updated_at = ?, deleted_at = NULL
WHERE id = ? AND account_id = ?`,
			upsert.Name,
			upsert.Provider,
			upsert.BaseURL,
			upsert.Model,
			upsert.Temperature,
			upsert.MaxTokens,
			boolInt(upsert.Enabled),
			boolInt(upsert.Active),
			nullableString(apiKeyEnvelope),
			boolInt(upsert.KeyUnavailable),
			boolInt(upsert.AutoSummary),
			now,
			id,
			upsert.AccountID,
		); err != nil {
			return nil, fmt.Errorf("update ai profile: %w", err)
		}
	}
	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit ai profile upsert: %w", err)
	}
	return s.GetAIProfile(ctx, upsert.AccountID, id)
}

func (s *Store) GetAIProfile(ctx context.Context, accountID, id string) (*AIProfile, error) {
	return scanAIProfile(s.driver.GetDB().QueryRowContext(ctx, aiProfileSelect()+`
WHERE id = ? AND account_id = ? AND deleted_at IS NULL`, id, accountID))
}

func (s *Store) ListAIProfiles(ctx context.Context, accountID string) ([]*AIProfile, error) {
	rows, err := s.driver.GetDB().QueryContext(ctx, aiProfileSelect()+`
WHERE account_id = ? AND deleted_at IS NULL
ORDER BY active DESC, updated_at DESC, id DESC`, accountID)
	if err != nil {
		return nil, fmt.Errorf("list ai profiles: %w", err)
	}
	defer rows.Close()

	var profiles []*AIProfile
	for rows.Next() {
		profile, err := scanAIProfile(rows)
		if err != nil {
			return nil, err
		}
		profiles = append(profiles, profile)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate ai profiles: %w", err)
	}
	return profiles, nil
}

func (s *Store) MarkAIProfileKeyUnavailable(ctx context.Context, accountID, id string) error {
	if _, err := s.driver.GetDB().ExecContext(ctx, `
UPDATE ai_profile SET key_unavailable = 1, updated_at = ? WHERE account_id = ? AND id = ?`,
		time.Now().UTC().UnixMilli(), accountID, id); err != nil {
		return fmt.Errorf("mark ai profile key unavailable: %w", err)
	}
	return nil
}

func aiProfileSelect() string {
	return `
SELECT id, account_id, name, provider, base_url, model, temperature, max_tokens,
  enabled, active, api_key_envelope, key_unavailable, auto_summary, created_at, updated_at, deleted_at
FROM ai_profile `
}

func scanAIProfile(row interface {
	Scan(dest ...any) error
}) (*AIProfile, error) {
	var profile AIProfile
	var enabled, active, keyUnavailable, autoSummary int
	if err := row.Scan(
		&profile.ID,
		&profile.AccountID,
		&profile.Name,
		&profile.Provider,
		&profile.BaseURL,
		&profile.Model,
		&profile.Temperature,
		&profile.MaxTokens,
		&enabled,
		&active,
		&profile.APIKeyEnvelope,
		&keyUnavailable,
		&autoSummary,
		&profile.CreatedAt,
		&profile.UpdatedAt,
		&profile.DeletedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, fmt.Errorf("scan ai profile: %w", err)
	}
	profile.Enabled = enabled == 1
	profile.Active = active == 1
	profile.KeyUnavailable = keyUnavailable == 1
	profile.AutoSummary = autoSummary == 1
	return &profile, nil
}

func boolInt(value bool) int {
	if value {
		return 1
	}
	return 0
}
