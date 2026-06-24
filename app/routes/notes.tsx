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
import type { Route } from "./+types/notes";

export function meta(_: Route.MetaArgs) {
  return [{ title: "笔记 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const entries = await listEntries(getDb(env.DB), 120);
  return {
    notes: entries.filter((entry) => normalizeEntryKind(entry.kind) === "note"),
  };
}

export default function Notes({ loaderData }: Route.ComponentProps) {
  const { notes } = loaderData;

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header className="flex items-end justify-between gap-4">
          <div>
            <h1 className={pageTitleClass}>笔记</h1>
            <p className={pageLeadClass}>认真整理一日、一周、一月，或一个反复出现的主题。</p>
          </div>
          <Link to="/new?kind=note" className={primaryButtonClass}>
            写笔记
          </Link>
        </header>

        {notes.length === 0 ? (
          <div className={`${subtlePanelClass} px-4 py-10 text-center text-sm text-gray-500`}>
            今天还没有被整理。晚些时候回来看看也可以。
          </div>
        ) : (
          <ul className="space-y-3">
            {notes.map((entry) => (
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
