(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const states = { RECEIVED: "신규 접수", AWAITING_DEPOSIT: "입금 대기", CONFIRMATION_REQUESTED: "입금 확인 요청", CONFIRMED: "제작 대기", PRODUCTION: "제작 중", DELIVERED: "전달 완료", COMPLETE: "처리 완료", CANCELLED: "취소 완료" };
  const fields = { name: "신청자", school: "학교/학원", grade: "학년", contactMethod: "연락 방법", contact: "연락처", examDate: "시험일", scope: "시험 범위", pastPapers: "기출 보유", questionTypes: "문제 유형", difficulty: "난이도", format: "시험지 형식", requirements: "출제 시 요구사항", inquiryType: "문의 유형", message: "문의 내용" };
  const actionLabels = { NOTIFICATION_RECEIVED: "담당자 메일 수신 확인", PRIVACY_REQUEST: "개인정보 권리 행사", DOCUMENT_CONSENT: "자료 제출 동의", UPDATE_DETAILS: "신청 내용 변경", DELETE_DOCUMENT: "자료 삭제", REVISION: "문제 수정", CANCEL: "취소", REFUND: "환불 검토", STATE_CHANGED: "진행 상태 변경", PAYMENT_CONFIRMATION: "입금 확인 요청", DELIVERED: "회차 전달", ACTION_REVIEW: "요청 처리 기록", DOCUMENT_RESCAN: "파일 검사 재시도", DETAILS_AMENDED: "신청 내용 변경 반영", PRIVACY_ERASURE: "개인정보 정리" };
  const reviewLabels = { REQUESTED: "접수", REVIEWING: "검토 중", APPROVED: "승인", REJECTED: "반려", RESOLVED: "처리 완료", REMITTED: "환불 완료 기록", RECORDED: "기록됨", PROCESSING: "처리 중" };
  const groups = { new: ["RECEIVED"], waiting: ["AWAITING_DEPOSIT"], payment: ["CONFIRMATION_REQUESTED"], ready: ["CONFIRMED"], production: ["PRODUCTION"], done: ["DELIVERED", "COMPLETE"], cancelled: ["CANCELLED"] };
  const nextTasks = { RECEIVED: "신청 내용과 제출 자료를 확인해 주세요.", AWAITING_DEPOSIT: "고객의 입금 확인 요청을 기다리고 있습니다.", CONFIRMATION_REQUESTED: "실제 입금 내역과 입금자명을 확인해 주세요.", CONFIRMED: "자료를 확인하고 제작을 시작해 주세요.", PRODUCTION: "다음 회차를 제작·검수하고 고객에게 전달해 주세요.", DELIVERED: "전체 전달 내역을 확인하고 처리를 마무리해 주세요.", COMPLETE: "서비스 처리가 완료되었습니다.", CANCELLED: "취소된 접수입니다. 필요한 요청·이력을 확인해 주세요." };
  let rows = null, page = 0, current = null, listSequence = 0, detailSequence = 0, previewSequence = 0, previewUrl = null, notificationVersion = null;
  const dateFormat = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false });
  function date(value) { const d = new Date(value); return value && !Number.isNaN(d.getTime()) ? dateFormat.format(d) : "시각 미기록"; }
  function node(tag, text, className) { const e = document.createElement(tag); if (text != null) e.textContent = text; if (className) e.className = className; return e; }
  function note(message, error = false) { $("notice").textContent = message; $("notice").dataset.error = String(error); $("notice").hidden = !message; }
  function requestKind(r) { return r.details.inquiryType === "FREE_SAMPLE" ? "클라우드리뷰 무료신청" : r.kind === "INQUIRY" ? "문의" : "정식 신청"; }
  function badge(state) { const e = node("span", states[state] || "상태 확인 필요", "status-badge"); e.dataset.state = state; return e; }
  function beforeProduction(r) { return ["RECEIVED", "AWAITING_DEPOSIT", "CONFIRMATION_REQUESTED", "CONFIRMED"].includes(r.state); }
  async function api(path, method = "GET", body) {
    const init = { method, credentials: "same-origin", cache: "no-store", headers: { "X-F2R-Exam": "1" } };
    if (body instanceof FormData) init.body = body;
    else if (body != null) { init.headers["Content-Type"] = "application/json"; init.body = JSON.stringify(body); }
    const response = await fetch(path, init);
    const value = await response.json().catch(() => ({}));
    if (!response.ok) { const error = new Error(value.message || (response.status === 401 ? "로그인이 만료되었습니다. 다시 로그인해 주세요." : "조회 또는 처리를 완료하지 못했습니다. 다시 시도해 주세요.")); error.status = response.status; throw error; }
    return value;
  }
  function actionButton(text, work, secondary = true) {
    const b = node("button", text, secondary ? "secondary" : ""); b.type = "button";
    b.addEventListener("click", async () => { b.disabled = true; try { await work(); } catch (e) { note(e.message + (e.status === 409 ? " 접수 상세를 다시 선택해 최신 내역을 확인해 주세요." : ""), true); } finally { if (b.isConnected) b.disabled = false; } });
    return b;
  }
  function submit(form, work) {
    form.addEventListener("submit", async (event) => {
      event.preventDefault(); const b = form.querySelector('[type="submit"]'); b.disabled = true;
      try { await work(new FormData(form)); } catch (e) { note(e.status === 409 ? e.message + " 접수 상세를 다시 선택해 최신 내역을 확인해 주세요." : e.message, true); }
      finally { if (b.isConnected) b.disabled = false; }
    });
  }
  function field(label, value, wide = false) { const e = node("div", null, "field" + (wide ? " wide" : "")); e.append(node("dt", label), node("dd", value || "미입력")); return e; }
  function fieldValue(key, value) {
    if (key === "contactMethod") return { EMAIL: "이메일", PHONE: "전화", KAKAO: "카카오톡" }[value] || value;
    if (key === "inquiryType" && value === "FREE_SAMPLE") return "클라우드리뷰 무료신청";
    if (key === "pastPapers") return { YES: "있음", NO: "없음" }[value] || value;
    return value;
  }
  function renderList() {
    if (!rows) return;
    const search = $("requestSearch").value.trim().toLocaleLowerCase("ko-KR"), filter = $("requestFilter").value;
    const visible = rows.filter((r) => (filter === "all" || groups[filter]?.includes(r.state)) && [r.id, r.details.name, r.details.school, r.details.grade, r.details.contact].join(" ").toLocaleLowerCase("ko-KR").includes(search));
    $("requests").replaceChildren(); $("listCount").textContent = visible.length + "건";
    if (!visible.length) $("requests").append(node("div", rows.length ? "검색 조건에 맞는 접수가 없습니다. 이름·학교 또는 상태를 바꿔 보세요." : "접수 내역이 없습니다. 새 신청이 저장되면 이곳에 표시됩니다.", "empty-state"));
    for (const r of visible) {
      const b = actionButton("", () => loadDetail(r.id)); b.className = "request-row"; b.setAttribute("aria-pressed", String(current?.request.id === r.id));
      const name = node("span", r.details.name || "이름 미입력", "request-name"); name.append(node("span", r.kind === "APPLICATION" ? "2만원" : r.details.inquiryType === "FREE_SAMPLE" ? "무료" : "문의", "kind-badge"));
      b.append(name, node("span", [r.details.school || "학교 미입력", r.details.grade].filter(Boolean).join(" · "), "request-school"), badge(r.state), node("span", date(r.createdAt) + " 접수 · " + (r.details.contact || "연락처 미입력"), "request-meta"));
      b.dataset.requestId = r.id;
      b.setAttribute("aria-label", (r.details.name || "이름 미입력") + " · " + (r.details.school || "") + " · " + (states[r.state] || r.state) + " 상세 보기");
      $("requests").append(b);
    }
    for (const [id, group] of [["countNew", "new"], ["countPayment", "payment"], ["countReady", "ready"], ["countProduction", "production"]]) $(id).textContent = rows.filter((r) => groups[group].includes(r.state)).length;
    document.querySelectorAll("#metrics button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.filter === filter)));
  }
  async function loadList(targetPage = page) {
    const sequence = ++listSequence;
    $("previous").disabled = true; $("next").disabled = true;
    try {
      const result = await api("/api/admin/exam-intake/requests?page=" + targetPage);
      if (sequence !== listSequence) return;
      if (!Array.isArray(result)) throw new Error("접수 목록의 응답 형식을 확인할 수 없습니다.");
      if (targetPage !== page) clearDetail();
      page = targetPage; rows = result; $("listError").hidden = true; renderList();
      $("pageNumber").textContent = (page + 1) + " 페이지";
      $("summaryScope").textContent = (page + 1) + "페이지 " + rows.length + "건 기준 · 최대 50건";
    } catch (e) {
      if (sequence !== listSequence) return;
      $("listError").textContent = "접수 목록을 불러오지 못했습니다. " + e.message + (rows ? " 마지막 조회 내역을 표시합니다." : " 접수 0건을 의미하지 않습니다."); $("listError").hidden = false;
      if (!rows) $("requests").replaceChildren(node("div", "상단 새로고침으로 다시 조회해 주세요.", "empty-state"));
      if ([401, 403].includes(e.status)) { rows = null; clearDetail(); $("requests").replaceChildren(); $("listCount").textContent = ""; for (const id of ["countNew", "countPayment", "countReady", "countProduction"]) $(id).textContent = "—"; }
    } finally { if (sequence === listSequence) { $("previous").disabled = page === 0; $("next").disabled = !rows || rows.length < 50 || page >= 10000; } }
  }
  function showTab(name) {
    for (const b of document.querySelectorAll("[data-tab]")) b.setAttribute("aria-pressed", String(b.dataset.tab === name));
    for (const key of ["application", "delivery", "history"]) $("tab-" + key).hidden = key !== name;
  }
  function closePreview() { ++previewSequence; if (previewUrl) URL.revokeObjectURL(previewUrl); previewUrl = null; $("viewerBody").replaceChildren(); if ($("documentViewer").open) $("documentViewer").close(); }
  function clearDetail() { ++detailSequence; current = null; closePreview(); $("requestDetail").hidden = true; $("detailPlaceholder").hidden = false; $("detailError").hidden = true; }
  async function loadDetail(id, focus = true) {
    const sequence = ++detailSequence, same = current?.request.id === id;
    current = null; closePreview(); $("requestDetail").hidden = true; $("detailPlaceholder").hidden = true; $("detailError").hidden = false; $("detailError").textContent = "접수 상세를 불러오고 있습니다.";
    try {
      const value = await api("/api/exam-intake/requests/" + encodeURIComponent(id));
      if (sequence !== detailSequence) return;
      current = value; $("detailError").hidden = true; $("requestDetail").hidden = false; renderDetail(); renderList(); if (!same) showTab("application");
      const url = new URL(location.href); url.searchParams.set("request", id); history.replaceState(null, "", url);
      if (focus) $("detailTitle").focus({ preventScroll: window.innerWidth > 850 });
    } catch (e) { if (sequence === detailSequence) { current = null; $("requestDetail").hidden = true; $("detailError").textContent = "접수 상세를 불러오지 못했습니다. " + e.message; renderList(); } }
  }
  async function save(suffix, body, message) {
    if (!current) throw new Error("접수를 먼저 선택해 주세요.");
    const r = current.request;
    await api("/api/admin/exam-intake/requests/" + encodeURIComponent(r.id) + suffix, "POST", { version: r.version, ...body });
    if (current?.request.id === r.id) await loadDetail(r.id, false);
    await loadList(); note(message);
  }
  function renderState(r) {
    $("stateActions").replaceChildren(); $("nextTask").textContent = nextTasks[r.state] || "진행 상태를 확인해 주세요.";
    const next = r.state === "RECEIVED" ? (r.kind === "APPLICATION" ? ["AWAITING_DEPOSIT", "입금 안내"] : ["COMPLETE", "문의 처리 완료"]) : { CONFIRMATION_REQUESTED: ["CONFIRMED", "실제 입금 확인 완료"], CONFIRMED: ["PRODUCTION", "제작 시작"], DELIVERED: ["COMPLETE", "처리 완료"] }[r.state];
    if (!next) return;
    if (next[0] === "CONFIRMED") {
      const form = node("form"); const label = node("label", null, "check"), check = node("input"); check.type = "checkbox"; check.required = true; label.append(check, document.createTextNode("통장에서 실제 입금을 확인했습니다."));
      const b = node("button", next[1]); b.type = "submit"; form.append(label, b); submit(form, () => save("/state", { state: next[0] }, "실제 입금 확인을 기록했습니다.")); $("stateActions").append(form);
    } else $("stateActions").append(actionButton(next[1], () => save("/state", { state: next[0] }, "진행 상태를 변경했습니다."), false));
  }
  function renderDetail() {
    const { request: r, documents, deliveries, actions, refundEstimate: estimate } = current;
    $("detailKind").textContent = requestKind(r); $("detailTitle").textContent = r.details.name || "이름 미입력"; $("detailSubtitle").textContent = [r.details.school, r.details.grade, date(r.createdAt) + " 접수"].filter(Boolean).join(" · ");
    $("detailState").replaceWith(Object.assign(badge(r.state), { id: "detailState" })); renderState(r);
    const dl = node("dl", null, "field-grid");
    for (const key of ["school", "grade", "contactMethod", "contact", "examDate", "scope", "pastPapers", "questionTypes", "difficulty", "format"]) if (r.details[key]) dl.append(field(fields[key], fieldValue(key, r.details[key]), ["scope"].includes(key)));
    dl.append(field("접수 번호", r.id, true)); $("details").replaceChildren(dl);
    for (const key of ["requirements", "message"]) { const box = node("div", null, "subsection"); box.append(node("h3", fields[key]), node("p", r.details[key] || "별도 요청이 없습니다.", "requirements")); if (key === "requirements" || r.details[key]) $("details").append(box); }
    const payment = actions.findLast((a) => a.kind === "PAYMENT_CONFIRMATION");
    if (payment) { let content; try { const p = JSON.parse(payment.body); content = "입금자명: " + (p.payer || "미입력") + " · 요청액: " + Number(p.priceWon || r.offer.priceWon).toLocaleString("ko-KR") + "원"; } catch (_) { content = payment.body; } $("details").append(node("p", content, "requirements")); }
    const agreements = node("dl", null, "field-grid");
    for (const [key, label] of [["privacyVersion", "개인정보 안내 버전"], ["termsVersion", "약관 버전"], ["termsAccepted", "이용약관 동의"], ["optionalConsent", "선택 정보 동의"], ["ageBand", "연령 구분"], ["guardianConsentId", "보호자 확인 번호"]]) if (r.details[key]) agreements.append(field(label, { true: "동의", false: "미동의", OVER14: "만 14세 이상", UNDER14: "만 14세 미만", GUARDIAN: "법정대리인" }[r.details[key]] || r.details[key]));
    $("consentDetails").replaceChildren(agreements);
    $("refundEstimate").textContent = estimate?.available ? "잔여 " + estimate.remaining + "회 · 기본 환불 예정액 " + Number(estimate.estimatedWon).toLocaleString("ko-KR") + "원. 최종 법정 환불액은 별도 검토합니다." : estimate?.message || "환불은 요청 내역과 계약 조건에 따라 검토합니다.";
    renderDocuments(r, documents); renderDeliveries(r, deliveries); renderHistory(r, actions); renderAmend(r, actions);
    $("uploadSection").hidden = !beforeProduction(r); $("uploadForm").reset(); $("actionForm").reset();
    $("earlyErasureSection").hidden = !["COMPLETE", "CANCELLED"].includes(r.state); $("earlyErasure").reset();
    for (const o of $("actionForm").elements.kind.options) o.disabled = r.state === "CANCELLED" ? o.value !== "PRIVACY_REQUEST" : o.value === "UPDATE_DETAILS" && !beforeProduction(r);
    $("actionForm").elements.kind.value = r.state === "CANCELLED" ? "PRIVACY_REQUEST" : beforeProduction(r) ? "UPDATE_DETAILS" : "REVISION";
    const pending = actions.filter((a) => !["RECORDED", "RESOLVED", "REMITTED", "REJECTED"].includes(a.state)); $("pendingCount").textContent = pending.length ? "(" + pending.length + ")" : "";
  }
  function renderDocuments(r, documents) {
    $("documents").replaceChildren(); $("documentCount").textContent = documents.filter((d) => !d.hidden).length + "개";
    if (!documents.length) $("documents").append(node("p", "제출된 자료가 없습니다. 고객에게 시험자료를 요청해 주세요.", "muted"));
    for (const d of documents) {
      const card = node("article", null, "document-card"), info = node("div"), buttons = node("div", null, "document-actions");
      info.append(node("strong", d.name), node("small", Math.ceil(d.size / 1024) + "KiB · " + (d.hidden ? "비공개 처리" : d.scanState === "CLEAN" ? "검사 완료" : "검사 대기 · 열람 불가"))); card.append(info, buttons);
      const path = "/api/admin/exam-intake/requests/" + encodeURIComponent(r.id) + "/documents/" + encodeURIComponent(d.id);
      if (!d.hidden && d.scanState === "CLEAN") { buttons.append(actionButton("자료 보기", () => preview(d, path))); const a = node("a", "다운로드", "secondary"); a.href = path; buttons.append(a); }
      else if (!d.hidden) buttons.append(actionButton("검사 재시도", () => save("/documents/" + encodeURIComponent(d.id) + "/rescan", {}, "자료 검사 상태를 갱신했습니다.")));
      if (!d.hidden && beforeProduction(r)) buttons.append(actionButton("삭제 요청", async () => { await api("/api/exam-intake/requests/" + encodeURIComponent(r.id) + "/actions", "POST", { version: r.version, kind: "DELETE_DOCUMENT", body: d.id }); if (current?.request.id === r.id) await loadDetail(r.id, false); note("자료 삭제 요청을 등록했습니다. 요청·이력에서 검토해 주세요."); }));
      $("documents").append(card);
    }
  }
  async function preview(d, path) {
    closePreview(); const sequence = ++previewSequence;
    $("viewerTitle").textContent = d.name; $("viewerDownload").href = path; $("viewerStatus").textContent = "자료를 불러오고 있습니다."; $("documentViewer").showModal();
    try {
      const response = await fetch(path, { credentials: "same-origin", cache: "no-store", headers: { "X-F2R-Exam": "1" } });
      if (!response.ok) throw new Error("자료를 열 수 없습니다. 권한과 검사 상태를 확인해 주세요.");
      const blob = await response.blob(); if (sequence !== previewSequence) return;
      const type = blob.type.split(";")[0];
      if (!["application/pdf", "image/jpeg", "image/png"].includes(type)) throw new Error("화면 미리보기를 지원하지 않는 자료입니다. 다운로드해 확인해 주세요.");
      previewUrl = URL.createObjectURL(blob); const media = node(type === "application/pdf" ? "iframe" : "img"); media.src = previewUrl;
      if (type === "application/pdf") { media.title = d.name; media.setAttribute("sandbox", ""); } else media.alt = d.name;
      $("viewerBody").replaceChildren(media); $("viewerStatus").textContent = "미리보기가 표시되지 않으면 파일 다운로드를 이용해 주세요.";
    } catch (e) { if (sequence === previewSequence) $("viewerStatus").textContent = e.message; }
  }
  function renderDeliveries(r, deliveries) {
    const application = r.kind === "APPLICATION", total = application ? Number(r.offer.totalDeliveries) : 0, next = deliveries.length + 1;
    $("deliveries").replaceChildren(); $("deliveryForm").hidden = r.state !== "PRODUCTION" || !application || next > total; $("deliveryForm").reset();
    $("progress").textContent = application ? "계약 " + r.offer.durationDays + "일 · " + (r.offer.deliveriesPerWeek || 2) + "회/주 · 총 " + total + "회 · " + r.offer.totalQuestions + "문항 · " + Number(r.offer.priceWon).toLocaleString("ko-KR") + "원 / 전달 " + deliveries.length + "/" + total + "회" : "무료신청·문의는 유료 4회 이용권과 별도로 처리합니다. 자료 전달 및 응대 내용은 요청·이력에 기록해 주세요.";
    for (let i = 1; i <= total; i++) {
      const delivered = deliveries.find((d) => Number(d.ordinal_number) === i), card = node("article", null, "delivery-card"); card.dataset.complete = String(Boolean(delivered)); card.dataset.next = String(!delivered && i === next && r.state === "PRODUCTION");
      const heading = node("h4", i + "회차"); heading.append(node("span", delivered ? "전달 완료" : r.state === "PRODUCTION" && i === next ? "다음 회차" : "대기", "muted")); card.append(heading, node("p", delivered ? delivered.note : "문제지·해설지 제작 및 전달"));
      if (delivered) card.append(node("time", date(delivered.delivered_at))); $("deliveries").append(card);
    }
    $("deliveryFormTitle").textContent = next + "회차 전달 기록"; $("deliveryForm").elements.ordinal.value = String(next);
  }
  function readableAction(a) {
    if (a.kind === "STATE_CHANGED") return a.body.split(" -> ").map((v) => states[v] || v).join(" → ");
    if (a.kind === "PAYMENT_CONFIRMATION") { try { const p = JSON.parse(a.body); return "입금자: " + p.payer + " · " + Number(p.priceWon).toLocaleString("ko-KR") + "원"; } catch (_) {} }
    if (a.kind === "DELETE_DOCUMENT") return current.documents.find((d) => d.id === a.body)?.name || "지정 자료 삭제 요청";
    if (a.kind === "ACTION_REVIEW") { try { const p = JSON.parse(a.body); return (reviewLabels[p.state] || p.state) + " · " + p.reason; } catch (_) {} }
    if (a.kind === "DETAILS_AMENDED") { try { const p = JSON.parse(a.body); return Object.keys(fields).filter((k) => p.before?.[k] !== p.after?.[k]).map((k) => fields[k] + ": " + (p.before?.[k] || "미입력") + " → " + (p.after?.[k] || "미입력")).join("\n") || "승인된 변경 내용을 반영했습니다."; } catch (_) {} }
    return a.body;
  }
  function renderHistory(r, actions) {
    $("history").replaceChildren();
    if (!actions.length) $("history").append(node("p", "등록된 요청·처리 이력이 없습니다.", "muted"));
    for (const a of [...actions].reverse()) {
      const card = node("article", null, "history-card"); card.append(node("strong", (actionLabels[a.kind] || a.kind) + " · " + (reviewLabels[a.state] || a.state)), node("p", readableAction(a)), node("time", date(a.createdAt)));
      if (a.amountWon != null) card.append(node("p", "검토 승인 금액 " + Number(a.amountWon).toLocaleString("ko-KR") + "원"));
      if (!["RECORDED", "RESOLVED", "REMITTED", "REJECTED", "PROCESSING"].includes(a.state)) {
        if (a.kind === "UPDATE_DETAILS" && a.state === "APPROVED") card.append(node("p", "신청·자료 탭의 ‘신청 내용 변경 반영’에서 수정 내용을 저장해 주세요.", "muted"));
        else {
          const form = node("form"), select = node("select"), label = node("label", "처리 상태"), reason = node("textarea"), reasonLabel = node("label", "처리 근거"); select.name = "state"; reason.name = "note"; reason.maxLength = 1000; reason.required = true;
          const refund = ["REFUND", "CANCEL"].includes(a.kind), next = a.state === "REQUESTED" ? ["REVIEWING", "REJECTED"] : a.state === "REVIEWING" ? ["APPROVED", "REJECTED"] : refund ? ["REMITTED"] : ["RESOLVED"];
          for (const s of next) { const o = node("option", reviewLabels[s]); o.value = s; select.append(o); } label.append(select); reasonLabel.append(reason); form.append(label, reasonLabel);
          if (refund && a.state === "REVIEWING") { const amountLabel = node("label", "검토 후 환불 승인 금액 (원)"), input = node("input"); input.type = "number"; input.name = "amount"; input.min = "0"; input.max = String(r.offer.priceWon); amountLabel.append(input); form.append(amountLabel); }
          const b = node("button", "처리 기록"); b.type = "submit"; form.append(b);
          submit(form, (data) => save("/actions/" + encodeURIComponent(a.id), { state: data.get("state"), note: data.get("note"), amount: data.get("state") === "APPROVED" && data.get("amount") != null && data.get("amount") !== "" ? Number(data.get("amount")) : null }, "요청 처리 내역을 기록했습니다." + (refund ? " 환불 송금은 별도로 처리해 주세요." : ""))); card.append(form);
        }
      }
      $("history").append(card);
    }
  }
  function renderAmend(r, actions) {
    const approved = actions.filter((a) => a.kind === "UPDATE_DETAILS" && a.state === "APPROVED"), form = $("amendForm"); form.hidden = !beforeProduction(r) || !approved.length;
    $("amendHelp").textContent = !beforeProduction(r) ? "제작 시작 후에는 문제 수정 요청으로 처리해 주세요." : !approved.length ? "요청·이력에서 변경 요청을 검토·승인하면 여기에서 신청 내용을 수정할 수 있습니다." : "승인된 요청에 따라 신청 정보를 수정해 주세요. 기존 계약 조건은 유지됩니다.";
    form.elements.actionId.replaceChildren(); for (const a of approved) { const o = node("option", a.body); o.value = a.id; form.elements.actionId.append(o); }
    $("amendFields").replaceChildren();
    for (const [key, labelText] of Object.entries(fields)) {
      if (!Object.hasOwn(r.details, key)) continue;
      const label = node("label", labelText), input = node(["scope", "requirements", "message"].includes(key) ? "textarea" : "input"); input.name = key; input.value = r.details[key]; input.maxLength = ["scope", "requirements", "message"].includes(key) ? 2000 : 120; label.append(input); $("amendFields").append(label);
    }
  }
  async function loadEvents() {
    try {
      const events = await api("/api/admin/exam-intake/events"); $("events").replaceChildren();
      $("eventCount").textContent = "미확인 " + events.filter((e) => !e.acknowledged_at).length + "건 · 전송 실패 " + events.filter((e) => e.state === "FAILED").length + "건 (최근 100건 기준)";
      if (!events.length) $("events").append(node("p", "알림 내역이 없습니다.", "muted"));
      for (const e of events) {
        const row = node("article", null, "event-row"), info = node("div"), buttons = node("div", null, "event-actions"), state = { PENDING: "전송 대기", SENDING: "전송 중", SENT: "메일 서버 전달", FAILED: "전송 실패" }[e.state] || e.state;
        const eventNames = { "application.created": "정식 신청 접수", "inquiry.created": "무료신청·문의 접수", "document.uploaded": "자료 제출", "payment.confirmation_requested": "입금 확인 요청", "payment.confirmed": "입금 확인 완료", "revision.requested": "변경·수정 요청", "refund.requested": "취소·환불 요청" };
        info.append(node("p", (eventNames[e.event_type] || "접수 처리 알림") + " · " + state + " · " + date(e.created_at)), node("small", "이벤트 번호 " + e.id));
        if (e.request_id) buttons.append(actionButton("접수 열기", () => loadDetail(e.request_id)));
        if (!e.acknowledged_at) buttons.append(actionButton("관리자 확인", async () => { await api("/api/admin/exam-intake/events/" + encodeURIComponent(e.id) + "/ack", "POST", {}); await loadEvents(); }));
        if (e.state === "FAILED") buttons.append(actionButton("전송 재시도", async () => { await api("/api/admin/exam-intake/events/" + encodeURIComponent(e.id) + "/retry", "POST", {}); await loadEvents(); note("실패한 알림을 전송 대기로 변경했습니다."); }));
        if (e.state === "SENT") buttons.append(actionButton("메일 수신 확인 기록", async () => { if (!window.confirm("수신함에서 이 이벤트 번호에 해당하는 메일을 실제로 확인했습니까?")) return; await api("/api/admin/exam-intake/events/" + encodeURIComponent(e.id) + "/received", "POST", { confirmedInMailbox: true }); note("메일의 실제 수신 확인을 기록했습니다."); }));
        row.append(info, buttons); $("events").append(row);
      }
    } catch (e) { $("eventCount").textContent = "조회 실패"; $("events").replaceChildren(node("p", "알림 내역을 불러오지 못했습니다. " + e.message, "error-box")); }
  }
  async function loadRetention() {
    const rows = await api("/api/admin/exam-intake/retention"); $("retentionPlan").replaceChildren();
    if (!rows.length) $("retentionPlan").append(node("p", "현재 정리 대상이 없습니다.", "muted"));
    for (const r of rows) {
      const form = node("form", null, "history-card"); form.append(node("strong", r.id + " · " + (states[r.state] || r.state)));
      for (const [key, text] of [["purposeComplete", "서비스 목적 달성과 정리 범위를 확인했습니다."], ["noLegalHold", "진행 중인 분쟁·법적 보존 유보가 없음을 확인했습니다."]]) { const label = node("label", null, "check"), input = node("input"); input.type = "checkbox"; input.name = key; input.required = true; label.append(input, document.createTextNode(text)); form.append(label); }
      const b = node("button", "학습자료 삭제·개인정보 정리"); b.type = "submit"; form.append(b);
      submit(form, async (data) => { if (!window.confirm("해당 접수의 학습자료 삭제는 되돌릴 수 없습니다. 법정 거래기록을 제외한 정리 범위를 확인했습니까?")) return; const value = await api("/api/exam-intake/requests/" + encodeURIComponent(r.id)); await api("/api/admin/exam-intake/requests/" + encodeURIComponent(r.id) + "/erase-learning-data", "POST", { version: value.request.version, purposeComplete: data.get("purposeComplete") === "on", noLegalHold: data.get("noLegalHold") === "on" }); if (current?.request.id === r.id) clearDetail(); await Promise.all([loadRetention(), loadList()]); note("학습자료를 정리했습니다. 법정 거래기록은 별도 보존합니다."); }); $("retentionPlan").append(form);
    }
  }
  submit($("deliveryForm"), (data) => save("/deliveries", { ordinal: Number(data.get("ordinal")), note: data.get("note"), accessibleAndReviewed: data.get("accessibleAndReviewed") === "on" }, "회차 전달을 기록했습니다."));
  submit($("earlyErasure"), async (data) => { if (!window.confirm("이 접수의 학습자료 삭제는 되돌릴 수 없습니다. 법정 거래기록을 제외한 정리 범위를 확인했습니까?")) return; await save("/erase-learning-data", { purposeComplete: data.get("purposeComplete") === "on", noLegalHold: data.get("noLegalHold") === "on" }, "학습자료와 불필요한 개인정보를 정리했습니다. 법정 거래기록은 별도 보존합니다."); closePreview(); });
  submit($("uploadForm"), async (data) => { const r = current?.request; if (!r) return; const file = data.get("file"); if (!(file instanceof File) || !file.size || file.size > 10 * 1024 * 1024 || !/\.(pdf|jpe?g|png)$/i.test(file.name)) throw new Error("10MiB 이하 PDF·JPEG·PNG 파일을 선택해 주세요."); await api("/api/exam-intake/requests/" + encodeURIComponent(r.id) + "/documents", "POST", data); if (current?.request.id === r.id) await loadDetail(r.id, false); note("자료를 제출했습니다. 검사 상태를 확인해 주세요."); });
  submit($("actionForm"), async (data) => { const r = current?.request; if (!r) return; await api("/api/exam-intake/requests/" + encodeURIComponent(r.id) + "/actions", "POST", { version: r.version, kind: data.get("kind"), body: data.get("body") }); if (current?.request.id === r.id) await loadDetail(r.id, false); note("고객 요청을 등록했습니다."); });
  submit($("amendForm"), (data) => { const details = {}; for (const key of Object.keys(fields)) details[key] = String(data.get(key) || ""); return save("/details", { actionId: data.get("actionId"), details }, "승인된 신청 내용 변경을 반영했습니다."); });
  $("requestSearch").addEventListener("input", renderList); $("requestFilter").addEventListener("change", renderList);
  document.querySelectorAll("[data-filter]").forEach((b) => b.addEventListener("click", () => { $("requestFilter").value = b.dataset.filter; renderList(); }));
  document.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => showTab(b.dataset.tab)));
  $("refresh").addEventListener("click", async () => { const id = current?.request.id; $("refresh").disabled = true; try { await Promise.all([loadList(), loadEvents(), id ? loadDetail(id, false) : Promise.resolve()]); } finally { $("refresh").disabled = false; } });
  $("previous").addEventListener("click", () => loadList(Math.max(0, page - 1))); $("next").addEventListener("click", () => loadList(page + 1));
  $("retentionRefresh").addEventListener("click", async () => { $("retentionRefresh").disabled = true; try { await loadRetention(); } catch (e) { $("retentionPlan").replaceChildren(node("p", "정리 대상을 조회하지 못했습니다. " + e.message, "error-box")); } finally { $("retentionRefresh").disabled = false; } });
  $("unusedGuardians").addEventListener("click", async () => { if (!window.confirm("만료·철회·거절된 미사용 보호자 연락처를 정리할까요? 삭제는 되돌릴 수 없습니다.")) return; $("unusedGuardians").disabled = true; try { const value = await api("/api/admin/exam-intake/retention/unused-guardians", "POST", {}); note(value.redacted + "건의 미사용 보호자 연락처를 정리했습니다."); } catch (e) { note(e.message, true); } finally { $("unusedGuardians").disabled = false; } });
  $("closeViewer").addEventListener("click", closePreview); $("documentViewer").addEventListener("cancel", (event) => { event.preventDefault(); closePreview(); }); $("documentViewer").addEventListener("close", () => { if (previewUrl) { URL.revokeObjectURL(previewUrl); previewUrl = null; } }); window.addEventListener("pagehide", closePreview);
  api("/api/exam-intake/config").then((config) => { $("offerSummary").textContent = config.summary; }).catch(() => { $("offerSummary").textContent = "서비스 안내를 불러오지 못했습니다. 선택한 접수의 계약 기록을 확인해 주세요."; });
  function showNotificationSettings(value) {
    const form = $("notificationForm");
    notificationVersion = value.version;
    for (const key of ["email", "phone", "naverAccount", "sender"])
      form.elements[key].value = value[key] || "";
    for (const key of ["emailEnabled", "smsEnabled"])
      form.elements[key].checked = value[key];
    for (const key of ["naverPassword", "solapiKey", "solapiSecret"])
      form.elements[key].value = "";
    const labels = { DISABLED: "사용 안 함", CONNECTION_REQUIRED: "발송 서비스 연결 필요", READY: "발송 준비됨 · 테스트로 수신 확인" };
    $("notificationSettingsStatus").textContent = "이메일: " + labels[value.emailStatus] + " / 문자: " + labels[value.smsStatus];
    $("testNotifications").disabled = ![value.emailStatus, value.smsStatus].includes("READY");
  }
  async function notificationSettings() {
    showNotificationSettings(await api("/api/admin/exam-intake/notifications"));
  }
  submit($("notificationForm"), async (data) => {
    if (!Number.isInteger(notificationVersion)) throw new Error("알림 설정을 먼저 불러온 뒤 저장해 주세요.");
    const input = { version: notificationVersion };
    for (const key of ["email", "phone", "naverAccount", "sender", "naverPassword", "solapiKey", "solapiSecret"])
      input[key] = String(data.get(key) || "");
    for (const key of ["emailEnabled", "smsEnabled"])
      input[key] = data.get(key) === "on";
    showNotificationSettings(await api("/api/admin/exam-intake/notifications", "PUT", input));
    $("notificationTestStatus").textContent = "";
    note("알림 설정을 저장했습니다. 연결 상태를 확인해 주세요.");
  });
  if ($("testNotifications"))
    $("testNotifications").addEventListener("click", async () => {
      const button = $("testNotifications");
      button.disabled = true;
      $("notificationTestStatus").textContent = "테스트 알림을 전송하고 있습니다.";
      try {
        const result = await api("/api/admin/exam-intake/notifications/test", "POST", {});
        const labels = { ACCEPTED: "발송 서비스가 접수했습니다. 휴대폰·메일 수신함을 확인해 주세요.", DISABLED: "사용 안 함", CONNECTION_REQUIRED: "발송 서비스 연결 필요", SMTP_FAILURE: "메일 전송 실패 · 네이버 설정과 앱 비밀번호를 확인해 주세요.", SMS_REJECTED: "문자 접수 실패 · 인증정보·발신번호·잔액을 확인해 주세요.", SMS_RESULT_UNKNOWN: "전송 결과 미확인 · 문자 서비스 발송 내역을 확인해 주세요." };
        $("notificationTestStatus").textContent = "이메일: " + labels[result.email] + " / 문자: " + labels[result.sms];
      } catch (e) {
        $("notificationTestStatus").textContent = e.message;
      } finally { button.disabled = false; }
    });

  notificationSettings().catch((e) => { $("notificationSettingsStatus").textContent = "알림 설정 조회 실패 · " + e.message; });
  if (new URL(location.href).hash === "#notifications") $("notifications").open = true;
  loadList(); loadEvents(); const requestedId = new URL(location.href).searchParams.get("request"); if (requestedId) loadDetail(requestedId, false);
})();
