/*
 * NewStart Chrome Agent — FSS page content script (Phase 2 PoC).
 *
 * 동작: 화면이 로드되고 DOM이 바뀌면(fssStepDetector.collectFacts → classify)
 *  구조 사실만으로 단계를 판별하고, 그림자 DOM 상단 중앙 어시스턴트 카드로 안내한다.
 *  패널/배경으로 전송하는 것은 {step 번호, substate, evidence 카테고리 문자열, 버전} 뿐 —
 *  입력값·텍스트 내용·개인정보·쿠키는 어떤 경로로도 전송하지 않는다(§6).
 * 금지: 클릭/체크/폼 입력/인증 대행 등 어떤 자동 조작도 하지 않는다(§12).
 * 이 카드는 모달 오버레이가 아닌 비차단 fixed 요소 — 사용자는 FSS 화면을 보면서 조작한다.
 */
'use strict';

(function () {
  var det = globalThis.NewStartFssDetector;
  if (!det) return; /* detector load 실패 시 조용히 아무 것도 하지 않음 */

  var panel = null;
  var refs = {};
  var lastRendered = '';
  var authCompactDone = false;

  function ensurePanel() {
    if (panel) return;
    var host = document.createElement('div');
    host.id = 'newstart-chrome-agent-host';
    (document.documentElement || document.body).appendChild(host);
    panel = host.attachShadow({ mode: 'closed' });

    var style = document.createElement('style');
    /* DESKTOP: 상단 중앙 플로팅 카드 — top:24px, left:50%+translateX(-50%), 폭 450px.
       NARROW(<=560px): 좌우 8px 여백의 상단 full-width. viewport 밖으로 나가지 않음.
       금감원 흰/회색 카드와 명확히 구분: 남색 브랜드 헤더 그라데이션 + 진한 border + 강한 shadow. */
    style.textContent =
      ':host{all:initial}' +
      '.wrap{position:fixed;top:24px;left:50%;transform:translateX(-50%);width:450px;' +
      'max-width:calc(100vw - 32px);box-sizing:border-box;background:#fff;' +
      'border:1px solid #1e293b;border-top:3px solid #1d4ed8;border-radius:12px;' +
      'box-shadow:0 10px 30px rgba(15,23,42,.28);' +
      'font-family:-apple-system,"Malgun Gothic","맑은 고딕",sans-serif;' +
      'font-size:13px;color:#0f172a;z-index:2147483000;line-height:1.5}' +
      '@media (max-width:560px){.wrap{top:8px;left:8px;right:8px;transform:none;width:auto;max-width:none}}' +
      '.hd{display:flex;align-items:center;gap:8px;padding:9px 12px;' +
      'background:linear-gradient(90deg,#1e3a8a,#2563eb);color:#fff;border-radius:11px 11px 0 0}' +
      '.brand{display:flex;align-items:center;gap:7px;font-weight:800;font-size:13.5px;white-space:nowrap}' +
      '.brand .logo{width:9px;height:9px;border-radius:50%;background:#93c5fd;box-shadow:0 0 0 3px rgba(147,197,253,.35)}' +
      '.chip{font-size:12px;font-weight:700;color:#dbeafe;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
      '.wrap.is-min .chip{margin-right:auto}' +
      '.mini{margin-left:auto;border:0;background:rgba(255,255,255,.18);color:#fff;border-radius:7px;' +
      'font-size:12px;line-height:1;padding:4px 10px;cursor:pointer;white-space:nowrap}' +
      '.mini:hover{background:rgba(255,255,255,.3)}' +
      '.bd{padding:11px 13px 12px}' +
      '.wrap.is-min .bd{display:none}' +
      '.wrap.is-min{width:auto;max-width:calc(100vw - 32px)}' +
      '@media (max-width:560px){.wrap.is-min{width:auto}}' +
      /* 시각 우선순위: 1 현재 단계(최대) → 2 지금 할 행동 → 3 근거/주의 → 4 수동 안내 */
      '.st{font-weight:800;font-size:19px;line-height:1.3;color:#0f172a;margin-bottom:2px}' +
      '.sub{font-size:11.5px;color:#475569;font-weight:600;margin-bottom:8px}' +
      '.st.is-unknown{color:#b91c1c;font-size:16.5px}' +
      /* AUTH(본인인증 창): 실패가 아닌 정상 수동 단계 — 빨간 금지, 중립 남색. 작은 인증 UI를 덜 가리는 compact 폭. */
      '.st.is-auth{color:#1e3a8a}' +
      '.wrap.is-auth{width:360px}' +
      '.wrap.is-auth .st{font-size:16px}' +
      '.wrap.is-auth .gd{font-size:12.5px}' +
      '@media (max-width:560px){.wrap.is-auth{width:auto}}' +
      '.gd{color:#1f2937;font-size:13.5px;font-weight:600}' +
      '.ev{margin-top:8px;color:#64748b;font-size:11px;word-break:break-all;border-top:1px dashed #e2e8f0;padding-top:7px}' +
      '.ft{margin-top:9px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}' +
      '.open{color:#1d4ed8;font-size:12px;font-weight:600;text-decoration:underline}' +
      '.tag{color:#94a3b8;font-size:10.5px;margin-left:auto}';
    panel.appendChild(style);

    var wrap = document.createElement('div');
    wrap.className = 'wrap';

    var hd = document.createElement('div');
    hd.className = 'hd';
    var brand = document.createElement('div');
    brand.className = 'brand';
    var logo = document.createElement('span');
    logo.className = 'logo';
    var brandTx = document.createElement('span');
    brandTx.textContent = 'NewStart 안내';
    brand.appendChild(logo);
    brand.appendChild(brandTx);
    refs.chip = document.createElement('span');
    refs.chip.className = 'chip';
    var mini = document.createElement('button');
    mini.className = 'mini';
    mini.type = 'button';
    mini.textContent = '접기';
    mini.setAttribute('aria-expanded', 'true');
    mini.addEventListener('click', function () {
      var collapsed = wrap.classList.toggle('is-min');
      mini.textContent = collapsed ? '펼치기' : '접기';
      mini.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
    });
    hd.appendChild(brand);
    hd.appendChild(refs.chip);
    hd.appendChild(mini);
    refs.mini = mini;

    var bd = document.createElement('div');
    bd.className = 'bd';
    refs.step = document.createElement('div');
    refs.step.className = 'st';
    refs.sub = document.createElement('div');
    refs.sub.className = 'sub';
    refs.guide = document.createElement('div');
    refs.guide.className = 'gd';
    refs.ev = document.createElement('div');
    refs.ev.className = 'ev';
    var ft = document.createElement('div');
    ft.className = 'ft';
    var link = document.createElement('span');
    link.className = 'open';
    link.style.cursor = 'pointer';
    link.textContent = 'NewStart 수동 안내 열기';
    link.addEventListener('click', function () {
      /* 사용자가 직접 클릭할 때만 백그라운드에 이동 요청 — 페이지 조작 없음. */
      try { chrome.runtime.sendMessage({ type: 'OPEN_NEWSTART' }, function () { void chrome.runtime.lastError; }); } catch (e) { /* noop */ }
    });
    var tag = document.createElement('span');
    tag.className = 'tag';
    tag.textContent = 'v' + det.VERSION;
    ft.appendChild(link);
    ft.appendChild(tag);
    bd.appendChild(refs.step);
    bd.appendChild(refs.sub);
    bd.appendChild(refs.guide);
    bd.appendChild(refs.ev);
    bd.appendChild(ft);
    wrap.appendChild(hd);
    wrap.appendChild(bd);
    /* 인증/지도 popup처럼 창 자체가 낮은 경우에만 접은 상태로 시작 — bar에 "NewStart 안내 · STEP N".
       모바일 세로(높이 충분)는 펼침: 안내·오류가 상단에서 즉시 보여야 한다. */
    if (window.innerHeight < 480) {
      wrap.classList.add('is-min');
      mini.textContent = '펼치기';
      mini.setAttribute('aria-expanded', 'false');
    }
    panel.appendChild(wrap);
    refs.wrap = wrap;
  }

  function guideFor(r) {
    if (r.step === 4 && r.substate === 'POPUP_ALERT') return det.GUIDE['4-POPUP_ALERT'];
    return det.GUIDE[r.step] || det.GUIDE.UNKNOWN;
  }

  /* 두 단계 체계 분리(2026-09-24 QA 지시):
     - FSS 자체 4단계: "금감원 N단계 · NAME" (화면 타이틀 실측 근거) → 사용자에게 우선 표시
     - NewStart 내부 흐름(신청 준비·사전확인 포함 1~14): 보조 표기만 */
  function stepMain(r) {
    if (r.step === 'UNKNOWN') return '단계 자동 확인 실패';
    if (r.step === 'AUTH') return '본인인증 진행';
    if (r.fssStage) return '금감원 ' + r.fssStage.no + '단계 · ' + r.fssStage.name;
    return '현재 단계 ' + r.step + '으로 확인됩니다';
  }
  function stepSub(r) {
    if (r.step === 'UNKNOWN') return '아래 수동 안내를 이용해주세요';
    if (r.step === 'AUTH') return '이 창은 금감원 민원 단계가 아닌 인증 창 — 정상적인 사용자 직접 단계입니다';
    return 'NewStart 흐름 STEP ' + r.step + ' · 14단계 기준' +
      (r.fssStage ? ' · 금감원 진행nav ' + r.fssStage.no + '/4단계' : '');
  }
  function chipLabel(r) {
    if (r.step === 'UNKNOWN') return '· 확인 실패';
    if (r.step === 'AUTH') return '· 본인인증';
    if (r.fssStage) return '· 금감원 ' + r.fssStage.no + '단계';
    return '· STEP ' + r.step;
  }

  function render(r) {
    ensurePanel();
    var key = JSON.stringify([r.step, r.substate, r.fssStage, r.evidence]);
    if (key === lastRendered) return;
    lastRendered = key;
    refs.step.textContent = stepMain(r);
    refs.step.className = 'st' +
      (r.step === 'UNKNOWN' ? ' is-unknown' : (r.step === 'AUTH' ? ' is-auth' : ''));
    refs.sub.textContent = stepSub(r);
    refs.guide.textContent = guideFor(r);
    refs.ev.textContent = '근거: ' + (r.evidence || []).join(' / ');
    refs.chip.textContent = chipLabel(r);
    refs.wrap.classList.toggle('is-auth', r.step === 'AUTH');
    /* §7(3차 QA): 작은 인증 popup에서는 compact + 처음엔 접어서 인증 UI 가림 최소화.
       사용자의 접기/펼치기 수동 조작을 덮어쓰지 않도록 1회만 적용. */
    if (r.step === 'AUTH' && !authCompactDone) {
      authCompactDone = true;
      if (window.innerHeight < 620) {
        refs.wrap.classList.add('is-min');
        refs.mini.textContent = '펼치기';
        refs.mini.setAttribute('aria-expanded', 'false');
      }
    }
  }

  function report(r) {
    try {
      chrome.runtime.sendMessage({
        type: 'FSS_STEP',
        step: r.step,
        substate: r.substate || null,
        evidence: (r.evidence || []).slice(0, 6),
        version: det.VERSION
      }, function () { void chrome.runtime.lastError; });
    } catch (e) { /* extension context invalidated(리로드) — 재삽입 전까지 무시 */ }
  }

  function detect() {
    if (location.hostname !== 'www.fss.or.kr') return; /* defense-in-depth */
    var facts = det.collectFacts(document, location.href, window);
    var r = det.classify(facts);
    render(r);
    report(r);
  }

  /* §15: 동일 화면 DOM 갱신(모달/선택 노출)을 관측 — 판별만 다시 하고 조작은 하지 않는다. */
  var timer = 0;
  function scheduleDetect() {
    if (timer) clearTimeout(timer);
    timer = setTimeout(function () { timer = 0; detect(); }, 700);
  }

  function start() {
    detect();
    try {
      new MutationObserver(scheduleDetect).observe(
        document.documentElement, { subtree: true, childList: true, attributes: true });
    } catch (e) { /* noop */ }
    window.addEventListener('pageshow', scheduleDetect);
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) scheduleDetect();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
