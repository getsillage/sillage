import { env } from "cloudflare:workers";
import { requireSession } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { getAttachment } from "~/lib/storage/attachments";
import type { Route } from "./+types/attachment";

/** Protected read: decrypts and streams an attachment to authenticated users. */
export async function loader({ request, params }: Route.LoaderArgs) {
  await requireSession(request, env);
  const db = getDb(env.DB);
  const attachment = await getAttachment(db, env.BLOBS, env.ATTACH_ENCRYPTION_KEY, params.id);
  if (!attachment) {
    throw new Response("Not Found", { status: 404 });
  }

  return new Response(attachment.bytes, {
    headers: {
      "Content-Type": attachment.contentType,
      // Private: the response is user-specific and must not be shared-cached.
      "Cache-Control": "private, max-age=31536000, immutable",
      "Content-Disposition": `inline; filename*=UTF-8''${encodeURIComponent(attachment.filename)}`,
    },
  });
}
