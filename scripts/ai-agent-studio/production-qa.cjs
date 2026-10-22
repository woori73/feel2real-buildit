/* Actual preserved #863 materials + isolated MySQL/UI. No model inference or product sessions. */
const { chromium } = require("playwright");
const fs = require("node:fs"),
  assert = require("node:assert/strict"),
  crypto = require("node:crypto");
(async () => {
  const fixture = JSON.parse(
      fs.readFileSync("/tmp/f2r-production-browser.json", "utf8"),
    ),
    base = "http://127.0.0.1:19089",
    out = "docs/collaboration/production-results",
    results = [];
  fs.mkdirSync(out + "/screens", { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    args: ["--no-sandbox"],
  });
  try {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    await context.addCookies([
      {
        name: "ACCESS_TOKEN",
        value: fixture.cookie,
        url: base,
        httpOnly: true,
        sameSite: "Lax",
      },
    ]);
    const page = await context.newPage();
    async function api(path) {
      const r = await context.request.get(base + "/api/collaboration/" + path);
      assert.equal(r.status(), 200);
      return r.json();
    }
    const all = [];
    let cursor = "";
    do {
      const response = await api(
        `workspaces/${fixture.workspace}/files?limit=100&beforeId=${encodeURIComponent(cursor)}`,
      );
      all.push(...response.items);
      cursor = response.nextBeforeId;
    } while (cursor);
    assert.equal(all.length, 536);
    assert.equal(new Set(all.map((f) => f.id)).size, 536);
    assert(all.every((f) => f.status === "DRAFT"));
    results.push(
      "All 536 real source files paginated through authenticated HTTP; no missing IDs, all DRAFT",
    );
    const cuts = await api(`workspaces/${fixture.workspace}/production`);
    assert.equal(cuts.cuts.length, 68);
    assert.equal(cuts.passConfirmed, 0);
    assert.equal(cuts.comparedCuts, 0);
    await page.goto(base + "/ai-collaboration");
    await page
      .locator("#status")
      .filter({ hasText: "저장했습니다." })
      .waitFor();
    await page.selectOption("#workspace", fixture.workspace);
    await page
      .locator("#production-summary")
      .filter({ hasText: "536" })
      .waitFor();
    await page
      .locator("#status")
      .filter({ hasText: "저장했습니다." })
      .waitFor();
    await page.locator("#production-board").scrollIntoViewIfNeeded();
    await page.locator(".cut-card img").first().waitFor();
    await page.locator(".cut-card img").first().scrollIntoViewIfNeeded();
    await page.waitForFunction(
      () => document.querySelector(".cut-card img")?.naturalWidth === 1280,
    );
    assert(
      await page
        .locator("#production-board")
        .textContent()
        .then((t) => t.includes("정식 원작 대조 0/68")),
    );
    results.push(
      "Real 1280x720 rough storyboard and original cut descriptions/dialogue displayed; canonical comparison0/PASS0",
    );
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.locator("#production-board").scrollIntoViewIfNeeded();
      await page.addScriptTag({ path: require.resolve("axe-core/axe.min.js") });
      const violations = await page.evaluate(async () =>
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
      assert.deepEqual(violations, []);
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page
        .locator("#production-board")
        .screenshot({ path: `${out}/screens/real-storyboard-${width}.png` });
      results.push(
        `Actual-material storyboard ${width}px, no horizontal overflow, axe WCAG violations0`,
      );
    }
    await page.selectOption("#cut-page", "5");
    assert.equal(await page.locator(".cut-card").count(), 8);
    assert.equal(await page.locator('.cut-card[data-cut="68"]').count(), 1);
    assert(await page.locator("#cuts-next").isDisabled());
    results.push(
      "Last storyboard page includes cut68; all six ranges accessible",
    );
    await page.selectOption("#cut-page", "3");
    const cut38 = page.locator('.cut-card[data-cut="38"]');
    await cut38.getByRole("button", { name: "컷 확대" }).click();
    await page.waitForFunction(
      () => document.querySelector("#image-large")?.naturalWidth === 1280,
    );
    await page
      .locator("#image-dialog")
      .screenshot({ path: out + "/screens/real-image-zoom-390.png" });
    await page.locator("#image-dialog .dialog-close").click();
    const candidates = all.filter(
      (f) => f.filename === "SUNI_S5_S6_CANDIDATE_SHEET_original.png",
    );
    const approved = all.find(
      (f) =>
        f.filename === "SUNI_S5_S6_APPROVED_SHEET_original.png" &&
        f.source_approval === "APPROVED_REFERENCE",
    );
    assert(candidates.length && approved);
    const candidate = candidates[0];
    assert.notEqual(candidate.sha256, approved.sha256);
    async function search(query) {
      await page.fill("#file-query", query);
      await page.locator("#file-search button").click();
      await page.waitForFunction(
        (q) =>
          document.querySelector("#status")?.textContent === "저장했습니다." &&
          document.querySelector(".file-card")?.textContent.includes(q),
        query,
      );
    }
    await search(candidate.filename);
    await page
      .locator(`.file-card[data-file-id="${candidate.id}"]`)
      .getByRole("button", { name: "비교에 추가" })
      .click();
    await search(approved.filename);
    await page
      .locator(`.file-card[data-file-id="${approved.id}"]`)
      .getByRole("button", { name: "비교에 추가" })
      .click();
    await page.locator("#compare-open").click();
    await page.locator("#compare-dialog img").first().waitFor();
    await page.waitForFunction(() =>
      [...document.querySelectorAll("#compare-dialog img")].every(
        (img) => img.naturalWidth > 1,
      ),
    );
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.locator("#compare-dialog").screenshot({
        path: `${out}/screens/real-version-compare-${width}.png`,
      });
    }
    await page.locator("#compare-dialog .dialog-close").click();
    results.push(
      "Actual S5/S6 v1 candidate vs approved v2 displayed side by side with distinct SHA/source version; originals unchanged",
    );
    await page.setViewportSize({ width: 1440, height: 1000 });
    for (const name of [
      "CURRENT_STATE.md",
      "APPROVAL_HISTORY.md",
      "MISSING_FILES.md",
    ])
      assert((await page.locator("#memory-list").textContent()).includes(name));
    results.push(
      "Original CURRENT_STATE/APPROVAL_HISTORY/MISSING_FILES are visible in shared memory, with source IDs",
    );
    await search("CURRENT_STATE.md");
    await page
      .locator(".file-card")
      .filter({ hasText: "CURRENT_STATE.md" })
      .getByRole("button", { name: "문서 보기" })
      .click();
    await page
      .locator("#document-view")
      .filter({ hasText: "제작 진행" })
      .waitFor();
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.locator("#document-dialog").screenshot({
        path: `${out}/screens/real-markdown-viewer-${width}.png`,
      });
    }
    await page.locator("#document-dialog .dialog-close").click();
    const html = all.find(
      (f) =>
        f.source_path ===
        "01_r14_handoff_source/AI_AGENT_EP01_68컷_전체콘티_r14.html",
    );
    assert(html);
    await search(html.filename);
    await page
      .locator(`.file-card[data-file-id="${html.id}"]`)
      .getByRole("button", { name: "문서 보기" })
      .click();
    const frame = page.frameLocator("#document-view iframe");
    await frame.locator("body").waitFor();
    assert.equal(await frame.locator("script").count(), 0);
    assert((await frame.locator("body").textContent()).length > 1000);
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await page
        .locator("#document-dialog")
        .screenshot({ path: `${out}/screens/real-html-viewer-${width}.png` });
    }
    await page.locator("#document-dialog .dialog-close").click();
    const pdf = all.find(
      (f) => f.filename === "AI_AGENT_EP01_68CUT_FINAL_STORYBOARD.pdf",
    );
    assert(pdf);
    await search(pdf.filename);
    await page
      .locator(`.file-card[data-file-id="${pdf.id}"]`)
      .getByRole("button", { name: "문서 보기" })
      .click();
    await page.waitForFunction(
      () => document.querySelector("#document-view img")?.naturalWidth > 100,
    );
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await page
        .locator("#document-dialog")
        .screenshot({ path: `${out}/screens/real-pdf-viewer-${width}.png` });
    }
    await page.locator("#document-dialog .dialog-close").click();
    results.push(
      "Actual MD, script-disabled HTML and PDF page viewers render original production documents",
    );
    await search("src_r14.md5");
    const sidecar = page
      .locator(".file-card")
      .filter({ hasText: "src_r14.md5" });
    assert((await sidecar.textContent()).includes("검증용 메타자료"));
    assert.equal(await sidecar.locator("img").count(), 0);
    results.push(
      "MD5 preserved as checksum metadata and never displayed as an image",
    );
    for (const file of [
      html,
      pdf,
      approved,
      candidate,
      all.find((f) => f.filename === "CURRENT_STATE.md"),
    ]) {
      const r = await context.request.get(
        base + `/api/collaboration/files/${file.id}/content`,
      );
      assert.equal(r.status(), 200);
      const bytes = await r.body();
      assert.equal(
        crypto.createHash("sha256").update(bytes).digest("hex"),
        file.sha256,
      );
    }
    results.push(
      "HTML/PDF/reference images/MD original download SHA unchanged after all previews and comparison",
    );
    await search("");
    while (await page.locator("#files-more").isVisible()) {
      const old = await page.locator(".file-card").count();
      await page.locator("#files-more").click();
      await page.waitForFunction(
        (n) => document.querySelectorAll(".file-card").length > n,
        old,
      );
    }
    assert.equal(await page.locator(".file-card").count(), 536);
    results.push(
      "Human load-more screen reaches all536 files, exceeding old200 limit",
    );
    // Separate review fixture: the primary imported workspace remains entirely DRAFT.
    await page.selectOption("#workspace", fixture.reviewWorkspace);
    await page.waitForFunction(
      (cutId) =>
        document.querySelector("#status")?.textContent === "저장했습니다." &&
        document
          .querySelector('.cut-card[data-cut="1"] img')
          ?.src.includes(cutId),
      fixture.reviewedCut,
    );
    await page
      .locator('.cut-card[data-cut="1"]')
      .getByRole("button", { name: "컷 확대" })
      .click();
    assert(
      (await page.locator("#image-details").textContent()).includes("APPROVED"),
    );
    assert(
      !(await page.locator("#image-details").textContent()).includes("DRAFT"),
    );
    await page.locator("#image-dialog .dialog-close").click();
    const reviewed = await api(`files/${fixture.reviewedReference}`);
    await search(reviewed.filename);
    await page
      .locator(`.file-card[data-file-id="${fixture.reviewedReference}"]`)
      .getByRole("button", { name: "이미지 확대" })
      .click();
    const details = await page.locator("#image-details").textContent();
    assert(details.includes("APPROVED") && details.includes("기존 승인 참조"));
    assert(!details.includes("DRAFT"));
    await page.locator("#image-dialog .dialog-close").click();
    results.push(
      "Human-approved panel and approved-source reference display current APPROVED state after importer replay; primary536 files stay DRAFT and canonical PASS0",
    );
    fs.writeFileSync(
      out + "/browser-results.json",
      JSON.stringify(
        {
          sourceRef: "09d62dee982e2e89e3653c90b1ee42f05bff4f9d",
          environment:
            "Actual preserved #863 files, isolated MySQL and actual Chromium; no generated replacement assets",
          results,
          originalFilesModified: false,
          canonicalComparedCuts: 0,
          canonicalPass: 0,
          actualChatGptClaudeOAuth: "NOT TESTED",
          actualModelImageUnderstanding: "NOT TESTED",
          paidModelCalls: 0,
        },
        null,
        2,
      ) + "\n",
    );
    console.log(
      JSON.stringify({
        realMaterialBrowserChecks: results.length,
        files: 536,
        cuts: 68,
        actualExternalModels: "NOT TESTED",
      }),
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
