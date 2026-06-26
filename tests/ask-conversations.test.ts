import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import {
  beginAskEdit,
  beginAskRegeneration,
  beginAskSend,
  completeAskAssistantMessage,
  getAskConversation,
  listAskConversationTree,
  saveAskMessageAsEntry,
  selectAskBranch,
  toggleAskConversationArchived,
  toggleAskConversationPinned,
} from "../app/lib/db/ask-conversations";
import { getDb } from "../app/lib/db/client";

const db = getDb(env.DB);

async function resetDb() {
  await env.DB.prepare("DELETE FROM ask_messages").run();
  await env.DB.prepare("DELETE FROM ask_conversations").run();
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
}

describe("ask conversation branch model", () => {
  beforeEach(resetDb);

  it("creates a thread and stores a completed assistant answer", async () => {
    const run = await beginAskSend(db, {
      question: "最近状态怎么样？",
      sourceTypes: ["entry"],
    });

    expect(run.conversation.title).toContain("最近状态");
    expect(run.userMessage.parentId).toBeNull();
    expect(run.assistantMessage.parentId).toBe(run.userMessage.id);
    expect(run.assistantMessage.status).toBe("running");

    await completeAskAssistantMessage(db, {
      messageId: run.assistantMessage.id,
      content: "整体稳定。",
      sources: [],
      model: "test-model",
      durationMs: 12,
    });

    const conversation = await getAskConversation(db, run.conversation.id);
    expect(conversation?.headMessageId).toBe(run.assistantMessage.id);
    expect(conversation?.messages.map((message) => message.content)).toEqual([
      "最近状态怎么样？",
      "整体稳定。",
    ]);
  });

  it("regenerates assistant messages as sibling branches", async () => {
    const run = await beginAskSend(db, {
      question: "我该怎么调整？",
      sourceTypes: ["entry"],
    });
    await completeAskAssistantMessage(db, {
      messageId: run.assistantMessage.id,
      content: "第一版回答",
      sources: [],
      model: null,
      durationMs: 1,
    });

    const regenerated = await beginAskRegeneration(
      db,
      run.conversation.id,
      run.assistantMessage.id,
    );
    expect(regenerated.status).toBe("created");
    if (regenerated.status !== "created") {
      throw new Error("expected regeneration");
    }
    expect(regenerated.assistantMessage.parentId).toBe(run.userMessage.id);
    expect(regenerated.assistantMessage.forkOfId).toBe(run.assistantMessage.id);

    const conversation = await getAskConversation(db, run.conversation.id);
    const assistant = conversation?.messages.at(-1);
    expect(assistant?.branch?.count).toBe(2);
    expect(assistant?.branch?.previousId).toBe(run.assistantMessage.id);
  });

  it("edits user messages as sibling branches and can switch back", async () => {
    const run = await beginAskSend(db, {
      question: "原问题",
      sourceTypes: ["entry"],
    });
    await completeAskAssistantMessage(db, {
      messageId: run.assistantMessage.id,
      content: "原回答",
      sources: [],
      model: null,
      durationMs: 1,
    });

    const edited = await beginAskEdit(db, {
      conversationId: run.conversation.id,
      messageId: run.userMessage.id,
      question: "编辑后的问题",
      sourceTypes: ["entry"],
    });
    expect(edited.status).toBe("created");
    if (edited.status !== "created") {
      throw new Error("expected edit branch");
    }
    expect(edited.userMessage.parentId).toBeNull();
    expect(edited.userMessage.forkOfId).toBe(run.userMessage.id);

    let conversation = await getAskConversation(db, run.conversation.id);
    expect(conversation?.messages[0]?.content).toBe("编辑后的问题");
    expect(conversation?.messages[0]?.branch?.count).toBe(2);

    await selectAskBranch(db, run.conversation.id, run.userMessage.id);
    conversation = await getAskConversation(db, run.conversation.id);
    expect(conversation?.messages[0]?.content).toBe("原问题");
    expect(conversation?.messages.at(-1)?.content).toBe("原回答");
  });

  it("toggles organization flags and saves an answer as a record", async () => {
    const run = await beginAskSend(db, { question: "保存什么？", sourceTypes: ["entry"] });
    await completeAskAssistantMessage(db, {
      messageId: run.assistantMessage.id,
      content: "这是一段可以保存的回答。",
      sources: [{ id: "e1", title: "来源", label: "来源", href: "/entries/e1", kind: "entry" }],
      model: null,
      durationMs: 1,
    });

    await toggleAskConversationPinned(db, run.conversation.id);
    await toggleAskConversationArchived(db, run.conversation.id);
    const conversation = await getAskConversation(db, run.conversation.id);
    expect(conversation?.pinnedAt).toBeInstanceOf(Date);
    expect(conversation?.archivedAt).toBeInstanceOf(Date);

    const saved = await saveAskMessageAsEntry(db, run.conversation.id, run.assistantMessage.id);
    expect(saved).toMatchObject({ ok: true, message: "已保存为记录" });
    const tree = await listAskConversationTree(db, run.conversation.id);
    expect(tree).toHaveLength(2);
  });
});
