import { applyD1Migrations, env } from "cloudflare:test";

// Applies the Drizzle/wrangler migrations (including the FTS5 table and triggers)
// to the test D1 database before any test runs.
await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
