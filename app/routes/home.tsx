import { env } from "cloudflare:workers";
import { Link, redirect } from "react-router";
import { EntryCard } from "~/components/EntryCard";
import { EntryForm, type EntryFormDefaults } from "~/components/EntryForm";
import { TraceThread, TraceThreadItem } from "~/components/TraceThread";
import {
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  panelClass,
  readingShellClass,
  serifTitleClass,
  subtlePanelClass,
} from "~/components/ui";
import { entryInsightRequestedByForm, scheduleEntryInsight } from "~/lib/ai/entry-insights";
import { shouldGenerateEntryInsightForKind } from "~/lib/ai/entry-insights.shared";
import { requireSession } from "~/lib/auth/session";
import { todayISO, yearsBetween } from "~/lib/date";
import { getOnThisDay, listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { createEntry, type EntryWithTags, listEntries } from "~/lib/db/entries";
import {
  entryKindLabel,
  normalizeEntryKind,
  normalizeNoteType,
  noteTypeLabel,
} from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { waitUntilContext } from "~/lib/request-context";
import { loadEntryInsightAutoMode } from "~/lib/settings/ai-settings";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/home";

const MOOD_LABEL: Record<number, string> = {
  1: "低落",
  2: "失落",
  3: "平静",
  4: "轻松",
  5: "明亮",
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "今天 · Sillage" }, { name: "description", content: "Sillage" }];
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function newDefaults(today: string): EntryFormDefaults {
  return {
    entryDate: today,
    title: "",
    body: "",
    mood: null,
    moodText: null,
    weather: null,
    location: null,
    kind: "fragment",
    noteType: "daily",
    people: [],
    relationships: [],
    tags: [],
  };
}

function excerpt(body: string, max = 96): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function splitToday(entries: EntryWithTags[]) {
  const fragments: EntryWithTags[] = [];
  const notes: EntryWithTags[] = [];
  const drafts: EntryWithTags[] = [];

  for (const entry of entries) {
    const kind = normalizeEntryKind(entry.kind);
    if (kind === "note") {
      notes.push(entry);
    } else if (kind === "draft") {
      drafts.push(entry);
    } else {
      fragments.push(entry);
    }
  }

  return { fragments, notes, drafts };
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const today = todayISO();
  const url = new URL(request.url);
  const kind = normalizeEntryKind(url.searchParams.get("kind"));
  const dateParam = url.searchParams.get("date");
  const entryDate = dateParam && DATE_RE.test(dateParam) ? dateParam : today;
  const [recentEntries, todayEntries, onThisDay, entryInsightAutoMode] = await Promise.all([
    listEntries(db, 80),
    listEntriesByDate(db, today),
    getOnThisDay(db, today),
    loadEntryInsightAutoMode(env),
  ]);
  return {
    entries: recentEntries.slice(0, 12),
    suggestions: buildEntryFormSuggestions(recentEntries),
    todayEntries,
    onThisDay,
    today,
    defaults: { ...newDefaults(entryDate), kind },
    entryInsightAutoMode,
  };
}

export async function action({ request, context }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const values = entryFormFromData(form);
  const parsed = entrySchema.safeParse(values);
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? "输入有误", values };
  }

  const db = getDb(env.DB);
  const id = await createEntry(db, parsed.data);
  if (entryInsightRequestedByForm(form)) {
    scheduleEntryInsight(
      env,
      db,
      context?.get(waitUntilContext) ?? ((promise) => void promise),
      id,
    );
  }
  return redirect("/");
}

