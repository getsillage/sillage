import {
  type APIRequestContext,
  type APIResponse,
  expect,
  type Locator,
  type Page,
  request as requestFactory,
  test,
} from "@playwright/test";
import { type Locale, translate } from "../src/i18n/messages";

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:5231";
const USERNAME = "felix-e2e";
const DISPLAY_NAME = "Sillage E2E";
const PASSWORD = "a-strong-password";
const UPDATED_PASSWORD = "a-new-strong-password";
const LIFECYCLE_MARKER = "E2ELifecycleJourney";
const CONFLICT_MARKER = "E2EConflictJourney";
const AI_MARKER = "E2EAIJourney";
const RESPONSIVE_MARKER = "E2EResponsiveJourney";
const ATTACHMENT_FILENAME = "e2e-attachment.txt";
const RESPONSIVE_CONTINUOUS_TEXT = `LongContinuousText${"x".repeat(600)}`;
const RESPONSIVE_CONTENT = [
  `# ${RESPONSIVE_MARKER}`,
  "",
  RESPONSIVE_CONTINUOUS_TEXT,
  "",
  "```text",
  `LongCodeLine${"0123456789".repeat(90)}`,
  "```",
  "",
  `| ${Array.from({ length: 12 }, (_, index) => `Column ${index + 1}`).join(" | ")} |`,
  `| ${Array.from({ length: 12 }, () => "---").join(" | ")} |`,
  `| ${Array.from({ length: 12 }, (_, index) => `Value${index}${"x".repeat(40)}`).join(" | ")} |`,
].join("\n");

type AuthPayload = { accessToken: string };
type Memo = {
  id: string;
  content: string;
  entryDate: string;
  version: number;
};

test("app boots and renders a view", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/Sillage/i);
  await expect(page.locator("#root")).not.toBeEmpty();
});

