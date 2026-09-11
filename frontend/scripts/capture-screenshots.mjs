import { chromium } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const output = new URL("../../docs/screenshots/", import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH,
});
try {
  for (const [name, width, height] of [
    ["desktop", 1440, 1100],
    ["mobile", 375, 900],
  ]) {
    const page = await browser.newPage({
      viewport: { width, height },
      colorScheme: "light",
      reducedMotion: "reduce",
    });
    await page.goto(process.env.E2E_BASE_URL || "http://127.0.0.1:8080");
    await page.getByLabel("Destination URL").waitFor();
    await page
      .getByRole("button", { name: "Shorten link", exact: true })
      .waitFor({ state: "visible" });
    await page.screenshot({
      path: fileURLToPath(new URL(`${name}.png`, output)),
      fullPage: true,
      animations: "disabled",
    });
    await page.close();
  }
} finally {
  await browser.close();
}
