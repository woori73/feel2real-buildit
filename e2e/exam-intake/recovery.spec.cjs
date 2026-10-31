const { test, expect, request } = require("@playwright/test");
const fs = require("fs"),
  path = require("path");
const base = "http://localhost:8091";
const output =
  process.env.INTAKE_EVIDENCE_DIR ||
  path.resolve(__dirname, "../../build/verification/exam-intake");
async function session(browser, width = 390) {
  const api = await request.newContext({
    baseURL: base,
    extraHTTPHeaders: { Origin: base, "X-F2R-Exam": "1" },
  });
  const suffix = require("crypto").randomBytes(6).toString("hex");
  const loginId = "intake-" + suffix;
  const registered = await api.post("/api/users/students", {
    data: {
      loginId,
      password: "admin1234",
      name: "합성 검증 계정",
      nickname: "검증" + suffix,
      email: loginId + "@example.invalid",
      phone: "010-0000-0000",
      schoolName: "합성중학교",
      grade: 3,
      parentName: "",
      parentPhone: "",
    },
  });
  expect(registered.ok(), await registered.text()).toBeTruthy();
  expect(
    (
      await api.post("/api/auth/login", {
        data: { loginId, password: "admin1234" },
      })
    ).ok(),
  ).toBeTruthy();
  const context = await browser.newContext({
    storageState: await api.storageState(),
    viewport: { width, height: 900 },
    isMobile: width < 500,
    hasTouch: width < 500,
  });
  await context.route("**/*", (route) =>
    new URL(route.request().url()).origin === base
      ? route.continue()
      : route.abort(),
  );
  const page = await context.newPage();
  return { api, context, page };
}
async function fill(page, marker) {
  await page.goto("/exam-service");
  await expect(page.locator("#offerSummary")).toContainText("160문항");
  await page.locator("[name=ageBand]").selectOption("OVER14");
  const form = page.locator("#intakeForm");
  for (const [key, value] of Object.entries({
    name: marker,
    school: "합성중학교",
    grade: "중3",
    contact: "synthetic@example.invalid",
    examDate: "2030-12-01",
    questionTypes: "독해",
    difficulty: "중간",
    format: "A4",
    scope: "합성 시험범위",
  }))
    await form.locator(`[name=${key}]`).fill(value);
  await form.locator("[name=consent]").check();
  await form.locator("[name=termsAccepted]").check();
}
async function matching(api, marker) {
  return (await (await api.get("/api/exam-intake/requests")).json()).filter(
    (r) => r.details.name === marker,
  );
}
const fixture = path.resolve(
  __dirname,
  "../unified-assistant/fixtures/poster.jpg",
);
for (const width of [390, 1440])
  test(`homepage to application with initial material ${width}`, async ({
    browser,
  }) => {
    const { api, context, page } = await session(browser, width);
    const marker = "첨부 합성 " + width + " " + Date.now();
    try {
      await page.goto("/");
      await expect(page.locator("#homeIntakeCta")).toHaveAttribute(
        "href",
        "/exam-service",
      );
      await page.locator("#homeIntakeCta").click();
      await expect(page).toHaveURL(/exam-service/);
      await fill(page, marker);
      await page.locator("#initialFiles").setInputFiles(fixture);
      await page.locator("[name=initialMaterialConsent]").check();
      fs.mkdirSync(output, { recursive: true });
      await page.screenshot({
        path: path.join(output, `ready-${width}-form.png`),
        fullPage: true,
      });
      await page.getByRole("button", { name: "접수하기", exact: true }).click();
      await expect(page.locator("#receiptStatus")).toContainText(
        "접수되었습니다",
      );
      await expect(page.locator("#documents")).toContainText("poster.jpg");
      const id = await page.locator("#receiptNumber").textContent();
      expect((await matching(api, marker)).map((r) => r.id)).toEqual([id]);
      await page.screenshot({
        path: path.join(output, `ready-${width}-receipt.png`),
        fullPage: true,
      });
      await page.reload();
      await expect(page.locator("#receiptNumber")).toHaveText(id);
      await page.locator("#newRequest").click();
      await expect(page).toHaveURL(base + "/exam-service");
      await fill(page, marker + " 추가");
      await page.getByRole("button", { name: "접수하기", exact: true }).click();
      await expect(page.locator("#receiptStatus")).toContainText(
        "접수되었습니다",
      );
      const secondId = await page.locator("#receiptNumber").textContent();
      expect(secondId).not.toBe(id);
      await expect(page).toHaveURL(base + "/exam-service?request=" + secondId);
      await page.reload();
      await expect(page.locator("#receiptNumber")).toHaveText(secondId);
      const privacy = await page.evaluate(() => JSON.stringify(sessionStorage));
      expect(privacy).not.toContain(marker);
      expect(privacy).not.toContain("synthetic@example.invalid");
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth - innerWidth,
        ),
      ).toBe(0);
    } finally {
      await context.close();
      await api.dispose();
    }
  });
