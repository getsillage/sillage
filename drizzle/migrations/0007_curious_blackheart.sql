ALTER TABLE `entry_ai` ADD `duration_ms` integer;--> statement-breakpoint
ALTER TABLE `entry_ai` ADD `generation_count` integer DEFAULT 0 NOT NULL;