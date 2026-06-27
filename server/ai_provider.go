package server

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/miofelix/sillage/internal/secret"
	"github.com/miofelix/sillage/store"
)

type aiProviderMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type aiCallResult struct {
	Content      string
	InputTokens  int64
	OutputTokens int64
	TotalTokens  int64
}

type openAIChatRequest struct {
	Model       string              `json:"model"`
	Messages    []aiProviderMessage `json:"messages"`
	Temperature float64             `json:"temperature,omitempty"`
	MaxTokens   int64               `json:"max_tokens,omitempty"`
}

type openAIChatResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
		Delta struct {
			Content string `json:"content"`
		} `json:"delta"`
	} `json:"choices"`
	Usage struct {
		InputTokens      int64 `json:"input_tokens"`
		OutputTokens     int64 `json:"output_tokens"`
		TotalTokens      int64 `json:"total_tokens"`
		PromptTokens     int64 `json:"prompt_tokens"`
		CompletionTokens int64 `json:"completion_tokens"`
	} `json:"usage"`
}

type anthropicMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type anthropicMessagesRequest struct {
	Model       string              `json:"model"`
	System      string              `json:"system,omitempty"`
	Messages    []anthropicMessage   `json:"messages"`
	Temperature float64             `json:"temperature,omitempty"`
	MaxTokens   int64               `json:"max_tokens"`
}

type anthropicMessagesResponse struct {
	Content []struct {
		Type string `json:"type"`
		Text string `json:"text"`
	} `json:"content"`
	Usage struct {
		InputTokens  int64 `json:"input_tokens"`
		OutputTokens int64 `json:"output_tokens"`
	} `json:"usage"`
}

func (s *Server) callAI(
	ctx context.Context,
	accountID string,
	profile *store.AIProfile,
	systemPrompt string,
	messages []aiProviderMessage,
	temperature float64,
	maxTokens int64,
) (*aiCallResult, error) {
	apiKey, err := s.resolveAIKey(ctx, accountID, profile)
	if err != nil {
		return nil, err
	}
	if maxTokens <= 0 {
		maxTokens = profile.MaxTokens
	}
	if temperature == 0 {
		temperature = profile.Temperature
	}
	baseURL, err := normalizeAIBaseURL(profile.BaseURL, profile.Provider)
	if err != nil {
		return nil, err
	}
	switch strings.ToLower(profile.Provider) {
	case "anthropic":
		return callAnthropicAI(ctx, baseURL, apiKey, profile.Model, systemPrompt, messages, temperature, maxTokens)
	default:
		return callOpenAICompatibleAI(ctx, baseURL, apiKey, profile.Model, systemPrompt, messages, temperature, maxTokens)
	}
}

func (s *Server) resolveAIKey(ctx context.Context, accountID string, profile *store.AIProfile) (string, error) {
	if profile == nil || !profile.APIKeyEnvelope.Valid {
		return "", errAINotConfigured
	}
	if s.Secrets == nil || s.Secrets.EncryptionSecret == "" {
		return "", errAINotConfigured
	}
	raw, err := secret.DecryptEnvelope(s.Secrets.EncryptionSecret, profile.APIKeyEnvelope.String)
	if err != nil {
		_ = s.Store.MarkAIProfileKeyUnavailable(ctx, accountID, profile.ID)
		return "", errAIKeyUnavailable
	}
	return raw, nil
}

func normalizeAIBaseURL(raw, provider string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		switch strings.ToLower(provider) {
		case "anthropic":
			raw = "https://api.anthropic.com/v1"
		default:
			raw = "https://api.openai.com/v1"
		}
	}
	parsed, err := url.Parse(raw)
	if err != nil {
		return "", fmt.Errorf("invalid ai base url: %w", err)
	}
	parsed.Fragment = ""
	parsed.RawQuery = ""
	return strings.TrimRight(parsed.String(), "/"), nil
}

