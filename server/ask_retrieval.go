package server

// Source retrieval and ranking for Ask answers: candidate listing, scope
// cutoffs, scoring, excerpts, and citation filtering. HTTP handlers live in
// ask_routes.go. These pure helpers are covered by ask_logic_test.go.

import (
	"context"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/getsillage/sillage/store"
)

// isSummarySourceKind reports whether the answer should be grounded in stored
// summaries rather than raw memo text. "records" (or empty) means raw text.
func isSummarySourceKind(kind string) bool {
	switch kind {
	case "summaries", "memo_summary":
		return true
	default:
		return false
	}
}

// applySummaryExcerpts replaces each source's excerpt with the memo's stored AI
// summary when one exists; memos without a summary keep their raw excerpt.
func (s *Server) applySummaryExcerpts(ctx context.Context, sources []askSourceRef) []askSourceRef {
	out := make([]askSourceRef, 0, len(sources))
	for _, source := range sources {
		ref := source
		if ai, err := s.Store.GetMemoAI(ctx, source.MemoID); err == nil && ai.Summary.Valid {
			if summary := strings.TrimSpace(ai.Summary.String); summary != "" {
				ref.Excerpt = excerpt(summary, 200)
			}
		}
		out = append(out, ref)
	}
	return out
}

func (s *Server) listAskCandidateMemos(ctx context.Context, accountID string) ([]*store.Memo, error) {
	const pageSize = 200

	memos := make([]*store.Memo, 0, pageSize)
	var updatedAfter int64
	var updatedAfterID string
	for {
		page, err := s.Store.ListMemos(ctx, &store.ListMemoOptions{
			AccountID:      accountID,
			Limit:          pageSize,
			Sync:           true,
			UpdatedAfter:   updatedAfter,
			UpdatedAfterID: updatedAfterID,
		})
		if err != nil {
			return nil, err
		}
		memos = append(memos, page...)
		if len(page) < pageSize {
			return memos, nil
		}
		last := page[len(page)-1]
		updatedAfter = last.UpdatedAt
		updatedAfterID = last.ID
	}
}

func selectAskSourceRefs(question string, memos []*store.Memo, scope string) []askSourceRef {
	const sourceLimit = 5

	terms := askQueryTerms(question)
	cutoff := ""
	if scope != "all" {
		cutoff = askScopeCutoff(scope)
	}

	type scoredMemo struct {
		memo  *store.Memo
		score int
	}

	scored := make([]scoredMemo, 0, len(memos))
	for _, memo := range memos {
		if memo == nil || memo.ID == "" {
			continue
		}
		if cutoff != "" && memo.EntryDate < cutoff {
			continue
		}
		score := askMemoScore(question, terms, memo)
		if score <= 0 {
			continue
		}
		scored = append(scored, scoredMemo{memo: memo, score: score})
	}

	sort.Slice(scored, func(i, j int) bool {
		if scored[i].score != scored[j].score {
			return scored[i].score > scored[j].score
		}
		if scored[i].memo.EntryDate != scored[j].memo.EntryDate {
			return scored[i].memo.EntryDate > scored[j].memo.EntryDate
		}
		if scored[i].memo.CreatedAt != scored[j].memo.CreatedAt {
			return scored[i].memo.CreatedAt > scored[j].memo.CreatedAt
		}
		return scored[i].memo.ID > scored[j].memo.ID
	})

	limit := sourceLimit
	if len(scored) < limit {
		limit = len(scored)
	}
	sources := make([]askSourceRef, 0, limit)
	for i := 0; i < limit; i++ {
		memo := scored[i].memo
		sources = append(sources, askSourceRef{
			MemoID:    memo.ID,
			EntryDate: memo.EntryDate,
			Excerpt:   askRelevantExcerpt(memo.Content, question, terms, 96),
			Rank:      i + 1,
		})
	}
	return sources
}

// askScopeCutoff returns the earliest entryDate (inclusive window start) for a
// scope. entryDate strings carry the user's local date while the server may run
// in another timezone (Docker defaults to UTC), so the window is widened by one
// day to cover any offset; candidates are still ranked by relevance.
func askScopeCutoff(scope string) string {
	switch scope {
	case "recent_7_days":
		return time.Now().AddDate(0, 0, -8).Format("2006-01-02")
	default:
		return time.Now().AddDate(0, 0, -31).Format("2006-01-02")
	}
}

