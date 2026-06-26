import { and, asc, desc, eq, inArray, isNotNull, isNull, like, or, type SQL } from "drizzle-orm";
import {
  ASK_SOURCE_TYPES,
  type AskCitation,
  type AskSourceType,
  DEFAULT_ASK_SOURCE_TYPES,
} from "~/lib/ai/ask-context";
import { todayISO } from "~/lib/date";
import type { Db } from "./client";
import { createEntry } from "./entries";
import { uuidv7 } from "./id";
import { type AskConversation, type AskMessage, askConversations, askMessages } from "./schema";

export type AskMessageRole = "user" | "assistant";
export type AskMessageStatus = "running" | "completed" | "error" | "interrupted";

export interface AskConversationSummary {
  id: string;
  title: string;
  sourceTypes: AskSourceType[];
  headMessageId: string | null;
  pinnedAt: Date | null;
  archivedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
  messageCount: number;
  lastMessagePreview: string;
}

export interface AskMessageView extends Omit<AskMessage, "sources" | "sourceTypes"> {
  role: AskMessageRole;
  status: AskMessageStatus;
  sources: AskCitation[];
  sourceTypes: AskSourceType[];
  branch: AskBranchInfo | null;
}

export interface AskBranchInfo {
  index: number;
  count: number;
  previousId: string | null;
  nextId: string | null;
}

export interface AskConversationView extends Omit<AskConversation, "sourceTypes"> {
  sourceTypes: AskSourceType[];
  messages: AskMessageView[];
}

export interface AskDraftResult {
  ok: boolean;
  message: string;
  entryId?: string;
}

export interface AskConversationExport {
  conversation: AskConversationView;
  messages: AskMessageView[];
}

export const ASK_TITLE_LIMIT = 36;
const SUMMARY_LIMIT = 40;
const HISTORY_LIMIT = 4;

