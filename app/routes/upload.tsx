import { env } from "cloudflare:workers";
import { isAuthenticated } from "~/lib/auth/session";
import { getDb } from "~/lib/db/client";
import { putAttachment } from "~/lib/storage/attachments";
import type { Route } from "./+types/upload";

const MAX_BYTES = 10 * 1024 * 1024; // 10 MB
const ALLOWED_TYPES = new Set(["image/png", "image/jpeg", "image/gif", "image/webp"]);

/** Resource route: accepts a multipart image upload and returns its URL. */
export async function action({ request }: Route.ActionArgs) {
  if (!(await isAuthenticated(request, env))) {
    return Response.json({ error: "未授权" }, { status: 401 });
  }

  const form = await request.formData();
  const file = form.get("file");
  if (!(file instanceof File)) {
    return Response.json({ error: "缺少文件" }, { status: 400 });
  }
  if (!ALLOWED_TYPES.has(file.type)) {
    return Response.json(
      { error: "仅支持 PNG/JPEG/GIF/WebP 图片" },
      {
        status: 400,
      },
    );
  }
  if (file.size > MAX_BYTES) {
    return Response.json({ error: "文件不能超过 10MB" }, { status: 400 });
  }

  const bytes = new Uint8Array(await file.arrayBuffer()) as Uint8Array<ArrayBuffer>;
  const db = getDb(env.DB);
  const attachment = await putAttachment(db, env.BLOBS, env.ATTACH_ENCRYPTION_KEY, {
    bytes,
    filename: file.name,
    contentType: file.type,
  });

  return Response.json({
    id: attachment.id,
    url: `/attachments/${attachment.id}`,
    filename: attachment.filename,
  });
}
