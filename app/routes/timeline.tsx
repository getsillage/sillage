import { env } from "cloudflare:workers";
import { Link } from "react-router";
import { CalendarView } from "~/components/CalendarView";
import { EntryCard } from "~/components/EntryCard";
import { TimelineFilters } from "~/components/TimelineFilters";
import {
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  subtlePanelClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { monthGrid, pad2, todayISO, yearsBetween } from "~/lib/date";
import { getEntryDateCounts, getOnThisDay, listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import {
  type EntryFilter,
  type EntryWithTags,
  listEntries,
  listEntriesFiltered,
} from "~/lib/db/entries";
import { cleanTextList, normalizeEntryKind, parseTextList } from "~/lib/product/entry-fields";
import type { Route } from "./+types/timeline";

export function meta(_: Route.MetaArgs) {
  return [{ title: "痕迹 · Sillage" }];
}

function readFilter(params: URLSearchParams): EntryFilter {
  const filter: EntryFilter = {};
  const kind = params.get("kind");
  if (kind) {
    const normalized = normalizeEntryKind(kind);
    if (normalized === kind) {
      filter.kind = normalized;
    }
  }
  const mood = Number(params.get("mood"));
  if (Number.isInteger(mood) && mood >= 1 && mood <= 5) {
    filter.mood = mood;
  }
  const tag = params.get("tag")?.trim();
  if (tag) {
    filter.tag = tag;
  }
  const person = params.get("person")?.trim();
  if (person) {
    filter.person = person;
  }
  const relationship = params.get("relationship")?.trim();
  if (relationship) {
    filter.relationship = relationship;
  }
  return filter;
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

  const filter = readFilter(url.searchParams);
  const filtersActive = Object.keys(filter).length > 0;
  const [list, facetSource, onThisDay] = await Promise.all([
    listEntriesFiltered(db, filter, 120),
    listEntries(db, 200),
    filtersActive ? Promise.resolve<EntryWithTags[]>([]) : getOnThisDay(db, today),
  ]);

  return {
    view: "list" as const,
    today,
    entries: list,
    onThisDay,
    facets: {
      tags: cleanTextList(facetSource.flatMap((entry) => entry.tags)),
      people: cleanTextList(facetSource.flatMap((entry) => parseTextList(entry.people))),
      relationships: cleanTextList(
        facetSource.flatMap((entry) => parseTextList(entry.relationships)),
      ),
    },
    active: {
      kind: filter.kind ?? "",
      tag: filter.tag ?? "",
      person: filter.person ?? "",
      relationship: filter.relationship ?? "",
      mood: filter.mood ? String(filter.mood) : "",
    },
  };
}

function viewToggleClass(active: boolean): string {
  return active
    ? "rounded-md bg-gray-950 px-3 py-1 text-sm text-white dark:bg-gray-100 dark:text-gray-950"
    : "rounded-md px-3 py-1 text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100";
}

function ViewToggle({ view }: { view: "list" | "calendar" }) {
  return (
    <div className="flex gap-0.5 rounded-lg border border-gray-200 p-0.5 dark:border-gray-800">
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
    <section className="rounded-lg border border-amber-200 bg-amber-50/70 p-4 dark:border-amber-900/60 dark:bg-amber-950/20">
      <h2 className="font-medium text-amber-950 text-sm dark:text-amber-100">那年今日</h2>
      <ul className="mt-3 space-y-2">
        {entries.map((entry) => (
          <li key={entry.id}>
            <Link
              to={`/entries/${entry.id}`}
              className="block rounded-lg border border-amber-200 bg-white/70 px-3 py-2 text-amber-950 text-sm transition hover:border-amber-300 hover:bg-white dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-100 dark:hover:border-amber-800 dark:hover:bg-amber-950/50"
            >
              <span className="font-medium text-amber-800 dark:text-amber-200">
                {yearsBetween(entry.entryDate, today)}年前
              </span>
              <span className="text-amber-700 dark:text-amber-300"> · {entry.entryDate}</span>
              <span> · {entry.title || excerpt(entry.body) || "未命名记录"}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function Timeline({ loaderData }: Route.ComponentProps) {
  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className={pageTitleClass}>痕迹</h1>
            <p className={pageLeadClass}>片段和笔记按时间混排，保留生活流本来的形状。</p>
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
          <div className="grid gap-6 xl:grid-cols-[280px_minmax(0,1fr)] 2xl:grid-cols-[320px_minmax(0,1fr)]">
            <aside className="space-y-4 xl:sticky xl:top-24 xl:self-start">
              <section className={`${subtlePanelClass} p-4 sm:p-5`}>
                <h2 className="mb-3 font-medium text-gray-950 text-sm dark:text-gray-50">筛选</h2>
                <TimelineFilters facets={loaderData.facets} active={loaderData.active} />
              </section>

              {loaderData.onThisDay.length > 0 ? (
                <OnThisDay entries={loaderData.onThisDay} today={loaderData.today} />
              ) : null}
            </aside>

            <section className="min-w-0">
              {loaderData.entries.length === 0 ? (
                <div
                  className={`${subtlePanelClass} px-4 py-10 text-center text-gray-500 text-sm dark:text-gray-400`}
                >
                  没有符合条件的记录。换个筛选，或从一个瞬间开始。
                </div>
              ) : (
                <ul className="grid gap-3 2xl:grid-cols-2">
                  {loaderData.entries.map((entry) => (
                    <li key={entry.id}>
                      <EntryCard entry={entry} showEntryInsight />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </section>
    </main>
  );
}
