package server

import (
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/getsillage/sillage/internal/secret"
	"github.com/getsillage/sillage/server/auth"
	memoapp "github.com/getsillage/sillage/server/memo"
	"github.com/getsillage/sillage/store"
)

var (
	errAIKeyUnavailable = errors.New("ai key unavailable")
	errAINotConfigured  = errors.New("ai not configured")
	errAIOverloaded     = errors.New("ai overloaded")
	errTooManyChanges   = errors.New("too many sync changes")
	errValidation       = errors.New("validation error")
)

type validationError struct {
	message string
}

type authInput struct {
	Username    string
	DisplayName string
	Password    string
}

func (s *Server) authBootstrap(ctx context.Context) (bool, error) {
	return s.auth.HasAccount(ctx)
}

func (s *Server) initializeAccount(ctx context.Context, input authInput, r *http.Request) (*store.Account, *auth.TokenPair, error) {
	input.Username = strings.TrimSpace(input.Username)
	if input.Username == "" || input.Password == "" {
		return nil, nil, validationError{message: "账号和密码不能为空"}
	}
	return s.auth.Initialize(ctx, input.Username, input.DisplayName, input.Password, r)
}

func (s *Server) signIn(ctx context.Context, input authInput, r *http.Request) (*store.Account, *auth.TokenPair, error) {
	return s.auth.SignIn(ctx, input.Username, input.Password, r)
}

func (s *Server) refreshAuth(ctx context.Context, refreshToken string, r *http.Request) (*store.Account, *auth.TokenPair, error) {
	return s.auth.Refresh(ctx, refreshToken, r)
}

func (s *Server) signOut(ctx context.Context, refreshToken string) error {
	return s.auth.SignOut(ctx, refreshToken)
}

func (e validationError) Error() string {
	return e.message
}

func (e validationError) Unwrap() error {
	return errValidation
}

func isValidationError(err error) bool {
	return errors.Is(err, errValidation) || errors.Is(err, memoapp.ErrValidation)
}

type aiSettingsInput struct {
	Profiles    []aiProfileInput
	AutoSummary *bool
}

type aiProfileInput struct {
	ID       string
	Name     string
	Provider string
	BaseURL  string
	Model    string
	// Temperature and MaxTokens are pointers so an explicit 0 (deterministic
	// output) is distinguishable from an omitted field that should take the
	// default. A nil pointer means "use the default".
	Temperature *float64
	MaxTokens   *int64
	Enabled     bool
	Active      bool
	AutoSummary bool
	APIKey      *string
}

type aiModelsInput struct {
	ID       string
	Provider string
	BaseURL  string
	APIKey   *string
}

type aiTestInput struct {
	ID          string
	Provider    string
	BaseURL     string
	Model       string
	Temperature *float64
	MaxTokens   *int64
	APIKey      *string
}

type aiSettingsResult struct {
	Profiles    []*store.AIProfile
	AutoSummary bool
}

type askConversationInput struct {
	Title        string
	ContextScope string
}

type askConversationListInput struct {
	Limit    int
	Query    string
	Archived bool
}

type askMessageInput struct {
	ConversationID string
	Content        string
	ContextScope   string
	ParentID       string
	ForkOfID       string
	SourceKind     string
}

type askCreateMessageResult struct {
	Messages []*store.AskMessage
}

func (s *Server) listAskConversations(ctx context.Context, accountID string, input askConversationListInput) ([]*store.AskConversation, error) {
	return s.Store.ListAskConversations(ctx, &store.ListAskConversationOptions{
		AccountID: accountID,
		Limit:     normalizeLimit(input.Limit, 50),
		Query:     input.Query,
		Archived:  input.Archived,
	})
}

func (s *Server) createAskConversation(ctx context.Context, accountID string, input askConversationInput) (*store.AskConversation, error) {
	return s.Store.CreateAskConversation(ctx, accountID, input.Title, input.ContextScope)
}

func (s *Server) getAskConversation(ctx context.Context, accountID, conversationID string) (*store.AskConversation, error) {
	return s.Store.GetAskConversation(ctx, accountID, conversationID)
}

