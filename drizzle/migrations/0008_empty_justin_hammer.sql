CREATE TABLE `ask_conversations` (
	`id` text PRIMARY KEY NOT NULL,
	`title` text DEFAULT '' NOT NULL,
	`source_types` text DEFAULT '[]' NOT NULL,
	`head_message_id` text,
	`pinned_at` integer,
	`archived_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `idx_ask_conversations_updated_at` ON `ask_conversations` (`updated_at`);--> statement-breakpoint
CREATE INDEX `idx_ask_conversations_pinned_at` ON `ask_conversations` (`pinned_at`);--> statement-breakpoint
CREATE INDEX `idx_ask_conversations_archived_at` ON `ask_conversations` (`archived_at`);--> statement-breakpoint
CREATE TABLE `ask_messages` (
	`id` text PRIMARY KEY NOT NULL,
	`conversation_id` text NOT NULL,
	`parent_id` text,
	`fork_of_id` text,
	`role` text NOT NULL,
	`content` text DEFAULT '' NOT NULL,
	`status` text DEFAULT 'completed' NOT NULL,
	`sources` text,
	`source_types` text,
	`model` text,
	`duration_ms` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`conversation_id`) REFERENCES `ask_conversations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `idx_ask_messages_conversation_parent` ON `ask_messages` (`conversation_id`,`parent_id`);--> statement-breakpoint
CREATE INDEX `idx_ask_messages_conversation_created` ON `ask_messages` (`conversation_id`,`created_at`);