import { env } from "cloudflare:workers";
import { Form, Link, redirect } from "react-router";
import { EntryForm } from "~/components/EntryForm";
import { LazyMarkdown } from "~/components/LazyMarkdown";
import { LocalDateTime } from "~/components/LocalDateTime";
import { pageTitleClass, panelClass, readingShellClass } from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { deleteEntry, getEntry, updateEntry } from "~/lib/db/entries";
import { type EntryRevisionView, listEntryRevisions } from "~/lib/db/revisions";
import { normalizeEntryKind, normalizeNoteType } from "~/lib/product/entry-fields";
import { formatReadingStats, readingStats } from "~/lib/product/reading-stats";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/entry";

export function meta(_: Route.MetaArgs) {
  return [{ title: "记录 · Sillage" }];
}

function revisionFieldSummary(fields: EntryRevisionView["fields"]): string {
  const parts: string[] = [];
  if (fields.entryDate) {
    parts.push(fields.entryDate);
  }
  return parts.join(" · ");
}

function formDefaults(entry: EntryWithTags) {
  const kind = normalizeEntryKind(entry.kind);
  return {
    entryDate: entry.entryDate,
    body: entry.body,
    kind,
    noteType: normalizeNoteType(entry.noteType, kind),
  };
}

export async function loader({ request, params }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const [entry, revisions] = await Promise.all([
    getEntry(db, params.id),
    listEntryRevisions(db, params.id),
  ]);
  if (!entry) {
    throw new Response("Not Found", { status: 404 });
  }
  const url = new URL(request.url);
  return {
    entry,
    editing: url.searchParams.has("edit"),
    revisions,
  };
}

export async function action({ request, params }: Route.ActionArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const form = await request.formData();
  const intent = String(form.get("intent") ?? "");

  if (intent === "delete") {
    await deleteEntry(db, params.id);
    return redirect("/");
  }

  const values = entryFormFromData(form);
  const parsed = entrySchema.safeParse(values);
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? "输入有误", values };
  }

  const result = await updateEntry(db, params.id, parsed.data);
  if (result.status === "missing") {
    throw new Response("Not Found", { status: 404 });
  }
  return redirect(`/entries/${params.id}`);
}

export default function EntryDetail({ loaderData, actionData }: Route.ComponentProps) {
  const { entry, editing, revisions } = loaderData;
  const formActionData = actionData && "error" in actionData ? actionData : undefined;
  const readingLine = formatReadingStats(readingStats(entry.body));

  if (editing) {
    return (
      <main className={readingShellClass}>
        <section>
          <div className="mb-6 flex items-center justify-between gap-3">
            <h1 className={pageTitleClass}>编辑记录</h1>
            <Link
              to={`/entries/${entry.id}`}
              className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
            >
              取消
            </Link>
          </div>
          <div className={`${panelClass} p-5 sm:p-6 lg:p-8`}>
            <EntryForm
              intent="update"
              submitLabel="更新"
              error={formActionData?.error}
              defaults={formActionData?.values ?? formDefaults(entry)}
            />
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className={readingShellClass}>
      <div className="mb-5 flex items-center justify-between gap-3 text-sm sm:mb-6">
        <Link
          to="/"
          className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          ← 返回
        </Link>
        <div className="flex shrink-0 items-center gap-3">
          <Link
            to={`/entries/${entry.id}?edit`}
            className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
          >
            编辑
          </Link>
          <Form method="post">
            <input type="hidden" name="intent" value="delete" />
            <button
              type="submit"
              className="text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
              onClick={(event) => {
                if (!confirm("确定删除这条记录？")) {
                  event.preventDefault();
                }
              }}
            >
              删除
            </button>
          </Form>
        </div>
      </div>

      <div className="space-y-6">
        <article className="min-w-0">
          <header className="border-gray-200 border-b pb-5 dark:border-gray-800">
            <div className="flex flex-wrap items-center gap-2 text-gray-500 text-sm dark:text-gray-400">
              <time>{entry.entryDate}</time>
            </div>
          </header>

          <div className="py-6 sm:py-8">
            <LazyMarkdown content={entry.body} />
          </div>
        </article>

        <aside className="space-y-5">
          <section className="border-gray-200 border-t pt-4 text-gray-500 text-sm dark:border-gray-800 dark:text-gray-400">
            <p>
              创建于 <LocalDateTime value={entry.createdAt} />
            </p>
            {entry.version > 1 ? (
              <p className="mt-1">
                最近修改 <LocalDateTime value={entry.updatedAt} />
                ，共修改 {entry.version - 1} 次
              </p>
            ) : null}
            {readingLine ? <p className="mt-1">{readingLine}</p> : null}
          </section>

          {revisions.length > 1 ? (
            <section className="border-gray-200 border-t pt-4 text-sm dark:border-gray-800">
              <h2 className="font-medium text-gray-900 text-sm dark:text-gray-100">修改记录</h2>
              <ol className="mt-3 space-y-2">
                {revisions.map((revision, index) => {
                  const summary = revisionFieldSummary(revision.fields);
                  return (
                    <li key={revision.id}>
                      <details className="rounded-lg border border-gray-200 px-3 py-2 dark:border-gray-800">
                        <summary className="flex cursor-pointer flex-wrap items-center gap-2 text-gray-600 dark:text-gray-300">
                          <span className="font-medium text-gray-900 dark:text-gray-100">
                            {index === 0 ? "当前版本" : `第 ${revision.version} 版`}
                          </span>
                          <LocalDateTime
                            value={revision.createdAt}
                            className="text-gray-400 text-xs dark:text-gray-500"
                          />
                        </summary>
                        <div className="mt-2 border-gray-100 border-t pt-2 dark:border-gray-800">
                          {revision.body ? (
                            <LazyMarkdown content={revision.body} />
                          ) : (
                            <p className="text-gray-400 text-xs dark:text-gray-500">(无正文)</p>
                          )}
                          {summary ? (
                            <p className="mt-2 text-gray-400 text-xs dark:text-gray-500">
                              {summary}
                            </p>
                          ) : null}
                        </div>
                      </details>
                    </li>
                  );
                })}
              </ol>
            </section>
          ) : null}
        </aside>
      </div>
    </main>
  );
}
