import { ChevronLeft, ChevronRight } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import { iconButtonClass } from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { Memo } from "../../lib/api";
import { formatEntryDate, formatMonth, formatWeekday } from "../../lib/date";
import { excerpt } from "./memos";

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
  const { locale, t } = useI18n();
  const location = useLocation();
  const prev = clampMonth(year, month - 1);
  const next = clampMonth(year, month + 1);
  const monthLabel = formatMonth(year, month, locale);
  const prevMonthLabel = formatMonth(prev.year, prev.month, locale);
  const nextMonthLabel = formatMonth(next.year, next.month, locale);
  const weekdays = Array.from({ length: 7 }, (_, index) => ({
    key: `weekday-${index}`,
    label: formatWeekday(index, locale),
  }));

  return (
    <div className="grid gap-4 sm:gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <section className="rounded-lg border border-gray-200/80 bg-white/80 p-3 shadow-sm shadow-gray-900/[0.03] sm:p-6 dark:border-gray-800 dark:bg-gray-900/70 dark:shadow-black/10">
        <div className="mb-4 grid grid-cols-[2.5rem_minmax(0,1fr)_2.5rem] items-center gap-2 sm:mb-5">
          <Link
            to={monthHref(prev.year, prev.month)}
            className={iconButtonClass}
            aria-label={t("calendar.previousMonthLabel", {
              month: prevMonthLabel,
            })}
            title={t("calendar.previousMonth")}
          >
            <ChevronLeft className="h-5 w-5" />
          </Link>
          <h2 className="text-center font-semibold text-gray-900 dark:text-gray-50">
            {monthLabel}
          </h2>
          <Link
            to={monthHref(next.year, next.month)}
            className={iconButtonClass}
            aria-label={t("calendar.nextMonthLabel", {
              month: nextMonthLabel,
            })}
            title={t("calendar.nextMonth")}
          >
            <ChevronRight className="h-5 w-5" />
          </Link>
        </div>

        <table
          className="w-full table-fixed border-separate [border-spacing:0.25rem]"
          aria-label={t("calendar.label", { month: monthLabel })}
        >
          <thead>
            <tr className="text-center text-gray-500 text-xs dark:text-gray-400">
              {weekdays.map((day) => (
                <th scope="col" key={day.key} className="py-1 font-medium">
                  {day.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {weeks.map((week) => {
              const weekKey = week.filter(Boolean).join("-");
              return (
                <tr key={weekKey}>
                  {week.map((date, dayIndex) => (
                    <td key={date ?? `${weekKey}-blank-${dayIndex}`}>
                      {date ? (
                        <DayCell
                          date={date}
                          count={counts[date] ?? 0}
                          isToday={date === today}
                          isSelected={date === selectedDate}
                          year={year}
                          month={month}
                        />
                      ) : null}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>

      <aside className="self-start rounded-lg border border-gray-200/80 bg-white/80 p-3 shadow-sm shadow-gray-900/[0.03] sm:p-5 dark:border-gray-800 dark:bg-gray-900/70 dark:shadow-black/10">
        {selectedDate ? (
          <>
            <h3 className="mb-2 font-medium text-gray-700 text-sm dark:text-gray-300">
              {formatEntryDate(selectedDate, today, locale)}
            </h3>
            {dayEntries.length === 0 ? (
              <p className="text-gray-400 text-sm dark:text-gray-500">
                {t("calendar.noRecordsDay")}
              </p>
            ) : (
              <ul className="space-y-2">
                {dayEntries.map((memo) => (
                  <li key={memo.id}>
                    <Link
                      to={`/entries/${memo.id}`}
                      state={{
                        returnTo: `${location.pathname}${location.search}${location.hash}`,
                      }}
                      className="block rounded-lg px-3 py-2 text-gray-800 text-sm transition hover:bg-gray-100 dark:text-gray-100 dark:hover:bg-gray-800"
                    >
                      {excerpt(memo.content, 40) || t("records.blank")}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : (
          <p className="text-gray-400 text-sm dark:text-gray-500">
            {t("calendar.selectDay")}
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
  const { locale, t } = useI18n();
  const day = Number(date.slice(8));
  const base =
    "flex aspect-square min-h-10 w-full flex-col items-center justify-center rounded-lg border text-sm transition-colors sm:min-h-12 sm:text-base";
  const state = isSelected
    ? "border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-gray-900"
    : count > 0
      ? "border-gray-200 bg-white hover:bg-gray-100 dark:border-gray-700 dark:bg-gray-800 dark:hover:bg-gray-700"
      : "border-transparent text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800";

  const [dateYear, dateMonth, dateDay] = date.split("-").map(Number);
  const dateLabel = new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(new Date(dateYear, dateMonth - 1, dateDay));
  const details = [
    dateLabel,
    count === 0
      ? t("calendar.dayNoRecords")
      : t(count === 1 ? "calendar.dayCountOne" : "calendar.dayCountMany", {
          count,
        }),
    isToday ? t("calendar.today") : "",
    isSelected ? t("calendar.selected") : "",
  ].filter(Boolean);
  const label = details.join(locale === "zh-CN" ? "，" : ", ");

  return (
    <Link
      to={monthHref(year, month, date)}
      className={`${base} ${state}`}
      aria-label={label}
      aria-current={isToday ? "date" : undefined}
    >
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
        <span className="mt-0.5 inline-flex items-center">
          <span
            aria-hidden="true"
            className={`h-1.5 w-1.5 rounded-full ${
              isSelected
                ? "bg-white dark:bg-gray-950"
                : "bg-gray-500 dark:bg-gray-400"
            }`}
          />
          <span className="sr-only">
            {t(count === 1 ? "calendar.dayCountOne" : "calendar.dayCountMany", {
              count,
            })}
          </span>
        </span>
      ) : null}
    </Link>
  );
}
