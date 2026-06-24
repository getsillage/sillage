import { env } from "cloudflare:workers";
import { Link } from "react-router";
import { ReviewTab } from "~/components/insights/ReviewTab";
import { AskTab } from "~/components/memory/AskTab";
import { pageLeadClass, pageSectionClass, pageShellClass, pageTitleClass } from "~/components/ui";
import { type AskActionData, runAskAction } from "~/lib/ai/ask-action";
import { requireSession } from "~/lib/auth/session";
import { todayISO } from "~/lib/date";
import { listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { listEntries } from "~/lib/db/entries";
import { listSummaries } from "~/lib/db/summaries";
import { normalizeEntryKind, parseTextList } from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import {
  isSummaryIntent,
  runSummaryAction,
  type SummaryActionData,
} from "~/lib/product/summary-actions";
import { searchEntriesByKeyword } from "~/lib/search/fts";
import type { Route } from "./+types/memory";

export function meta(_: Route.MetaArgs) {
  return [{ title: "微光 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const url = new URL(request.url);
  const tab = url.searchParams.get("tab") === "ask" ? "ask" : "review";

  if (tab === "ask") {
    const query = url.searchParams.get("q")?.trim() ?? "";
    const [recentEntries, results] = await Promise.all([
      listEntries(db, 80),
      query ? searchEntriesByKeyword(db, query) : Promise.resolve([]),
    ]);

    const people = new Map<string, number>();
    const relationships = new Map<string, number>();
    for (const entry of recentEntries) {
      for (const person of parseTextList(entry.people)) {
        people.set(person, (people.get(person) ?? 0) + 1);
      }
      for (const relationship of parseTextList(entry.relationships)) {
        relationships.set(relationship, (relationships.get(relationship) ?? 0) + 1);
      }
    }

    return {
      tab: "ask" as const,
      query,
      results,
      people: [...people.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12),
      relationships: [...relationships.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12),
    };
  }

  const today = todayISO();
  const [todayEntries, recentEntries, summaryRows] = await Promise.all([
    listEntriesByDate(db, today),
    listEntries(db, 80),
    listSummaries(db, { limit: 30 }),
  ]);

  return {
    tab: "review" as const,
    todayInsights: todayEntries
      .filter((entry) => entry.summary)
      .map((entry) => ({ id: entry.id, summary: entry.summary })),
    recentInsights: recentEntries
      .filter((entry) => entry.summary)
      .slice(0, 12)
      .map((entry) => ({ id: entry.id, summary: entry.summary })),
    themes: recentEntries
      .flatMap((entry) => entry.tags)
      .reduce<Record<string, number>>((acc, tag) => {
        acc[tag] = (acc[tag] ?? 0) + 1;
        return acc;
      }, {}),
    noteCount: recentEntries.filter((entry) => normalizeEntryKind(entry.kind) === "note").length,
    suggestions: buildEntryFormSuggestions(recentEntries),
    pickerEntries: recentEntries
      .slice(0, 40)
      .map((entry) => ({ id: entry.id, entryDate: entry.entryDate, title: entry.title })),
    summaries: summaryRows.map((row) => ({
      id: row.id,
      scope: row.scope,
      periodType: row.periodType,
      startDate: row.startDate,
      endDate: row.endDate,
      style: row.style,
      title: row.title,
      content: row.content,
      sourceEntryIds: row.sourceEntryIds,
      generatedAt: row.generatedAt,
    })),
  };
}

export async function action({
  request,
}: Route.ActionArgs): Promise<AskActionData | SummaryActionData> {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  if (intent === "ask") {
    return runAskAction(db, form);
  }
  if (isSummaryIntent(intent)) {
    return runSummaryAction(db, form, intent);
  }
  return { intent: "ask", ok: false, message: "未知操作" };
}

function tabClass(active: boolean): string {
  return active
    ? "border-gray-950 border-b-2 pb-2 font-medium text-gray-950 text-sm dark:border-gray-50 dark:text-gray-50"
    : "border-transparent border-b-2 pb-2 text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100";
}

export default function Memory({ loaderData }: Route.ComponentProps) {
  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>微光</h1>
          <p className={pageLeadClass}>记录之间亮起的一点光：照见来路，也照向前路。</p>
          <nav className="mt-4 flex gap-5 border-gray-200 border-b dark:border-gray-800">
            <Link to="/memory?tab=review" className={tabClass(loaderData.tab === "review")}>
              回顾
            </Link>
            <Link to="/memory?tab=ask" className={tabClass(loaderData.tab === "ask")}>
              追问
            </Link>
          </nav>
        </header>

        {loaderData.tab === "review" ? (
          <ReviewTab
            todayInsights={loaderData.todayInsights}
            recentInsights={loaderData.recentInsights}
            themes={loaderData.themes}
            noteCount={loaderData.noteCount}
            suggestions={loaderData.suggestions}
            pickerEntries={loaderData.pickerEntries}
            summaries={loaderData.summaries}
          />
        ) : (
          <AskTab
            query={loaderData.query}
            results={loaderData.results}
            people={loaderData.people}
            relationships={loaderData.relationships}
          />
        )}
      </section>
    </main>
  );
}