func (s *Server) setAskConversationArchived(ctx context.Context, accountID, conversationID string, archived bool) (*store.AskConversation, error) {
	return s.Store.SetAskConversationArchived(ctx, accountID, conversationID, archived)
}

func (s *Server) listAskMessages(ctx context.Context, accountID, conversationID string) ([]*store.AskMessage, error) {
	conversation, err := s.Store.GetAskConversation(ctx, accountID, conversationID)
	if err != nil {
		return nil, err
	}
	return s.Store.ListAskMessages(ctx, conversation.ID)
}

// askTurn is the resolved question for a new answer. newUser is non-nil only
// when a fresh user message was created (a normal turn); forkOfID is set when
// regenerating, so the new answer becomes a sibling of an existing one.
type askTurn struct {
	question *store.AskMessage
	newUser  *store.AskMessage
	forkOfID string
}

// resolveAskTurn figures out which question a new answer responds to. A
// regenerate (forkOfId set, empty content) reuses the forked answer's question;
// a normal turn creates a user message under the requested parent (or the head).
func (s *Server) resolveAskTurn(ctx context.Context, conv *store.AskConversation, input askMessageInput) (*askTurn, error) {
	if input.ForkOfID != "" && strings.TrimSpace(input.Content) == "" {
		fork, err := s.Store.GetAskMessage(ctx, input.ForkOfID)
		if err != nil {
			return nil, err
		}
		if fork.ConversationID != conv.ID || fork.Role != "assistant" || !fork.ParentID.Valid {
			return nil, validationError{message: "无法重新生成该回答"}
		}
		question, err := s.Store.GetAskMessage(ctx, fork.ParentID.String)
		if err != nil {
			return nil, err
		}
		return &askTurn{question: question, forkOfID: fork.ID}, nil
	}

	content := strings.TrimSpace(input.Content)
	if content == "" {
		return nil, validationError{message: "问题不能为空"}
	}
	parentID := input.ParentID
	if parentID == "" && conv.HeadMessageID.Valid {
		parentID = conv.HeadMessageID.String
	}
	user, err := s.Store.CreateAskMessage(ctx, &store.AskMessage{
		ConversationID: conv.ID,
		Role:           "user",
		Content:        content,
		ParentID:       sql.NullString{String: parentID, Valid: parentID != ""},
		Status:         "complete",
		SourceRefs:     "[]",
	})
	if err != nil {
		return nil, err
	}
	return &askTurn{question: user, newUser: user}, nil
}

func (s *Server) createAskMessage(ctx context.Context, accountID string, input askMessageInput) (*askCreateMessageResult, error) {
	conversation, err := s.Store.GetAskConversation(ctx, accountID, input.ConversationID)
	if err != nil {
		return nil, err
	}
	turn, err := s.resolveAskTurn(ctx, conversation, input)
	if err != nil {
		return nil, err
	}

	scope := firstNonEmpty(input.ContextScope, conversation.ContextScope)
	sources, answer, modelName, err := s.answerFromMemos(ctx, accountID, turn.question.Content, scope, input.SourceKind, conversation.ID, nullStringValue(turn.question.ParentID))
	if err != nil {
		return nil, err
	}
	assistantMessage, err := s.Store.CreateAskMessage(ctx, &store.AskMessage{
		ConversationID: conversation.ID,
		Role:           "assistant",
		Content:        answer,
		ParentID:       sql.NullString{String: turn.question.ID, Valid: true},
		ForkOfID:       sql.NullString{String: turn.forkOfID, Valid: turn.forkOfID != ""},
		Status:         "complete",
		SourceRefs:     encodeAskSourceRefs(sources),
		Model:          modelName,
		PromptVersion:  askPromptVersion,
	})
	if err != nil {
		return nil, err
	}
	messages := make([]*store.AskMessage, 0, 2)
	if turn.newUser != nil {
		messages = append(messages, turn.newUser)
	}
	messages = append(messages, assistantMessage)
	return &askCreateMessageResult{Messages: messages}, nil
}

func (s *Server) getAISettings(ctx context.Context, accountID string) (*aiSettingsResult, error) {
	profiles, err := s.Store.ListAIProfiles(ctx, accountID)
	if err != nil {
		return nil, err
	}
	autoSummary, err := s.getGlobalAutoSummary(ctx, accountID, profiles)
	if err != nil {
		return nil, err
	}
	return &aiSettingsResult{Profiles: profiles, AutoSummary: autoSummary}, nil
}

