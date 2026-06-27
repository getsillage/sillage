package server

import (
	"database/sql"
	"errors"
	"net/http"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/getsillage/sillage/store"
)

type aiSettingsRequest struct {
	Profiles    []aiProfileRequest `json:"profiles"`
	AutoSummary *bool              `json:"autoSummary"`
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
	AutoSummary bool    `json:"autoSummary"`
	APIKey      *string `json:"apiKey"`
}

func (s *Server) registerAIRoutes(e *echo.Echo) {
	e.GET("/api/v1/settings/ai", s.handleGetAISettings)
	e.PATCH("/api/v1/settings/ai", s.handlePatchAISettings)
	e.POST("/api/v1/settings/:aiAction", s.handleAISettingsAction)
}

type aiTestRequest struct {
	ID          string  `json:"id"`
	Provider    string  `json:"provider"`
	BaseURL     string  `json:"baseUrl"`
	Model       string  `json:"model"`
	Temperature float64 `json:"temperature"`
	MaxTokens   int64   `json:"maxTokens"`
	APIKey      *string `json:"apiKey"`
}

type aiModelsRequest struct {
	ID       string  `json:"id"`
	Provider string  `json:"provider"`
	BaseURL  string  `json:"baseUrl"`
	APIKey   *string `json:"apiKey"`
}

func (s *Server) handleAISettingsAction(c *echo.Context) error {
	switch c.Request().URL.Path {
	case "/api/v1/settings/ai:test":
		return s.handleTestAISettings(c)
	case "/api/v1/settings/ai:models":
		return s.handleListAIModels(c)
	default:
		return apiError(c, http.StatusNotFound, "not_found", "接口不存在")
	}
}

func (s *Server) handleTestAISettings(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req aiTestRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	model, err := s.testAIConnection(c.Request().Context(), account.ID, aiTestInput{
		ID:          req.ID,
		Provider:    req.Provider,
		BaseURL:     req.BaseURL,
		Model:       req.Model,
		Temperature: req.Temperature,
		MaxTokens:   req.MaxTokens,
		APIKey:      req.APIKey,
	})
	if err != nil {
		status, code, message := aiTestHTTPStatus(err)
		return apiError(c, status, code, message)
	}
	return c.JSON(http.StatusOK, map[string]any{"ok": true, "model": model})
}

func (s *Server) handleListAIModels(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req aiModelsRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	models, err := s.listAIModels(c.Request().Context(), account.ID, aiModelsInput{
		ID:       req.ID,
		Provider: req.Provider,
		BaseURL:  req.BaseURL,
		APIKey:   req.APIKey,
	})
	if err != nil {
		status, code, message := aiTestHTTPStatus(err)
		return apiError(c, status, code, message)
	}
	return c.JSON(http.StatusOK, map[string]any{"models": models})
}

// aiTestHTTPStatus maps a connection-test failure to a status + a message the
// user can act on. Unknown failures surface the provider's own message since
// diagnosing the connection is the whole point of this endpoint.
func aiTestHTTPStatus(err error) (int, string, string) {
	switch {
	case errors.Is(err, errValidation):
		return http.StatusBadRequest, "invalid_field", err.Error()
	case errors.Is(err, sql.ErrNoRows):
		return http.StatusNotFound, "not_found", "AI 档案不存在"
	case errors.Is(err, errAINotConfigured):
		return http.StatusBadRequest, "ai_not_configured", "该档案还没有可用的 API Key"
	case errors.Is(err, errAIKeyUnavailable):
		return http.StatusBadRequest, "key_unavailable", "当前 API Key 无法解密，请重新保存"
	case errors.Is(err, errAIOverloaded):
		return http.StatusTooManyRequests, "rate_limited", "当前生成任务较多，请稍后再试"
	default:
		return http.StatusBadGateway, "ai_error", "连接失败：" + err.Error()
	}
}

func (s *Server) handleGetAISettings(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	settings, err := s.getAISettings(c.Request().Context(), account.ID)
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取 AI 设置失败")
	}
	return c.JSON(http.StatusOK, aiSettingsDTO(settings))
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
	input := aiSettingsInput{Profiles: make([]aiProfileInput, 0, len(req.Profiles))}
	for _, profileReq := range req.Profiles {
		input.Profiles = append(input.Profiles, aiProfileInput{
			ID:          profileReq.ID,
			Name:        profileReq.Name,
			Provider:    profileReq.Provider,
			BaseURL:     profileReq.BaseURL,
			Model:       profileReq.Model,
			Temperature: profileReq.Temperature,
			MaxTokens:   profileReq.MaxTokens,
			Enabled:     profileReq.Enabled,
			Active:      profileReq.Active,
			AutoSummary: profileReq.AutoSummary,
			APIKey:      profileReq.APIKey,
		})
	}
	input.AutoSummary = req.AutoSummary
	settings, err := s.patchAISettings(c.Request().Context(), account.ID, input)
	if err != nil {
		if errors.Is(err, errValidation) {
			return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
		}
		return apiError(c, http.StatusInternalServerError, "internal", "保存 AI 设置失败")
	}
	return c.JSON(http.StatusOK, aiSettingsDTO(settings))
}

func aiSettingsDTO(settings *aiSettingsResult) map[string]any {
	if settings == nil {
		return map[string]any{"profiles": []map[string]any{}, "autoSummary": false}
	}
	return map[string]any{
		"profiles":    aiProfileDTOs(settings.Profiles),
		"autoSummary": settings.AutoSummary,
	}
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
		"autoSummary":    profile.AutoSummary,
		"createdAt":      time.UnixMilli(profile.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":      time.UnixMilli(profile.UpdatedAt).UTC().Format(time.RFC3339),
	}
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
		"inputTokens":   ai.InputTokens,
		"outputTokens":  ai.OutputTokens,
		"totalTokens":   ai.TotalTokens,
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
