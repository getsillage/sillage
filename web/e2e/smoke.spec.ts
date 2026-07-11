import { expect, test } from "@playwright/test";

// Smoke test: the SPA boots and routes to a usable view. Works regardless of
// whether the instance is initialized (it lands on initialize, login, or the
// app shell), so it needs no seeding.
test("app boots and renders a view", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/Sillage/i);
  // The React root mounts content (not a blank page).
  await expect(page.locator("#root")).not.toBeEmpty();
});

// First-run flow: only meaningful against a fresh, uninitialized instance.
// Skipped unless E2E_FRESH_INSTANCE is set so it never fails on a seeded server.
test("initialize the single account on a fresh instance", async ({ page }) => {
  test.skip(
    !process.env.E2E_FRESH_INSTANCE,
    "set E2E_FRESH_INSTANCE against an empty database",
  );
  await page.goto("/initialize");
  await page.getByLabel(/用户名|账号/).fill("felix");
  await page.getByLabel("密码", { exact: true }).fill("a-strong-password");
  await page.getByRole("button", { name: /创建|初始化|开始/ }).click();
  // After initialization the app shell (record composer) should appear.
  await expect(page.getByPlaceholder(/记录|想记录/)).toBeVisible();
});
