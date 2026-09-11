import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";

// Each worker owns only links it successfully created; no shared database resets.
const codes: string[] = [];
const alias = () => "qa" + randomUUID().replaceAll("-", "").slice(0, 14);
test.afterEach(async ({ request }) => {
  for (const code of codes.splice(0)) {
    const response = await request.delete(`/api/v1/urls/${code}`);
    expect([204, 404]).toContain(response.status());
  }
});

test("userCreatesShortUrlAndOpensRedirect", async ({
  page,
  context,
  request,
  baseURL,
}) => {
  // The destination is the app itself, so the test needs no third-party website.
  const destination = `${baseURL}/?destination=${randomUUID()}`;
  await page.goto("/");
  await page.getByLabel("Destination URL").fill(destination);
  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/v1/urls") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Shorten link", exact: true }).click();
  const response = await created;
  expect(response.status()).toBe(201);
  const result = await response.json();
  codes.push(result.shortCode);
  expect(new URL(result.shortUrl).origin).toBe(new URL(baseURL!).origin);
  await expect(
    page.getByRole("heading", { name: "Your short link is ready." }),
  ).toBeVisible();
  const [destinationPage] = await Promise.all([
    context.waitForEvent("page"),
    page.getByRole("link", { name: /Open link/ }).click(),
  ]);
  await expect(destinationPage).toHaveURL(destination);
  await expect(destinationPage.getByLabel("Destination URL")).toBeVisible();
  const stats = await request.get(`/api/v1/urls/${result.shortCode}`);
  expect(stats.status()).toBe(200);
  expect((await stats.json()).clickCount).toBe(1);
});

test("userLooksUpAnalyticsAndDeletesLink", async ({ page, request }) => {
  const code = alias();
  const created = await request.post("/api/v1/urls", {
    data: { originalUrl: "https://example.com/release", customAlias: code },
  });
  expect(created.status()).toBe(201);
  codes.push(code);
  await page.goto("/#/analytics");
  await page.getByLabel("Short code").fill(code);
  await page.getByRole("button", { name: /Look up/ }).click();
  await expect(
    page.getByRole("heading", { name: `Details for ${code}` }),
  ).toBeVisible();
  await expect(page.getByText("No visits yet.")).toBeVisible();
  await page.getByRole("button", { name: "Delete link", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText(code);
  await page.getByRole("button", { name: "Delete permanently" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "permanently deleted" }),
  ).toBeVisible();
  expect((await request.get(`/api/v1/urls/${code}`)).status()).toBe(404);
  expect((await request.get(`/${code}`, { maxRedirects: 0 })).status()).toBe(
    404,
  );
});

test("duplicateAliasDisplaysActionableError", async ({ page, request }) => {
  const code = alias();
  const originalUrl = "https://example.com/original";
  const created = await request.post("/api/v1/urls", {
    data: { originalUrl, customAlias: code },
  });
  expect(created.status()).toBe(201);
  codes.push(code);
  await page.goto("/");
  await page.getByLabel("Destination URL").fill("https://example.com/new");
  await page.locator("summary").click();
  await page.getByLabel("Custom alias (optional)").fill(code);
  await page.getByRole("button", { name: "Shorten link", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Choose a different alias",
  );
  await expect(
    page.getByText("This alias is already taken. Choose another one."),
  ).toBeVisible();
  await expect(page.getByLabel("Destination URL")).toHaveValue(
    "https://example.com/new",
  );
  const replacement = alias();
  await page.getByLabel("Custom alias (optional)").fill(replacement);
  await page.getByRole("button", { name: "Shorten link", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Your short link is ready." }),
  ).toBeVisible();
  codes.push(replacement);
  const original = await request.get(`/api/v1/urls/${code}`);
  expect((await original.json()).originalUrl).toBe(originalUrl);
});

test("containerSmoke_sameOriginServesFrontendAnalyticsAndApi", async ({
  page,
  request,
  baseURL,
}) => {
  const home = await page.goto("/");
  expect(home?.status()).toBe(200);
  await expect(page.getByLabel("Destination URL")).toBeVisible();
  await page.getByRole("link", { name: "Analytics", exact: true }).click();
  await expect(page).toHaveURL(`${baseURL}/#/analytics`);
  await expect(page.getByLabel("Short code")).toBeVisible();
  const code = alias();
  const response = await request.post("/api/v1/urls", {
    data: { originalUrl: "https://example.com/smoke", customAlias: code },
  });
  expect(response.status()).toBe(201);
  codes.push(code);
  expect(new URL((await response.json()).shortUrl).origin).toBe(
    new URL(baseURL!).origin,
  );
});
