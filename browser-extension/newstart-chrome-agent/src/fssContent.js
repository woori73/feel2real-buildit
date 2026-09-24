/*
 * NewStart Chrome Agent — FSS page content script (Phase 2 PoC).
 *
 * 동작: 화면이 로드되고 DOM이 바뀌면(fssStepDetector.collectFacts → classify)
 *  구조 사실만으로 단계를 판별하고, 그림자 DOM 미니 패널로 안내문만 표시한다.
 *  panel/배경으로 전송하는 것은 {step 번호, substate, evidence 카테고리 문자열, 버전} 뿐 —
 *  입력값·텍스트 내용·개인정보·쿠키는 어떤 경로로도 전송하지 않는다(§6).
 * 금지: 클릭/체크/폼 입력/인증 대행 등 어떤 자동 조작도 하지 않는다(§12).
 */
'use strict';

(function () {
  var det = globalThis.NewStartFssDetector;
  if (!det) return; /* detector load 실패 시 조용히 아무 것도 하지 않음 */

  var STALE_TITLE = 'NewStart 안내';
  var panel = null;
  var refs = {};
  var lastRendered = '';

  function ensurePanel() {
    if (panel) return;
    var host = document.createElement('div');
    host.id = 'newstart-chrome-agent-host';
    /* fixed. 데스크톱 우상단(top:90px,right:20px) — 스크롤 유지, FSS 상단 메뉴 회피. 좁은 화면은 상단 full-width. */
    (document.documentElement || document.body).appendChild(host);
    panel = host.attachShadow({ mode: 'closed' });

    var style = document.createElement('style');
    /* desktop: 우상단 고정(스크롤해도 유지) — FSS 상단 헤더/메뉴를 피하기 top:90px.
       narrow/mobile: 상단 full-width 계열(좌우 8px margin), 억지로 우측 좁은 박스 유지하지 않음. */
    style.textContent =
      ':host{all:initial}' +
      '.wrap{position:fixed;top:90px;right:20px;width:300px;max-width:calc(100vw - 40px);' +
      'box-sizing:border-box;background:#ffffff;border:1px solid #d5dae2;border-radius:10px;' +
      'box-shadow:0 6px 18px rgba(20,30,50,.18);font-family:-apple-system,"Malgun Gothic","맑은 고딕",sans-serif;' +
      'font-size:13px;color:#1c2430;z-index:2147483000;line-height:1.45}' +
      '@media (max-width:640px){.wrap{top:8px;left:8px;right:8px;width:auto;max-width:none}}' +
      '.hd{display:flex;align-items:center;justify-content:space-between;padding:8px 10px;' +
      'background:#f4f7fb;border-bottom:1px solid #e2e7ee;border-radius:10px 10px 0 0}' +
      '.hd b{font-size:12.5px}' +
      '.mini{border:0;background:transparent;font-size:15px;line-height:1;padding:2px 6px;cursor:pointer;color:#5a6678}' +
      '.bd{padding:9px 11px 11px}' +
      '.wrap.is-min .bd{display:none}' +
      '.wrap.is-min{width:auto;max-width:calc(100vw - 40px)}' +
      /* 시각 우선순위: 1 단계 → 2 행동(안내) → 3 근거/주의 → 4 수동 안내 */ +
      '.st{font-weight:800;font-size:15px;margin-bottom:5px}' +
      '.st.is-unknown{color:#b91c1c}' +
      '.gd{color:#1f2a38;font-size:13px}' +
      '.ev{margin-top:7px;color:#7b8798;font-size:11px;word-break:break-all}' +
      '.ft{margin-top:8px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}' +
      'a.open,.open{color:#2456a6;font-size:12px}' +
      '.tag{color:#9aa5b3;font-size:10.5px;margin-left:auto}';
    panel.appendChild(style);

    var wrap = document.createElement('div');
    wrap.className = 'wrap';
    var hd = document.createElement('div');
    hd.className = 'hd';
    var title = document.createElement('b');
    title.textContent = STALE_TITLE;
    var mini = document.createElement('button');
    mini.className = 'mini';
    mini.type = 'button';
    mini.textContent = '—';
    mini.addEventListener('click', function () {
      wrap.classList.toggle('is-min');
    });
    hd.appendChild(title);
    hd.appendChild(mini);

    var bd = document.createElement('div');
    bd.className = 'bd';
    refs.step = document.createElement('div');
    refs.step.className = 'st';
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
    bd.appendChild(refs.guide);
    bd.appendChild(refs.ev);
    bd.appendChild(ft);
    wrap.appendChild(hd);
    wrap.appendChild(bd);
    /* 인증/지도 popup처럼 창 자체가 낮은 경우에만 접은 상태로 시작(가림 최소화).
       모바일 세로(높이가 충분)는 상단 full-width 패널로 펼침 — 오류도 상단에서 즉시 보여야 한다. */
    if (window.innerHeight < 480) wrap.classList.add('is-min');
    panel.appendChild(wrap);
    refs.wrap = wrap;
  }

  function guideFor(r) {
    if (r.step === 4 && r.substate === 'POPUP_ALERT') return det.GUIDE['4-POPUP_ALERT'];
    return det.GUIDE[r.step] || det.GUIDE.UNKNOWN;
  }

  function stepLabel(r) {
    if (r.step === 'UNKNOWN') return '단계 자동 확인 실패';
    return '현재 단계 ' + r.step + '으로 확인됩니다 (14단계 기준)';
  }

  function render(r) {
    ensurePanel();
    var key = JSON.stringify([r.step, r.substate, r.evidence]);
    if (key === lastRendered) return;
    lastRendered = key;
    refs.step.textContent = stepLabel(r);
    refs.step.className = 'st' + (r.step === 'UNKNOWN' ? ' is-unknown' : '');
    refs.guide.textContent = guideFor(r);
    refs.ev.textContent = '근거: ' + (r.evidence || []).join(' / ');
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
    var facts = det.collectFacts(document, location.href);
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