function EntryMiniList({ entries, empty }: { entries: EntryWithTags[]; empty: string }) {
  if (entries.length === 0) {
    return <p className="text-gray-400 text-sm dark:text-gray-500">{empty}</p>;
  }

  return (
    <ul className="space-y-1">
      {entries.map((entry) => {
        const kind = normalizeEntryKind(entry.kind);
        const noteLabel = noteTypeLabel(normalizeNoteType(entry.noteType, kind));
        return (
          <li key={entry.id}>
            <Link
              to={`/entries/${entry.id}`}
              className="block rounded-lg px-2 py-2 transition hover:bg-gray-100 dark:hover:bg-gray-800/60"
            >
              <div className="flex flex-wrap items-center gap-2 text-gray-400 text-xs dark:text-gray-500">
                <span>{entryKindLabel(kind)}</span>
                {noteLabel ? <span>{noteLabel}</span> : null}
                {entry.mood ? <span>{MOOD_LABEL[entry.mood]}</span> : null}
              </div>
              <p className="mt-1 line-clamp-2 text-gray-700 text-sm dark:text-gray-300">
                {entry.title || excerpt(entry.body, 56) || "未命名记录"}
              </p>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

export default function Home({ loaderData, actionData }: Route.ComponentProps) {
  const { entries, todayEntries, onThisDay, today, defaults } = loaderData;
  const { fragments, notes, drafts } = splitToday(todayEntries);
  const todayInsights = todayEntries.filter((entry) => entry.summary);
  const defaultEntryInsightForKind = (kind: EntryWithTags["kind"]) =>
    shouldGenerateEntryInsightForKind(loaderData.entryInsightAutoMode, kind);

  return (
    <main className={readingShellClass}>
      <section className={pageSectionClass}>
        <header>
          <p className="text-gray-400 text-xs tracking-wide dark:text-gray-500">{today}</p>
          <h1 className={`mt-1.5 ${pageTitleClass}`}>今天留下些什么？</h1>
          <p className={`${pageLeadClass} italic [font-family:Palatino,'Iowan_Old_Style',serif]`}>
            What lingers today?
          </p>
        </header>

        <section className={`${panelClass} rounded-xl p-4 sm:p-5`}>
          <EntryForm
            error={actionData?.error}
            defaults={actionData?.values ?? defaults}
            suggestions={loaderData.suggestions}
            submitLabel="留下"
            showEntryInsightOption
            defaultEntryInsightForKind={defaultEntryInsightForKind}
          />
        </section>

        <section className="grid gap-5 sm:grid-cols-2">
          <div>
            <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">今日片段</h2>
            <div className="mt-2">
              <EntryMiniList entries={fragments} empty="还没有留下片段。" />
            </div>
          </div>

          <div>
            <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">今日笔记</h2>
            <div className="mt-2">
              <EntryMiniList entries={notes} empty="今天还没有被整理。" />
            </div>
          </div>

          {drafts.length > 0 ? (
            <div>
              <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">草稿</h2>
              <div className="mt-2">
                <EntryMiniList entries={drafts} empty="" />
              </div>
            </div>
          ) : null}

          <div>
            <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">今日洞察</h2>
            {todayInsights.length === 0 ? (
              <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">
                写下一些内容后，Sillage 会帮你看见它们之间的线索。
              </p>
            ) : (
              <ul className="mt-2 space-y-2">
                {todayInsights.map((entry) => (
                  <li
                    key={entry.id}
                    className="rounded-lg bg-celadon-50 px-3 py-2 text-sm dark:bg-celadon-900/40"
                  >
                    <p className="text-celadon-800 dark:text-celadon-200">{entry.summary}</p>
                    <Link
                      to={`/entries/${entry.id}`}
                      className="mt-1 inline-block text-celadon-700 text-xs hover:text-celadon-900 dark:text-celadon-200 dark:hover:text-celadon-100"
                    >
                      查看来源
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className={`text-sm tracking-[0.16em] ${serifTitleClass}`}>最近的痕迹</h2>
            <Link
              to="/timeline"
              className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
            >
              查看时间线
            </Link>
          </div>
          {entries.length === 0 && onThisDay.length === 0 ? (
            <div
              className={`${subtlePanelClass} px-4 py-10 text-center text-gray-500 text-sm dark:text-gray-400`}
            >
              还没有留下什么。可以从一个瞬间开始。
            </div>
          ) : (
            <TraceThread>
              {entries.map((entry) => (
                <TraceThreadItem key={entry.id}>
                  <EntryCard entry={entry} openOnCardClick showEntryInsight />
                </TraceThreadItem>
              ))}
              {onThisDay.map((entry) => {
                const years = yearsBetween(entry.entryDate, today);
                return (
                  <TraceThreadItem key={`memory-${entry.id}`} memory>
                    <Link
                      to={`/entries/${entry.id}`}
                      className="block rounded-lg px-3 py-3 transition hover:bg-clay-50 dark:hover:bg-clay-900/40"
                    >
                      <div className="text-clay-600 text-xs dark:text-clay-300">
                        那年今日 · {years}年前 · {entry.entryDate}
                      </div>
                      <h3 className={`mt-1 text-base ${serifTitleClass}`}>
                        {entry.title || excerpt(entry.body, 40) || "未命名记录"}
                      </h3>
                      {entry.body ? (
                        <p className="mt-1 text-gray-500 text-sm leading-6 dark:text-gray-400">
                          {excerpt(entry.body)}
                        </p>
                      ) : null}
                    </Link>
                  </TraceThreadItem>
                );
              })}
            </TraceThread>
          )}
        </section>
      </section>
    </main>
  );
}
