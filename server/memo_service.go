package server

import (
	"context"
	"net/http"
	"strings"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
	"github.com/getsillage/sillage/server/auth"
	memoapp "github.com/getsillage/sillage/server/memo"
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
		memos, err = s.server.memos.Search(ctx, account.ID, memoapp.SearchInput{
			Query:     query,
			Archived:  req.Msg.Archived,
			Favorited: req.Msg.Favorited,
			Limit:     limit,
		})
	} else {
		var page *memoapp.Page
		page, err = s.server.memos.List(ctx, account.ID, memoapp.ListInput{
			Archived:  req.Msg.Archived,
			Favorited: req.Msg.Favorited,
			Limit:     limit,
			Cursor:    req.Msg.GetCursor(),
		})
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
	memo, err := s.server.memos.Create(ctx, account.ID, memoapp.CreateInput{
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
	detail, err := s.server.memos.Get(ctx, account.ID, req.Msg.GetId())
	if err != nil {
		return nil, connectError(err)
	}
	res := &apiv1.MemoResponse{Memo: memoPB(detail.Memo), Ai: memoAIPB(detail.AI)}
	return connect.NewResponse(res), nil
}

func (s *memoService) UpdateMemo(ctx context.Context, req *connect.Request[apiv1.UpdateMemoRequest]) (*connect.Response[apiv1.MemoResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	memo, err := s.server.memos.Update(ctx, account.ID, memoapp.UpdateInput{
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
	memo, err := s.server.memos.Delete(ctx, account.ID, req.Msg.GetId(), req.Msg.GetExpectedVersion())
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
	memo, err := s.server.memos.Update(ctx, account.ID, memoapp.UpdateInput{
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
