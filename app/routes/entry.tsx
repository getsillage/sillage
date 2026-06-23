import { env } from "cloudflare:workers";
import { Form, Link, redirect } from "react-router";
import { EntryForm } from "~/components/EntryForm";
import { Markdown } from "~/components/Markdown";
import { runAiPipeline } from "~/lib/ai/pipeline";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { deleteEntry, getEntry, updateEntry } from "~/lib/db/entries";
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
  return [{ title: "日记 · 我的日记" }];
}

export async function loader({ request, params }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const entry = await getEntry(db, params.id);
  if (!entry) {
    throw new Response("Not Found", { status: 404 });
  }
  const url = new URL(request.url);
  return { entry, editing: url.searchParams.has("edit") };
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
  const ok = await updateEntry(db, params.id, parsed.data);
  if (!ok) {
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

  if (editing) {
    return (
      <main className="mx-auto max-w-2xl p-6">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-xl font-semibold">编辑日记</h1>
          <Link to={`/entries/${entry.id}`} className="text-sm text-gray-500 hover:text-gray-900">
            取消
          </Link>
        </div>
        <EntryForm
          intent="update"
          submitLabel="更新"
          error={actionData?.error}
          defaults={
            actionData?.values ?? {
              entryDate: entry.entryDate,
              title: entry.title,
              body: entry.body,
              mood: entry.mood,
              weather: entry.weather,
              tags: entry.tags,
            }
          }
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
                if (!confirm("确定删除这篇日记？")) {
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
