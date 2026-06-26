package server

import (
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/miofelix/sillage/store"
)

type memoRequest struct {
	ID              string  `json:"id"`
	Content         *string `json:"content"`
	EntryDate       *string `json:"entryDate"`
	ExpectedVersion int64   `json:"expectedVersion"`
	Pinned          *bool   `json:"pinned"`
	Archived        *bool   `json:"archived"`
}

func (s *Server) registerMemoRoutes(e *echo.Echo) {
	e.GET("/api/v1/memos", s.handleListMemos)
	e.POST("/api/v1/memos", s.handleCreateMemo)
	e.GET("/api/v1/memos/:memo", s.handleGetMemo)
	e.PATCH("/api/v1/memos/:memo", s.handleUpdateMemo)
	e.DELETE("/api/v1/memos/:memo", s.handleDeleteMemo)
	e.POST("/api/v1/memos/:memo:archive", s.handleArchiveMemo)
	e.POST("/api/v1/memos/:memo:unarchive", s.handleUnarchiveMemo)
	e.POST("/api/v1/memos/:memo:pin", s.handlePinMemo)
	e.POST("/api/v1/memos/:memo:unpin", s.handleUnpinMemo)
	e.GET("/api/v1/sync", s.handleSyncPull)
	e.POST("/api/v1/sync:push", s.handleSyncPush)
}

func (s *Server) handleListMemos(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	limit := parseLimit(c.QueryParam("limit"), 50)
	memos, err := s.Store.ListMemos(c.Request().Context(), &store.ListMemoOptions{
		AccountID: account.ID,
		Limit:     limit,
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取记录失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"memos": memoDTOs(memos)})
}

func (s *Server) handleCreateMemo(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req memoRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	content := stringValue(req.Content)
	entryDate := stringValue(req.EntryDate)
	if err := validateMemoFields(content, entryDate); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
	}
	memo, err := s.Store.CreateMemo(c.Request().Context(), &store.CreateMemo{
		ID:        req.ID,
		CreatorID: account.ID,
		Content:   content,
		EntryDate: entryDate,
	})
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "保存记录失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"memo": memoDTO(memo)})
}

func (s *Server) handleGetMemo(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	memo, err := s.Store.GetMemo(c.Request().Context(), account.ID, c.Param("memo"), false)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "记录不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取记录失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"memo": memoDTO(memo)})
}

func (s *Server) handleUpdateMemo(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	var req memoRequest
	if err := c.Bind(&req); err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
	}
	if req.Content != nil || req.EntryDate != nil {
		content := ""
		if req.Content != nil {
			content = *req.Content
		}
		entryDate := ""
		if req.EntryDate != nil {
			entryDate = *req.EntryDate
		}
		if req.Content != nil && content == "" {
			return apiError(c, http.StatusBadRequest, "invalid_field", "记录内容不能为空")
		}
		if req.EntryDate != nil {
			if _, err := time.Parse("2006-01-02", entryDate); err != nil {
				return apiError(c, http.StatusBadRequest, "invalid_field", "记录日期必须是 YYYY-MM-DD")
			}
		}
	}
	memo, err := s.Store.UpdateMemo(c.Request().Context(), &store.UpdateMemo{
		ID:              c.Param("memo"),
		CreatorID:       account.ID,
		ExpectedVersion: req.ExpectedVersion,
		Content:         req.Content,
		EntryDate:       req.EntryDate,
		Pinned:          req.Pinned,
		Archived:        req.Archived,
	})
	return s.writeMemoMutationResult(c, memo, err)
}

func (s *Server) handleDeleteMemo(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	expectedVersion, _ := strconv.ParseInt(c.QueryParam("expectedVersion"), 10, 64)
	deleted := true
	memo, err := s.Store.UpdateMemo(c.Request().Context(), &store.UpdateMemo{
		ID:              c.Param("memo"),
		CreatorID:       account.ID,
		ExpectedVersion: expectedVersion,
		Deleted:         &deleted,
	})
	return s.writeMemoMutationResult(c, memo, err)
}

