import { defineConfig, devices } from "@playwright/test";

// E2E runs against an already-running Sillage instance. Point at it with
// PLAYWRIGHT_BASE_URL; defaults to the Go server's dev port. To run:
//   pnpm --dir web exec playwright install   # once, to fetch browsers
//   SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage &   # fresh instance
//   pnpm --dir web test:e2e
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:5231";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "line" : "list",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
