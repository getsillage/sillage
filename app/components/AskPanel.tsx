import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { Form, Link, useFetcher, useNavigate, useRevalidator } from "react-router";
import {
  ASK_SOURCE_TYPES,
  type AskCitation,
  type AskSourceType,
  DEFAULT_ASK_SOURCE_TYPES,
} from "~/lib/ai/ask-context";
import type {
  AskConversationSummary,
  AskConversationView,
  AskMessageView,
} from "~/lib/db/ask-conversations";
import { LazyMarkdown } from "./LazyMarkdown";
import { LocalDateTime } from "./LocalDateTime";
import { helperTextClass, inputClass, primaryButtonClass, subtleButtonClass } from "./ui";

interface AskActionData {
  intent?: string;
  ok: boolean;
  message: string;
}

interface AskPanelProps {
  conversations: AskConversationSummary[];
  currentConversation: AskConversationView | null;
  conversationQuery: string;
  includeArchived: boolean;
}

type AskRunMode = "send" | "edit" | "regenerate";

type AskStreamEvent =
  | {
      type: "created";
      conversationId: string;
      userMessage: AskMessageView;
      assistantMessage: AskMessageView;
    }
  | { type: "delta"; text: string }
  | { type: "sources"; sources: AskCitation[] }
  | { type: "done"; answer: string; model: string | null; durationMs: number }
  | { type: "error"; message: string; durationMs?: number };

const SOURCE_LABELS: Record<AskSourceType, string> = {
  fragment: "片段",
  note: "笔记",
  draft: "草稿",
  "entry-ai": "AI 洞察",
  summary: "AI 总结",
};

const STREAM_FLUSH_INTERVAL_MS = 80;

function toFormData(fields: Record<string, string | string[] | null | undefined>): FormData {
  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    if (value === null || value === undefined) {
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        form.append(key, item);
      }
    } else {
      form.set(key, value);
    }
  }
  return form;
}

function messageWithBranch(current: AskMessageView[], message: AskMessageView): AskMessageView[] {
  const existing = current.findIndex((item) => item.id === message.id);
  if (existing >= 0) {
    const next = [...current];
    next[existing] = { ...next[existing], ...message };
    return next;
  }
  return [...current, message];
}

function conversationHref(conversationId: string, includeArchived: boolean): string {
  return `/ask?conversation=${conversationId}${includeArchived ? "&archived=1" : ""}`;
}

