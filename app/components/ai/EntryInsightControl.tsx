import type { ReactNode } from "react";
import { RelativeTime } from "~/components/RelativeTime";
import { ENTRY_INSIGHT_PHASES, formatDuration } from "~/lib/ai/progress";
import type { EntryWithTags } from "~/lib/db/entries";
import { GenerationStatus } from "./GenerationStatus";
import { useAiGeneration } from "./useAiGeneration";

interface EntryInsightControlProps {
  entry: EntryWithTags;
  /** Tighter type scale for the in-card variant. */
  compact?: boolean;
  /** Extra control rendered next to the generate button (e.g. a 查看详情 link). */
  trailing?: ReactNode;
}

/**
 * Single-entry "AI 洞察" generation, shared by the entry detail page and the card.
 * Owns the live/cancellable generation (timer + phase copy + classified failures)
 * and the provenance line (生成于 · 用时 · 模型 · 已生成 N 次). The summary text it
 * shows comes from the entry, which the hook refreshes via revalidation on success.
 */
export function EntryInsightControl({
  entry,
  compact = false,
  trailing,
}: EntryInsightControlProps) {
  const generation = useAiGeneration("/api/entry-insight");
  const running = generation.status === "running";
  const intent = entry.summary ? "regenerate-entry-insight" : "generate-entry-insight";
  const buttonLabel = running ? "生成中…" : entry.summary ? "重新生成洞察" : "生成洞察";

  const summaryClass = compact
    ? "text-gray-500 dark:text-gray-300"
    : "text-gray-600 dark:text-gray-300";

  return (
    <div>
      {entry.summary ? (
        <p className={summaryClass}>{entry.summary}</p>
      ) : (
        <p className="text-gray-400 italic dark:text-gray-500">
          微光未起 —— 还没有为这条记录生成洞察。
        </p>
      )}

      {entry.summary && entry.aiGeneratedAt ? (
        <p className="mt-1 text-gray-400 text-xs dark:text-gray-500">
          洞察生成于 <RelativeTime value={entry.aiGeneratedAt} />
          {entry.aiDurationMs != null ? ` · 用时 ${formatDuration(entry.aiDurationMs)}` : ""}
          {entry.aiModel ? ` · ${entry.aiModel}` : ""}
          {entry.aiGenerationCount > 1 ? ` · 已生成 ${entry.aiGenerationCount} 次` : ""}
        </p>
      ) : null}

      <div className="mt-2 flex items-center justify-between gap-3 text-xs">
        <button
          type="button"
          onClick={() => generation.run({ entryId: entry.id, intent })}
          disabled={running}
          className="text-gray-500 hover:text-gray-900 disabled:opacity-60 dark:text-gray-400 dark:hover:text-gray-100"
        >
          {buttonLabel}
        </button>
        {trailing}
      </div>

      <GenerationStatus state={generation} phases={ENTRY_INSIGHT_PHASES} />
    </div>
  );
}
