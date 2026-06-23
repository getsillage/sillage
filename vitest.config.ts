import { fileURLToPath } from "node:url";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

// Tests run inside the real workerd runtime so D1/KV/R2 bindings behave exactly
// as in production. Migrations are read here and applied per test-worker via the
// setup file, giving each isolated-storage run a fully-migrated D1 database.
export default defineConfig(async () => {
  const migrations = await readD1Migrations("./drizzle/migrations");

  return {
    resolve: {
      // Mirror the `~/* -> app/*` tsconfig path alias used by the app code.
      alias: { "~": fileURLToPath(new URL("./app", import.meta.url)) },
    },
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migrations,
            // Test-only secret values; never used outside the test runtime.
            SESSION_SECRET: "test-session-secret-value-do-not-use",
            ATTACH_ENCRYPTION_KEY: "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
          },
        },
      }),
    ],
    test: {
      setupFiles: ["./tests/apply-migrations.ts"],
      coverage: {
        provider: "istanbul",
        include: ["app/lib/**/*.ts"],
        thresholds: {
          lines: 80,
          functions: 80,
          branches: 80,
          statements: 80,
        },
      },
    },
  };
});