func callOpenAICompatibleAI(
	ctx context.Context,
	baseURL, apiKey, model, systemPrompt string,
	messages []aiProviderMessage,
	temperature float64,
	maxTokens int64,
) (*aiCallResult, error) {
	reqMessages := make([]aiProviderMessage, 0, len(messages)+1)
	if systemPrompt != "" {
		reqMessages = append(reqMessages, aiProviderMessage{Role: "system", Content: systemPrompt})
	}
	reqMessages = append(reqMessages, messages...)
	reqBody := openAIChatRequest{
		Model:       model,
		Messages:    reqMessages,
		Temperature: temperature,
		MaxTokens:   maxTokens,
	}
	payload, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("marshal ai request: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, baseURL+"/chat/completions", bytes.NewReader(payload))
	if err != nil {
		return nil, fmt.Errorf("create ai request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	res, body, err := doAIRequest(req)
	if err != nil {
		return nil, err
	}
	if res.StatusCode < 200 || res.StatusCode >= 300 {
		return nil, fmt.Errorf("ai provider status %d: %s", res.StatusCode, strings.TrimSpace(string(body)))
	}
	var decoded openAIChatResponse
	if err := json.Unmarshal(body, &decoded); err != nil {
		return nil, fmt.Errorf("decode ai response: %w", err)
	}
	content := openAIContent(decoded)
	if content == "" {
		return nil, fmt.Errorf("ai provider returned empty content")
	}
	return &aiCallResult{
		Content:      strings.TrimSpace(content),
		InputTokens:  firstNonZero(decoded.Usage.InputTokens, decoded.Usage.PromptTokens),
		OutputTokens: firstNonZero(decoded.Usage.OutputTokens, decoded.Usage.CompletionTokens),
		TotalTokens:  firstNonZero(decoded.Usage.TotalTokens, decoded.Usage.InputTokens+decoded.Usage.OutputTokens, decoded.Usage.PromptTokens+decoded.Usage.CompletionTokens),
	}, nil
}

func callAnthropicAI(
	ctx context.Context,
	baseURL, apiKey, model, systemPrompt string,
	messages []aiProviderMessage,
	temperature float64,
	maxTokens int64,
) (*aiCallResult, error) {
	reqMessages := make([]anthropicMessage, 0, len(messages))
	for _, message := range messages {
		role := strings.ToLower(strings.TrimSpace(message.Role))
		if role != "user" && role != "assistant" {
			continue
		}
		reqMessages = append(reqMessages, anthropicMessage{Role: role, Content: message.Content})
	}
	reqBody := anthropicMessagesRequest{
		Model:       model,
		System:      systemPrompt,
		Messages:    reqMessages,
		Temperature: temperature,
		MaxTokens:   maxTokens,
	}
	payload, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("marshal ai request: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, baseURL+"/messages", bytes.NewReader(payload))
	if err != nil {
		return nil, fmt.Errorf("create ai request: %w", err)
	}
	req.Header.Set("x-api-key", apiKey)
	req.Header.Set("anthropic-version", "2023-06-01")
	req.Header.Set("Content-Type", "application/json")
	res, body, err := doAIRequest(req)
	if err != nil {
		return nil, err
	}
	if res.StatusCode < 200 || res.StatusCode >= 300 {
		return nil, fmt.Errorf("ai provider status %d: %s", res.StatusCode, strings.TrimSpace(string(body)))
	}
	var decoded anthropicMessagesResponse
	if err := json.Unmarshal(body, &decoded); err != nil {
		return nil, fmt.Errorf("decode ai response: %w", err)
	}
	content := anthropicContent(decoded)
	if content == "" {
		return nil, fmt.Errorf("ai provider returned empty content")
	}
	return &aiCallResult{
		Content:      strings.TrimSpace(content),
		InputTokens:  decoded.Usage.InputTokens,
		OutputTokens: decoded.Usage.OutputTokens,
		TotalTokens:  firstNonZero(decoded.Usage.InputTokens+decoded.Usage.OutputTokens, decoded.Usage.InputTokens, decoded.Usage.OutputTokens),
	}, nil
}

func doAIRequest(req *http.Request) (*http.Response, []byte, error) {
	client := &http.Client{Timeout: 60 * time.Second}
	res, err := client.Do(req)
	if err != nil {
		return nil, nil, fmt.Errorf("call ai provider: %w", err)
	}
	defer res.Body.Close()
	body, err := io.ReadAll(res.Body)
	if err != nil {
		return nil, nil, fmt.Errorf("read ai response: %w", err)
	}
	return res, body, nil
}

func openAIContent(decoded openAIChatResponse) string {
	for _, choice := range decoded.Choices {
		if choice.Message.Content != "" {
			return choice.Message.Content
		}
		if choice.Delta.Content != "" {
			return choice.Delta.Content
		}
	}
	return ""
}

func anthropicContent(decoded anthropicMessagesResponse) string {
	for _, block := range decoded.Content {
		if block.Type == "text" && block.Text != "" {
			return block.Text
		}
	}
	return ""
}

func firstNonZero(values ...int64) int64 {
	for _, value := range values {
		if value != 0 {
			return value
		}
	}
	return 0
}
