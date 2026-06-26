package server

import (
	"database/sql"
	"errors"
	"net/http"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/miofelix/sillage/internal/secret"
	"github.com/miofelix/sillage/store"
)

type aiSettingsRequest struct {
	Profiles []aiProfileRequest `json:"profiles"`
}

type aiProfileRequest struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Provider    string  `json:"provider"`
	BaseURL     string  `json:"baseUrl"`
	Model       string  `json:"model"`
	Temperature float64 `json:"temperature"`
	MaxTokens   int64   `json:"maxTokens"`
	Enabled     bool    `json:"enabled"`
	Active      bool    `json:"active"`
	APIKey      *string `json:"apiKey"`
}

func (s *Server) registerAIRoutes(e *echo.Echo) {
	e.GET("/api/v1/settings/ai", s.handleGetAISettings)
	e.PATCH("/api/v1/settings/ai", s.handlePatchAISettings)
	e.POST("/api/v1/memos/:memo:generate-summary", s.handleGenerateMemoSummary)
}

func (s *Server) handleGetAISettings(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	profiles, err := s.Store.ListAIProfiles(c.Request().Context(), account.ID)
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取 AI 设置失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"profiles": aiProfileDTOs(profiles)})
}

func (s *Server) handlePatchAISettings(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req aiSettingsRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	var profiles []*store.AIProfile
	for _, profileReq := range req.Profiles {
		if profileReq.Name == "" || profileReq.Provider == "" {
			return apiError(c, http.StatusBadRequest, "invalid_field", "AI 档案名称和 provider 不能为空")
		}
		var envelope *string
		if profileReq.APIKey != nil {
			raw, err := secret.EncryptEnvelope(s.Secrets.EncryptionSecret, *profileReq.APIKey)
			if err != nil {
				return apiError(c, http.StatusInternalServerError, "internal", "加密 API Key 失败")
			}
			envelope = &raw
		}
		maxTokens := profileReq.MaxTokens
		if maxTokens <= 0 {
			maxTokens = 1000
		}
		temperature := profileReq.Temperature
		if temperature == 0 {
			temperature = 0.3
		}
		profile, err := s.Store.UpsertAIProfile(c.Request().Context(), &store.UpsertAIProfile{
			ID:             profileReq.ID,
			AccountID:      account.ID,
			Name:           profileReq.Name,
			Provider:       profileReq.Provider,
			BaseURL:        profileReq.BaseURL,
			Model:          profileReq.Model,
			Temperature:    temperature,
			MaxTokens:      maxTokens,
			Enabled:        profileReq.Enabled,
			Active:         profileReq.Active,
			APIKeyEnvelope: envelope,
			KeyUnavailable: false,
		})
		if err != nil {
			return apiError(c, http.StatusInternalServerError, "internal", "保存 AI 设置失败")
		}
		profiles = append(profiles, profile)
	}
	return c.JSON(http.StatusOK, map[string]any{"profiles": aiProfileDTOs(profiles)})
}

func (s *Server) handleGenerateMemoSummary(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	memo, err := s.Store.GetMemo(c.Request().Context(), account.ID, memoParam(c), false)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "记录不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取记录失败")
	}
	profiles, err := s.Store.ListAIProfiles(c.Request().Context(), account.ID)
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取 AI 设置失败")
	}
	for _, profile := range profiles {
		if profile.Active && profile.APIKeyEnvelope.Valid {
			if _, err := secret.DecryptEnvelope(s.Secrets.EncryptionSecret, profile.APIKeyEnvelope.String); err != nil {
				_ = s.Store.MarkAIProfileKeyUnavailable(c.Request().Context(), account.ID, profile.ID)
				return apiError(c, http.StatusBadRequest, "key_unavailable", "当前 AI API Key 无法解密，请重新保存")
			}
			break
		}
	}
	summary := summarizeMemoLocally(memo.Content)
	ai, err := s.Store.UpsertMemoAI(c.Request().Context(), &store.UpsertMemoAI{
		MemoID:        memo.ID,
		Summary:       summary,
		Sentiment:     "",
		Provider:      "local",
		Model:         "local-summary",
		ProfileID:     "",
		PromptVersion: "memo-summary-v1",
		SourceMemoIDs: `["` + memo.ID + `"]`,
		Status:        "complete",
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "生成总结失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"ai": memoAIDTO(ai)})
}

func aiProfileDTOs(profiles []*store.AIProfile) []map[string]any {
	dtos := make([]map[string]any, 0, len(profiles))
	for _, profile := range profiles {
		dtos = append(dtos, aiProfileDTO(profile))
	}
	return dtos
}

func aiProfileDTO(profile *store.AIProfile) map[string]any {
	return map[string]any{
		"id":             profile.ID,
		"name":           profile.Name,
		"provider":       profile.Provider,
		"baseUrl":        profile.BaseURL,
		"model":          profile.Model,
		"temperature":    profile.Temperature,
		"maxTokens":      profile.MaxTokens,
		"enabled":        profile.Enabled,
		"active":         profile.Active,
		"hasApiKey":      profile.APIKeyEnvelope.Valid,
		"keyUnavailable": profile.KeyUnavailable,
		"createdAt":      time.UnixMilli(profile.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":      time.UnixMilli(profile.UpdatedAt).UTC().Format(time.RFC3339),
	}
}

func summarizeMemoLocally(content string) string {
	if len(content) <= 120 {
		return content
	}
	return content[:120] + "..."
}

func memoAIDTO(ai *store.MemoAI) map[string]any {
	if ai == nil {
		return nil
	}
	return map[string]any{
		"memoId":        ai.MemoID,
		"summary":       optionalString(ai.Summary),
		"sentiment":     optionalString(ai.Sentiment),
		"provider":      ai.Provider,
		"model":         ai.Model,
		"profileId":     ai.ProfileID,
		"promptVersion": ai.PromptVersion,
		"sourceMemoIds": ai.SourceMemoIDs,
		"status":        ai.Status,
		"errorCode":     optionalString(ai.ErrorCode),
		"startedAt":     optionalTime(ai.StartedAt),
		"finishedAt":    optionalTime(ai.FinishedAt),
		"createdAt":     time.UnixMilli(ai.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":     time.UnixMilli(ai.UpdatedAt).UTC().Format(time.RFC3339),
	}
}

func memoAIDTOs(items []*store.MemoAI) []map[string]any {
	dtos := make([]map[string]any, 0, len(items))
	for _, item := range items {
		dtos = append(dtos, memoAIDTO(item))
	}
	return dtos
}
