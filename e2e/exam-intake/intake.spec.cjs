const { test, expect, request } = require("@playwright/test");
const path = require("path"),
  fs = require("fs");
const base = "http://localhost:8091";
const headers = { Origin: base, "X-F2R-Exam": "1" };
const output =
  process.env.INTAKE_EVIDENCE_DIR ||
  path.resolve(__dirname, "../../build/verification/exam-intake");
async function logged(loginId) {
  const api = await request.newContext({
    baseURL: base,
    extraHTTPHeaders: headers,
  });
  const response = await api.post("/api/auth/login", {
    data: { loginId, password: "admin1234" },
  });
  expect(response.ok()).toBe(true);
  return api;
}
async function ok(response) {
  const r = await response;
  expect(r.ok(), await r.text()).toBe(true);
  return r.json();
}
async function create(api) {
  const cfg = await ok(api.get("/api/exam-intake/config"));
  return ok(
    api.post("/api/exam-intake/requests", {
      data: {
        kind: "APPLICATION",
        details: {
          name: "로컬 합성 신청자",
          school: "검증중학교",
          grade: "중3",
          contactMethod: "EMAIL",
          contact: "fixture@example.invalid",
          examDate: "2030-12-01",
          scope: "합성 시험범위",
          pastPapers: "YES",
          questionTypes: "독해",
          difficulty: "중간",
          format: "A4",
        },
        consent: true,
        consentVersion: cfg.consentVersion,
        idempotencyKey: require("crypto").randomUUID(),
      },
    }),
  );
}
test.describe.configure({ mode: "serial" });
test.beforeAll(async () => {
  fs.mkdirSync(output, { recursive: true });
  const a = await logged("admin");
  try {
    const current = await ok(a.get("/api/admin/exam-intake/bank"));
    if (current.bank)
      await ok(
        a.put("/api/admin/exam-intake/bank", {
          data: { ...current.bank, approved: false },
        }),
      );
  } finally {
    await a.dispose();
  }
});
for (const width of [360, 390, 430, 768, 1440])
  test(
    "intake " +
      width +
      ": real create/upload/request UI, bank unavailable and no overflow",
    async ({ browser }) => {
      const api = await logged("student");
      const context = await browser.newContext({
        storageState: await api.storageState(),
        viewport: { width, height: 900 },
        isMobile: width < 500,
        hasTouch: width < 500,
      });
      try {
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", (e) => errors.push(e.message));
        await context.route("**/*", (route) =>
          new URL(route.request().url()).origin === base
            ? route.continue()
            : route.abort(),
        );
        await page.goto("/exam-service");
        await expect(page.locator("#offerSummary")).toContainText("160문항");
        const form = page.locator("#intakeForm");
        for (const [name, value] of Object.entries({
          name: "합성 신청자",
          school: "테스트중",
          grade: "중3",
          contact: "fixture@example.invalid",
          examDate: "2030-12-01",
          questionTypes: "독해",
          difficulty: "중간",
          format: "A4",
          scope: "로컬 검증 자료",
        }))
          await form.locator('[name="' + name + '"]').fill(value);
        await form.locator('[name="consent"]').check();
        await form
          .getByRole("button", { name: "접수하기", exact: true })
          .click();
        await expect(page.locator("#notice")).toContainText("접수되었습니다");
        await expect(page.locator("#progress")).toContainText(
          "총 4회 · 160문항 · 20,000원",
        );
        await expect(page.locator("#paymentRequest")).toBeDisabled();
        await page
          .locator("#uploadForm input")
          .setInputFiles(
            path.join(__dirname, "../unified-assistant/fixtures/poster.jpg"),
          );
        await page.locator("#uploadForm button").click();
        await expect(page.locator("#documents")).toContainText(
          "격리·검사 대기",
        );
        await expect(page.locator("#documents a")).toHaveCount(0);
        await page
          .locator('#actionForm [name="body"]')
          .fill("시험 범위 수정 요청 — 합성");
        await page.locator("#actionForm button").click();
        await expect(page.locator("#history")).toContainText(
          "신청 내용 변경 요청",
        );
        expect(
          await page.evaluate(
            () => document.documentElement.scrollWidth - innerWidth,
          ),
        ).toBe(0);
        fs.mkdirSync(output, { recursive: true });
        await page.screenshot({
          path: path.join(output, width + "-detail.png"),
          fullPage: true,
        });
        await page.evaluate(() => scrollTo(0, 0));
        await page.screenshot({ path: path.join(output, width + "-form.png") });
        expect(errors).toEqual([]);
      } finally {
        await context.close();
        await api.dispose();
      }
    },
  );
