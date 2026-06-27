import { Link } from "react-router-dom";
import type { Memo } from "../lib/api";
import { excerpt } from "../lib/memos";

const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"];

function clampMonth(
  year: number,
  month: number,
): { year: number; month: number } {
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
  dayEntries: Memo[];
}

/** Month grid for 历史's calendar view: dotted day cells + the selected day's records. */
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
    <div className="grid gap-4 sm:gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <section className="rounded-lg border border-gray-200 bg-white p-3 sm:p-6 dark:border-gray-800 dark:bg-gray-900">
        <div className="mb-4 grid grid-cols-2 items-center gap-3 sm:mb-5 sm:flex sm:justify-between">
          <Link
            to={monthHref(prev.year, prev.month)}
            className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
          >
            ← {prev.year}年{prev.month}月
          </Link>
          <h2 className="order-first col-span-2 text-center font-semibold text-gray-900 sm:order-none sm:col-auto dark:text-gray-50">
            {year}年{month}月
          </h2>
          <Link
            to={monthHref(next.year, next.month)}
            className="text-right text-gray-500 text-sm hover:text-gray-900 sm:text-left dark:text-gray-400 dark:hover:text-gray-100"
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
      </section>

      <aside className="rounded-lg border border-gray-200 bg-white p-3 sm:p-5 dark:border-gray-800 dark:bg-gray-900">
        {selectedDate ? (
          <>
            <h3 className="mb-2 font-medium text-gray-700 text-sm dark:text-gray-300">
              {selectedDate}
            </h3>
            {dayEntries.length === 0 ? (
              <p className="text-gray-400 text-sm dark:text-gray-500">
                这一天没有记录。
              </p>
            ) : (
              <ul className="space-y-2">
                {dayEntries.map((memo) => (
                  <li key={memo.id}>
                    <Link
                      to={`/entries/${memo.id}`}
                      className="block rounded-lg px-3 py-2 hover:bg-gray-100 dark:hover:bg-gray-800"
                    >
                      {excerpt(memo.content, 40) || "空白记录"}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : (
          <p className="text-gray-400 text-sm dark:text-gray-500">
            选择一天查看当天记录。
          </p>
        )}
      </aside>
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

function DayCell({
  date,
  count,
  isToday,
  isSelected,
  year,
  month,
}: DayCellProps) {
  const day = Number(date.slice(8));
  const base =
    "flex aspect-square min-h-10 flex-col items-center justify-center rounded-lg border text-sm sm:min-h-12 sm:text-base";
  const state = isSelected
    ? "border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900"
    : count > 0
      ? "border-gray-200 bg-white hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-800 dark:hover:bg-gray-700"
      : "border-transparent text-gray-400 hover:bg-gray-100 dark:text-gray-500 dark:hover:bg-gray-800";

  return (
    <Link to={monthHref(year, month, date)} className={`${base} ${state}`}>
      <span
        className={
          isToday && !isSelected
            ? "font-bold text-gray-900 dark:text-gray-100"
            : ""
        }
      >
        {day}
      </span>
      {count > 0 ? (
        <span
          className={`mt-0.5 h-1.5 w-1.5 rounded-full ${
            isSelected
              ? "bg-white dark:bg-gray-950"
              : "bg-gray-500 dark:bg-gray-400"
          }`}
        />
      ) : null}
    </Link>
  );
}
