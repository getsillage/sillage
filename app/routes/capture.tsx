import { env } from "cloudflare:workers";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { createEntry } from "~/lib/db/entries";
import { entryFormFromData, entrySchema } from "~/lib/validation/entry";
import type { Route } from "./+types/capture";

/**
 * Action-only endpoint for the global QuickCapture overlay. Returns JSON (never a
 * redirect) so capturing from any page leaves the user where they are; React Router
 * revalidates the current route's loaders afterwards, so the new entry appears in
 * place (e.g. in 此刻's today list or 痕迹's stream).
 */
export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const parsed = entrySchema.safeParse(entryFormFromData(form));
  if (!parsed.success) {
    return { ok: false as const, error: parsed.error.issues[0]?.message ?? "输入有误" };
  }
  const id = await createEntry(getDb(env.DB), parsed.data);
  return { ok: true as const, id };
}
