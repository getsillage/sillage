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
import { requireSession } from "~/lib/auth/session";
import { todayISO, yearsBetween } from "~/lib/date";
import { getOnThisDay, listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { createEntry, type EntryWithAi, listEntries } from "~/lib/db/entries";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/home";

export function meta(_: Route.MetaArgs) {
  return [{ title: "今天 · Sillage" }, { name: "description", content: "Sillage" }];
}

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function newDefaults(today: string): EntryFormDefaults {
  return {
    entryDate: today,
    body: "",
  };
}

function excerpt(body: string, max = 96): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const today = todayISO();
  const url = new URL(request.url);
  const dateParam = url.searchParams.get("date");
  const entryDate = dateParam && DATE_RE.test(dateParam) ? dateParam : today;
  const [recentEntries, todayEntries, onThisDay] = await Promise.all([
    listEntries(db, 80),
    listEntriesByDate(db, today),
    getOnThisDay(db, today),
  ]);
  return {
    entries: recentEntries.slice(0, 12),
    todayEntries,
    onThisDay,
    today,
    defaults: newDefaults(entryDate),
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

function EntryMiniList({ entries, empty }: { entries: EntryWithAi[]; empty: string }) {
  if (entries.length === 0) {
    return <p className="text-gray-400 text-sm dark:text-gray-500">{empty}</p>;
  }

  return (
    <ul className="space-y-1">
      {entries.map((entry) => (
        <li key={entry.id}>
          <Link
            to={`/entries/${entry.id}`}
            className="block rounded-lg px-2 py-2 transition hover:bg-gray-100 dark:hover:bg-gray-800/60"
          >
            <p className="line-clamp-2 text-gray-700 text-sm dark:text-gray-300">
              {excerpt(entry.body, 72) || "空白记录"}
            </p>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export default function Home({ loaderData, actionData }: Route.ComponentProps) {
  const { entries, todayEntries, onThisDay, today, defaults } = loaderData;

  return (
    <main className={readingShellClass}>
      <section className={pageSectionClass}>
        <header>
          <p className="text-gray-400 text-xs tracking-wide dark:text-gray-500">{today}</p>
          <h1 className={`mt-1.5 ${pageTitleClass}`}>今天想记录什么？</h1>
          <p className={pageLeadClass}>写下今天发生的事、想法或感受。</p>
        </header>

        <section className={`${panelClass} rounded-xl p-4 sm:p-5`}>
          <EntryForm
            error={actionData?.error}
            defaults={actionData?.values ?? defaults}
            submitLabel="保存"
          />
        </section>

        <section>
          <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">今天</h2>
          <div className="mt-2">
            <EntryMiniList entries={todayEntries} empty="今天还没有记录。" />
          </div>
        </section>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className={`text-sm tracking-[0.16em] ${serifTitleClass}`}>最近的历史</h2>
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
              还没有记录。可以先写一条记录。
            </div>
          ) : (
            <TraceThread>
              {entries.map((entry) => (
                <TraceThreadItem key={entry.id}>
                  <EntryCard entry={entry} openOnCardClick />
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
                        {excerpt(entry.body, 40) || "空白记录"}
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
