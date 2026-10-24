"use strict";
// 재현: McpStudioMySqlTest로 격리 fixture 생성 -> run-isolated.sh -> node scripts/ai-agent-studio/media-browser-qa.cjs (PW_MODULE·PW_CHROME·F2R_FIXTURE 선택)
// Isolated studio QA (127.0.0.1:18088 only). Synthetic files; never touches production.
const fs = require("node:fs"), path = require("node:path"), assert = require("node:assert/strict");
const { chromium } = require(process.env.PW_MODULE || "playwright");
const base = "http://127.0.0.1:18088";
const out = process.env.QA_OUT || path.resolve(__dirname, "../../docs/studio-collaboration");
const fixture = JSON.parse(fs.readFileSync(process.env.F2R_FIXTURE || "/tmp/f2r-browser-fixture.json", "utf8"));
const results = [];
const pass = (name, detail = "") => { results.push({ name, pass: true, detail }); console.log("PASS " + name + (detail ? " - " + detail : "")); };

function wav(seconds = 1, rate = 8000) {
  const n = seconds * rate, b = Buffer.alloc(44 + n * 2);
  b.write("RIFF", 0); b.writeUInt32LE(36 + n * 2, 4); b.write("WAVE", 8); b.write("fmt ", 12);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(rate, 24);
  b.writeUInt32LE(rate * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write("data", 36); b.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i++) b.writeInt16LE(Math.round(8000 * Math.sin((2 * Math.PI * 440 * i) / rate)), 44 + i * 2);
  return b;
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.PW_CHROME });
  try {
    for (const viewport of [{ width: 1440, height: 1000 }, { width: 390, height: 844 }]) {
      const tag = viewport.width + "px";
      const context = await browser.newContext({ viewport });
      const [name, ...rest] = fixture.adminCookie.split("=");
      await context.addCookies([{ name, value: rest.join("="), url: base }]);
      const upload = async (buffer, filename) => {
        const r = await context.request.post(base + "/api/studio/files", { headers: { Origin: base }, multipart: { file: { name: filename, mimeType: "application/octet-stream", buffer } } });
        assert.equal(r.status(), 200, await r.text());
        return (await r.json()).file;
      };
      const page = await context.newPage();
      const errors = [], external = [], streamStatuses = [];
      page.on("pageerror", (e) => errors.push(e.message));
      page.on("response", (r) => { if (r.url().includes("/stream")) streamStatuses.push(r.status()); });
      await page.route("**/*", (route) => {
        const u = route.request().url();
        if (!u.startsWith(base) && !u.startsWith("blob:") && !u.startsWith("data:")) { external.push(u); route.abort(); } else route.continue();
      });
      await page.goto(base + "/ai-agent-studio");
      await page.waitForLoadState("networkidle");
      const dismiss = page.locator("#assistantWelcome button");
      if (await dismiss.isVisible().catch(() => false)) await dismiss.click();

      // JPG/WEBP made by the browser itself so the bytes are real images.
      const images = await page.evaluate(async () => {
        const c = document.createElement("canvas"); c.width = 320; c.height = 180;
        const g = c.getContext("2d"); g.fillStyle = "#1d4ed8"; g.fillRect(0, 0, 320, 180); g.fillStyle = "#fff"; g.font = "28px sans-serif"; g.fillText("QA SYNTHETIC", 40, 100);
        const toB64 = (type) => new Promise((res) => c.toBlob(async (b) => res(btoa(String.fromCharCode(...new Uint8Array(await b.arrayBuffer())))), type, 0.9));
        return { jpg: await toB64("image/jpeg"), webp: await toB64("image/webp") };
      });
      const stamp = Date.now();
      const audio = await upload(wav(), `qa-synthetic-sfx-${tag}-${stamp}.wav`);
      const jpg = await upload(Buffer.from(images.jpg, "base64"), `qa-synthetic-board-${tag}-${stamp}.jpg`);
      const webp = await upload(Buffer.from(images.webp, "base64"), `qa-synthetic-board-${tag}-${stamp}.webp`);
      pass(`${tag} upload WAV/JPG/WEBP accepted with server SHA`, [audio, jpg, webp].map((f) => f.sha256.slice(0, 8)).join(","));

      // Review + task through the same REST the AI tools use, then render the AI tab.
      const post = async (url, data) => { const r = await context.request.post(base + "/api/studio/" + url, { headers: { Origin: base }, data }); assert.equal(r.status(), 200, await r.text()); return r.json(); };
      const rev = await post("revisions", { itemKey: `QA-MEDIA-${tag}-${stamp}`, label: "v1", kind: "MUSIC", episode: 1, origin: "USER_EDIT", title: "합성 QA 효과음 · 실제 작품 아님", content: "QA", workStatus: "REVIEW", fileId: audio.id });
      const review = await post("reviews", { revisionId: rev.id, verdict: "NEEDS_CHANGES", summary: "QA 합성 검수", issues: [{ severity: "LOW", description: "볼륨 확인", timecode: "00:01" }] });
      await post("tasks", { workId: rev.item_key, revisionId: rev.id, reviewId: review.reviewId, title: "QA 합성 작업", instructions: "볼륨 조정", assignee: "CLAUDE" });

      await page.reload(); await page.waitForLoadState("networkidle");
      if (await dismiss.isVisible().catch(() => false)) await dismiss.click();
      await page.click('[data-tab="files"]');
      await page.locator("#file-list .card").first().waitFor();
      const openFile = async (filename) => {
        const card = page.locator("#file-list .card", { hasText: filename });
        await card.getByRole("button", { name: "자료 열람" }).click();
      };
      await openFile(audio.filename);
      const player = page.locator("#file-preview audio.media-player");
      await player.waitFor();
      const meta = await player.evaluate((a) => new Promise((res, rej) => { if (a.readyState >= 1) return res(a.duration); a.onloadedmetadata = () => res(a.duration); a.onerror = () => rej(new Error("audio error " + (a.error && a.error.code))); setTimeout(() => rej(new Error("metadata timeout")), 15000); }));
      assert.ok(meta > 0.9 && meta < 1.1, "duration " + meta);
      assert.ok(streamStatuses.some((s) => s === 206 || s === 200), "stream statuses " + streamStatuses);
      pass(`${tag} WAV plays through Range stream`, `duration=${meta.toFixed(2)}s statuses=${[...new Set(streamStatuses)]}`);
      for (const f of [jpg, webp]) {
        await openFile(f.filename);
        const img = page.locator("#file-preview img");
        await img.waitFor();
        const w = await img.evaluate((i) => new Promise((res) => (i.complete ? res(i.naturalWidth) : (i.onload = () => res(i.naturalWidth)))));
        assert.ok(w > 0, f.filename);
        pass(`${tag} ${f.filename.split(".").pop().toUpperCase()} preview renders`, `naturalWidth=${w}`);
      }
      await page.screenshot({ path: path.join(out, `studio-files-${tag}.png`), fullPage: false });

      await page.click('[data-tab="ai-work"]');
      await page.locator("#ai-task-list .card").first().waitFor(); await page.locator("#ai-review-list .card").first().waitFor();
      const aiText = await page.locator("#ai-work").innerText();
      assert.ok(aiText.includes("QA 합성 작업") && aiText.includes("QA 합성 검수") && aiText.includes("00:01"), aiText.slice(0, 300));
      pass(`${tag} AI 검수·작업 tab shows review and task`);
      await page.screenshot({ path: path.join(out, `studio-ai-work-${tag}.png`), fullPage: false });

      for (const tab of ["materials", "inbox", "ai-work", "files", "history"]) {
        await page.click(`[data-tab="${tab}"]`); await page.waitForTimeout(300);
        const overflow = await page.evaluate(() => document.scrollingElement.scrollWidth - window.innerWidth);
        assert.ok(overflow <= 1, `${tab} horizontal overflow ${overflow}px`);
      }
      pass(`${tag} no horizontal overflow on 5 tabs`);
      assert.deepEqual(errors, [], "page errors"); assert.deepEqual(external, [], "external requests");
      pass(`${tag} no JS errors, no external requests`);
      await context.close();
    }
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(out, "media-qa-results.json"), JSON.stringify(results, null, 1));
  }
})().catch((e) => { console.error("FAIL", e.message); process.exit(1); });
