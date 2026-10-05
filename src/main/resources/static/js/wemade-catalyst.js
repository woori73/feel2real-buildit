/* 위메이드 Q4 Triple Catalyst — 3대 이벤트 카드 + 복합 시나리오 렌더링.
 * 읽기 전용. /api/wemade/catalyst/* 에서 데이터를 가져와 표시한다.
 * D-Day는 서버에서 자동 계산된 값을 그대로 표시한다.
 */
(function () {
  "use strict";

  var API = "/api/wemade/catalyst";

  function gradeBadge(g) {
    var map = { A: "A·공식", B: "B·신뢰", C: "C·언론", D: "D·계산", U: "U·미확인" };
    return '<span class="grade ' + (g || "U") + '">' + (map[g] || map.U) + "</span>";
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function renderCards(events) {
    var box = document.getElementById("cat-cards");
    if (!box) return;
    var labels = { DEAL_CLOSE: "①", NCW_LAUNCH: "②", DIGITAL_ASSET_LAW: "③" };
    box.innerHTML = events.map(function (e) {
      var unconf = e.status === "UNCONFIRMED";
      var dday = e.dday
        ? '<span class="dday' + (e.dday === "D-Day" ? "" : "") + '">' + esc(e.dday) + "</span>"
        : '<span class="dday na">일정 미정</span>';
      var facts = (e.keyFacts || []).map(function (f) {
        return '<li><span class="fk">' + esc(f.k) + "</span>" +
          '<span class="fv">' + esc(f.v) + gradeBadge(f.g) + "</span></li>";
      }).join("");
      return '<div class="cat-card' + (unconf ? " unconfirmed" : "") + '">' +
        '<span class="num">' + (labels[e.catalystKey] || "") + " CATALYST</span>" +
        "<h3>" + esc(e.nameKo) + gradeBadge(e.grade) +
        (unconf ? '<span class="unconf-tag">미확정</span>' : "") + "</h3>" +
        dday +
        '<div class="tlabel">' + esc(e.targetLabel || "") + "</div>" +
        '<p class="summ">' + esc(e.summary || "") + "</p>" +
        (facts ? '<ul class="cat-facts">' + facts + "</ul>" : "") +
        '<div class="src">출처: ' + esc(e.sourceName || "—") +
        (e.asOfDate ? " · 기준일 " + esc(e.asOfDate) : "") + "</div>" +
        "</div>";
    }).join("");
  }

  function renderComposite(comp) {
    var ph = document.getElementById("cat-phases");
    if (ph && comp.phases) {
      ph.innerHTML = comp.phases.map(function (p) {
        return '<div class="phase"><div class="pw">' + esc(p.when) + "</div>" +
          '<div class="pt">' + esc(p.title) + "</div>" +
          '<div class="pd">' + esc(p.description) + "</div></div>";
      }).join("");
    }
    var fn = document.getElementById("cat-flow-note");
    if (fn) fn.textContent = comp.flowNote || "";
  }

  function renderPolicy(policies) {
    var box = document.getElementById("cat-policy");
    if (!box) return;
    box.innerHTML = policies.map(function (p) {
      return "<p><strong>" + esc(p.title) + "</strong>" +
        (p.isConfirmed ? "" : ' <span class="unconf-tag">통과 미확정</span>') +
        gradeBadge(p.grade) + "<br/>" +
        "단계: " + esc(p.stage) +
        (p.stageDate ? " (" + esc(p.stageDate) + ")" : " — 추적 중") + "<br/>" +
        esc(p.summary || "") + "<br/>" +
        (p.p2eRelevance ? "P2E 연관: " + esc(p.p2eRelevance) + "<br/>" : "") +
        '<span style="color:#888;font-size:11px">출처: ' + esc(p.sourceName || "—") +
        (p.asOfDate ? " · 기준일 " + esc(p.asOfDate) : "") + "</span></p>";
    }).join("");
  }

  function get(url) {
    return fetch(url).then(function (r) {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (!document.getElementById("catalyst-section")) return;
    get(API + "/events").then(renderCards).catch(function () {});
    get(API + "/composite").then(renderComposite).catch(function () {});
    get(API + "/policy").then(renderPolicy).catch(function () {});
  });
})();
