import { env } from "cloudflare:workers";
import { ReviewTab } from "~/components/insights/ReviewTab";
import {
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  readingShellClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { listEntries } from "~/lib/db/entries";
import { listSummaries } from "~/lib/db/summaries";
import { normalizeEntryKind } from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import {
  isSummaryIntent,
  runSummaryAction,
  type SummaryActionData,
} from "~/lib/product/summary-actions";
import type { Route } from "./+types/review";

export function meta(_: Route.MetaArgs) {
  return [{ title: "照见 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const [recentEntries, summaryRows] = await Promise.all([
    listEntries(db, 80),
    listSummaries(db, { limit: 30 }),
  ]);

  return {
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

export async function action({ request }: Route.ActionArgs): Promise<SummaryActionData> {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  if (isSummaryIntent(intent)) {
    return runSummaryAction(db, form, intent);
  }
  return { intent: "generate", ok: false, message: "未知操作" };
}

export default function Review({ loaderData }: Route.ComponentProps) {
  return (
    <main className={readingShellClass}>
      <section className={`${pageSectionClass} min-h-[calc(100svh-92px)]`}>
        <header>
          <h1 className={pageTitleClass}>照见</h1>
          <p className={pageLeadClass}>让 AI 主动整理最近留下的线索，回看那些正在浮现的主题。</p>
        </header>

        <ReviewTab
          themes={loaderData.themes}
          noteCount={loaderData.noteCount}
          suggestions={loaderData.suggestions}
          pickerEntries={loaderData.pickerEntries}
          summaries={loaderData.summaries}
        />
      </section>
    </main>
  );
}
