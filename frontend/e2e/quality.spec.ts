import { test, expect, type Page, type Locator } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { randomUUID } from "node:crypto";

const codes: string[] = [];
test.afterEach(async ({ request }) => {
  for (const code of codes.splice(0))
    await request.delete(`/api/v1/urls/${code}`);
});
async function tabTo(page: Page, target: Locator) {
  for (let attempt = 0; attempt < 60; attempt++) {
    if (await target.evaluate((element) => element === document.activeElement))
      return;
    await page.keyboard.press("Tab");
  }
  throw new Error("Keyboard focus could not reach the target");
}

for (const width of [320, 375, 768, 1024, 1440, 1920]) {
  test(`responsive_createAndAnalytics_${width}px`, async ({
    page,
    request,
  }) => {
    const code = "qa" + randomUUID().replaceAll("-", "").slice(0, 14);
    expect(
      (
        await request.post("/api/v1/urls", {
          data: {
            originalUrl: "https://example.com/" + "a".repeat(200),
            customAlias: code,
          },
        })
      ).status(),
    ).toBe(201);
    codes.push(code);
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/");
    await page.getByText("Customize your link", { exact: false }).click();
    await expect(page.getByLabel("Expiry (optional)")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page.goto(`/#/analytics?code=${code}`);
    await expect(
      page.getByRole("heading", { name: `Details for ${code}` }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page
      .getByRole("button", { name: "Delete link", exact: true })
      .click();
    await expect(page.getByRole("dialog")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page.keyboard.press("Escape");
    await expect(
      page.getByRole("button", { name: "Delete link", exact: true }),
    ).toBeFocused();
  });
}

for (const theme of ["light", "dark"] as const) {
  test(`accessibility_createAnalyticsAndModal_${theme}`, async ({
    page,
    request,
  }) => {
    const code = "qa" + randomUUID().replaceAll("-", "").slice(0, 14);
    expect(
      (
        await request.post("/api/v1/urls", {
          data: { originalUrl: "https://example.com/", customAlias: code },
        })
      ).status(),
    ).toBe(201);
    codes.push(code);
    await page.emulateMedia({ colorScheme: theme, reducedMotion: "reduce" });
    const scan = async () => {
      const result = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze();
      expect(
        result.violations.filter((item) =>
          ["serious", "critical"].includes(item.impact || ""),
        ),
      ).toEqual([]);
    };
    await page.goto("/");
    await expect(page.getByLabel("Destination URL")).toBeVisible();
    await scan();
    await page.goto(`/#/analytics?code=${code}`);
    await expect(page.getByText("No visits yet.")).toBeVisible();
    await scan();
    await page
      .getByRole("button", { name: "Delete link", exact: true })
      .click();
    await scan();
  });
}

test("keyboardOnly_createCopyAnalyticsAndConfirmedDelete", async ({
  page,
  context,
  request,
}) => {
  const code = "qa" + randomUUID().replaceAll("-", "").slice(0, 14);
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  await page.goto("/");
  await tabTo(page, page.getByLabel("Destination URL"));
  await page.keyboard.type("https://example.com/keyboard");
  await tabTo(page, page.locator("summary"));
  await page.keyboard.press("Enter");
  await tabTo(page, page.getByLabel("Custom alias (optional)"));
  await page.keyboard.type(code);
  await tabTo(
    page,
    page.getByRole("button", { name: "Shorten link", exact: true }),
  );
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "Your short link is ready." }),
  ).toBeFocused();
  codes.push(code);
  await tabTo(
    page,
    page.getByRole("button", { name: "Copy link", exact: true }),
  );
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("status").filter({ hasText: "copied to clipboard" }),
  ).toBeVisible();
  await tabTo(page, page.getByRole("link", { name: /View analytics/ }));
  const [analytics] = await Promise.all([
    context.waitForEvent("page"),
    page.keyboard.press("Enter"),
  ]);
  await expect(
    analytics.getByRole("heading", { name: `Details for ${code}` }),
  ).toBeVisible();
  await tabTo(analytics, analytics.getByLabel("Short code"));
  await analytics.keyboard.press("ControlOrMeta+A");
  await analytics.keyboard.type(code);
  await analytics.keyboard.press("Enter");
  await expect(analytics.getByText("No visits yet.")).toBeVisible();
  expect(
    (await (await request.get(`/api/v1/urls/${code}`)).json()).clickCount,
  ).toBe(0);
  const trigger = analytics.getByRole("button", {
    name: "Delete link",
    exact: true,
  });
  await tabTo(analytics, trigger);
  await analytics.keyboard.press("Enter");
  const close = analytics.getByRole("button", { name: "Close dialog" });
  await expect(close).toBeFocused();
  await analytics.keyboard.press("Shift+Tab");
  await expect(
    analytics.getByRole("button", { name: "Delete permanently" }),
  ).toBeFocused();
  await analytics.keyboard.press("Tab");
  await expect(close).toBeFocused();
  await analytics.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await analytics.keyboard.press("Enter");
  await tabTo(
    analytics,
    analytics.getByRole("button", { name: "Cancel", exact: true }),
  );
  await analytics.keyboard.press("Enter");
  expect((await request.get(`/api/v1/urls/${code}`)).status()).toBe(200);
  await expect(trigger).toBeFocused();
  await analytics.keyboard.press("Enter");
  await tabTo(
    analytics,
    analytics.getByRole("button", { name: "Delete permanently" }),
  );
  await analytics.keyboard.press("Enter");
  await expect(
    analytics.getByRole("status").filter({ hasText: "permanently deleted" }),
  ).toBeVisible();
  await expect(analytics.getByLabel("Short code")).toBeFocused();
  expect((await request.get(`/api/v1/urls/${code}`)).status()).toBe(404);
});