test("failed creation shows no success; lost committed response resolves after reload without duplicate", async ({
  browser,
}) => {
  const { api, context, page } = await session(browser);
  const marker = "응답 복구 " + Date.now();
  try {
    await fill(page, marker);
    let mode = "fail";
    await page.route("**/api/exam-intake/requests", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      if (mode === "fail")
        return route.fulfill({
          status: 503,
          contentType: "application/json",
          body: JSON.stringify({ message: "합성 저장 실패" }),
        });
      await route.fetch();
      return route.abort("failed");
    });
    await page.getByRole("button", { name: "접수하기", exact: true }).click();
    await expect(page.locator("#notice")).toContainText("합성 저장 실패");
    await expect(page.locator("#receipt")).toBeHidden();
    expect(await matching(api, marker)).toHaveLength(0);
    await page.screenshot({
      path: path.join(output, "ready-390-save-error.png"),
    });
    mode = "drop";
    await page.getByRole("button", { name: "접수하기", exact: true }).click();
    await expect.poll(async () => (await matching(api, marker)).length).toBe(1);
    await page.reload();
    await expect(page.locator("#receiptNumber")).toHaveText(
      (await matching(api, marker))[0].id,
    );
    expect(await matching(api, marker)).toHaveLength(1);
  } finally {
    await context.close();
    await api.dispose();
  }
});
test("upload failure retains receipt, retries only files and does not create a second request", async ({
  browser,
}) => {
  const { api, context, page } = await session(browser);
  const marker = "자료 복구 " + Date.now();
  try {
    await fill(page, marker);
    await page.locator("#initialFiles").setInputFiles(fixture);
    await page.locator("[name=initialMaterialConsent]").check();
    await page.route("**/api/exam-intake/requests/*/documents", (route) =>
      route.fulfill({
        status: 503,
        contentType: "application/json",
        body: '{"message":"합성 전송 실패"}',
      }),
    );
    await page.getByRole("button", { name: "접수하기", exact: true }).click();
    await expect(page.locator("#receiptStatus")).toContainText(
      "자료 1개는 제출 실패",
    );
    await expect(page.locator("#receiptStatus")).not.toContainText(
      "접수되었습니다",
    );
    const id = await page.locator("#receiptNumber").textContent();
    await page.screenshot({
      path: path.join(output, "ready-390-file-error.png"),
      fullPage: true,
    });
    await page.unroute("**/api/exam-intake/requests/*/documents");
    await page.locator("#retryInitialFiles").click();
    await expect(page.locator("#receiptStatus")).toContainText(
      "접수되었습니다",
    );
    await expect(page.locator("#documents")).toContainText("poster.jpg");
    expect((await matching(api, marker)).map((r) => r.id)).toEqual([id]);
  } finally {
    await context.close();
    await api.dispose();
  }
});
test("secondary list failure cannot lose a committed receipt or offer another submit", async ({
  browser,
}) => {
  const { api, context, page } = await session(browser);
  const marker = "상세 복구 " + Date.now();
  try {
    await fill(page, marker);
    await page.route("**/api/exam-intake/requests?page=*", (route) =>
      route.fulfill({
        status: 503,
        contentType: "application/json",
        body: '{"message":"합성 조회 실패"}',
      }),
    );
    await page.getByRole("button", { name: "접수하기", exact: true }).click();
    await expect(page.locator("#receiptStatus")).toContainText(
      "상세 조회에 실패",
    );
    await expect(page.locator("#newRequestSection")).toBeHidden();
    expect(await matching(api, marker)).toHaveLength(1);
  } finally {
    await context.close();
    await api.dispose();
  }
});
test("anonymous homepage application returns to form after existing login", async ({
  browser,
}) => {
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 900 },
    isMobile: true,
    hasTouch: true,
  });
  try {
    await ctx.route("**/*", (route) =>
      new URL(route.request().url()).origin === base
        ? route.continue()
        : route.abort(),
    );
    const page = await ctx.newPage();
    await page.goto(base);
    await page.locator("#homeIntakeCta").click();
    const modal = page.locator("[data-login-modal]");
    await expect(modal).toBeVisible();
    await modal.locator("[name=loginId]").fill("student");
    await modal.locator("[name=password]").fill("admin1234");
    await modal.locator("[type=submit]").click();
    await expect(page).toHaveURL(/\/exam-service$/);
    await expect(page.locator("#offerSummary")).toContainText("160문항");
  } finally {
    await ctx.close();
  }
});
