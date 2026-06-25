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
  // Wall-clock of the last successful generation, in milliseconds (audit + the UI's
  // "用时" line). Null until a generation has succeeded.
  durationMs: integer("duration_ms"),
  // How many times insight has been (re)generated for this entry; powers the
  // "生成历史" affordance without a separate history table.
  generationCount: integer("generation_count").notNull().default(0),
  generatedAt: integer("generated_at", { mode: "timestamp_ms" }),
});

/**
 * Append-only edit history for an entry. One row is written on creation and on
 * every successful content update, snapshotting that version's content so the
 * user can review "what changed when". `createdAt` is the moment the version
 * became current; rows are immutable and cascade-deleted with the entry.
 */
export const entryRevisions = sqliteTable(
  "entry_revisions",
  {
    id: text("id").primaryKey(),
    entryId: text("entry_id")
      .notNull()
      .references(() => entries.id, { onDelete: "cascade" }),
    // The entry `version` this snapshot represents (1 = creation).
    version: integer("version").notNull(),
    title: text("title").notNull().default(""),
    body: text("body").notNull().default(""),
    // JSON snapshot of the secondary fields (mood, location, people, tags, …).
    fields: text("fields"),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
  },
  (table) => [index("idx_entry_revisions_entry_id").on(table.entryId)],
);

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

/**
 * AI-generated reviews that aggregate *many* entries — period digests (day/week/
 * month/…), topic threads ("one event / one state"), woven narratives. Unlike
 * `entryAi` (1:1 with an entry) these are standalone documents, so they live in
 * their own table and reference their sources only by `sourceEntryIds` (JSON, no
 * FK) — deleting a source entry must not cascade-delete a review that mentions it.
 *
 * Follows the entries sync conventions: UUIDv7 id, `updatedAt` watermark, and a
 * `deletedAt` soft-delete tombstone. `trigger` distinguishes manual generation
 * from the scheduled cron path (phase 2).
 */
export const summaries = sqliteTable(
  "summaries",
  {
    id: text("id").primaryKey(),
    // "period" = a time window; "topic" = a thread filtered by tags/people/keyword.
    scope: text("scope").notNull(),
    // day|week|month|quarter|year|custom. Null when a topic review has no period filter.
    periodType: text("period_type"),
    // Effective window actually summarized (YYYY-MM-DD, inclusive). For a topic
    // without an explicit window this is the min/max date of the matched entries.
    startDate: text("start_date").notNull(),
    endDate: text("end_date").notNull(),
    // Output depth/voice: brief | structured | narrative (narrative = long-form).
    style: text("style").notNull().default("brief"),
    // JSON of the topic filter ({ tags?, people?, relationships?, keyword?, entryIds? }).
    // Null for a pure period summary.
    filter: text("filter"),
    title: text("title").notNull().default(""),
    content: text("content").notNull(),
    // Which provider/model produced this (audit), same idea as entry_ai.model.
    model: text("model"),
    // JSON array of the entry ids this review was built from (provenance).
    sourceEntryIds: text("source_entry_ids"),
    // How it was produced: "manual" now; "scheduled" reserved for the cron phase.
    trigger: text("trigger").notNull().default("manual"),
    generatedAt: integer("generated_at", { mode: "timestamp_ms" }).notNull(),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    deletedAt: integer("deleted_at", { mode: "timestamp_ms" }),
  },
  (table) => [
    index("idx_summaries_updated_at").on(table.updatedAt),
    // Dedup / "regenerate overwrites" lookup for period summaries.
    index("idx_summaries_scope_period_start").on(table.scope, table.periodType, table.startDate),
  ],
);

/**
 * Persistent "探寻" conversations. The current visible branch is represented by
 * `headMessageId`: loading the conversation walks that message's ancestors to
 * render one linear path through the message tree. Older sibling branches remain
 * stored and can be selected later.
 */
export const askConversations = sqliteTable(
  "ask_conversations",
  {
    id: text("id").primaryKey(),
    title: text("title").notNull().default(""),
    // JSON array of AskSourceType values used by the last send in this thread.
    sourceTypes: text("source_types").notNull().default("[]"),
    headMessageId: text("head_message_id"),
    pinnedAt: integer("pinned_at", { mode: "timestamp_ms" }),
    archivedAt: integer("archived_at", { mode: "timestamp_ms" }),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
  },
  (table) => [
    index("idx_ask_conversations_updated_at").on(table.updatedAt),
    index("idx_ask_conversations_pinned_at").on(table.pinnedAt),
    index("idx_ask_conversations_archived_at").on(table.archivedAt),
  ],
);

/**
 * Message tree for "探寻" conversations. `parentId` forms the visible branch
 * chain; `forkOfId` records which sibling a regenerated/edited message branched
 * from, so the UI can explain where a branch came from without mutating history.
 */
export const askMessages = sqliteTable(
  "ask_messages",
  {
    id: text("id").primaryKey(),
    conversationId: text("conversation_id")
      .notNull()
      .references(() => askConversations.id, { onDelete: "cascade" }),
    parentId: text("parent_id"),
    forkOfId: text("fork_of_id"),
    role: text("role").notNull(),
    content: text("content").notNull().default(""),
    status: text("status").notNull().default("completed"),
    sources: text("sources"),
    sourceTypes: text("source_types"),
    model: text("model"),
    durationMs: integer("duration_ms"),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
  },
  (table) => [
    index("idx_ask_messages_conversation_parent").on(table.conversationId, table.parentId),
    index("idx_ask_messages_conversation_created").on(table.conversationId, table.createdAt),
  ],
);

export type Entry = typeof entries.$inferSelect;
export type NewEntry = typeof entries.$inferInsert;
export type EntryAi = typeof entryAi.$inferSelect;
export type NewEntryAi = typeof entryAi.$inferInsert;
export type EntryRevision = typeof entryRevisions.$inferSelect;
export type NewEntryRevision = typeof entryRevisions.$inferInsert;
export type Tag = typeof tags.$inferSelect;
export type NewTag = typeof tags.$inferInsert;
export type Attachment = typeof attachments.$inferSelect;
export type NewAttachment = typeof attachments.$inferInsert;
export type Summary = typeof summaries.$inferSelect;
export type NewSummary = typeof summaries.$inferInsert;
export type AskConversation = typeof askConversations.$inferSelect;
export type NewAskConversation = typeof askConversations.$inferInsert;
export type AskMessage = typeof askMessages.$inferSelect;
export type NewAskMessage = typeof askMessages.$inferInsert;
