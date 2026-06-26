import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { askSourceTypesFromForm, collectAskContext } from "../app/lib/ai/ask-context";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { createSummary } from "../app/lib/db/summaries";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM summaries").run();
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entry_ai").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
}

describe("ask context", () => {
  beforeEach(resetDb);

  it("defaults to fragments and notes and excludes drafts", async () => {
    await createEntry(db, {
      entryDate: "2026-06-20",
      title: "短记录",
      body: "和小明在咖啡馆聊天。",
      kind: "fragment",
      tags: [],
    });
    await createEntry(db, {
      entryDate: "2026-06-21",
      title: "笔记",
      body: "认真写下和小明的谈话。",
      kind: "note",
      noteType: "daily",
      tags: [],
    });
    await createEntry(db, {
      entryDate: "2026-06-22",
      title: "草稿",
      body: "草稿里也提到了小明。",
      kind: "draft",
      tags: [],
    });

    const context = await collectAskContext(db, "小明");

    expect(context.entries.map((entry) => entry.title)).toEqual(["笔记", "短记录"]);
    expect(context.evidence).toContain("和小明在咖啡馆聊天");
    expect(context.evidence).not.toContain("草稿里也提到了小明");
  });

  it("can use AI generated entry insights without exposing the raw entry body", async () => {
    const id = await createEntry(db, {
      entryDate: "2026-06-20",
      title: "原文",
      body: "这段原文不应进入证据。",
      kind: "fragment",
      tags: [],
    });
    await env.DB.prepare(
      "INSERT INTO entry_ai (entry_id, summary, sentiment, model, generated_at) VALUES (?, ?, ?, ?, ?)",
    )
      .bind(id, "AI 总结里提到了海边散步。", "平静", "test", Date.now())
      .run();

    const context = await collectAskContext(db, "海边散步", ["entry-ai"]);

    expect(context.evidence).toContain("AI 总结里提到了海边散步");
    expect(context.evidence).not.toContain("这段原文不应进入证据");
    expect(context.citations[0]?.href).toBe(`/entries/${id}`);
  });

  it("can include generated summaries as supplemental evidence", async () => {
    const id = await createSummary(db, {
      scope: "topic",
      periodType: null,
      startDate: "2026-06-01",
      endDate: "2026-06-30",
      style: "brief",
      filter: { keyword: "项目" },
      title: "六月项目",
      content: "AI 总结中提到项目推进很顺利。",
      model: "test",
      sourceEntryIds: [],
    });

    const context = await collectAskContext(db, "项目推进", ["summary"]);

    expect(context.evidence).toContain("AI 总结中提到项目推进很顺利");
    expect(context.citations[0]).toMatchObject({
      id,
      href: `/ask#summary-${id}`,
      kind: "summary",
    });
  });

  it("handles long natural-language Chinese questions without complex LIKE patterns", async () => {
    await createEntry(db, {
      entryDate: "2026-06-23",
      title: "今天的时间",
      body: "系统里的今天是 2026-06-24。",
      kind: "note",
      tags: [],
    });

    const context = await collectAskContext(
      db,
      "不要根据记录，我问的是你系统里指定的今天是什么时间",
    );

    expect(context.entries.map((entry) => entry.title)).toContain("今天的时间");
  });

  it("parses source selections from form data and falls back to defaults", () => {
    const empty = new FormData();
    expect(askSourceTypesFromForm(empty)).toEqual(["fragment", "note"]);

    const form = new FormData();
    form.append("sources", "summary");
    form.append("sources", "entry-ai");
    form.append("sources", "unknown");
    expect(askSourceTypesFromForm(form)).toEqual(["summary", "entry-ai"]);
  });
});
