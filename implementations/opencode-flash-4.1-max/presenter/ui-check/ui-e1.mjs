// Browser check for the exercise 1 reference solution: the availability checkbox
// filters the session table through the API. Requires the patched exported app at BASE_URL.
import { chromium } from "playwright";
import assert from "node:assert/strict";

const BASE = process.env.BASE_URL ?? "http://localhost:8080";

const browser = await chromium.launch();
const page = await browser.newPage();

async function rowTitles() {
  const rows = await page.locator("#sessions-table tbody tr").all();
  return Promise.all(rows.map((row) => row.locator("td").first().textContent()));
}

await page.goto(BASE);
await page.waitForFunction(
  () => document.querySelector("#sessions-status")?.textContent?.includes("3 session(s)"),
  undefined,
  { timeout: 15000 },
);

assert.deepEqual(await rowTitles(), [
  "Debugging Distributed Systems",
  "Practical Kotlin",
  "Understanding Databases",
]);

await page.locator("#available-only").check();
await page.waitForFunction(
  () => document.querySelectorAll("#sessions-table tbody tr").length === 2,
  undefined,
  { timeout: 15000 },
);
assert.deepEqual(await rowTitles(), ["Debugging Distributed Systems", "Practical Kotlin"]);

await page.locator("#available-only").uncheck();
await page.waitForFunction(
  () => document.querySelectorAll("#sessions-table tbody tr").length === 3,
  undefined,
  { timeout: 15000 },
);
assert.deepEqual(await rowTitles(), [
  "Debugging Distributed Systems",
  "Practical Kotlin",
  "Understanding Databases",
]);

await browser.close();
console.log("EXERCISE 1 CHECKBOX UI CHECK PASSED");
