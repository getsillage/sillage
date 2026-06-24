import { useEffect, useRef, useState } from "react";
import { Link, useFetcher } from "react-router";
import {
  ASK_SOURCE_TYPES,
  type AskSourceType,
  DEFAULT_ASK_SOURCE_TYPES,
} from "~/lib/ai/ask-context";
import { Markdown } from "./Markdown";
import {
  helperTextClass,
  panelClass,
  primaryButtonClass,
  subtleButtonClass,
  textareaClass,
} from "./ui";

interface AskSource {
  id: string;
  title: string;
  label: string;
  href: string;
  kind: "entry" | "summary";
}

interface AskActionData {
  intent?: string;
  ok: boolean;
  message: string;
  answer?: string;
  sources?: AskSource[];
}

interface Turn {
  id: string;
  question: string;
  answer: string | null;
  sources?: AskSource[];
  error?: boolean;
}

interface StoredAskPanelState {
  turns: Turn[];
  input: string;
  sourceTypes: AskSourceType[];
}

const STORAGE_KEY = "sillage.memory.ask-panel.v1";
const ASK_FETCHER_KEY = "memory-ask-panel";
const MAX_STORED_TURNS = 30;
const INTERRUPTED_ANSWER = "上一次回答在页面切换时中断了，可以重新发送这个问题。";

function isAskSourceType(value: unknown): value is AskSourceType {
  return typeof value === "string" && ASK_SOURCE_TYPES.includes(value as AskSourceType);
}

function normalizeSourceTypes(value: unknown): AskSourceType[] {
  if (!Array.isArray(value)) {
    return [...DEFAULT_ASK_SOURCE_TYPES];
  }
  return [...new Set(value.filter(isAskSourceType))];
}

function normalizeSource(value: unknown): AskSource | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const source = value as AskSource;
  if (
    typeof source.id !== "string" ||
    typeof source.title !== "string" ||
    typeof source.label !== "string" ||
    typeof source.href !== "string" ||
    (source.kind !== "entry" && source.kind !== "summary")
  ) {
    return null;
  }
  return source;
}

function normalizeTurn(value: unknown, fallbackId: string): Turn | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const turn = value as Turn;
  if (typeof turn.question !== "string") {
    return null;
  }
  const answer =
    typeof turn.answer === "string"
      ? turn.answer
      : turn.answer === null
        ? INTERRUPTED_ANSWER
        : null;
  if (answer === null) {
    return null;
  }
  const sources = Array.isArray(turn.sources)
    ? turn.sources.map(normalizeSource).filter((source): source is AskSource => source !== null)
    : undefined;
  return {
    id: typeof turn.id === "string" && turn.id ? turn.id : fallbackId,
    question: turn.question,
    answer,
    sources,
    error: turn.answer === null || turn.error === true,
  };
}

function readStoredState(): StoredAskPanelState | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<StoredAskPanelState>;
    return {
      turns: Array.isArray(parsed.turns)
        ? parsed.turns
            .map((turn, index) => normalizeTurn(turn, `restored-${index + 1}`))
            .filter((turn): turn is Turn => turn !== null)
            .slice(-MAX_STORED_TURNS)
        : [],
      input: typeof parsed.input === "string" ? parsed.input : "",
      sourceTypes: normalizeSourceTypes(parsed.sourceTypes),
    };
  } catch {
    return null;
  }
}

function hasDefaultSourceTypes(sourceTypes: AskSourceType[]): boolean {
  return (
    sourceTypes.length === DEFAULT_ASK_SOURCE_TYPES.length &&
    DEFAULT_ASK_SOURCE_TYPES.every((type) => sourceTypes.includes(type))
  );
}

function writeStoredState(state: StoredAskPanelState) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    const trimmedState = {
      ...state,
      turns: state.turns.slice(-MAX_STORED_TURNS),
    };
    if (
      trimmedState.turns.length === 0 &&
      trimmedState.input.trim() === "" &&
      hasDefaultSourceTypes(trimmedState.sourceTypes)
    ) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return;
    }
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(trimmedState));
  } catch {
    // Storage can be unavailable in hardened browsers; the panel still works in memory.
  }
}

function nextTurnSequence(turns: Turn[]): number {
  return turns.reduce((max, turn) => {
    const match = /^t(\d+)$/.exec(turn.id);
    return match ? Math.max(max, Number(match[1])) : max;
  }, turns.length);
}