func (s *Server) handleArchiveMemo(c *echo.Context) error {
	return s.handleMemoBoolPatch(c, "archived", true)
}

func (s *Server) handleUnarchiveMemo(c *echo.Context) error {
	return s.handleMemoBoolPatch(c, "archived", false)
}

func (s *Server) handlePinMemo(c *echo.Context) error {
	return s.handleMemoBoolPatch(c, "pinned", true)
}

func (s *Server) handleUnpinMemo(c *echo.Context) error {
	return s.handleMemoBoolPatch(c, "pinned", false)
}

func (s *Server) handleMemoBoolPatch(c *echo.Context, field string, value bool) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	expectedVersion, _ := strconv.ParseInt(c.QueryParam("expectedVersion"), 10, 64)
	update := &store.UpdateMemo{
		ID:              c.Param("memo"),
		CreatorID:       account.ID,
		ExpectedVersion: expectedVersion,
	}
	if field == "archived" {
		update.Archived = &value
	} else {
		update.Pinned = &value
	}
	memo, err := s.Store.UpdateMemo(c.Request().Context(), update)
	return s.writeMemoMutationResult(c, memo, err)
}

func (s *Server) writeMemoMutationResult(c *echo.Context, memo *store.Memo, err error) error {
	if err == nil {
		return c.JSON(http.StatusOK, map[string]any{"memo": memoDTO(memo)})
	}
	var conflict *store.MemoConflictError
	switch {
	case errors.As(err, &conflict):
		return c.JSON(http.StatusConflict, map[string]any{
			"error": map[string]any{
				"code":          "version_conflict",
				"message":       "记录已被其他修改更新",
				"serverMemo":    memoDTO(conflict.ServerMemo),
				"serverVersion": conflict.ServerMemo.Version,
			},
		})
	case errors.Is(err, sql.ErrNoRows):
		return apiError(c, http.StatusNotFound, "not_found", "记录不存在")
	default:
		return apiError(c, http.StatusInternalServerError, "internal", "保存记录失败")
	}
}

func validateMemoFields(content, entryDate string) error {
	if content == "" {
		return errors.New("记录内容不能为空")
	}
	if _, err := time.Parse("2006-01-02", entryDate); err != nil {
		return errors.New("记录日期必须是 YYYY-MM-DD")
	}
	return nil
}

func parseLimit(raw string, fallback int) int {
	limit, err := strconv.Atoi(raw)
	if err != nil || limit <= 0 {
		return fallback
	}
	if limit > 200 {
		return 200
	}
	return limit
}

func stringValue(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}

func memoDTOs(memos []*store.Memo) []map[string]any {
	dtos := make([]map[string]any, 0, len(memos))
	for _, memo := range memos {
		dtos = append(dtos, memoDTO(memo))
	}
	return dtos
}

func memoDTO(memo *store.Memo) map[string]any {
	if memo == nil {
		return nil
	}
	return map[string]any{
		"id":         memo.ID,
		"content":    memo.Content,
		"entryDate":  memo.EntryDate,
		"version":    memo.Version,
		"pinnedAt":   optionalTime(memo.PinnedAt),
		"archivedAt": optionalTime(memo.ArchivedAt),
		"createdAt":  time.UnixMilli(memo.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":  time.UnixMilli(memo.UpdatedAt).UTC().Format(time.RFC3339),
		"deletedAt":  optionalTime(memo.DeletedAt),
	}
}

func optionalTime(value sql.NullInt64) any {
	if !value.Valid {
		return nil
	}
	return time.UnixMilli(value.Int64).UTC().Format(time.RFC3339)
}

type syncCursor struct {
	Memo       store.SyncCursorPosition `json:"memo"`
	Attachment store.SyncCursorPosition `json:"attachment"`
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
