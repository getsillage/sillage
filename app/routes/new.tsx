import { env } from "cloudflare:workers";
import { Link, redirect } from "react-router";
import { EntryForm, type EntryFormDefaults } from "~/components/EntryForm";
import { runAiPipeline } from "~/lib/ai/pipeline";
import { requireSession } from "~/lib/auth/session";
import { todayISO } from "~/lib/date";
import { getDb } from "~/lib/db/client";
import { createEntry, getEntry } from "~/lib/db/entries";
import { normalizeEntryKind } from "~/lib/product/entry-fields";
import { waitUntilContext } from "~/lib/request-context";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/new";

export function meta(_: Route.MetaArgs) {
  return [{ title: "写下片段 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const url = new URL(request.url);
  const kind = normalizeEntryKind(url.searchParams.get("kind"));
  const defaults: EntryFormDefaults = {
    entryDate: todayISO(),
    title: "",
    body: "",
    mood: null,
    moodText: null,
    weather: null,
    location: null,
    kind,
    noteType: kind === "note" ? "daily" : null,
    people: [],
    relationships: [],
    tags: [],
  };
  return {
    defaults,
  };
}

export async function action({ request, context }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const values = entryFormFromData(form);
  const parsed = entrySchema.safeParse(values);
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? "输入有误", values };
  }

  const db = getDb(env.DB);
  const id = await createEntry(db, parsed.data);
  const entry = await getEntry(db, id);
  if (entry) {
    context.get(waitUntilContext)(runAiPipeline(env, entry));
  }
  return redirect(`/entries/${id}`);
}

export default function NewEntry({ loaderData, actionData }: Route.ComponentProps) {
  return (
    <main className="mx-auto max-w-2xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">写下片段</h1>
        <Link to="/" className="text-sm text-gray-500 hover:text-gray-900">
          ← 返回
        </Link>
      </div>
      <EntryForm
        error={actionData?.error}
        defaults={actionData?.values ?? loaderData.defaults}
        submitLabel="保存"
      />
    </main>
  );
}