test("csp_blocksInlineScriptAndSkipLinkPreservesAnalyticsRoute", async ({
  page,
}) => {
  // Inject through the HTML response, not DevTools evaluation (which has script privileges).
  await page.route("**/", async (route) => {
    const response = await route.fetch();
    await route.fulfill({
      response,
      body: (await response.text()).replace(
        "</body>",
        "<script>window.unsafeInlineExecuted = true</script></body>",
      ),
    });
  });
  const response = await page.goto("/#/analytics");
  expect(response?.headers()["content-security-policy"]).toContain(
    "'strict-dynamic'",
  );
  expect(await page.evaluate(() => "unsafeInlineExecuted" in window)).toBe(
    false,
  );
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("link", { name: "Skip to content" }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await expect(page).toHaveURL(/#\/analytics$/);
  await page.emulateMedia({ reducedMotion: "reduce" });
  expect(
    await page.evaluate(
      () => getComputedStyle(document.documentElement).scrollBehavior,
    ),
  ).toBe("auto");
});

for (const fault of [
  "offline",
  "timeout",
  "malformed",
  "429",
  "503",
] as const) {
  test(`network_${fault}_preservesInputAndExplainsRecovery`, async ({
    page,
    context,
  }) => {
    await page.goto("/");
    await expect(page.getByLabel("Destination URL")).toBeVisible();
    if (fault === "offline") await context.setOffline(true);
    else
      await page.route("**/api/v1/urls", (route) => {
        if (fault === "timeout") return;
        return route.fulfill({
          status: fault === "malformed" ? 201 : Number(fault),
          body: "private stack trace",
          contentType: "text/plain",
        });
      });
    await page.getByLabel("Destination URL").fill("https://example.com/keep");
    await page
      .getByRole("button", { name: "Shorten link", exact: true })
      .click();
    const text = {
      offline: "offline",
      timeout: "too long",
      malformed: "unexpected response",
      "429": "Too many requests",
      "503": "temporarily unavailable",
    }[fault];
    await expect(page.getByRole("alert")).toContainText(text);
    await expect(page.getByLabel("Destination URL")).toHaveValue(
      "https://example.com/keep",
    );
    await expect(page.getByRole("alert")).not.toContainText(
      "private stack trace",
    );
  });
}

test("create_loadingAndResult_reserveSpaceAndRemainAccessible", async ({
  page,
}) => {
  await page.goto("/");
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/urls", async (route) => {
    await gate;
    await route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        shortCode: "example",
        shortUrl: "https://links.example/example",
        originalUrl: "https://example.com/",
        createdAt: "2026-01-01T00:00:00Z",
        expiresAt: null,
      }),
    });
  });
  await page.getByLabel("Destination URL").fill("https://example.com/");
  const card = page.locator(".creation-card");
  const initial = (await card.boundingBox())!.height;
  await page.getByRole("button", { name: "Shorten link", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /Creating your link/ }),
  ).toBeDisabled();
  expect((await card.boundingBox())!.height).toBe(initial);
  release();
  await expect(
    page.getByRole("heading", { name: "Your short link is ready." }),
  ).toBeVisible();
  expect((await card.boundingBox())!.height).toBe(initial);
  const scan = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(
    scan.violations.filter((item) =>
      ["serious", "critical"].includes(item.impact || ""),
    ),
  ).toEqual([]);
});
