import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { exportSillageBackup, runScheduledBackup } from "../app/lib/backup/export";
import { beginAskSend, completeAskAssistantMessage } from "../app/lib/db/ask-conversations";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { putAttachment } from "../app/lib/storage/attachments";

const db = getDb(env.DB);
const KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

function bytes(text: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(text) as Uint8Array<ArrayBuffer>;
}

describe("Sillage backup export", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM attachments").run();
    await env.DB.prepare("DELETE FROM ask_messages").run();
    await env.DB.prepare("DELETE FROM ask_conversations").run();
    await env.DB.prepare("DELETE FROM entries").run();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("writes JSON and Markdown backups to R2", async () => {
    const entryId = await createEntry(db, {
      entryDate: "2026-06-23",
      body: "今天看到了漂亮夕阳。",
    });
    await putAttachment(db, env.BLOBS, KEY, {
      entryId,
      bytes: bytes("image"),
      filename: "photo.png",
      contentType: "image/png",
    });

    const exportedAt = new Date("2026-06-24T03:04:05.000Z");
    const result = await exportSillageBackup(env, exportedAt);

    expect(result).toEqual({
      jsonKey: "backups/2026-06-24/sillage-2026-06-24T03-04-05-000Z.json",
      markdownKey: "backups/2026-06-24/sillage-2026-06-24T03-04-05-000Z.md",
      entryCount: 1,
    });

    const jsonObject = await env.BLOBS.get(result.jsonKey);
    const markdownObject = await env.BLOBS.get(result.markdownKey);
    expect(jsonObject?.httpMetadata?.contentType).toBe("application/json; charset=utf-8");
    expect(markdownObject?.httpMetadata?.contentType).toBe("text/markdown; charset=utf-8");

    const payload = await jsonObject?.json<{
      version: 1;
      exportedAt: string;
      entries: Array<{
        id: string;
        entryDate: string;
        body: string;
      }>;
      attachments: Array<{ filename: string; r2Key: string }>;
    }>();
    expect(payload?.exportedAt).toBe("2026-06-24T03:04:05.000Z");
    expect(payload?.entries[0]).toMatchObject({
      id: entryId,
      entryDate: "2026-06-23",
      body: "今天看到了漂亮夕阳。",
    });
    expect(payload?.entries[0]).not.toHaveProperty("title");
    expect(payload?.entries[0]).not.toHaveProperty("tags");
    expect(payload?.attachments[0]?.filename).toBe("photo.png");
    expect(await markdownObject?.text()).toContain("## 2026-06-23");
  });

  it("renders summary/sentiment lines", async () => {
    const entryId = await createEntry(db, {
      entryDate: "2026-06-20",
      body: "无标题正文",
    });
    await env.DB.prepare("INSERT INTO entry_ai (entry_id, summary, sentiment) VALUES (?, ?, ?)")
      .bind(entryId, "一段摘要", "积极")
      .run();

    const { markdownKey } = await exportSillageBackup(env, new Date("2026-06-21T00:00:00.000Z"));
    const markdown = (await (await env.BLOBS.get(markdownKey))?.text()) ?? "";

    expect(markdown).toContain("## 2026-06-20");
    expect(markdown).toContain("摘要：一段摘要");
    expect(markdown).toContain("情绪：积极");
    expect(markdown).not.toContain("标签：");
    expect(markdown).not.toContain("心情：");
    expect(markdown).not.toContain("天气：");
  });

  it("runScheduledBackup writes a backup on success", async () => {
    await createEntry(db, { entryDate: "2026-06-19", body: "B" });

    await expect(runScheduledBackup(env)).resolves.toBeUndefined();

    const listing = await env.BLOBS.list({ prefix: "backups/" });
    expect(listing.objects.length).toBeGreaterThan(0);
  });

  it("runScheduledBackup logs context and rethrows when R2 fails", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const failingEnv = {
      ...env,
      BLOBS: {
        put: async () => {
          throw new Error("r2 unavailable");
        },
      },
    } as unknown as Env;

    await expect(runScheduledBackup(failingEnv)).rejects.toThrow("r2 unavailable");
    expect(errorSpy).toHaveBeenCalledOnce();
    expect(String(errorSpy.mock.calls[0]?.[0])).toContain("sillage-backup");
  });

  it("exports ask conversations in JSON and Markdown backups", async () => {
    const run = await beginAskSend(db, {
      question: "最近有什么重点？",
      sourceTypes: ["entry"],
    });
    await completeAskAssistantMessage(db, {
      messageId: run.assistantMessage.id,
      content: "你反复提到散步和早睡。",
      sources: [],
      model: "test-model",
      durationMs: 8,
    });

    const result = await exportSillageBackup(env, new Date("2026-06-25T00:00:00.000Z"));
    const jsonObject = await env.BLOBS.get(result.jsonKey);
    const payload = await jsonObject?.json<{
      askConversations: Array<{ title: string; messages: Array<{ content: string }> }>;
    }>();
    expect(payload?.askConversations[0]?.title).toContain("最近有什么重点");
    expect(payload?.askConversations[0]?.messages.at(-1)?.content).toBe("你反复提到散步和早睡。");

    const markdown = (await (await env.BLOBS.get(result.markdownKey))?.text()) ?? "";
    expect(markdown).toContain("# 问答会话");
    expect(markdown).toContain("你反复提到散步和早睡。");
  });
});
