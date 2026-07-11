package memo

import (
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/getsillage/sillage/store"
)

const (
	defaultPageSize       = 50
	memoListCursorVersion = 2
)

var (
	ErrSummaryRead = errors.New("read memo summary")
	ErrValidation  = errors.New("memo validation error")
)

type validationError struct {
	message string
}

func (e validationError) Error() string {
	return e.message
}

func (e validationError) Unwrap() error {
	return ErrValidation
}

type Repository interface {
	ListMemos(context.Context, *store.ListMemoOptions) ([]*store.Memo, error)
	SearchMemos(context.Context, *store.SearchMemoOptions) ([]*store.Memo, error)
	CreateMemo(context.Context, *store.CreateMemo) (*store.Memo, error)
	GetMemo(context.Context, string, string, bool) (*store.Memo, error)
	UpdateMemo(context.Context, *store.UpdateMemo) (*store.Memo, error)
	GetMemoAI(context.Context, string) (*store.MemoAI, error)
}

var _ Repository = (*store.Store)(nil)

type AfterCreateFunc func(accountID, memoID string)

type Service struct {
	repository  Repository
	afterCreate AfterCreateFunc
}

func NewService(repository Repository, afterCreate AfterCreateFunc) *Service {
	return &Service{repository: repository, afterCreate: afterCreate}
}

type ListInput struct {
	Archived  *bool
	Favorited *bool
	Limit     int
	Cursor    string
}

type SearchInput struct {
	Query     string
	Archived  *bool
	Favorited *bool
	Limit     int
}

type CreateInput struct {
	ID        string
	Content   string
	EntryDate string
	Favorited bool
	Archived  bool
}

type UpdateInput struct {
	ID              string
	ExpectedVersion int64
	Content         *string
	EntryDate       *string
	Favorited       *bool
	Archived        *bool
	Deleted         *bool
}

type Page struct {
	Memos      []*store.Memo
	NextCursor string
}

type Detail struct {
	Memo *store.Memo
	AI   *store.MemoAI
}

// List returns one reverse-chronological page plus an opaque cursor for the
// next page. An empty NextCursor means the list is exhausted.
func (s *Service) List(ctx context.Context, accountID string, input ListInput) (*Page, error) {
	pageSize := normalizeLimit(input.Limit, defaultPageSize)
	opts := &store.ListMemoOptions{
		AccountID:         accountID,
		Limit:             pageSize + 1,
		LookaheadPageSize: pageSize,
		Archived:          input.Archived,
		Favorited:         input.Favorited,
	}
	legacyV1 := false
	if cursor, ok := decodeListCursor(input.Cursor); ok {
		if cursor.Version == 1 {
			legacyV1 = true
			opts.LegacyFavoritedFirst = true
			opts.BeforeFavorited = cursor.Pinned
		}
		opts.BeforeEntryDate = cursor.EntryDate
		opts.BeforeCreatedAt = cursor.CreatedAt
		opts.BeforeID = cursor.ID
	}
	memos, err := s.repository.ListMemos(ctx, opts)
	if err != nil {
		return nil, err
	}
	next := ""
	if len(memos) > pageSize {
		memos = memos[:pageSize]
		last := memos[len(memos)-1]
		nextCursor := listCursor{
			Version:   memoListCursorVersion,
			EntryDate: last.EntryDate,
			CreatedAt: last.CreatedAt,
			ID:        last.ID,
		}
		if legacyV1 {
			favorited := last.FavoritedAt.Valid
			nextCursor.Version = 1
			nextCursor.Pinned = &favorited
		}
		next = encodeListCursor(nextCursor)
	}
	return &Page{Memos: memos, NextCursor: next}, nil
}

func (s *Service) Search(ctx context.Context, accountID string, input SearchInput) ([]*store.Memo, error) {
	return s.repository.SearchMemos(ctx, &store.SearchMemoOptions{
		AccountID: accountID,
		Query:     input.Query,
		Limit:     normalizeLimit(input.Limit, defaultPageSize),
		Archived:  input.Archived,
		Favorited: input.Favorited,
	})
}

func (s *Service) Create(ctx context.Context, accountID string, input CreateInput) (*store.Memo, error) {
	if err := ValidateFields(input.Content, input.EntryDate); err != nil {
		return nil, err
	}
	memo, err := s.repository.CreateMemo(ctx, &store.CreateMemo{
		ID:        input.ID,
		CreatorID: accountID,
		Content:   input.Content,
		EntryDate: input.EntryDate,
		Favorited: input.Favorited,
		Archived:  input.Archived,
	})
	if err != nil {
		return nil, err
	}
	if s.afterCreate != nil {
		s.afterCreate(accountID, memo.ID)
	}
	return memo, nil
}

func (s *Service) Get(ctx context.Context, accountID, id string) (*Detail, error) {
	memo, err := s.repository.GetMemo(ctx, accountID, id, false)
	if err != nil {
		return nil, err
	}
	detail := &Detail{Memo: memo}
	ai, err := s.repository.GetMemoAI(ctx, memo.ID)
	if err == nil {
		detail.AI = ai
	} else if !errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("%w: %w", ErrSummaryRead, err)
	}
	return detail, nil
}

func (s *Service) Update(ctx context.Context, accountID string, input UpdateInput) (*store.Memo, error) {
	if input.ExpectedVersion <= 0 {
		return nil, validationError{message: "expectedVersion 必须大于 0"}
	}
	if input.Content != nil && *input.Content == "" {
		return nil, validationError{message: "记录内容不能为空"}
	}
	if input.EntryDate != nil {
		if err := validateEntryDate(*input.EntryDate); err != nil {
			return nil, err
		}
	}
	return s.repository.UpdateMemo(ctx, &store.UpdateMemo{
		ID:              input.ID,
		CreatorID:       accountID,
		ExpectedVersion: input.ExpectedVersion,
		Content:         input.Content,
		EntryDate:       input.EntryDate,
		Favorited:       input.Favorited,
		Archived:        input.Archived,
		Deleted:         input.Deleted,
	})
}

func (s *Service) Delete(ctx context.Context, accountID, id string, expectedVersion int64) (*store.Memo, error) {
	deleted := true
	return s.Update(ctx, accountID, UpdateInput{
		ID:              id,
		ExpectedVersion: expectedVersion,
		Deleted:         &deleted,
	})
}

func ValidateFields(content, entryDate string) error {
	if content == "" {
		return validationError{message: "记录内容不能为空"}
	}
	return validateEntryDate(entryDate)
}

func validateEntryDate(entryDate string) error {
	if _, err := time.Parse("2006-01-02", entryDate); err != nil {
		return validationError{message: "记录日期必须是 YYYY-MM-DD"}
	}
	return nil
}

type listCursor struct {
	Version   int    `json:"version,omitempty"`
	Pinned    *bool  `json:"pinned,omitempty"`
	EntryDate string `json:"entryDate"`
	CreatedAt int64  `json:"createdAt"`
	ID        string `json:"id"`
}

func decodeListCursor(raw string) (listCursor, bool) {
	if raw == "" {
		return listCursor{}, false
	}
	payload, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return listCursor{}, false
	}
	var cursor listCursor
	if err := json.Unmarshal(payload, &cursor); err != nil || cursor.ID == "" {
		return listCursor{}, false
	}
	switch cursor.Version {
	case 0, memoListCursorVersion:
	case 1:
		if cursor.Pinned == nil {
			return listCursor{}, false
		}
	default:
		return listCursor{}, false
	}
	return cursor, true
}

func encodeListCursor(cursor listCursor) string {
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
