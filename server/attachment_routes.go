package server

import (
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/labstack/echo/v5"

	"github.com/miofelix/sillage/store"
)

const defaultMaxUploadBytes = 30 << 20

var unsafeFilenameChars = regexp.MustCompile(`[^\w.\- ]+`)

func (s *Server) registerAttachmentRoutes(e *echo.Echo) {
	e.POST("/api/v1/attachments", s.handleUploadAttachment)
	e.GET("/api/v1/attachments/:attachment", s.handleGetAttachment)
	e.DELETE("/api/v1/attachments/:attachment", s.handleDeleteAttachment)
	e.GET("/file/attachments/:uid/:filename", s.handleServeAttachment)
}

func (s *Server) handleUploadAttachment(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	maxUpload := s.maxUploadBytes()
	c.Request().Body = http.MaxBytesReader(c.Response(), c.Request().Body, maxUpload)
	if err := c.Request().ParseMultipartForm(maxUpload); err != nil {
		return apiError(c, http.StatusRequestEntityTooLarge, "too_large", "文件超过上传大小限制")
	}
	fileHeader, err := c.FormFile("file")
	if err != nil {
		return apiError(c, http.StatusBadRequest, "missing_file", "请选择要上传的文件")
	}
	filename, err := sanitizeFilename(fileHeader.Filename)
	if err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_filename", "文件名不安全")
	}
	if fileHeader.Size > maxUpload {
		return apiError(c, http.StatusRequestEntityTooLarge, "too_large", "文件超过上传大小限制")
	}

	if mutationID := c.FormValue("mutation_id"); mutationID != "" {
		if existing, ok, err := s.Store.GetAttachmentByMutationID(c.Request().Context(), account.ID, mutationID); err != nil {
			return apiError(c, http.StatusInternalServerError, "internal", "读取附件幂等状态失败")
		} else if ok {
			return c.JSON(http.StatusOK, map[string]any{"attachment": attachmentDTO(existing)})
		}
	}
	if idempotencyKey := c.FormValue("idempotency_key"); idempotencyKey != "" {
		if existing, ok, err := s.Store.GetAttachmentByIdempotencyKey(c.Request().Context(), account.ID, idempotencyKey); err != nil {
			return apiError(c, http.StatusInternalServerError, "internal", "读取附件幂等状态失败")
		} else if ok {
			return c.JSON(http.StatusOK, map[string]any{"attachment": attachmentDTO(existing)})
		}
	}

	src, err := fileHeader.Open()
	if err != nil {
		return apiError(c, http.StatusBadRequest, "invalid_file", "无法读取上传文件")
	}
	defer src.Close()

	attachmentDir := filepath.Join(s.Profile.Data, "assets", "attachments")
	if err := os.MkdirAll(attachmentDir, 0o770); err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "创建附件目录失败")
	}
	tmp, err := os.CreateTemp(attachmentDir, "upload-*")
	if err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "创建临时文件失败")
	}
	tmpPath := tmp.Name()
	defer os.Remove(tmpPath)

	hasher := sha256.New()
	limited := io.LimitReader(src, maxUpload+1)
	written, copyErr := io.Copy(tmp, io.TeeReader(limited, hasher))
	closeErr := tmp.Close()
	if copyErr != nil || closeErr != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "保存附件失败")
	}
	if written > maxUpload {
		return apiError(c, http.StatusRequestEntityTooLarge, "too_large", "文件超过上传大小限制")
	}

	sha := hex.EncodeToString(hasher.Sum(nil))
	storageRef := filepath.Join("assets", "attachments", fmt.Sprintf("%d_%s_%s", time.Now().UTC().UnixMilli(), sha[:12], filename))
	finalPath := filepath.Join(s.Profile.Data, storageRef)
	if err := os.Rename(tmpPath, finalPath); err != nil {
		return apiError(c, http.StatusInternalServerError, "internal", "保存附件失败")
	}

	contentType := fileHeader.Header.Get("Content-Type")
	if contentType == "" || contentType == "application/octet-stream" {
		contentType = sniffContentType(finalPath, filename)
	}
	attachment, err := s.Store.CreateAttachment(c.Request().Context(), &store.CreateAttachment{
		CreatorID:      account.ID,
		MemoID:         c.FormValue("memo_id"),
		StorageRef:     storageRef,
		Filename:       filename,
		ContentType:    contentType,
		Size:           written,
		SHA256:         sha,
		MutationID:     c.FormValue("mutation_id"),
		IdempotencyKey: c.FormValue("idempotency_key"),
	})
	if err != nil {
		_ = os.Remove(finalPath)
		return apiError(c, http.StatusInternalServerError, "internal", "保存附件元数据失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"attachment": attachmentDTO(attachment)})
}

func (s *Server) handleGetAttachment(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	attachment, err := s.getAttachment(c.Request().Context(), account.ID, c.Param("attachment"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "附件不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取附件失败")
	}
	return c.JSON(http.StatusOK, map[string]any{"attachment": attachmentDTO(attachment)})
}

func (s *Server) handleDeleteAttachment(c *echo.Context) error {
	account, err := s.accountFromBearer(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	attachment, err := s.Store.DeleteAttachment(c.Request().Context(), account.ID, c.Param("attachment"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "附件不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "删除附件失败")
	}
	if path, err := s.safeStoragePath(attachment.StorageRef); err == nil {
		_ = os.Remove(path)
	}
	return c.JSON(http.StatusOK, map[string]any{"attachment": attachmentDTO(attachment)})
}

func (s *Server) handleServeAttachment(c *echo.Context) error {
	account, err := s.accountFromRequest(c)
	if err != nil {
		return apiError(c, http.StatusUnauthorized, "unauthenticated", "请重新登录")
	}
	attachment, err := s.getAttachment(c.Request().Context(), account.ID, c.Param("uid"))
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return apiError(c, http.StatusNotFound, "not_found", "附件不存在")
		}
		return apiError(c, http.StatusInternalServerError, "internal", "读取附件失败")
	}
	path, err := s.safeStoragePath(attachment.StorageRef)
	if err != nil {
		return apiError(c, http.StatusNotFound, "not_found", "附件不存在")
	}
	file, err := os.Open(path)
	if err != nil {
		return apiError(c, http.StatusNotFound, "not_found", "附件不存在")
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return apiError(c, http.StatusNotFound, "not_found", "附件不存在")
	}

	c.Response().Header().Set("Content-Type", attachment.ContentType)
	if shouldForceDownload(attachment.ContentType) {
		c.Response().Header().Set("Content-Disposition", contentDispositionAttachment(attachment.Filename))
	}
	http.ServeContent(c.Response(), c.Request(), attachment.Filename, info.ModTime(), file)
	return nil
}

func (s *Server) safeStoragePath(storageRef string) (string, error) {
	clean := filepath.Clean(storageRef)
	if filepath.IsAbs(clean) || strings.HasPrefix(clean, "..") {
		return "", fmt.Errorf("unsafe storage ref")
	}
	path := filepath.Join(s.Profile.Data, clean)
	root, err := filepath.Abs(s.Profile.Data)
	if err != nil {
		return "", err
	}
	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	if absPath != root && !strings.HasPrefix(absPath, root+string(filepath.Separator)) {
		return "", fmt.Errorf("storage ref escapes data dir")
	}
	return absPath, nil
}

func (s *Server) maxUploadBytes() int64 {
	if s.Profile != nil && s.Profile.MaxUploadMB > 0 {
		return int64(s.Profile.MaxUploadMB) << 20
	}
	return defaultMaxUploadBytes
}

func sanitizeFilename(raw string) (string, error) {
	name := strings.TrimSpace(filepath.Base(raw))
	name = unsafeFilenameChars.ReplaceAllString(name, "_")
	name = strings.Trim(name, ". ")
	if name == "" || name == ".." || strings.ContainsRune(name, filepath.Separator) {
		return "", fmt.Errorf("invalid filename")
	}
	return name, nil
}

func sniffContentType(path, filename string) string {
	if extType := mime.TypeByExtension(filepath.Ext(filename)); extType != "" {
		return extType
	}
	file, err := os.Open(path)
	if err != nil {
		return "application/octet-stream"
	}
	defer file.Close()
	buf := make([]byte, 512)
	n, _ := file.Read(buf)
	return http.DetectContentType(buf[:n])
}

func shouldForceDownload(contentType string) bool {
	return !(strings.HasPrefix(contentType, "image/") ||
		strings.HasPrefix(contentType, "text/plain") ||
		contentType == "application/pdf")
}

func contentDispositionAttachment(filename string) string {
	return `attachment; filename="` + strings.ReplaceAll(filename, `"`, "_") + `"`
}

func attachmentDTO(attachment *store.Attachment) map[string]any {
	if attachment == nil {
		return nil
	}
	return map[string]any{
		"id":          attachment.ID,
		"uid":         attachment.UID,
		"memoId":      optionalString(attachment.MemoID),
		"url":         "/file/attachments/" + attachment.UID + "/" + attachment.Filename,
		"filename":    attachment.Filename,
		"contentType": attachment.ContentType,
		"size":        attachment.Size,
		"sha256":      optionalString(attachment.SHA256),
		"width":       optionalInt(attachment.Width),
		"height":      optionalInt(attachment.Height),
		"status":      attachment.Status,
		"createdAt":   time.UnixMilli(attachment.CreatedAt).UTC().Format(time.RFC3339),
		"updatedAt":   time.UnixMilli(attachment.UpdatedAt).UTC().Format(time.RFC3339),
		"deletedAt":   optionalTime(attachment.DeletedAt),
	}
}

func attachmentDTOs(attachments []*store.Attachment) []map[string]any {
	dtos := make([]map[string]any, 0, len(attachments))
	for _, attachment := range attachments {
		dtos = append(dtos, attachmentDTO(attachment))
	}
	return dtos
}

func optionalString(value sql.NullString) any {
	if !value.Valid {
		return nil
	}
	return value.String
}

func optionalInt(value sql.NullInt64) any {
	if !value.Valid {
		return nil
	}
	return value.Int64
}
