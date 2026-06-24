import { useEffect, useId, useState } from "react";
import { Form, useNavigation } from "react-router";
import { ENTRY_INSIGHT_FORM_FIELD } from "~/lib/ai/entry-insights";
import { todayISO } from "~/lib/date";
import { ENTRY_KINDS, type EntryKind, NOTE_TYPES, type NoteType } from "~/lib/product/entry-fields";
import type { EntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { MarkdownEditor } from "./MarkdownEditor";
import { SuggestedInput } from "./SuggestedInput";
import { helperTextClass, inputClass, labelClass, primaryButtonClass } from "./ui";

export interface EntryFormDefaults {
  entryDate: string;
  title: string;
  body: string;
  mood: number | null;
  moodText: string | null;
  weather: string | null;
  location: string | null;
  kind: EntryKind;
  noteType: NoteType | null;
  people: string[];
  relationships: string[];
  tags: string[];
}

interface EntryFormProps {
  defaults?: EntryFormDefaults;
  suggestions?: EntryFormSuggestions;
  error?: string | null;
  submitLabel?: string;
  intent?: string;
  showEntryInsightOption?: boolean;
  defaultEntryInsightForKind?: (kind: EntryKind) => boolean;
}

const MOODS: ReadonlyArray<{ value: number; emoji: string; label: string }> = [
  { value: 1, emoji: "", label: "低落" },
  { value: 2, emoji: "", label: "失落" },
  { value: 3, emoji: "", label: "平静" },
  { value: 4, emoji: "", label: "轻松" },
  { value: 5, emoji: "", label: "明亮" },
];

const KIND_LABELS: Record<EntryKind, { label: string; help: string }> = {
  fragment: { label: "片段", help: "当下的一件事或一种感受" },
  note: { label: "笔记", help: "认真整理一段时间或主题" },
  draft: { label: "草稿", help: "先放下，还不决定形态" },
};

const NOTE_TYPE_LABELS: Record<NoteType, string> = {
  daily: "今日笔记",
  weekly: "周笔记",
  monthly: "月笔记",
  topic: "主题笔记",
  freeform: "自由笔记",
};

const WEATHER_OPTIONS = ["晴", "多云", "阴", "小雨", "雨", "大雨", "雪", "雾", "风"] as const;

function includeCurrentOption(options: readonly string[], current: string | null | undefined) {
  const value = current?.trim();
  return value && !options.includes(value) ? [value, ...options] : options;
}

export function EntryForm({
  defaults,
  suggestions,
  error,
  submitLabel = "保存",
  intent,
  showEntryInsightOption = false,
  defaultEntryInsightForKind,
}: EntryFormProps) {
  const navigation = useNavigation();
  const busy = navigation.state !== "idle";
  const idBase = useId().replaceAll(":", "");
  const defaultKind = defaults?.kind ?? "fragment";
  const [selectedKind, setSelectedKind] = useState<EntryKind>(defaultKind);
  const [generateEntryInsight, setGenerateEntryInsight] = useState(
    defaultEntryInsightForKind?.(defaultKind) ?? false,
  );
  const defaultNoteType = defaults?.noteType ?? "daily";
  const weatherOptions = includeCurrentOption(WEATHER_OPTIONS, defaults?.weather);
  const locationSuggestions = suggestions?.locations ?? [];
  const peopleSuggestions = suggestions?.people ?? [];
  const relationshipSuggestions = suggestions?.relationships ?? [];
  const tagSuggestions = suggestions?.tags ?? [];
  const locationId = `${idBase}-location`;
  const peopleId = `${idBase}-people`;
  const relationshipId = `${idBase}-relationships`;
  const tagId = `${idBase}-tags`;

  useEffect(() => {
    if (showEntryInsightOption) {
      setGenerateEntryInsight(defaultEntryInsightForKind?.(selectedKind) ?? false);
    }
  }, [defaultEntryInsightForKind, selectedKind, showEntryInsightOption]);

  return (
    <Form method="post" className="space-y-6">
      {intent ? <input type="hidden" name="intent" value={intent} /> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <label className={labelClass}>
          日期
          <input
            type="date"
            name="entryDate"
            required
            defaultValue={defaults?.entryDate ?? todayISO()}
            className={inputClass}
          />
        </label>
        <label className={labelClass}>
          天气
          <select name="weather" defaultValue={defaults?.weather ?? ""} className={inputClass}>
            <option value="">未选择</option>
            {weatherOptions.map((weather) => (
              <option key={weather} value={weather}>
                {weather}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div>
        <label htmlFor={locationId} className={labelClass}>
          地点
        </label>
        <SuggestedInput
          id={locationId}
          name="location"
          optionLabel="选择已有地点"
          options={locationSuggestions}
          placeholder="某个城市、房间、路口…"
          defaultValue={defaults?.location ?? ""}
        />
      </div>

      <label className={labelClass}>
        标题
        <input
          type="text"
          name="title"
          placeholder="可以留空，让内容自己先存在"
          defaultValue={defaults?.title ?? ""}
          className={inputClass}
        />
      </label>

      <fieldset>
        <legend className={labelClass}>保存为</legend>
        <div className="mt-2 grid gap-2 sm:grid-cols-3">
          {ENTRY_KINDS.map((kind) => (
            <label
              key={kind}
              className="flex min-h-20 cursor-pointer flex-col rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm transition hover:border-gray-300 hover:bg-gray-50 has-[:checked]:border-gray-950 has-[:checked]:bg-gray-950 has-[:checked]:text-white dark:border-gray-800 dark:bg-gray-950 dark:text-gray-200 dark:hover:border-gray-700 dark:hover:bg-gray-900 dark:has-[:checked]:border-gray-100 dark:has-[:checked]:bg-gray-100 dark:has-[:checked]:text-gray-950"
            >
              <input
                type="radio"
                name="kind"
                value={kind}
                checked={selectedKind === kind}
                onChange={() => setSelectedKind(kind)}
                className="sr-only"
              />
              <span className="font-medium">{KIND_LABELS[kind].label}</span>
              <span className="mt-1 text-xs opacity-70">{KIND_LABELS[kind].help}</span>
            </label>
          ))}
        </div>
      </fieldset>

      {selectedKind === "note" ? (
        <label className={labelClass}>
          笔记类型
          <select name="noteType" defaultValue={defaultNoteType} className={inputClass}>
            {NOTE_TYPES.map((type) => (
              <option key={type} value={type}>
                {NOTE_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
          <span className={helperTextClass}>用于区分今日笔记、周笔记、主题笔记等整理方式。</span>
        </label>
      ) : null}

      <fieldset>
        <legend className={labelClass}>预设心情</legend>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {MOODS.map((mood) => (
            <label
              key={mood.value}
              className="flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-gray-200 bg-white px-3 py-2 text-gray-500 text-xs transition hover:border-gray-300 hover:bg-gray-50 has-[:checked]:border-gray-950 has-[:checked]:bg-gray-950 has-[:checked]:text-white dark:border-gray-800 dark:bg-gray-950 dark:text-gray-400 dark:hover:border-gray-700 dark:hover:bg-gray-900 dark:has-[:checked]:border-gray-100 dark:has-[:checked]:bg-gray-100 dark:has-[:checked]:text-gray-950"
            >
              <input
                type="radio"
                name="mood"
                value={mood.value}
                defaultChecked={defaults?.mood === mood.value}
                className="sr-only"
              />
              <span className="font-semibold text-sm">{mood.value}</span>
              {mood.label}
            </label>
          ))}
        </div>
      </fieldset>

      <label className={labelClass}>
        细腻感受
        <input
          type="text"
          name="moodText"
          placeholder="比如：有点松了一口气，但还是委屈"
          defaultValue={defaults?.moodText ?? ""}
          className={inputClass}
        />
      </label>

      <div>
        <span className={`${labelClass} mb-1 block`}>内容</span>
        <MarkdownEditor name="body" defaultValue={defaults?.body ?? ""} />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={peopleId} className={labelClass}>
            人物
          </label>
          <SuggestedInput
            id={peopleId}
            name="people"
            optionLabel="添加已有人物"
            options={peopleSuggestions}
            placeholder="输入人物，用逗号分隔"
            defaultValue={defaults?.people.join(", ") ?? ""}
            selectionMode="append"
          />
        </div>
        <div>
          <label htmlFor={relationshipId} className={labelClass}>
            关系
          </label>
          <SuggestedInput
            id={relationshipId}
            name="relationships"
            optionLabel="添加已有关系"
            options={relationshipSuggestions}
            placeholder="输入关系，用逗号分隔"
            defaultValue={defaults?.relationships.join(", ") ?? ""}
            selectionMode="append"
          />
        </div>
      </div>

      <div>
        <label htmlFor={tagId} className={labelClass}>
          标签
        </label>
        <SuggestedInput
          id={tagId}
          name="tags"
          optionLabel="添加已有标签"
          options={tagSuggestions}
          placeholder="输入标签，用逗号分隔"
          defaultValue={defaults?.tags.join(", ") ?? ""}
          selectionMode="append"
        />
      </div>

      {error ? <p className="text-red-600 text-sm dark:text-red-400">{error}</p> : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        {showEntryInsightOption ? (
          <label className="flex items-center gap-2 text-gray-700 text-sm dark:text-gray-300">
            <input
              type="checkbox"
              name={ENTRY_INSIGHT_FORM_FIELD}
              checked={generateEntryInsight}
              onChange={(event) => setGenerateEntryInsight(event.target.checked)}
              className="h-4 w-4 rounded border-gray-300 dark:border-gray-700"
            />
            保存后生成 AI 洞察
          </label>
        ) : (
          <span />
        )}

        <button type="submit" disabled={busy} className={primaryButtonClass}>
          {busy ? "保存中…" : submitLabel}
        </button>
      </div>
    </Form>
  );
}
