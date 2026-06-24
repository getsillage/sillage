import { index, integer, primaryKey, sqliteTable, text } from "drizzle-orm/sqlite-core";

/**
 * Diary entries. `body` is stored as Markdown plaintext (relying on Cloudflare's
 * at-rest encryption + access control) so that FTS5 and AI features can operate
 * on it. `summary` is written back asynchronously by the AI pipeline.
 */
export const entries = sqliteTable(
  "entries",
  {
    id: text("id").primaryKey(),
    // The date the entry is "for", as YYYY-MM-DD (local calendar date).
    entryDate: text("entry_date").notNull(),
    title: text("title").notNull().default(""),
    body: text("body").notNull().default(""),
    // Mood on a 1-5 scale; null when not set.
    mood: integer("mood"),
    weather: text("weather"),
    isPinned: integer("is_pinned", { mode: "boolean" }).notNull().default(false),
    // AI-generated, nullable until the pipeline fills them in.
    summary: text("summary"),
    sentiment: text("sentiment"),
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
    updatedAt: integer("updated_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
  },
  (table) => [index("idx_entries_entry_date").on(table.entryDate)],
);

export const tags = sqliteTable("tags", {
  id: text("id").primaryKey(),
  name: text("name").notNull().unique(),
  createdAt: integer("created_at", { mode: "timestamp_ms" })
    .notNull()
    .$defaultFn(() => new Date()),
});

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
    createdAt: integer("created_at", { mode: "timestamp_ms" })
      .notNull()
      .$defaultFn(() => new Date()),
  },
  (table) => [index("idx_attachments_entry_id").on(table.entryId)],
);

export type Entry = typeof entries.$inferSelect;
export type NewEntry = typeof entries.$inferInsert;
export type Tag = typeof tags.$inferSelect;
export type NewTag = typeof tags.$inferInsert;
export type Attachment = typeof attachments.$inferSelect;
export type NewAttachment = typeof attachments.$inferInsert;