func askMemoScore(question string, terms []string, memo *store.Memo) int {
	content := strings.ToLower(strings.TrimSpace(memo.Content))
	if content == "" {
		return 0
	}
	score := 0
	if q := strings.ToLower(strings.TrimSpace(question)); q != "" && strings.Contains(content, q) {
		score += 100
	}
	for _, term := range terms {
		if term == "" {
			continue
		}
		if strings.Contains(content, term) {
			score += 10 + len(term)
		}
	}
	return score
}

func askRelevantExcerpt(content, question string, terms []string, limit int) string {
	content = strings.TrimSpace(content)
	if content == "" || limit <= 0 {
		return ""
	}

	candidates := append([]string{strings.TrimSpace(question)}, terms...)
	bestIndex := -1
	bestLength := 0
	for _, candidate := range candidates {
		index, length := foldedRuneIndex(content, candidate)
		if index >= 0 && (length > bestLength || length == bestLength && (bestIndex < 0 || index < bestIndex)) {
			bestIndex = index
			bestLength = length
		}
	}
	if bestIndex < 0 {
		return excerpt(content, limit)
	}

	runes := []rune(content)
	if len(runes) <= limit {
		return content
	}
	start := bestIndex - limit/3
	if start < 0 {
		start = 0
	}
	end := start + limit
	if end > len(runes) {
		end = len(runes)
		start = end - limit
		if start < 0 {
			start = 0
		}
	}
	result := string(runes[start:end])
	if start > 0 {
		result = "..." + result
	}
	if end < len(runes) {
		result += "..."
	}
	return result
}

func foldedRuneIndex(content, term string) (int, int) {
	haystack := []rune(strings.ToLower(content))
	needle := []rune(strings.ToLower(strings.TrimSpace(term)))
	if len(needle) == 0 || len(needle) > len(haystack) {
		return -1, 0
	}
	for i := 0; i+len(needle) <= len(haystack); i++ {
		matched := true
		for j := range needle {
			if haystack[i+j] != needle[j] {
				matched = false
				break
			}
		}
		if matched {
			return i, len(needle)
		}
	}
	return -1, 0
}

var askCitationPattern = regexp.MustCompile(`\[([1-9][0-9]*)\]`)

func citedAskSourceRefs(answer string, candidates []askSourceRef) []askSourceRef {
	byRank := make(map[int]askSourceRef, len(candidates))
	for _, source := range candidates {
		byRank[source.Rank] = source
	}
	seen := make(map[int]struct{}, len(candidates))
	refs := make([]askSourceRef, 0, len(candidates))
	for _, match := range askCitationPattern.FindAllStringSubmatch(answer, -1) {
		rank, err := strconv.Atoi(match[1])
		if err != nil {
			continue
		}
		source, ok := byRank[rank]
		if !ok {
			continue
		}
		if _, duplicate := seen[rank]; duplicate {
			continue
		}
		seen[rank] = struct{}{}
		refs = append(refs, source)
	}
	return refs
}

func askQueryTerms(question string) []string {
	question = strings.TrimSpace(strings.ToLower(question))
	if question == "" {
		return nil
	}

	seen := make(map[string]struct{})
	add := func(term string) {
		term = strings.TrimSpace(strings.ToLower(term))
		if utf8.RuneCountInString(term) < 2 {
			return
		}
		if _, ok := seen[term]; ok {
			return
		}
		seen[term] = struct{}{}
	}

	for _, field := range strings.FieldsFunc(question, func(r rune) bool {
		return unicode.IsSpace(r) || strings.ContainsRune("，。！？；：、,.!?;:()[]{}<>\"'`", r)
	}) {
		add(field)
	}

	if strings.IndexFunc(question, func(r rune) bool { return r > unicode.MaxASCII }) >= 0 {
		runes := []rune(question)
		for i := 0; i < len(runes); i++ {
			if i+1 < len(runes) && containsNonASCII(runes[i:i+2]) {
				add(string(runes[i : i+2]))
			}
			if i+2 < len(runes) && containsNonASCII(runes[i:i+3]) {
				add(string(runes[i : i+3]))
			}
		}
	}

	terms := make([]string, 0, len(seen))
	for term := range seen {
		terms = append(terms, term)
	}
	sort.Slice(terms, func(i, j int) bool { return terms[i] < terms[j] })
	return terms
}

func containsNonASCII(runes []rune) bool {
	for _, r := range runes {
		if r > unicode.MaxASCII {
			return true
		}
	}
	return false
}
