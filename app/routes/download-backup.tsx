import { env } from "cloudflare:workers";
import { requireSession } from "~/lib/auth/session";
import type { Route } from "./+types/download-backup";

/**
 * Streams a backup object from R2 as a file download (session-guarded). The key is
 * constrained to the `backups/` prefix so it can't be used to read arbitrary objects.
 */
export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const key = new URL(request.url).searchParams.get("key") ?? "";
  if (!key.startsWith("backups/") || key.includes("..")) {
    throw new Response("无效的备份路径", { status: 400 });
  }

  const object = await env.BLOBS.get(key);
  if (!object) {
    throw new Response("备份不存在", { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Length", String(object.size));
  headers.set("Content-Disposition", `attachment; filename="${key.split("/").pop() ?? "backup"}"`);
  headers.set("Cache-Control", "private, no-store");
  return new Response(object.body, { headers });
}