func (s *Server) patchAISettings(ctx context.Context, accountID string, input aiSettingsInput) (*aiSettingsResult, error) {
	if err := s.Store.EnsureCompatSchema(ctx); err != nil {
		return nil, err
	}
	input.Profiles = normalizeAIProfileInputs(input.Profiles)
	profiles := make([]*store.AIProfile, 0, len(input.Profiles))
	for _, profileReq := range input.Profiles {
		if profileReq.Name == "" || profileReq.Provider == "" {
			return nil, validationError{message: "AI 档案名称和 provider 不能为空"}
		}
		if profileReq.ID != "" {
			deleted, err := s.Store.AIProfileDeleted(ctx, accountID, profileReq.ID)
			if err != nil {
				return nil, err
			}
			if deleted {
				return nil, validationError{message: "AI 档案已被删除，请刷新设置后重试"}
			}
		}
	}
	autoSummaryToSet, shouldSetAutoSummary := resolvePatchAutoSummary(input)
	keepIDs := make([]string, 0, len(input.Profiles))
	for _, profileReq := range input.Profiles {
		var envelope *string
		if profileReq.APIKey != nil {
			raw, err := secret.EncryptEnvelope(s.Secrets.EncryptionSecret, *profileReq.APIKey)
			if err != nil {
				return nil, err
			}
			envelope = &raw
		}
		maxTokens := int64(1000)
		if profileReq.MaxTokens != nil && *profileReq.MaxTokens > 0 {
			maxTokens = *profileReq.MaxTokens
		}
		temperature := defaultAITemperature
		if profileReq.Temperature != nil {
			temperature = clampAITemperature(*profileReq.Temperature)
		}
		profile, err := s.Store.UpsertAIProfile(ctx, &store.UpsertAIProfile{
			ID:             profileReq.ID,
			AccountID:      accountID,
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
			AutoSummary:    false,
		})
		if err != nil {
			return nil, err
		}
		profiles = append(profiles, profile)
		keepIDs = append(keepIDs, profile.ID)
	}
	if err := s.Store.DeleteAIProfilesExcept(ctx, accountID, keepIDs); err != nil {
		return nil, err
	}
	if shouldSetAutoSummary {
		if err := s.setGlobalAutoSummary(ctx, accountID, autoSummaryToSet); err != nil {
			return nil, err
		}
	}
	autoSummary, err := s.getGlobalAutoSummary(ctx, accountID, profiles)
	if err != nil {
		return nil, err
	}
	return &aiSettingsResult{Profiles: profiles, AutoSummary: autoSummary}, nil
}

func normalizeAIProfileInputs(profiles []aiProfileInput) []aiProfileInput {
	if len(profiles) == 0 {
		return profiles
	}
	activeIndex := -1
	for i, profile := range profiles {
		if profile.Active && activeIndex < 0 {
			activeIndex = i
		}
	}
	if activeIndex < 0 {
		activeIndex = 0
	}
	normalized := make([]aiProfileInput, len(profiles))
	for i, profile := range profiles {
		profile.Enabled = true
		profile.Active = i == activeIndex
		normalized[i] = profile
	}
	return normalized
}

const aiAutoSummarySettingKey = "ai.auto_summary"

func resolvePatchAutoSummary(input aiSettingsInput) (bool, bool) {
	if input.AutoSummary != nil {
		return *input.AutoSummary, true
	}
	for _, profileReq := range input.Profiles {
		if profileReq.AutoSummary {
			return true, true
		}
	}
	return false, false
}

func (s *Server) getGlobalAutoSummary(ctx context.Context, accountID string, profiles []*store.AIProfile) (bool, error) {
	value, ok, err := s.Store.GetAccountSetting(ctx, accountID, aiAutoSummarySettingKey)
	if err != nil {
		return false, err
	}
	if ok {
		return value == "true", nil
	}
	for _, profile := range profiles {
		if profile != nil && profile.AutoSummary {
			return true, nil
		}
	}
	return false, nil
}

