import type { AiFailureCategory } from "./generation-result";

export interface ClassifiedFailure {
  category: AiFailureCategory;
  message: string;
  hint?: string;
}

/**
 * Turns a raw provider/skip reason into a readable category, message, and optional
 * next step. Centralizes what used to be duplicated `friendlyReason` helpers so the
 * entry-insight and summary flows speak with one voice.
 */
export function classifyAiFailure(reason?: string | null): ClassifiedFailure {
  if (!reason) {
    return { category: "unknown", message: "没能生成，稍后再试一次" };
  }
  const r = reason.toLowerCase();

  if (r.includes("disabled")) {
    return {
      category: "disabled",
      message: "AI 还没启用",
      hint: "到「设置」选一个提供商并开启",
    };
  }
  if (r.includes("key not configured") || r.includes("api key")) {
    return {
      category: "no-key",
      message: "还没有配置 API Key",
      hint: "到「设置」补全 API Key",
    };
  }
  if (r.includes("429") || r.includes("rate limit") || r.includes("too many")) {
    return { category: "rate-limited", message: "请求太密集了，缓一会儿再来" };
  }
  if (r.includes("timeout") || r.includes("timed out") || r.includes("etimedout")) {
    return { category: "timeout", message: "等待超时，稍后再试一次" };
  }
  if (
    r.includes("截断") ||
    r.includes("长度上限") ||
    r.includes("max_tokens") ||
    r.includes("max_output_tokens") ||
    r.includes("length")
  ) {
    return {
      category: "truncated",
      message: "这次写得太长被截断了",
      hint: "可以再生成一次",
    };
  }
  if (r.includes("refus") || r.includes("拒绝")) {
    return { category: "refusal", message: "模型这次没有作答，换个角度再试试" };
  }
  if (reason.includes("没有记录") || r.includes("no entries") || reason.includes("没有可")) {
    // The summary flow already produces a precise sentence here; keep it verbatim.
    return { category: "no-entries", message: reason };
  }
  if (r.includes("未返回") || r.includes("no usable text") || r.includes("empty")) {
    return { category: "empty", message: "模型没有返回内容，可以再试一次" };
  }
  if (
    r.includes("network") ||
    r.includes("fetch failed") ||
    r.includes("failed to fetch") ||
    r.includes("econnreset")
  ) {
    return { category: "network", message: "网络不太顺，检查连接后再试" };
  }
  return { category: "unknown", message: `没能生成：${reason}` };
}
