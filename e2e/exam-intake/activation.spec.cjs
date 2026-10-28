const { test, expect, request } = require("@playwright/test");
const fs = require("fs"),
  path = require("path");
const output =
  process.env.INTAKE_EVIDENCE_DIR ||
  path.resolve(__dirname, "../../build/verification/exam-intake");
async function logged(port, loginId = "student") {
  const base = `http://localhost:${port}`;
  const api = await request.newContext({
    baseURL: base,
    extraHTTPHeaders: { Origin: base, "X-F2R-Exam": "1" },
  });
  expect(
    (
      await api.post("/api/auth/login", {
        data: { loginId, password: "admin1234" },
      })
    ).ok(),
  ).toBe(true);
  return api;
}
async function ok(p) {
  const r = await p;
  expect(r.ok(), await r.text()).toBe(true);
  return r.json();
}
async function input(api, extra = {}) {
  const cfg = await ok(api.get("/api/exam-intake/config"));
  return {
    kind: "APPLICATION",
    details: {
      name: "합성 학생",
      school: "합성중",
      grade: "중1",
      contactMethod: "EMAIL",
      contact: "test@example.invalid",
      examDate: "2030-12-01",
      scope: "합성 범위",
      pastPapers: "NO",
      questionTypes: "독해",
      difficulty: "중",
      format: "A4",
      ageBand: "OVER14",
      privacyVersion: cfg.privacyVersion,
      termsVersion: cfg.termsVersion,
      termsAccepted: "true",
      ...extra,
    },
    consent: true,
    consentVersion: cfg.consentVersion,
    idempotencyKey: require("crypto").randomUUID(),
  };
}
test.describe.configure({ mode: "serial" });
for (const width of [360, 390, 430, 1440])
  test(`bank preparation with intake OFF ${width}`, async ({ browser }) => {
    const api = await logged(8092, "admin"),
      student = await logged(8092),
      context = await browser.newContext({
        baseURL: "http://localhost:8092",
        storageState: await api.storageState(),
        viewport: { width, height: 900 },
        isMobile: width < 500,
        hasTouch: width < 500,
      });
    try {
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(e.message));
      await page.goto("/admin/hub/exam-bank");
      await expect(page.locator("#bankStatus")).not.toBeEmpty();
      for (const [k, v] of Object.entries({
        bankName: "합성은행",
        accountNumber: "LOCAL-ONLY-0000",
        accountHolder: "합성 예금주",
      }))
        await page.locator(`#bankForm [name="${k}"]`).fill(v);
      await page.locator("#bankForm button").click();
      await expect(page.locator("#bankStatus")).toContainText("승인 전");
      await page.locator("#approveBank").click();
      await expect(page.locator("#bankStatus")).toContainText(
        "고객 계좌 공개는 아직 비활성",
      );
      const b = await ok(api.get("/api/admin/exam-intake/bank"));
      expect(b.bank.approved).toBe(true);
      expect(b.customerVisible).toBe(false);
      for (const url of [
        "/api/exam-intake/bank",
        "/api/exam-intake/requests",
        "/api/exam-intake/config",
      ]) {
        const r = await student.get(url);
        expect(r.status()).not.toBe(200);
        expect(await r.text()).not.toContain("LOCAL-ONLY-0000");
      }
      expect((await student.get("/api/admin/exam-intake/bank")).status()).toBe(
        403,
      );
      expect((await student.get("/admin/hub/exam-bank")).status()).toBe(403);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth - innerWidth,
        ),
      ).toBe(0);
      expect(errors).toEqual([]);
      fs.mkdirSync(output, { recursive: true });
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({
        path: path.join(output, `${width}-bank-preparation-off.png`),
        fullPage: true,
      });
    } finally {
      await context.close();
      await api.dispose();
      await student.dispose();
    }
  });
