import { index, integer, primaryKey, sqliteTable, text } from "drizzle-orm/sqlite-core";

/**
 * Sillage entries — the user-authored record. `body` is Markdown plaintext (relying
 * on Cloudflare's at-rest encryption + access control) so FTS5 and AI features can
 * operate on it.
 *
 * Sync/robustness columns:
 * - `version` is an optimistic-concurrency token bumped on every content update,
 *   so a second client editing a stale copy is rejected instead of silently
 *   overwriting.
 * - `deletedAt` is a soft-delete tombstone (null = live). Hard deletes are invisible
 *   to offline clients; a tombstone lets them learn about removals and supports undo.
 * - `updatedAt` (indexed) is the delta-sync watermark: "give me everything changed
 *   since X". Machine-derived data lives in `entryAi` precisely so regenerating it
 *   never bumps this and churns the sync feed.
 */
export const entries = sqliteTable(
  "entries",
  {
    id: text("id").primaryKey(),
    // The date the entry is "for", as YYYY-MM-DD (local calendar date).
    entryDate: text("entry_date").notNull(),
    title: text("title").notNull().default(""),
    body: text("body").notNull().default(""),
    // Product shape: fragments capture the moment, notes are deliberate
    // daily/weekly/monthly/topic writing, and drafts are undecided writing.
    kind: text("kind").notNull().default("fragment"),
    noteType: text("note_type"),
    // Mood on a 1-5 scale; null when not set.
    mood: integer("mood"),
    // Free-form mood nuance, alongside the preset numeric mood.
    moodText: text("mood_text"),
    weather: text("weather"),
    location: text("location"),
    // JSON-encoded string arrays. They are first-class product fields, stored as
    // text to keep D1 reads/writes simple and explicit.
    people: text("people").notNull().default("[]"),
    relationships: text("relationships").notNull().default("[]"),
    isPinned: integer("is_pinned", { mode: "boolean" }).notNull().default(false),
    // Writer's UTC offset in minutes when the entry was saved; resolves the local
    // meaning of `entryDate` across devices in different time zones. Null = unknown.
    utcOffsetMinutes: integer("utc_offset_minutes"),
    // Forward-compatible JSON bag for client-specific / experimental fields, so new
    // clients can extend an entry without a schema migration. Null when unused.
    metadata: text("metadata"),
    // Optimistic-concurrency token; starts at 1, +1 on each content update.
    version: integer("version").notNull().default(1),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    // Soft-delete tombstone; null = live.
    deletedAt: integer("deleted_at", { mode: "timestamp_ms" }),
  },
  (table) => [
    index("idx_entries_entry_date").on(table.entryDate),
    index("idx_entries_kind").on(table.kind),
    index("idx_entries_updated_at").on(table.updatedAt),
  ],
);

/**
 * Machine-derived enrichment for an entry, kept in a 1:1 side table rather than on
 * `entries`. Writing it (the AI pipeline) must not bump `entries.updatedAt` or fire
 * the FTS triggers — otherwise every summary regeneration would wake every synced
 * client and re-index the row. New derived fields (embeddings, keywords, …) can be
 * added here without widening the hot table.
 */
export const entryAi = sqliteTable("entry_ai", {
  entryId: text("entry_id")
    .primaryKey()
    .references(() => entries.id, { onDelete: "cascade" }),
  summary: text("summary"),
  sentiment: text("sentiment"),
  // Which provider/model produced the current values (audit + regeneration logic).
  model: text("model"),
  generatedAt: integer("generated_at", { mode: "timestamp_ms" }),
});

export const tags = sqliteTable(
  "tags",
  {
    id: text("id").primaryKey(),
    name: text("name").notNull().unique(),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    deletedAt: integer("deleted_at", { mode: "timestamp_ms" }),
  },
  (table) => [index("idx_tags_updated_at").on(table.updatedAt)],
);

export const entryTags = sqliteTable(
  "entry_tags",
  {
    entryId: text("entry_id")
      .notNull()
      .references(() => entries.id, { onDelete: "cascade" }),
    tagId: text("tag_id")
      .notNull()
      .references(() => tags.id, { onDelete: "cascade" }),
  },
  (table) => [primaryKey({ columns: [table.entryId, table.tagId] })],
);

export const attachments = sqliteTable(
  "attachments",
  {
    id: text("id").primaryKey(),
    // Nullable: an attachment can be uploaded before its entry exists (new-entry
    // flow) and is referenced by URL in the Markdown body.
    entryId: text("entry_id").references(() => entries.id, {
      onDelete: "cascade",
    }),
    // Object key in R2. The stored bytes are AES-GCM encrypted at the app layer.
    r2Key: text("r2_key").notNull(),
    filename: text("filename").notNull(),
    contentType: text("content_type").notNull(),
    size: integer("size").notNull(),
    // SHA-256 (hex) of the plaintext bytes: integrity check, dedup, and resumable
    // uploads. Null for rows predating the column.
    sha256: text("sha256"),
    // Pixel dimensions when the attachment is an image, so clients can reserve
    // layout space without downloading the bytes. Null when unknown / non-image.
    width: integer("width"),
    height: integer("height"),
    // Upload lifecycle: "pending" until the bytes are committed, then "stored".
    status: text("status").notNull().default("stored"),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    deletedAt: integer("deleted_at", { mode: "timestamp_ms" }),
  },
  (table) => [
    index("idx_attachments_entry_id").on(table.entryId),
    index("idx_attachments_updated_at").on(table.updatedAt),
  ],
);

export type Entry = typeof entries.$inferSelect;
export type NewEntry = typeof entries.$inferInsert;
export type EntryAi = typeof entryAi.$inferSelect;
export type NewEntryAi = typeof entryAi.$inferInsert;
export type Tag = typeof tags.$inferSelect;
export type NewTag = typeof tags.$inferInsert;
export type Attachment = typeof attachments.$inferSelect;
export type NewAttachment = typeof attachments.$inferInsert;
