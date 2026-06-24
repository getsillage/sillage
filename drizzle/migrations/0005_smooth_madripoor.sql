CREATE TABLE `summaries` (
	`id` text PRIMARY KEY NOT NULL,
	`scope` text NOT NULL,
	`period_type` text,
	`start_date` text NOT NULL,
	`end_date` text NOT NULL,
	`style` text DEFAULT 'brief' NOT NULL,
	`filter` text,
	`title` text DEFAULT '' NOT NULL,
	`content` text NOT NULL,
	`model` text,
	`source_entry_ids` text,
	`trigger` text DEFAULT 'manual' NOT NULL,
	`generated_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	`deleted_at` integer
);
--> statement-breakpoint
CREATE INDEX `idx_summaries_updated_at` ON `summaries` (`updated_at`);--> statement-breakpoint
CREATE INDEX `idx_summaries_scope_period_start` ON `summaries` (`scope`,`period_type`,`start_date`);