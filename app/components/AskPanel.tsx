import { useEffect, useRef, useState } from "react";
import { Link, useFetcher } from "react-router";
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
  entryDate: string;
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

export function AskPanel() {
  const fetcher = useFetcher<AskActionData>();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const pendingRef = useRef(false);
  const turnSeq = useRef(0);
  const busy = fetcher.state !== "idle";

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

  function submit() {
    const question = input.trim();
    if (!question || busy) {
      return;
    }
    const history = turns
      .filter((turn) => turn.answer !== null && !turn.error)
      .slice(-4)
      .map((turn) => ({ question: turn.question, answer: turn.answer }));
    turnSeq.current += 1;
    setTurns((prev) => [...prev, { id: `t${turnSeq.current}`, question, answer: null }]);
    setInput("");
    pendingRef.current = true;
    const formData = new FormData();
    formData.set("intent", "ask");
    formData.set("question", question);
    formData.set("history", JSON.stringify(history));
    fetcher.submit(formData, { method: "post" });
  }

  return (
    <section className={`${panelClass} p-4`}>
      <div className="flex items-center justify-between">
        <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">问问记忆</h2>
        {turns.length > 0 ? (
          <button type="button" onClick={() => setTurns([])} className={subtleButtonClass}>
            清空对话
          </button>
        ) : null}
      </div>
      <p className={helperTextClass}>用自然语言提问，AI 会依据你的记录来回答。</p>

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
                        to={`/entries/${source.id}`}
                        className="text-gray-400 text-xs hover:text-gray-900 dark:text-gray-500 dark:hover:text-gray-100"
                      >
                        {source.entryDate}
                        {source.title ? ` · ${source.title}` : ""}
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
          placeholder="比如：上个月我和谁见面最多？最近在反复想什么？"
          className={`${textareaClass} min-w-0 flex-1`}
        />
        <button
          type="button"
          onClick={submit}
          disabled={busy || input.trim().length === 0}
          className={primaryButtonClass}
        >
          {busy ? "思考中…" : "发送"}
        </button>
      </div>
    </section>
  );
}
