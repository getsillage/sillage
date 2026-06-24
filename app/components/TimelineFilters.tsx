import { Form, Link } from "react-router";

const KIND_OPTIONS = [
  { value: "", label: "全部类型" },
  { value: "fragment", label: "片段" },
  { value: "note", label: "笔记" },
  { value: "draft", label: "草稿" },
] as const;

const MOOD_OPTIONS = [
  { value: "", label: "全部心情" },
  { value: "1", label: "低落" },
  { value: "2", label: "失落" },
  { value: "3", label: "平静" },
  { value: "4", label: "轻松" },
  { value: "5", label: "明亮" },
] as const;

const filterSelectClass =
  "rounded-lg border border-gray-300 bg-white px-2.5 py-1.5 text-gray-700 text-sm shadow-sm transition focus:border-gray-950 focus:outline-none focus:ring-2 focus:ring-gray-900/10 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-200 dark:focus:border-gray-200 dark:focus:ring-gray-100/20";

export interface TimelineFacets {
  tags: string[];
  people: string[];
  relationships: string[];
}

export interface TimelineActiveFilters {
  kind: string;
  tag: string;
  person: string;
  relationship: string;
  mood: string;
}

interface TimelineFiltersProps {
  facets: TimelineFacets;
  active: TimelineActiveFilters;
}

function hasAny(active: TimelineActiveFilters): boolean {
  return Boolean(active.kind || active.tag || active.person || active.relationship || active.mood);
}

function autoSubmit(event: { currentTarget: HTMLSelectElement }): void {
  event.currentTarget.form?.requestSubmit();
}

function PresetSelect({
  name,
  label,
  value,
  options,
}: {
  name: string;
  label: string;
  value: string;
  options: ReadonlyArray<{ value: string; label: string }>;
}) {
  return (
    <select
      name={name}
      aria-label={label}
      defaultValue={value}
      onChange={autoSubmit}
      className={filterSelectClass}
    >
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

function FacetSelect({
  name,
  label,
  allLabel,
  prefix,
  value,
  values,
}: {
  name: string;
  label: string;
  allLabel: string;
  prefix?: string;
  value: string;
  values: string[];
}) {
  return (
    <select
      name={name}
      aria-label={label}
      defaultValue={value}
      onChange={autoSubmit}
      className={filterSelectClass}
    >
      <option value="">{allLabel}</option>
      {values.map((item) => (
        <option key={item} value={item}>
          {prefix ? `${prefix}${item}` : item}
        </option>
      ))}
    </select>
  );
}

/**
 * Query-param-driven filter bar for 痕迹. A GET form keeps the URL shareable; each
 * control auto-submits on change (with a no-JS submit button fallback). `key` ties
 * the form to the active filters so external resets (清除 / 类型链接) re-init the
 * uncontrolled selects.
 */
export function TimelineFilters({ facets, active }: TimelineFiltersProps) {
  return (
    <Form
      key={`${active.kind}|${active.tag}|${active.person}|${active.relationship}|${active.mood}`}
      method="get"
      action="/timeline"
      className="flex flex-wrap items-center gap-2"
    >
      <PresetSelect name="kind" label="类型" value={active.kind} options={KIND_OPTIONS} />
      <FacetSelect
        name="tag"
        label="标签"
        allLabel="全部标签"
        prefix="#"
        value={active.tag}
        values={facets.tags}
      />
      <FacetSelect
        name="person"
        label="人物"
        allLabel="全部人物"
        value={active.person}
        values={facets.people}
      />
      <FacetSelect
        name="relationship"
        label="关系"
        allLabel="全部关系"
        value={active.relationship}
        values={facets.relationships}
      />
      <PresetSelect name="mood" label="心情" value={active.mood} options={MOOD_OPTIONS} />
      <noscript>
        <button
          type="submit"
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-gray-700 text-sm dark:border-gray-700 dark:text-gray-200"
        >
          筛选
        </button>
      </noscript>
      {hasAny(active) ? (
        <Link
          to="/timeline"
          className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          清除
        </Link>
      ) : null}
    </Form>
  );
}
