import { env } from "cloudflare:workers";
import { Link } from "react-router";
import { requireSession } from "~/lib/auth/session";
import { todayISO } from "~/lib/date";
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
  return { entries, onThisDay };
}

function excerpt(body: string, max = 120): string {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

export default function Home({ loaderData }: Route.ComponentProps) {
  const { entries, onThisDay } = loaderData;

  return (
    <main className="mx-auto max-w-2xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="font-semibold text-xl">时间线</h1>
        <Link
          to="/new"
          className="rounded-lg bg-gray-900 px-4 py-2 font-medium text-white text-sm hover:bg-gray-800"
        >
          写日记
        </Link>
      </header>

      {onThisDay.length > 0 ? (
        <section className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <h2 className="font-medium text-amber-900 text-sm">那年今日</h2>
          <ul className="mt-2 space-y-2">
            {onThisDay.map((entry) => (
              <li key={entry.id}>
                <Link
                  to={`/entries/${entry.id}`}
                  className="block text-amber-900 text-sm hover:underline"
                >
                  <span className="text-amber-700">{entry.entryDate}</span> ·{" "}
                  {entry.title || excerpt(entry.body, 40) || "（无标题）"}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {entries.length === 0 ? (
        <p className="mt-16 text-center text-gray-400">
          还没有日记，
          <Link to="/new" className="text-gray-900 underline">
            写下第一篇
          </Link>
          吧。
        </p>
      ) : (
        <ul className="space-y-3">
          {entries.map((entry) => (
            <li key={entry.id}>
              <Link
                to={`/entries/${entry.id}`}
                className="block rounded-xl border border-gray-200 p-4 hover:border-gray-300 hover:bg-gray-50"
              >
                <div className="flex items-center gap-2 text-gray-500 text-xs">
                  <time>{entry.entryDate}</time>
                  {entry.mood ? <span>{MOOD_EMOJI[entry.mood]}</span> : null}
                </div>
                <h2 className="mt-1 font-medium text-gray-900">{entry.title || "（无标题）"}</h2>
                {entry.body ? (
                  <p className="mt-1 text-gray-500 text-sm">{excerpt(entry.body)}</p>
                ) : null}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
