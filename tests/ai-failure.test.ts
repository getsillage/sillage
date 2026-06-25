import { describe, expect, it } from "vitest";
import { classifyAiFailure } from "../app/lib/ai/failure";

describe("classifyAiFailure", () => {
  it("flags a disabled provider with a settings hint", () => {
    const result = classifyAiFailure("AI text generation disabled");
    expect(result.category).toBe("disabled");
    expect(result.hint).toContain("设置");
  });

  it("flags a missing API key", () => {
    expect(classifyAiFailure("Anthropic API key not configured").category).toBe("no-key");
  });

  it("classifies truncation", () => {
    expect(classifyAiFailure("Anthropic 输出达到长度上限（max_tokens）").category).toBe(
      "truncated",
    );
  });

  it("classifies rate limiting from a 429", () => {
    expect(classifyAiFailure("OpenAI API returned 429 Too Many Requests").category).toBe(
      "rate-limited",
    );
  });

  it("classifies timeouts", () => {
    expect(classifyAiFailure("Request timed out after 30s").category).toBe("timeout");
  });

  it("classifies a model refusal", () => {
    expect(classifyAiFailure("Anthropic refused to answer").category).toBe("refusal");
  });

  it("classifies an empty response", () => {
    expect(classifyAiFailure("OpenAI 未返回可用文本").category).toBe("empty");
  });

  it("classifies a network failure", () => {
    expect(classifyAiFailure("fetch failed: ECONNRESET").category).toBe("network");
  });

  it("passes the empty-range summary sentence through verbatim", () => {
    const result = classifyAiFailure("所选范围内没有记录");
    expect(result.category).toBe("no-entries");
    expect(result.message).toBe("所选范围内没有记录");
  });

  it("falls back to unknown but keeps the raw reason", () => {
    const result = classifyAiFailure("weird boom");
    expect(result.category).toBe("unknown");
    expect(result.message).toContain("weird boom");
  });

  it("handles a missing reason", () => {
    expect(classifyAiFailure().category).toBe("unknown");
  });
});
