import { env } from "cloudflare:workers";
import { Link, redirect } from "react-router";
import { EntryForm, type EntryFormDefaults } from "~/components/EntryForm";
import {
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  panelClass,
  rowLinkClass,
  subtlePanelClass,
} from "~/components/ui";
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
  const [recentEntries, todayEntries, onThisDay] = await Promise.all([
    listEntries(db, 80),
    listEntriesByDate(db, today),
    getOnThisDay(db, today),
  ]);
  return {
    entries: recentEntries.slice(0, 12),
    suggestions: buildEntryFormSuggestions(recentEntries),
    todayEntries,
    onThisDay,
    today,
  };
}

export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const values = entryFormFromData(form);
  const parsed = entrySchema.safeParse(values);
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? "输入有误", values };
  }

  const db = getDb(env.DB);
  await createEntry(db, parsed.data);
  return redirect("/");
}

function EntryMiniList({ entries, empty }: { entries: EntryWithTags[]; empty: string }) {
  if (entries.length === 0) {
    return <p className="text-gray-400 text-sm dark:text-gray-500">{empty}</p>;
  }

  return (
    <ul className="space-y-2">
      {entries.map((entry) => {
        const kind = normalizeEntryKind(entry.kind);
        const noteLabel = noteTypeLabel(normalizeNoteType(entry.noteType, kind));
        return (
          <li key={entry.id}>
            <Link
              to={`/entries/${entry.id}`}
              className="block rounded-lg border border-gray-200 bg-white px-3 py-2 transition hover:border-gray-300 hover:bg-gray-50 dark:border-gray-800 dark:bg-gray-950 dark:hover:border-gray-700 dark:hover:bg-gray-900"
            >
              <div className="flex flex-wrap items-center gap-2 text-gray-500 text-xs dark:text-gray-400">
                <span>{entryKindLabel(kind)}</span>
                {noteLabel ? <span>{noteLabel}</span> : null}
                {entry.mood ? <span>{MOOD_LABEL[entry.mood]}</span> : null}
              </div>
              <p className="mt-1 line-clamp-2 text-gray-800 text-sm dark:text-gray-200">
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
  const { entries, todayEntries, onThisDay, today } = loaderData;
  const { fragments, notes, drafts } = splitToday(todayEntries);
  const todayInsights = todayEntries.filter((entry) => entry.summary);

  return (
    <main className={`${pageShellClass} max-w-6xl`}>
      <section className={pageSectionClass}>
        <header>
          <p className="text-gray-500 text-sm dark:text-gray-400">{today}</p>
          <h1 className={pageTitleClass}>今天留下些什么？</h1>
          <p className={pageLeadClass}>What lingers today?</p>
        </header>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <section className={`${panelClass} p-5`}>
            <EntryForm
              error={actionData?.error}
              defaults={actionData?.values ?? newDefaults(today)}
              suggestions={loaderData.suggestions}
              submitLabel="保存"
            />
          </section>

          <aside className="space-y-4">
            <section className={`${panelClass} p-4`}>
              <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">今日片段</h2>
              <div className="mt-3">
                <EntryMiniList entries={fragments} empty="还没有留下片段。" />
              </div>
            </section>

            <section className={`${panelClass} p-4`}>
              <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">今日笔记</h2>
              <div className="mt-3">
                <EntryMiniList entries={notes} empty="今天还没有被整理。" />
              </div>
            </section>

            {drafts.length > 0 ? (
              <section className={`${panelClass} p-4`}>
                <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">草稿</h2>
                <div className="mt-3">
                  <EntryMiniList entries={drafts} empty="" />
                </div>
              </section>
            ) : null}

            <section className={`${panelClass} p-4`}>
              <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">今日洞察</h2>
              {todayInsights.length === 0 ? (
                <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">
                  写下一些内容后，Sillage 会帮你看见它们之间的线索。
                </p>
              ) : (
                <ul className="mt-3 space-y-2">
                  {todayInsights.map((entry) => (
                    <li
                      key={entry.id}
                      className="rounded-lg bg-gray-50 px-3 py-2 text-sm dark:bg-gray-950"
                    >
                      <p className="text-gray-700 dark:text-gray-300">{entry.summary}</p>
                      <Link
                        to={`/entries/${entry.id}`}
                        className="mt-1 inline-block text-gray-400 text-xs hover:text-gray-900 dark:hover:text-gray-100"
                      >
                        查看来源
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {onThisDay.length > 0 ? (
              <section className="rounded-lg border border-amber-200 bg-amber-50/70 p-4 dark:border-amber-900/60 dark:bg-amber-950/20">
                <h2 className="font-medium text-amber-950 text-sm dark:text-amber-100">那年今日</h2>
                <ul className="mt-3 space-y-2">
                  {onThisDay.map((entry) => {
                    const years = yearsBetween(entry.entryDate, today);
                    return (
                      <li key={entry.id}>
                        <Link
                          to={`/entries/${entry.id}`}
                          className="block rounded-lg border border-amber-200 bg-white/70 px-3 py-2 text-amber-950 text-sm transition hover:border-amber-300 hover:bg-white dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-100 dark:hover:border-amber-800 dark:hover:bg-amber-950/50"
                        >
                          <span className="font-medium text-amber-800 dark:text-amber-200">
                            {years}年前
                          </span>
                          <span className="text-amber-700 dark:text-amber-300">
                            {" "}
                            · {entry.entryDate}
                          </span>
                          <span> · {entry.title || excerpt(entry.body, 40) || "未命名记录"}</span>
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </section>
            ) : null}
          </aside>
        </div>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">最近记录</h2>
            <Link
              to="/timeline"
              className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
            >
              查看时间线
            </Link>
          </div>
          {entries.length === 0 ? (
            <div
              className={`${subtlePanelClass} px-4 py-10 text-center text-gray-500 text-sm dark:text-gray-400`}
            >
              还没有留下什么。可以从一个瞬间开始。
            </div>
          ) : (
            <ul className="space-y-3">
              {entries.map((entry) => {
                const kind = normalizeEntryKind(entry.kind);
                return (
                  <li key={entry.id}>
                    <Link to={`/entries/${entry.id}`} className={rowLinkClass}>
                      <div className="flex flex-wrap items-center gap-2 text-gray-500 text-xs dark:text-gray-400">
                        <time>{entry.entryDate}</time>
                        <span>{entryKindLabel(kind)}</span>
                        {entry.mood ? <span>{MOOD_LABEL[entry.mood]}</span> : null}
                      </div>
                      <h3 className="mt-1 font-medium text-gray-950 dark:text-gray-50">
                        {entry.title || "未命名记录"}
                      </h3>
                      {entry.body ? (
                        <p className="mt-1 text-gray-500 text-sm leading-6 dark:text-gray-400">
                          {excerpt(entry.body)}
                        </p>
                      ) : null}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </section>
    </main>
  );
}
