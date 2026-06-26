import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { buildEntriesDigest, generateSummary } from "../app/lib/ai/summarize";
import { rangeForPeriod } from "../app/lib/date";
import { listEntriesByDateRange } from "../app/lib/db/calendar";
import { getDb } from "../app/lib/db/client";
import { createEntry, type EntryWithAi, getEntry } from "../app/lib/db/entries";
import {
  collectEntriesForTopic,
  createSummary,
  deleteSummary,
  findPeriodSummary,
  getSummary,
  listSummaries,
  updateSummary,
} from "../app/lib/db/summaries";
import { runSummaryAction } from "../app/lib/product/summary-actions";
import {
  isSummaryPeriodType,
  isSummaryScope,
  isSummaryStyle,
} from "../app/lib/product/summary-fields";
import { saveAiSettings } from "../app/lib/settings/ai-settings";
import { summaryGenerateFromData, summaryGenerateSchema } from "../app/lib/validation/summary";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM summaries").run();
  await env.DB.prepare("DELETE FROM entry_ai").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.SESSIONS.delete("ai-settings");
}

async function seedEntry(overrides: { entryDate: string; body?: string }): Promise<EntryWithAi> {
  const id = await createEntry(db, {
    entryDate: overrides.entryDate,
    body: overrides.body ?? "记录正文",
  });
  const entry = await getEntry(db, id);
  if (!entry) {
    throw new Error("seed entry not created");
  }
  return entry;
}

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    data.set(key, value);
  }
  return data;
}

async function configureAnthropic() {
  await saveAiSettings(env, {
    enabled: true,
    name: "Claude",
    protocol: "anthropic",
    baseUrl: "https://api.anthropic.com",
    model: "claude-test",
    apiKey: "sk-ant-web",
  });
}

describe("rangeForPeriod", () => {
  it("computes day/week/month/quarter/year windows", () => {
    expect(rangeForPeriod("day", "2026-06-24")).toEqual({
      startDate: "2026-06-24",
      endDate: "2026-06-24",
    });
    // 2026-06-24 is a Wednesday → week is Mon 22 .. Sun 28.
    expect(rangeForPeriod("week", "2026-06-24")).toEqual({
      startDate: "2026-06-22",
      endDate: "2026-06-28",
    });
    expect(rangeForPeriod("month", "2026-06-24")).toEqual({
      startDate: "2026-06-01",
      endDate: "2026-06-30",
    });
    expect(rangeForPeriod("quarter", "2026-06-24")).toEqual({
      startDate: "2026-04-01",
      endDate: "2026-06-30",
    });
    expect(rangeForPeriod("year", "2026-06-24")).toEqual({
      startDate: "2026-01-01",
      endDate: "2026-12-31",
    });
    // custom has no implicit window → falls back to the reference day.
    expect(rangeForPeriod("custom", "2026-06-24")).toEqual({
      startDate: "2026-06-24",
      endDate: "2026-06-24",
    });
  });
});

describe("summary field guards", () => {
  it("narrows valid values and rejects junk", () => {
    expect(isSummaryScope("period")).toBe(true);
    expect(isSummaryScope("nope")).toBe(false);
    expect(isSummaryPeriodType("all")).toBe(true);
    expect(isSummaryPeriodType("week")).toBe(true);
    expect(isSummaryPeriodType("decade")).toBe(false);
    expect(isSummaryStyle("narrative")).toBe(true);
    expect(isSummaryStyle("haiku")).toBe(false);
  });
});

