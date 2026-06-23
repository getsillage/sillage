import { Form, useNavigation } from "react-router";
import { todayISO } from "~/lib/date";
import { MarkdownEditor } from "./MarkdownEditor";

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
    <Form method="post" className="space-y-5">
      {intent ? <input type="hidden" name="intent" value={intent} /> : null}

      <div className="flex flex-wrap gap-4">
        <label className="text-sm font-medium text-gray-700">
          日期
          <input
            type="date"
            name="entryDate"
            required
            defaultValue={defaults?.entryDate ?? todayISO()}
            className="mt-1 block rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="text-sm font-medium text-gray-700">
          天气
          <input
            type="text"
            name="weather"
            placeholder="晴 / 阴 / 雨…"
            defaultValue={defaults?.weather ?? ""}
            className="mt-1 block rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
        </label>
      </div>

      <label className="block text-sm font-medium text-gray-700">
        标题
        <input
          type="text"
          name="title"
          placeholder="给今天起个标题"
          defaultValue={defaults?.title ?? ""}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
        />
      </label>

      <fieldset>
        <legend className="text-sm font-medium text-gray-700">心情</legend>
        <div className="mt-2 flex gap-2">
          {MOODS.map((mood) => (
            <label
              key={mood.value}
              className="flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-gray-200 px-3 py-2 text-xs text-gray-500 has-[:checked]:border-gray-900 has-[:checked]:text-gray-900"
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
        <span className="mb-1 block text-sm font-medium text-gray-700">内容</span>
        <MarkdownEditor name="body" defaultValue={defaults?.body ?? ""} />
      </div>

      <label className="block text-sm font-medium text-gray-700">
        标签
        <input
          type="text"
          name="tags"
          placeholder="用逗号或空格分隔，如：旅行, 美食"
          defaultValue={defaults?.tags.join(", ") ?? ""}
          className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
        />
      </label>

      {error ? <p className="text-sm text-red-600">{error}</p> : null}

      <button
        type="submit"
        disabled={busy}
        className="rounded-lg bg-gray-900 px-5 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-60"
      >
        {busy ? "保存中…" : submitLabel}
      </button>
    </Form>
  );
}
