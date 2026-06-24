import { env } from "cloudflare:workers";
import { Link } from "react-router";
import {
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  panelClass,
  primaryButtonClass,
  rowLinkClass,
  subtlePanelClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { todayISO, yearsBetween } from "~/lib/date";
import { getOnThisDay } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { listEntries } from "~/lib/db/entries";
import type { Route } from "./+types/home";

const MOOD_EMOJI: Record<number, string> = {
  1: "😞",
  2: "😕",
  3: "😐",
  4: "🙂",
  5: "😄",
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "我的日记" }, { name: "description", content: "个人日记" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const today = todayISO();
  const [entries, onThisDay] = await Promise.all([listEntries(db), getOnThisDay(db, today)]);
  return { entries, onThisDay, today };
}

function excerpt(body: string, max = 120): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

export default function Home({ loaderData }: Route.ComponentProps) {
  const { entries, onThisDay, today } = loaderData;

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header className="flex items-end justify-between gap-4">
          <div>
            <h1 className={pageTitleClass}>时间线</h1>
            <p className={pageLeadClass}>按时间顺序查看最近记录，点开即可继续编辑或回看。</p>
          </div>
          <Link to="/new" className={primaryButtonClass}>
            写日记
          </Link>
        </header>

        {onThisDay.length > 0 ? (
          <section className={`${panelClass} border-amber-200 bg-amber-50/70 p-4`}>
            <h2 className="font-medium text-amber-950 text-sm">那年今日</h2>
            <ul className="mt-3 space-y-2">
              {onThisDay.map((entry) => {
                const years = yearsBetween(entry.entryDate, today);
                return (
                  <li key={entry.id}>
                    <Link
                      to={`/entries/${entry.id}`}
                      className="block rounded-lg border border-amber-200 bg-white/70 px-3 py-2 text-sm text-amber-950 transition hover:border-amber-300 hover:bg-white"
                    >
                      <span className="font-medium text-amber-800">{years}年前</span>
                      <span className="text-amber-700"> · {entry.entryDate}</span>
                      {entry.mood ? <span> {MOOD_EMOJI[entry.mood]}</span> : null} ·{" "}
                      {entry.title || excerpt(entry.body, 40) || "（无标题）"}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </section>
        ) : null}

        {entries.length === 0 ? (
          <div className={`${subtlePanelClass} px-4 py-10 text-center text-sm text-gray-500`}>
            还没有日记，先写下第一篇吧。
          </div>
        ) : (
          <ul className="space-y-3">
            {entries.map((entry) => (
              <li key={entry.id}>
                <Link to={`/entries/${entry.id}`} className={rowLinkClass}>
                  <div className="flex items-center gap-2 text-xs text-gray-500">
                    <time>{entry.entryDate}</time>
                    {entry.mood ? <span>{MOOD_EMOJI[entry.mood]}</span> : null}
                  </div>
                  <h2 className="mt-1 font-medium text-gray-950">{entry.title || "（无标题）"}</h2>
                  {entry.body ? (
                    <p className="mt-1 text-sm leading-6 text-gray-500">{excerpt(entry.body)}</p>
                  ) : null}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