func (s *Server) setGlobalAutoSummary(ctx context.Context, accountID string, enabled bool) error {
	value := "false"
	if enabled {
		value = "true"
	}
	return s.Store.PutAccountSetting(ctx, accountID, aiAutoSummarySettingKey, value)
}

func (s *Server) setAIAutoSummary(ctx context.Context, accountID string, enabled bool) (bool, error) {
	if err := s.setGlobalAutoSummary(ctx, accountID, enabled); err != nil {
		return false, err
	}
	return enabled, nil
}

// testAIConnection makes a minimal call against a saved profile or an unsaved
// draft to verify the key, base URL, and model actually work.
func (s *Server) testAIConnection(ctx context.Context, accountID string, input aiTestInput) (string, error) {
	ctx, cancel := context.WithTimeout(ctx, 65*time.Second)
	defer cancel()

	profile, err := s.aiProfileForConnectionTest(ctx, accountID, input)
	if err != nil {
		return "", err
	}
	jobDone, err := s.acquireMemoAIJob()
	if err != nil {
		return "", err
	}
	defer jobDone()
	if _, err := s.callAI(ctx, accountID, profile,
		"你是连接测试助手。",
		[]aiProviderMessage{{Role: "user", Content: "请只回复 ok。"}},
		profile.Temperature, 16,
	); err != nil {
		return "", err
	}
	return profile.Model, nil
}

func (s *Server) aiProfileForConnectionTest(ctx context.Context, accountID string, input aiTestInput) (*store.AIProfile, error) {
	var profile *store.AIProfile
	if input.ID != "" {
		saved, err := s.Store.GetAIProfile(ctx, accountID, input.ID)
		if err != nil {
			return nil, err
		}
		copy := *saved
		profile = &copy
	} else {
		profile = &store.AIProfile{
			ID:          "draft",
			AccountID:   accountID,
			Provider:    "openai",
			Temperature: 0.3,
			MaxTokens:   1000,
			Enabled:     true,
		}
	}

	if strings.TrimSpace(input.Provider) != "" {
		profile.Provider = strings.TrimSpace(input.Provider)
	}
	if strings.TrimSpace(input.BaseURL) != "" {
		profile.BaseURL = strings.TrimSpace(input.BaseURL)
	}
	if strings.TrimSpace(input.Model) != "" {
		profile.Model = strings.TrimSpace(input.Model)
	}
	if input.Temperature != nil {
		profile.Temperature = clampAITemperature(*input.Temperature)
	}
	if input.MaxTokens != nil && *input.MaxTokens > 0 {
		profile.MaxTokens = *input.MaxTokens
	}
	if input.APIKey != nil {
		apiKey := strings.TrimSpace(*input.APIKey)
		if apiKey != "" {
			envelope, err := secret.EncryptEnvelope(s.Secrets.EncryptionSecret, apiKey)
			if err != nil {
				return nil, err
			}
			profile.APIKeyEnvelope = sql.NullString{String: envelope, Valid: true}
			profile.KeyUnavailable = false
		}
	}
	if input.ID == "" && !profile.APIKeyEnvelope.Valid {
		return nil, errAINotConfigured
	}
	if strings.TrimSpace(profile.Model) == "" {
		return nil, validationError{message: "模型不能为空"}
	}
	return profile, nil
}

func (s *Server) listAIModels(ctx context.Context, accountID string, input aiModelsInput) ([]string, error) {
	ctx, cancel := context.WithTimeout(ctx, 65*time.Second)
	defer cancel()

	provider := strings.TrimSpace(input.Provider)
	baseURL := strings.TrimSpace(input.BaseURL)
	apiKey := ""
	if input.APIKey != nil {
		apiKey = strings.TrimSpace(*input.APIKey)
	}

	if input.ID != "" && (provider == "" || baseURL == "" || apiKey == "") {
		profile, err := s.Store.GetAIProfile(ctx, accountID, input.ID)
		if err != nil {
			return nil, err
		}
		if provider == "" {
			provider = profile.Provider
		}
		if baseURL == "" {
			baseURL = profile.BaseURL
		}
		if apiKey == "" {
			resolved, err := s.resolveAIKey(ctx, accountID, profile)
			if err != nil {
				return nil, err
			}
			apiKey = resolved
		}
	}

	if provider == "" {
		provider = "openai"
	}
	if apiKey == "" {
		return nil, errAINotConfigured
	}
	normalizedBaseURL, err := normalizeAIBaseURL(baseURL, provider)
	if err != nil {
		return nil, validationError{message: "Base URL 格式不正确"}
	}
	jobDone, err := s.acquireMemoAIJob()
	if err != nil {
		return nil, err
	}
	defer jobDone()
	return fetchAIModels(ctx, provider, normalizedBaseURL, apiKey)
}