export function AskPanel() {
  const fetcher = useFetcher<AskActionData>({ key: ASK_FETCHER_KEY });
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [sourceTypes, setSourceTypes] = useState<AskSourceType[]>(() => [
    ...DEFAULT_ASK_SOURCE_TYPES,
  ]);
  const [storageReady, setStorageReady] = useState(false);
  const pendingRef = useRef(false);
  const turnSeq = useRef(0);
  const busy = fetcher.state !== "idle";

  useEffect(() => {
    const stored = readStoredState();
    if (stored) {
      setTurns(stored.turns);
      setInput(stored.input);
      setSourceTypes(stored.sourceTypes);
      turnSeq.current = nextTurnSequence(stored.turns);
    }
    setStorageReady(true);
  }, []);

  useEffect(() => {
    if (!storageReady) {
      return;
    }
    writeStoredState({ turns, input, sourceTypes });
  }, [input, sourceTypes, storageReady, turns]);

  useEffect(() => {
    if (fetcher.state !== "idle" || !pendingRef.current || fetcher.data?.intent !== "ask") {
      return;
    }
    pendingRef.current = false;
    const data = fetcher.data;
    setTurns((prev) => {
      const next = [...prev];
      for (let i = next.length - 1; i >= 0; i--) {
        if (next[i].answer === null) {
          next[i] = {
            ...next[i],
            answer: data.ok ? (data.answer ?? "") : data.message,
            sources: data.sources,
            error: !data.ok,
          };
          break;
        }
      }
      return next;
    });
  }, [fetcher.state, fetcher.data]);

  function clearTurns() {
    setTurns([]);
    writeStoredState({ turns: [], input, sourceTypes });
  }

  function submit() {
    const question = input.trim();
    if (!question || busy || sourceTypes.length === 0) {
      return;
    }
    const history = turns
      .filter((turn) => turn.answer !== null && !turn.error)
      .slice(-4)
      .map((turn) => ({ question: turn.question, answer: turn.answer }));
    turnSeq.current += 1;
    const optimisticTurn = { id: `t${turnSeq.current}`, question, answer: null };
    const nextTurns = [...turns, optimisticTurn];
    setTurns(nextTurns);
    writeStoredState({ turns: nextTurns, input: "", sourceTypes });
    setInput("");
    pendingRef.current = true;
    const formData = new FormData();
    formData.set("intent", "ask");
    formData.set("question", question);
    formData.set("history", JSON.stringify(history));
    for (const sourceType of sourceTypes) {
      formData.append("sources", sourceType);
    }
    fetcher.submit(formData, { method: "post" });
  }

  function toggleSource(type: AskSourceType) {
    setSourceTypes((current) => {
      if (current.includes(type)) {
        return current.filter((value) => value !== type);
      }
      return [...current, type];
    });
  }

  return (
    <section className={`${panelClass} p-4`}>
      <div className="flex items-center justify-between">
        <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">手记问答</h2>
        {turns.length > 0 ? (
          <button type="button" onClick={clearTurns} className={subtleButtonClass}>
            清空对话
          </button>
        ) : null}
      </div>
      <p className={helperTextClass}>用自然语言提问，AI 只依据你勾选的手记来源回答。</p>

      <div className="mt-3 flex flex-wrap gap-2">
        <SourceToggle
          type="fragment"
          label="片段"
          checked={sourceTypes.includes("fragment")}
          onChange={toggleSource}
        />
        <SourceToggle
          type="note"
          label="笔记"
          checked={sourceTypes.includes("note")}
          onChange={toggleSource}
        />
        <SourceToggle
          type="draft"
          label="草稿"
          checked={sourceTypes.includes("draft")}
          onChange={toggleSource}
        />
        <SourceToggle
          type="entry-ai"
          label="AI 洞察"
          checked={sourceTypes.includes("entry-ai")}
          onChange={toggleSource}
        />
        <SourceToggle
          type="summary"
          label="AI 总结"
          checked={sourceTypes.includes("summary")}
          onChange={toggleSource}
        />
      </div>

      {turns.length > 0 ? (
        <div className="mt-4 space-y-4">
          {turns.map((turn) => (
            <div key={turn.id} className="space-y-2">
              <div className="flex justify-end">
                <p className="max-w-[85%] whitespace-pre-wrap rounded-lg bg-gray-900 px-3 py-2 text-sm text-white dark:bg-gray-100 dark:text-gray-950">
                  {turn.question}
                </p>
              </div>
              <div className="max-w-[90%] rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950">
                {turn.answer === null ? (
                  <span className="text-gray-400 dark:text-gray-500">思考中…</span>
                ) : turn.error ? (
                  <span className="text-red-600 dark:text-red-400">{turn.answer}</span>
                ) : (
                  <Markdown content={turn.answer} />
                )}
                {turn.sources && turn.sources.length > 0 ? (
                  <div className="mt-2 flex flex-wrap gap-2 border-gray-100 border-t pt-2 dark:border-gray-800">
                    {turn.sources.map((source) => (
                      <Link
                        key={source.id}
                        to={source.href}
                        className="text-gray-400 text-xs hover:text-gray-900 dark:text-gray-500 dark:hover:text-gray-100"
                      >
                        {source.label}
                      </Link>
                    ))}
                  </div>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      ) : null}

      <div className="mt-4 flex items-end gap-2">
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
          placeholder="比如：上个月我和谁见面最多？最近反复出现了什么？"
          className={`${textareaClass} min-w-0 flex-1`}
        />
        <button
          type="button"
          onClick={submit}
          disabled={busy || input.trim().length === 0 || sourceTypes.length === 0}
          className={primaryButtonClass}
        >
          {busy ? "思考中…" : "发送"}
        </button>
      </div>
    </section>
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
    <label className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-gray-200 bg-white px-3 py-1.5 text-gray-700 text-sm transition hover:border-gray-300 hover:bg-gray-50 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-300 dark:hover:border-gray-700 dark:hover:bg-gray-900">
      <input
        type="checkbox"
        checked={checked}
        onChange={() => onChange(type)}
        className="h-4 w-4 rounded border-gray-300 dark:border-gray-700"
      />
      {label}
    </label>
  );
}
