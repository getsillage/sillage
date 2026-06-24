import { env } from "cloudflare:workers";
import { Link, redirect } from "react-router";
import { EntryForm, type EntryFormDefaults } from "~/components/EntryForm";
import { requireSession } from "~/lib/auth/session";
import { todayISO } from "~/lib/date";
import { getDb } from "~/lib/db/client";
import { createEntry, listEntries } from "~/lib/db/entries";
import { normalizeEntryKind } from "~/lib/product/entry-fields";
import { buildEntryFormSuggestions } from "~/lib/product/entry-suggestions";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/new";

export function meta(_: Route.MetaArgs) {
  return [{ title: "写下片段 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const url = new URL(request.url);
  const kind = normalizeEntryKind(url.searchParams.get("kind"));
  const recentEntries = await listEntries(getDb(env.DB), 80);
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
    suggestions: buildEntryFormSuggestions(recentEntries),
  };
}

export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const values = entryFormFromData(form);
  const parsed = entrySchema.safeParse(values);
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? "输入有误", values };
  }

  const db = getDb(env.DB);
  const id = await createEntry(db, parsed.data);
  return redirect(`/entries/${id}`);
}

export default function NewEntry({ loaderData, actionData }: Route.ComponentProps) {
  return (
    <main className="mx-auto max-w-2xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-semibold text-xl dark:text-gray-50">写下片段</h1>
        <Link
          to="/"
          className="text-gray-500 text-sm hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100"
        >
          ← 返回
        </Link>
      </div>
      <EntryForm
        error={actionData?.error}
        defaults={actionData?.values ?? loaderData.defaults}
        suggestions={loaderData.suggestions}
        submitLabel="保存"
      />
    </main>
  );
}