describe("summaryGenerate validation", () => {
  it("accepts a period request and parses form data", () => {
    const raw = summaryGenerateFromData(
      form({ scope: "period", periodType: "week", style: "brief" }),
    );
    const parsed = summaryGenerateSchema.safeParse(raw);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.usePeriod).toBe(true);
      expect(parsed.data.useTopic).toBe(false);
    }
  });

  it("requires custom dates when periodType is custom", () => {
    const parsed = summaryGenerateSchema.safeParse(
      summaryGenerateFromData(form({ scope: "period", periodType: "custom", style: "brief" })),
    );
    expect(parsed.success).toBe(false);
  });

  it("rejects a custom range with start after end", () => {
    const parsed = summaryGenerateSchema.safeParse(
      summaryGenerateFromData(
        form({
          scope: "period",
          periodType: "custom",
          style: "brief",
          startDate: "2026-06-30",
          endDate: "2026-06-01",
        }),
      ),
    );
    expect(parsed.success).toBe(false);
  });

  it("requires a non-empty keyword for topic scope", () => {
    const empty = summaryGenerateSchema.safeParse(
      summaryGenerateFromData(form({ scope: "topic", style: "narrative" })),
    );
    expect(empty.success).toBe(false);

    const withKeyword = summaryGenerateSchema.safeParse(
      summaryGenerateFromData(form({ scope: "topic", style: "narrative", keyword: "工作" })),
    );
    expect(withKeyword.success).toBe(true);
    if (withKeyword.success) {
      expect(withKeyword.data.filter.keyword).toBe("工作");
      expect(withKeyword.data.usePeriod).toBe(false);
      expect(withKeyword.data.useTopic).toBe(true);
    }
  });

  it("accepts period and topic filters together", () => {
    const parsed = summaryGenerateSchema.safeParse(
      summaryGenerateFromData(
        form({
          scope: "period",
          usePeriod: "1",
          useTopic: "auto",
          periodType: "custom",
          startDate: "2026-06-01",
          endDate: "2026-06-30",
          style: "structured",
          keyword: "复盘",
        }),
      ),
    );
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.usePeriod).toBe(true);
      expect(parsed.data.useTopic).toBe(true);
      expect(parsed.data.periodType).toBe("custom");
      expect(parsed.data.startDate).toBe("2026-06-01");
      expect(parsed.data.endDate).toBe("2026-06-30");
      expect(parsed.data.filter.keyword).toBe("复盘");
    }
  });

  it("keeps auto topic disabled when topic fields are empty", () => {
    const parsed = summaryGenerateSchema.safeParse(
      summaryGenerateFromData(
        form({
          scope: "period",
          usePeriod: "1",
          useTopic: "auto",
          periodType: "all",
          style: "brief",
        }),
      ),
    );
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.usePeriod).toBe(true);
      expect(parsed.data.useTopic).toBe(false);
      expect(parsed.data.periodType).toBe("all");
    }
  });
});

describe("listEntriesByDateRange", () => {
  beforeEach(resetDb);

  it("returns live entries within an inclusive window, newest day first", async () => {
    await seedEntry({ entryDate: "2026-06-01", body: "早" });
    await seedEntry({ entryDate: "2026-06-15", body: "中" });
    await seedEntry({ entryDate: "2026-07-01", body: "晚" });

    const rows = await listEntriesByDateRange(db, "2026-06-01", "2026-06-30");
    expect(rows.map((row) => row.body)).toEqual(["中", "早"]);
  });
});