test.describe("fresh-instance release journeys", () => {
  test.describe.configure({ mode: "serial" });
  test.skip(
    !process.env.E2E_FRESH_INSTANCE,
    "release journeys require make check-e2e's disposable instance",
  );

  test("switches language and initializes the only account", async ({
    page,
    request,
  }) => {
    const bootstrapResponse = await request.get("/api/v1/auth/bootstrap");
    const bootstrap = await json<{
      initialized: boolean;
      serverVersion: string;
      serverRevision: string;
      apiVersion: string;
      minimumAndroidVersionCode: number;
    }>(bootstrapResponse);
    expect(bootstrap.apiVersion).toBe("v1");
    expect(bootstrap.minimumAndroidVersionCode).toBe(9);
    expect(bootstrap.serverVersion).toBeTruthy();
    expect(bootstrap.serverRevision).toBeTruthy();
    expect(bootstrapResponse.headers()["cache-control"]).toBe("no-store");
    expect(bootstrapResponse.headers()["x-sillage-api-version"]).toBe("v1");
    if (bootstrap.initialized) {
      // A serial-group retry reuses the disposable server. Restore the known
      // baseline and still prove the initialized instance remains usable.
      await ensureBaselinePassword(request);
      await signInThroughUI(page, PASSWORD);
      return;
    }

    await page.goto("/initialize");
    await page.getByRole("button", { name: "English" }).click();
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    await expect(
      page.getByRole("heading", { name: "Create the only account" }),
    ).toBeVisible();

    await page.setViewportSize({ width: 320, height: 844 });
    for (const colorScheme of ["dark", "light"] as const) {
      await page.emulateMedia({ colorScheme });
      await page.evaluate((preference) => {
        localStorage.setItem("sillage-theme", preference);
      }, colorScheme);
      await page.reload();
      await expect(page.locator("html")).toHaveCSS("color-scheme", colorScheme);
      await expectPhoneInput(page.getByLabel("Username"));
      await expectPhoneInput(page.getByLabel("Display name"));
      await expectPhoneInput(page.getByLabel("Password", { exact: true }));
      await expectTouchTarget(
        page.getByRole("button", { name: "Create and continue" }),
      );
      await expectNoHorizontalPageScroll(page);
      await page.screenshot({
        path: test.info().outputPath(`mobile-initialize-${colorScheme}.png`),
      });
    }

    await page.reload();
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    await page.getByLabel("Username").fill(USERNAME);
    await page.getByLabel("Display name").fill(DISPLAY_NAME);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Create and continue" }).click();
    await expect(
      page.getByRole("status").filter({ hasText: "Account created" }),
    ).toBeVisible();
    await expect(
      page.getByPlaceholder("Write what you want to remember..."),
    ).toBeVisible();
  });

  test("handles wrong passwords, refreshes a session, signs out, and signs back in", async ({
    page,
  }) => {
    await setEnglishLocale(page);
    await page.goto("/login");
    await expect(
      page.getByRole("heading", { name: "Sign in to Sillage" }),
    ).toBeVisible();

    await page.getByLabel("Username").fill(USERNAME);
    await page.getByLabel("Password", { exact: true }).fill("definitely-wrong");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(
      page.getByRole("alert").filter({
        hasText: "Incorrect account or password",
      }),
    ).toBeVisible();

    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(
      page.getByPlaceholder("Write what you want to remember..."),
    ).toBeVisible();

    // Reload clears the memory-only access token. The HttpOnly refresh cookie
    // must reopen the app without script-readable token persistence.
    await page.reload();
    await expect(
      page.getByPlaceholder("Write what you want to remember..."),
    ).toBeVisible();

    await page.getByText(DISPLAY_NAME, { exact: true }).click();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "Sign in to Sillage" }),
    ).toBeVisible();

    await page.getByLabel("Username").fill(USERNAME);
    await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(
      page.getByPlaceholder("Write what you want to remember..."),
    ).toBeVisible();
  });

  test("creates, searches, edits, archives, downloads, restores, and permanently deletes a record", async ({
    page,
    request,
  }) => {
    const token = await signInAPI(request, PASSWORD);
    await cleanupE2EState(request, token, LIFECYCLE_MARKER);
    await signInThroughUI(page, PASSWORD);

    const originalContent = `${LIFECYCLE_MARKER} stores an attachment and remains searchable.`;
    const updatedContent = `${LIFECYCLE_MARKER} was edited and is still searchable.`;
    const editor = page.getByRole("textbox", {
      name: "Record content",
      exact: true,
    });
    await editor.fill(originalContent);
    await page.locator('input[type="file"]').setInputFiles({
      name: ATTACHMENT_FILENAME,
      mimeType: "text/plain",
      buffer: Buffer.from("Sillage authenticated attachment E2E\n"),
    });
    await expect(
      page.getByRole("status").filter({ hasText: "Attachment inserted" }),
    ).toBeVisible();
    const draft = await editor.inputValue();
    const attachmentURL = extractAttachmentURL(draft);

    const saveResponsePromise = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/v1/memos") &&
        response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "Save", exact: true }).click();
    const saveResponse = await saveResponsePromise;
    expect(saveResponse.ok()).toBeTruthy();
    const { memo: savedMemo } = (await saveResponse.json()) as { memo: Memo };
    expect(savedMemo.content).toContain(LIFECYCLE_MARKER);
    await expect(
      page.getByRole("status").filter({ hasText: "Record saved" }),
    ).toBeVisible();

    await expect
      .poll(async () => {
        const response = await json<{ memos: Memo[] }>(
          await request.get(
            `/api/v1/memos?query=${encodeURIComponent(LIFECYCLE_MARKER)}&limit=100&archived=false`,
            { headers: bearer(token) },
          ),
        );
        return response.memos.some((memo) => memo.id === savedMemo.id);
      })
      .toBe(true);

    await page.getByRole("link", { name: "All records" }).click();
    await page.getByPlaceholder("Search records...").fill(LIFECYCLE_MARKER);
    await expect(page.getByText("Found 1 record")).toBeVisible();
    await page.getByText(LIFECYCLE_MARKER).first().click();
    await expect(
      page.locator("article").filter({ hasText: LIFECYCLE_MARKER }),
    ).toBeVisible();

    const attachmentLink = page.getByRole("link", {
      name: ATTACHMENT_FILENAME,
    });
    await expect(attachmentLink).toHaveAttribute("href", attachmentURL);
    const authenticatedDownload = await page
      .context()
      .request.get(attachmentURL);
    expect(authenticatedDownload.status()).toBe(200);
    expect(await authenticatedDownload.text()).toBe(
      "Sillage authenticated attachment E2E\n",
    );

    const anonymous = await requestFactory.newContext({ baseURL: BASE_URL });
    try {
      const anonymousDownload = await anonymous.get(attachmentURL);
      expect(anonymousDownload.status()).toBe(401);
    } finally {
      await anonymous.dispose();
    }

    await page.getByRole("button", { name: "Edit", exact: true }).click();
    const editBox = page.getByRole("textbox", {
      name: "Record content",
      exact: true,
    });
    await editBox.fill(
      (await editBox.inputValue()).replace(originalContent, updatedContent),
    );
    await page.getByRole("button", { name: "Update" }).click();
    await expect(
      page.locator("article").filter({ hasText: LIFECYCLE_MARKER }),
    ).toBeVisible();

    await page.getByRole("button", { name: "Archive", exact: true }).click();
    await expect(page.getByText("Archived", { exact: true })).toBeVisible();
    await page.goto("/timeline?filter=archived");
    await expect(page.getByText(LIFECYCLE_MARKER).first()).toBeVisible();
    await page.getByText(LIFECYCLE_MARKER).first().click();
    await page.getByRole("button", { name: "Unarchive", exact: true }).click();
    await expect(page.getByText("Archived", { exact: true })).toHaveCount(0);

    const memoID = entryIDFromURL(page.url());
    await page.getByRole("button", { name: "Delete", exact: true }).click();
    const deleteDialog = page.getByRole("alertdialog", {
      name: "Delete this record?",
    });
    await deleteDialog.getByRole("button", { name: "Confirm delete" }).click();
    await expect(
      page.locator("article").filter({ hasText: LIFECYCLE_MARKER }),
    ).toHaveCount(0);
    expect(
      (
        await request.get(`/api/v1/memos/${memoID}`, { headers: bearer(token) })
      ).status(),
    ).toBe(404);

    await page.goto("/timeline?filter=deleted");
    await expect(page.getByText(LIFECYCLE_MARKER).first()).toBeVisible();
    await expect(
      page.getByText(/Records can be restored for 30 days/),
    ).toBeVisible();
    const deletedItem = page
      .locator("li")
      .filter({ hasText: LIFECYCLE_MARKER })
      .first();
    await deletedItem
      .getByRole("button", { name: "Restore", exact: true })
      .click();
    await expect(page.getByText(LIFECYCLE_MARKER)).toHaveCount(0);

    await page.goto("/timeline");
    await expect(page.getByText(LIFECYCLE_MARKER).first()).toBeVisible();
    await page.getByText(LIFECYCLE_MARKER).first().click();
    await page.getByRole("button", { name: "Delete", exact: true }).click();
    await page
      .getByRole("alertdialog", { name: "Delete this record?" })
      .getByRole("button", { name: "Confirm delete" })
      .click();
    await page.goto("/timeline?filter=deleted");
    const purgeItem = page
      .locator("li")
      .filter({ hasText: LIFECYCLE_MARKER })
      .first();
    await expect(purgeItem).toBeVisible();
    await purgeItem
      .getByRole("button", { name: "Delete permanently", exact: true })
      .click();
    await purgeItem
      .getByRole("button", { name: "Confirm permanent delete", exact: true })
      .click();
    await expect(page.getByText(LIFECYCLE_MARKER)).toHaveCount(0);
    expect(
      (
        await request.get(attachmentURL, {
          headers: bearer(token),
        })
      ).status(),
    ).toBe(404);
  });

  test("preserves a local draft when a concurrent update conflicts", async ({
    page,
    request,
  }) => {
    const token = await signInAPI(request, PASSWORD);
    await cleanupE2EState(request, token, CONFLICT_MARKER);
    const created = await createMemoAPI(
      request,
      token,
      `${CONFLICT_MARKER} original server version`,
    );
    await signInThroughUI(page, PASSWORD);
    await page.goto(`/entries/${created.id}`);

    await page.getByRole("button", { name: "Edit", exact: true }).click();
    const localDraft = `${CONFLICT_MARKER} local draft survives`;
    await page
      .getByRole("textbox", { name: "Record content", exact: true })
      .fill(localDraft);

    const externalContent = `${CONFLICT_MARKER} external server update`;
    await json(
      await request.patch(`/api/v1/memos/${created.id}`, {
        headers: bearer(token),
        data: {
          content: externalContent,
          expectedVersion: created.version,
        },
      }),
    );

    await page.getByRole("button", { name: "Update" }).click();
    await expect(
      page
        .getByRole("alert")
        .filter({
          hasText: "This record was updated elsewhere. Refresh and try again",
        })
        .first(),
    ).toBeVisible();

    page.once("dialog", (dialog) => dialog.accept());
    await page.reload();
    await page.getByRole("button", { name: "Edit", exact: true }).click();
    await expect(
      page.getByText("This record was updated elsewhere", { exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Restore my draft" }).click();
    await expect(
      page.getByRole("textbox", { name: "Record content", exact: true }),
    ).toHaveValue(localDraft);
    await page.getByRole("button", { name: "Update" }).click();
    await expect(page.getByText(localDraft, { exact: true })).toBeVisible();

    await deleteMemoAPI(request, token, created.id);
  });

  test("generates a summary and answers from records through a mock AI provider", async ({
    page,
    request,
  }) => {
    const mockBaseURL = process.env.E2E_MOCK_AI_BASE_URL;
    test.skip(!mockBaseURL, "make check-e2e supplies the mock AI provider");
    const token = await signInAPI(request, PASSWORD);
    await cleanupE2EState(request, token, AI_MARKER);

    await json(
      await request.patch("/api/v1/settings/ai", {
        headers: bearer(token),
        data: {
          profiles: [
            {
              name: "E2E Mock AI",
              provider: "openai",
              baseUrl: mockBaseURL,
              model: "e2e-model",
              temperature: 0.2,
              maxTokens: 1000,
              enabled: true,
              active: true,
              apiKey: "e2e-mock-key",
            },
          ],
        },
      }),
    );
    const memo = await createMemoAPI(
      request,
      token,
      `${AI_MARKER} 最近睡眠更稳定，晚上也更平静。`,
    );

    await signInThroughUI(page, PASSWORD);
    await page.goto(`/entries/${memo.id}`);
    await page.getByRole("button", { name: "Generate summary" }).click();
    await expect(
      page.getByText(
        "mock-summary: this record mentions sleep and a calmer evening.",
        { exact: true },
      ),
    ).toBeVisible();

    await page.goto("/ask");
    await page
      .getByPlaceholder("Ask a question...")
      .fill("最近睡眠状态如何变化？");
    await page.getByRole("button", { name: "Send" }).click();
    await expect(
      page.getByText(
        "According to the selected records, sleep became more stable. [1]",
        { exact: true },
      ),
    ).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("1 source", { exact: true })).toBeVisible();

    await deleteMemoAPI(request, token, memo.id);
  });

  for (const width of [320, 390]) {
    for (const locale of ["zh-CN", "en"] as const) {
      for (const theme of ["light", "dark"] as const) {
        test(`keeps mobile journeys usable at ${width}px in ${locale} ${theme}`, async ({
          page,
          request,
        }) => {
          test.setTimeout(90_000);
          const t = (key: Parameters<typeof translate>[1]) =>
            translate(locale, key);
          const pageErrors: string[] = [];
          page.on("pageerror", (error) => pageErrors.push(error.message));
          const token = await signInAPI(request, PASSWORD);
          await cleanupE2EState(request, token, RESPONSIVE_MARKER);
          const memo = await createMemoAPI(request, token, RESPONSIVE_CONTENT);

          try {
            await page.setViewportSize({ width, height: 844 });
            await page.emulateMedia({ colorScheme: theme });
            await setLocale(page, locale);
            await page.addInitScript((preference) => {
              localStorage.setItem("sillage-theme", preference);
            }, theme);

            await test.step("login fields and action fit the phone", async () => {
              await page.goto("/login");
              await expect(
                page.getByRole("heading", { name: t("auth.loginTitle") }),
              ).toBeVisible();
              await expect(page.locator("html")).toHaveAttribute(
                "lang",
                locale,
              );
              await expect(page.locator("html")).toHaveCSS(
                "color-scheme",
                theme,
              );
              await expectPhoneInput(page.getByLabel(t("auth.account")));
              await expectPhoneInput(
                page.getByLabel(t("auth.password"), { exact: true }),
              );
              await expectTouchTarget(
                page.getByRole("button", {
                  name: t("auth.loginAction"),
                  exact: true,
                }),
              );
              await expectNoHorizontalPageScroll(page);
            });

            await signInThroughUI(page, PASSWORD, locale);
            await test.step("home and mobile navigation remain usable", async () => {
              await expect(
                page.getByText(RESPONSIVE_MARKER).first(),
              ).toBeVisible();
              await expectPhoneInput(
                page.getByRole("textbox", {
                  name: t("editor.contentLabel"),
                  exact: true,
                }),
              );
              await expectTouchTarget(
                page.getByRole("button", {
                  name: t("common.save"),
                  exact: true,
                }),
              );
              await expectNoHorizontalPageScroll(page);

              const openNavigation = page.getByRole("button", {
                name: t("nav.open"),
              });
              await expectTouchTarget(openNavigation);
              await openNavigation.click();
              const navigation = page.getByRole("dialog", {
                name: t("nav.navigation"),
              });
              await expect(navigation).toBeVisible();
              const allRecords = navigation.getByRole("link", {
                name: t("nav.allRecords"),
              });
              await expectTouchTarget(allRecords);
              await expectNoHorizontalPageScroll(page);
              await allRecords.click();
              await expect(navigation).toHaveCount(0);
            });

            await test.step("record search and each list filter fit", async () => {
              const search = page.getByPlaceholder(
                t("timeline.searchPlaceholder"),
              );
              await expectPhoneInput(search);
              await search.fill(RESPONSIVE_MARKER);
              await expect(
                page.getByText(translate(locale, "timeline.foundOne")),
              ).toBeVisible();
              await expect(
                page.getByText(RESPONSIVE_MARKER).first(),
              ).toBeVisible();
              await expectTouchTarget(
                page.getByRole("link", {
                  name: t("timeline.calendar"),
                  exact: true,
                }),
              );
              for (const filterLink of await page
                .getByRole("group", { name: t("timeline.statusLabel") })
                .getByRole("link")
                .all()) {
                await expectTouchTarget(filterLink);
              }
              await expectNoHorizontalPageScroll(page);

              for (const filter of ["archived", "favorite", "deleted"]) {
                await page.goto(
                  `/timeline?filter=${filter}&q=${RESPONSIVE_MARKER}`,
                );
                await expect(
                  page.getByText(
                    translate(locale, "timeline.foundMany", { count: 0 }),
                  ),
                ).toBeVisible();
                await expectPhoneInput(
                  page.getByPlaceholder(t("timeline.searchPlaceholder")),
                );
                await expectNoHorizontalPageScroll(page);
              }
            });

            await test.step("calendar dates can be selected without page overflow", async () => {
              const [year, month] = memo.entryDate.split("-").map(Number);
              await page.goto(`/timeline?view=calendar&y=${year}&m=${month}`);
              const calendar = page.getByRole("table");
              await expect(calendar).toBeVisible();
              const dateLabel = new Intl.DateTimeFormat(locale, {
                year: "numeric",
                month: "long",
                day: "numeric",
              }).format(new Date(`${memo.entryDate}T12:00:00`));
              const day = calendar.getByRole("link", {
                name: new RegExp(`^${dateLabel}`),
              });
              // Seven date columns share a narrow viewport; their height remains
              // generous while width meets the 24px minimum target size.
              await expectTouchTarget(day, 24);
              await expectNoHorizontalPageScroll(page);
              await day.click();
              await expect(page).toHaveURL(
                new RegExp(`date=${memo.entryDate}`),
              );
              await expect(
                page.getByText(RESPONSIVE_MARKER).first(),
              ).toBeVisible();
              await expectNoHorizontalPageScroll(page);
            });

            await test.step("long record content scrolls locally in detail and preview", async () => {
              await page.goto(`/entries/${memo.id}`);
              await expect(
                page.getByRole("heading", { name: RESPONSIVE_MARKER }),
              ).toBeVisible();
              const longParagraph = page.getByText(RESPONSIVE_CONTINUOUS_TEXT, {
                exact: true,
              });
              await expect(longParagraph).toBeVisible();
              const paragraphOverflow = await longParagraph.evaluate(
                (element) => element.scrollWidth - element.clientWidth,
              );
              expect(
                paragraphOverflow,
                "continuous text should wrap without clipping",
              ).toBeLessThanOrEqual(1);
              await expectNoHorizontalPageScroll(page);
              await expectLocalHorizontalScroll(page.locator("article pre"));
              await expectLocalHorizontalScroll(page.locator("article table"));
              await expectNoHorizontalPageScroll(page);
              if (width === 390 && locale === "en") {
                await page.screenshot({
                  path: test.info().outputPath("mobile-record.png"),
                  fullPage: true,
                });
              }

              const edit = page.getByRole("button", {
                name: t("common.edit"),
                exact: true,
              });
              await expectTouchTarget(edit);
              await edit.click();
              await expectPhoneInput(
                page.getByRole("textbox", {
                  name: t("editor.contentLabel"),
                  exact: true,
                }),
              );
              await page
                .getByRole("button", { name: t("editor.preview"), exact: true })
                .click();
              await expect(
                page.getByRole("heading", { name: RESPONSIVE_MARKER }),
              ).toBeVisible();
              await expectNoHorizontalPageScroll(page);
              await expectLocalHorizontalScroll(page.locator("main pre"));
              await expectLocalHorizontalScroll(page.locator("main table"));
              await page
                .getByRole("button", { name: t("common.cancel"), exact: true })
                .click();
            });

            await test.step("question composer and source selectors fit", async () => {
              await page.goto("/ask");
              const question = page.getByPlaceholder(t("ask.placeholder"));
              await expectPhoneInput(question);
              await question.fill(`Mobile question ${"x".repeat(300)}`);
              await expect(question).toHaveValue(
                `Mobile question ${"x".repeat(300)}`,
              );
              await expectPhoneInput(
                page.getByRole("combobox", {
                  name: t("ask.source"),
                  exact: true,
                }),
              );
              await expectPhoneInput(
                page.getByRole("combobox", {
                  name: t("ask.range"),
                  exact: true,
                }),
              );
              await expectTouchTarget(
                page.getByRole("button", { name: t("ask.send"), exact: true }),
              );
              await expectNoHorizontalPageScroll(page);
            });

            await test.step("AI, account, and appearance settings fit", async () => {
              await page.goto("/settings");
              await expect(
                page.getByRole("heading", {
                  name: t("settings.title"),
                  exact: true,
                }),
              ).toBeVisible();
              const addProfile = page.getByRole("button", {
                name: t("settings.addProfile"),
                exact: true,
              });
              await expectTouchTarget(addProfile);
              const configure = page
                .getByRole("button", {
                  name: t("settings.configure"),
                  exact: true,
                })
                .first();
              if (await configure.count()) {
                await configure.click();
              } else {
                await addProfile.click();
              }
              const settingsInputs = page.locator(
                'main input:not([type="checkbox"]):enabled:visible, main select:enabled:visible',
              );
              await expect(settingsInputs.first()).toBeVisible();
              for (const input of await settingsInputs.all()) {
                await expectPhoneInput(input);
              }
              await expectNoHorizontalPageScroll(page);

              const account = page.getByRole("button", {
                name: t("settings.accountTab"),
                exact: true,
              });
              await expectTouchTarget(account);
              await account.click();
              await expectPhoneInput(
                page.getByLabel(t("settings.currentPassword")),
              );
              await expectTouchTarget(
                page.getByRole("button", {
                  name: t("settings.changePassword"),
                  exact: true,
                }),
              );
              await expectNoHorizontalPageScroll(page);

              const appearance = page.getByRole("button", {
                name: t("settings.appearanceTab"),
                exact: true,
              });
              await expectTouchTarget(appearance);
              await appearance.click();
              await expect(
                page.getByRole("heading", {
                  name: t("theme.sectionTitle"),
                  exact: true,
                }),
              ).toBeVisible();
              await expectTouchTarget(
                page.getByRole("button", {
                  name: t("language.english"),
                  exact: true,
                }),
              );
              await expectNoHorizontalPageScroll(page);
            });
            expect(
              pageErrors,
              "mobile journeys should have no page errors",
            ).toEqual([]);
          } finally {
            await cleanupE2EState(request, token, RESPONSIVE_MARKER);
          }
        });
      }
    }
  }

  test("changing the password revokes another browser session", async ({
    browser,
    request,
  }) => {
    const primaryContext = await browser.newContext({ baseURL: BASE_URL });
    const secondaryContext = await browser.newContext({ baseURL: BASE_URL });
    const verificationContext = await browser.newContext({ baseURL: BASE_URL });
    try {
      const primary = await primaryContext.newPage();
      const secondary = await secondaryContext.newPage();
      const verification = await verificationContext.newPage();
      await signInThroughUI(primary, PASSWORD);
      await signInThroughUI(secondary, PASSWORD);

      await primary.goto("/settings");
      await expect(
        primary.getByRole("heading", { name: "Version and compatibility" }),
      ).toBeVisible();
      await expect(
        primary.getByText("Server build", { exact: true }),
      ).toBeVisible();
      await expect(
        primary.getByText("Web build", { exact: true }),
      ).toBeVisible();
      await expect(
        primary.getByText("API version", { exact: true }),
      ).toBeVisible();
      await primary.getByRole("button", { name: "Account" }).click();
      await changePasswordThroughUI(primary, PASSWORD, UPDATED_PASSWORD);

      // Reload clears the memory-only access token and forces the revoked
      // refresh session to prove it can no longer restore authentication.
      await secondary.reload();
      await expect(
        secondary.getByRole("heading", { name: "Sign in to Sillage" }),
      ).toBeVisible();

      await setEnglishLocale(verification);
      await verification.goto("/login");
      await verification.getByLabel("Username").fill(USERNAME);
      await verification.getByLabel("Password", { exact: true }).fill(PASSWORD);
      await verification.getByRole("button", { name: "Sign in" }).click();
      await expect(
        verification.getByRole("alert").filter({
          hasText: "Incorrect account or password",
        }),
      ).toBeVisible();
      await verification
        .getByLabel("Password", { exact: true })
        .fill(UPDATED_PASSWORD);
      await verification.getByRole("button", { name: "Sign in" }).click();
      await expect(
        verification.getByPlaceholder("Write what you want to remember..."),
      ).toBeVisible();

      // Restore the suite baseline so retries and local reruns remain safe.
      await changePasswordThroughUI(primary, UPDATED_PASSWORD, PASSWORD);
      const finalContext = await browser.newContext({ baseURL: BASE_URL });
      try {
        await signInThroughUI(await finalContext.newPage(), PASSWORD);
      } finally {
        await finalContext.close();
      }
    } finally {
      await ensureBaselinePassword(request);
      await Promise.all([
        primaryContext.close(),
        secondaryContext.close(),
        verificationContext.close(),
      ]);
    }
  });
});

