(function () {
  "use strict";
  const $ = (id) => document.getElementById(id),
    admin = document.body.dataset.admin === "true";
  const labels = {
    RECEIVED: "신청 접수",
    AWAITING_DEPOSIT: "입금 대기",
    CONFIRMATION_REQUESTED: "입금 확인 요청",
    CONFIRMED: "입금 확인 완료",
    PRODUCTION: "제작 중",
    DELIVERED: "전달 완료",
    COMPLETE: "처리 완료",
    CANCELLED: "취소 완료",
  };
  const actionLabels = {
    UPDATE_DETAILS: "신청 내용 변경 요청",
    DELETE_DOCUMENT: "자료 삭제 요청",
    REVISION: "문제 수정 요청",
    CANCEL: "취소 요청",
    REFUND: "환불 검토 요청",
    STATE_CHANGED: "처리 상태 변경",
    PAYMENT_CONFIRMATION: "입금 확인 요청",
    DELIVERED: "회차 전달",
    ACTION_REVIEW: "요청 처리 기록",
    DOCUMENT_RESCAN: "파일 검사 재시도",
    DETAILS_AMENDED: "승인된 신청 내용 변경",
  };
  const reviewLabels = {
    REQUESTED: "접수",
    REVIEWING: "검토 중",
    APPROVED: "승인",
    REJECTED: "반려",
    RESOLVED: "처리 완료",
    REMITTED: "환불 완료 기록",
    RECORDED: "기록됨",
  };
  const fieldLabels = {
    name: "신청자",
    school: "학교",
    grade: "학년",
    contactMethod: "연락 방법",
    contact: "연락처",
    examDate: "시험일",
    scope: "시험 범위",
    pastPapers: "기출 보유",
    questionTypes: "문제 유형",
    difficulty: "난이도",
    format: "시험지 형식",
    requirements: "추가 요구사항",
    inquiryType: "문의 유형",
    message: "문의 내용",
  };
  let paymentAllowed = false,
    config,
    current,
    bankVersion = 0,
    page = 0,
    intakeKey = crypto.randomUUID().replaceAll("-", "");
  function note(message) {
    $("notice").textContent = message;
  }
  function el(tag, text) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    return node;
  }
  async function api(path, method = "GET", body) {
    const init = {
      method,
      credentials: "same-origin",
      headers: { "X-F2R-Exam": "1" },
    };
    if (body instanceof FormData) init.body = body;
    else if (body != null) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    const r = await fetch(path, init);
    const value = await r.json().catch(() => ({}));
    if (!r.ok)
      throw new Error(
        value.message ||
          "요청을 처리하지 못했습니다. 로그인과 연결을 확인해 주세요.",
      );
    return value;
  }
  function control(form, work) {
    if (!form) return;
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const button = form.querySelector('[type="submit"]');
      button.disabled = true;
      try {
        await work(new FormData(form));
      } catch (e) {
        note(e.message);
      } finally {
        if (button.isConnected)
          button.disabled = button.id === "paymentRequest" && !paymentAllowed;
      }
    });
  }
  function button(text, work) {
    const node = el("button", text);
    node.type = "button";
    node.addEventListener("click", async () => {
      node.disabled = true;
      try {
        await work();
      } catch (e) {
        note(e.message);
      } finally {
        if (node.isConnected) node.disabled = false;
      }
    });
    return node;
  }
  async function list() {
    const items = await api(
      (admin
        ? "/api/admin/exam-intake/requests"
        : "/api/exam-intake/requests") +
        "?page=" +
        page,
    );
    $("requests").replaceChildren();
    if (!items.length) $("requests").append(el("p", "접수 내역이 없습니다."));
    items.forEach((r) => {
      const a = el("article");
      a.append(
        el("strong", r.id),
        el(
          "p",
          (r.kind === "INQUIRY" ? "문의" : "신청") +
            " · " +
            r.details.school +
            " " +
            r.details.grade +
            " · " +
            labels[r.state],
        ),
        button("접수 상세 보기", () => detail(r.id)),
      );
      $("requests").append(a);
    });
    $("pageNumber").textContent = String(page + 1);
    $("previous").disabled = page === 0;
    $("next").disabled = items.length < 50;
    if (admin) await events();
  }
  async function detail(id) {
    const value = await api("/api/exam-intake/requests/" + id);
    current = value.request;
    $("requestDetail").hidden = false;
    $("detailTitle").textContent = "접수 번호 " + id;
    $("details").replaceChildren();
    const dl = el("dl");
    Object.entries(current.details).forEach(([k, v]) => {
      if (v) dl.append(el("dt", fieldLabels[k] || k), el("dd", v));
    });
    $("details").append(el("p", labels[current.state]), dl);
    $("progress").textContent =
      "계약: " +
      current.offer.durationDays +
      "일 · 총 " +
      current.offer.totalDeliveries +
      "회 · " +
      current.offer.totalQuestions +
      "문항 · " +
      Number(current.offer.priceWon).toLocaleString("ko-KR") +
      "원 / 전달 " +
      value.deliveries.length +
      "/" +
      current.offer.totalDeliveries +
      "회";
    const before = [
      "RECEIVED",
      "AWAITING_DEPOSIT",
      "CONFIRMATION_REQUESTED",
      "CONFIRMED",
    ].includes(current.state);
    $("uploadForm").hidden = !before;
    $("actionForm").elements.kind.querySelector(
      '[value="UPDATE_DETAILS"]',
    ).disabled = !before;
    if (!before) $("actionForm").elements.kind.value = "REVISION";
    if (admin) {
      const approved = value.actions.filter(
        (a) => a.kind === "UPDATE_DETAILS" && a.state === "APPROVED",
      );
      $("amendForm").hidden = !before || !approved.length;
      $("amendForm").elements.actionId.replaceChildren();
      approved.forEach((a) => {
        const o = el("option", a.body);
        o.value = a.id;
        $("amendForm").elements.actionId.append(o);
      });
      $("amendFields").replaceChildren();
      Object.entries(current.details).forEach(([key, value]) => {
        const label = el("label", fieldLabels[key] || key);
        const input = el(
          ["scope", "requirements", "message"].includes(key)
            ? "textarea"
            : "input",
        );
        input.name = key;
        input.value = value;
        input.maxLength = ["scope", "requirements", "message"].includes(key)
          ? 2000
          : 120;
        label.append(input);
        $("amendFields").append(label);
      });
    }
    $("documents").replaceChildren();
    value.documents.forEach((d) => {
      const card = el("article");
      card.append(
        el(
          "p",
          d.name +
            " · " +
            Math.ceil(d.size / 1024) +
            "KiB · " +
            (d.hidden
              ? "비공개 처리"
              : d.scanState === "CLEAN"
                ? "검사 완료"
                : "격리·검사 대기"),
        ),
      );
      if (admin && !d.hidden && d.scanState === "CLEAN") {
        const a = el("a", "관리자 자료 다운로드");
        a.href = "/api/admin/exam-intake/requests/" + id + "/documents/" + d.id;
        card.append(a);
      }
      if (admin && !d.hidden && d.scanState !== "CLEAN")
        card.append(
          button("파일 안전 검사 다시 시도", async () => {
            await api(
              "/api/admin/exam-intake/requests/" +
                id +
                "/documents/" +
                d.id +
                "/rescan",
              "POST",
              {},
            );
            await detail(id);
            note("검사 상태를 갱신했습니다.");
          }),
        );
      if (before && !d.hidden)
        card.append(
          button("자료 삭제 요청", async () => {
            await api("/api/exam-intake/requests/" + id + "/actions", "POST", {
              version: current.version,
              kind: "DELETE_DOCUMENT",
              body: d.id,
            });
            await detail(id);
            note("삭제 요청을 접수했습니다. 관리자 처리 후 반영됩니다.");
          }),
        );
      $("documents").append(card);
    });
    $("history").replaceChildren();
    value.actions.forEach((a) => {
      const card = el("article");
      card.append(
        el(
          "strong",
          (actionLabels[a.kind] || a.kind) +
            " · " +
            (reviewLabels[a.state] || a.state),
        ),
        el("p", a.body),
      );
      if (a.amountWon != null)
        card.append(
          el(
            "p",
            "검토 승인 금액 " + a.amountWon.toLocaleString("ko-KR") + "원",
          ),
        );
      if (
        admin &&
        !(a.kind === "UPDATE_DETAILS" && a.state === "APPROVED") &&
        !["RECORDED", "RESOLVED", "REMITTED", "REJECTED"].includes(a.state)
      ) {
        const form = el("form"),
          select = el("select");
        select.name = "state";
        const next =
          a.state === "REQUESTED"
            ? ["REVIEWING", "REJECTED"]
            : a.state === "REVIEWING"
              ? ["APPROVED", "REJECTED"]
              : ["REFUND", "CANCEL"].includes(a.kind)
                ? ["REMITTED"]
                : ["RESOLVED"];
        next.forEach((s) => {
          const o = el("option", reviewLabels[s] || s);
          o.value = s;
          select.append(o);
        });
        const label = el("label", "처리 상태");
        label.append(select);
        const reason = el("textarea");
        reason.name = "note";
        reason.required = true;
        reason.maxLength = 1000;
        const reasonLabel = el("label", "처리 근거");
        reasonLabel.append(reason);
        form.append(label, reasonLabel);
        if (["REFUND", "CANCEL"].includes(a.kind) && a.state === "REVIEWING") {
          const amount = el("input");
          amount.type = "number";
          amount.name = "amount";
          amount.min = "0";
          amount.max = String(current.offer.priceWon);
          const amountLabel = el(
            "label",
            "법적 검토 후 승인 금액 (자동 계산 없음)",
          );
          amountLabel.append(amount);
          form.append(amountLabel);
        }
        const submit = el("button", "처리 기록");
        submit.type = "submit";
        form.append(submit);
        control(form, async (data) => {
          await api(
            "/api/admin/exam-intake/requests/" + id + "/actions/" + a.id,
            "POST",
            {
              version: current.version,
              state: data.get("state"),
              amount:
                data.get("amount") !== null && data.get("amount") !== ""
                  ? Number(data.get("amount"))
                  : null,
              note: data.get("note"),
            },
          );
          await detail(id);
          note("처리 이력을 기록했습니다. 실제 송금은 실행하지 않았습니다.");
        });
        card.append(form);
      }
      $("history").append(card);
    });
    if (!admin) {
      const bank = await api("/api/exam-intake/bank");
      $("bankGuide").textContent = bank.available
        ? bank.bank.bankName +
          " / " +
          bank.bank.accountNumber +
          " / " +
          bank.bank.accountHolder +
          " · " +
          Number(current.offer.priceWon).toLocaleString("ko-KR") +
          "원"
        : bank.message;
      paymentAllowed =
        bank.available &&
        current.kind === "APPLICATION" &&
        ["RECEIVED", "AWAITING_DEPOSIT"].includes(current.state);
      $("paymentRequest").disabled = !paymentAllowed;
    }
  }
  async function bank() {
    const value = await api("/api/admin/exam-intake/bank");
    const b = value.bank;
    bankVersion = b ? b.version : 0;
    if (b)
      for (const k of ["bankName", "accountNumber", "accountHolder"])
        $("bankForm").elements[k].value = b[k];
    $("approveBank").disabled = !b || b.approved;
    $("bankStatus").textContent = b
      ? b.approved
        ? "승인됨 · 신청자에게 표시됩니다."
        : "저장됨 · 승인 전에는 비공개입니다."
      : "등록된 계좌 없음";
  }
  async function events() {
    const items = await api("/api/admin/exam-intake/events");
    $("events").replaceChildren();
    items.forEach((item) => {
      const a = el("article");
      a.append(
        el(
          "p",
          item.event_type +
            " · " +
            item.state +
            " · 시도 " +
            item.attempts +
            "회",
        ),
        el("small", item.id),
      );
      if (!item.acknowledged_at)
        a.append(
          button("관리자 확인", async () => {
            await api(
              "/api/admin/exam-intake/events/" + item.id + "/ack",
              "POST",
              {},
            );
            await events();
          }),
        );
      $("events").append(a);
    });
  }
  control($("intakeForm"), async (data) => {
    const details = {};
    for (const k of Object.keys(fieldLabels))
      details[k] = String(data.get(k) || "");
    const r = await api("/api/exam-intake/requests", "POST", {
      kind: data.get("kind"),
      details,
      consent: data.get("consent") === "on",
      consentVersion: config.consentVersion,
      idempotencyKey: intakeKey,
    });
    intakeKey = crypto.randomUUID().replaceAll("-", "");
    $("intakeForm").reset();
    $("intakeForm").elements.kind.dispatchEvent(new Event("change"));
    await list();
    await detail(r.id);
    $("detailTitle").focus();
    note("접수되었습니다. 접수 번호: " + r.id);
  });
  control($("uploadForm"), async (data) => {
    await api(
      "/api/exam-intake/requests/" + current.id + "/documents",
      "POST",
      data,
    );
    await detail(current.id);
    note("자료를 접수했습니다. 검사 상태를 확인해 주세요.");
  });
  control($("paymentForm"), async (data) => {
    await api(
      "/api/exam-intake/requests/" + current.id + "/payment-confirmation",
      "POST",
      { version: current.version, payer: data.get("payer") },
    );
    await detail(current.id);
    note("입금 확인을 요청했습니다. 관리자 확인 전입니다.");
  });
  control($("actionForm"), async (data) => {
    await api("/api/exam-intake/requests/" + current.id + "/actions", "POST", {
      version: current.version,
      kind: data.get("kind"),
      body: data.get("body"),
    });
    await detail(current.id);
    note("요청을 접수했습니다.");
  });
  control($("amendForm"), async (data) => {
    const details = {};
    for (const key of Object.keys(fieldLabels))
      details[key] = String(data.get(key) || "");
    await api(
      "/api/admin/exam-intake/requests/" + current.id + "/details",
      "POST",
      { version: current.version, actionId: data.get("actionId"), details },
    );
    await detail(current.id);
    note("승인된 신청 내용 변경을 반영했습니다. 계약 조건은 유지됩니다.");
  });
  control($("stateForm"), async (data) => {
    await api(
      "/api/admin/exam-intake/requests/" + current.id + "/state",
      "POST",
      { version: current.version, state: data.get("state") },
    );
    await detail(current.id);
    await list();
    note("상태를 기록했습니다.");
  });
  control($("deliveryForm"), async (data) => {
    await api(
      "/api/admin/exam-intake/requests/" + current.id + "/deliveries",
      "POST",
      {
        version: current.version,
        ordinal: Number(data.get("ordinal")),
        note: data.get("note"),
      },
    );
    await detail(current.id);
    note("전달 이력을 기록했습니다.");
  });
  control($("bankForm"), async (data) => {
    await api("/api/admin/exam-intake/bank", "PUT", {
      bankName: data.get("bankName"),
      accountNumber: data.get("accountNumber"),
      accountHolder: data.get("accountHolder"),
      approved: false,
      version: bankVersion,
    });
    await bank();
    note("미승인 계좌로 저장했습니다.");
  });
  if ($("approveBank"))
    $("approveBank").addEventListener("click", async () => {
      try {
        await api("/api/admin/exam-intake/bank/approve", "POST", {
          version: bankVersion,
        });
        await bank();
        note("계좌를 승인했습니다.");
      } catch (e) {
        note(e.message);
      }
    });
  $("refresh").addEventListener("click", () =>
    list().catch((e) => note(e.message)),
  );
  $("previous").addEventListener("click", () => {
    if (page > 0) page--;
    list().catch((e) => note(e.message));
  });
  $("next").addEventListener("click", () => {
    page++;
    list().catch((e) => note(e.message));
  });
  if ($("intakeForm"))
    $("intakeForm").elements.kind.addEventListener("change", (event) => {
      const application = event.target.value === "APPLICATION";
      $("applicationFields").hidden = !application;
      $("inquiryFields").hidden = application;
      for (const id of ["applicationFields", "inquiryFields"])
        $(id)
          .querySelectorAll("input,textarea,select")
          .forEach((input) => {
            input.disabled = $(id).hidden;
          });
      $("inquiryFields")
        .querySelectorAll("input,textarea")
        .forEach((input) => (input.required = !application));
    });
  (async () => {
    config = await api("/api/exam-intake/config");
    $("offerSummary").textContent = config.summary;
    await list();
    if (admin) await bank();
    note(
      "접수 내역은 로그인한 계정과 담당 관리자에게 표시됩니다. 파일 본문은 관리자만 열람합니다.",
    );
  })().catch((e) => note(e.message));
})();