func (s *Server) getAttachment(ctx context.Context, accountID, uid string) (*store.Attachment, error) {
	return s.Store.GetAttachmentByUID(ctx, accountID, uid, false)
}

// maybeScheduleAutoSummary generates a summary in the background when the global
// auto summary setting is on. It is best-effort: it runs off the request
// path with its own timeout and never blocks or fails the memo write.
func (s *Server) maybeScheduleAutoSummary(accountID, memoID string) {
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
		defer cancel()
		profiles, err := s.Store.ListAIProfiles(ctx, accountID)
		if err != nil {
			return
		}
		autoSummary, err := s.getGlobalAutoSummary(ctx, accountID, profiles)
		if err != nil || !autoSummary {
			return
		}
		profile, err := pickActiveAIProfile(profiles)
		if err != nil || profile == nil {
			return
		}
		if _, err := s.generateMemoSummary(ctx, accountID, memoID); err != nil {
			slog.Warn("auto summary failed", "memo", memoID, "error", err)
		}
	}()
}

func (s *Server) generateMemoSummary(ctx context.Context, accountID, id string) (*store.MemoAI, error) {
	memo, err := s.Store.GetMemo(ctx, accountID, id, false)
	if err != nil {
		return nil, err
	}
	profiles, err := s.Store.ListAIProfiles(ctx, accountID)
	if err != nil {
		return nil, err
	}
	profile, err := pickActiveAIProfile(profiles)
	if err != nil {
		return nil, err
	}
	jobDone, err := s.acquireMemoAIJob()
	if err != nil {
		return nil, err
	}
	defer jobDone()
	result, err := s.callAI(ctx, accountID, profile, memoSummarySystemPrompt(), []aiProviderMessage{
		{Role: "user", Content: memoSummaryUserPrompt(memo.Content)},
	}, profile.Temperature, profile.MaxTokens)
	if err != nil {
		return nil, err
	}
	ai, err := s.Store.UpsertMemoAI(ctx, &store.UpsertMemoAI{
		MemoID:        memo.ID,
		Summary:       result.Content,
		Sentiment:     "",
		Provider:      profile.Provider,
		Model:         profile.Model,
		ProfileID:     profile.ID,
		PromptVersion: "memo-summary-v2",
		SourceMemoIDs: fmt.Sprintf("[\"%s\"]", memo.ID),
		Status:        "complete",
		InputTokens:   result.InputTokens,
		OutputTokens:  result.OutputTokens,
		TotalTokens:   result.TotalTokens,
	})
	if err != nil {
		return nil, err
	}
	return ai, nil
}

type syncPushRequest struct {
	Changes []syncChange `json:"changes"`
}

type syncChange struct {
	MutationID     string           `json:"mutationId"`
	ResourceType   string           `json:"resourceType"`
	ResourceID     string           `json:"resourceId"`
	Action         string           `json:"action"`
	BaseVersion    int64            `json:"baseVersion"`
	LocalChangedAt string           `json:"localChangedAt"`
	Memo           *syncMemoPayload `json:"memo,omitempty"`
}

type syncMemoPayload struct {
	ID        string `json:"id"`
	Content   string `json:"content"`
	EntryDate string `json:"entryDate"`
	Pinned    *bool  `json:"pinned"`
	Archived  *bool  `json:"archived"`
	Favorited *bool  `json:"favorited"`
}

func (p syncMemoPayload) favoritedValue() *bool {
	if p.Favorited != nil {
		return p.Favorited
	}
	return p.Pinned
}

type syncPullResult struct {
	Memos            []*store.Memo
	Attachments      []*store.Attachment
	MemoAI           []*store.MemoAI
	AskConversations []*store.AskConversation
	AskMessages      []*store.AskMessage
	Cursor           string
	NextCursor       string
	HasMore          bool
}