async function setEnglishLocale(page: Page) {
  await setLocale(page, "en");
}

async function setLocale(page: Page, locale: Locale) {
  await page.addInitScript((language) => {
    localStorage.setItem("sillage-language", language);
  }, locale);
}

async function signInThroughUI(
  page: Page,
  password: string,
  locale: Locale = "en",
) {
  await setLocale(page, locale);
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { name: translate(locale, "auth.loginTitle") }),
  ).toBeVisible();
  await page.getByLabel(translate(locale, "auth.account")).fill(USERNAME);
  await page
    .getByLabel(translate(locale, "auth.password"), { exact: true })
    .fill(password);
  await page
    .getByRole("button", {
      name: translate(locale, "auth.loginAction"),
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("textbox", {
      name: translate(locale, "editor.contentLabel"),
      exact: true,
    }),
  ).toBeVisible();
}

async function expectNoHorizontalPageScroll(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }));
  expect(
    dimensions.document,
    `document overflow on ${page.url()}`,
  ).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.body, `body overflow on ${page.url()}`).toBeLessThanOrEqual(
    dimensions.viewport + 1,
  );
}

async function expectTouchTarget(target: Locator, minimumWidth = 44) {
  await expect(target).toBeVisible();
  const dimensions = await target.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return {
      // Fractional viewport scaling can report 43.99997 for a 44px target.
      width: Number(rect.width.toFixed(3)),
      height: Number(rect.height.toFixed(3)),
      left: rect.left,
      right: rect.right,
      viewport: document.documentElement.clientWidth,
    };
  });
  expect(dimensions.width, "touch target width").toBeGreaterThanOrEqual(
    minimumWidth,
  );
  expect(dimensions.height, "touch target height").toBeGreaterThanOrEqual(44);
  expect(dimensions.left, "touch target left edge").toBeGreaterThanOrEqual(-1);
  expect(dimensions.right, "touch target right edge").toBeLessThanOrEqual(
    dimensions.viewport + 1,
  );
}

