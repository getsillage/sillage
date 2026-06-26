import type { FormEvent } from "react";
import { useState } from "react";
import { GenerationStatus } from "~/components/ai/GenerationStatus";
import { useAiGeneration } from "~/components/ai/useAiGeneration";
import {
  helperTextClass,
  inputClass,
  labelClass,
  panelClass,
  primaryButtonClass,
} from "~/components/ui";
import { SUMMARY_PHASES } from "~/lib/ai/progress";
import type { SummaryPeriodType, SummaryStyle } from "~/lib/product/summary-fields";
import { ChipGroup, PERIOD_OPTIONS, STYLE_OPTIONS } from "./shared";

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
export function SummaryGenerator() {
  const generation = useAiGeneration("/api/summary");
  const [periodType, setPeriodType] = useState<SummaryPeriodType>("all");
  const [style, setStyle] = useState<SummaryStyle>("brief");

  const generating = generation.status === "running";

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    generation.run(formFields(event.currentTarget));
  }

  return (
    <section className={`${panelClass} p-4 sm:p-5 lg:p-6`}>
      <h2 className="font-serif text-gray-900 text-lg dark:text-gray-50">整理一段记录</h2>
      <p className={helperTextClass}>
        选择时间范围，也可以加一个关键词作为主题限制。留空关键词时，会按时间整理。
      </p>

      <form method="post" onSubmit={handleSubmit} className="mt-4 space-y-5">
        <input type="hidden" name="intent" value="generate" />
        <input type="hidden" name="scope" value="period" />
        <input type="hidden" name="usePeriod" value="1" />
        <input type="hidden" name="useTopic" value="auto" />
        <input type="hidden" name="style" value={style} />

        <div className="rounded-lg bg-gray-100/60 p-3 dark:bg-gray-950/60">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <span className={labelClass}>时间范围</span>
              <p className={helperTextClass}>先圈定时间，再按需要加一个主题关键词。</p>
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

        <label className={labelClass}>
          主题限制
          <input
            type="text"
            name="keyword"
            placeholder="可选，例如：工作、旅行、家"
            className={inputClass}
          />
          <span className={helperTextClass}>只按正文搜索，不再使用标签、人物或关系。</span>
        </label>

        <div>
          <span className={labelClass}>风格</span>
          <ChipGroup
            options={STYLE_OPTIONS}
            value={style}
            onChange={(value) => setStyle(value as SummaryStyle)}
          />
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <button
            type="submit"
            disabled={generating}
            className={`${primaryButtonClass} w-full sm:w-auto`}
          >
            {generating ? "生成中…" : "生成"}
          </button>
        </div>

        <GenerationStatus state={generation} phases={SUMMARY_PHASES} />
      </form>
    </section>
  );
}
