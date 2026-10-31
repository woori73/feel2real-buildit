(function () {
  "use strict";
  const $ = (id) => document.getElementById(id),
    admin = document.body.dataset.admin === "true",
    bankOnly = document.body.dataset.bankOnly === "true";
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
    NOTIFICATION_RECEIVED: "담당자 메일 수신 확인",
    PRIVACY_REQUEST: "개인정보 권리 행사 요청",
    DOCUMENT_CONSENT: "선택 자료 제출 동의",
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
  let paymentAccount = "",
    paymentAllowed = false,
    config,
    current,
    bankVersion = 0,
    page = 0,
    intakeKey = submissionKey(),
    pendingFiles = [],
    receiptId = null;
  function submissionKey(reset = false) {
    const fresh = () => crypto.randomUUID().replaceAll("-", "");
    try {
      const key = !reset && sessionStorage.getItem("f2r-exam-submission-key");
      const value = key && /^[A-Za-z0-9_-]{16,80}$/.test(key) ? key : fresh();
      // Opaque retry key only: never persist form fields, files, contact details or credentials.
      sessionStorage.setItem("f2r-exam-submission-key", value);
      return value;
    } catch (_) {
      return fresh();
    }
  }
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
    if (!r.ok) {
      const error = new Error(
        value.message ||
          "요청을 처리하지 못했습니다. 로그인과 연결을 확인해 주세요.",
      );
      error.status = r.status;
      throw error;
    }
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
    if ($("earlyErasure"))
      $("earlyErasure").hidden = !["COMPLETE", "CANCELLED"].includes(
        current.state,
      );
    $("requestDetail").hidden = false;
    const notificationLabels = {
      PENDING: "담당자 알림 전송 대기 중입니다. 신청은 저장되었습니다.",
      SENDING: "담당자 알림을 전송 중입니다. 신청은 저장되었습니다.",
      SENT: "알림 메일 서버에 전달했습니다. 실제 수신 확인은 대기 중입니다.",
      FAILED:
        "담당자 알림 전송에 실패했습니다. 신청은 저장되어 관리자 목록에서 확인할 수 있습니다.",
    };
    $("notificationStatus").textContent = "알림 상태를 확인하고 있습니다.";
    api("/api/exam-intake/requests/" + id + "/notification")
      .then((n) => {
        if (current.id !== id) return;
        $("notificationStatus").textContent = n.received
          ? "담당자가 알림 메일의 실제 수신을 확인했습니다."
          : notificationLabels[n.state] ||
            "알림 상태 미확인 · 신청 저장과 별도로 확인합니다.";
      })
      .catch(() => {
        if (current.id === id)
          $("notificationStatus").textContent =
            "알림 상태를 불러오지 못했습니다. 신청은 저장되었습니다.";
      });
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
    const estimate = value.refundEstimate;
    $("refundEstimate").textContent = estimate.available
      ? "잔여 " +
        estimate.remaining +
        "회 · 기본 환불 예정액 " +
        Number(estimate.estimatedWon).toLocaleString("ko-KR") +
        "원 — 최종 법정 환불액이 아니며 별도 검토합니다."
      : estimate.message;
    const paymentRecord = value.actions.findLast(
      (a) => a.kind === "PAYMENT_CONFIRMATION",
    );
    let payment = null;
    if (paymentRecord) {
      try {
        payment = JSON.parse(paymentRecord.body);
      } catch (_) {}
    }
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
      Object.entries(current.details)
        .filter(([key]) => Object.hasOwn(fieldLabels, key))
        .forEach(([key, value]) => {
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
      const application = current.kind === "APPLICATION";
      const unpaid = ["RECEIVED", "AWAITING_DEPOSIT"].includes(current.state);
      const approved = payment
        ? payment.bank
        : bank.available
          ? bank.bank
          : null;
      paymentAccount =
        application && unpaid && approved ? approved.accountNumber : "";
      $("bankGuide").replaceChildren();
      if (application && approved) {
        const account = el("dl");
        for (const [label, content] of [
          ["은행", approved.bankName],
          ["계좌번호", approved.accountNumber],
          ["예금주", approved.accountHolder],
          [
            payment ? "입금 확인 요청액" : "입금액",
            Number(current.offer.priceWon).toLocaleString("ko-KR") + "원",
          ],
          ["신청번호", current.id],
        ])
          account.append(el("dt", label), el("dd", content));
        $("bankGuide").append(account);
        if (payment)
          $("bankGuide").append(
            el("p", "입금자명: " + payment.payer + " · 추가 입금하지 마세요."),
          );
      } else
        $("bankGuide").textContent = application
          ? bank.message
          : "문의 접수에는 입금하지 마세요.";
      $("copyAccount").hidden = !paymentAccount;
      $("copyStatus").textContent = "";
      $("kakaoConfirmation").hidden = !application || !approved;
      $("paymentMessage").value =
        "신청번호: " +
        current.id +
        "\n입금자명: " +
        (payment ? payment.payer : $("paymentForm").elements.payer.value) +
        "\n입금액: " +
        Number(current.offer.priceWon).toLocaleString("ko-KR") +
        "원\n실제 입금 확인을 부탁드립니다.";
      paymentAllowed =
        bank.available &&
        current.kind === "APPLICATION" &&
        ["RECEIVED", "AWAITING_DEPOSIT"].includes(current.state);
      $("paymentRequest").disabled = !paymentAllowed;
    }
  }
  async function copyPaymentText(text) {
    if (!text) return;
    try {
      if (!navigator.clipboard || !navigator.clipboard.writeText)
        throw new Error("unavailable");
      await navigator.clipboard.writeText(text);
      $("copyStatus").textContent = "복사했습니다.";
    } catch (_) {
      $("copyStatus").textContent =
        "복사할 수 없습니다. 화면의 내용을 선택해 직접 복사해 주세요.";
    }
  }
  if ($("copyAccount"))
    $("copyAccount").addEventListener("click", () =>
      copyPaymentText(paymentAccount),
    );
  if ($("copyPaymentMessage"))
    $("copyPaymentMessage").addEventListener("click", () =>
      copyPaymentText($("paymentMessage").value),
    );
  if ($("paymentForm"))
    $("paymentForm").elements.payer.addEventListener("input", () => {
      if (current)
        $("paymentMessage").value =
          "신청번호: " +
          current.id +
          "\n입금자명: " +
          $("paymentForm").elements.payer.value +
          "\n입금액: " +
          Number(current.offer.priceWon).toLocaleString("ko-KR") +
          "원\n실제 입금 확인을 부탁드립니다.";
    });
  if ($("bankImport"))
    $("bankImport").addEventListener("change", async (event) => {
      try {
        const file = event.target.files[0];
        if (!file || file.size > 4096)
          throw new Error("4KiB 이하 등록 파일을 선택해 주세요.");
        const data = JSON.parse(await file.text());
        for (const key of ["bankName", "accountNumber", "accountHolder"])
          if (
            typeof data[key] !== "string" ||
            !data[key].trim() ||
            data[key].length > 80
          )
            throw new Error("은행명·계좌번호·예금주를 확인해 주세요.");
        for (const key of ["bankName", "accountNumber", "accountHolder"])
          $("bankForm").elements[key].value = data[key];
        $("approveBank").disabled = true;
        note(
          "등록 내용을 채웠습니다. 아직 저장·승인하지 않았습니다. 저장 후 표시할 계좌를 승인해 주세요.",
        );
      } catch (_) {
        note("등록 파일을 확인해 주세요. 저장·승인하지 않았습니다.");
      } finally {
        event.target.value = "";
      }
    });
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
        ? value.customerVisible
          ? "승인됨 · 신청자에게 표시됩니다."
          : "승인됨 · 고객 계좌 공개는 아직 비활성입니다."
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
      if (item.state === "FAILED")
        a.append(
          button("실패 알림 다시 전송", async () => {
            await api(
              "/api/admin/exam-intake/events/" + item.id + "/retry",
              "POST",
              {},
            );
            await events();
          }),
        );
      if (item.state === "SENT")
        a.append(
          button("수신함에서 이 이벤트 ID의 메일 확인 완료", async () => {
            await api(
              "/api/admin/exam-intake/events/" + item.id + "/received",
              "POST",
              { confirmedInMailbox: true },
            );
            note("담당자가 확인한 메일 수신 기록을 남겼습니다.");
          }),
        );
      $("events").append(a);
    });
  }
  function receipt(id) {
    receiptId = id;
    $("receipt").hidden = false;
    $("newRequestSection").hidden = true;
    $("receiptNumber").textContent = id;
    $("receiptLink").href = "/exam-service?request=" + encodeURIComponent(id);
    $("receiptTitle").focus();
  }
  async function uploadInitialFiles() {
    const failed = [];
    for (const file of pendingFiles) {
      const data = new FormData();
      data.set("file", file);
      data.set("materialConsent", "true");
      try {
        await api(
          "/api/exam-intake/requests/" + receiptId + "/documents",
          "POST",
          data,
        );
      } catch (_) {
        failed.push(file);
      }
    }
    pendingFiles = failed;
    $("retryInitialFiles").hidden = !failed.length;
    $("receiptStatus").textContent = failed.length
      ? "신청은 저장되었습니다. 자료 " +
        failed.length +
        "개는 제출 실패했습니다. 실패한 자료만 다시 제출하거나 접수 상세에서 파일을 변경해 제출해 주세요."
      : "접수되었습니다. 제출 자료의 검사 상태와 담당자 알림 상태는 접수 상세에서 확인할 수 있습니다.";
    const refresh = await Promise.allSettled([list(), detail(receiptId)]);
    if (refresh.some((r) => r.status === "rejected"))
      $("receiptStatus").textContent +=
        " 상세 조회에 실패했습니다. 접수 번호를 보관하고 내 접수 확인을 다시 눌러 주세요.";
    note(
      failed.length
        ? "자료 제출 실패 · 신청을 새로 만들지 말고 같은 접수에서 재시도해 주세요."
        : "접수되었습니다. 접수 번호: " + receiptId,
    );
  }
  if ($("retryInitialFiles"))
    $("retryInitialFiles").replaceWith(
      buttonWithId(
        "retryInitialFiles",
        "실패한 자료만 다시 제출",
        uploadInitialFiles,
      ),
    );
  function buttonWithId(id, text, work) {
    const b = button(text, work);
    b.id = id;
    b.hidden = true;
    return b;
  }
  if ($("newRequest"))
    $("newRequest").onclick = () => {
      if (
        pendingFiles.length &&
        !window.confirm(
          "아직 제출하지 못한 자료가 있습니다. 현재 접수에서 나중에 제출하고 새 신청을 작성할까요?",
        )
      )
        return;
      intakeKey = submissionKey(true);
      receiptId = null;
      pendingFiles = [];
      $("receipt").hidden = true;
      $("newRequestSection").hidden = false;
      $("intakeForm").reset();
      $("intakeForm").elements.kind.dispatchEvent(new Event("change"));
    };
  control($("intakeForm"), async (data) => {
    if (!config)
      throw new Error(
        "신청 설정을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.",
      );
    const selectedFiles = data
      .getAll("initialFiles")
      .filter((f) => f instanceof File && f.size > 0);
    if (selectedFiles.length && data.get("initialMaterialConsent") !== "on")
      throw new Error("선택 자료 제출 동의를 확인해 주세요.");
    if (
      selectedFiles.length > 10 ||
      selectedFiles.some(
        (f) => f.size > 10 * 1024 * 1024 || !/\.(pdf|jpe?g|png)$/i.test(f.name),
      )
    )
      throw new Error(
        "PDF·JPEG·PNG 파일을 10MiB 이하, 최대 10개 선택해 주세요.",
      );
    const details = {};
    for (const k of Object.keys(fieldLabels))
      details[k] = String(data.get(k) || "");
    details.ageBand = data.get("ageBand");
    details.guardianConsentId = data.get("guardianConsentId") || "";
    details.privacyVersion = config.privacyVersion;
    details.termsVersion = config.termsVersion;
    details.termsAccepted = String(data.get("termsAccepted") === "on");
    details.optionalConsent = String(data.get("optionalConsent") === "on");
    let r;
    try {
      r = await api("/api/exam-intake/requests", "POST", {
        kind: data.get("kind"),
        details,
        consent: data.get("consent") === "on",
        consentVersion: config.consentVersion,
        idempotencyKey: intakeKey,
      });
    } catch (e) {
      // A network/5xx failure can occur after commit. Keep the same key for resolution/retry.
      if ([400, 401, 403, 422, 429].includes(e.status))
        intakeKey = submissionKey(true);
      throw e;
    }
    if (!r || !r.id)
      throw new Error(
        "접수 결과를 확인하지 못했습니다. 같은 신청으로 다시 시도해 주세요.",
      );
    pendingFiles = selectedFiles;
    receipt(r.id);
    $("receiptStatus").textContent =
      "신청이 저장되었습니다. 선택한 자료를 제출하고 있습니다.";
    await uploadInitialFiles();
  });
  control($("earlyErasure"), async (data) => {
    await api(
      "/api/admin/exam-intake/requests/" + current.id + "/erase-learning-data",
      "POST",
      {
        version: current.version,
        purposeComplete: data.get("purposeComplete") === "on",
        noLegalHold: data.get("noLegalHold") === "on",
      },
    );
    await detail(current.id);
    note(
      "학습자료와 불필요한 개인정보를 정리했습니다. 법정 거래기록은 별도 보존합니다.",
    );
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
        accessibleAndReviewed: data.get("accessibleAndReviewed") === "on",
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
  if (bankOnly) {
    bank().catch((e) => note(e.message));
    return;
  }
  async function retentionPlan() {
    const rows = await api("/api/admin/exam-intake/retention");
    $("retentionPlan").replaceChildren();
    if (!rows.length)
      $("retentionPlan").append(el("p", "현재 정리 대상이 없습니다."));
    for (const row of rows) {
      const f = el("form"),
        title = el("p", row.id + " · " + row.state);
      f.append(title);
      for (const [key, text] of [
        ["purposeComplete", "서비스 목적 달성을 확인했습니다."],
        ["noLegalHold", "진행 중인 분쟁·법적 보존 유보가 없음을 확인했습니다."],
      ]) {
        const label = el("label", text),
          check = el("input");
        label.className = "check";
        check.type = "checkbox";
        check.name = key;
        check.required = true;
        label.prepend(check);
        f.append(label);
      }
      const submit = el("button", "학습자료 삭제·개인정보 정리");
      submit.type = "submit";
      f.append(submit);
      control(f, async (data) => {
        const detail = await api("/api/exam-intake/requests/" + row.id);
        await api(
          "/api/admin/exam-intake/requests/" + row.id + "/erase-learning-data",
          "POST",
          {
            version: detail.request.version,
            purposeComplete: data.get("purposeComplete") === "on",
            noLegalHold: data.get("noLegalHold") === "on",
          },
        );
        await retentionPlan();
        note(
          "학습자료 정리를 완료했습니다. 법정 거래기록은 별도로 보존합니다.",
        );
      });
      $("retentionPlan").append(f);
    }
  }
  if ($("retentionRefresh"))
    $("retentionRefresh").onclick = () =>
      retentionPlan().catch((e) => note(e.message));
  if ($("unusedGuardians"))
    $("unusedGuardians").onclick = async () => {
      try {
        const v = await api(
          "/api/admin/exam-intake/retention/unused-guardians",
          "POST",
          {},
        );
        note(v.redacted + "건의 미사용 보호자 연락처를 정리했습니다.");
      } catch (e) {
        note(e.message);
      }
    };

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
      for (const name of ["school", "grade"])
        $("intakeForm").elements[name].required = application;
      $("termsConsent").hidden = !application;
      $("intakeForm").elements.termsAccepted.required = application;
      syncAge();
      $("inquiryFields")
        .querySelectorAll("input,textarea")
        .forEach((input) => (input.required = !application));
    });
  function syncAge() {
    const f = $("intakeForm");
    if (!f) return;
    const under = f.elements.ageBand.value === "UNDER14";
    $("guardianGate").hidden = !under;
    $("intakeData").disabled = !(
      f.elements.ageBand.value === "OVER14" ||
      (under && f.elements.guardianConsentId.value)
    );
  }
  if ($("intakeForm")) {
    const f = $("intakeForm");
    let generation = 0;
    f.elements.guardianConsentId.addEventListener("change", syncAge);
    f.elements.ageBand.addEventListener("change", async () => {
      const thisGeneration = ++generation;
      f.querySelectorAll(
        '#intakeData input:not([type="checkbox"]),#intakeData textarea',
      ).forEach((e) => (e.value = ""));
      f.querySelectorAll('#intakeData input[type="checkbox"]').forEach(
        (e) => (e.checked = false),
      );
      f.elements.guardianConsentId.replaceChildren(
        new Option("확인된 동의를 선택해 주세요", ""),
      );
      syncAge();
      if (f.elements.ageBand.value !== "UNDER14") return;
      try {
        const rows = await api("/api/exam-intake/guardian-consents");
        if (thisGeneration !== generation) return;
        rows
          .filter(
            (r) =>
              r.state === "VERIFIED" &&
              r.privacy_version === config.privacyVersion &&
              r.terms_version === config.termsVersion &&
              Date.parse(r.expires_at) > Date.now(),
          )
          .forEach((r) =>
            f.elements.guardianConsentId.append(
              new Option(r.guardian_name + " · " + r.id, r.id),
            ),
          );
      } catch (e) {
        note(e.message);
      }
      syncAge();
    });
  }
  (async () => {
    config = await api("/api/exam-intake/config");
    $("offerSummary").textContent = config.summary;
    if ($("refundRows"))
      for (let n = 0; n <= config.offer.totalDeliveries; n++) {
        const row = el("tr");
        const remaining = config.offer.totalDeliveries - n;
        row.append(
          el("td", n + "회"),
          el("td", remaining + "회"),
          el(
            "td",
            (
              (config.offer.priceWon / config.offer.totalDeliveries) *
              remaining
            ).toLocaleString("ko-KR") + "원",
          ),
        );
        $("refundRows").append(row);
      }
    if (!admin) {
      const requestId = new URL(location.href).searchParams.get("request");
      const prior = await api("/api/exam-intake/submissions/resolve", "POST", {
        idempotencyKey: intakeKey,
      });
      const id = requestId || (prior.found && prior.request.id);
      if (id) {
        await detail(id); // Ownership is checked by the server before showing a receipt.
        receipt(id);
        $("receiptStatus").textContent =
          "저장된 접수를 확인했습니다. 자료 제출 여부와 검사 상태를 상세에서 확인해 주세요.";
      }
    }
    await list();
    if (admin && $("bankForm")) await bank();
    note(
      "접수 내역은 로그인한 계정과 담당 관리자에게 표시됩니다. 파일 본문은 관리자만 열람합니다.",
    );
  })().catch((e) => note(e.message));
})();
