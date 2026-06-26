import { env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { getEntryDateCounts, getOnThisDay, listEntriesByDate } from "../app/lib/db/calendar";
import { getDb } from "../app/lib/db/client";
import { createEntry } from "../app/lib/db/entries";

const db = getDb(env.DB);

describe("calendar repository", () => {
  beforeEach(async () => {
    await env.DB.prepare("DELETE FROM entries").run();
  });

  it("counts entries by date within a month", async () => {
    await createEntry(db, { entryDate: "2026-06-23", body: "a" });
    await createEntry(db, { entryDate: "2026-06-23", body: "b" });
    await createEntry(db, { entryDate: "2026-07-01", body: "outside" });

    const counts = await getEntryDateCounts(db, "2026-06-01", "2026-06-30");
    expect(counts.get("2026-06-23")).toBe(2);
    expect(counts.has("2026-07-01")).toBe(false);
  });

  it("lists entries for a selected date", async () => {
    await createEntry(db, {
      entryDate: "2026-06-23",
      body: "today",
    });
    await createEntry(db, { entryDate: "2026-06-24", body: "other" });

    const rows = await listEntriesByDate(db, "2026-06-23");
    expect(rows).toHaveLength(1);
    expect(rows[0]?.body).toBe("today");
  });

  it("finds on-this-day entries from other years only", async () => {
    await createEntry(db, { entryDate: "2024-06-23", body: "two years ago" });
    await createEntry(db, { entryDate: "2026-06-23", body: "today" });
    await createEntry(db, { entryDate: "2025-06-24", body: "other day" });

    const rows = await getOnThisDay(db, "2026-06-23");
    expect(rows.map((entry) => entry.body)).toEqual(["two years ago"]);
  });
});
