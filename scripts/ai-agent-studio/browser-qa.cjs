"use strict";
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const assert = require("node:assert/strict");
const deps = process.env.F2R_QA_MODULES || "/tmp/f2r-qa-deps/node_modules";
const { chromium } = require(path.join(deps, "playwright"));
const { default: AxeBuilder } = require(
  path.join(deps, "@axe-core/playwright"),
);
const root = path.resolve(__dirname, "../.."),
  out = path.join(root, "docs/ai-agent-studio"),
  base = "http://127.0.0.1:18088";
const fixture = JSON.parse(
  fs.readFileSync("/tmp/f2r-browser-fixture.json", "utf8"),
);
const results = [];
const passed = (name, detail) => results.push({ name, pass: true, detail });
(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath:process.env.CHROMIUM_PATH||"/usr/bin/chromium",
    args: ["--no-sandbox"],
  });
  try {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    const cookie = fixture.adminCookie.split("=");
    await context.addCookies([
      { name: cookie[0], value: cookie.slice(1).join("="), url: base },
    ]);
    const page = await context.newPage();
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.goto(base + "/ai-agent-studio");
    await page.waitForFunction(() =>
      document.getElementById("project-info").textContent.includes("14화"),
    );
    assert.equal(await page.locator("#cut-grid button").count(), 68);
    passed("EP01 68컷 대기 현황");
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    passed("PC 가로 넘침 없음");
    await page.screenshot({
      path: path.join(out, "screens/studio-pc-1440.png"),
      fullPage: true,
    });
    const accessibility = [];
    for (const tab of [
      "materials",
      "files",
      "compare",
      "chat",
      "history",
      "members",
    ]) {
      await page.locator(`[data-tab="${tab}"]`).click();
      await page.waitForTimeout(150);
      const a = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze();
      accessibility.push({
        tab,
        violations: a.violations.map((v) => ({
          id: v.id,
          impact: v.impact,
          nodes: v.nodes.length,
        })),
      });
      assert.equal(a.violations.length, 0, JSON.stringify(accessibility));
    }
    passed("PC 6개 탭 axe WCAG 2.1 AA 위반 0", accessibility);
    await page.locator('[data-tab="files"]').click();
    for (const ext of ["md", "html", "json", "zip", "png", "pdf", "mp4"]) {
      const file = path.join("/tmp/f2r-qa-assets", "qa-synthetic." + ext);
      await page.locator("#upload-file").setInputFiles(file);
      const responsePromise = page.waitForResponse(
        (r) =>
          r.url() === base + "/api/studio/files" &&
          r.request().method() === "POST",
      );
      await page.locator("#upload-form button").click();
      const response = await responsePromise;
      assert.equal(response.status(), 200, await response.text());
      const payload = await response.json();
      const id = payload.file.id;
      const bytes = fs.readFileSync(file);
      const expected = crypto.createHash("sha256").update(bytes).digest("hex");
      assert.equal(payload.file.sha256, expected);
      const downloaded = await context.request.get(
        base + "/api/studio/files/" + id + "/content",
      );
      assert.equal(downloaded.status(), 200);
      assert.deepEqual(await downloaded.body(), bytes);
      assert.match(downloaded.headers()["content-disposition"], /^attachment/);
      if (["pdf", "zip"].includes(ext)) {
        const preview = await context.request.get(
          base + "/api/studio/files/" + id + "/text",
        );
        assert.equal(preview.status(), 200, await preview.text());
        const data = await preview.json();
        assert.ok(
          ext === "pdf"
            ? data.text.includes("QA SYNTHETIC ONLY")
            : data.entries[0].name === "../never-extracted.txt",
        );
      }
      passed(ext.toUpperCase() + " 업로드·원본 SHA-256·다운로드 동일");
    }
    await page
      .locator("#upload-file")
      .setInputFiles("/tmp/f2r-qa-assets/qa-synthetic.md");
    const duplicatePromise = page.waitForResponse(
      (r) =>
        r.url() === base + "/api/studio/files" &&
        r.request().method() === "POST",
    );
    await page.locator("#upload-form button").click();
    assert.equal((await (await duplicatePromise).json()).duplicate, true);
    passed("동일 원본 중복 저장 방지");
    await page.waitForFunction(() =>
      document
        .querySelector("#file-list")
        .textContent.includes("qa-synthetic.html"),
    );
    await page
      .locator("#file-list .card")
      .filter({
        has: page.getByRole("heading", {
          name: "qa-synthetic.html",
          exact: true,
        }),
      })
      .getByRole("button", { name: "자료 열람" })
      .click();
    await page.locator("#file-preview pre").waitFor();
    assert.equal(
      await page.evaluate(() => window.f2rUnsafeExecuted),
      undefined,
    );
    passed("HTML 원문 스크립트 실행 차단");
    await page.screenshot({
      path: path.join(out, "screens/files-pc-1440.png"),
      fullPage: true,
    });
    await page.locator('[data-tab="materials"]').click();
    await page.locator("#new-revision").click();
    const form = page.locator("#revision-form"),
      key = "qa-character-" + Date.now();
    await form.locator("[name=itemKey]").fill(key);
    await form.locator("[name=label]").fill("r14");
    await form.locator("[name=kind]").selectOption("CHARACTER");
    await form.locator("[name=origin]").selectOption("ORIGINAL");
    await form.locator("[name=title]").fill("QA SYNTHETIC · 캐릭터 설정");
    await form
      .locator("[name=content]")
      .fill("합성 검증용 캐릭터입니다. 실제 작품 설정이 아닙니다.");
    await form.locator("[name=dialogue]").fill("검증 대사");
    await form.locator("[name=direction]").fill("검증 연출");
    await form.locator("button[type=submit]").click();
    await page.locator("#editor").waitFor({ state: "hidden" });
    await page
      .locator("#detail")
      .getByRole("button", { name: "이 자료의 다음 버전 작성" })
      .click();
    await form.locator("[name=label]").fill("r2");
    await form.locator("[name=origin]").selectOption("AI_PROPOSAL");
    await form
      .locator("[name=content]")
      .fill("AI 제안 검증용 · 승인 전 정본 변경 없음");
    await form.locator("button[type=submit]").click();
    await page.locator("#editor").waitFor({ state: "hidden" });
    assert.match(await page.locator("#detail").innerText(), /미승인/);
    passed("새 버전·AI 제안 미승인 유지");
    await page
      .locator("#detail")
      .getByRole("button", { name: "검수·사용자 승인" })
      .click();
    await page
      .locator("#review-form [name=note]")
      .fill("QA 수동 승인 동작 검증");
    await page
      .locator("#review-form button")
      .filter({ hasText: "검수 결과 저장" })
      .click();
    await page.waitForFunction(() =>
      document.querySelector("#detail").textContent.includes("사용자 승인본"),
    );
    passed("명시적 사용자 검수 후 승인본 지정");
    await page.screenshot({
      path: path.join(out, "screens/character-review-pc-1440.png"),
      fullPage: true,
    });
    await page.locator('[data-tab="compare"]').click();
    await page.waitForFunction(
      () =>
        document.getElementById("left-version").options.length > 1 &&
        !document.getElementById("left-version").disabled,
    );
    await page.locator("#left-version").selectOption(fixture.originalId);
    await page.locator("#right-version").selectOption(fixture.proposalId);
    await page.locator("#compare-button").click();
    await page.locator("#comparison h3").first().waitFor();
    assert.match(await page.locator("#comparison").innerText(), /r14/);
    assert.match(await page.locator("#comparison").innerText(), /r2/);
    passed("r14·r2 버전 실제 비교");
    await page.screenshot({
      path: path.join(out, "screens/compare-pc-1440.png"),
      fullPage: true,
    });
    await page.locator('[data-tab="chat"]').click();
    await page.locator("#chat-query").fill("제안 테스트");
    await page.locator("#chat-form button").click();
    await page.locator("#chat-log button").first().waitFor();
    assert.match(await page.locator("#chat-log").innerText(), /r2/);
    passed("작품 자료 검색·채팅 근거 링크");
    await page.locator('[data-tab="history"]').click();
    await page.waitForFunction(() =>
      document.querySelector("#event-list").textContent.includes("APPROVE"),
    );
    passed("검수·승인·업로드 작업 이력");
    const mobile = await browser.newContext({
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
    });
    await mobile.addCookies([
      { name: cookie[0], value: cookie.slice(1).join("="), url: base },
    ]);
    const m = await mobile.newPage();
    await m.goto(base + "/ai-agent-studio");
    await m.waitForFunction(() =>
      document.getElementById("project-info").textContent.includes("14화"),
    );
    assert.equal(
      await m.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false,
    );
    assert.equal(await m.locator("#cut-grid button").count(), 68);
    const ma = await new AxeBuilder({ page: m })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    assert.equal(ma.violations.length, 0, JSON.stringify(ma.violations));
    passed("모바일 390px·68컷·가로 넘침 없음·axe 위반 0");
    await m.locator('#cut-grid button').first().click();
    await m.locator('#detail').getByRole('button',{name:'검수·사용자 승인',exact:true}).click();
    await m.locator('#review-dialog').waitFor({state:'visible'});
    await m.locator('#review-form [name=note]').fill('합성 68컷 모바일 승인 검증');
    await m.locator('#review-form button:not([type=button])').click();
    await m.locator('#review-dialog').waitFor({state:'hidden'});
    await m.waitForFunction(()=>document.querySelector('#detail').textContent.includes('사용자 승인본'));
    passed('모바일 실제 승인 및 저장 재조회');
    await m.locator('#detail').getByRole('button',{name:'이 버전으로 복원 초안 만들기'}).click();
    await m.waitForFunction(()=>document.querySelector('#message').textContent.includes('새 버전을 만들었습니다'));
    assert.match(await m.locator('#detail').innerText(),/미승인/);
    passed('모바일 복원은 미승인 새 버전·기존 승인 이력 유지');
    await m.screenshot({path:path.join(out,'screens/restore-mobile-390.png'),fullPage:true});
    await m.screenshot({
      path: path.join(out, "screens/studio-mobile-390.png"),
      fullPage: true,
    });
    await m.locator('[data-tab="files"]').click();
    await m.screenshot({
      path: path.join(out, "screens/files-mobile-390.png"),
      fullPage: true,
    });
    const stranger = await browser.newContext();
    const sc = fixture.strangerCookie.split("=");
    await stranger.addCookies([
      { name: sc[0], value: sc.slice(1).join("="), url: base },
    ]);
    const denied = await stranger.request.get(base + "/api/studio/files");
    assert.equal(denied.status(), 403);
    passed("미허가 제작자 파일 목록 403");
    assert.deepEqual(errors, []);
    passed("브라우저 JS 오류 0");
    fs.writeFileSync(
      path.join(out, "browser-results.json"),
      JSON.stringify(
        {
          testData:
            "SYNTHETIC ONLY; real EP01 assets were not created or registered",
          checks: results,
        },
        null,
        2,
      ) + "\n",
    );
    console.log(
      JSON.stringify({ passed: results.length, checks: results }, null, 2),
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