function useAskStream() {
  const navigate = useNavigate();
  const revalidator = useRevalidator();
  const [runningMessageId, setRunningMessageId] = useState<string | null>(null);
  const [draftMessages, setDraftMessages] = useState<AskMessageView[] | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const partialRef = useRef("");
  const runningMessageIdRef = useRef<string | null>(null);
  const flushTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearFlushTimer = useCallback(() => {
    if (!flushTimerRef.current) {
      return;
    }
    clearTimeout(flushTimerRef.current);
    flushTimerRef.current = null;
  }, []);

  const flushPartial = useCallback(
    (messageId: string | null) => {
      if (!messageId) {
        return;
      }
      clearFlushTimer();
      setDraftMessages((current) =>
        current
          ? current.map((message) =>
              message.id === messageId
                ? { ...message, content: partialRef.current, status: "running" }
                : message,
            )
          : current,
      );
    },
    [clearFlushTimer],
  );

  const schedulePartialFlush = useCallback(
    (messageId: string | null) => {
      if (!messageId || flushTimerRef.current) {
        return;
      }
      flushTimerRef.current = setTimeout(() => {
        flushTimerRef.current = null;
        flushPartial(messageId);
      }, STREAM_FLUSH_INTERVAL_MS);
    },
    [flushPartial],
  );

  useEffect(
    () => () => {
      controllerRef.current?.abort();
      clearFlushTimer();
    },
    [clearFlushTimer],
  );

  async function stop() {
    const messageId = runningMessageIdRef.current;
    if (!messageId) {
      return;
    }
    controllerRef.current?.abort();
    clearFlushTimer();
    await fetch("/api/ask-stop", {
      method: "POST",
      body: toFormData({ messageId, content: partialRef.current }),
    });
    setDraftMessages((current) =>
      current
        ? current.map((message) =>
            message.id === messageId
              ? { ...message, content: partialRef.current, status: "interrupted" }
              : message,
          )
        : current,
    );
    setRunningMessageId(null);
    runningMessageIdRef.current = null;
    revalidator.revalidate();
  }

  async function run(fields: {
    mode: AskRunMode;
    conversationId?: string | null;
    messageId?: string;
    question?: string;
    sourceTypes: AskSourceType[];
    currentMessages: AskMessageView[];
  }) {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    partialRef.current = "";

    const response = await fetch("/api/ask-stream", {
      method: "POST",
      body: toFormData({
        mode: fields.mode,
        conversationId: fields.conversationId,
        messageId: fields.messageId,
        question: fields.question,
        sources: fields.sourceTypes,
      }),
      signal: controller.signal,
    });

    const reader = response.body?.getReader();
    if (!reader) {
      return;
    }
    const decoder = new TextDecoder();
    let buffer = "";
    let conversationId = fields.conversationId ?? null;

    async function handle(event: AskStreamEvent) {
      const activeMessageId = runningMessageIdRef.current;
      if (event.type === "created") {
        conversationId = event.conversationId;
        setRunningMessageId(event.assistantMessage.id);
        runningMessageIdRef.current = event.assistantMessage.id;
        setDraftMessages((current) =>
          messageWithBranch(
            messageWithBranch(current ?? fields.currentMessages, event.userMessage),
            event.assistantMessage,
          ),
        );
        navigate(`/ask?conversation=${event.conversationId}`, { replace: true });
        return;
      }
      if (event.type === "sources") {
        setDraftMessages((current) =>
          current
            ? current.map((message) =>
                message.id === activeMessageId ? { ...message, sources: event.sources } : message,
              )
            : current,
        );
        return;
      }
      if (event.type === "delta") {
        partialRef.current += event.text;
        schedulePartialFlush(activeMessageId);
        return;
      }
      if (event.type === "done") {
        clearFlushTimer();
        setDraftMessages((current) =>
          current
            ? current.map((message) =>
                message.id === activeMessageId
                  ? {
                      ...message,
                      content: event.answer,
                      status: "completed",
                      model: event.model,
                      durationMs: event.durationMs,
                    }
                  : message,
              )
            : current,
        );
        setRunningMessageId(null);
        runningMessageIdRef.current = null;
        revalidator.revalidate();
        return;
      }
      if (event.type === "error") {
        clearFlushTimer();
        setDraftMessages((current) =>
          current
            ? current.map((message) =>
                message.id === activeMessageId
                  ? {
                      ...message,
                      content: event.message,
                      status: "error",
                      durationMs: event.durationMs ?? message.durationMs,
                    }
                  : message,
              )
            : current,
        );
        setRunningMessageId(null);
        runningMessageIdRef.current = null;
        if (conversationId) {
          revalidator.revalidate();
        }
      }
    }

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";
        for (const line of lines) {
          if (line.trim()) {
            await handle(JSON.parse(line) as AskStreamEvent);
          }
        }
      }
      buffer += decoder.decode();
      if (buffer.trim()) {
        await handle(JSON.parse(buffer) as AskStreamEvent);
      }
    } catch {
      if (!controller.signal.aborted) {
        setRunningMessageId(null);
      }
    }
  }

  return {
    running: runningMessageId !== null,
    runningMessageId,
    draftMessages,
    setDraftMessages,
    run,
    stop,
  };
}

