import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// Deterministic UI fixtures; backend ownership is covered by Java integration tests.
const key = `usk_${"a".repeat(24)}_${"b".repeat(64)}`;
for (const width of [375, 1440]) {
  test(`ownerManagement_accessPaginationAndClearing_${width}px`, async ({
    page,
  }) => {
    let calls = 0;
    await page.route("**/api/v2/urls?*", async (route) => {
      calls++;
      expect(route.request().headers()["x-api-key"]).toBe(key);
      const pageNumber = Number(
        new URL(route.request().url()).searchParams.get("page"),
      );
      await route.fulfill({
        json: {
          content: [
            {
              shortCode: `owned${pageNumber}`,
              originalUrl: "https://example.com/" + "a".repeat(200),
              clickCount: 3,
            },
          ],
          page: pageNumber,
          size: 20,
          totalElements: 21,
        },
      });
    });
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/#/links?mode=v2");
    await expect(
      page.getByRole("heading", { name: "API key required" }),
    ).toBeVisible();
    expect(calls).toBe(0);
    await page.getByText(/API access ·/).click();
    await page.getByLabel("Management API key").fill(key);
    await page
      .getByRole("button", { name: "Use API key", exact: true })
      .click();
    await expect(page.getByRole("link", { name: "owned0" })).toBeVisible();
    await page.getByRole("button", { name: "Next page" }).click();
    await expect(page.getByRole("link", { name: "owned1" })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    const accessibility = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(
      accessibility.violations.filter(
        ({ impact }) => impact === "serious" || impact === "critical",
      ),
    ).toEqual([]);
    expect(
      await page.evaluate(() => JSON.stringify([localStorage, sessionStorage])),
    ).not.toContain(key);
    await page
      .getByRole("button", { name: "Clear API key", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "API key required" }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "owned1" })).toHaveCount(0);
    expect(calls).toBe(2);
  });
}