type syncResult struct {
	MutationID     string      `json:"mutationId"`
	ResourceType   string      `json:"resourceType"`
	ResourceID     string      `json:"resourceId"`
	Status         string      `json:"status"`
	Reason         string      `json:"reason,omitempty"`
	Message        string      `json:"message,omitempty"`
	Idempotent     bool        `json:"idempotent,omitempty"`
	Resource       *store.Memo `json:"-"`
	ServerResource *store.Memo `json:"-"`
	ClientVersion  int64       `json:"clientVersion,omitempty"`
	ServerVersion  int64       `json:"serverVersion,omitempty"`
}

func (s *Server) pullSync(ctx context.Context, accountID, rawCursor string, limit int) (*syncPullResult, error) {
	cursor := decodeSyncCursor(rawCursor)
	limit = normalizeSyncPageLimit(limit)

	memos, err := s.Store.ListMemos(ctx, &store.ListMemoOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		IncludeDeleted:    true,
		Sync:              true,
		UpdatedAfter:      cursor.Memo.UpdatedAt,
		UpdatedAfterID:    cursor.Memo.ID,
	})
	if err != nil {
		return nil, err
	}
	attachments, err := s.Store.ListAttachments(ctx, &store.ListAttachmentOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		IncludeDeleted:    true,
		UpdatedAfter:      cursor.Attachment.UpdatedAt,
		UpdatedAfterID:    cursor.Attachment.ID,
	})
	if err != nil {
		return nil, err
	}
	memoAI, err := s.Store.ListMemoAI(ctx, &store.ListMemoAIOptions{
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		UpdatedAfter:      cursor.MemoAI.UpdatedAt,
		UpdatedAfterID:    cursor.MemoAI.ID,
	})
	if err != nil {
		return nil, err
	}
	askConversations, err := s.Store.ListAskConversationsForSync(ctx, &store.ListAskSyncOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		UpdatedAfter:      cursor.AskConversation.UpdatedAt,
		UpdatedAfterID:    cursor.AskConversation.ID,
	})
	if err != nil {
		return nil, err
	}
	askMessages, err := s.Store.ListAskMessagesForSync(ctx, &store.ListAskSyncOptions{
		AccountID:         accountID,
		Limit:             limit + 1,
		LookaheadPageSize: limit,
		UpdatedAfter:      cursor.AskMessage.UpdatedAt,
		UpdatedAfterID:    cursor.AskMessage.ID,
	})
	if err != nil {
		return nil, err
	}

	memoHasMore := len(memos) > limit
	if memoHasMore {
		memos = memos[:limit]
	}
	attachmentHasMore := len(attachments) > limit
	if attachmentHasMore {
		attachments = attachments[:limit]
	}
	memoAIHasMore := len(memoAI) > limit
	if memoAIHasMore {
		memoAI = memoAI[:limit]
	}
	askConversationHasMore := len(askConversations) > limit
	if askConversationHasMore {
		askConversations = askConversations[:limit]
	}
	askMessageHasMore := len(askMessages) > limit
	if askMessageHasMore {
		askMessages = askMessages[:limit]
	}

	if len(memos) > 0 {
		last := memos[len(memos)-1]
		cursor.Memo = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(attachments) > 0 {
		last := attachments[len(attachments)-1]
		cursor.Attachment = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(memoAI) > 0 {
		last := memoAI[len(memoAI)-1]
		cursor.MemoAI = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.MemoID}
	}
	if len(askConversations) > 0 {
		last := askConversations[len(askConversations)-1]
		cursor.AskConversation = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}
	if len(askMessages) > 0 {
		last := askMessages[len(askMessages)-1]
		cursor.AskMessage = store.SyncCursorPosition{UpdatedAt: last.UpdatedAt, ID: last.ID}
	}

	encodedCursor := encodeSyncCursor(cursor)
	return &syncPullResult{
		Memos:            memos,
		Attachments:      attachments,
		MemoAI:           memoAI,
		AskConversations: askConversations,
		AskMessages:      askMessages,
		Cursor:           encodedCursor,
		NextCursor:       encodedCursor,
		HasMore:          memoHasMore || attachmentHasMore || memoAIHasMore || askConversationHasMore || askMessageHasMore,
	}, nil
}