describe("summaries repository", () => {
  beforeEach(resetDb);

  it("creates, reads back parsed JSON, lists, and soft-deletes", async () => {
    const id = await createSummary(db, {
      scope: "topic",
      periodType: null,
      startDate: "2026-06-01",
      endDate: "2026-06-30",
      style: "structured",
      filter: { keyword: "项目" },
      title: "工作回顾",
      content: "正文",
      model: "test-model",
      sourceEntryIds: ["a", "b"],
      generatedAt: new Date("2026-06-30T00:00:00Z"),
    });

    const view = await getSummary(db, id);
    expect(view?.filter).toEqual({ keyword: "项目" });
    expect(view?.sourceEntryIds).toEqual(["a", "b"]);

    const all = await listSummaries(db);
    expect(all).toHaveLength(1);
    expect(await listSummaries(db, { scope: "period" })).toHaveLength(0);

    await deleteSummary(db, id);
    expect(await getSummary(db, id)).toBeNull();
    expect(await listSummaries(db)).toHaveLength(0);
  });

  it("orders by generatedAt descending", async () => {
    await createSummary(db, {
      scope: "period",
      periodType: "day",
      startDate: "2026-06-01",
      endDate: "2026-06-01",
      style: "brief",
      filter: null,
      title: "旧",
      content: "x",
      model: null,
      sourceEntryIds: [],
      generatedAt: new Date("2026-06-01T00:00:00Z"),
    });
    await createSummary(db, {
      scope: "period",
      periodType: "day",
      startDate: "2026-06-02",
      endDate: "2026-06-02",
      style: "brief",
      filter: null,
      title: "新",
      content: "y",
      model: null,
      sourceEntryIds: [],
      generatedAt: new Date("2026-06-02T00:00:00Z"),
    });
    const rows = await listSummaries(db);
    expect(rows.map((row) => row.title)).toEqual(["新", "旧"]);
  });

  it("finds a period summary by window and overwrites it on update", async () => {
    const id = await createSummary(db, {
      scope: "period",
      periodType: "week",
      startDate: "2026-06-22",
      endDate: "2026-06-28",
      style: "brief",
      filter: null,
      title: "本周",
      content: "旧内容",
      model: null,
      sourceEntryIds: [],
    });

    const found = await findPeriodSummary(db, "week", "2026-06-22", "2026-06-28");
    expect(found?.id).toBe(id);
    expect(await findPeriodSummary(db, "week", "2026-06-01", "2026-06-07")).toBeNull();

    await updateSummary(db, id, {
      title: "本周(新)",
      content: "新内容",
      model: "m2",
      style: "narrative",
      startDate: "2026-06-22",
      endDate: "2026-06-28",
      sourceEntryIds: ["e1"],
    });
    const updated = await getSummary(db, id);
    expect(updated?.title).toBe("本周(新)");
    expect(updated?.content).toBe("新内容");
    expect(updated?.sourceEntryIds).toEqual(["e1"]);
  });
});

describe("collectEntriesForTopic", () => {
  beforeEach(resetDb);

  it("returns an empty set for an empty filter", async () => {
    await seedEntry({ entryDate: "2026-06-01", body: "孤立" });
    expect(await collectEntriesForTopic(db, {})).toEqual([]);
  });

  it("matches by keyword and respects an optional window", async () => {
    await seedEntry({ entryDate: "2026-06-01", body: "工作在六月初" });
    const keyworded = await seedEntry({
      entryDate: "2026-06-04",
      body: "今天去爬山看日出",
    });
    await seedEntry({ entryDate: "2026-07-01", body: "七月也看到日出" });

    const byKeyword = await collectEntriesForTopic(db, { keyword: "日出" });
    expect(byKeyword.map((row) => row.id)).toContain(keyworded.id);

    const windowed = await collectEntriesForTopic(
      db,
      { keyword: "日出" },
      { startDate: "2026-06-01", endDate: "2026-06-30" },
    );
    expect(windowed.map((row) => row.id)).toEqual([keyworded.id]);
  });
});

