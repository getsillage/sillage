package server

import (
	"database/sql"
	"encoding/json"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
	"github.com/getsillage/sillage/store"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func memoPB(memo *store.Memo) *apiv1.Memo {
	if memo == nil {
		return nil
	}
	return &apiv1.Memo{
		Id:            memo.ID,
		Content:       memo.Content,
		EntryDate:     memo.EntryDate,
		Version:       memo.Version,
		FavoritedTime: timestampPB(memo.FavoritedAt),
		ArchivedTime:  timestampPB(memo.ArchivedAt),
		CreatedTime:   timestamppb.New(unixMilliTime(memo.CreatedAt)),
		UpdatedTime:   timestamppb.New(unixMilliTime(memo.UpdatedAt)),
		DeletedTime:   timestampPB(memo.DeletedAt),
	}
}

func accountPB(account *store.Account) *apiv1.Account {
	if account == nil {
		return nil
	}
	return &apiv1.Account{
		Id:          account.ID,
		Username:    account.Username,
		DisplayName: account.DisplayName,
		CreatedTime: timestamppb.New(unixMilliTime(account.CreatedAt)),
		UpdatedTime: timestamppb.New(unixMilliTime(account.UpdatedAt)),
	}
}

func aiProfilePB(profile *store.AIProfile) *apiv1.AIProfile {
	if profile == nil {
		return nil
	}
	return &apiv1.AIProfile{
		Id:             profile.ID,
		Name:           profile.Name,
		Provider:       profile.Provider,
		BaseUrl:        profile.BaseURL,
		Model:          profile.Model,
		Temperature:    profile.Temperature,
		MaxTokens:      profile.MaxTokens,
		Enabled:        profile.Enabled,
		Active:         profile.Active,
		HasApiKey:      profile.APIKeyEnvelope.Valid,
		KeyUnavailable: profile.KeyUnavailable,
		AutoSummary:    profile.AutoSummary,
		CreatedTime:    timestamppb.New(unixMilliTime(profile.CreatedAt)),
		UpdatedTime:    timestamppb.New(unixMilliTime(profile.UpdatedAt)),
	}
}

func aiSettingsResponsePB(settings *aiSettingsResult) *apiv1.AISettingsResponse {
	if settings == nil {
		return &apiv1.AISettingsResponse{}
	}
	res := &apiv1.AISettingsResponse{
		Profiles:    make([]*apiv1.AIProfile, 0, len(settings.Profiles)),
		AutoSummary: settings.AutoSummary,
	}
	for _, profile := range settings.Profiles {
		res.Profiles = append(res.Profiles, aiProfilePB(profile))
	}
	return res
}

func memoAIPB(ai *store.MemoAI) *apiv1.MemoAI {
	if ai == nil {
		return nil
	}
	return &apiv1.MemoAI{
		MemoId:        ai.MemoID,
		Summary:       nullStringValue(ai.Summary),
		Sentiment:     nullStringValue(ai.Sentiment),
		Provider:      ai.Provider,
		Model:         ai.Model,
		ProfileId:     ai.ProfileID,
		PromptVersion: ai.PromptVersion,
		SourceMemoIds: ai.SourceMemoIDs,
		Status:        ai.Status,
		ErrorCode:     nullStringValue(ai.ErrorCode),
		StartedTime:   timestampPB(ai.StartedAt),
		FinishedTime:  timestampPB(ai.FinishedAt),
		InputTokens:   ai.InputTokens,
		OutputTokens:  ai.OutputTokens,
		TotalTokens:   ai.TotalTokens,
		CreatedTime:   timestamppb.New(unixMilliTime(ai.CreatedAt)),
		UpdatedTime:   timestamppb.New(unixMilliTime(ai.UpdatedAt)),
	}
}

func attachmentPB(attachment *store.Attachment) *apiv1.Attachment {
	if attachment == nil {
		return nil
	}
	return &apiv1.Attachment{
		Id:          attachment.ID,
		Uid:         attachment.UID,
		MemoId:      nullStringPB(attachment.MemoID),
		Url:         "/file/attachments/" + attachment.UID + "/" + attachment.Filename,
		Filename:    attachment.Filename,
		ContentType: attachment.ContentType,
		Size:        attachment.Size,
		Sha256:      nullStringPB(attachment.SHA256),
		Width:       nullIntPB(attachment.Width),
		Height:      nullIntPB(attachment.Height),
		Status:      attachment.Status,
		CreatedTime: timestamppb.New(unixMilliTime(attachment.CreatedAt)),
		UpdatedTime: timestamppb.New(unixMilliTime(attachment.UpdatedAt)),
		DeletedTime: timestampPB(attachment.DeletedAt),
	}
}

func askConversationPB(conversation *store.AskConversation) *apiv1.AskConversation {
	if conversation == nil {
		return nil
	}
	return &apiv1.AskConversation{
		Id:            conversation.ID,
		Title:         conversation.Title,
		Status:        conversation.Status,
		ContextScope:  conversation.ContextScope,
		HeadMessageId: nullStringPB(conversation.HeadMessageID),
		PinnedTime:    timestampPB(conversation.PinnedAt),
		ArchivedTime:  timestampPB(conversation.ArchivedAt),
		CreatedTime:   timestamppb.New(unixMilliTime(conversation.CreatedAt)),
		UpdatedTime:   timestamppb.New(unixMilliTime(conversation.UpdatedAt)),
		DeletedTime:   timestampPB(conversation.DeletedAt),
	}
}

func askMessagePB(message *store.AskMessage) *apiv1.AskMessage {
	if message == nil {
		return nil
	}
	return &apiv1.AskMessage{
		Id:             message.ID,
		ConversationId: message.ConversationID,
		Role:           message.Role,
		Content:        message.Content,
		ParentId:       nullStringPB(message.ParentID),
		ForkOfId:       nullStringPB(message.ForkOfID),
		Status:         message.Status,
		SourceRefs:     askSourceRefsPB(message.SourceRefs),
		Model:          message.Model,
		PromptVersion:  message.PromptVersion,
		CreatedTime:    timestamppb.New(unixMilliTime(message.CreatedAt)),
		UpdatedTime:    timestamppb.New(unixMilliTime(message.UpdatedAt)),
		DeletedTime:    timestampPB(message.DeletedAt),
	}
}

func syncResultPB(result syncResult) *apiv1.SyncResult {
	return &apiv1.SyncResult{
		MutationId:     result.MutationID,
		ResourceType:   result.ResourceType,
		ResourceId:     result.ResourceID,
		Status:         result.Status,
		Reason:         result.Reason,
		Message:        result.Message,
		Idempotent:     result.Idempotent,
		Resource:       memoPB(result.Resource),
		ServerResource: memoPB(result.ServerResource),
		ClientVersion:  result.ClientVersion,
		ServerVersion:  result.ServerVersion,
	}
}

func askSourceRefsPB(raw string) []*apiv1.AskSourceRef {
	var refs []askSourceRef
	if err := json.Unmarshal([]byte(raw), &refs); err != nil {
		return nil
	}
	items := make([]*apiv1.AskSourceRef, 0, len(refs))
	for _, ref := range refs {
		items = append(items, &apiv1.AskSourceRef{
			MemoId:    ref.MemoID,
			EntryDate: ref.EntryDate,
			Excerpt:   ref.Excerpt,
			Rank:      int32(ref.Rank),
		})
	}
	return items
}

func timestampPB(value sql.NullInt64) *timestamppb.Timestamp {
	if !value.Valid {
		return nil
	}
	return timestamppb.New(unixMilliTime(value.Int64))
}

func nullStringValue(value sql.NullString) string {
	if !value.Valid {
		return ""
	}
	return value.String
}

func nullStringPB(value sql.NullString) string {
	if !value.Valid {
		return ""
	}
	return value.String
}

func nullIntPB(value sql.NullInt64) int64 {
	if !value.Valid {
		return 0
	}
	return value.Int64
}
