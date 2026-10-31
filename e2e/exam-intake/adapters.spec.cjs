const { test, expect, request } = require("@playwright/test");
const fs = require("fs"),
  path = require("path");
const base = process.env.INTAKE_ADAPTERS_BASE;
test("real local SMTP reception and ClamAV clean/infected handling", async () => {
  test.skip(
    !base,
    "Requires isolated SMTP inbox and real local ClamAV engine; never production",
  );
  const mailbox = process.env.INTAKE_MAILBOX_URL || "http://127.0.0.1:18025";
  for (const url of [base, mailbox])
    expect(["localhost", "127.0.0.1"]).toContain(new URL(url).hostname);
  const make = async (loginId) => {
    const a = await request.newContext({
      baseURL: base,
      extraHTTPHeaders: { Origin: base, "X-F2R-Exam": "1" },
    });
    expect(
      (
        await a.post("/api/auth/login", {
          data: { loginId, password: "admin1234" },
        })
      ).ok(),
    ).toBeTruthy();
    return a;
  };
  const user = await make("student"),
    admin = await make("admin"),
    inbox = await request.newContext();
  try {
    const cfg = await (await user.get("/api/exam-intake/config")).json();
    const key = require("crypto").randomUUID();
    const payload = {
      kind: "APPLICATION",
      consent: true,
      consentVersion: cfg.consentVersion,
      idempotencyKey: key,
      details: {
        name: "SMTP 검사 합성",
        school: "테스트중",
        grade: "중3",
        contactMethod: "EMAIL",
        contact: "synthetic@example.invalid",
        examDate: "2030-12-01",
        scope: "합성 자료",
        pastPapers: "YES",
        questionTypes: "독해",
        difficulty: "중간",
        format: "A4",
        ageBand: "OVER14",
        privacyVersion: cfg.privacyVersion,
        termsVersion: cfg.termsVersion,
        termsAccepted: "true",
        optionalConsent: "true",
      },
    };
    const saved = await user.post("/api/exam-intake/requests", {
      data: payload,
    });
    expect(saved.ok(), await saved.text()).toBeTruthy();
    const r = await saved.json();
    const file = fs.readFileSync(
      path.join(__dirname, "../unified-assistant/fixtures/poster.jpg"),
    );
    const clean = await user.post(
      `/api/exam-intake/requests/${r.id}/documents`,
      {
        multipart: {
          materialConsent: "true",
          file: { name: "synthetic.jpg", mimeType: "image/jpeg", buffer: file },
        },
      },
    );
    expect(clean.ok(), await clean.text()).toBeTruthy();
    const detail = await (
      await user.get(`/api/exam-intake/requests/${r.id}`)
    ).json();
    expect(detail.documents).toHaveLength(1);
    expect(detail.documents[0].scanState).toBe("CLEAN");
    const url = `/api/admin/exam-intake/requests/${r.id}/documents/${detail.documents[0].id}`;
    expect((await user.get(url)).status()).toBe(403);
    expect((await admin.get(url)).status()).toBe(200);
    const eicar = Buffer.from(
      "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*",
    );
    const bad = await user.post(`/api/exam-intake/requests/${r.id}/documents`, {
      multipart: {
        materialConsent: "true",
        file: {
          name: "safe-eicar-test.jpg",
          mimeType: "image/jpeg",
          buffer: Buffer.concat([file, eicar]),
        },
      },
    });
    expect(bad.status()).toBe(400);
    expect(await bad.text()).toContain("안전하지 않은");
    expect(
      (await (await user.get(`/api/exam-intake/requests/${r.id}`)).json())
        .documents,
    ).toHaveLength(1);
    const events = await (
      await admin.get("/api/admin/exam-intake/events")
    ).json();
    const event = events.find(
      (e) => e.request_id === r.id && e.event_type === "application.created",
    );
    expect(event).toBeTruthy();
    let message;
    await expect
      .poll(
        async () => {
          const m = await (
            await inbox.get(mailbox + "/api/v1/messages")
          ).json();
          message = m.messages.find((m) => m.Subject.includes(event.id));
          return Boolean(message);
        },
        { timeout: 60000 },
      )
      .toBe(true);
    const body = await (
      await inbox.get(mailbox + "/api/v1/message/" + message.ID)
    ).json();
    expect(body.Text).toContain(r.id);
    expect(body.Text).not.toContain(payload.details.contact);
    expect(body.Text).not.toContain(payload.details.name);
    expect(body.Text).not.toContain(payload.details.school);
    await expect
      .poll(
        async () =>
          (
            await (
              await user.get(`/api/exam-intake/requests/${r.id}/notification`)
            ).json()
          ).state,
      )
      .toBe("SENT");
    expect(
      (
        await (
          await user.get(`/api/exam-intake/requests/${r.id}/notification`)
        ).json()
      ).received,
    ).toBe(false);
    expect(
      (
        await admin.post(`/api/admin/exam-intake/events/${event.id}/received`, {
          data: { confirmedInMailbox: true },
        })
      ).ok(),
    ).toBe(true);
    expect(
      (
        await (
          await user.get(`/api/exam-intake/requests/${r.id}/notification`)
        ).json()
      ).received,
    ).toBe(true);
    expect(
      (
        await (
          await user.post("/api/exam-intake/requests", { data: payload })
        ).json()
      ).id,
    ).toBe(r.id);
    const out =
      process.env.INTAKE_EVIDENCE_DIR ||
      path.resolve(__dirname, "../../build/verification/exam-intake");
    fs.mkdirSync(out, { recursive: true });
    fs.writeFileSync(
      path.join(out, "local-adapters.json"),
      JSON.stringify(
        {
          localOnly: true,
          requestId: r.id,
          eventId: event.id,
          smtpReceived: true,
          recipientConfirmed: true,
          cleanScanned: true,
          eicarRejected: true,
          nonAdminFileDenied: true,
          productionDefinitionsVerified: false,
          productionVerified: false,
        },
        null,
        2,
      ),
    );
  } finally {
    await user.dispose();
    await admin.dispose();
    await inbox.dispose();
  }
});
