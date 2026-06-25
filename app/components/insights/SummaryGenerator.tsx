import type { FormEvent } from "react";
import { useState } from "react";
import { GenerationStatus } from "~/components/ai/GenerationStatus";
import { useAiGeneration } from "~/components/ai/useAiGeneration";
import { SuggestedInput } from "~/components/SuggestedInput";
import {
  helperTextClass,
  inputClass,
  labelClass,
  panelClass,
  primaryButtonClass,
} from "~/components/ui";
import { SUMMARY_PHASES } from "~/lib/ai/progress";
import type { EntryFormSuggestions } from "~/lib/product/entry-suggestions";
import type { SummaryPeriodType, SummaryStyle } from "~/lib/product/summary-fields";
import { ChipGroup, PERIOD_OPTIONS, STYLE_OPTIONS } from "./shared";

export interface PickerEntry {
  id: string;
  entryDate: string;
  title: string;
}

interface SummaryGeneratorProps {
  suggestions: EntryFormSuggestions;
  pickerEntries: PickerEntry[];
}

/** Collects the rendered form fields into a flat record for the JSON endpoint. */
function formFields(form: HTMLFormElement): Record<string, string> {
  const fields: Record<string, string> = {};
  for (const [key, value] of new FormData(form).entries()) {
    if (typeof value === "string") {
      fields[key] = value;
    }
  }
  return fields;
}

/** "回顾" generation form: pick a period or a topic thread, let AI weave a review. */
export function SummaryGenerator({ suggestions, pickerEntries }: SummaryGeneratorProps) {
  const generation = useAiGeneration("/api/summary");
  const [periodType, setPeriodType] = useState<SummaryPeriodType>("all");
  const [style, setStyle] = useState<SummaryStyle>("brief");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const generating = generation.status === "running";

  function toggleEntry(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    );
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    generation.run(formFields(event.currentTarget));
  }

  return (
    <section className={`${panelClass} p-4 sm:p-5 lg:p-6`}>
      <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">生成总结</h2>
      <p className={helperTextClass}>
        时间范围和主题线索可以一起使用；主题留空时，就按所选时间生成回顾。
      </p>

      <form method="post" onSubmit={handleSubmit} className="mt-4 space-y-5">
        <input type="hidden" name="intent" value="generate" />
        <input type="hidden" name="scope" value="period" />
        <input type="hidden" name="usePeriod" value="1" />
        <input type="hidden" name="useTopic" value="auto" />
        <input type="hidden" name="style" value={style} />

        <div className="rounded-lg border border-gray-200 bg-gray-50/70 p-3 dark:border-gray-800 dark:bg-gray-950/60">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <span className={labelClass}>时间范围</span>
              <p className={helperTextClass}>先圈定时间，再按需要叠加主题线索。</p>
            </div>
            <span className="rounded-full bg-white px-2.5 py-1 text-gray-500 text-xs ring-1 ring-gray-200 dark:bg-gray-900 dark:text-gray-400 dark:ring-gray-800">
              {periodType === "all" ? "不限制日期" : "按日期筛选"}
            </span>
          </div>
          <input type="hidden" name="periodType" value={periodType} />
          <ChipGroup
            options={PERIOD_OPTIONS}
            value={periodType}
            onChange={(value) => setPeriodType(value as SummaryPeriodType)}
          />
          {periodType === "custom" ? (
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className={labelClass}>
                起始
                <input type="date" name="startDate" required className={inputClass} />
              </label>
              <label className={labelClass}>
                结束
                <input type="date" name="endDate" required className={inputClass} />
              </label>
            </div>
          ) : null}
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-800 dark:bg-gray-950">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <span className={labelClass}>主题线索</span>
              <p className={helperTextClass}>
                填任意一项就会自动生成主题回顾；全部留空则只看时间。
              </p>
            </div>
            <span className="rounded-full bg-gray-100 px-2.5 py-1 text-gray-500 text-xs dark:bg-gray-800 dark:text-gray-400">
              可选
            </span>
          </div>

          <div className="mt-3 space-y-3">
            <div>
              <span className={labelClass}>标签 / 人物 / 关系</span>
              <div className="mt-1 grid gap-3 sm:grid-cols-3">
                <SuggestedInput
                  id="summary-tags"
                  name="tags"
                  optionLabel="添加已有标签"
                  options={suggestions.tags}
                  placeholder="标签"
                  selectionMode="append"
                />
                <SuggestedInput
                  id="summary-people"
                  name="people"
                  optionLabel="添加已有人物"
                  options={suggestions.people}
                  placeholder="人物"
                  selectionMode="append"
                />
                <SuggestedInput
                  id="summary-relationships"
                  name="relationships"
                  optionLabel="添加已有关系"
                  options={suggestions.relationships}
                  placeholder="关系"
                  selectionMode="append"
                />
              </div>
            </div>
            <label className={labelClass}>
              关键词
              <input
                type="text"
                name="keyword"
                placeholder="在正文 / 心情 / 地点中搜索"
                className={inputClass}
              />
            </label>
            <input type="hidden" name="entryIds" value={selectedIds.join(",")} />
            {pickerEntries.length > 0 ? (
              <details className="rounded-lg border border-gray-200 dark:border-gray-800">
                <summary className="cursor-pointer px-3 py-2 text-gray-700 text-sm dark:text-gray-300">
                  从最近记录手动勾选{selectedIds.length > 0 ? `（已选 ${selectedIds.length}）` : ""}
                </summary>
                <ul className="max-h-56 overflow-auto border-gray-100 border-t dark:border-gray-800">
                  {pickerEntries.map((entry) => (
                    <li key={entry.id}>
                      <label className="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-900">
                        <input
                          type="checkbox"
                          checked={selectedIds.includes(entry.id)}
                          onChange={() => toggleEntry(entry.id)}
                          className="h-4 w-4 rounded border-gray-300 dark:border-gray-700"
                        />
                        <span className="text-gray-400 text-xs dark:text-gray-500">
                          {entry.entryDate}
                        </span>
                        <span className="truncate text-gray-700 dark:text-gray-300">
                          {entry.title || "(无标题)"}
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
              </details>
            ) : null}
          </div>
        </div>

        <div>
          <span className={labelClass}>风格</span>
          <ChipGroup
            options={STYLE_OPTIONS}
            value={style}
            onChange={(value) => setStyle(value as SummaryStyle)}
          />
        </div>

        <div className="flex items-center gap-3">
          <button type="submit" disabled={generating} className={primaryButtonClass}>
            {generating ? "生成中…" : "生成"}
          </button>
        </div>

        <GenerationStatus state={generation} phases={SUMMARY_PHASES} />
      </form>
    </section>
  );
}
