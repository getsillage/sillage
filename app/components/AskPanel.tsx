import { type ReactNode, type RefObject, useCallback, useEffect, useRef, useState } from "react";
import { Form, Link, useFetcher, useNavigate, useRevalidator } from "react-router";
import {
  ASK_SOURCE_TYPES,
  type AskCitation,
  type AskSourceType,
  DEFAULT_ASK_SOURCE_TYPES,
} from "~/lib/ai/ask-context";
import type { AskConversationView, AskMessageView } from "~/lib/db/ask-conversations";
import type { EntryWithAi } from "~/lib/db/entries";
import { LazyMarkdown } from "./LazyMarkdown";
import { LocalDateTime } from "./LocalDateTime";
import { inputClass, primaryButtonClass, subtleButtonClass } from "./ui";

interface AskActionData {
  intent?: string;
  ok: boolean;
  message: string;
}

interface AskPanelProps {
  query: string;
  results: EntryWithAi[];
  currentConversation: AskConversationView | null;
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
  entry: "记录",
  "entry-ai": "单条总结",
  summary: "总结",
};

const STARTER_PROMPTS = [
  "最近我反复提到什么？",
  "这一周有哪些重要内容？",
  "我最近的状态有什么变化？",
  "哪些事情反复出现了？",
] as const;

const STREAM_FLUSH_INTERVAL_MS = 80;
const menuItemClass =
  "block w-full rounded-lg px-3 py-2 text-left text-sm text-gray-700 transition hover:bg-gray-100 hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-celadon-400/30";
const dangerMenuItemClass =
  "block w-full rounded-lg px-3 py-2 text-left text-sm text-red-600 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600/20 dark:text-red-400 dark:hover:bg-red-950/30";

function resizeTextarea(textarea: HTMLTextAreaElement | null) {
  if (!textarea) {
    return;
  }
  textarea.style.height = "auto";
  textarea.style.height = `${Math.min(textarea.scrollHeight, 192)}px`;
}

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

