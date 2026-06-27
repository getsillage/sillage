package server

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"github.com/miofelix/sillage/store"
)

// streamingHTTPClient has no overall timeout (a long answer can take a while);
// cancellation is driven by the request context instead.
func streamingHTTPClient() *http.Client {
	return &http.Client{Timeout: 0}
}

// callAIStream is the streaming counterpart of callAI: it invokes the active
// provider with stream=true and reports each text delta through onDelta as it
// arrives, returning the accumulated result when the stream ends. Cancellation
// flows through ctx (closing the response body), so callers can stop mid-answer.
func (s *Server) callAIStream(
	ctx context.Context,
	accountID string,
	profile *store.AIProfile,
	systemPrompt string,
	messages []aiProviderMessage,
	temperature float64,
	maxTokens int64,
	onDelta func(string),
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
		return streamAnthropicAI(ctx, baseURL, apiKey, profile.Model, systemPrompt, messages, temperature, maxTokens, onDelta)
	default:
		return streamOpenAICompatibleAI(ctx, baseURL, apiKey, profile.Model, systemPrompt, messages, temperature, maxTokens, onDelta)
	}
}

func streamOpenAICompatibleAI(
	ctx context.Context,
	baseURL, apiKey, model, systemPrompt string,
	messages []aiProviderMessage,
	temperature float64,
	maxTokens int64,
	onDelta func(string),
) (*aiCallResult, error) {
	reqMessages := make([]aiProviderMessage, 0, len(messages)+1)
	if systemPrompt != "" {
		reqMessages = append(reqMessages, aiProviderMessage{Role: "system", Content: systemPrompt})
	}
	reqMessages = append(reqMessages, messages...)
	payload, err := json.Marshal(openAIChatRequest{
		Model:       model,
		Messages:    reqMessages,
		Temperature: temperature,
		MaxTokens:   maxTokens,
		Stream:      true,
	})
	if err != nil {
		return nil, fmt.Errorf("marshal ai request: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, baseURL+"/chat/completions", bytes.NewReader(payload))
	if err != nil {
		return nil, fmt.Errorf("create ai request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "text/event-stream")

	res, err := streamingHTTPClient().Do(req)
	if err != nil {
		return nil, fmt.Errorf("call ai provider: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode < 200 || res.StatusCode >= 300 {
		return nil, providerStatusError(res)
	}

	var builder strings.Builder
	err = scanSSE(res.Body, func(data string) bool {
		if data == "[DONE]" {
			return false
		}
		var chunk openAIChatResponse
		if json.Unmarshal([]byte(data), &chunk) != nil {
			return true
		}
		for _, choice := range chunk.Choices {
			if choice.Delta.Content != "" {
				builder.WriteString(choice.Delta.Content)
				onDelta(choice.Delta.Content)
			}
		}
		return true
	})
	if err != nil {
		return nil, err
	}
	return finishStream(builder.String())
}

type anthropicStreamEvent struct {
	Type  string `json:"type"`
	Delta struct {
		Type string `json:"type"`
		Text string `json:"text"`
	} `json:"delta"`
	Message struct {
		Usage struct {
			InputTokens int64 `json:"input_tokens"`
		} `json:"usage"`
	} `json:"message"`
	Usage struct {
		OutputTokens int64 `json:"output_tokens"`
	} `json:"usage"`
}

func streamAnthropicAI(
	ctx context.Context,
	baseURL, apiKey, model, systemPrompt string,
	messages []aiProviderMessage,
	temperature float64,
	maxTokens int64,
	onDelta func(string),
) (*aiCallResult, error) {
	reqMessages := make([]anthropicMessage, 0, len(messages))
	for _, message := range messages {
		role := strings.ToLower(strings.TrimSpace(message.Role))
		if role != "user" && role != "assistant" {
			continue
		}
		reqMessages = append(reqMessages, anthropicMessage{Role: role, Content: message.Content})
	}
	payload, err := json.Marshal(anthropicMessagesRequest{
		Model:       model,
		System:      systemPrompt,
		Messages:    reqMessages,
		Temperature: temperature,
		MaxTokens:   maxTokens,
		Stream:      true,
	})
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
	req.Header.Set("Accept", "text/event-stream")

	res, err := streamingHTTPClient().Do(req)
	if err != nil {
		return nil, fmt.Errorf("call ai provider: %w", err)
	}
	defer res.Body.Close()
	if res.StatusCode < 200 || res.StatusCode >= 300 {
		return nil, providerStatusError(res)
	}

	var builder strings.Builder
	var inputTokens, outputTokens int64
	err = scanSSE(res.Body, func(data string) bool {
		var event anthropicStreamEvent
		if json.Unmarshal([]byte(data), &event) != nil {
			return true
		}
		switch event.Type {
		case "content_block_delta":
			if event.Delta.Text != "" {
				builder.WriteString(event.Delta.Text)
				onDelta(event.Delta.Text)
			}
		case "message_start":
			if event.Message.Usage.InputTokens > 0 {
				inputTokens = event.Message.Usage.InputTokens
			}
		case "message_delta":
			if event.Usage.OutputTokens > 0 {
				outputTokens = event.Usage.OutputTokens
			}
		case "message_stop":
			return false
		}
		return true
	})
	if err != nil {
		return nil, err
	}
	result, err := finishStream(builder.String())
	if err != nil {
		return nil, err
	}
	result.InputTokens = inputTokens
	result.OutputTokens = outputTokens
	result.TotalTokens = inputTokens + outputTokens
	return result, nil
}

// scanSSE reads an event stream, invoking onData for each `data:` payload until
// onData returns false or the stream ends. Blank lines and non-data fields
// (event:, id:, comments) are ignored.
func scanSSE(body io.Reader, onData func(string) bool) error {
	scanner := bufio.NewScanner(body)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		line := scanner.Text()
		if !strings.HasPrefix(line, "data:") {
			continue
		}
		data := strings.TrimSpace(strings.TrimPrefix(line, "data:"))
		if data == "" {
			continue
		}
		if !onData(data) {
			return nil
		}
	}
	if err := scanner.Err(); err != nil {
		return fmt.Errorf("read ai stream: %w", err)
	}
	return nil
}

func finishStream(content string) (*aiCallResult, error) {
	trimmed := strings.TrimSpace(content)
	if trimmed == "" {
		return nil, fmt.Errorf("ai provider returned empty content")
	}
	return &aiCallResult{Content: trimmed}, nil
}

func providerStatusError(res *http.Response) error {
	body, _ := io.ReadAll(io.LimitReader(res.Body, 4096))
	return fmt.Errorf("ai provider status %d: %s", res.StatusCode, strings.TrimSpace(string(body)))
}
