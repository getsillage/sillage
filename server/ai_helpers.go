package server

import (
	"fmt"
	"strings"

	"github.com/getsillage/sillage/store"
)

func (s *Server) acquireMemoAIJob() (func(), error) {
	return acquireAIJob(s.memoAIJobs)
}

func (s *Server) acquireAskAIJob() (func(), error) {
	return acquireAIJob(s.askAIJobs)
}

func acquireAIJob(sem chan struct{}) (func(), error) {
	select {
	case sem <- struct{}{}:
		return func() { <-sem }, nil
	default:
		return nil, errAIOverloaded
	}
}

func pickActiveAIProfile(profiles []*store.AIProfile) (*store.AIProfile, error) {
	var fallback *store.AIProfile
	for _, profile := range profiles {
		if profile == nil || !profile.Enabled {
			continue
		}
		if profile.Active {
			if profile.APIKeyEnvelope.Valid {
				return profile, nil
			}
			return nil, errAINotConfigured
		}
		if fallback == nil && profile.APIKeyEnvelope.Valid {
			fallback = profile
		}
	}
	if fallback != nil {
		return fallback, nil
	}
	return nil, errAINotConfigured
}

func memoSummarySystemPrompt() string {
	return strings.TrimSpace(`
你是 Sillage 的私人记录总结助手。
只能根据提供的单条记录内容总结，不要编造，不要诊断，不要延伸到记录之外。
输出简洁中文，尽量概括核心事实、状态变化和可见线索。`)
}

func memoSummaryUserPrompt(content string) string {
	var b strings.Builder
	b.WriteString("请根据下面这条记录生成简洁总结。\n\n")
	b.WriteString("记录：\n")
	b.WriteString(strings.TrimSpace(content))
	b.WriteString("\n\n要求：\n")
	b.WriteString("1. 只根据这条记录。\n")
	b.WriteString("2. 不要编造背景或结论。\n")
	b.WriteString("3. 输出一段中文总结。\n")
	return b.String()
}

func askSystemPrompt() string {
	return strings.TrimSpace(`
你是 Sillage 的私人记录问答助手。
只能根据提供的记录来源和对话历史回答，不要编造，不要做医学或心理诊断。
如果信息不足，就明确说“现有记录不足以判断”。
回答保持中文、简洁、具体。`)
}

func askUserPrompt(scope, question string, sources []askSourceRef) string {
	var b strings.Builder
	b.WriteString("上下文范围：")
	b.WriteString(askScopeLabel(scope))
	b.WriteString("\n\n当前问题：\n")
	b.WriteString(strings.TrimSpace(question))
	b.WriteString("\n\n可引用来源：\n")
	if len(sources) == 0 {
		b.WriteString("（无）\n")
	} else {
		for i, source := range sources {
			b.WriteString(fmt.Sprintf("%d. [%s] %s\n", i+1, source.EntryDate, source.Excerpt))
		}
	}
	b.WriteString("\n回答要求：\n")
	b.WriteString("1. 只能根据来源和对话历史回答。\n")
	b.WriteString("2. 若证据不足，直接说现有记录不足以判断。\n")
	b.WriteString("3. 如有引用，尽量用来源编号对应。\n")
	return b.String()
}

func askScopeLabel(scope string) string {
	switch scope {
	case "recent_7_days":
		return "最近 7 天"
	case "all":
		return "全部记录"
	default:
		return "最近 30 天"
	}
}
