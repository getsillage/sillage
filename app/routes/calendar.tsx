import { env } from "cloudflare:workers";
import { Link } from "react-router";
import { monthGrid, pad2, todayISO } from "~/lib/date";
import { getEntryDateCounts, listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import type { Route } from "./+types/calendar";

const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"];

export function meta(_: Route.MetaArgs) {
  return [{ title: "日历 · Sillage" }];
}

function clampMonth(year: number, month: number): { year: number; month: number } {
  if (month < 1) {
    return { year: year - 1, month: 12 };
  }
  if (month > 12) {
    return { year: year + 1, month: 1 };
  }
  return { year, month };
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const now = todayISO();
  const year = Number(url.searchParams.get("y")) || Number(now.slice(0, 4));
  const month = Number(url.searchParams.get("m")) || Number(now.slice(5, 7));
  const selectedDate = url.searchParams.get("date");

  const start = `${year}-${pad2(month)}-01`;
  const end = `${year}-${pad2(month)}-31`;
  const db = getDb(env.DB);
  const counts = await getEntryDateCounts(db, start, end);
  const dayEntries = selectedDate ? await listEntriesByDate(db, selectedDate) : [];

  return {
    year,
    month,
    today: now,
    selectedDate,
    weeks: monthGrid(year, month),
    counts: Object.fromEntries(counts),
    dayEntries,
  };
}

export default function Calendar({ loaderData }: Route.ComponentProps) {
  const { year, month, today, selectedDate, weeks, counts, dayEntries } = loaderData;
  const prev = clampMonth(year, month - 1);
  const next = clampMonth(year, month + 1);

  return (
    <main className="mx-auto max-w-2xl p-6">
      <div className="mb-4 flex items-center justify-between">
        <Link
          to={`/calendar?y=${prev.year}&m=${prev.month}`}
          className="text-gray-500 text-sm hover:text-gray-900"
        >
          ← {prev.year}年{prev.month}月
        </Link>
        <h1 className="font-semibold text-lg">
          {year}年{month}月
        </h1>
        <Link
          to={`/calendar?y=${next.year}&m=${next.month}`}
          className="text-gray-500 text-sm hover:text-gray-900"
        >
          {next.year}年{next.month}月 →
        </Link>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center text-gray-400 text-xs">
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
          <h2 className="mb-2 font-medium text-gray-700 text-sm">{selectedDate}</h2>
          {dayEntries.length === 0 ? (
            <p className="text-gray-400 text-sm">这一天没有记录。</p>
          ) : (
            <ul className="space-y-2">
              {dayEntries.map((entry) => (
                <li key={entry.id}>
                  <Link
                    to={`/entries/${entry.id}`}
                    className="block rounded-lg border border-gray-200 p-3 hover:bg-gray-50"
                  >
                    {entry.title || "（无标题）"}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </main>
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
    ? "border-gray-900 bg-gray-900 text-white"
    : count > 0
      ? "border-gray-300 bg-white hover:bg-gray-50"
      : "border-transparent text-gray-400 hover:bg-gray-100";

  return (
    <Link to={`/calendar?y=${year}&m=${month}&date=${date}`} className={`${base} ${state}`}>
      <span className={isToday && !isSelected ? "font-bold text-gray-900" : ""}>{day}</span>
      {count > 0 ? (
        <span
          className={`mt-0.5 h-1.5 w-1.5 rounded-full ${isSelected ? "bg-white" : "bg-gray-900"}`}
        />
      ) : null}
    </Link>
  );
}
