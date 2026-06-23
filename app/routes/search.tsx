import { env } from "cloudflare:workers";
import { Form, Link } from "react-router";
import { getDb } from "~/lib/db/client";
import { type SearchResult, searchEntriesByKeyword } from "~/lib/search/fts";
import { mergeSearchResults } from "~/lib/search/hybrid";
import type { Route } from "./+types/search";

export function meta(_: Route.MetaArgs) {
  return [{ title: "搜索 · 我的日记" }];
}

function excerpt(body: string, query: string, max = 140): string {
  const normalized = body.replace(/\s+/g, " ").trim();
  const index = query ? normalized.indexOf(query) : -1;
  const start = index > 20 ? index - 20 : 0;
  const slice = normalized.slice(start, start + max);
  return `${start > 0 ? "…" : ""}${slice}${start + max < normalized.length ? "…" : ""}`;
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const query = url.searchParams.get("q")?.trim() ?? "";
  if (!query) {
    return { query, results: [], semanticEnabled: false };
  }

  const db = getDb(env.DB);
  const keywordResults = await searchEntriesByKeyword(db, query);
  // Vectorize local dev is unavailable; M8 will fill this with embedding-backed
  // semantic results when running against a remote Vectorize index.
  const semanticResults: SearchResult[] = [];
  return {
    query,
    results: mergeSearchResults(keywordResults, semanticResults),
    semanticEnabled: semanticResults.length > 0,
  };
}

export default function Search({ loaderData }: Route.ComponentProps) {
  const { query, results, semanticEnabled } = loaderData;

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="mb-4 font-semibold text-xl">搜索</h1>
      <Form method="get" className="flex gap-2">
        <input
          type="search"
          name="q"
          defaultValue={query}
          placeholder="搜索日记内容…"
          className="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          className="rounded-lg bg-gray-900 px-4 py-2 font-medium text-sm text-white hover:bg-gray-800"
        >
          搜索
        </button>
      </Form>

      {query ? (
        <p className="mt-3 text-gray-500 text-sm">
          {semanticEnabled
            ? "关键词 + 语义混合搜索"
            : "当前为关键词搜索；语义搜索将在 AI 流水线启用后自动加入。"}
        </p>
      ) : null}

      <section className="mt-6">
        {query && results.length === 0 ? (
          <p className="text-gray-400 text-sm">没有找到相关日记。</p>
        ) : null}

        <ul className="space-y-3">
          {results.map((entry) => (
            <li key={entry.id}>
              <Link
                to={`/entries/${entry.id}`}
                className="block rounded-xl border border-gray-200 bg-white p-4 hover:border-gray-300 hover:bg-gray-50"
              >
                <div className="flex items-center gap-2 text-gray-500 text-xs">
                  <time>{entry.entryDate}</time>
                  <span>{entry.source === "semantic" ? "语义" : "关键词"}</span>
                </div>
                <h2 className="mt-1 font-medium text-gray-900">{entry.title || "（无标题）"}</h2>
                {entry.body ? (
                  <p className="mt-1 text-gray-500 text-sm">{excerpt(entry.body, query)}</p>
                ) : null}
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
