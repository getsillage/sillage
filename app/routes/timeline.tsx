import { env } from "cloudflare:workers";
import { EntryCard } from "~/components/EntryCard";
import {
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  subtlePanelClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { listEntries } from "~/lib/db/entries";
import type { Route } from "./+types/timeline";

export function meta(_: Route.MetaArgs) {
  return [{ title: "时间线 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  return { entries: await listEntries(getDb(env.DB), 80) };
}

export default function Timeline({ loaderData }: Route.ComponentProps) {
  const { entries } = loaderData;

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>时间线</h1>
          <p className={pageLeadClass}>片段和回顾按时间混排，保留生活流本来的形状。</p>
        </header>

        {entries.length === 0 ? (
          <div className={`${subtlePanelClass} px-4 py-10 text-center text-sm text-gray-500`}>
            还没有留下什么。可以从一个瞬间开始。
          </div>
        ) : (
          <ul className="space-y-3">
            {entries.map((entry) => (
              <li key={entry.id}>
                <EntryCard entry={entry} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
