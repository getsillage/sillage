CREATE TABLE `attachments` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_id` text NOT NULL,
	`r2_key` text NOT NULL,
	`filename` text NOT NULL,
	`content_type` text NOT NULL,
	`size` integer NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `idx_attachments_entry_id` ON `attachments` (`entry_id`);--> statement-breakpoint
CREATE TABLE `entries` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_date` text NOT NULL,
	`title` text DEFAULT '' NOT NULL,
	`body` text DEFAULT '' NOT NULL,
	`mood` integer,
	`weather` text,
	`is_pinned` integer DEFAULT false NOT NULL,
	`summary` text,
	`sentiment` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `idx_entries_entry_date` ON `entries` (`entry_date`);--> statement-breakpoint
CREATE TABLE `entry_tags` (
	`entry_id` text NOT NULL,
	`tag_id` text NOT NULL,
	PRIMARY KEY(`entry_id`, `tag_id`),
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`tag_id`) REFERENCES `tags`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `tags` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `tags_name_unique` ON `tags` (`name`);--> statement-breakpoint
-- Full-text search over entries. External-content FTS5 table mapped to the
-- implicit rowid of `entries`. The `trigram` tokenizer gives usable substring
-- matching for CJK (Chinese) text, which the default unicode61 tokenizer cannot.
CREATE VIRTUAL TABLE `entries_fts` USING fts5(
	title,
	body,
	content='entries',
	content_rowid='rowid',
	tokenize='trigram'
);--> statement-breakpoint
-- Keep the FTS index in sync with the entries table.
CREATE TRIGGER `entries_fts_ai` AFTER INSERT ON `entries` BEGIN
	INSERT INTO entries_fts(rowid, title, body) VALUES (new.rowid, new.title, new.body);
END;--> statement-breakpoint
CREATE TRIGGER `entries_fts_ad` AFTER DELETE ON `entries` BEGIN
	INSERT INTO entries_fts(entries_fts, rowid, title, body) VALUES ('delete', old.rowid, old.title, old.body);
END;--> statement-breakpoint
CREATE TRIGGER `entries_fts_au` AFTER UPDATE ON `entries` BEGIN
	INSERT INTO entries_fts(entries_fts, rowid, title, body) VALUES ('delete', old.rowid, old.title, old.body);
	INSERT INTO entries_fts(rowid, title, body) VALUES (new.rowid, new.title, new.body);
END;