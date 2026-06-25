import { env } from "cloudflare:workers";
import { Form, Link, redirect } from "react-router";
import { EntryInsightControl } from "~/components/ai/EntryInsightControl";
import { EntryForm } from "~/components/EntryForm";
import { LazyMarkdown } from "~/components/LazyMarkdown";
import { LocalDateTime } from "~/components/LocalDateTime";
import { pageTitleClass, panelClass, readingShellClass, subtlePanelClass } from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { deleteEntry, getEntry, listEntries, updateEntry } from "~/lib/db/entries";
import { type EntryRevisionView, listEntryRevisions } from "~/lib/db/revisions";
import {
  entryKindLabel,
  normalizeEntryKind,
  normalizeNoteType,
  noteTypeLabel,
  parseTextList,
} from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { formatReadingStats, readingStats } from "~/lib/product/reading-stats";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/entry";

const MOOD_LABEL: Record<number, string> = {
  1: "低落",
  2: "失落",
  3: "平静",
  4: "轻松",
  5: "明亮",
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "记录 · Sillage" }];
}

function revisionFieldSummary(fields: EntryRevisionView["fields"]): string {
  const parts: string[] = [];
  if (fields.entryDate) {
    parts.push(fields.entryDate);
  }
  if (fields.mood) {
    parts.push(`心情 ${fields.mood}`);
  }
  if (fields.location) {
    parts.push(fields.location);
  }
  if (fields.people.length > 0) {
    parts.push(`人物 ${fields.people.join("、")}`);
  }
  if (fields.relationships.length > 0) {
    parts.push(fields.relationships.join("、"));
  }
  if (fields.tags.length > 0) {
    parts.push(fields.tags.map((tag) => `#${tag}`).join(" "));
  }
  return parts.join(" · ");
}

function formDefaults(entry: EntryWithTags) {
  const kind = normalizeEntryKind(entry.kind);
  return {
    entryDate: entry.entryDate,
    title: entry.title,
    body: entry.body,
    mood: entry.mood,
    moodText: entry.moodText,
    weather: entry.weather,
    location: entry.location,
    kind,
    noteType: normalizeNoteType(entry.noteType, kind),
    people: parseTextList(entry.people),
    relationships: parseTextList(entry.relationships),
    tags: entry.tags,
  };
}

export async function loader({ request, params }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const [entry, recentEntries, revisions] = await Promise.all([
    getEntry(db, params.id),
    listEntries(db, 80),
    listEntryRevisions(db, params.id),
  ]);
  if (!entry) {
    throw new Response("Not Found", { status: 404 });
  }
  const url = new URL(request.url);
  return {
    entry,
    editing: url.searchParams.has("edit"),
    suggestions: buildEntryFormSuggestions(recentEntries),
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

function EntryInsightSection({ entry }: { entry: EntryWithTags }) {
  return (
    <section className="border-gray-100 border-t py-4 text-sm dark:border-gray-800">
      <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">AI 洞察</h2>
      <div className="mt-1">
        <EntryInsightControl entry={entry} />
      </div>
    </section>
  );
}

export default function EntryDetail({ loaderData, actionData }: Route.ComponentProps) {
  const { entry, editing, revisions } = loaderData;
  const formActionData = actionData && "error" in actionData ? actionData : undefined;
  const kind = normalizeEntryKind(entry.kind);
  const people = parseTextList(entry.people);
  const relationships = parseTextList(entry.relationships);
  const kindLabel = entryKindLabel(kind);
  const noteLabel = noteTypeLabel(normalizeNoteType(entry.noteType, kind));
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
              suggestions={loaderData.suggestions}
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
              <span>· {kindLabel}</span>
              {noteLabel ? <span>· {noteLabel}</span> : null}
              {entry.weather ? <span>· {entry.weather}</span> : null}
              {entry.mood ? <span>· {MOOD_LABEL[entry.mood]}</span> : null}
            </div>
            {entry.title ? (
              <h1 className="mt-2 max-w-3xl break-words font-serif text-2xl text-gray-900 tracking-normal sm:text-3xl dark:text-gray-50">
                {entry.title}
              </h1>
            ) : null}
          </header>

          <div className="py-6 sm:py-8">
            <LazyMarkdown content={entry.body} />
          </div>
        </article>

        <aside className="space-y-4">
          <section className={`${subtlePanelClass} p-4 text-sm sm:p-5`}>
            <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">记录信息</h2>
            <div className="mt-3 space-y-2 text-gray-600 dark:text-gray-300">
              <p>
                创建于 <LocalDateTime value={entry.createdAt} />
              </p>
              {entry.version > 1 ? (
                <>
                  <p>
                    最近修改 <LocalDateTime value={entry.updatedAt} />
                  </p>
                  <p>共修改 {entry.version - 1} 次</p>
                </>
              ) : null}
              {readingLine ? <p>{readingLine}</p> : null}
            </div>
          </section>

          {entry.moodText || entry.location || people.length > 0 || relationships.length > 0 ? (
            <section className={`${subtlePanelClass} grid gap-3 p-4 text-sm sm:p-5`}>
              {entry.moodText ? (
                <div>
                  <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">细腻感受</h2>
                  <p className="mt-1 text-gray-600 dark:text-gray-300">{entry.moodText}</p>
                </div>
              ) : null}
              {entry.location ? (
                <div>
                  <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">地点</h2>
                  <p className="mt-1 text-gray-600 dark:text-gray-300">{entry.location}</p>
                </div>
              ) : null}
              {people.length > 0 ? (
                <div>
                  <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">人物</h2>
                  <p className="mt-1 text-gray-600 dark:text-gray-300">{people.join("、")}</p>
                </div>
              ) : null}
              {relationships.length > 0 ? (
                <div>
                  <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">关系</h2>
                  <p className="mt-1 text-gray-600 dark:text-gray-300">
                    {relationships.join("、")}
                  </p>
                </div>
              ) : null}
            </section>
          ) : null}

          <section className={`${subtlePanelClass} p-4 sm:p-5`}>
            <EntryInsightSection entry={entry} />
          </section>

          {revisions.length > 1 ? (
            <section className={`${subtlePanelClass} p-4 text-sm sm:p-5`}>
              <h2 className="font-medium text-gray-900 text-xs dark:text-gray-100">
                修改记录 · 共 {revisions.length} 个版本
              </h2>
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
                          <span className="min-w-0 max-w-full truncate text-gray-500 text-xs dark:text-gray-400">
                            {revision.title || "(无标题)"}
                          </span>
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

          {entry.tags.length > 0 ? (
            <footer className={`${subtlePanelClass} flex flex-wrap gap-2 p-4 sm:p-5`}>
              {entry.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-xs dark:bg-gray-800 dark:text-gray-300"
                >
                  #{tag}
                </span>
              ))}
            </footer>
          ) : null}
        </aside>
      </div>
    </main>
  );
}