func (s *Server) pushSync(ctx context.Context, accountID string, changes []syncChange) ([]syncResult, error) {
	if len(changes) > 200 {
		return nil, errTooManyChanges
	}
	results := make([]syncResult, 0, len(changes))
	for _, change := range changes {
		results = append(results, s.applySyncChange(ctx, accountID, change))
	}
	return results, nil
}

func (s *Server) applySyncChange(ctx context.Context, accountID string, change syncChange) syncResult {
	if change.MutationID == "" {
		return syncRejected(change, "missing_mutation_id", "mutationId 不能为空")
	}
	if previous, ok, err := s.Store.GetSyncMutation(ctx, accountID, change.MutationID); err == nil && ok {
		result, err := s.storedSyncResult(ctx, accountID, previous)
		if err == nil {
			return result
		}
		return syncRejected(change, "internal", "读取幂等状态失败")
	} else if err != nil {
		return syncRejected(change, "internal", "读取幂等状态失败")
	}
	if change.ResourceType != "memo" {
		return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "unsupported_resource", "暂不支持该资源类型"))
	}

	payload := syncMemoPayload{}
	if change.Memo != nil {
		payload = *change.Memo
	}
	if payload.ID == "" {
		payload.ID = change.ResourceID
	}

	var memo *store.Memo
	var err error
	favorited := payload.favoritedValue()
	switch change.Action {
	case "create":
		memo, err = s.memos.Create(ctx, accountID, memoapp.CreateInput{
			ID:        payload.ID,
			Content:   payload.Content,
			EntryDate: payload.EntryDate,
			Favorited: favorited != nil && *favorited,
			Archived:  payload.Archived != nil && *payload.Archived,
		})
	case "update":
		if change.BaseVersion <= 0 {
			return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "missing_base_version", "baseVersion 必须大于 0"))
		}
		if validateErr := memoapp.ValidateFields(payload.Content, payload.EntryDate); validateErr != nil {
			return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "invalid_field", validateErr.Error()))
		}
		memo, err = s.memos.Update(ctx, accountID, memoapp.UpdateInput{
			ID:              payload.ID,
			ExpectedVersion: change.BaseVersion,
			Content:         &payload.Content,
			EntryDate:       &payload.EntryDate,
			Favorited:       favorited,
			Archived:        payload.Archived,
		})
	case "delete":
		if change.BaseVersion <= 0 {
			return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "missing_base_version", "baseVersion 必须大于 0"))
		}
		memo, err = s.memos.Delete(ctx, accountID, payload.ID, change.BaseVersion)
	default:
		return s.finishSyncChange(ctx, accountID, change, syncRejected(change, "unsupported_action", "暂不支持该同步动作"))
	}

	var conflict *store.MemoConflictError
	var result syncResult
	switch {
	case errors.As(err, &conflict):
		result = syncConflict(change, conflict.ServerMemo)
	case isValidationError(err):
		result = syncRejected(change, "invalid_field", err.Error())
	case err != nil:
		result = syncRejected(change, "rejected", err.Error())
	default:
		result = syncApplied(change, memo)
	}
	return s.finishSyncChange(ctx, accountID, change, result)
}

// finishSyncChange persists the per-mutation result for idempotency and returns
// it. If persistence fails we must NOT report the change as applied: the client
// would receive success yet a retry of the same mutationId would re-execute it.
// We surface an internal rejection so the client retries the whole change.
func (s *Server) finishSyncChange(ctx context.Context, accountID string, change syncChange, result syncResult) syncResult {
	if err := s.persistSyncResult(ctx, accountID, change, result); err != nil {
		return syncRejected(change, "internal", "保存同步状态失败，请重试")
	}
	return result
}

