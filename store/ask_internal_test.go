package store

import "testing"

func TestTitleFromContentTruncatesByRune(t *testing.T) {
	// 30 CJK runes; the 24-rune cap must not split a multi-byte rune.
	long := ""
	for i := 0; i < 30; i++ {
		long += "记"
	}
	got := titleFromContent(long)
	if gotRunes := []rune(got); len(gotRunes) != 24 {
		t.Fatalf("title rune count = %d, want 24", len(gotRunes))
	}
	if short := titleFromContent("  你好  "); short != "你好" {
		t.Fatalf("title short = %q, want 你好", short)
	}
}
