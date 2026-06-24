import { env } from "cloudflare:workers";
import { Link } from "react-router";
import { EntryCard } from "~/components/EntryCard";
import {
  pageLeadClass,
  pageSectionClass,
  pageShellClass,
  pageTitleClass,
  primaryButtonClass,
  subtlePanelClass,
} from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { listEntries } from "~/lib/db/entries";
import { normalizeEntryKind } from "~/lib/product/entry-fields";
import type { Route } from "./+types/reflections";

export function meta(_: Route.MetaArgs) {
  return [{ title: "回顾 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const entries = await listEntries(getDb(env.DB), 120);
  return {
    reflections: entries.filter((entry) => normalizeEntryKind(entry.kind) === "reflection"),
  };
}

export default function Reflections({ loaderData }: Route.ComponentProps) {
  const { reflections } = loaderData;

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header className="flex items-end justify-between gap-4">
          <div>
            <h1 className={pageTitleClass}>回顾</h1>
            <p className={pageLeadClass}>认真整理一日、一周、一月，或一个反复出现的主题。</p>
          </div>
          <Link to="/new?kind=reflection" className={primaryButtonClass}>
            写回顾
          </Link>
        </header>

        {reflections.length === 0 ? (
          <div className={`${subtlePanelClass} px-4 py-10 text-center text-sm text-gray-500`}>
            今天还没有被整理。晚些时候回来看看也可以。
          </div>
        ) : (
          <ul className="space-y-3">
            {reflections.map((entry) => (
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
