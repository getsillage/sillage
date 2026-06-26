PRAGMA foreign_keys=OFF;--> statement-breakpoint
DROP TRIGGER IF EXISTS `entries_fts_ai`;--> statement-breakpoint
DROP TRIGGER IF EXISTS `entries_fts_ad`;--> statement-breakpoint
DROP TRIGGER IF EXISTS `entries_fts_au`;--> statement-breakpoint
DROP TABLE IF EXISTS `entries_fts`;--> statement-breakpoint
CREATE TABLE `__new_entries` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_date` text NOT NULL,
	`body` text DEFAULT '' NOT NULL,
	`version` integer DEFAULT 1 NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	`deleted_at` integer
);--> statement-breakpoint
INSERT INTO `__new_entries` (`id`, `entry_date`, `body`, `version`, `created_at`, `updated_at`, `deleted_at`)
	SELECT `id`, `entry_date`, `body`, `version`, `created_at`, `updated_at`, `deleted_at` FROM `entries`;--> statement-breakpoint
DROP TABLE `entries`;--> statement-breakpoint
ALTER TABLE `__new_entries` RENAME TO `entries`;--> statement-breakpoint
CREATE INDEX `idx_entries_entry_date` ON `entries` (`entry_date`);--> statement-breakpoint
CREATE INDEX `idx_entries_updated_at` ON `entries` (`updated_at`);--> statement-breakpoint
CREATE TABLE `__new_entry_revisions` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_id` text NOT NULL,
	`version` integer NOT NULL,
	`entry_date` text NOT NULL,
	`body` text DEFAULT '' NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade
);--> statement-breakpoint
INSERT INTO `__new_entry_revisions` (`id`, `entry_id`, `version`, `entry_date`, `body`, `created_at`)
	SELECT `id`, `entry_id`, `version`, COALESCE(json_extract(`fields`, '$.entryDate'), ''), `body`, `created_at` FROM `entry_revisions`;--> statement-breakpoint
DROP TABLE `entry_revisions`;--> statement-breakpoint
ALTER TABLE `__new_entry_revisions` RENAME TO `entry_revisions`;--> statement-breakpoint
CREATE INDEX `idx_entry_revisions_entry_id` ON `entry_revisions` (`entry_id`);--> statement-breakpoint
DROP TABLE IF EXISTS `entry_tags`;--> statement-breakpoint
DROP TABLE IF EXISTS `tags`;--> statement-breakpoint
CREATE VIRTUAL TABLE `entries_fts` USING fts5(
	body,
	content='entries',
	content_rowid='rowid',
	tokenize='trigram'
);--> statement-breakpoint
CREATE TRIGGER `entries_fts_ai` AFTER INSERT ON `entries` WHEN new.deleted_at IS NULL BEGIN
	INSERT INTO entries_fts(rowid, body) VALUES (new.rowid, new.body);
END;--> statement-breakpoint
CREATE TRIGGER `entries_fts_ad` AFTER DELETE ON `entries` BEGIN
	INSERT INTO entries_fts(entries_fts, rowid, body)
		SELECT 'delete', old.rowid, old.body WHERE old.deleted_at IS NULL;
END;--> statement-breakpoint
CREATE TRIGGER `entries_fts_au` AFTER UPDATE ON `entries` BEGIN
	INSERT INTO entries_fts(entries_fts, rowid, body)
		SELECT 'delete', old.rowid, old.body WHERE old.deleted_at IS NULL;
	INSERT INTO entries_fts(rowid, body)
		SELECT new.rowid, new.body WHERE new.deleted_at IS NULL;
END;--> statement-breakpoint
INSERT INTO `entries_fts` (`rowid`, `body`)
	SELECT rowid, body FROM entries WHERE deleted_at IS NULL;--> statement-breakpoint
PRAGMA foreign_keys=ON;