describe("runSummaryAction", () => {
  beforeEach(resetDb);
  afterEach(() => vi.unstubAllGlobals());

  it("generates a pure all-time period summary when topic fields are empty", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "# 全部回顾\n\n所有记录被收束在一起。" }],
          stop_reason: "end_turn",
        }),
      ),
    );

    const older = await seedEntry({ entryDate: "2026-05-01", body: "早" });
    const newer = await seedEntry({ entryDate: "2026-07-01", body: "晚" });

    const result = await runSummaryAction(
      db,
      form({
        scope: "period",
        usePeriod: "1",
        useTopic: "auto",
        periodType: "all",
        style: "brief",
      }),
      "generate",
    );

    expect(result.ok).toBe(true);
    const [summary] = await listSummaries(db);
    expect(summary.scope).toBe("period");
    expect(summary.periodType).toBe("all");
    expect(summary.startDate).toBe("2026-05-01");
    expect(summary.endDate).toBe("2026-07-01");
    expect(summary.filter).toBeNull();
    expect(summary.sourceEntryIds).toEqual([newer.id, older.id]);
  });

  it("combines topic clues with a custom time range", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "# 六月工作\n\n六月里的工作重点被整理在一起。" }],
          stop_reason: "end_turn",
        }),
      ),
    );

    await seedEntry({ entryDate: "2026-05-30", body: "工作在五月" });
    const juneEntry = await seedEntry({
      entryDate: "2026-06-12",
      body: "工作在六月",
    });
    await seedEntry({ entryDate: "2026-07-01", body: "工作在七月" });

    const result = await runSummaryAction(
      db,
      form({
        scope: "topic",
        usePeriod: "1",
        useTopic: "1",
        periodType: "custom",
        startDate: "2026-06-01",
        endDate: "2026-06-30",
        style: "brief",
        keyword: "工作",
      }),
      "generate",
    );

    expect(result.ok).toBe(true);
    const [summary] = await listSummaries(db);
    expect(summary.scope).toBe("topic");
    expect(summary.periodType).toBe("custom");
    expect(summary.startDate).toBe("2026-06-01");
    expect(summary.endDate).toBe("2026-06-30");
    expect(summary.filter).toEqual({ keyword: "工作" });
    expect(summary.sourceEntryIds).toEqual([juneEntry.id]);
  });

  it("auto-detects topic clues across all time", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "# 工作重点\n\n所有工作记录被整理在一起。" }],
          stop_reason: "end_turn",
        }),
      ),
    );

    const older = await seedEntry({
      entryDate: "2026-05-30",
      body: "工作在五月",
    });
    const newer = await seedEntry({
      entryDate: "2026-07-01",
      body: "工作在七月",
    });

    const result = await runSummaryAction(
      db,
      form({
        scope: "period",
        usePeriod: "1",
        useTopic: "auto",
        periodType: "all",
        style: "brief",
        keyword: "工作",
      }),
      "generate",
    );

    expect(result.ok).toBe(true);
    const [summary] = await listSummaries(db);
    expect(summary.scope).toBe("topic");
    expect(summary.periodType).toBe("all");
    expect(summary.startDate).toBe("2026-05-30");
    expect(summary.endDate).toBe("2026-07-01");
    expect(summary.filter).toEqual({ keyword: "工作" });
    expect(summary.sourceEntryIds).toEqual([newer.id, older.id]);
  });
});

describe("buildEntriesDigest", () => {
  it("truncates long bodies and notes omitted entries beyond the cap", () => {
    const base: EntryWithAi = {
      id: "x",
      entryDate: "2026-06-01",
      body: "正文",
      version: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
      deletedAt: null,
      summary: null,
      sentiment: null,
      aiModel: null,
      aiDurationMs: null,
      aiGeneratedAt: null,
      aiGenerationCount: 0,
    };
    const many = Array.from({ length: 65 }, (_, index) => ({
      ...base,
      id: `e${index}`,
      body: "字".repeat(600),
    }));
    const digest = buildEntriesDigest(many);
    expect(digest).toContain("另有 5 条更早的记录未纳入");
    expect(digest).toContain("…"); // body was truncated
    expect(digest).toContain("【2026-06-01】");
  });
});

