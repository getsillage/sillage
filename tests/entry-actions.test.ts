import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createUserSession } from "../app/lib/auth/session";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";
import { action as entryAction } from "../app/routes/entry";
import { action as homeAction } from "../app/routes/home";
import { action as newAction } from "../app/routes/new";

const db = getDb(env.DB);

async function resetState() {
  await env.DB.prepare("DELETE FROM entry_revisions").run();
  await env.DB.prepare("DELETE FROM entry_tags").run();
  await env.DB.prepare("DELETE FROM entries").run();
  await env.DB.prepare("DELETE FROM tags").run();
  await env.SESSIONS.delete("ai-settings");
}

function requestFrom(form: Record<string, string>, url: string): Request {
  const body = new URLSearchParams(form);
  return new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  });
}

async function authenticatedRequest(form: Record<string, string>, url: string): Promise<Request> {
  const response = await createUserSession(env, "/");
  const cookie = response.headers.get("Set-Cookie");
  if (!cookie) {
    throw new Error("missing session cookie");
  }
  const request = requestFrom(form, url);
  request.headers.set("Cookie", cookie.split(";")[0]);
  return request;
}

describe("entry actions", () => {
  beforeEach(resetState);
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("does not invoke AI when saving a new entry from the home page", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await homeAction({
      request: await authenticatedRequest(
        {
          entryDate: "2026-06-24",
          title: "手动保存",
          body: "只保存，不触发 AI。",
          kind: "fragment",
          noteType: "",
          mood: "",
          moodText: "",
          weather: "",
          location: "",
          people: "",
          relationships: "",
          tags: "",
        },
        "https://sillage.example/",
      ),
      context: undefined as never,
      params: {},
    } as never);

    expect(response.status).toBe(302);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("does not invoke AI when creating a new entry from the new-entry page", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const response = await newAction({
      request: await authenticatedRequest(
        {
          entryDate: "2026-06-24",
          title: "新建记录",
          body: "保存后不应自动生成洞察。",
          kind: "fragment",
          noteType: "",
          mood: "",
          moodText: "",
          weather: "",
          location: "",
          people: "",
          relationships: "",
          tags: "",
        },
        "https://sillage.example/new",
      ),
      context: undefined as never,
      params: {},
    } as never);

    expect(response.status).toBe(302);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("does not invoke AI when updating an entry", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const entryId = await createEntry(db, {
      entryDate: "2026-06-24",
      title: "待更新",
      body: "先建一条记录。",
      kind: "fragment",
      noteType: null,
      mood: null,
      moodText: null,
      weather: null,
      location: null,
      people: [],
      relationships: [],
      tags: [],
    });

    const updateResponse = await entryAction({
      request: await authenticatedRequest(
        {
          entryDate: "2026-06-24",
          title: "已更新",
          body: "更新后也不应自动生成洞察。",
          kind: "fragment",
          noteType: "",
          mood: "",
          moodText: "",
          weather: "",
          location: "",
          people: "",
          relationships: "",
          tags: "",
        },
        `https://sillage.example/entries/${entryId}`,
      ),
      context: undefined as never,
      params: { id: entryId },
    } as never);

    expect(updateResponse.status).toBe(302);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
