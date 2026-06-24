import { env } from "cloudflare:workers";
import { Form, Link, redirect } from "react-router";
import { EntryForm } from "~/components/EntryForm";
import { Markdown } from "~/components/Markdown";
import { runAiPipeline } from "~/lib/ai/pipeline";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import type { EntryWithTags } from "~/lib/db/entries";
import { deleteEntry, getEntry, listEntries, updateEntry } from "~/lib/db/entries";
import {
  entryKindLabel,
  normalizeEntryKind,
  normalizeNoteType,
  noteTypeLabel,
  parseTextList,
} from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { waitUntilContext } from "~/lib/request-context";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/entry";

const MOOD_EMOJI: Record<number, string> = {
  1: "😞",
  2: "😕",
  3: "😐",
  4: "🙂",
  5: "😄",
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "记录 · Sillage" }];
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
  const [entry, recentEntries] = await Promise.all([getEntry(db, params.id), listEntries(db, 80)]);
  if (!entry) {
    throw new Response("Not Found", { status: 404 });
  }
  const url = new URL(request.url);
  return {
    entry,
    editing: url.searchParams.has("edit"),
    suggestions: buildEntryFormSuggestions(recentEntries),
  };
}

export async function action({ request, params, context }: Route.ActionArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const form = await request.formData();

  if (form.get("intent") === "delete") {
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
  const entry = await getEntry(db, params.id);
  if (entry) {
    context.get(waitUntilContext)(runAiPipeline(env, entry));
  }
  return redirect(`/entries/${params.id}`);
}

export default function EntryDetail({ loaderData, actionData }: Route.ComponentProps) {
  const { entry, editing } = loaderData;
  const kind = normalizeEntryKind(entry.kind);
  const people = parseTextList(entry.people);
  const relationships = parseTextList(entry.relationships);
  const kindLabel = entryKindLabel(kind);
  const noteLabel = noteTypeLabel(normalizeNoteType(entry.noteType, kind));

  if (editing) {
    return (
      <main className="mx-auto max-w-2xl p-6">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-xl font-semibold">编辑记录</h1>
          <Link to={`/entries/${entry.id}`} className="text-sm text-gray-500 hover:text-gray-900">
            取消
          </Link>
        </div>
        <EntryForm
          intent="update"
          submitLabel="更新"
          error={actionData?.error}
          defaults={actionData?.values ?? formDefaults(entry)}
          suggestions={loaderData.suggestions}
        />
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl p-6">
      <div className="mb-4 flex items-center justify-between text-sm">
        <Link to="/" className="text-gray-500 hover:text-gray-900">
          ← 返回
        </Link>
        <div className="flex items-center gap-3">
          <Link to={`/entries/${entry.id}?edit`} className="text-gray-500 hover:text-gray-900">
            编辑
          </Link>
          <Form method="post">
            <input type="hidden" name="intent" value="delete" />
            <button
              type="submit"
              className="text-red-600 hover:text-red-700"
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

      <header className="border-gray-100 border-b pb-4">
        <div className="flex items-center gap-2 text-gray-500 text-sm">
          <time>{entry.entryDate}</time>
          <span>· {kindLabel}</span>
          {noteLabel ? <span>· {noteLabel}</span> : null}
          {entry.weather ? <span>· {entry.weather}</span> : null}
          {entry.mood ? <span>· {MOOD_EMOJI[entry.mood]}</span> : null}
        </div>
        {entry.title ? (
          <h1 className="mt-2 font-semibold text-2xl text-gray-900">{entry.title}</h1>
        ) : null}
      </header>

      <article className="py-6">
        <Markdown content={entry.body} />
      </article>

      {entry.moodText || entry.location || people.length > 0 || relationships.length > 0 ? (
        <section className="grid gap-3 border-gray-100 border-t py-4 text-sm sm:grid-cols-2">
          {entry.moodText ? (
            <div>
              <h2 className="font-medium text-gray-900 text-xs">细腻感受</h2>
              <p className="mt-1 text-gray-600">{entry.moodText}</p>
            </div>
          ) : null}
          {entry.location ? (
            <div>
              <h2 className="font-medium text-gray-900 text-xs">地点</h2>
              <p className="mt-1 text-gray-600">{entry.location}</p>
            </div>
          ) : null}
          {people.length > 0 ? (
            <div>
              <h2 className="font-medium text-gray-900 text-xs">人物</h2>
              <p className="mt-1 text-gray-600">{people.join("、")}</p>
            </div>
          ) : null}
          {relationships.length > 0 ? (
            <div>
              <h2 className="font-medium text-gray-900 text-xs">关系</h2>
              <p className="mt-1 text-gray-600">{relationships.join("、")}</p>
            </div>
          ) : null}
        </section>
      ) : null}

      {entry.summary ? (
        <section className="border-gray-100 border-t py-4 text-sm">
          <h2 className="font-medium text-gray-900 text-xs">洞察</h2>
          <p className="mt-1 text-gray-600">{entry.summary}</p>
        </section>
      ) : null}

      {entry.tags.length > 0 ? (
        <footer className="flex flex-wrap gap-2 border-gray-100 border-t pt-4">
          {entry.tags.map((tag) => (
            <span key={tag} className="rounded-full bg-gray-100 px-3 py-1 text-gray-600 text-xs">
              #{tag}
            </span>
          ))}
        </footer>
      ) : null}
    </main>
  );
}
