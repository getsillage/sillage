import {
  BookmarkPlus,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  MessageCircleQuestion,
  RefreshCw,
  Search,
  SendHorizontal,
  Square,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import {
  Link,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { Markdown } from "../components/Markdown";
import {
  ghostLinkClass,
  primaryButtonClass,
  secondaryButtonClass,
  selectClass,
  textareaClass,
} from "../components/ui";
import type { AskContextScope, AskSourceKind } from "../lib/api";
import type { ActiveEntry } from "../lib/askTree";
import { todayISO } from "../lib/date";
import { useAsk } from "../state/AskContext";
import { useMemos } from "../state/MemosContext";

const SCOPE_LABELS: Record<AskContextScope, string> = {
  recent_7_days: "最近 7 天",
  recent_30_days: "最近 30 天",
  all: "全部记录",
};

const SOURCE_KIND_OPTIONS: { value: AskSourceKind; label: string }[] = [
  { value: "records", label: "原始记录" },
  { value: "summaries", label: "记录总结" },
];

const QUESTION_SUGGESTIONS = [
  "最近我反复提到什么？",
  "这一周有哪些重要内容？",
  "我最近的状态有什么变化？",
];

export function AskPage() {
  const [searchParams] = useSearchParams();
  const conversationParam = searchParams.get("conversation");
  const {
    activeConversation,
    activeId,
    entries,
    loadingMessages,
    liveUser,
    liveAnswer,
    regeneratingId,
    scope,
    sourceKind,
    busy,
    streaming,
    error,
    setScope,
    setSourceKind,
    selectConversation,
    send,
    regenerate,
    selectVariant,
    stop,
  } = useAsk();
  const [question, setQuestion] = useState("");
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const followOutputRef = useRef(true);
  const liveUserMessage = shouldShowLiveUser(entries, liveUser)
    ? liveUser
    : null;
  const lastAssistantId = [...entries]
    .reverse()
    .find((entry) => entry.message.role === "assistant")?.message.id;

  // biome-ignore lint/correctness/useExhaustiveDependencies: react only to the URL param
  useEffect(() => {
    if (conversationParam && conversationParam !== activeId) {
      selectConversation(conversationParam);
    }
  }, [conversationParam]);

  useEffect(() => {
    function updateFollowState() {
      const remaining =
        document.documentElement.scrollHeight -
        window.scrollY -
        window.innerHeight;
      followOutputRef.current = remaining < 180;
    }
    updateFollowState();
    window.addEventListener("scroll", updateFollowState, { passive: true });
    return () => window.removeEventListener("scroll", updateFollowState);
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: follow newly rendered chat output while the user stays near the bottom
  useEffect(() => {
    if (followOutputRef.current) {
      endRef.current?.scrollIntoView?.({ block: "end" });
    }
  }, [entries.length, liveUser, liveAnswer]);

  async function submit() {
    if (busy) {
      return;
    }
    const text = question.trim();
    if (!text) {
      return;
    }
    setQuestion("");
    followOutputRef.current = true;
    const accepted = await send(text);
    if (!accepted) {
      setQuestion((current) => current || text);
    }
  }

  return (
    <main className="mx-auto flex min-h-[calc(100vh-3.5rem)] w-full max-w-4xl flex-col px-4 pt-5 pb-0 sm:px-6 lg:min-h-screen lg:pt-6">
      <header className="flex flex-wrap items-center justify-between gap-3 pb-4">
        <div>
          <h1 className="font-semibold text-xl text-gray-900 sm:text-2xl dark:text-gray-50">
            {activeConversation?.title || "根据记录提问"}
          </h1>
          <p className="mt-1 text-gray-500 text-sm dark:text-gray-400">
            基于你的记录回答，范围：{SCOPE_LABELS[scope]}
          </p>
        </div>
        <div className="-mx-1 flex max-w-full items-center gap-2 overflow-x-auto px-1">
          <label className="flex shrink-0 items-center gap-2 text-gray-500 text-sm dark:text-gray-400">
            <span className="whitespace-nowrap">来源</span>
            <select
              value={sourceKind}
              onChange={(event) =>
                setSourceKind(event.target.value as AskSourceKind)
              }
              className={`${selectClass} mt-0 w-auto min-w-30 bg-white/80 dark:bg-gray-900/80`}
            >
              {SOURCE_KIND_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex shrink-0 items-center gap-2 text-gray-500 text-sm dark:text-gray-400">
            <span className="whitespace-nowrap">范围</span>
            <select
              value={scope}
              onChange={(event) =>
                setScope(event.target.value as AskContextScope)
              }
              className={`${selectClass} mt-0 w-auto min-w-32 bg-white/80 dark:bg-gray-900/80`}
            >
              <option value="recent_7_days">最近 7 天</option>
              <option value="recent_30_days">最近 30 天</option>
              <option value="all">全部记录</option>
            </select>
          </label>
        </div>
      </header>

      <div className="flex-1 space-y-7 pt-5 pb-36">
        {loadingMessages ? (
          <p
            className="inline-flex items-center gap-2 text-gray-500 text-sm dark:text-gray-400"
            role="status"
          >
            <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
            正在读取对话
          </p>
        ) : entries.length === 0 && !liveUser ? (
          <div className="mx-auto flex min-h-[46vh] w-full max-w-xl items-center justify-center">
            <div className="w-full space-y-5 text-center">
              <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                <MessageCircleQuestion className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                <p className="font-medium text-gray-900 text-lg dark:text-gray-50">
                  可以根据记录提问
                </p>
                <p className="mt-1 text-gray-500 text-sm dark:text-gray-400">
                  选择一个问题，或写下你想回看的内容。
                </p>
              </div>
              <div className="grid gap-2 text-left sm:grid-cols-3">
                {QUESTION_SUGGESTIONS.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    onClick={() => {
                      setQuestion(suggestion);
                      inputRef.current?.focus();
                    }}
                    className="min-h-16 rounded-lg border border-gray-200 bg-white/70 px-3 py-2 text-left text-gray-700 text-sm leading-5 transition-colors hover:border-gray-300 hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:border-gray-800 dark:bg-gray-900/60 dark:text-gray-200 dark:hover:border-gray-700 dark:hover:bg-gray-900 dark:focus-visible:ring-gray-500/40"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
              <Link
                to="/timeline"
                className={`${ghostLinkClass} inline-flex h-10 items-center gap-2 px-2 text-sm`}
              >
                <Search className="h-4 w-4" aria-hidden="true" />
                先搜索记录
              </Link>
            </div>
          </div>
        ) : (
          entries.map((entry) => (
            <MessageBubble
              key={entry.message.id}
              entry={entry}
              streamingText={
                regeneratingId === entry.message.id ? liveAnswer : undefined
              }
              canRegenerate={
                entry.message.role === "assistant" &&
                entry.message.id === lastAssistantId &&
                !busy
              }
              onRegenerate={() => regenerate(entry.message.id)}
              onSelectVariant={selectVariant}
            />
          ))
        )}
        {liveUserMessage ? (
          <>
            <div className="ml-auto max-w-[82%] rounded-2xl bg-gray-200/70 px-4 py-2.5 text-gray-900 dark:bg-gray-800 dark:text-gray-50">
              <p className="whitespace-pre-wrap text-[15px] leading-7">
                {liveUserMessage.content}
              </p>
            </div>
            <div className="max-w-[92%] px-1">
              {liveAnswer ? (
                <Markdown content={liveAnswer} variant="chat" />
              ) : (
                <p className="inline-flex items-center gap-2 text-gray-500 text-sm dark:text-gray-400">
                  <LoaderCircle
                    className="h-4 w-4 animate-spin"
                    aria-hidden="true"
                  />
                  正在整理回答
                </p>
              )}
            </div>
          </>
        ) : null}
        {busy && !streaming ? (
          <p
            className="inline-flex items-center gap-2 text-gray-500 text-sm dark:text-gray-400"
            role="status"
          >
            <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
            正在读取相关记录
          </p>
        ) : null}
        <div ref={endRef} aria-hidden="true" />
      </div>

      <div className="sticky bottom-0 z-10 -mx-4 bg-gradient-to-t from-gray-50 via-gray-50 to-gray-50/0 px-4 pt-6 pb-4 sm:-mx-6 sm:px-6 dark:from-gray-950 dark:via-gray-950 dark:to-gray-950/0">
        <div className="space-y-2 rounded-2xl border border-gray-200/80 bg-white/95 p-2 shadow-xl shadow-gray-900/[0.07] backdrop-blur-xl dark:border-gray-800 dark:bg-gray-900/95 dark:shadow-black/25">
          <textarea
            ref={inputRef}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (
                event.key === "Enter" &&
                !event.shiftKey &&
                !event.nativeEvent.isComposing
              ) {
                event.preventDefault();
                if (!busy) {
                  void submit();
                }
              }
            }}
            rows={2}
            placeholder="根据记录提问…"
            className={`${textareaClass} min-h-20 resize-none border-0 bg-transparent px-3 py-3 text-[15px] leading-7 focus:ring-0 dark:bg-transparent`}
          />
          {error ? (
            <p
              role="alert"
              className="px-3 text-red-600 text-sm dark:text-red-400"
            >
              {error}
            </p>
          ) : null}
          <div className="flex items-center justify-between gap-2 px-1 pb-1">
            <p className="px-2 text-gray-400 text-xs dark:text-gray-500">
              AI 只基于选定范围内的记录回答
            </p>
            <div className="flex items-center gap-2">
              {streaming ? (
                <button
                  type="button"
                  onClick={stop}
                  className={`${secondaryButtonClass} h-11 w-11 rounded-full px-0`}
                  aria-label="停止生成"
                  title="停止生成"
                >
                  <Square className="h-5 w-5" />
                </button>
              ) : null}
              <button
                type="button"
                onClick={submit}
                disabled={busy || !question.trim()}
                className={`${primaryButtonClass} h-11 w-11 rounded-full px-0`}
                aria-label={busy ? "生成中" : "发送"}
                title={busy ? "生成中" : "发送"}
              >
                {busy ? (
                  <LoaderCircle className="h-5 w-5 animate-spin" />
                ) : (
                  <SendHorizontal className="h-5 w-5" />
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

export function shouldShowLiveUser(
  entries: ActiveEntry[],
  liveUser: { id: string } | null,
): boolean {
  if (!liveUser) {
    return false;
  }
  return !entries.some((entry) => entry.message.id === liveUser.id);
}

interface MessageBubbleProps {
  entry: ActiveEntry;
  streamingText?: string;
  canRegenerate: boolean;
  onRegenerate: () => void;
  onSelectVariant: (messageId: string) => void;
}

function MessageBubble({
  entry,
  streamingText,
  canRegenerate,
  onRegenerate,
  onSelectVariant,
}: MessageBubbleProps) {
  const { create } = useMemos();
  const navigate = useNavigate();
  const location = useLocation();
  const returnTo = `${location.pathname}${location.search}${location.hash}`;
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState("");
  const { message, variants, index } = entry;

  if (message.role === "user") {
    return (
      <div className="ml-auto max-w-[82%] rounded-2xl bg-gray-200/70 px-4 py-2.5 text-gray-900 dark:bg-gray-800 dark:text-gray-50">
        <p className="whitespace-pre-wrap text-[15px] leading-7">
          {message.content}
        </p>
      </div>
    );
  }

  async function saveAsRecord() {
    if (!message.content.trim()) {
      return;
    }
    setSaving(true);
    setSaveError("");
    try {
      const memo = await create({
        content: message.content,
        entryDate: todayISO(),
      });
      setSaved(true);
      navigate(`/entries/${memo.id}`, { state: { returnTo } });
    } catch (cause) {
      setSaving(false);
      setSaveError(cause instanceof Error ? cause.message : "保存失败");
    }
  }

  const content = streamingText ?? message.content;
  const hasVariants = variants.length > 1;

  return (
    <div className="max-w-[92%] px-1">
      <Markdown content={content} variant="chat" />
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {message.sourceRefs.map((source) => (
          <Link
            key={`${message.id}-${source.memoId}-${source.rank}`}
            to={`/entries/${source.memoId}`}
            state={{ returnTo }}
            className="inline-flex min-h-10 max-w-full items-center gap-2 rounded-lg bg-gray-100 px-2.5 text-gray-700 text-xs transition-colors hover:bg-gray-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700 dark:focus-visible:ring-gray-500/40"
          >
            <BookOpen className="h-3.5 w-3.5 flex-none" aria-hidden="true" />
            <span className="flex-none">{source.entryDate}</span>
            <span className="truncate text-gray-500 dark:text-gray-400">
              {source.excerpt}
            </span>
          </Link>
        ))}
        {hasVariants ? (
          <span className="inline-flex items-center gap-1 text-gray-500 text-xs dark:text-gray-400">
            <button
              type="button"
              aria-label="上一个回答"
              disabled={index <= 0}
              onClick={() => onSelectVariant(variants[index - 1].id)}
              className={`${ghostLinkClass} px-1`}
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span>
              {index + 1}/{variants.length}
            </span>
            <button
              type="button"
              aria-label="下一个回答"
              disabled={index >= variants.length - 1}
              onClick={() => onSelectVariant(variants[index + 1].id)}
              className={`${ghostLinkClass} px-1`}
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </span>
        ) : null}
        {canRegenerate ? (
          <button
            type="button"
            onClick={onRegenerate}
            className={`${ghostLinkClass} inline-flex h-10 items-center gap-1.5 px-2 text-xs`}
          >
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            重新生成
          </button>
        ) : null}
        {message.content.trim() ? (
          <button
            type="button"
            onClick={saveAsRecord}
            disabled={saving || saved}
            className={`${ghostLinkClass} inline-flex h-10 items-center gap-1.5 px-2 text-xs`}
          >
            <BookmarkPlus className="h-3.5 w-3.5" aria-hidden="true" />
            {saved ? "已存为记录" : saving ? "保存中…" : "存为记录"}
          </button>
        ) : null}
      </div>
      {saveError ? (
        <p role="alert" className="mt-2 text-red-600 text-xs dark:text-red-400">
          {saveError}
        </p>
      ) : null}
    </div>
  );
}
