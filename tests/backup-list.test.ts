import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { createUserSession } from "../app/lib/auth/session";
import { listBackups } from "../app/lib/backup/list";
import { loader as downloadLoader } from "../app/routes/download-backup";

async function authedRequest(url: string): Promise<Request> {
  const response = await createUserSession(env, "/");
  const cookie = response.headers.get("Set-Cookie");
  if (!cookie) {
    throw new Error("missing session cookie");
  }
  const request = new Request(url);
  request.headers.set("Cookie", cookie.split(";")[0]);
  return request;
}

function callDownload(request: Request) {
  return downloadLoader({ request, context: undefined as never, params: {} } as never);
}

async function caughtResponse(promise: Promise<unknown>): Promise<Response> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof Response) {
      return error;
    }
    throw error;
  }
  throw new Error("expected a thrown Response");
}

beforeEach(async () => {
  const listed = await env.BLOBS.list({ prefix: "backups/" });
  await Promise.all(listed.objects.map((object) => env.BLOBS.delete(object.key)));
});

describe("listBackups", () => {
  it("groups each export's json + md pair, parsing date and size", async () => {
    await env.BLOBS.put("backups/2026-06-23/sillage-2026-06-23T00-00-00-000Z.json", "{}");
    await env.BLOBS.put("backups/2026-06-23/sillage-2026-06-23T00-00-00-000Z.md", "# 一");
    await env.BLOBS.put("backups/2026-06-24/sillage-2026-06-24T00-00-00-000Z.json", "{}");
    await env.BLOBS.put("backups/2026-06-24/sillage-2026-06-24T00-00-00-000Z.md", "# 二");

    const items = await listBackups(env);
    expect(items).toHaveLength(2);

    const byDate = Object.fromEntries(items.map((item) => [item.date, item]));
    expect(Object.keys(byDate).sort()).toEqual(["2026-06-23", "2026-06-24"]);

    const latest = byDate["2026-06-24"];
    expect(latest.files.map((file) => file.format).sort()).toEqual(["json", "markdown"]);
    expect(latest.files.find((file) => file.format === "json")?.key).toBe(
      "backups/2026-06-24/sillage-2026-06-24T00-00-00-000Z.json",
    );
  });

  it("returns an empty list when there are no backups", async () => {
    expect(await listBackups(env)).toEqual([]);
  });
});

describe("download-backup route", () => {
  it("streams a backup as a file download", async () => {
    const key = "backups/2026-06-24/sillage-2026-06-24T00-00-00-000Z.json";
    await env.BLOBS.put(key, '{"ok":true}', {
      httpMetadata: { contentType: "application/json; charset=utf-8" },
    });

    const response = (await callDownload(
      await authedRequest(`https://sillage.example/download-backup?key=${encodeURIComponent(key)}`),
    )) as Response;

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Disposition")).toContain(
      "sillage-2026-06-24T00-00-00-000Z.json",
    );
    expect(await response.text()).toBe('{"ok":true}');
  });

  it("rejects keys outside the backups/ prefix", async () => {
    const response = await caughtResponse(
      callDownload(await authedRequest("https://sillage.example/download-backup?key=secrets/app")),
    );
    expect(response.status).toBe(400);
  });

  it("rejects path traversal", async () => {
    const response = await caughtResponse(
      callDownload(
        await authedRequest("https://sillage.example/download-backup?key=backups/../secrets"),
      ),
    );
    expect(response.status).toBe(400);
  });

  it("404s a missing backup", async () => {
    const response = await caughtResponse(
      callDownload(
        await authedRequest(
          "https://sillage.example/download-backup?key=backups/2026-06-24/missing.json",
        ),
      ),
    );
    expect(response.status).toBe(404);
  });
});
