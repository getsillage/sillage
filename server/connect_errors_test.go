package server

import (
	"database/sql"
	"errors"
	"testing"

	"connectrpc.com/connect"

	"github.com/miofelix/sillage/store"
)

func TestConnectErrorMapping(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want connect.Code
	}{
		{"validation", validationError{message: "字段非法"}, connect.CodeInvalidArgument},
		{"too many changes", errTooManyChanges, connect.CodeInvalidArgument},
		{"not found", sql.ErrNoRows, connect.CodeNotFound},
		{"conflict", &store.MemoConflictError{}, connect.CodeAborted},
		{"ai not configured", errAINotConfigured, connect.CodeFailedPrecondition},
		{"ai key unavailable", errAIKeyUnavailable, connect.CodeFailedPrecondition},
		{"ai overloaded", errAIOverloaded, connect.CodeResourceExhausted},
		{"unknown", errors.New("boom"), connect.CodeInternal},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := connectError(tc.err)
			if connect.CodeOf(got) != tc.want {
				t.Fatalf("code = %v, want %v", connect.CodeOf(got), tc.want)
			}
		})
	}
}

// A raw internal error must not leak its underlying text to the client.
func TestConnectErrorHidesInternalText(t *testing.T) {
	got := connectError(errors.New("sql: syntax error near SELECT"))
	if msg := connect.CodeOf(got); msg != connect.CodeInternal {
		t.Fatalf("code = %v, want Internal", msg)
	}
	var connErr *connect.Error
	if errors.As(got, &connErr) {
		if got := connErr.Message(); contains(got, "SELECT") {
			t.Fatalf("internal error leaked raw text: %q", got)
		}
	}
}

func contains(haystack, needle string) bool {
	for i := 0; i+len(needle) <= len(haystack); i++ {
		if haystack[i:i+len(needle)] == needle {
			return true
		}
	}
	return false
}

func TestExcerptTruncatesByRune(t *testing.T) {
	// 6 CJK runes; limit 3 must yield 3 runes + ellipsis, not a sliced byte.
	got := excerpt("今天天气很好啊", 3)
	want := "今天天..."
	if got != want {
		t.Fatalf("excerpt = %q, want %q", got, want)
	}
	if short := excerpt("短", 3); short != "短" {
		t.Fatalf("excerpt short = %q, want 短", short)
	}
}