test("bank publishing gate also redacts historical payment snapshots", async () => {
  const admin = await logged(8092, "admin"),
    customer = await logged(8091),
    hidden = await logged(8093),
    anon = await request.newContext({ baseURL: "http://localhost:8092" });
  try {
    expect((await anon.get("/api/admin/exam-intake/bank")).status()).toBe(401);
    const csrf = await admin.put("/api/admin/exam-intake/bank", {
      headers: { Origin: "https://wrong-origin.invalid" },
      data: {
        bankName: "x",
        accountNumber: "x",
        accountHolder: "x",
        approved: false,
        version: 0,
      },
    });
    expect(csrf.status()).toBe(403);
    const r = await ok(
      customer.post("/api/exam-intake/requests", {
        data: await input(customer),
      }),
    );
    await ok(
      customer.post(`/api/exam-intake/requests/${r.id}/payment-confirmation`, {
        data: { version: r.version, payer: "합성 입금자" },
      }),
    );
    expect((await ok(hidden.get("/api/exam-intake/bank"))).available).toBe(
      false,
    );
    expect(
      JSON.stringify(await ok(hidden.get(`/api/exam-intake/requests/${r.id}`))),
    ).not.toContain("LOCAL-ONLY-0000");
    const b = (await ok(admin.get("/api/admin/exam-intake/bank"))).bank;
    await ok(
      admin.put("/api/admin/exam-intake/bank", {
        data: { ...b, approved: true },
      }),
    );
    expect((await ok(customer.get("/api/exam-intake/bank"))).available).toBe(
      false,
    );
    expect(
      JSON.stringify(
        await ok(customer.get(`/api/exam-intake/requests/${r.id}`)),
      ),
    ).not.toContain("LOCAL-ONLY-0000");
  } finally {
    await Promise.all([
      admin.dispose(),
      customer.dispose(),
      hidden.dispose(),
      anon.dispose(),
    ]);
  }
});
test("child intake waits for adult electronic consent and administrator telephone verification", async ({
  browser,
}) => {
  const user = await logged(8091),
    admin = await logged(8091, "admin"),
    ctx = await browser.newContext({
      baseURL: "http://localhost:8091",
      storageState: await user.storageState(),
      viewport: { width: 390, height: 900 },
      isMobile: true,
      hasTouch: true,
    });
  try {
    const page = await ctx.newPage();
    await page.goto("/exam-service");
    await expect(page.locator("#offerSummary")).not.toBeEmpty();
    await page.locator('[name="ageBand"]').selectOption("UNDER14");
    await expect(page.locator("#intakeData")).toHaveJSProperty(
      "disabled",
      true,
    );
    await expect(page.locator('#intakeData input[name="name"]')).toBeDisabled();
    await expect(
      page.locator('#intakeData button[type="submit"]'),
    ).toBeDisabled();
    const cfg = await ok(user.get("/api/exam-intake/config"));
    const submission = {
      guardianName: "합성 법정대리인",
      phone: "010-0000-0000",
      relationship: "PARENT",
      adultGuardian: true,
      consent: true,
      privacyVersion: cfg.privacyVersion,
      termsVersion: cfg.termsVersion,
    };
    const pending = await ok(
      user.post("/api/exam-intake/guardian-consents", { data: submission }),
    );
    expect(
      (
        await user.post("/api/exam-intake/requests", {
          data: await input(user, {
            ageBand: "UNDER14",
            guardianConsentId: pending.id,
          }),
        })
      ).status(),
    ).toBe(409);
    expect(
      (
        await user.post(
          `/api/admin/exam-intake/guardian-consents/${pending.id}/verify`,
          {
            data: {
              identityChecked: true,
              authorityChecked: true,
              explainedAndAgreed: true,
            },
          },
        )
      ).status(),
    ).toBe(403);
    await ok(
      admin.post(
        `/api/admin/exam-intake/guardian-consents/${pending.id}/reject`,
        { data: { result: "UNVERIFIED" } },
      ),
    );
    expect(
      (
        await user.post("/api/exam-intake/requests", {
          data: await input(user, {
            ageBand: "UNDER14",
            guardianConsentId: pending.id,
          }),
        })
      ).status(),
    ).toBe(409);
    const grant = await ok(
      user.post("/api/exam-intake/guardian-consents", { data: submission }),
    );
    const v = await ok(
      admin.post(
        `/api/admin/exam-intake/guardian-consents/${grant.id}/verify`,
        {
          data: {
            identityChecked: true,
            authorityChecked: true,
            explainedAndAgreed: true,
          },
        },
      ),
    );
    expect(v.verification_method).toBe("PHONE_EXPLAIN_AND_CONFIRM");
    expect(v.consent_items).toContain(cfg.privacyVersion);
    expect(v.verified_at).toBeTruthy();
    await page.reload();
    await expect(page.locator("#offerSummary")).not.toBeEmpty();
    await page.locator('[name="ageBand"]').selectOption("UNDER14");
    await expect(
      page.locator(`[name="guardianConsentId"] option[value="${grant.id}"]`),
    ).toHaveCount(1);
    await page.locator('[name="guardianConsentId"]').selectOption(grant.id);
    await expect(page.locator("#intakeData")).toHaveJSProperty(
      "disabled",
      false,
    );
    await expect(page.locator('#intakeData input[name="name"]')).toBeEnabled();
    await expect(page.locator('[name="consent"]')).not.toBeChecked();
    await expect(page.locator('[name="termsAccepted"]')).not.toBeChecked();
    const r = await ok(
      user.post("/api/exam-intake/requests", {
        data: await input(user, {
          ageBand: "UNDER14",
          guardianConsentId: grant.id,
        }),
      }),
    );
    expect(
      (
        await user.post("/api/exam-intake/requests", {
          data: await input(user, {
            ageBand: "UNDER14",
            guardianConsentId: grant.id,
          }),
        })
      ).status(),
    ).toBe(409);
    await ok(
      user.post(`/api/exam-intake/guardian-consents/${grant.id}/revoke`, {
        data: {},
      }),
    );
    expect(
      (
        await user.post(`/api/exam-intake/requests/${r.id}/documents`, {
          multipart: {
            materialConsent: "true",
            file: {
              name: "test.jpg",
              mimeType: "image/jpeg",
              buffer: fs.readFileSync(
                path.join(
                  __dirname,
                  "../unified-assistant/fixtures/poster.jpg",
                ),
              ),
            },
          },
        })
      ).status(),
    ).toBe(409);
    expect(
      (await ok(user.get(`/api/exam-intake/requests/${r.id}`))).documents,
    ).toHaveLength(0);
    await page.goto("/exam-service/guardian");
    await expect(page.locator("#consents")).toContainText("철회됨");
    fs.mkdirSync(output, { recursive: true });
    await page.screenshot({
      path: path.join(output, "390-guardian-consent.png"),
      fullPage: true,
    });
  } finally {
    await ctx.close();
    await user.dispose();
    await admin.dispose();
  }
});
test("privacy and pre-purchase terms are public independently of intake", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 960 });
  for (const endpoint of ["privacy", "terms"]) {
    const r = await page.goto(`http://localhost:8092/exam-service/${endpoint}`);
    expect(r.status()).toBe(200);
    await expect(page.locator("#privacy")).toContainText("법정대리인");
    await expect(page.locator("#privacy")).toContainText("30일");
    await expect(page.locator("#terms table tbody tr")).toHaveCount(5);
    for (const amount of ["20,000원", "15,000원", "10,000원", "5,000원", "0원"])
      await expect(page.locator("#terms table")).toContainText(amount);
  }
  await page.screenshot({
    path: path.join(output, "1440-policy-terms.png"),
    fullPage: true,
  });
});
