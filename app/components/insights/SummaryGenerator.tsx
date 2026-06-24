import { useState } from "react";
import { useFetcher } from "react-router";
import { SuggestedInput } from "~/components/SuggestedInput";
import {
  helperTextClass,
  inputClass,
  labelClass,
  panelClass,
  primaryButtonClass,
} from "~/components/ui";
import type { EntryFormSuggestions } from "~/lib/product/entry-suggestions";
import type { SummaryActionData } from "~/lib/product/summary-actions";
import type { SummaryPeriodType, SummaryStyle } from "~/lib/product/summary-fields";
import { ChipGroup, PERIOD_OPTIONS, STYLE_OPTIONS, statusClass } from "./shared";

export interface PickerEntry {
  id: string;
  entryDate: string;
  title: string;
}

interface SummaryGeneratorProps {
  suggestions: EntryFormSuggestions;
  pickerEntries: PickerEntry[];
}

/** "回顾" generation form: pick a period or a topic thread, let AI weave a review. */
export function SummaryGenerator({ suggestions, pickerEntries }: SummaryGeneratorProps) {
  const fetcher = useFetcher<SummaryActionData>();
  const [scope, setScope] = useState<"period" | "topic">("period");
  const [periodType, setPeriodType] = useState<SummaryPeriodType>("week");
  const [style, setStyle] = useState<SummaryStyle>("brief");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const generating = fetcher.state !== "idle" && fetcher.formData?.get("intent") === "generate";
  const data = fetcher.data;

  function toggleEntry(id: string) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    );
  }

  return (
    <section className={`${panelClass} p-4`}>
      <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">生成总结</h2>
      <p className={helperTextClass}>挑一个时间范围或一条主题线索，让 AI 把记录织成一篇回顾。</p>

      <fetcher.Form method="post" className="mt-4 space-y-4">
        <input type="hidden" name="intent" value="generate" />
        <input type="hidden" name="scope" value={scope} />
        <input type="hidden" name="style" value={style} />

        <ChipGroup
          options={[
            { value: "period", label: "时间范围" },
            { value: "topic", label: "主题线索" },
          ]}
          value={scope}
          onChange={(value) => setScope(value as "period" | "topic")}
        />

        {scope === "period" ? (
          <div>
            <span className={labelClass}>时间范围</span>
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
        ) : (
          <div className="space-y-3">
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
        )}

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

        {data && data.intent === "generate" ? (
          <p className={statusClass(data.ok)}>{data.message}</p>
        ) : null}
      </fetcher.Form>
    </section>
  );
}
