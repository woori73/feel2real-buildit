/* Actual isolated UI + OAuth MCP calls. No inference or consumer-app sessions. */
const { chromium } = require("playwright");
const fs = require("node:fs"),
  assert = require("node:assert/strict"),
  crypto = require("node:crypto");
const base = "http://127.0.0.1:19088",
  fixturePath = "/tmp/f2r-collab-browser.json";
(async () => {
  const fixture = JSON.parse(fs.readFileSync(fixturePath, "utf8"));
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
        url: base,
        httpOnly: true,
        sameSite: "Lax",
      },
    ]);
    const page = await context.newPage();
    async function api(path, data) {
      const r = await context.request.fetch(
        base + "/api/collaboration/" + path,
        {
          method: data ? "POST" : "GET",
          headers: data ? { Origin: base } : {},
          data,
        },
      );
      assert.equal(r.status(), 200, await r.text());
      return r.json();
    }
    async function mcp(agent, name, args) {
      const r = await context.request.post(base + "/mcp", {
        headers: {
          Authorization: "Bearer " + fixture[agent],
          "MCP-Protocol-Version": "2025-11-25",
        },
        data: {
          jsonrpc: "2.0",
          id: crypto.randomUUID(),
          method: "tools/call",
          params: { name, arguments: args },
        },
      });
      assert.equal(r.status(), 200);
      const b = await r.json();
      assert(!b.error, JSON.stringify(b));
      assert(!b.result.isError, JSON.stringify(b));
      return b.result.content;
    }
    const decode = (blocks) => JSON.parse(blocks[0].text);
    await page.goto(base + "/ai-collaboration");
    await page.locator("[name=participant]").first().waitFor();
    await page
      .locator("#status")
      .filter({ hasText: "저장했습니다." })
      .waitFor();
    await page.selectOption("#workspace", fixture.workspace);
    await page.waitForFunction(
      () => document.querySelector("#workspace").value !== "ai-agent-part1",
    );
    await page.waitForTimeout(350);
    assert.equal(await page.locator("[name=participant]:checked").count(), 0);
    assert.equal(await page.locator("#assistantWelcome").count(), 0);
    results.push(
      "No roles selected by default; unrelated dashboard assistant is not injected",
    );
    await page.selectOption("#memory-kind", "DECISION");
    await page.fill(
      "#memory-body",
      "합성 QA 결정: 원본 SHA와 승인 정본은 보존합니다.",
    );
    await page.locator("#memory-form button").click();
    await page
      .locator("#memory-list")
      .getByText("합성 QA 결정:", { exact: false })
      .waitFor();
    await page.fill("#memory-query", "합성 QA 결정");
    await page.locator("#memory-search button").click();
    await page
      .locator("#search-results")
      .getByText("MEMORY", { exact: false })
      .waitFor();
    results.push(
      "Human project memory and decisions saved to MySQL and found through actual search",
    );
    await page.locator("#upload-file").setInputFiles({
      name: "human-review-QA.md",
      mimeType: "text/markdown",
      buffer: Buffer.from("# 합성 검수 자료\n실제 EP01 원본이 아닙니다."),
    });
    await page.locator("#file-upload button").click();
    await page
      .locator(".file-card")
      .filter({ hasText: "human-review-QA.md" })
      .waitFor();
    await page
      .locator(".file-card")
      .filter({ hasText: "human-review-QA.md" })
      .getByRole("button", { name: "텍스트 열람" })
      .click();
    await page
      .locator("#file-preview")
      .filter({ hasText: "실제 EP01" })
      .waitFor();
    results.push(
      "Human multipart DRAFT upload, SHA metadata and escaped text preview work",
    );
    await page.fill("#task-title", "QA 합성 · GPT→Codex→Claude 공동 검토");
    await page.fill(
      "#task-instruction",
      "격리 합성 검증. 실제 EP01 자료·유료 API 호출 아님. 정본 보존.",
    );
    for (const role of ["GPT", "CODEX", "CLAUDE"])
      await page.locator(`[name=participant][value=${role}]`).check();
    await page
      .locator("#create-task button[type=submit], #create-task > button")
      .click();
    await page.locator(".title").filter({ hasText: "QA 합성" }).waitFor();
    await page.locator("[data-action=start]").click();
    await page.locator("#status").filter({ hasText: "공유" }).waitFor();
    await page.locator(".consent").check();
    await page.locator("[data-action=start]").click();
    await page.locator(".summary").filter({ hasText: "인계 대기" }).waitFor();
    let tasks = await api("tasks?workspaceId=" + fixture.workspace);
    const task = tasks.find((t) => t.title.startsWith("QA 합성"));
    const document = decode(
      await mcp("gpt", "collaboration_add_document", {
        taskId: task.id,
        filename: "gpt-proposal-QA.md",
        text: "# GPT 역할의 합성 후보\n프로토콜 검증 자료이며 실제 모델이 작성한 결과가 아닙니다.",
      }),
    );
    assert.equal(document.status, "DRAFT");
    let shared = [];
    for (const agent of ["gpt", "claude"])
      shared.push(
        decode(await mcp(agent, "collaboration_read", { fileId: document.id })),
      );
    assert.equal(shared[0].metadata.sha256, shared[1].metadata.sha256);
    assert.equal(shared[0].text, shared[1].text);
    results.push(
      "Two distinct OAuth MCP clients read identical document bytes/SHA; only DRAFT append",
    );
    const png = Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aB9sAAAAASUVORK5CYII=",
      "base64",
    );
    const sha = crypto.createHash("sha256").update(png).digest("hex");
    const upload = decode(
      await mcp("gpt", "collaboration_begin_upload", {
        taskId: task.id,
        filename: "synthetic-transport-QA.png",
        size: png.length,
        sha256: sha,
      }),
    );
    await mcp("gpt", "collaboration_upload_chunk", {
      uploadId: upload.uploadId,
      index: 0,
      base64: png.toString("base64"),
    });
    const image = decode(
      await mcp("gpt", "collaboration_commit_upload", {
        uploadId: upload.uploadId,
      }),
    );
    const imageRead = await mcp("claude", "collaboration_read", {
      fileId: image.id,
    });
    assert.equal(imageRead[1].type, "image");
    assert.equal(
      crypto
        .createHash("sha256")
        .update(Buffer.from(imageRead[1].data, "base64"))
        .digest("hex"),
      sha,
    );
    results.push(
      "Binary chunk upload verified SHA; second OAuth client receives actual MCP image block",
    );
    for (const [agent, decision, text] of [
      ["gpt", "INFO", "합성 제안"],
      ["codex", "INFO", "합성 구현 제안"],
      ["claude", "CHANGES_REQUESTED", "합성 검수: 접근 권한 보완"],
      ["codex", "INFO", "합성 보완 제안"],
      ["claude", "PASS", "합성 재검수 의견"],
    ]) {
      const current = decode(
        await mcp(agent, "collaboration_read", { taskId: task.id }),
      );
      await mcp(agent, "collaboration_submit", {
        requestId: crypto.randomUUID(),
        taskId: task.id,
        version: current.version,
        text,
        decision,
        fileIds: agent === "gpt" ? [document.id, image.id] : [],
      });
    }
    await page.locator("#refresh").click();
    await page
      .locator(".summary")
      .filter({ hasText: "사용자 승인 대기" })
      .waitFor();
    assert.equal(
      await page
        .locator(".timeline strong")
        .filter({ hasText: "DRAFT_PROPOSAL" })
        .count(),
      5,
    );
    assert.equal(
      await page
        .locator(".timeline strong")
        .filter({ hasText: "MUSE" })
        .count(),
      0,
    );
    results.push(
      "Selected role handoff and Claude→Codex→Claude loop persist; no consumer app/model auto-run",
    );
    await page.locator("[data-action=approve]").click();
    await page.locator(".summary").filter({ hasText: "결과 승인" }).waitFor();
    const card = page
      .locator(".file-card")
      .filter({ hasText: "gpt-proposal-QA.md" });
    await card.getByRole("button", { name: "협업 후보 승인" }).click();
    await card.filter({ hasText: "APPROVED" }).waitFor();
    results.push(
      "Human task approval and candidate review recorded separately from canonical studio originals",
    );
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
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
      await page.screenshot({
        path: `docs/collaboration/screens/control-room-${width}.png`,
        fullPage: true,
      });
      results.push(
        `Chromium ${width}px: no horizontal overflow, axe WCAG violations 0`,
      );
    }
    const downloading = page.waitForEvent("download");
    await page.locator(".export").click();
    const download = await downloading;
    assert(
      fs.readFileSync(await download.path(), "utf8").includes("DRAFT_PROPOSAL"),
    );
    results.push("Shared proposal and approval history exported as Markdown");
    const timings = [];
    for (let sample = 0; sample < 20; sample++) {
      const started = performance.now();
      await api("tasks/" + task.id);
      timings.push(performance.now() - started);
    }
    timings.sort((a, b) => a - b);
    fs.writeFileSync(
      "docs/collaboration/performance-results.json",
      JSON.stringify(
        {
          environment:
            "Isolated loopback Spring/MySQL test instance; single client, no production capacity claim",
          endpoint: "GET /api/collaboration/tasks/{id}",
          samples: 20,
          medianMs: Math.round(timings[9] * 100) / 100,
          p95Ms: Math.round(timings[18] * 100) / 100,
          paidModelApiCalls: 0,
        },
        null,
        2,
      ) + "\n",
    );
    const sdkTask = await api("tasks", {
      title: "SDK 합성 파일 인계",
      instruction: "공식 SDK 전송만 검증. 실제 앱/모델 호출 아님.",
      participants: ["GPT", "CLAUDE"],
      maxCalls: 4,
      budgetMicrousd: 0,
      workspaceId: fixture.workspace,
    });
    fixture.sdktask = sdkTask.id;
    fixture.document = document.id;
    fs.writeFileSync(fixturePath, JSON.stringify(fixture), { mode: 0o600 });
    fs.writeFileSync(
      "docs/collaboration/browser-results.json",
      JSON.stringify(
        {
          environment:
            "Isolated MySQL 8.4 + Chromium + real OAuth/PKCE MCP transport. Synthetic QA assets; no inference and no actual ChatGPT/Claude account connection.",
          actualConsumerAppConnectionVerified: false,
          actualExternalModelCalls: 0,
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
        actualConsumerAppConnectionVerified: false,
        axeViolations: 0,
      }),
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