export function AskPanel({
  conversations,
  currentConversation,
  conversationQuery,
  includeArchived,
}: AskPanelProps) {
  const stream = useAskStream();
  const [input, setInput] = useState("");
  const [editing, setEditing] = useState<AskMessageView | null>(null);
  const [sourceTypes, setSourceTypes] = useState<AskSourceType[]>(() =>
    currentConversation?.sourceTypes.length
      ? currentConversation.sourceTypes
      : DEFAULT_ASK_SOURCE_TYPES,
  );
  const sourceTypesKey = (currentConversation?.sourceTypes ?? DEFAULT_ASK_SOURCE_TYPES).join(",");
  const setDraftMessages = stream.setDraftMessages;

  useEffect(() => {
    setDraftMessages(null);
    setEditing(null);
    setSourceTypes(
      sourceTypesKey ? (sourceTypesKey.split(",") as AskSourceType[]) : DEFAULT_ASK_SOURCE_TYPES,
    );
  }, [setDraftMessages, sourceTypesKey]);

  const messages = stream.draftMessages ?? currentConversation?.messages ?? [];
  const busy = stream.running;

  function submit() {
    const question = input.trim();
    if (!question || busy || sourceTypes.length === 0) {
      return;
    }
    stream.run({
      mode: editing ? "edit" : "send",
      conversationId: currentConversation?.id,
      messageId: editing?.id,
      question,
      sourceTypes,
      currentMessages: messages,
    });
    setInput("");
    setEditing(null);
  }

  function regenerate(message: AskMessageView) {
    if (!currentConversation || busy) {
      return;
    }
    stream.run({
      mode: "regenerate",
      conversationId: currentConversation.id,
      messageId: message.id,
      sourceTypes: message.sourceTypes.length ? message.sourceTypes : sourceTypes,
      currentMessages: messages,
    });
  }

  function startEdit(message: AskMessageView) {
    setEditing(message);
    setInput(message.content);
  }

  function toggleSource(type: AskSourceType) {
    setSourceTypes((current) =>
      current.includes(type) ? current.filter((value) => value !== type) : [...current, type],
    );
  }

  return (
    <section className="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
      <div className="grid gap-0 lg:min-h-[calc(100svh-220px)] lg:grid-cols-[220px_1fr]">
        <MobileConversationSwitcher
          conversations={conversations}
          currentConversation={currentConversation}
          conversationQuery={conversationQuery}
          includeArchived={includeArchived}
        />

        <aside className="hidden border-gray-200 border-b bg-gray-100/50 p-3 dark:border-gray-800 dark:bg-gray-950/50 lg:block lg:border-r lg:border-b-0">
          <div className="flex items-center justify-between gap-2">
            <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">探寻会话</h2>
            <Link to="/ask" className={subtleButtonClass}>
              新对话
            </Link>
          </div>
          <ConversationSearch
            conversationQuery={conversationQuery}
            includeArchived={includeArchived}
          />
          <ConversationList
            conversations={conversations}
            currentConversation={currentConversation}
            includeArchived={includeArchived}
          />
        </aside>

        <div className="flex h-[calc(100svh-132px)] min-h-[430px] flex-col lg:h-auto lg:min-h-[calc(100svh-220px)]">
          <ThreadHeader conversation={currentConversation} />

          <div className="flex-1 space-y-4 overflow-auto overscroll-contain px-3 py-4 sm:space-y-6 sm:px-6 sm:py-5 lg:px-8">
            {messages.length === 0 ? (
              <div className="mx-auto flex min-h-44 w-full max-w-2xl flex-col items-center justify-center rounded-lg border border-dashed border-gray-200 p-5 text-center sm:min-h-72 sm:p-8 dark:border-gray-800">
                <p className="font-serif text-gray-900 text-xl dark:text-gray-50">问问你的记忆</p>
                <p className={helperTextClass}>
                  可以检索、总结、复盘或讨论下一步；AI 会基于你勾选的记忆来源回答。
                </p>
              </div>
            ) : (
              messages.map((message) => (
                <ThreadMessage
                  key={message.id}
                  message={message}
                  conversationId={currentConversation?.id ?? ""}
                  busy={busy}
                  onEdit={startEdit}
                  onRegenerate={regenerate}
                />
              ))
            )}
          </div>

          <div className="border-gray-200 border-t bg-gray-50/95 p-2 backdrop-blur sm:p-4 dark:border-gray-800 dark:bg-gray-950/80">
            <details className="mb-2 sm:hidden">
              <summary className="cursor-pointer list-none px-2 py-1 text-gray-500 text-xs dark:text-gray-400">
                来源 · 已选 {sourceTypes.length} 项
              </summary>
              <div className="-mx-1 mt-1 flex gap-2 overflow-x-auto px-1 pb-1">
                {ASK_SOURCE_TYPES.map((type) => (
                  <SourceToggle
                    key={type}
                    type={type}
                    label={SOURCE_LABELS[type]}
                    checked={sourceTypes.includes(type)}
                    onChange={toggleSource}
                  />
                ))}
              </div>
            </details>
            <div className="mb-3 hidden flex-wrap gap-2 sm:flex">
              {ASK_SOURCE_TYPES.map((type) => (
                <SourceToggle
                  key={type}
                  type={type}
                  label={SOURCE_LABELS[type]}
                  checked={sourceTypes.includes(type)}
                  onChange={toggleSource}
                />
              ))}
            </div>
            {editing ? (
              <div className="mb-2 flex items-center justify-between rounded-lg bg-clay-50 px-3 py-2 text-clay-600 text-sm dark:bg-clay-900/50 dark:text-clay-300">
                <span>正在编辑一条旧问题，会创建新的分支。</span>
                <button type="button" className="font-medium" onClick={() => setEditing(null)}>
                  取消
                </button>
              </div>
            ) : null}
            <div className="mx-auto flex max-w-3xl items-end gap-2 rounded-xl border border-gray-200 bg-white p-2 pl-4 dark:border-gray-800 dark:bg-gray-900">
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    submit();
                  }
                }}
                rows={2}
                placeholder="比如：我最近状态怎么样？有哪些调整值得尝试？"
                className="max-h-32 min-h-10 min-w-0 flex-1 resize-none bg-transparent px-0 py-2 text-gray-900 text-sm leading-6 outline-none placeholder:text-gray-400 dark:text-gray-50 dark:placeholder:text-gray-500"
              />
              {busy ? (
                <button
                  type="button"
                  onClick={stream.stop}
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-celadon-600 font-medium text-white text-sm transition hover:bg-celadon-700 dark:bg-celadon-500 dark:text-gray-950 dark:hover:bg-celadon-400"
                >
                  <span aria-hidden="true">■</span>
                  <span className="sr-only">停止</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={submit}
                  disabled={input.trim().length === 0 || sourceTypes.length === 0}
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-celadon-600 font-medium text-white text-lg transition hover:bg-celadon-700 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-celadon-500 dark:text-gray-950 dark:hover:bg-celadon-400"
                >
                  <span aria-hidden="true">↑</span>
                  <span className="sr-only">发送</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function ConversationSearch({
  conversationQuery,
  includeArchived,
}: {
  conversationQuery: string;
  includeArchived: boolean;
}) {
  return (
    <Form method="get" className="mt-3 space-y-2">
      {includeArchived ? <input type="hidden" name="archived" value="1" /> : null}
      <input
        type="search"
        name="cq"
        defaultValue={conversationQuery}
        placeholder="搜索会话"
        className={`${inputClass} mt-0`}
      />
      <div className="flex items-center justify-between">
        <button type="submit" className={subtleButtonClass}>
          搜索
        </button>
        <Link
          to={includeArchived ? "/ask" : "/ask?archived=1"}
          className="text-gray-500 text-xs hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          {includeArchived ? "隐藏归档" : "查看归档"}
        </Link>
      </div>
    </Form>
  );
}

function ConversationList({
  conversations,
  currentConversation,
  includeArchived,
}: {
  conversations: AskConversationSummary[];
  currentConversation: AskConversationView | null;
  includeArchived: boolean;
}) {
  return (
    <nav className="mt-3 max-h-72 space-y-1 overflow-auto lg:max-h-[calc(100svh-420px)]">
      {conversations.map((conversation) => (
        <Link
          key={conversation.id}
          to={conversationHref(conversation.id, includeArchived)}
          className={`block rounded-lg px-3 py-2 text-sm transition ${
            currentConversation?.id === conversation.id
              ? "bg-celadon-50 text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200"
              : "text-gray-600 hover:bg-white dark:text-gray-300 dark:hover:bg-gray-900"
          }`}
        >
          <span className="flex items-center gap-1">
            {conversation.pinnedAt ? <span aria-hidden="true">★</span> : null}
            <span className="truncate font-medium">{conversation.title || "新的探寻"}</span>
          </span>
          <span className="mt-1 block truncate text-gray-400 text-xs dark:text-gray-500">
            {conversation.lastMessagePreview || "还没有消息"}
          </span>
        </Link>
      ))}
      {conversations.length === 0 ? (
        <p className="px-2 py-4 text-gray-400 text-sm dark:text-gray-500">没有会话。</p>
      ) : null}
    </nav>
  );
}

function MobileConversationSwitcher({
  conversations,
  currentConversation,
  conversationQuery,
  includeArchived,
}: AskPanelProps) {
  return (
    <div className="relative border-gray-200 border-b bg-white/95 backdrop-blur dark:border-gray-800 dark:bg-gray-900/95 lg:hidden">
      <div className="grid h-12 grid-cols-[44px_minmax(0,1fr)_44px_44px] items-center px-2">
        <details>
          <summary
            aria-label="会话列表"
            className="flex h-10 w-10 cursor-pointer list-none items-center justify-center rounded-lg text-gray-600 text-xl hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
          >
            <span aria-hidden="true">☰</span>
          </summary>
          <div className="absolute top-full right-0 left-0 z-20 border-gray-200 border-t bg-gray-50 px-3 pb-3 shadow-lg shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-950 dark:shadow-black/30">
            <ConversationSearch
              conversationQuery={conversationQuery}
              includeArchived={includeArchived}
            />
            <ConversationList
              conversations={conversations}
              currentConversation={currentConversation}
              includeArchived={includeArchived}
            />
          </div>
        </details>

        <div className="min-w-0 px-2 text-center">
          <p className="truncate font-medium text-gray-950 text-sm dark:text-gray-50">
            {currentConversation?.title || "新的探寻"}
          </p>
        </div>

        <Link
          to="/ask"
          aria-label="新对话"
          className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-600 text-xl hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
        >
          <span aria-hidden="true">＋</span>
        </Link>

        <details>
          <summary
            aria-label="会话选项"
            className="flex h-10 w-10 cursor-pointer list-none items-center justify-center rounded-lg text-gray-600 text-xl hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
          >
            <span aria-hidden="true">⋯</span>
          </summary>
          <div className="absolute top-full right-0 left-0 z-20 border-gray-200 border-t bg-gray-50 px-3 pb-3 shadow-lg shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-950 dark:shadow-black/30">
            {currentConversation ? (
              <div className="space-y-3 pt-3">
                <Form method="post" className="flex gap-2">
                  <input type="hidden" name="intent" value="renameAskConversation" />
                  <input type="hidden" name="conversationId" value={currentConversation.id} />
                  <input
                    name="title"
                    defaultValue={currentConversation.title}
                    placeholder="会话标题"
                    className={`${inputClass} mt-0 min-w-0 flex-1`}
                  />
                  <button type="submit" className={primaryButtonClass}>
                    保存
                  </button>
                </Form>
                <div className="flex flex-wrap gap-1">
                  <ActionButton intent="toggleAskPinned" conversationId={currentConversation.id}>
                    {currentConversation.pinnedAt ? "取消置顶" : "置顶"}
                  </ActionButton>
                  <ActionButton intent="toggleAskArchived" conversationId={currentConversation.id}>
                    {currentConversation.archivedAt ? "恢复" : "归档"}
                  </ActionButton>
                  <Link
                    to={`/download-ask-conversation?conversation=${currentConversation.id}`}
                    className={subtleButtonClass}
                  >
                    导出
                  </Link>
                  <ActionButton
                    intent="deleteAskConversation"
                    conversationId={currentConversation.id}
                    danger
                  >
                    删除
                  </ActionButton>
                </div>
              </div>
            ) : (
              <p className="px-2 py-4 text-gray-400 text-sm dark:text-gray-500">
                还没有选中的会话。
              </p>
            )}
          </div>
        </details>
      </div>
    </div>
  );
}

function ThreadHeader({ conversation }: { conversation: AskConversationView | null }) {
  const fetcher = useFetcher<AskActionData>();
  const [renaming, setRenaming] = useState(false);
  const [title, setTitle] = useState(conversation?.title ?? "");
  const conversationTitle = conversation?.title ?? "";

  useEffect(() => {
    setTitle(conversationTitle);
    setRenaming(false);
  }, [conversationTitle]);

  if (!conversation) {
    return (
      <header className="hidden border-gray-200 border-b p-3 sm:block sm:p-4 dark:border-gray-800">
        <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">新对话</h2>
      </header>
    );
  }

  return (
    <header className="hidden space-y-3 border-gray-200 border-b p-3 sm:block sm:p-4 dark:border-gray-800">
      <div className="flex flex-wrap items-center justify-between gap-2">
        {renaming ? (
          <fetcher.Form method="post" className="flex min-w-0 flex-1 flex-col gap-2 sm:flex-row">
            <input type="hidden" name="intent" value="renameAskConversation" />
            <input type="hidden" name="conversationId" value={conversation.id} />
            <input
              name="title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              className={`${inputClass} mt-0`}
            />
            <button type="submit" className={primaryButtonClass}>
              保存
            </button>
          </fetcher.Form>
        ) : (
          <div className="min-w-0">
            <h2 className="truncate font-medium text-gray-950 text-sm dark:text-gray-50">
              {conversation.title || "新的探寻"}
            </h2>
            <p className="text-gray-400 text-xs dark:text-gray-500">
              更新于 <LocalDateTime value={conversation.updatedAt} />
            </p>
          </div>
        )}
        <div className="-mx-1 flex flex-nowrap items-center gap-1 overflow-x-auto px-1 pb-1 sm:mx-0 sm:flex-wrap sm:overflow-visible sm:px-0 sm:pb-0">
          <button
            type="button"
            className={subtleButtonClass}
            onClick={() => setRenaming((v) => !v)}
          >
            重命名
          </button>
          <ActionButton intent="toggleAskPinned" conversationId={conversation.id}>
            {conversation.pinnedAt ? "取消置顶" : "置顶"}
          </ActionButton>
          <ActionButton intent="toggleAskArchived" conversationId={conversation.id}>
            {conversation.archivedAt ? "恢复" : "归档"}
          </ActionButton>
          <Link
            to={`/download-ask-conversation?conversation=${conversation.id}`}
            className={subtleButtonClass}
          >
            导出
          </Link>
          <ActionButton intent="deleteAskConversation" conversationId={conversation.id} danger>
            删除
          </ActionButton>
        </div>
      </div>
    </header>
  );
}

function ActionButton({
  intent,
  conversationId,
  children,
  danger,
}: {
  intent: string;
  conversationId: string;
  children: ReactNode;
  danger?: boolean;
}) {
  return (
    <Form method="post">
      <input type="hidden" name="intent" value={intent} />
      <input type="hidden" name="conversationId" value={conversationId} />
      <button
        type="submit"
        className={
          danger
            ? "inline-flex items-center justify-center rounded-lg px-3 py-2 font-medium text-red-600 text-sm transition hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/30"
            : subtleButtonClass
        }
      >
        {children}
      </button>
    </Form>
  );
}

function ThreadMessage({
  message,
  conversationId,
  busy,
  onEdit,
  onRegenerate,
}: {
  message: AskMessageView;
  conversationId: string;
  busy: boolean;
  onEdit: (message: AskMessageView) => void;
  onRegenerate: (message: AskMessageView) => void;
}) {
  const isUser = message.role === "user";
  if (isUser) {
    return (
      <div className="mx-auto flex w-full max-w-3xl justify-end">
        <div className="max-w-[82%] rounded-2xl rounded-br-sm border border-gray-200 bg-white px-4 py-2.5 text-gray-800 text-sm leading-7 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100">
          <p className="whitespace-pre-wrap">{message.content}</p>
          <div className="mt-2 flex flex-wrap items-center justify-end gap-2 text-xs">
            {message.branch ? (
              <BranchControls conversationId={conversationId} branch={message.branch} />
            ) : null}
            <button
              type="button"
              onClick={() => onEdit(message)}
              disabled={busy}
              className="text-gray-400 hover:text-gray-900 disabled:opacity-50 dark:text-gray-500 dark:hover:text-gray-100"
            >
              编辑
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl gap-3">
      <span className="mt-1 flex h-7 w-7 flex-none items-center justify-center rounded-full bg-celadon-50 text-celadon-600 dark:bg-celadon-900/40 dark:text-celadon-300">
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-4 w-4"
        >
          <path d="M12 4l1.7 5.2L19 11l-5.3 1.8L12 18l-1.7-5.2L5 11l5.3-1.8L12 4z" />
        </svg>
      </span>
      <div className="min-w-0 flex-1">
        <div className="font-serif text-[15px] leading-8 text-gray-900 dark:text-gray-50">
          {message.status === "running" && !message.content ? (
            <span className="text-gray-400 dark:text-gray-500">正在生成…</span>
          ) : message.status === "error" ? (
            <p className="text-red-600 dark:text-red-400">{message.content}</p>
          ) : message.status === "running" ? (
            <p className="whitespace-pre-wrap">{message.content}</p>
          ) : (
            <LazyMarkdown content={message.content} />
          )}
        </div>

        {message.status === "interrupted" ? (
          <p className="mt-2 text-clay-600 text-xs dark:text-clay-300">已中断，保留部分回答。</p>
        ) : null}

        {message.sources.length > 0 ? (
          <details className="mt-3">
            <summary className="cursor-pointer text-gray-400 text-xs tracking-wide dark:text-gray-500">
              引自你的记录 · {message.sources.length}
            </summary>
            <div className="mt-2 flex flex-col gap-1.5">
              {message.sources.map((source) => (
                <Link
                  key={`${message.id}-${source.id}`}
                  to={source.href}
                  className="w-fit rounded-lg bg-celadon-50 px-2.5 py-1 text-celadon-800 text-xs transition hover:bg-celadon-100 dark:bg-celadon-900/40 dark:text-celadon-200 dark:hover:bg-celadon-900/70"
                >
                  {source.label}
                </Link>
              ))}
            </div>
          </details>
        ) : null}

        <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
          {message.branch ? (
            <BranchControls conversationId={conversationId} branch={message.branch} />
          ) : null}
          <button
            type="button"
            onClick={() => onRegenerate(message)}
            disabled={busy || message.status === "running"}
            className="text-gray-400 hover:text-gray-900 disabled:opacity-50 dark:text-gray-500 dark:hover:text-gray-100"
          >
            重新生成
          </button>
          {message.status === "completed" || message.status === "interrupted" ? (
            <Form method="post">
              <input type="hidden" name="intent" value="saveAskDraft" />
              <input type="hidden" name="conversationId" value={conversationId} />
              <input type="hidden" name="messageId" value={message.id} />
              <button
                type="submit"
                className="text-gray-400 hover:text-gray-900 dark:text-gray-500 dark:hover:text-gray-100"
              >
                保存为草稿
              </button>
            </Form>
          ) : null}
          {message.model ? (
            <span className="text-gray-300 dark:text-gray-600">
              {message.model}
              {message.durationMs ? ` · ${(message.durationMs / 1000).toFixed(1)}s` : ""}
            </span>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function BranchControls({
  conversationId,
  branch,
}: {
  conversationId: string;
  branch: NonNullable<AskMessageView["branch"]>;
}) {
  return (
    <span className="inline-flex items-center gap-1 text-gray-400 dark:text-gray-500">
      <BranchButton conversationId={conversationId} messageId={branch.previousId} label="‹" />
      <span>
        {branch.index + 1}/{branch.count}
      </span>
      <BranchButton conversationId={conversationId} messageId={branch.nextId} label="›" />
    </span>
  );
}

function BranchButton({
  conversationId,
  messageId,
  label,
}: {
  conversationId: string;
  messageId: string | null;
  label: string;
}) {
  if (!messageId) {
    return <span className="px-1 opacity-30">{label}</span>;
  }
  return (
    <Form method="post" className="inline">
      <input type="hidden" name="intent" value="selectAskBranch" />
      <input type="hidden" name="conversationId" value={conversationId} />
      <input type="hidden" name="messageId" value={messageId} />
      <button type="submit" className="px-1 hover:text-gray-900 dark:hover:text-gray-100">
        {label}
      </button>
    </Form>
  );
}

function SourceToggle({
  type,
  label,
  checked,
  onChange,
}: {
  type: AskSourceType;
  label: string;
  checked: boolean;
  onChange: (type: AskSourceType) => void;
}) {
  return (
    <label
      className={`inline-flex shrink-0 cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1.5 text-xs transition sm:gap-2 sm:px-3 sm:text-sm ${
        checked
          ? "border-celadon-600 bg-celadon-50 text-celadon-800 dark:border-celadon-400 dark:bg-celadon-900/40 dark:text-celadon-200"
          : "border-gray-200 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-300 dark:hover:bg-gray-900"
      }`}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={() => onChange(type)}
        className="h-4 w-4 rounded border-gray-300 accent-celadon-600 dark:border-gray-700"
      />
      {label}
    </label>
  );
}
