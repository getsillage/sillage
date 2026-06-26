DROP INDEX `idx_entries_kind`;--> statement-breakpoint
ALTER TABLE `entries` DROP COLUMN `kind`;--> statement-breakpoint
ALTER TABLE `entries` DROP COLUMN `note_type`;