async function expectPhoneInput(input: Locator) {
  await expectTouchTarget(input);
  const fontSize = await input.evaluate((element) =>
    Number.parseFloat(getComputedStyle(element).fontSize),
  );
  expect(
    fontSize,
    "phone inputs should not trigger Safari focus zoom",
  ).toBeGreaterThanOrEqual(16);
}

async function expectLocalHorizontalScroll(content: Locator) {
  await expect(content).toBeVisible();
  await content.scrollIntoViewIfNeeded();
  const result = await content.evaluate((element) => {
    let container: HTMLElement | null = element as HTMLElement;
    while (
      container &&
      container !== document.body &&
      container !== document.documentElement
    ) {
      const overflow = getComputedStyle(container).overflowX;
      if (
        (overflow === "auto" || overflow === "scroll") &&
        container.scrollWidth > container.clientWidth + 1
      ) {
        container.scrollLeft = 80;
        const rect = container.getBoundingClientRect();
        return {
          scrollLeft: container.scrollLeft,
          left: rect.left,
          right: rect.right,
          viewport: document.documentElement.clientWidth,
        };
      }
      container = container.parentElement;
    }
    return null;
  });
  expect(
    result,
    "wide Markdown content must have a local horizontal scroller",
  ).not.toBeNull();
  expect(
    result?.scrollLeft,
    "wide Markdown content remains reachable by scrolling",
  ).toBeGreaterThan(0);
  expect(result?.left).toBeGreaterThanOrEqual(-1);
  expect(result?.right).toBeLessThanOrEqual((result?.viewport ?? 0) + 1);
}