func (s *Server) storedSyncResult(ctx context.Context, accountID string, mutation *store.SyncMutation) (syncResult, error) {
	var result syncResult
	if err := json.Unmarshal([]byte(mutation.Result), &result); err != nil {
		return syncResult{}, err
	}
	result.Idempotent = true
	if result.ResourceType == "memo" && result.ResourceID != "" {
		switch result.Status {
		case "applied":
			memo, err := s.Store.GetMemo(ctx, accountID, result.ResourceID, true)
			if err == nil {
				result.Resource = memo
			}
		case "conflict":
			memo, err := s.Store.GetMemo(ctx, accountID, result.ResourceID, true)
			if err == nil {
				result.ServerResource = memo
			}
		}
	}
	return result, nil
}

func (s *Server) persistSyncResult(ctx context.Context, accountID string, change syncChange, result syncResult) error {
	payload, err := json.Marshal(result)
	if err != nil {
		return fmt.Errorf("marshal sync result: %w", err)
	}
	if err := s.Store.PutSyncMutation(ctx, &store.SyncMutation{
		AccountID:    accountID,
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   result.ResourceID,
		Result:       string(payload),
	}); err != nil {
		return fmt.Errorf("persist sync mutation: %w", err)
	}
	return nil
}

func syncApplied(change syncChange, memo *store.Memo) syncResult {
	resourceID := change.ResourceID
	if memo != nil {
		resourceID = memo.ID
	}
	return syncResult{
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   resourceID,
		Status:       "applied",
		Resource:     memo,
	}
}

func syncConflict(change syncChange, memo *store.Memo) syncResult {
	serverVersion := int64(0)
	resourceID := change.ResourceID
	if memo != nil {
		serverVersion = memo.Version
		resourceID = memo.ID
	}
	return syncResult{
		MutationID:     change.MutationID,
		ResourceType:   change.ResourceType,
		ResourceID:     resourceID,
		Status:         "conflict",
		Reason:         "version_conflict",
		ClientVersion:  change.BaseVersion,
		ServerVersion:  serverVersion,
		ServerResource: memo,
	}
}

func syncRejected(change syncChange, reason, message string) syncResult {
	return syncResult{
		MutationID:   change.MutationID,
		ResourceType: change.ResourceType,
		ResourceID:   change.ResourceID,
		Status:       "rejected",
		Reason:       reason,
		Message:      message,
	}
}

type syncCursor struct {
	Memo            store.SyncCursorPosition `json:"memo"`
	Attachment      store.SyncCursorPosition `json:"attachment"`
	MemoAI          store.SyncCursorPosition `json:"memoAi"`
	AskConversation store.SyncCursorPosition `json:"askConversation"`
	AskMessage      store.SyncCursorPosition `json:"askMessage"`
}

func decodeSyncCursor(raw string) syncCursor {
	if raw == "" {
		return syncCursor{}
	}
	payload, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return syncCursor{}
	}
	var cursor syncCursor
	if err := json.Unmarshal(payload, &cursor); err != nil {
		return syncCursor{}
	}
	return cursor
}

func encodeSyncCursor(cursor syncCursor) string {
	payload, err := json.Marshal(cursor)
	if err != nil {
		return ""
	}
	return base64.RawURLEncoding.EncodeToString(payload)
}

func normalizeLimit(limit, fallback int) int {
	if limit <= 0 {
		return fallback
	}
	if limit > store.MaxMemoListLimit {
		return store.MaxMemoListLimit
	}
	return limit
}

func normalizeSyncPageLimit(limit int) int {
	if limit <= 0 || limit > store.MaxSyncPageLimit {
		return store.MaxSyncPageLimit
	}
	return limit
}

func memoHTTPStatus(err error) (int, string, string) {
	var conflict *store.MemoConflictError
	switch {
	case isValidationError(err):
		return 400, "invalid_field", err.Error()
	case errors.Is(err, errAINotConfigured):
		return 400, "ai_not_configured", "请先配置一个默认 AI 档案"
	case errors.Is(err, errAIOverloaded):
		return 429, "rate_limited", "当前生成任务较多，请稍后再试"
	case errors.As(err, &conflict):
		return 409, "version_conflict", "记录已被其他修改更新"
	case errors.Is(err, sql.ErrNoRows):
		return 404, "not_found", "记录不存在"
	case errors.Is(err, errAIKeyUnavailable):
		return 400, "key_unavailable", "当前 AI API Key 无法解密，请重新保存"
	default:
		return 500, "internal", "保存记录失败"
	}
}
