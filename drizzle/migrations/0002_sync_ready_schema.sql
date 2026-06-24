-- Sync-ready / multi-client schema upgrade.
--
-- Everything here is additive (ADD COLUMN) or a localized DROP COLUMN. No table is
-- rebuilt: rebuilding `entries` would break the FTS5 external-content link, and
-- rebuilding `tags` would let `DROP TABLE` cascade-delete `entry_tags`. Avoiding
-- rebuilds keeps both hazards off the table.

-- 1. Machine-derived AI data moves to a 1:1 side table so regenerating it never
--    bumps entries.updated_at or fires the FTS triggers.
CREATE TABLE `entry_ai` (
	`entry_id` text PRIMARY KEY NOT NULL,
	`summary` text,
	`sentiment` text,
	`model` text,
	`generated_at` integer,
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
INSERT INTO `entry_ai` (`entry_id`, `summary`, `sentiment`, `generated_at`)
	SELECT `id`, `summary`, `sentiment`, `updated_at` FROM `entries`
	WHERE `summary` IS NOT NULL OR `sentiment` IS NOT NULL;
--> statement-breakpoint

-- 2. Sync/robustness columns on entries (additive; FTS external content untouched).
ALTER TABLE `entries` ADD COLUMN `utc_offset_minutes` integer;--> statement-breakpoint
ALTER TABLE `entries` ADD COLUMN `metadata` text;--> statement-breakpoint
ALTER TABLE `entries` ADD COLUMN `version` integer DEFAULT 1 NOT NULL;--> statement-breakpoint
ALTER TABLE `entries` ADD COLUMN `deleted_at` integer;--> statement-breakpoint
ALTER TABLE `entries` DROP COLUMN `summary`;--> statement-breakpoint
ALTER TABLE `entries` DROP COLUMN `sentiment`;--> statement-breakpoint
CREATE INDEX `idx_entries_updated_at` ON `entries` (`updated_at`);--> statement-breakpoint

-- 3. Rebuild FTS triggers to honor soft-delete tombstones. Only live rows are ever
--    indexed, and we only delete-mark rows that were previously indexed (live), so
--    the external-content FTS5 index never goes out of sync.
DROP TRIGGER `entries_fts_ai`;--> statement-breakpoint
DROP TRIGGER `entries_fts_ad`;--> statement-breakpoint
DROP TRIGGER `entries_fts_au`;--> statement-breakpoint
CREATE TRIGGER `entries_fts_ai` AFTER INSERT ON `entries` WHEN new.deleted_at IS NULL BEGIN
	INSERT INTO entries_fts(rowid, title, body) VALUES (new.rowid, new.title, new.body);
END;--> statement-breakpoint
CREATE TRIGGER `entries_fts_ad` AFTER DELETE ON `entries` BEGIN
	INSERT INTO entries_fts(entries_fts, rowid, title, body)
		SELECT 'delete', old.rowid, old.title, old.body WHERE old.deleted_at IS NULL;
END;--> statement-breakpoint
CREATE TRIGGER `entries_fts_au` AFTER UPDATE ON `entries` BEGIN
	INSERT INTO entries_fts(entries_fts, rowid, title, body)
		SELECT 'delete', old.rowid, old.title, old.body WHERE old.deleted_at IS NULL;
	INSERT INTO entries_fts(rowid, title, body)
		SELECT new.rowid, new.title, new.body WHERE new.deleted_at IS NULL;
END;--> statement-breakpoint

-- 4. tags: sync columns. updated_at backfilled from created_at; the leftover SQL
--    default is never used at runtime (the app always sets updated_at on write).
ALTER TABLE `tags` ADD COLUMN `updated_at` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
UPDATE `tags` SET `updated_at` = `created_at`;--> statement-breakpoint
ALTER TABLE `tags` ADD COLUMN `deleted_at` integer;--> statement-breakpoint
CREATE INDEX `idx_tags_updated_at` ON `tags` (`updated_at`);--> statement-breakpoint

-- 5. attachments: integrity/lifecycle metadata + sync columns.
ALTER TABLE `attachments` ADD COLUMN `sha256` text;--> statement-breakpoint
ALTER TABLE `attachments` ADD COLUMN `width` integer;--> statement-breakpoint
ALTER TABLE `attachments` ADD COLUMN `height` integer;--> statement-breakpoint
ALTER TABLE `attachments` ADD COLUMN `status` text DEFAULT 'stored' NOT NULL;--> statement-breakpoint
ALTER TABLE `attachments` ADD COLUMN `updated_at` integer DEFAULT 0 NOT NULL;--> statement-breakpoint
UPDATE `attachments` SET `updated_at` = `created_at`;--> statement-breakpoint
ALTER TABLE `attachments` ADD COLUMN `deleted_at` integer;--> statement-breakpoint
CREATE INDEX `idx_attachments_updated_at` ON `attachments` (`updated_at`);
