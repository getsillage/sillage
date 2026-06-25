import {
  PERIOD_TYPE_LABELS,
  STYLE_LABELS,
  SUMMARY_PERIOD_TYPES,
  SUMMARY_STYLES,
} from "~/lib/product/summary-fields";

export function chipClass(active: boolean): string {
  return active
    ? "rounded-full border border-celadon-600 bg-celadon-50 px-3 py-1.5 text-celadon-800 text-sm dark:border-celadon-400 dark:bg-celadon-900/40 dark:text-celadon-200"
    : "rounded-full border border-gray-200 bg-white px-3 py-1.5 text-gray-700 text-sm transition hover:bg-gray-50 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-300 dark:hover:bg-gray-900";
}

export function badgeClass(): string {
  return "rounded-full bg-gray-100 px-2 py-0.5 text-gray-600 text-xs dark:bg-gray-800 dark:text-gray-300";
}

export function statusClass(ok: boolean): string {
  return `rounded-lg border px-3 py-2 text-sm ${
    ok
      ? "border-green-300 bg-green-50 text-green-800 dark:border-green-900/70 dark:bg-green-950/50 dark:text-green-200"
      : "border-red-300 bg-red-50 text-red-800 dark:border-red-900/70 dark:bg-red-950/50 dark:text-red-200"
  }`;
}

export function ChipGroup({
  options,
  value,
  onChange,
}: {
  options: ReadonlyArray<{ value: string; label: string }>;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={chipClass(value === option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export const STYLE_OPTIONS = SUMMARY_STYLES.map((style) => ({
  value: style,
  label: STYLE_LABELS[style],
}));

export const PERIOD_OPTIONS = SUMMARY_PERIOD_TYPES.map((period) => ({
  value: period,
  label: PERIOD_TYPE_LABELS[period],
}));
