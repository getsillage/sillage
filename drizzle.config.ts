import { defineConfig } from "drizzle-kit";

// drizzle-kit only generates the SQL migration files from the schema.
// Applying them to D1 is done via `wrangler d1 migrations apply` (local/remote),
// so no DB credentials are needed here.
export default defineConfig({
  dialect: "sqlite",
  schema: "./app/lib/db/schema.ts",
  out: "./drizzle/migrations",
});