export function AskPanel({ query, results, currentConversation }: AskPanelProps) {
  const stream = useAskStream();
  const [input, setInput] = useState("");
  const [editing, setEditing] = useState<AskMessageView | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const threadEndRef = useRef<HTMLDivElement | null>(null);
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

  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ block: "end" });
  });

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
    requestAnimationFrame(() => resizeTextarea(textareaRef.current));
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
    requestAnimationFrame(() => {
      resizeTextarea(textareaRef.current);
      textareaRef.current?.focus();
    });
  }

  function toggleSource(type: AskSourceType) {
    setSourceTypes((current) =>
      current.includes(type) ? current.filter((value) => value !== type) : [...current, type],
    );
  }

  function useSuggestion(prompt: string) {
    setInput(prompt);
    requestAnimationFrame(() => {
      resizeTextarea(textareaRef.current);
      textareaRef.current?.focus();
    });
  }

  function cancelEdit() {
    setEditing(null);
    setInput("");
    requestAnimationFrame(() => resizeTextarea(textareaRef.current));
  }

  return (
    <section className="flex h-full min-h-0 flex-col overflow-hidden bg-gray-50 text-gray-900 dark:bg-gray-950 dark:text-gray-50">
      <ThreadHeader conversation={currentConversation} />

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        <div
          className={`mx-auto flex min-h-full w-full max-w-3xl flex-col px-4 sm:px-6 ${
            messages.length === 0 ? "justify-center py-10" : "gap-7 py-6 sm:py-8"
          }`}
        >
          {messages.length === 0 ? (
            <EmptyState query={query} results={results} onSuggestion={useSuggestion} />
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
          <div ref={threadEndRef} />
        </div>
      </div>

      <Composer
        input={input}
        setInput={setInput}
        textareaRef={textareaRef}
        editing={editing}
        busy={busy}
        sourceTypes={sourceTypes}
        onSubmit={submit}
        onStop={stream.stop}
        onCancelEdit={cancelEdit}
        onToggleSource={toggleSource}
      />
    </section>
  );
}

function EmptyState({
  query,
  results,
  onSuggestion,
}: {
  query: string;
  results: EntryWithAi[];
  onSuggestion: (prompt: string) => void;
}) {
  return (
    <div className="mx-auto w-full max-w-2xl text-center">
      <p className="font-serif text-2xl text-gray-900 sm:text-3xl dark:text-gray-50">
        今天想回看什么？
      </p>
      <p className="mx-auto mt-2 max-w-lg text-gray-500 text-sm leading-6 dark:text-gray-400">
        直接提问、继续追问，Sillage 会基于你的记录和可选总结回答。
      </p>

      <div className="mt-7 grid gap-2 sm:grid-cols-2">
        {STARTER_PROMPTS.map((prompt) => (
          <button
            key={prompt}
            type="button"
            onClick={() => onSuggestion(prompt)}
            className="min-h-16 rounded-lg border border-gray-200 bg-white px-4 py-3 text-left text-gray-700 text-sm leading-6 transition hover:border-celadon-200 hover:bg-celadon-50 hover:text-celadon-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-200 dark:hover:border-celadon-800 dark:hover:bg-celadon-900/40 dark:hover:text-celadon-200 dark:focus-visible:ring-celadon-400/30"
          >
            {prompt}
          </button>
        ))}
      </div>

      {query ? (
        <section className="mt-7 rounded-lg border border-gray-200 bg-white p-4 text-left dark:border-gray-800 dark:bg-gray-900">
          <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">搜索「{query}」</h2>
          {results.length === 0 ? (
            <p className="mt-2 text-gray-400 text-sm dark:text-gray-500">
              没有找到相关记录。可以换一个词，或者直接把它作为问题问下去。
            </p>
          ) : (
            <div className="mt-3 space-y-2">
              {results.slice(0, 3).map((entry) => (
                <Link
                  key={entry.id}
                  to={`/entries/${entry.id}`}
                  className="block rounded-lg px-3 py-2 text-gray-600 text-sm transition hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                >
                  <span className="block text-gray-400 text-xs dark:text-gray-500">
                    {entry.entryDate}
                  </span>
                  <span className="line-clamp-1">{entry.body || "空白记录"}</span>
                </Link>
              ))}
              <button
                type="button"
                onClick={() => onSuggestion(`围绕“${query}”，帮我整理相关记录里的重点。`)}
                className="mt-1 text-celadon-700 text-sm hover:text-celadon-900 dark:text-celadon-200 dark:hover:text-celadon-100"
              >
                用这个搜索词继续提问
              </button>
            </div>
          )}
        </section>
      ) : null}
    </div>
  );
}

function Composer({
  input,
  setInput,
  textareaRef,
  editing,
  busy,
  sourceTypes,
  onSubmit,
  onStop,
  onCancelEdit,
  onToggleSource,
}: {
  input: string;
  setInput: (value: string) => void;
  textareaRef: RefObject<HTMLTextAreaElement | null>;
  editing: AskMessageView | null;
  busy: boolean;
  sourceTypes: AskSourceType[];
  onSubmit: () => void;
  onStop: () => void;
  onCancelEdit: () => void;
  onToggleSource: (type: AskSourceType) => void;
}) {
  return (
    <div className="bg-gradient-to-t from-gray-50 via-gray-50/95 to-gray-50/40 px-3 pt-2 pb-3 backdrop-blur dark:from-gray-950 dark:via-gray-950/95 dark:to-gray-950/40">
      <div className="mx-auto w-full max-w-3xl">
        {editing ? (
          <div className="mb-2 flex items-center justify-between gap-3 rounded-lg border border-clay-200 bg-clay-50 px-3 py-2 text-clay-700 text-sm dark:border-clay-900/70 dark:bg-clay-900/40 dark:text-clay-200">
            <span className="min-w-0 truncate">正在编辑旧问题，会创建新的分支。</span>
            <button
              type="button"
              className="rounded-md px-2 py-1 font-medium hover:bg-clay-100 dark:hover:bg-clay-900"
              onClick={onCancelEdit}
            >
              取消
            </button>
          </div>
        ) : null}

        <div className="rounded-2xl border border-gray-200 bg-white p-2 shadow-lg shadow-gray-900/8 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/25">
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(event) => {
              setInput(event.target.value);
              resizeTextarea(event.currentTarget);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                onSubmit();
              }
            }}
            rows={1}
            placeholder="问问你的记录"
            className="max-h-48 min-h-12 w-full resize-none bg-transparent px-3 py-2.5 text-gray-900 text-sm leading-6 outline-none placeholder:text-gray-400 dark:text-gray-50 dark:placeholder:text-gray-500"
          />

          <div className="flex items-center justify-between gap-2 px-1">
            <details className="group relative">
              <summary className="flex h-9 cursor-pointer list-none items-center gap-2 rounded-lg px-2.5 text-gray-500 text-sm transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100 dark:focus-visible:ring-celadon-400/30">
                <ToolIcon />
                <span>来源</span>
                <span className="text-gray-400 text-xs dark:text-gray-500">
                  {sourceTypes.length}
                </span>
              </summary>
              <div className="absolute bottom-full left-0 z-20 mb-2 w-72 max-w-[calc(100vw-2rem)] rounded-xl border border-gray-200 bg-white p-2 shadow-xl shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/30">
                <p className="px-2 pb-2 text-gray-400 text-xs dark:text-gray-500">
                  选择本轮回答可使用的内容
                </p>
                <div className="grid gap-1">
                  {ASK_SOURCE_TYPES.map((type) => (
                    <SourceToggle
                      key={type}
                      type={type}
                      label={SOURCE_LABELS[type]}
                      checked={sourceTypes.includes(type)}
                      onChange={onToggleSource}
                    />
                  ))}
                </div>
              </div>
            </details>

            {busy ? (
              <button
                type="button"
                onClick={onStop}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-900 text-white transition hover:bg-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/30 dark:bg-gray-100 dark:text-gray-950 dark:hover:bg-white dark:focus-visible:ring-celadon-400/30"
              >
                <StopIcon />
                <span className="sr-only">停止</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={onSubmit}
                disabled={input.trim().length === 0 || sourceTypes.length === 0}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-900 text-white transition hover:bg-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/30 disabled:cursor-not-allowed disabled:opacity-35 dark:bg-gray-100 dark:text-gray-950 dark:hover:bg-white dark:focus-visible:ring-celadon-400/30"
              >
                <SendIcon />
                <span className="sr-only">发送</span>
              </button>
            )}
          </div>
        </div>
        <p className="mt-2 text-center text-gray-400 text-xs dark:text-gray-500">
          Sillage 只根据可用记录回答，重要内容请回到原文确认。
        </p>
      </div>
    </div>
  );
}

