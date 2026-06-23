import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getEntryDateCounts, getOnThisDay, listEntriesByDate } from "../app/lib/db/calendar";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";

const db = getDb(env.DB);

describe("calendar repository", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
    await env.DB.prepare("DELETE FROM tags").run();
  });

  it("counts entries by date within a month", async () => {
    await createEntry(db, { entryDate: "2026-06-23", title: "a", body: "", tags: [] });
    await createEntry(db, { entryDate: "2026-06-23", title: "b", body: "", tags: [] });
    await createEntry(db, { entryDate: "2026-07-01", title: "outside", body: "", tags: [] });

    const counts = await getEntryDateCounts(db, "2026-06-01", "2026-06-30");
    expect(counts.get("2026-06-23")).toBe(2);
    expect(counts.has("2026-07-01")).toBe(false);
  });

  it("lists entries for a selected date with tags", async () => {
    await createEntry(db, {
      entryDate: "2026-06-23",
      title: "today",
      body: "",
      tags: ["日历"],
    });
    await createEntry(db, { entryDate: "2026-06-24", title: "other", body: "", tags: [] });

    const rows = await listEntriesByDate(db, "2026-06-23");
    expect(rows).toHaveLength(1);
    expect(rows[0]?.title).toBe("today");
    expect(rows[0]?.tags).toEqual(["日历"]);
  });

  it("finds on-this-day entries from other years only", async () => {
    await createEntry(db, { entryDate: "2024-06-23", title: "two years ago", body: "", tags: [] });
    await createEntry(db, { entryDate: "2026-06-23", title: "today", body: "", tags: [] });
    await createEntry(db, { entryDate: "2025-06-24", title: "other day", body: "", tags: [] });

    const rows = await getOnThisDay(db, "2026-06-23");
    expect(rows.map((entry) => entry.title)).toEqual(["two years ago"]);
  });
});
