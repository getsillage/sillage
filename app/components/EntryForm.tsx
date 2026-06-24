import { useId, useRef, useState } from "react";
import { Form, useNavigation } from "react-router";
import { todayISO } from "~/lib/date";
import { ENTRY_KINDS, type EntryKind, NOTE_TYPES, type NoteType } from "~/lib/product/entry-fields";
import type { EntryFormSuggestions } from "~/lib/product/entry-suggestions";
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

function splitDelimitedValues(raw: string): string[] {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function setInputValue(input: HTMLInputElement | null, value: string) {
  if (!input) {
    return;
  }
  input.value = value;
  input.focus();
}

function appendInputValue(input: HTMLInputElement | null, value: string) {
  if (!input) {
    return;
  }
  const existing = splitDelimitedValues(input.value);
  if (!existing.includes(value)) {
    existing.push(value);
  }
  input.value = existing.join(", ");
  input.focus();
}

function SuggestionMenu({
  label,
  options,
  onSelect,
}: {
  label: string;
  options: string[];
  onSelect: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);

  if (options.length === 0) {
    return null;
  }

  return (
    <div className="absolute top-2 right-2 z-20">
      <button
        type="button"
        aria-label={label}
        aria-expanded={open}
        className="flex h-7 w-7 items-center justify-center rounded-md text-gray-400 transition hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-gray-900/10"
        onClick={() => setOpen((value) => !value)}
      >
        <span aria-hidden="true" className="-mt-0.5 text-base leading-none">
          ⌄
        </span>
      </button>
      {open ? (
        <div className="absolute top-9 right-0 z-30 max-h-48 min-w-36 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
          {options.map((option) => (
            <button
              key={option}
              type="button"
              className="block w-full px-3 py-1.5 text-left text-gray-700 text-sm transition hover:bg-gray-50 hover:text-gray-950 focus:bg-gray-50 focus:outline-none"
              onClick={() => {
                onSelect(option);
                setOpen(false);
              }}
            >
              {option}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function suggestionInputClass(options: string[]): string {
  return options.length > 0 ? `${inputClass} pr-12` : inputClass;
}

export function EntryForm({
  defaults,
  suggestions,
  error,
  submitLabel = "保存",
  intent,
}: EntryFormProps) {
  const navigation = useNavigation();
  const busy = navigation.state !== "idle";
  const idBase = useId().replaceAll(":", "");
  const defaultKind = defaults?.kind ?? "fragment";
  const [selectedKind, setSelectedKind] = useState<EntryKind>(defaultKind);
  const defaultNoteType = defaults?.noteType ?? "daily";
  const weatherOptions = includeCurrentOption(WEATHER_OPTIONS, defaults?.weather);
  const locationSuggestions = suggestions?.locations ?? [];
  const peopleSuggestions = suggestions?.people ?? [];
  const relationshipSuggestions = suggestions?.relationships ?? [];
  const tagSuggestions = suggestions?.tags ?? [];
  const locationRef = useRef<HTMLInputElement>(null);
  const peopleRef = useRef<HTMLInputElement>(null);
  const relationshipRef = useRef<HTMLInputElement>(null);
  const tagRef = useRef<HTMLInputElement>(null);
  const locationId = `${idBase}-location`;
  const peopleId = `${idBase}-people`;
  const relationshipId = `${idBase}-relationships`;
  const tagId = `${idBase}-tags`;

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
        <div className="relative">
          <input
            id={locationId}
            ref={locationRef}
            type="text"
            name="location"
            placeholder="某个城市、房间、路口…"
            defaultValue={defaults?.location ?? ""}
            className={suggestionInputClass(locationSuggestions)}
          />
          <SuggestionMenu
            label="选择已有地点"
            options={locationSuggestions}
            onSelect={(value) => setInputValue(locationRef.current, value)}
          />
        </div>
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
              className="flex min-h-20 cursor-pointer flex-col rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm transition hover:border-gray-300 hover:bg-gray-50 has-[:checked]:border-gray-950 has-[:checked]:bg-gray-950 has-[:checked]:text-white"
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
        <div>
          <label htmlFor={peopleId} className={labelClass}>
            人物
          </label>
          <div className="relative">
            <input
              id={peopleId}
              ref={peopleRef}
              type="text"
              name="people"
              placeholder="输入人物，用逗号分隔"
              defaultValue={defaults?.people.join(", ") ?? ""}
              className={suggestionInputClass(peopleSuggestions)}
            />
            <SuggestionMenu
              label="添加已有人物"
              options={peopleSuggestions}
              onSelect={(value) => appendInputValue(peopleRef.current, value)}
            />
          </div>
        </div>
        <div>
          <label htmlFor={relationshipId} className={labelClass}>
            关系
          </label>
          <div className="relative">
            <input
              id={relationshipId}
              ref={relationshipRef}
              type="text"
              name="relationships"
              placeholder="输入关系，用逗号分隔"
              defaultValue={defaults?.relationships.join(", ") ?? ""}
              className={suggestionInputClass(relationshipSuggestions)}
            />
            <SuggestionMenu
              label="添加已有关系"
              options={relationshipSuggestions}
              onSelect={(value) => appendInputValue(relationshipRef.current, value)}
            />
          </div>
        </div>
      </div>

      <div>
        <label htmlFor={tagId} className={labelClass}>
          标签
        </label>
        <div className="relative">
          <input
            id={tagId}
            ref={tagRef}
            type="text"
            name="tags"
            placeholder="输入标签，用逗号分隔"
            defaultValue={defaults?.tags.join(", ") ?? ""}
            className={suggestionInputClass(tagSuggestions)}
          />
          <SuggestionMenu
            label="添加已有标签"
            options={tagSuggestions}
            onSelect={(value) => appendInputValue(tagRef.current, value)}
          />
        </div>
      </div>

      {error ? <p className="text-sm text-red-600">{error}</p> : null}

      <button type="submit" disabled={busy} className={primaryButtonClass}>
        {busy ? "保存中…" : submitLabel}
      </button>
    </Form>
  );
}