function parseJsonArray(raw: string | null): unknown[] {
  if (!raw) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function normalizeAskSourceTypes(value: unknown): AskSourceType[] {
  const values = Array.isArray(value) ? value : [];
  const sourceTypes = values.filter(
    (item): item is AskSourceType =>
      typeof item === "string" && ASK_SOURCE_TYPES.includes(item as AskSourceType),
  );
  return sourceTypes.length > 0 ? [...new Set(sourceTypes)] : [...DEFAULT_ASK_SOURCE_TYPES];
}

function parseSourceTypes(raw: string | null): AskSourceType[] {
  return normalizeAskSourceTypes(parseJsonArray(raw));
}

function parseSources(raw: string | null): AskCitation[] {
  return parseJsonArray(raw).filter((item): item is AskCitation => {
    if (!item || typeof item !== "object") {
      return false;
    }
    const source = item as AskCitation;
    return (
      typeof source.id === "string" &&
      typeof source.title === "string" &&
      typeof source.label === "string" &&
      typeof source.href === "string" &&
      (source.kind === "entry" || source.kind === "summary")
    );
  });
}

function serializeJson(value: unknown): string {
  return JSON.stringify(value);
}

function normalizeRole(value: string): AskMessageRole {
  return value === "assistant" ? "assistant" : "user";
}

function normalizeStatus(value: string): AskMessageStatus {
  return value === "running" ||
    value === "completed" ||
    value === "error" ||
    value === "interrupted"
    ? value
    : "completed";
}

function compactText(text: string): string {
  return text.replace(/\s+/g, " ").trim();
}

export function titleFromQuestion(question: string): string {
  const compact = compactText(question);
  if (compact.length <= ASK_TITLE_LIMIT) {
    return compact || "新的问答";
  }
  return `${compact.slice(0, ASK_TITLE_LIMIT - 1)}…`;
}

function preview(text: string): string {
  const compact = compactText(text);
  if (compact.length <= 64) {
    return compact;
  }
  return `${compact.slice(0, 63)}…`;
}

function toMessageView(row: AskMessage, branch: AskBranchInfo | null = null): AskMessageView {
  return {
    ...row,
    role: normalizeRole(row.role),
    status: normalizeStatus(row.status),
    sources: parseSources(row.sources),
    sourceTypes: parseSourceTypes(row.sourceTypes),
    branch,
  };
}

function toConversationView(row: AskConversation, messages: AskMessageView[]): AskConversationView {
  return {
    ...row,
    sourceTypes: parseSourceTypes(row.sourceTypes),
    messages,
  };
}

async function getConversationRow(db: Db, id: string): Promise<AskConversation | null> {
  const [row] = await db.select().from(askConversations).where(eq(askConversations.id, id));
  return row ?? null;
}

async function getMessageRow(db: Db, id: string): Promise<AskMessage | null> {
  const [row] = await db.select().from(askMessages).where(eq(askMessages.id, id));
  return row ?? null;
}

async function touchConversation(
  db: Db,
  id: string,
  patch: Partial<typeof askConversations.$inferInsert> = {},
): Promise<void> {
  await db
    .update(askConversations)
    .set({ ...patch, updatedAt: new Date() })
    .where(eq(askConversations.id, id));
}

async function messagesForConversation(db: Db, conversationId: string): Promise<AskMessage[]> {
  return db
    .select()
    .from(askMessages)
    .where(eq(askMessages.conversationId, conversationId))
    .orderBy(asc(askMessages.createdAt), asc(askMessages.id));
}

function messageMap(messages: AskMessage[]): Map<string, AskMessage> {
  return new Map(messages.map((message) => [message.id, message]));
}

function pathToHead(messages: AskMessage[], headMessageId: string | null): AskMessage[] {
  if (!headMessageId) {
    return [];
  }
  const byId = messageMap(messages);
  const path: AskMessage[] = [];
  const seen = new Set<string>();
  let current = byId.get(headMessageId);
  while (current && !seen.has(current.id)) {
    path.push(current);
    seen.add(current.id);
    current = current.parentId ? byId.get(current.parentId) : undefined;
  }
  return path.reverse();
}

function branchInfoForPath(messages: AskMessage[], path: AskMessage[]): Map<string, AskBranchInfo> {
  const byParent = new Map<string, AskMessage[]>();
  for (const message of messages) {
    const key = message.parentId ?? "__root__";
    const siblings = byParent.get(key) ?? [];
    siblings.push(message);
    byParent.set(key, siblings);
  }
  for (const siblings of byParent.values()) {
    siblings.sort(
      (a, b) => a.createdAt.getTime() - b.createdAt.getTime() || a.id.localeCompare(b.id),
    );
  }

  const result = new Map<string, AskBranchInfo>();
  for (const message of path) {
    const siblings = byParent.get(message.parentId ?? "__root__") ?? [];
    if (siblings.length <= 1) {
      continue;
    }
    const index = siblings.findIndex((sibling) => sibling.id === message.id);
    result.set(message.id, {
      index,
      count: siblings.length,
      previousId: siblings[index - 1]?.id ?? null,
      nextId: siblings[index + 1]?.id ?? null,
    });
  }
  return result;
}

function descendantLeaf(messages: AskMessage[], selectedId: string): string {
  const byParent = new Map<string, AskMessage[]>();
  for (const message of messages) {
    if (!message.parentId) {
      continue;
    }
    const children = byParent.get(message.parentId) ?? [];
    children.push(message);
    byParent.set(message.parentId, children);
  }
  for (const children of byParent.values()) {
    children.sort(
      (a, b) => b.createdAt.getTime() - a.createdAt.getTime() || b.id.localeCompare(a.id),
    );
  }

  let currentId = selectedId;
  const seen = new Set<string>();
  while (!seen.has(currentId)) {
    seen.add(currentId);
    const [child] = byParent.get(currentId) ?? [];
    if (!child) {
      break;
    }
    currentId = child.id;
  }
  return currentId;
}

export async function createAskConversation(
  db: Db,
  question: string,
  sourceTypes: AskSourceType[],
): Promise<AskConversation> {
  const id = uuidv7();
  const now = new Date();
  await db.insert(askConversations).values({
    id,
    title: titleFromQuestion(question),
    sourceTypes: serializeJson(sourceTypes),
    createdAt: now,
    updatedAt: now,
  });
  const row = await getConversationRow(db, id);
  if (!row) {
    throw new Error("failed to create ask conversation");
  }
  return row;
}

export async function listAskConversations(
  db: Db,
  options: { includeArchived?: boolean; query?: string; limit?: number } = {},
): Promise<AskConversationSummary[]> {
  const conditions: SQL[] = [];
  if (!options.includeArchived) {
    conditions.push(isNull(askConversations.archivedAt));
  }
  const query = options.query?.trim();
  if (query) {
    const pattern = `%${query}%`;
    conditions.push(
      or(
        like(askConversations.title, pattern),
        inArray(
          askConversations.id,
          db
            .select({ id: askMessages.conversationId })
            .from(askMessages)
            .where(like(askMessages.content, pattern)),
        ),
      ) as SQL,
    );
  }

  const rows = await db
    .select()
    .from(askConversations)
    .where(conditions.length > 0 ? and(...conditions) : undefined)
    .orderBy(desc(isNotNull(askConversations.pinnedAt)), desc(askConversations.updatedAt))
    .limit(options.limit ?? SUMMARY_LIMIT);

  if (rows.length === 0) {
    return [];
  }

  const ids = rows.map((row) => row.id);
  const messageRows = await db
    .select()
    .from(askMessages)
    .where(inArray(askMessages.conversationId, ids))
    .orderBy(asc(askMessages.createdAt), asc(askMessages.id));
  const byConversation = new Map<string, AskMessage[]>();
  for (const message of messageRows) {
    const list = byConversation.get(message.conversationId) ?? [];
    list.push(message);
    byConversation.set(message.conversationId, list);
  }

  return rows.map((row) => {
    const messages = byConversation.get(row.id) ?? [];
    const last = pathToHead(messages, row.headMessageId).at(-1) ?? messages.at(-1);
    return {
      id: row.id,
      title: row.title,
      sourceTypes: parseSourceTypes(row.sourceTypes),
      headMessageId: row.headMessageId,
      pinnedAt: row.pinnedAt,
      archivedAt: row.archivedAt,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
      messageCount: messages.length,
      lastMessagePreview: last ? preview(last.content) : "",
    };
  });
}

export async function getAskConversation(db: Db, id: string): Promise<AskConversationView | null> {
  const row = await getConversationRow(db, id);
  if (!row) {
    return null;
  }
  const messages = await messagesForConversation(db, id);
  const path = pathToHead(messages, row.headMessageId);
  const branchMap = branchInfoForPath(messages, path);
  return toConversationView(
    row,
    path.map((message) => toMessageView(message, branchMap.get(message.id) ?? null)),
  );
}

export async function listAskConversationTree(db: Db, id: string): Promise<AskMessageView[]> {
  const rows = await messagesForConversation(db, id);
  return rows.map((row) => toMessageView(row));
}

export async function getAskConversationExport(
  db: Db,
  id: string,
): Promise<AskConversationExport | null> {
  const conversation = await getAskConversation(db, id);
  const row = await getConversationRow(db, id);
  if (!conversation || !row) {
    return null;
  }
  return { conversation, messages: await listAskConversationTree(db, id) };
}

export function renderAskConversationMarkdown(exported: AskConversationExport): string {
  const { conversation, messages } = exported;
  const lines = [
    `# ${conversation.title || "问答会话"}`,
    "",
    `会话 ID：${conversation.id}`,
    `创建时间：${conversation.createdAt.toISOString()}`,
    `更新时间：${conversation.updatedAt.toISOString()}`,
    conversation.pinnedAt ? `置顶时间：${conversation.pinnedAt.toISOString()}` : "",
    conversation.archivedAt ? `归档时间：${conversation.archivedAt.toISOString()}` : "",
    "",
  ].filter(Boolean);

  for (const message of messages) {
    const role = message.role === "user" ? "用户" : "Sillage";
    const branch = message.forkOfId ? `（分支自 ${message.forkOfId}）` : "";
    lines.push(`## ${role} · ${message.createdAt.toISOString()}${branch}`);
    lines.push("");
    lines.push(message.content || "（空）");
    if (message.status !== "completed") {
      lines.push("");
      lines.push(`状态：${message.status}`);
    }
    if (message.sources.length > 0) {
      lines.push("");
      lines.push("引用来源：");
      for (const source of message.sources) {
        lines.push(`- [${source.label}](${source.href})`);
      }
    }
    lines.push("");
  }

  return lines.join("\n");
}

export async function beginAskSend(
  db: Db,
  input: {
    conversationId?: string | null;
    parentId?: string | null;
    forkOfId?: string | null;
    question: string;
    sourceTypes: AskSourceType[];
  },
): Promise<{
  conversation: AskConversation;
  userMessage: AskMessageView;
  assistantMessage: AskMessageView;
}> {
  const question = input.question.trim();
  const conversation =
    input.conversationId && (await getConversationRow(db, input.conversationId))
      ? await getConversationRow(db, input.conversationId)
      : await createAskConversation(db, question, input.sourceTypes);
  if (!conversation) {
    throw new Error("missing ask conversation");
  }

  const now = new Date();
  const userId = uuidv7();
  const assistantId = uuidv7();
  await db.insert(askMessages).values([
    {
      id: userId,
      conversationId: conversation.id,
      parentId: "parentId" in input ? (input.parentId ?? null) : conversation.headMessageId,
      forkOfId: input.forkOfId ?? null,
      role: "user",
      content: question,
      status: "completed",
      sourceTypes: serializeJson(input.sourceTypes),
      createdAt: now,
      updatedAt: now,
    },
    {
      id: assistantId,
      conversationId: conversation.id,
      parentId: userId,
      role: "assistant",
      content: "",
      status: "running",
      sourceTypes: serializeJson(input.sourceTypes),
      createdAt: now,
      updatedAt: now,
    },
  ]);
  await touchConversation(db, conversation.id, {
    sourceTypes: serializeJson(input.sourceTypes),
    headMessageId: assistantId,
    title: conversation.title.trim() ? conversation.title : titleFromQuestion(question),
  });

  const createdConversation = await getConversationRow(db, conversation.id);
  const userMessage = await getMessageRow(db, userId);
  const assistantMessage = await getMessageRow(db, assistantId);
  if (!createdConversation || !userMessage || !assistantMessage) {
    throw new Error("failed to create ask messages");
  }
  return {
    conversation: createdConversation,
    userMessage: toMessageView(userMessage),
    assistantMessage: toMessageView(assistantMessage),
  };
}

export async function beginAskRegeneration(
  db: Db,
  conversationId: string,
  assistantMessageId: string,
): Promise<
  | { status: "missing" }
  | { status: "invalid" }
  | {
      status: "created";
      conversation: AskConversation;
      userMessage: AskMessageView;
      assistantMessage: AskMessageView;
    }
> {
  const conversation = await getConversationRow(db, conversationId);
  const previous = await getMessageRow(db, assistantMessageId);
  if (!conversation || !previous || previous.conversationId !== conversationId) {
    return { status: "missing" };
  }
  if (normalizeRole(previous.role) !== "assistant" || !previous.parentId) {
    return { status: "invalid" };
  }
  const user = await getMessageRow(db, previous.parentId);
  if (!user || normalizeRole(user.role) !== "user") {
    return { status: "invalid" };
  }
  const now = new Date();
  const assistantId = uuidv7();
  const sourceTypes = parseSourceTypes(previous.sourceTypes ?? user.sourceTypes);
  await db.insert(askMessages).values({
    id: assistantId,
    conversationId,
    parentId: user.id,
    forkOfId: previous.id,
    role: "assistant",
    content: "",
    status: "running",
    sourceTypes: serializeJson(sourceTypes),
    createdAt: now,
    updatedAt: now,
  });
  await touchConversation(db, conversationId, {
    sourceTypes: serializeJson(sourceTypes),
    headMessageId: assistantId,
  });
  const nextConversation = await getConversationRow(db, conversationId);
  const assistant = await getMessageRow(db, assistantId);
  if (!nextConversation || !assistant) {
    throw new Error("failed to regenerate ask message");
  }
  return {
    status: "created",
    conversation: nextConversation,
    userMessage: toMessageView(user),
    assistantMessage: toMessageView(assistant),
  };
}

export async function beginAskEdit(
  db: Db,
  input: {
    conversationId: string;
    messageId: string;
    question: string;
    sourceTypes: AskSourceType[];
  },
): Promise<
  | { status: "missing" }
  | { status: "invalid" }
  | {
      status: "created";
      conversation: AskConversation;
      userMessage: AskMessageView;
      assistantMessage: AskMessageView;
    }
> {
  const conversation = await getConversationRow(db, input.conversationId);
  const previous = await getMessageRow(db, input.messageId);
  if (!conversation || !previous || previous.conversationId !== input.conversationId) {
    return { status: "missing" };
  }
  if (normalizeRole(previous.role) !== "user") {
    return { status: "invalid" };
  }
  return {
    status: "created",
    ...(await beginAskSend(db, {
      conversationId: input.conversationId,
      parentId: previous.parentId,
      forkOfId: previous.id,
      question: input.question,
      sourceTypes: input.sourceTypes,
    })),
  };
}

export async function completeAskAssistantMessage(
  db: Db,
  input: {
    messageId: string;
    content: string;
    sources: AskCitation[];
    model: string | null;
    durationMs: number;
  },
): Promise<boolean> {
  const now = new Date();
  const updated = await db
    .update(askMessages)
    .set({
      content: input.content,
      sources: serializeJson(input.sources),
      model: input.model,
      durationMs: input.durationMs,
      status: "completed",
      updatedAt: now,
    })
    .where(and(eq(askMessages.id, input.messageId), eq(askMessages.status, "running")))
    .returning({ conversationId: askMessages.conversationId });
  const conversationId = updated[0]?.conversationId;
  if (!conversationId) {
    return false;
  }
  await touchConversation(db, conversationId, { headMessageId: input.messageId });
  return true;
}

export async function failAskAssistantMessage(
  db: Db,
  messageId: string,
  message: string,
  durationMs?: number,
): Promise<void> {
  const now = new Date();
  const updated = await db
    .update(askMessages)
    .set({
      content: message,
      status: "error",
      durationMs,
      updatedAt: now,
    })
    .where(and(eq(askMessages.id, messageId), eq(askMessages.status, "running")))
    .returning({ conversationId: askMessages.conversationId });
  const conversationId = updated[0]?.conversationId;
  if (conversationId) {
    await touchConversation(db, conversationId, { headMessageId: messageId });
  }
}

export async function interruptAskAssistantMessage(
  db: Db,
  messageId: string,
  content: string,
): Promise<void> {
  const now = new Date();
  const updated = await db
    .update(askMessages)
    .set({ content, status: "interrupted", updatedAt: now })
    .where(and(eq(askMessages.id, messageId), inArray(askMessages.status, ["running", "error"])))
    .returning({ conversationId: askMessages.conversationId });
  const conversationId = updated[0]?.conversationId;
  if (conversationId) {
    await touchConversation(db, conversationId, { headMessageId: messageId });
  }
}

export async function historyBeforeMessage(
  db: Db,
  conversationId: string,
  parentId: string | null,
): Promise<Array<{ question: string; answer: string }>> {
  const messages = await messagesForConversation(db, conversationId);
  const path = pathToHead(messages, parentId);
  const turns: Array<{ question: string; answer: string }> = [];
  for (let i = 0; i < path.length - 1; i++) {
    const user = path[i];
    const assistant = path[i + 1];
    if (
      user &&
      assistant &&
      normalizeRole(user.role) === "user" &&
      normalizeRole(assistant.role) === "assistant" &&
      normalizeStatus(assistant.status) === "completed"
    ) {
      turns.push({ question: user.content, answer: assistant.content });
      i += 1;
    }
  }
  return turns.slice(-HISTORY_LIMIT);
}

export async function selectAskBranch(
  db: Db,
  conversationId: string,
  messageId: string,
): Promise<boolean> {
  const row = await getConversationRow(db, conversationId);
  if (!row) {
    return false;
  }
  const messages = await messagesForConversation(db, conversationId);
  if (!messages.some((message) => message.id === messageId)) {
    return false;
  }
  const headMessageId = descendantLeaf(messages, messageId);
  await touchConversation(db, conversationId, { headMessageId });
  return true;
}

export async function renameAskConversation(db: Db, id: string, title: string): Promise<void> {
  const nextTitle = compactText(title).slice(0, 80) || "新的问答";
  await touchConversation(db, id, { title: nextTitle });
}

export async function toggleAskConversationPinned(db: Db, id: string): Promise<void> {
  const row = await getConversationRow(db, id);
  if (!row) {
    return;
  }
  await touchConversation(db, id, { pinnedAt: row.pinnedAt ? null : new Date() });
}

export async function toggleAskConversationArchived(db: Db, id: string): Promise<void> {
  const row = await getConversationRow(db, id);
  if (!row) {
    return;
  }
  await touchConversation(db, id, { archivedAt: row.archivedAt ? null : new Date() });
}

export async function deleteAskConversation(db: Db, id: string): Promise<void> {
  await db.delete(askConversations).where(eq(askConversations.id, id));
}

function sourcesMarkdown(sources: AskCitation[]): string {
  if (sources.length === 0) {
    return "";
  }
  return ["", "引用来源：", ...sources.map((source) => `- [${source.label}](${source.href})`)].join(
    "\n",
  );
}

export async function saveAskMessageAsEntry(
  db: Db,
  conversationId: string,
  messageId: string,
): Promise<AskDraftResult> {
  const message = await getMessageRow(db, messageId);
  if (!message || message.conversationId !== conversationId) {
    return { ok: false, message: "未找到这条回答" };
  }
  if (normalizeRole(message.role) !== "assistant") {
    return { ok: false, message: "只能把回答保存为记录" };
  }
  const status = normalizeStatus(message.status);
  if (status !== "completed" && status !== "interrupted") {
    return { ok: false, message: "这条回答还不能保存为记录" };
  }
  const parent = message.parentId ? await getMessageRow(db, message.parentId) : null;
  const sources = parseSources(message.sources);
  const entryId = await createEntry(db, {
    entryDate: todayISO(),
    body: [`> ${parent?.content ?? "AI 回答"}`, "", message.content, sourcesMarkdown(sources)]
      .filter(Boolean)
      .join("\n"),
  });
  return { ok: true, message: "已保存为记录", entryId };
}
