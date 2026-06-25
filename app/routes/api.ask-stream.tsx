import { env } from "cloudflare:workers";
import { runAskStream } from "~/lib/ai/ask-stream";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import type { Route } from "./+types/api.ask-stream";

export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const db = getDb(env.DB);
  return runAskStream(db, form, request.signal);
}
