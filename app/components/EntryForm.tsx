import { Form, useNavigation } from "react-router";
import { todayISO } from "~/lib/date";
import { MarkdownEditor } from "./MarkdownEditor";
import { helperTextClass, inputClass, labelClass, primaryButtonClass } from "./ui";

export interface EntryFormDefaults {
  entryDate: string;
  title: string;
  body: string;
  mood: number | null;
  weather: string | null;
  tags: string[];
}

interface EntryFormProps {
  defaults?: EntryFormDefaults;
  error?: string | null;
  submitLabel?: string;
  intent?: string;
}

const MOODS: ReadonlyArray<{ value: number; emoji: string; label: string }> = [
  { value: 1, emoji: "😞", label: "很差" },
  { value: 2, emoji: "😕", label: "一般" },
  { value: 3, emoji: "😐", label: "平静" },
  { value: 4, emoji: "🙂", label: "不错" },
  { value: 5, emoji: "😄", label: "很好" },
];

export function EntryForm({ defaults, error, submitLabel = "保存", intent }: EntryFormProps) {
  const navigation = useNavigation();
  const busy = navigation.state !== "idle";

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
        标题
        <input
          type="text"
          name="title"
          placeholder="给今天起个标题"
          defaultValue={defaults?.title ?? ""}
          className={inputClass}
        />
      </label>

      <fieldset>
        <legend className={labelClass}>心情</legend>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {MOODS.map((mood) => (
            <label
              key={mood.value}
              className="flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-gray-200 px-3 py-2 text-xs text-gray-500 transition hover:border-gray-300 hover:bg-gray-50 has-[:checked]:border-gray-950 has-[:checked]:bg-gray-950 has-[:checked]:text-white"
            >
              <input
                type="radio"
                name="mood"
                value={mood.value}
                defaultChecked={defaults?.mood === mood.value}
                className="sr-only"
              />
              <span className="text-xl">{mood.emoji}</span>
              {mood.label}
            </label>
          ))}
        </div>
      </fieldset>

      <div>
        <span className={`${labelClass} mb-1 block`}>内容</span>
        <MarkdownEditor name="body" defaultValue={defaults?.body ?? ""} />
      </div>

      <label className={labelClass}>
        标签
        <input
          type="text"
          name="tags"
          placeholder="用逗号或空格分隔，如：旅行, 美食"
          defaultValue={defaults?.tags.join(", ") ?? ""}
          className={inputClass}
        />
        <span className={helperTextClass}>输入后会自动拆分为多个标签。</span>
      </label>

      {error ? <p className="text-sm text-red-600">{error}</p> : null}

      <button type="submit" disabled={busy} className={primaryButtonClass}>
        {busy ? "保存中…" : submitLabel}
      </button>
    </Form>
  );
}
