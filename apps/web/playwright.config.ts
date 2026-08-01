import { defineConfig, devices } from "@playwright/test";

// E2E runs against an already-running Sillage instance. Point at it with
// PLAYWRIGHT_BASE_URL; defaults to the Go server's dev port. To run:
//   pnpm --dir apps/web exec playwright install   # once, to fetch browsers
//   SILLAGE_DATA="$(mktemp -d)" go run ./cmd/sillage &   # fresh instance
//   pnpm --dir apps/web test:e2e
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:5231";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  expect: { timeout: 5_000 },
  // E2E targets one mutable, single-account instance. Serial execution keeps
  // account, session, and record lifecycle tests deterministic.
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "line" : "list",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});
