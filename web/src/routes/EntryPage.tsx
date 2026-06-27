import { Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { EntryComposer } from "../components/EntryComposer";
import { LocalDateTime } from "../components/LocalDateTime";
import { Markdown } from "../components/Markdown";
import {
  dangerLinkClass,
  ghostLinkClass,
  mutedTextClass,
  pageTitleClass,
  panelClass,
  readingShellClass,
  subtleButtonClass,
} from "../components/ui";
import { useMemos } from "../state/MemosContext";

export function EntryPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const memos = useMemos();
  const memo = memos.getById(id);
  const summary = memos.summaries[id];
  const [editing, setEditing] = useState(false);
  const [missing, setMissing] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!id || memo || fetching || missing) {
      return;
    }
    setFetching(true);
    memos
      .fetchMemo(id)
      .catch(() => setMissing(true))
      .finally(() => setFetching(false));
  }, [id, memo, fetching, missing, memos]);

  if (missing) {
    return (
      <main className={readingShellClass}>
        <p className={mutedTextClass}>这条记录不存在或已被删除。</p>
        <Link
          to="/timeline"
          className={`${ghostLinkClass} mt-3 inline-block text-sm`}
        >
          ← 回到历史
        </Link>
      </main>
    );
  }

  if (!memo) {
    return (
      <main className={readingShellClass}>
        <p className="text-gray-400 dark:text-gray-500">正在打开记录…</p>
      </main>
    );
  }

  if (editing) {
    return (
      <main className={readingShellClass}>
        <div className="mb-6 flex items-center justify-between gap-3">
          <h1 className={pageTitleClass}>编辑记录</h1>
          <button
            type="button"
            onClick={() => setEditing(false)}
            className={`${ghostLinkClass} text-sm`}
          >
            取消
          </button>
        </div>
        <div className={`${panelClass} p-5 sm:p-6`}>
          <EntryComposer
            mode="edit"
            submitLabel="更新"
            initialContent={memo.content}
            initialEntryDate={memo.entryDate}
            onUpload={memos.upload}
            onSubmit={async (input) => {
              await memos.update(memo, input);
              setEditing(false);
            }}
            onCancel={() => setEditing(false)}
          />
        </div>
      </main>
    );
  }

  async function runAction(action: "pin" | "archive" | "delete") {
    if (!memo) {
      return;
    }
    if (action === "delete" && !window.confirm("确定删除这条记录？")) {
      return;
    }
    setBusy(action);
    setError("");
    try {
      if (action === "pin") {
        await memos.setPinned(memo, !memo.pinnedAt);
      } else if (action === "archive") {
        await memos.setArchived(memo, !memo.archivedAt);
      } else {
        await memos.remove(memo);
        navigate("/");
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "操作失败");
    } finally {
      setBusy("");
    }
  }

  async function summarize() {
    if (!memo) {
      return;
    }
    setBusy("summarize");
    setError("");
    try {
      await memos.summarize(memo);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "生成总结失败");
    } finally {
      setBusy("");
    }
  }

  return (
    <main className={readingShellClass}>
      <div className="mb-5 flex items-center justify-between gap-3 text-sm sm:mb-6">
        <Link to="/" className={ghostLinkClass}>
          ← 返回
        </Link>
        <div className="flex shrink-0 items-center gap-3">
          <button
            type="button"
            onClick={() => runAction("pin")}
            disabled={busy === "pin"}
            className={ghostLinkClass}
          >
            {memo.pinnedAt ? "取消置顶" : "置顶"}
          </button>
          <button
            type="button"
            onClick={() => runAction("archive")}
            disabled={busy === "archive"}
            className={ghostLinkClass}
          >
            {memo.archivedAt ? "取消归档" : "归档"}
          </button>
          <button
            type="button"
            onClick={() => setEditing(true)}
            className={ghostLinkClass}
          >
            编辑
          </button>
          <button
            type="button"
            onClick={() => runAction("delete")}
            disabled={busy === "delete"}
            className={dangerLinkClass}
          >
            删除
          </button>
        </div>
      </div>

      <article className="min-w-0">
        <header className="flex flex-wrap items-center gap-2 border-gray-200 border-b pb-5 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
          <time>{memo.entryDate}</time>
          {memo.pinnedAt ? <span className="text-gray-400">· 置顶</span> : null}
          {memo.archivedAt ? <span>· 已归档</span> : null}
        </header>

        <div className="py-6 sm:py-8">
          {memo.content.trim() ? (
            <Markdown content={memo.content} />
          ) : (
            <p className="text-gray-400 dark:text-gray-500">空白记录</p>
          )}
        </div>
      </article>

      <section className="border-gray-200 border-t pt-4 dark:border-gray-800">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-semibold text-gray-900 dark:text-gray-50">
            总结
          </h2>
          <button
            type="button"
            onClick={summarize}
            disabled={busy === "summarize"}
            className={subtleButtonClass}
          >
            <Sparkles className="h-4 w-4" />
            {busy === "summarize"
              ? "总结中…"
              : summary
                ? "重新总结"
                : "生成总结"}
          </button>
        </div>
        {summary ? (
          <div className="mt-3 rounded-xl bg-gray-100 p-4 dark:bg-gray-800">
            <Markdown
              content={summary.summary || "（暂无总结内容）"}
              variant="chat"
            />
          </div>
        ) : (
          <p className="mt-2 text-gray-400 text-sm dark:text-gray-500">
            让 AI 基于这条记录生成一段简短的总结。
          </p>
        )}
      </section>

      <section className="mt-5 border-gray-200 border-t pt-4 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
        <p>
          创建于 <LocalDateTime value={memo.createdAt} />
        </p>
        {memo.version > 1 ? (
          <p className="mt-1">
            最近修改 <LocalDateTime value={memo.updatedAt} />
            ，共修改 {memo.version - 1} 次
          </p>
        ) : null}
      </section>

      {error ? (
        <p className="mt-4 text-red-600 text-sm dark:text-red-400">{error}</p>
      ) : null}
    </main>
  );
}
