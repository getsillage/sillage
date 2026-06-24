CREATE TABLE `entry_revisions` (
	`id` text PRIMARY KEY NOT NULL,
	`entry_id` text NOT NULL,
	`version` integer NOT NULL,
	`title` text DEFAULT '' NOT NULL,
	`body` text DEFAULT '' NOT NULL,
	`fields` text,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`entry_id`) REFERENCES `entries`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `idx_entry_revisions_entry_id` ON `entry_revisions` (`entry_id`);