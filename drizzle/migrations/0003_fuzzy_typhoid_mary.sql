ALTER TABLE `entries` ADD `kind` text DEFAULT 'fragment' NOT NULL;--> statement-breakpoint
ALTER TABLE `entries` ADD `note_type` text;--> statement-breakpoint
ALTER TABLE `entries` ADD `mood_text` text;--> statement-breakpoint
ALTER TABLE `entries` ADD `location` text;--> statement-breakpoint
ALTER TABLE `entries` ADD `people` text DEFAULT '[]' NOT NULL;--> statement-breakpoint
ALTER TABLE `entries` ADD `relationships` text DEFAULT '[]' NOT NULL;--> statement-breakpoint
CREATE INDEX `idx_entries_kind` ON `entries` (`kind`);
