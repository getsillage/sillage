package server

import (
	"context"
	"database/sql"
	"errors"
	"net/http"
	"strings"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
	"github.com/getsillage/sillage/server/auth"
	"github.com/getsillage/sillage/store"
)

type memoService struct {
	server *Server
}

func (s *memoService) ListMemos(ctx context.Context, req *connect.Request[apiv1.ListMemosRequest]) (*connect.Response[apiv1.ListMemosResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	limit := int(req.Msg.GetLimit())
	var memos []*store.Memo
	var nextCursor string
	if query := req.Msg.GetQuery(); query != "" {
		memos, err = s.server.searchMemos(ctx, account.ID, query, req.Msg.Archived, req.Msg.Favorited, limit)
	} else {
		var page *memoListPage
		page, err = s.server.listMemos(ctx, account.ID, req.Msg.Archived, req.Msg.Favorited, limit, req.Msg.GetCursor())
		if page != nil {
			memos = page.Memos
			nextCursor = page.NextCursor
		}
	}
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.ListMemosResponse{
		Memos:      make([]*apiv1.Memo, 0, len(memos)),
		NextCursor: nextCursor,
	}
	for _, memo := range memos {
		res.Memos = append(res.Memos, memoPB(memo))
	}
	return connect.NewResponse(res), nil
}

func (s *memoService) CreateMemo(ctx context.Context, req *connect.Request[apiv1.CreateMemoRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	memo, err := s.server.createMemo(ctx, account.ID, memoCreateInput{
		ID:        req.Msg.GetId(),
		Content:   req.Msg.GetContent(),
		EntryDate: req.Msg.GetEntryDate(),
	})
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.MemoResponse{Memo: memoPB(memo)}), nil
}

func (s *memoService) GetMemo(ctx context.Context, req *connect.Request[apiv1.GetMemoRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	memo, err := s.server.getMemo(ctx, account.ID, req.Msg.GetId())
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.MemoResponse{Memo: memoPB(memo)}
	if ai, aiErr := s.server.Store.GetMemoAI(ctx, memo.ID); aiErr == nil {
		res.Ai = memoAIPB(ai)
	} else if !errors.Is(aiErr, sql.ErrNoRows) {
		return nil, connectError(aiErr)
	}
	return connect.NewResponse(res), nil
}

func (s *memoService) UpdateMemo(ctx context.Context, req *connect.Request[apiv1.UpdateMemoRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	memo, err := s.server.updateMemo(ctx, account.ID, memoUpdateInput{
		ID:              req.Msg.GetId(),
		ExpectedVersion: req.Msg.GetExpectedVersion(),
		Content:         req.Msg.Content,
		EntryDate:       req.Msg.EntryDate,
		Favorited:       req.Msg.Favorited,
		Archived:        req.Msg.Archived,
	})
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.MemoResponse{Memo: memoPB(memo)}), nil
}

func (s *memoService) DeleteMemo(ctx context.Context, req *connect.Request[apiv1.DeleteMemoRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	memo, err := s.server.deleteMemo(ctx, account.ID, req.Msg.GetId(), req.Msg.GetExpectedVersion())
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.MemoResponse{Memo: memoPB(memo)}), nil
}

func (s *memoService) SetMemoFavorited(ctx context.Context, req *connect.Request[apiv1.SetMemoFavoritedRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	return s.updateMemoBool(ctx, req.Header(), req.Msg.GetId(), req.Msg.GetExpectedVersion(), &req.Msg.Favorited, nil)
}

func (s *memoService) SetMemoArchived(ctx context.Context, req *connect.Request[apiv1.SetMemoArchivedRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	return s.updateMemoBool(ctx, req.Header(), req.Msg.GetId(), req.Msg.GetExpectedVersion(), nil, &req.Msg.Archived)
}

func (s *memoService) GenerateMemoSummary(ctx context.Context, req *connect.Request[apiv1.GenerateMemoSummaryRequest]) (*connect.Response[apiv1.GenerateMemoSummaryResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	ai, err := s.server.generateMemoSummary(ctx, account.ID, req.Msg.GetId())
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.GenerateMemoSummaryResponse{Ai: memoAIPB(ai)}), nil
}

func (s *memoService) updateMemoBool(
	ctx context.Context,
	header http.Header,
	id string,
	expectedVersion int64,
	favorited *bool,
	archived *bool,
) (*connect.Response[apiv1.MemoResponse], error) {
	account, err := s.server.accountFromConnect(ctx, header)
	if err != nil {
		return nil, err
	}
	memo, err := s.server.updateMemo(ctx, account.ID, memoUpdateInput{
		ID:              id,
		ExpectedVersion: expectedVersion,
		Favorited:       favorited,
		Archived:        archived,
	})
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.MemoResponse{Memo: memoPB(memo)}), nil
}

func (s *Server) accountFromConnect(ctx context.Context, header http.Header) (*store.Account, error) {
	token, ok := strings.CutPrefix(header.Get("Authorization"), "Bearer ")
	if !ok || token == "" {
		return nil, connect.NewError(connect.CodeUnauthenticated, auth.ErrUnauthenticated)
	}
	claims, err := s.auth.VerifyAccessToken(token)
	if err != nil {
		return nil, connect.NewError(connect.CodeUnauthenticated, err)
	}
	account, err := s.Store.GetAccountByID(ctx, claims.AccountID)
	if err != nil {
		return nil, connect.NewError(connect.CodeUnauthenticated, err)
	}
	return account, nil
}