async function signInAPI(request: APIRequestContext, password: string) {
  const payload = await json<AuthPayload>(
    await request.post("/api/v1/auth/signin", {
      data: { username: USERNAME, password },
    }),
  );
  return payload.accessToken;
}

async function trySignInAPI(
  request: APIRequestContext,
  password: string,
): Promise<string | null> {
  const response = await request.post("/api/v1/auth/signin", {
    data: { username: USERNAME, password },
  });
  if (!response.ok()) {
    return null;
  }
  return ((await response.json()) as AuthPayload).accessToken;
}

async function ensureBaselinePassword(request: APIRequestContext) {
  if (await trySignInAPI(request, PASSWORD)) {
    return;
  }
  const token = await trySignInAPI(request, UPDATED_PASSWORD);
  if (!token) {
    throw new Error("could not restore the E2E account password baseline");
  }
  await json(
    await request.post("/api/v1/auth/change-password", {
      headers: bearer(token),
      data: {
        currentPassword: UPDATED_PASSWORD,
        newPassword: PASSWORD,
      },
    }),
  );
}

async function changePasswordThroughUI(
  page: Page,
  currentPassword: string,
  newPassword: string,
) {
  await page.getByLabel("Current password").fill(currentPassword);
  await page.getByLabel("New password", { exact: true }).fill(newPassword);
  await page.getByLabel("Confirm new password").fill(newPassword);
  const responsePromise = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/v1/auth/change-password") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Save new password" }).click();
  const response = await responsePromise;
  expect(response.ok()).toBeTruthy();
  await expect(
    page.getByRole("status").filter({ hasText: "Password updated" }),
  ).toBeVisible();
}