function ToolIcon() {
  return (
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
      <path d="M4 7h16" />
      <path d="M7 7a2 2 0 1 0 4 0 2 2 0 0 0-4 0z" />
      <path d="M4 17h16" />
      <path d="M13 17a2 2 0 1 0 4 0 2 2 0 0 0-4 0z" />
    </svg>
  );
}

function SendIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-4 w-4"
    >
      <path d="M12 19V5" />
      <path d="m6 11 6-6 6 6" />
    </svg>
  );
}

function StopIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="currentColor" className="h-3.5 w-3.5">
      <rect x="7" y="7" width="10" height="10" rx="1.5" />
    </svg>
  );
}

function MoreIcon() {
  return (
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
      <path d="M7 12h.01" />
      <path d="M12 12h.01" />
      <path d="M17 12h.01" />
    </svg>
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
      <header className="flex h-14 items-center bg-gray-50/85 px-3 backdrop-blur dark:bg-gray-950/75 sm:px-4">
        <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">新对话</h2>
      </header>
    );
  }

  return (
    <header className="min-h-14 bg-gray-50/85 px-3 py-2 backdrop-blur dark:bg-gray-950/75 sm:px-4">
      <div className="mx-auto flex max-w-3xl items-center justify-between gap-3">
        {renaming ? (
          <fetcher.Form method="post" className="flex min-w-0 flex-1 items-center gap-2">
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
            <button
              type="button"
              className={subtleButtonClass}
              onClick={() => {
                setTitle(conversationTitle);
                setRenaming(false);
              }}
            >
              取消
            </button>
          </fetcher.Form>
        ) : (
          <div className="min-w-0">
            <h2 className="truncate font-medium text-gray-900 text-sm dark:text-gray-50">
              {conversation.title || "新的问答"}
            </h2>
            <p className="text-gray-400 text-xs dark:text-gray-500">
              更新于 <LocalDateTime value={conversation.updatedAt} />
            </p>
          </div>
        )}
        {renaming ? null : (
          <ConversationMenu
            conversation={conversation}
            onRename={() => {
              setTitle(conversationTitle);
              setRenaming(true);
            }}
          />
        )}
      </div>
    </header>
  );
}

function ConversationMenu({
  conversation,
  onRename,
}: {
  conversation: AskConversationView;
  onRename: () => void;
}) {
  return (
    <details className="relative shrink-0">
      <summary className="flex h-9 w-9 cursor-pointer list-none items-center justify-center rounded-lg text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100 dark:focus-visible:ring-celadon-400/30">
        <MoreIcon />
        <span className="sr-only">会话操作</span>
      </summary>
      <div className="absolute right-0 z-20 mt-2 w-40 rounded-xl border border-gray-200 bg-white p-1.5 shadow-xl shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/30">
        <button type="button" className={menuItemClass} onClick={onRename}>
          重命名
        </button>
        <ActionButton intent="toggleAskPinned" conversationId={conversation.id} menu>
          {conversation.pinnedAt ? "取消置顶" : "置顶"}
        </ActionButton>
        <ActionButton intent="toggleAskArchived" conversationId={conversation.id} menu>
          {conversation.archivedAt ? "恢复" : "归档"}
        </ActionButton>
        <Link
          to={`/download-ask-conversation?conversation=${conversation.id}`}
          className={menuItemClass}
        >
          导出
        </Link>
        <ActionButton intent="deleteAskConversation" conversationId={conversation.id} danger menu>
          删除
        </ActionButton>
      </div>
    </details>
  );
}

