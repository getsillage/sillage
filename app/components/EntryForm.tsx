import { Form, useNavigation } from "react-router";
import { todayISO } from "~/lib/date";
import {
  ENTRY_KINDS,
  type EntryKind,
  REFLECTION_TYPES,
  type ReflectionType,
} from "~/lib/product/entry-fields";
import { MarkdownEditor } from "./MarkdownEditor";
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
  reflectionType: ReflectionType | null;
  people: string[];
  relationships: string[];
  tags: string[];
}

interface EntryFormProps {
  defaults?: EntryFormDefaults;
  error?: string | null;
  submitLabel?: string;
  intent?: string;
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
  reflection: { label: "回顾", help: "认真整理一段时间或主题" },
  draft: { label: "草稿", help: "先放下，还不决定形态" },
};

const REFLECTION_LABELS: Record<ReflectionType, string> = {
  daily: "今日回顾",
  weekly: "周回顾",
  monthly: "月回顾",
  topic: "主题回顾",
  freeform: "自由回顾",
};

export function EntryForm({ defaults, error, submitLabel = "保存", intent }: EntryFormProps) {
  const navigation = useNavigation();
  const busy = navigation.state !== "idle";
  const defaultKind = defaults?.kind ?? "fragment";
  const defaultReflectionType = defaults?.reflectionType ?? "daily";

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
          <input
            type="text"
            name="weather"
            placeholder="晴 / 阴 / 雨…"
            defaultValue={defaults?.weather ?? ""}
            className={inputClass}
          />
        </label>
      </div>

      <label className={labelClass}>
        地点
        <input
          type="text"
          name="location"
          placeholder="某个城市、房间、路口…"
          defaultValue={defaults?.location ?? ""}
          className={inputClass}
        />
      </label>

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
              className="flex min-h-20 cursor-pointer flex-col rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm transition hover:border-gray-300 hover:bg-gray-50 has-[:checked]:border-gray-950 has-[:checked]:bg-gray-950 has-[:checked]:text-white"
            >
              <input
                type="radio"
                name="kind"
                value={kind}
                defaultChecked={defaultKind === kind}
                className="sr-only"
              />
              <span className="font-medium">{KIND_LABELS[kind].label}</span>
              <span className="mt-1 text-xs opacity-70">{KIND_LABELS[kind].help}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <label className={labelClass}>
        回顾类型
        <select name="reflectionType" defaultValue={defaultReflectionType} className={inputClass}>
          {REFLECTION_TYPES.map((type) => (
            <option key={type} value={type}>
              {REFLECTION_LABELS[type]}
            </option>
          ))}
        </select>
        <span className={helperTextClass}>仅在保存为回顾时使用。</span>
      </label>

      <fieldset>
        <legend className={labelClass}>预设心情</legend>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {MOODS.map((mood) => (
            <label
              key={mood.value}
              className="flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs text-gray-500 transition hover:border-gray-300 hover:bg-gray-50 has-[:checked]:border-gray-950 has-[:checked]:bg-gray-950 has-[:checked]:text-white"
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
        <label className={labelClass}>
          人物
          <input
            type="text"
            name="people"
            placeholder="用逗号或空格分隔"
            defaultValue={defaults?.people.join(", ") ?? ""}
            className={inputClass}
          />
        </label>
        <label className={labelClass}>
          关系
          <input
            type="text"
            name="relationships"
            placeholder="朋友 / 家人 / 同事…"
            defaultValue={defaults?.relationships.join(", ") ?? ""}
            className={inputClass}
          />
        </label>
      </div>

      <label className={labelClass}>
        标签
        <input
          type="text"
          name="tags"
          placeholder="用逗号或空格分隔，如：散步, 工作"
          defaultValue={defaults?.tags.join(", ") ?? ""}
          className={inputClass}
        />
      </label>

      {error ? <p className="text-sm text-red-600">{error}</p> : null}

      <button type="submit" disabled={busy} className={primaryButtonClass}>
        {busy ? "保存中…" : submitLabel}
      </button>
    </Form>
  );
}