test("inquiry form omits application requirements", async ({ browser }) => {
  const api = await logged("student");
  const context = await browser.newContext({
    storageState: await api.storageState(),
  });
  try {
    const page = await context.newPage();
    await page.goto("/exam-service");
    await expect(page.locator("#offerSummary")).toContainText("160문항");
    await page.locator('#intakeForm [name="kind"]').selectOption("INQUIRY");
    for (const [key, value] of Object.entries({
      name: "합성 문의자",
      school: "테스트중",
      grade: "중3",
      contact: "fixture@example.invalid",
      inquiryType: "일정",
      message: "합성 문의입니다",
    }))
      await page.locator('#intakeForm [name="' + key + '"]').fill(value);
    await page.locator('[name="consent"]').check();
    await page.locator("#intakeForm button").click();
    await expect(page.locator("#notice")).toContainText("접수되었습니다");
    await expect(page.locator("#details")).toContainText("합성 문의입니다");
  } finally {
    await context.close();
    await api.dispose();
  }
});
test("ownership, forged payment, quarantine and CSRF are enforced by actual API", async () => {
  const owner = await logged("student"),
    admin = await logged("admin"),
    anon = await request.newContext({
      baseURL: base,
      extraHTTPHeaders: headers,
    });
  try {
    const r = await create(owner);
    expect((await anon.get("/api/exam-intake/requests/" + r.id)).status()).toBe(
      401,
    );
    expect(
      (
        await owner.post("/api/admin/exam-intake/requests/" + r.id + "/state", {
          data: { version: r.version, state: "CONFIRMED" },
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await owner.post("/api/exam-intake/requests/" + r.id + "/actions", {
          headers: { Origin: "https://attacker.invalid" },
          data: { version: r.version, kind: "CANCEL", body: "bad origin" },
        })
      ).status(),
    ).toBe(403);
    await ok(
      owner.post("/api/exam-intake/requests/" + r.id + "/documents", {
        multipart: {
          file: {
            name: "synthetic.jpg",
            mimeType: "image/jpeg",
            buffer: fs.readFileSync(
              path.join(__dirname, "../unified-assistant/fixtures/poster.jpg"),
            ),
          },
        },
      }),
    );
    const d = await ok(owner.get("/api/exam-intake/requests/" + r.id));
    expect(d.documents).toHaveLength(1);
    expect(
      (
        await owner.get(
          "/api/admin/exam-intake/requests/" +
            r.id +
            "/documents/" +
            d.documents[0].id,
        )
      ).status(),
    ).toBe(403);
    expect(
      (
        await admin.get(
          "/api/admin/exam-intake/requests/" +
            r.id +
            "/documents/" +
            d.documents[0].id,
        )
      ).status(),
    ).toBe(409);
    expect(JSON.stringify(d)).not.toContain("storageName");
    expect(JSON.stringify(d)).not.toContain("private-test-files");
  } finally {
    await owner.dispose();
    await admin.dispose();
    await anon.dispose();
  }
});
test("admin bank save/approve, manual confirmation, four deliveries and refund gate", async ({
  browser,
}) => {
  const owner = await logged("student"),
    admin = await logged("admin");
  try {
    let bank = await ok(admin.get("/api/admin/exam-intake/bank"));
    const v = bank.bank ? bank.bank.version : 0;
    bank = await ok(
      admin.put("/api/admin/exam-intake/bank", {
        data: {
          bankName: "LOCAL TEST BANK",
          accountNumber: "NOT-A-REAL-ACCOUNT",
          accountHolder: "SYNTHETIC",
          approved: true,
          version: v,
        },
      }),
    );
    expect(bank.approved).toBe(false);
    expect((await ok(owner.get("/api/exam-intake/bank"))).available).toBe(
      false,
    );
    await ok(
      admin.post("/api/admin/exam-intake/bank/approve", {
        data: { version: bank.version },
      }),
    );
    expect((await ok(owner.get("/api/exam-intake/bank"))).available).toBe(true);
    let r = await create(owner);
    r = await ok(
      owner.post(
        "/api/exam-intake/requests/" + r.id + "/payment-confirmation",
        { data: { version: r.version, payer: "합성 입금자 — 실제 입금 없음" } },
      ),
    );
    expect(r.state).toBe("CONFIRMATION_REQUESTED");
    r = await ok(
      admin.post("/api/admin/exam-intake/requests/" + r.id + "/state", {
        data: { version: r.version, state: "CONFIRMED" },
      }),
    );
    r = await ok(
      admin.post("/api/admin/exam-intake/requests/" + r.id + "/state", {
        data: { version: r.version, state: "PRODUCTION" },
      }),
    );
    expect(
      (
        await owner.post("/api/exam-intake/requests/" + r.id + "/actions", {
          data: { version: r.version, kind: "UPDATE_DETAILS", body: "변경" },
        })
      ).status(),
    ).toBe(409);
    for (let ordinal = 1; ordinal <= 4; ordinal++)
      r = await ok(
        admin.post("/api/admin/exam-intake/requests/" + r.id + "/deliveries", {
          data: {
            version: r.version,
            ordinal,
            note: "합성 전달 완료 기록 — 실제 전달 없음",
          },
        }),
      );
    expect(r.state).toBe("DELIVERED");
    r = await ok(
      owner.post("/api/exam-intake/requests/" + r.id + "/actions", {
        data: { version: r.version, kind: "REFUND", body: "합성 환불 검토" },
      }),
    );
    const detail = await ok(admin.get("/api/exam-intake/requests/" + r.id));
    const a = detail.actions.find((a) => a.kind === "REFUND");
    expect(a.amountWon).toBeNull();
    r = await ok(
      admin.post(
        "/api/admin/exam-intake/requests/" + r.id + "/actions/" + a.id,
        {
          data: {
            version: r.version,
            state: "REVIEWING",
            note: "합성 검토",
            amount: null,
          },
        },
      ),
    );
    expect(
      (
        await admin.post(
          "/api/admin/exam-intake/requests/" + r.id + "/actions/" + a.id,
          {
            data: {
              version: r.version,
              state: "APPROVED",
              note: "미승인 정책",
              amount: 15000,
            },
          },
        )
      ).status(),
    ).toBe(409);
    const context = await browser.newContext({
      storageState: await admin.storageState(),
      viewport: { width: 1440, height: 1050 },
    });
    try {
      const page = await context.newPage();
      await page.goto("/admin/hub/exam-requests");
      await expect(page.locator("#requests")).toContainText(r.id);
      await page
        .locator("#requests article")
        .filter({ hasText: r.id })
        .getByRole("button")
        .click();
      await expect(page.locator("#progress")).toContainText("4/4회");
      await page.screenshot({
        path: path.join(output, "admin-review.png"),
        fullPage: true,
      });
    } finally {
      await context.close();
    }
  } finally {
    await owner.dispose();
    await admin.dispose();
  }
});
