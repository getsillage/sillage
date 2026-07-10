package server

import (
	"database/sql"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/getsillage/sillage/store"
)

type memoRequest struct {
	ID              string  `json:"id"`
	Content         *string `json:"content"`
	EntryDate       *string `json:"entryDate"`
	ExpectedVersion int64   `json:"expectedVersion"`
	Favorited       *bool   `json:"favorited"`
	Archived        *bool   `json:"archived"`
}

func (s *Server) registerMemoRoutes(e *echo.Echo) {
	e.GET("/api/v1/memos", s.handleListMemos)
	e.POST("/api/v1/memos", s.handleCreateMemo)
	e.GET("/api/v1/memos/:memo", s.handleGetMemo)
	e.PATCH("/api/v1/memos/:memo", s.handleUpdateMemo)
	e.DELETE("/api/v1/memos/:memo", s.handleDeleteMemo)
	e.POST("/api/v1/memos/:memo", s.handleMemoAction)
	e.GET("/api/v1/sync", s.handleSyncPull)
	e.POST("/api/v1/sync:push", s.handleSyncPush)
}

func (s *Server) handleListMemos(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	limit := parseLimit(c.QueryParam("limit"), 50)
	ctx := c.Request().Context()
	// Accept both "query" (canonical, matches proto) and legacy "q".
	query := c.QueryParam("query")
	if query == "" {
		query = c.QueryParam("q")
	}
	archived, err := parseMemoBoolFilter(c.QueryParam("archived"), "archived")
	if err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
	}
	favorited, err := parseMemoBoolFilter(c.QueryParam("favorited"), "favorited")
	if err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
	}
	if query != "" {
		memos, err := s.searchMemos(ctx, account.ID, query, archived, favorited, limit)
		if err != nil {
			return apiError(c, http.StatusInternalServerError, "internal", "读取记录失败")
		}
		return c.JSON(http.StatusOK, map[string]any{"memos": memoDTOs(memos)})
	}
	page, err := s.listMemos(ctx, account.ID, archived, favorited, limit, c.QueryParam("cursor"))
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "读取记录失败")
	}
	return c.JSON(http.StatusOK, map[string]any{
		"memos":      memoDTOs(page.Memos),
		"nextCursor": page.NextCursor,
	})
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
	memo, err := s.createMemo(c.Request().Context(), account.ID, memoCreateInput{
		ID:        req.ID,
		Content:   content,
		EntryDate: entryDate,
	})
	if err != nil {
		if errors.Is(err, errValidation) {
			return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
		}
		return apiError(c, http.StatusInternalServerError, "internal", "保存记录失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"memo": memoDTO(memo)})
}

func (s *Server) handleGetMemo(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	ctx := c.Request().Context()
	memo, err := s.getMemo(ctx, account.ID, c.Param("memo"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "记录不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取记录失败")
	}
	body := map[string]any{"memo": memoDTO(memo)}
	// Inline the stored summary (if any) so clients render it without
	// regenerating. A missing row is not an error.
	if ai, aiErr := s.Store.GetMemoAI(ctx, memo.ID); aiErr == nil {
		body["ai"] = memoAIDTO(ai)
	} else if !errors.Is(aiErr, sql.ErrNoRows) {
		return apiError(c, http.StatusInternalServerError, "internal", "读取总结失败")
	}
	return c.JSON(http.StatusOK, body)
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
	memo, err := s.updateMemo(c.Request().Context(), account.ID, memoUpdateInput{
		ID:              memoParam(c),
		ExpectedVersion: req.ExpectedVersion,
		Content:         req.Content,
		EntryDate:       req.EntryDate,
		Favorited:       req.Favorited,
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
	memo, err := s.deleteMemo(c.Request().Context(), account.ID, memoParam(c), expectedVersion)
	return s.writeMemoMutationResult(c, memo, err)
}

func (s *Server) handleMemoAction(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	memoID, action, ok := memoActionParam(c)
	if !ok {
		return apiError(c, http.StatusNotFound, "not_found", "接口不存在")
	}

	if action == "generate-summary" {
		ai, err := s.generateMemoSummary(c.Request().Context(), account.ID, memoID)
		if err != nil {
			status, code, message := memoHTTPStatus(err)
			return apiError(c, status, code, message)
		}
		return c.JSON(http.StatusOK, map[string]any{"ai": memoAIDTO(ai)})
	}

	update := memoUpdateInput{ID: memoID}
	switch action {
	case "setArchived":
		var req memoRequest
		if err := c.Bind(&req); err != nil {
			return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
		}
		if req.Archived == nil {
			return apiError(c, http.StatusBadRequest, "invalid_field", "archived 必须是 true 或 false")
		}
		value := *req.Archived
		update.ExpectedVersion = req.ExpectedVersion
		update.Archived = &value
	case "setFavorited":
		var req memoRequest
		if err := c.Bind(&req); err != nil {
			return apiError(c, http.StatusBadRequest, "invalid_json", "请求格式不正确")
		}
		if req.Favorited == nil {
			return apiError(c, http.StatusBadRequest, "invalid_field", "favorited 必须是 true 或 false")
		}
		value := *req.Favorited
		update.ExpectedVersion = req.ExpectedVersion
		update.Favorited = &value
	case "archive":
		value := true
		update.ExpectedVersion, _ = strconv.ParseInt(c.QueryParam("expectedVersion"), 10, 64)
		update.Archived = &value
	case "unarchive":
		value := false
		update.ExpectedVersion, _ = strconv.ParseInt(c.QueryParam("expectedVersion"), 10, 64)
		update.Archived = &value
	default:
		return apiError(c, http.StatusNotFound, "not_found", "接口不存在")
	}
	memo, err := s.updateMemo(c.Request().Context(), account.ID, update)
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
	case errors.Is(err, errValidation):
		return apiError(c, http.StatusBadRequest, "invalid_field", err.Error())
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

func memoParam(c *echo.Context) string {
	if value := c.Param("memo"); value != "" {
		return value
	}
	path := c.Request().URL.Path
	prefix := "/api/v1/memos/"
	if !strings.HasPrefix(path, prefix) {
		return ""
	}
	value := strings.TrimPrefix(path, prefix)
	if before, _, ok := strings.Cut(value, ":"); ok {
		return before
	}
	if before, _, ok := strings.Cut(value, "/"); ok {
		return before
	}
	return value
}

func memoActionParam(c *echo.Context) (string, string, bool) {
	value := c.Param("memo")
	if value == "" {
		path := c.Request().URL.Path
		prefix := "/api/v1/memos/"
		if !strings.HasPrefix(path, prefix) {
			return "", "", false
		}
		value = strings.TrimPrefix(path, prefix)
	}
	memoID, action, ok := strings.Cut(value, ":")
	if !ok || memoID == "" || action == "" {
		return "", "", false
	}
	return memoID, action, true
}

func parseLimit(raw string, fallback int) int {
	limit, err := strconv.Atoi(raw)
	if err != nil || limit <= 0 {
		return fallback
	}
	return normalizeLimit(limit, fallback)
}

func parseMemoBoolFilter(raw, field string) (*bool, error) {
	if raw == "" {
		return nil, nil
	}
	value, err := strconv.ParseBool(raw)
	if err != nil {
		return nil, errors.New(field + " 必须是 true 或 false")
	}
	return &value, nil
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
		"id":          memo.ID,
		"content":     memo.Content,
		"entryDate":   memo.EntryDate,
		"version":     memo.Version,
		"favoritedAt": optionalTime(memo.FavoritedAt),
		"archivedAt":  optionalTime(memo.ArchivedAt),
		"createdAt":   time.UnixMilli(memo.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":   time.UnixMilli(memo.UpdatedAt).UTC().Format(time.RFC3339),
		"deletedAt":   optionalTime(memo.DeletedAt),
	}
}

func optionalTime(value sql.NullInt64) any {
	if !value.Valid {
		return nil
	}
	return time.UnixMilli(value.Int64).UTC().Format(time.RFC3339)
}
