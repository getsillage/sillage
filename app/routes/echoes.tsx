import { env } from "cloudflare:workers";
import { Link } from "react-router";
import {
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  panelClass,
  subtlePanelClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { todayISO } from "~/lib/date";
import { listEntriesByDate } from "~/lib/db/calendar";
import { getDb } from "~/lib/db/client";
import { listEntries } from "~/lib/db/entries";
import { normalizeEntryKind } from "~/lib/product/entry-fields";
import type { Route } from "./+types/echoes";

export function meta(_: Route.MetaArgs) {
  return [{ title: "回声 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const today = todayISO();
  const [todayEntries, recentEntries] = await Promise.all([
    listEntriesByDate(db, today),
    listEntries(db, 80),
  ]);
  return {
    today,
    todayEchoes: todayEntries.filter((entry) => entry.summary),
    recentEchoes: recentEntries.filter((entry) => entry.summary).slice(0, 12),
    themes: recentEntries
      .flatMap((entry) => entry.tags)
      .reduce<Record<string, number>>((acc, tag) => {
        acc[tag] = (acc[tag] ?? 0) + 1;
        return acc;
      }, {}),
    reflectionCount: recentEntries.filter(
      (entry) => normalizeEntryKind(entry.kind) === "reflection",
    ).length,
  };
}

export default function Echoes({ loaderData }: Route.ComponentProps) {
  const { todayEchoes, recentEchoes, themes, reflectionCount } = loaderData;
  const topThemes = Object.entries(themes)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>回声</h1>
          <p className={pageLeadClass}>短摘要优先，线索和来源随后展开。</p>
        </header>

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm">今日余韵</h2>
          {todayEchoes.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm">
              写下一些内容后，Sillage 会帮你听见它们之间的回声。
            </p>
          ) : (
            <ul className="mt-3 space-y-3">
              {todayEchoes.map((entry) => (
                <li key={entry.id} className="rounded-lg bg-gray-50 px-3 py-2">
                  <p className="text-gray-700 text-sm">{entry.summary}</p>
                  <Link
                    to={`/entries/${entry.id}`}
                    className="mt-2 inline-block text-gray-400 text-xs hover:text-gray-900"
                  >
                    查看来源
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm">最近回声</h2>
          {recentEchoes.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm">还没有可展示的回声。</p>
          ) : (
            <ul className="mt-3 space-y-3">
              {recentEchoes.map((entry) => (
                <li key={entry.id} className="rounded-lg border border-gray-200 bg-white px-3 py-2">
                  <p className="text-gray-700 text-sm">{entry.summary}</p>
                  <div className="mt-2 flex items-center justify-between text-xs">
                    <span className="text-gray-400">基于 1 条记录生成</span>
                    <Link to={`/entries/${entry.id}`} className="text-gray-500 hover:text-gray-900">
                      查看来源
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className={`${panelClass} p-4`}>
          <h2 className="font-medium text-gray-950 text-sm">萦绕主题</h2>
          {topThemes.length === 0 ? (
            <p className="mt-3 text-gray-400 text-sm">更多记录之后，这里会浮现反复出现的主题。</p>
          ) : (
            <div className="mt-3 flex flex-wrap gap-2">
              {topThemes.map(([tag, count]) => (
                <span
                  key={tag}
                  className="rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-sm"
                >
                  #{tag} · {count}
                </span>
              ))}
            </div>
          )}
          <p className="mt-3 text-gray-400 text-xs">已整理 {reflectionCount} 篇回顾。</p>
        </section>

        <div className={`${subtlePanelClass} px-4 py-3 text-gray-500 text-sm`}>
          记忆问答已独立放在“记忆”入口，回声页只保留主动浮现的余韵和主题。
        </div>
      </section>
    </main>
  );
}