function ActionButton({
  intent,
  conversationId,
  children,
  danger,
  menu,
}: {
  intent: string;
  conversationId: string;
  children: ReactNode;
  danger?: boolean;
  menu?: boolean;
}) {
  return (
    <Form method="post">
      <input type="hidden" name="intent" value={intent} />
      <input type="hidden" name="conversationId" value={conversationId} />
      <button
        type="submit"
        className={
          menu
            ? danger
              ? dangerMenuItemClass
              : menuItemClass
            : danger
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
      <div className="group/message flex w-full justify-end">
        <div className="max-w-[88%] sm:max-w-[76%]">
          <div className="rounded-3xl bg-gray-200/70 px-4 py-2.5 text-gray-900 text-sm leading-7 dark:bg-gray-800 dark:text-gray-100">
            <p className="whitespace-pre-wrap">{message.content}</p>
          </div>
          <div className="mt-1 flex flex-wrap items-center justify-end gap-1 text-xs opacity-0 transition group-hover/message:opacity-100 group-focus-within/message:opacity-100">
            {message.branch ? (
              <BranchControls conversationId={conversationId} branch={message.branch} />
            ) : null}
            <button
              type="button"
              onClick={() => onEdit(message)}
              disabled={busy}
              className="rounded-md px-2 py-1 text-gray-400 hover:bg-gray-100 hover:text-gray-900 disabled:opacity-50 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-100"
            >
              编辑
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="group/message w-full">
      <div className="min-w-0 text-gray-900 dark:text-gray-50">
        <div className="text-[15px] leading-7">
          {message.status === "running" && !message.content ? (
            <span className="inline-flex items-center gap-2 text-gray-400 dark:text-gray-500">
              <span className="h-2 w-2 animate-pulse rounded-full bg-gray-400 dark:bg-gray-500" />
              正在生成
            </span>
          ) : message.status === "error" ? (
            <p className="text-red-600 dark:text-red-400">{message.content}</p>
          ) : message.status === "running" ? (
            <p className="whitespace-pre-wrap">{message.content}</p>
          ) : (
            <LazyMarkdown content={message.content} variant="chat" />
          )}
        </div>

        {message.status === "interrupted" ? (
          <p className="mt-2 text-clay-600 text-xs dark:text-clay-300">已中断，保留部分回答。</p>
        ) : null}

        {message.sources.length > 0 ? (
          <div className="mt-3 flex flex-wrap items-center gap-1.5">
            <span className="mr-1 text-gray-400 text-xs dark:text-gray-500">来源</span>
            {message.sources.map((source) => (
              <Link
                key={`${message.id}-${source.id}`}
                to={source.href}
                className="max-w-full truncate rounded-full bg-celadon-50 px-2.5 py-1 text-celadon-800 text-xs transition hover:bg-celadon-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:bg-celadon-900/40 dark:text-celadon-200 dark:hover:bg-celadon-900/70 dark:focus-visible:ring-celadon-400/30"
                title={source.label}
              >
                {source.label}
              </Link>
            ))}
          </div>
        ) : null}

        <div className="mt-2 flex flex-wrap items-center gap-1 text-xs opacity-0 transition group-hover/message:opacity-100 group-focus-within/message:opacity-100">
          {message.branch ? (
            <BranchControls conversationId={conversationId} branch={message.branch} />
          ) : null}
          <button
            type="button"
            onClick={() => onRegenerate(message)}
            disabled={busy || message.status === "running"}
            className="rounded-md px-2 py-1 text-gray-400 hover:bg-gray-100 hover:text-gray-900 disabled:opacity-50 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-100"
          >
            重新生成
          </button>
          {message.status === "completed" || message.status === "interrupted" ? (
            <Form method="post">
              <input type="hidden" name="intent" value="saveAskEntry" />
              <input type="hidden" name="conversationId" value={conversationId} />
              <input type="hidden" name="messageId" value={message.id} />
              <button
                type="submit"
                className="rounded-md px-2 py-1 text-gray-400 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-100"
              >
                保存为记录
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
