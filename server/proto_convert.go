package server

import (
	"database/sql"

	apiv1 "github.com/miofelix/sillage/proto/gen/api/v1"
	"github.com/miofelix/sillage/store"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func memoPB(memo *store.Memo) *apiv1.Memo {
	if memo == nil {
		return nil
	}
	return &apiv1.Memo{
		Id:           memo.ID,
		Content:      memo.Content,
		EntryDate:    memo.EntryDate,
		Version:      memo.Version,
		PinnedTime:   timestampPB(memo.PinnedAt),
		ArchivedTime: timestampPB(memo.ArchivedAt),
		CreatedTime:  timestamppb.New(unixMilliTime(memo.CreatedAt)),
		UpdatedTime:  timestamppb.New(unixMilliTime(memo.UpdatedAt)),
		DeletedTime:  timestampPB(memo.DeletedAt),
	}
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
		CreatedTime:   timestamppb.New(unixMilliTime(ai.CreatedAt)),
		UpdatedTime:   timestamppb.New(unixMilliTime(ai.UpdatedAt)),
	}
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
