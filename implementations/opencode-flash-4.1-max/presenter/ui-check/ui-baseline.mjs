// Baseline UI check for the Conference Planner (presenter-only tooling).
// Requires the app to be running at BASE_URL (default http://localhost:8080).
import { chromium } from "playwright";
import assert from "node:assert/strict";

const BASE = process.env.BASE_URL ?? "http://localhost:8080";

const browser = await chromium.launch();
const page = await browser.newPage();
const failures = [];

async function waitForText(selector, expected, timeout = 15000) {
  await page.waitForFunction(
    ([sel, text]) => document.querySelector(sel)?.textContent?.includes(text),
    [selector, expected],
    { timeout },
  );
}

async function waitForRowText(index, expected, timeout = 15000) {
  await page.waitForFunction(
    ([i, text]) => document.querySelectorAll("#sessions-table tbody tr")[i]?.textContent?.includes(text),
    [index, expected],
    { timeout },
  );
}

async function sessionRows() {
  return page.locator("#sessions-table tbody tr").all();
}

async function rowText(index) {
  return (await sessionRows())[index].textContent();
}

async function bookRow(index) {
  await (await sessionRows())[index].locator("button").click();
}

async function openView(name) {
  await page.locator(`.tab[data-view='${name}']`).click();
}

async function step(name, fn) {
  try {
    await fn();
    console.log(`PASS ${name}`);
  } catch (error) {
    failures.push(name);
    console.error(`FAIL ${name}: ${error.message}`);
  }
}

await step("page loads with three seed sessions", async () => {
  await page.goto(BASE);
  await waitForText("#sessions-status", "3 session(s)");
  const rows = await sessionRows();
  assert.equal(rows.length, 3);
  assert.match(await rowText(0), /Debugging Distributed Systems.*Room A.*30.*22.*8/s);
  assert.match(await rowText(1), /Practical Kotlin.*Room B.*20.*19.*1/s);
  assert.match(await rowText(2), /Understanding Databases.*Room C.*40.*40.*0/s);
  assert.equal(await (await sessionRows())[2].locator("button").isDisabled(), true);
});

await step("reset brings back the deterministic seed", async () => {
  await openView("demo");
  await page.locator("#reset-demo").click();
  await waitForText("#toast", "Demo data reset");
  await openView("sessions");
  await waitForRowText(0, "22");
  assert.match(await rowText(0), /30.*22.*8/s);
});

await step("Alice books a place and sees it in My bookings", async () => {
  await bookRow(0);
  await waitForText("#toast", "Booked");
  await waitForRowText(0, "23");

  await openView("bookings");
  await waitForText("#bookings-status", "1 active booking");
  assert.match(await page.locator("#bookings-table tbody tr").first().textContent(), /Debugging Distributed Systems/);
  assert.equal(await page.locator("#booking-count").textContent(), "1");
});

await step("duplicate booking surfaces the documented error state", async () => {
  await openView("sessions");
  await bookRow(0);
  await waitForText("#banner", "DUPLICATE_BOOKING");
  assert.match(await page.locator("#banner").textContent(), /already has an active booking/i);
});

await step("cancel releases the place", async () => {
  await openView("bookings");
  await page.locator("#bookings-table tbody tr button").first().click();
  await waitForText("#toast", "Cancelled");
  await waitForText("#bookings-status", "No active bookings");
  await openView("sessions");
  await waitForRowText(0, "22");
  assert.match(await rowText(0), /30.*22.*8/s);
});

await step("switching demo identity switches the booking scope", async () => {
  await page.selectOption("#identity", "Bob");
  await waitForText("#toast", "identity switched to Bob");
  await openView("bookings");
  await waitForText("#bookings-status", "No active bookings for Bob");
});

await step("demo panel demonstrates a deduplicated retry", async () => {
  await openView("sessions");
  await page.selectOption("#identity", "Alice");
  await waitForText("#toast", "identity switched to Alice");

  await openView("demo");
  await page.locator("#reset-demo").click();
  await waitForText("#toast", "Demo data reset");
  await openView("sessions");
  await waitForRowText(0, "22");
  await bookRow(0);
  await waitForText("#toast", "Booked");

  await openView("demo");
  await page.locator("#failure-toggle").check();
  await page.locator("#dispatch").click();
  await waitForText("#toast", "1 timed out");
  await waitForText("#demo-status", "Pending confirmations: 1");
  await waitForText("#notifications-table", "PENDING");
  const notification = () => page.locator("#notifications-table tbody tr").first();
  assert.match(await notification().textContent(), /PENDING.*1.*1/s);

  await page.locator("#dispatch").click();
  await waitForText("#toast", "1 acknowledged");
  await waitForText("#demo-status", "Pending confirmations: 0");
  assert.match(await notification().textContent(), /ACKNOWLEDGED.*2.*1/s);
  await waitForText("#activity-list", "DELIVERY_TIMEOUT");
  await waitForText("#activity-list", "DELIVERY_ACKNOWLEDGED");
});

await step("final reset leaves the seed counts", async () => {
  await page.locator("#reset-demo").click();
  await waitForText("#toast", "Demo data reset");
  await openView("sessions");
  await waitForRowText(1, "19");
  assert.match(await rowText(1), /20.*19.*1/s);
});

await browser.close();

if (failures.length > 0) {
  console.error(`\nUI CHECK FAILED (${failures.length} step(s)): ${failures.join(", ")}`);
  process.exit(1);
}
console.log("\nBASELINE UI CHECKS PASSED");
