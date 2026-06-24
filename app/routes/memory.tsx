import { env } from "cloudflare:workers";
import { Form, Link } from "react-router";
import { EntryCard } from "~/components/EntryCard";
import {
  inputClass,
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
import { parseTextList } from "~/lib/product/entry-fields";
import { searchEntriesByKeyword } from "~/lib/search/fts";
import type { Route } from "./+types/memory";

export function meta(_: Route.MetaArgs) {
  return [{ title: "记忆 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const url = new URL(request.url);
  const query = url.searchParams.get("q")?.trim() ?? "";
  const db = getDb(env.DB);
  const [recentEntries, results] = await Promise.all([
    listEntries(db, 80),
    query ? searchEntriesByKeyword(db, query) : Promise.resolve([]),
  ]);

  const people = new Map<string, number>();
  const relationships = new Map<string, number>();
  for (const entry of recentEntries) {
    for (const person of parseTextList(entry.people)) {
      people.set(person, (people.get(person) ?? 0) + 1);
    }
    for (const relationship of parseTextList(entry.relationships)) {
      relationships.set(relationship, (relationships.get(relationship) ?? 0) + 1);
    }
  }

  return {
    query,
    results,
    people: [...people.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12),
    relationships: [...relationships.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12),
  };
}

export default function Memory({ loaderData }: Route.ComponentProps) {
  const { query, results, people, relationships } = loaderData;

  return (
    <main className={pageShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>记忆</h1>
          <p className={pageLeadClass}>搜索、问问记忆，或从人物与关系重新进入过去。</p>
        </header>

        <Form method="get" className="flex gap-2">
          <input
            type="search"
            name="q"
            defaultValue={query}
            placeholder="问问你的记忆，或搜索一个词…"
            className={`${inputClass} mt-0 min-w-0 flex-1`}
          />
          <button type="submit" className={primaryButtonClass}>
            搜索
          </button>
        </Form>

        {query ? (
          <section>
            <h2 className="mb-3 font-medium text-gray-950 text-sm">搜索结果</h2>
            {results.length === 0 ? (
              <p className="text-gray-400 text-sm">没有找到相关记忆。换一个词，或者问问回声。</p>
            ) : (
              <ul className="space-y-3">
                {results.map((entry) => (
                  <li key={entry.id}>
                    <EntryCard entry={entry} />
                  </li>
                ))}
              </ul>
            )}
          </section>
        ) : null}

        <section className="grid gap-4 sm:grid-cols-2">
          <div className={`${subtlePanelClass} p-4`}>
            <h2 className="font-medium text-gray-950 text-sm">人物</h2>
            {people.length === 0 ? (
              <p className="mt-3 text-gray-400 text-sm">记录人物后，这里会出现关系线索。</p>
            ) : (
              <div className="mt-3 flex flex-wrap gap-2">
                {people.map(([person, count]) => (
                  <Link
                    key={person}
                    to={`/memory?q=${encodeURIComponent(person)}`}
                    className="rounded-full bg-white px-3 py-1 text-gray-600 text-sm hover:text-gray-950"
                  >
                    {person} · {count}
                  </Link>
                ))}
              </div>
            )}
          </div>

          <div className={`${subtlePanelClass} p-4`}>
            <h2 className="font-medium text-gray-950 text-sm">关系</h2>
            {relationships.length === 0 ? (
              <p className="mt-3 text-gray-400 text-sm">记录关系后，这里会帮助你回看变化。</p>
            ) : (
              <div className="mt-3 flex flex-wrap gap-2">
                {relationships.map(([relationship, count]) => (
                  <Link
                    key={relationship}
                    to={`/memory?q=${encodeURIComponent(relationship)}`}
                    className="rounded-full bg-white px-3 py-1 text-gray-600 text-sm hover:text-gray-950"
                  >
                    {relationship} · {count}
                  </Link>
                ))}
              </div>
            )}
          </div>
        </section>
      </section>
    </main>
  );
}