describe("generateSummary", () => {
  beforeEach(resetDb);
  afterEach(() => vi.unstubAllGlobals());

  it("skips with a reason when there are no entries", async () => {
    const draft = await generateSummary(env, {
      scope: "period",
      periodType: "day",
      startDate: "2026-06-01",
      endDate: "2026-06-01",
      style: "brief",
      entries: [],
    });
    expect(draft.ok).toBe(false);
    expect(draft.skippedReason).toBe("所选范围内没有记录");
  });

  it("skips when the provider is disabled", async () => {
    const entry = await seedEntry({ entryDate: "2026-06-01", body: "一天" });
    const draft = await generateSummary(env, {
      scope: "period",
      periodType: "day",
      startDate: "2026-06-01",
      endDate: "2026-06-01",
      style: "brief",
      entries: [entry],
    });
    expect(draft.ok).toBe(false);
    expect(draft.skippedReason).toContain("disabled");
  });

  it("splits a leading title line from the body on success", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "# 六月回顾\n\n这一周你过得平稳而充实。" }],
          stop_reason: "end_turn",
        }),
      ),
    );
    const entry = await seedEntry({ entryDate: "2026-06-22", body: "周中" });
    const draft = await generateSummary(env, {
      scope: "period",
      periodType: "week",
      startDate: "2026-06-22",
      endDate: "2026-06-28",
      style: "structured",
      entries: [entry],
    });
    expect(draft.ok).toBe(true);
    expect(draft.title).toBe("六月回顾");
    expect(draft.content).toBe("这一周你过得平稳而充实。");
    expect(draft.model).toBe("claude-test");
  });

  it("falls back to a derived title when the model omits one (topic + period)", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "没有标题行的正文。" }],
          stop_reason: "end_turn",
        }),
      ),
    );
    const entry = await seedEntry({ entryDate: "2026-06-01", body: "孤立" });

    const topic = await generateSummary(env, {
      scope: "topic",
      periodType: null,
      startDate: "2026-06-01",
      endDate: "2026-06-01",
      style: "narrative",
      entries: [entry],
      topicLabel: "关键词「工作」",
    });
    expect(topic.ok).toBe(true);
    expect(topic.title).toBe("关键词「工作」回顾");
    expect(topic.content).toBe("没有标题行的正文。");

    const day = await generateSummary(env, {
      scope: "period",
      periodType: "day",
      startDate: "2026-06-01",
      endDate: "2026-06-01",
      style: "brief",
      entries: [entry],
    });
    expect(day.title).toBe("2026-06-01 回顾");
  });

  it("retries with a larger budget when the summary is truncated", async () => {
    await configureAnthropic();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({
          content: [{ type: "text", text: "# 六月总结\n\n这一周刚开了个头" }],
          stop_reason: "max_tokens",
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          content: [{ type: "text", text: "# 六月总结\n\n这一周完整收束。" }],
          stop_reason: "end_turn",
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const entry = await seedEntry({ entryDate: "2026-06-22", body: "周中" });
    const draft = await generateSummary(env, {
      scope: "period",
      periodType: "week",
      startDate: "2026-06-22",
      endDate: "2026-06-28",
      style: "structured",
      entries: [entry],
    });

    expect(draft.ok).toBe(true);
    expect(draft.content).toBe("这一周完整收束。");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const firstBody = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    const secondBody = JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body));
    expect(firstBody.max_tokens).toBe(1600);
    expect(secondBody.max_tokens).toBe(2600);
    expect(secondBody.messages[0].content).toContain("请重新生成完整总结");
  });

  it("reports a length-limit reason when the retry is still truncated", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          content: [{ type: "text", text: "# 六月总结\n\n仍然不完整" }],
          stop_reason: "max_tokens",
        }),
      ),
    );

    const entry = await seedEntry({ entryDate: "2026-06-22", body: "周中" });
    const draft = await generateSummary(env, {
      scope: "period",
      periodType: "week",
      startDate: "2026-06-22",
      endDate: "2026-06-28",
      style: "structured",
      entries: [entry],
    });

    expect(draft.ok).toBe(false);
    expect(draft.skippedReason).toBe(
      "Anthropic 输出达到长度上限（max_tokens），请缩小范围或改用更短风格后重试",
    );
  });

  it("reports provider detail when the provider returns no usable text", async () => {
    await configureAnthropic();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({ content: [{ type: "text", text: "" }], stop_reason: "refusal" }),
      ),
    );
    const entry = await seedEntry({ entryDate: "2026-06-01" });
    const draft = await generateSummary(env, {
      scope: "period",
      periodType: "day",
      startDate: "2026-06-01",
      endDate: "2026-06-01",
      style: "brief",
      entries: [entry],
    });
    expect(draft.ok).toBe(false);
    expect(draft.skippedReason).toBe("Anthropic 未返回可用文本（refusal）");
  });
});
