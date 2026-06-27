package server

import (
	"database/sql"
	"errors"
	"log/slog"

	"connectrpc.com/connect"

	"github.com/getsillage/sillage/store"
)

// connectError maps a domain error to a Connect error with a code and a
// user-safe message. Known, user-facing errors keep their message; unknown
// errors are logged server-side and returned as a generic message so raw SQL /
// AI provider text never leaks to clients. REST handlers map the same domain
// errors via apiError; this keeps the two transports consistent.
func connectError(err error) error {
	if err == nil {
		return nil
	}

	var conflict *store.MemoConflictError
	switch {
	case errors.As(err, &conflict):
		return connect.NewError(connect.CodeAborted, errors.New("数据已被更新，请刷新后重试"))
	case errors.Is(err, errValidation):
		// validationError carries a vetted, user-facing message.
		return connect.NewError(connect.CodeInvalidArgument, err)
	case errors.Is(err, errTooManyChanges):
		return connect.NewError(connect.CodeInvalidArgument, errors.New("一次提交的变更过多"))
	case errors.Is(err, sql.ErrNoRows):
		return connect.NewError(connect.CodeNotFound, errors.New("资源不存在"))
	case errors.Is(err, errAINotConfigured):
		return connect.NewError(connect.CodeFailedPrecondition, errors.New("请先配置一个默认 AI 档案"))
	case errors.Is(err, errAIKeyUnavailable):
		return connect.NewError(connect.CodeFailedPrecondition, errors.New("当前 AI API Key 无法解密，请重新保存"))
	case errors.Is(err, errAIOverloaded):
		return connect.NewError(connect.CodeResourceExhausted, errors.New("当前生成任务较多，请稍后再试"))
	default:
		slog.Error("connect handler internal error", "error", err)
		return connect.NewError(connect.CodeInternal, errors.New("服务暂时不可用，请稍后再试"))
	}
}
