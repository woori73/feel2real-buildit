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
          ageBand: "OVER14",
          privacyVersion: cfg.privacyVersion,
          termsVersion: cfg.termsVersion,
          termsAccepted: "true",
          optionalConsent: "true",
          name: "로컬 합성 신청자",
          school: "검증중학교",
          grade: "중3",
          contactMethod: "EMAIL",
          contact: "fixture@example.invalid",
          scope: "합성 시험범위",
          pastPapers: "YES",
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
          format: "A4",
        }))
          await form.locator('[name="' + name + '"]').fill(value);
        await form.locator('[name="consent"]').check();
        await form.locator('[name="termsAccepted"]').check();
        await form
          .getByRole("button", { name: "접수하기", exact: true })
          .click();
        await expect(page.locator("#notice")).toContainText("접수되었습니다");
        await expect(page.locator("#progress")).toContainText(
          "총 4회 · 160문항 · 20,000원",
        );
        await expect(page.locator("#paymentRequest")).toBeDisabled();
        await page
          .locator('#uploadForm input[type="file"]')
          .setInputFiles(
            path.join(__dirname, "../unified-assistant/fixtures/poster.jpg"),
          );
        await page.locator('#uploadForm [name="materialConsent"]').check();
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
test("simplified form omits age and kind selectors and collects requirements", async ({ browser }) => {
  const api = await logged("student");
  const context = await browser.newContext({
    storageState: await api.storageState(),
  });
  try {
    const page = await context.newPage();
    await page.goto("/exam-service");
    await expect(page.locator("#offerSummary")).toContainText("160문항");
    await expect(page.locator('#intakeForm select[name="kind"]')).toHaveCount(0);
    await expect(page.locator('#intakeForm [name="ageBand"]')).toHaveCount(0);
    await expect(page.locator('#intakeForm [name="examDate"], #intakeForm [name="scope"], #intakeForm [name="pastPapers"], #intakeForm [name="questionTypes"], #intakeForm [name="difficulty"]')).toHaveCount(0);
    await expect(page.locator('#intakeForm [name="name"]')).toBeEnabled();
    for (const [key, value] of Object.entries({
      name: "합성 문의자",
      school: "테스트중",
      grade: "중3",
      contact: "fixture@example.invalid",
      format: "A4",
      requirements: "합성 출제 요구사항입니다",
    }))
      await page.locator('#intakeForm [name="' + key + '"]').fill(value);
    await page.locator('[name="consent"]').check();
    await page.locator('[name="optionalConsent"]').check();
    await page.locator('[name="termsAccepted"]').check();
    await page.locator("#intakeForm button").click();
    await expect(page.locator("#notice")).toContainText("접수되었습니다");
    await expect(page.locator("#details")).toContainText("합성 출제 요구사항입니다");
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
          materialConsent: "true",
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
            accessibleAndReviewed: true,
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

test("bank import stays unapproved; approved account, clipboard and Kakao acknowledgement at all widths", async ({
  browser,
}) => {
  const a = await logged("admin"),
    owner = await logged("student");
  const bank = process.env.F2R_LOCAL_BANK_FILE
    ? JSON.parse(fs.readFileSync(process.env.F2R_LOCAL_BANK_FILE, "utf8"))
    : {
        bankName: "SYNTHETIC BANK",
        accountNumber: "TEST-ACCOUNT-ONLY",
        accountHolder: "LOCAL FIXTURE",
      };
  const context = await browser.newContext({
    storageState: await a.storageState(),
    viewport: { width: 1440, height: 1050 },
  });
  try {
    const page = await context.newPage();
    await page.goto("/admin/hub/exam-requests");
    await expect(page.locator("#bankStatus")).toContainText("승인됨");
    await page.locator("#bankImport").setInputFiles({
      name: "registration.json",
      mimeType: "application/json",
      buffer: Buffer.from(JSON.stringify(bank)),
    });
    await expect(
      page.locator('#bankForm input[name="accountNumber"]'),
    ).toHaveValue(bank.accountNumber);
    await expect(page.locator("#approveBank")).toBeDisabled();
    await page.locator('#bankForm button[type="submit"]').click();
    await expect(page.locator("#bankStatus")).toContainText(
      "승인 전에는 비공개",
    );
    expect((await ok(owner.get("/api/exam-intake/bank"))).available).toBe(
      false,
    );
    await page.locator("#approveBank").click();
    await expect(page.locator("#bankStatus")).toContainText("승인됨");
    const saved = await ok(a.get("/api/admin/exam-intake/bank"));
    expect(saved.bank.approved).toBe(true);
    expect(saved.bank.accountNumber).toBe(bank.accountNumber);
    await page.screenshot({
      path: path.join(output, "bank-admin-approved.png"),
      fullPage: true,
    });
    for (const width of [360, 390, 430, 768, 1440]) {
      const r = await create(owner);
      const c = await browser.newContext({
        storageState: await owner.storageState(),
        viewport: { width, height: 960 },
        isMobile: width < 768,
        hasTouch: width < 768,
      });
      try {
        const p = await c.newPage();
        await p.addInitScript(() => {
          window.__copies = [];
          Object.defineProperty(navigator, "clipboard", {
            configurable: true,
            value: { writeText: async (text) => window.__copies.push(text) },
          });
        });
        await p.goto("/exam-service");
        await p
          .locator("#requests article")
          .filter({ hasText: r.id })
          .getByRole("button")
          .click();
        await expect(p.locator("#bankGuide")).toContainText(bank.bankName);
        await expect(p.locator("#bankGuide")).toContainText(bank.accountNumber);
        await expect(p.locator("#bankGuide")).toContainText(bank.accountHolder);
        await expect(p.locator("#bankGuide")).toContainText("20,000원");
        await expect(p.locator("#refundRows tr")).toHaveCount(5);
        await expect(p.locator("#refundEstimate")).toContainText("20,000원");
        await p.locator("#copyAccount").click();
        await expect(p.locator("#copyStatus")).toHaveText("복사했습니다.");
        expect(await p.evaluate(() => window.__copies[0])).toBe(
          bank.accountNumber,
        );
        await p.locator('#paymentForm input[name="payer"]').fill("합성 입금자");
        await expect(p.locator("#paymentMessage")).toHaveValue(
          new RegExp(r.id),
        );
        await expect(p.locator("#paymentMessage")).toHaveValue(/합성 입금자/);
        await expect(p.locator("#paymentKakao")).toHaveAttribute(
          "href",
          "https://open.kakao.com/o/sMf4ifRi",
        );
        expect(
          await p.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth + 1,
          ),
        ).toBe(true);
        await p.locator("#bankGuide").scrollIntoViewIfNeeded();
        await p.screenshot({
          path: path.join(output, `${width}-bank-guide.png`),
        });
        await p.evaluate(() =>
          Object.defineProperty(navigator, "clipboard", {
            configurable: true,
            value: {
              writeText: async () => {
                throw new Error("denied");
              },
            },
          }),
        );
        await p.locator("#copyAccount").click();
        await expect(p.locator("#copyStatus")).toContainText("직접 복사");
        await p.evaluate(() =>
          Object.defineProperty(navigator, "clipboard", {
            configurable: true,
            value: undefined,
          }),
        );
        await p.locator("#copyAccount").click();
        await expect(p.locator("#copyStatus")).toContainText("직접 복사");
        await p.locator("#paymentRequest").click();
        await expect(p.locator("#notice")).toContainText("관리자 확인 전");
        await expect(p.locator("#copyAccount")).toBeHidden();
        await expect(p.locator("#bankGuide")).toContainText(
          "추가 입금하지 마세요",
        );
        const detail = await ok(a.get("/api/exam-intake/requests/" + r.id));
        expect(detail.request.state).toBe("CONFIRMATION_REQUESTED");
        const payment = JSON.parse(
          detail.actions.find((x) => x.kind === "PAYMENT_CONFIRMATION").body,
        );
        expect(payment.payer).toBe("합성 입금자");
        expect(payment.bank.accountNumber).toBe(bank.accountNumber);
        await p.locator("#kakaoConfirmation").scrollIntoViewIfNeeded();
        await p.screenshot({
          path: path.join(output, `${width}-payment-requested.png`),
        });
      } finally {
        await c.close();
      }
    }
  } finally {
    await context.close();
    await a.dispose();
    await owner.dispose();
  }
});

test("public refund guide renders with the confirmed publisher seller info", async ({
  page,
}) => {
  const response = await page.goto("/faq#exam-service-terms");
  expect(response.status()).toBe(200);
  await expect(page.locator("#exam-service-terms")).toContainText("20,000원");
  await expect(page.locator("#exam-service-terms")).toContainText("15,000원");
  await expect(page.locator("#exam-service-terms")).toContainText(
    "위 금액과 관계없이 해당 부분을 환불합니다",
  );
  await expect(page.locator("#exam-service-terms")).not.toContainText("예정액");
  // 2026-10-28 확정 출판사 정보는 항상 표시한다(학원 정보와 섞지 않음).
  await expect(page.locator("#examSellerDetails")).toContainText("압구정에듀홀릭출판사");
  await expect(page.locator("#examSellerDetails")).toContainText("146-99-01347");
  await expect(page.locator("#examSellerDetails")).toContainText("제2026-서울송파-1411호");
  await expect(page.locator("#examSellerDetails")).not.toContainText("759-91-01977");
  await page.screenshot({
    path: path.join(output, "refund-seller-review.png"),
    fullPage: true,
  });
});
