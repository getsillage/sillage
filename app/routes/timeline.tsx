import { env } from "cloudflare:workers";
import { Link } from "react-router";
import { CalendarView } from "~/components/CalendarView";
import { EntryCard } from "~/components/EntryCard";
import { TraceThread, TraceThreadItem } from "~/components/TraceThread";
import {
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  serifTitleClass,
  subtlePanelClass,
  wideShellClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { monthGrid, pad2, todayISO, yearsBetween } from "~/lib/date";
import { getEntryDateCounts, getOnThisDay, listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { type EntryWithTags, listEntries } from "~/lib/db/entries";
import type { Route } from "./+types/timeline";

export function meta(_: Route.MetaArgs) {
  return [{ title: "历史 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const url = new URL(request.url);
  const today = todayISO();

  if (url.searchParams.get("view") === "calendar") {
    const year = Number(url.searchParams.get("y")) || Number(today.slice(0, 4));
    const month = Number(url.searchParams.get("m")) || Number(today.slice(5, 7));
    const selectedDate = url.searchParams.get("date");
    const start = `${year}-${pad2(month)}-01`;
    const end = `${year}-${pad2(month)}-31`;
    const [counts, dayEntries] = await Promise.all([
      getEntryDateCounts(db, start, end),
      selectedDate ? listEntriesByDate(db, selectedDate) : Promise.resolve([]),
    ]);
    return {
      view: "calendar" as const,
      year,
      month,
      today,
      selectedDate,
      weeks: monthGrid(year, month),
      counts: Object.fromEntries(counts),
      dayEntries,
    };
  }

  const [list, onThisDay] = await Promise.all([listEntries(db, 120), getOnThisDay(db, today)]);

  return {
    view: "list" as const,
    today,
    entries: list,
    onThisDay,
  };
}

function viewToggleClass(active: boolean): string {
  return active
    ? "rounded-md bg-celadon-50 px-3 py-1 text-sm text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200"
    : "rounded-md px-3 py-1 text-gray-500 text-sm hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100";
}

function ViewToggle({ view }: { view: "list" | "calendar" }) {
  return (
    <div className="flex gap-0.5 rounded-lg border border-gray-200 bg-white p-0.5 dark:border-gray-800 dark:bg-gray-900">
      <Link to="/timeline" className={viewToggleClass(view === "list")}>
        列表
      </Link>
      <Link to="/timeline?view=calendar" className={viewToggleClass(view === "calendar")}>
        日历
      </Link>
    </div>
  );
}

function excerpt(body: string, max = 40): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function OnThisDay({ entries, today }: { entries: EntryWithTags[]; today: string }) {
  return (
    <section className="rounded-lg bg-clay-50 p-4 dark:bg-clay-900/50">
      <h2 className={`text-sm ${serifTitleClass}`}>那年今日</h2>
      <ul className="mt-3 space-y-2">
        {entries.map((entry) => (
          <li key={entry.id}>
            <Link
              to={`/entries/${entry.id}`}
              className="block rounded-lg px-3 py-2 text-clay-600 text-sm transition hover:bg-white/60 dark:text-clay-300 dark:hover:bg-gray-900/50"
            >
              <span className="font-medium">{yearsBetween(entry.entryDate, today)}年前</span>
              <span> · {entry.entryDate}</span>
              <span> · {excerpt(entry.body) || "空白记录"}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function Timeline({ loaderData }: Route.ComponentProps) {
  return (
    <main className={wideShellClass}>
      <section className={pageSectionClass}>
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className={pageTitleClass}>历史</h1>
            <p className={pageLeadClass}>按时间查看短记录和笔记。</p>
          </div>
          <ViewToggle view={loaderData.view} />
        </header>

        {loaderData.view === "calendar" ? (
          <CalendarView
            year={loaderData.year}
            month={loaderData.month}
            today={loaderData.today}
            selectedDate={loaderData.selectedDate}
            weeks={loaderData.weeks}
            counts={loaderData.counts}
            dayEntries={loaderData.dayEntries}
          />
        ) : (
          <div className="space-y-6">
            {loaderData.onThisDay.length > 0 ? (
              <OnThisDay entries={loaderData.onThisDay} today={loaderData.today} />
            ) : null}
            <section className="min-w-0">
              {loaderData.entries.length === 0 ? (
                <div
                  className={`${subtlePanelClass} px-4 py-10 text-center text-gray-500 text-sm dark:text-gray-400`}
                >
                  没有符合条件的记录。换个筛选，或从一个瞬间开始。
                </div>
              ) : (
                <TraceThread>
                  {loaderData.entries.map((entry) => (
                    <TraceThreadItem key={entry.id}>
                      <EntryCard entry={entry} openOnCardClick />
                    </TraceThreadItem>
                  ))}
                </TraceThread>
              )}
            </section>
          </div>
        )}
      </section>
    </main>
  );
}
