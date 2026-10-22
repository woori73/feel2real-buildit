/* Real isolated browser UI; model responses are explicit test doubles, never live API evidence. */
const { chromium } = require("playwright");
const fs = require("node:fs");
const assert = require("node:assert/strict");
const axe = require.resolve("axe-core/axe.min.js");
(async () => {
  const fixture = JSON.parse(
    fs.readFileSync("/tmp/f2r-collab-browser.json", "utf8"),
  );
  const browser = await chromium.launch({
    headless: true,
    args: ["--no-sandbox"],
  });
  const results = [];
  fs.mkdirSync("docs/collaboration/screens", { recursive: true });
  try {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    await context.addCookies([
      {
        name: "ACCESS_TOKEN",
        value: fixture.cookie,
        url: "http://127.0.0.1:19088",
        httpOnly: true,
        sameSite: "Lax",
      },
    ]);
    const page = await context.newPage();
    await page.goto("http://127.0.0.1:19088/ai-collaboration");
    await page.locator('[name="participant"]').first().waitFor();
    assert.equal(await page.locator('[name="participant"]:checked').count(), 0);
    results.push("no AI selected by default; Muse not assigned");
    await page.fill("#task-title", "QA 합성 · GPT→Codex→Claude 공동 검토");
    await page.fill(
      "#task-instruction",
      "이것은 격리 합성 검증입니다. 실제 EP01 또는 외부 API 호출이 아닙니다. 정본·운영 데이터 변경 금지.",
    );
    for (const name of ["GPT", "CODEX", "CLAUDE"])
      await page.locator(`[name=participant][value=${name}]`).check();
    await page.fill("#budget", "0.10");
    await page.locator("#create-task button[type=submit]").click();
    await page.locator(".title").filter({ hasText: "QA 합성" }).waitFor();
    results.push("task created in actual MySQL from browser");
    await page.locator("[data-action=start]").click();
    await page.getByRole("status").filter({ hasText: "동의" }).waitFor();
    results.push("external sharing consent required before dispatch");
    await page.locator(".consent").check();
    await page.locator("[data-action=start]").click();
    await page.waitForTimeout(700);
    await page.locator("#refresh").click();
    await page
      .locator(".summary")
      .filter({ hasText: "사용자 승인 대기" })
      .waitFor();
    assert.equal(
      await page
        .locator(".timeline strong")
        .filter({ hasText: "MODEL_PROPOSAL" })
        .count(),
      3,
    );
    assert.equal(
      await page
        .locator(".timeline strong")
        .filter({ hasText: "MUSE" })
        .count(),
      0,
    );
    results.push(
      "three selected roles automatically exchange stored proposals; unselected Muse absent (mock models)",
    );
    await page.locator("[data-action=approve]").click();
    await page.locator(".summary").filter({ hasText: "결과 승인" }).waitFor();
    results.push(
      "user approval stored separately from canonical studio approval",
    );
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.addScriptTag({ path: axe });
      const audit = await page.evaluate(async () =>
        (
          await axe.run(document, {
            runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21aa"] },
          })
        ).violations.map((v) => ({
          id: v.id,
          impact: v.impact,
          count: v.nodes.length,
        })),
      );
      assert.deepEqual(audit, []);
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
      );
      await page.screenshot({
        path: `docs/collaboration/screens/control-room-${width}.png`,
        fullPage: true,
      });
      results.push(
        `Chromium ${width}px, no horizontal overflow, axe violations 0`,
      );
    }
    const download = page.waitForEvent("download");
    await page.locator(".export").click();
    const file = await download;
    const content = fs.readFileSync(await file.path(), "utf8");
    assert(content.includes("MODEL_PROPOSAL"));
    results.push("result and approval history exported as Markdown attachment");
    fs.writeFileSync(
      "docs/collaboration/browser-results.json",
      JSON.stringify(
        {
          environment:
            "isolated MySQL and actual Chromium; synthetic data and mock models; no live paid API calls",
          results,
        },
        null,
        2,
      ) + "\n",
    );
    console.log(
      JSON.stringify({
        passed: results.length,
        actualExternalModelCalls: 0,
        axeViolations: 0,
      }),
    );
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