async function createMemoAPI(
  request: APIRequestContext,
  token: string,
  content: string,
): Promise<Memo> {
  const payload = await json<{ memo: Memo }>(
    await request.post("/api/v1/memos", {
      headers: bearer(token),
      data: {
        content,
        entryDate: new Date().toISOString().slice(0, 10),
      },
    }),
  );
  return payload.memo;
}

async function deleteMemoAPI(
  request: APIRequestContext,
  token: string,
  memoID: string,
) {
  const detail = await request.get(`/api/v1/memos/${memoID}`, {
    headers: bearer(token),
  });
  if (detail.status() === 404) {
    return;
  }
  const { memo } = await json<{ memo: Memo }>(detail);
  await json(
    await request.delete(
      `/api/v1/memos/${memo.id}?expectedVersion=${memo.version}`,
      { headers: bearer(token) },
    ),
  );
}

async function cleanupE2EState(
  request: APIRequestContext,
  token: string,
  marker: string,
) {
  for (const archived of [false, true]) {
    const response = await json<{ memos: Memo[] }>(
      await request.get(
        `/api/v1/memos?query=${encodeURIComponent(marker)}&limit=100&archived=${archived}`,
        { headers: bearer(token) },
      ),
    );
    for (const memo of response.memos) {
      await deleteMemoAPI(request, token, memo.id);
    }
  }

  const deleted = await json<{ memos: Memo[] }>(
    await request.get(
      `/api/v1/memos?query=${encodeURIComponent(marker)}&limit=100&deleted=true`,
      { headers: bearer(token) },
    ),
  );
  for (const memo of deleted.memos) {
    await json(
      await request.post(`/api/v1/memos/${memo.id}:purge`, {
        headers: bearer(token),
        data: { expectedVersion: memo.version },
      }),
    );
  }

  const sync = await json<{
    attachments: Array<{
      uid: string;
      filename: string;
      deletedAt: string | null;
    }>;
  }>(
    await request.get("/api/v1/sync?limit=500", {
      headers: bearer(token),
    }),
  );
  for (const attachment of sync.attachments) {
    if (attachment.filename === ATTACHMENT_FILENAME && !attachment.deletedAt) {
      await request.delete(`/api/v1/attachments/${attachment.uid}`, {
        headers: bearer(token),
      });
    }
  }
}

function extractAttachmentURL(markdown: string) {
  const match = markdown.match(/\((\/file\/attachments\/[^)]+)\)/);
  if (!match) {
    throw new Error(`uploaded attachment URL missing from draft: ${markdown}`);
  }
  return match[1];
}

function entryIDFromURL(url: string) {
  const match = new URL(url).pathname.match(/^\/entries\/([^/]+)$/);
  if (!match) {
    throw new Error(`not on an entry detail route: ${url}`);
  }
  return match[1];
}

function bearer(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function json<T>(response: APIResponse): Promise<T> {
  if (!response.ok()) {
    throw new Error(
      `${response.url()} returned ${response.status()}: ${await response.text()}`,
    );
  }
  return response.json() as Promise<T>;
}
