import { Link } from "react-router";
import type { EntryWithTags } from "~/lib/db/entries";

const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"];

function clampMonth(year: number, month: number): { year: number; month: number } {
  if (month < 1) {
    return { year: year - 1, month: 12 };
  }
  if (month > 12) {
    return { year: year + 1, month: 1 };
  }
  return { year, month };
}

function monthHref(year: number, month: number, date?: string): string {
  const base = `/timeline?view=calendar&y=${year}&m=${month}`;
  return date ? `${base}&date=${date}` : base;
}

export interface CalendarViewProps {
  year: number;
  month: number;
  today: string;
  selectedDate: string | null;
  weeks: (string | null)[][];
  counts: Record<string, number>;
  dayEntries: EntryWithTags[];
}

/** Month grid for 痕迹's calendar view: dotted day cells + the selected day's entries. */
export function CalendarView({
  year,
  month,
  today,
  selectedDate,
  weeks,
  counts,
  dayEntries,
}: CalendarViewProps) {
  const prev = clampMonth(year, month - 1);
  const next = clampMonth(year, month + 1);

  return (
    <div className="mx-auto w-full max-w-2xl">
      <div className="mb-4 flex items-center justify-between">
        <Link
          to={monthHref(prev.year, prev.month)}
          className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          ← {prev.year}年{prev.month}月
        </Link>
        <h2 className="font-medium text-gray-950 dark:text-gray-50">
          {year}年{month}月
        </h2>
        <Link
          to={monthHref(next.year, next.month)}
          className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          {next.year}年{next.month}月 →
        </Link>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center text-gray-400 text-xs dark:text-gray-500">
        {WEEKDAYS.map((day) => (
          <div key={day} className="py-1">
            {day}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1">
        {weeks.flat().map((date, index) =>
          date === null ? (
            // biome-ignore lint/suspicious/noArrayIndexKey: blank cells are stable by position
            <div key={`blank-${index}`} />
          ) : (
            <DayCell
              key={date}
              date={date}
              count={counts[date] ?? 0}
              isToday={date === today}
              isSelected={date === selectedDate}
              year={year}
              month={month}
            />
          ),
        )}
      </div>

      {selectedDate ? (
        <section className="mt-6">
          <h3 className="mb-2 font-medium text-gray-700 text-sm dark:text-gray-300">
            {selectedDate}
          </h3>
          {dayEntries.length === 0 ? (
            <p className="text-gray-400 text-sm dark:text-gray-500">这一天没有记录。</p>
          ) : (
            <ul className="space-y-2">
              {dayEntries.map((entry) => (
                <li key={entry.id}>
                  <Link
                    to={`/entries/${entry.id}`}
                    className="block rounded-lg border border-gray-200 p-3 hover:bg-gray-50 dark:border-gray-800 dark:hover:bg-gray-900"
                  >
                    {entry.title || "（无标题）"}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </div>
  );
}

interface DayCellProps {
  date: string;
  count: number;
  isToday: boolean;
  isSelected: boolean;
  year: number;
  month: number;
}

function DayCell({ date, count, isToday, isSelected, year, month }: DayCellProps) {
  const day = Number(date.slice(8));
  const base = "flex aspect-square flex-col items-center justify-center rounded-lg border text-sm";
  const state = isSelected
    ? "border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-950"
    : count > 0
      ? "border-gray-300 bg-white hover:bg-gray-50 dark:border-gray-800 dark:bg-gray-900 dark:hover:bg-gray-800"
      : "border-transparent text-gray-400 hover:bg-gray-100 dark:text-gray-500 dark:hover:bg-gray-900";

  return (
    <Link to={monthHref(year, month, date)} className={`${base} ${state}`}>
      <span className={isToday && !isSelected ? "font-bold text-gray-900 dark:text-gray-50" : ""}>
        {day}
      </span>
      {count > 0 ? (
        <span
          className={`mt-0.5 h-1.5 w-1.5 rounded-full ${
            isSelected ? "bg-white dark:bg-gray-950" : "bg-gray-900 dark:bg-gray-100"
          }`}
        />
      ) : null}
    </Link>
  );
}
