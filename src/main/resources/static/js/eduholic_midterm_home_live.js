/* 압구정에듀홀릭 홈페이지 — 「2학기 영어내신」(#sec-featured) 를 "2학기 영어 내신 LIVE" 로 전환한다.
 *
 * 목적(작업지시서 §9~§12):
 *  - 섹션 제목/부제를 "2학기 영어 내신 LIVE" 로 바꾸고, 학교 카드를 작은 LIVE window 로 만든다.
 *  - 카드 내부 scene(이번 시험 준비 → 출제 경향 분석 → 수업 반영)이 자동으로 바뀐다.
 *  - 데이터는 academy_public.js 의 wireFeaturedSchoolCards 가 이미 채운 실제 출제자 Digital Twin
 *    (/api/analyzes) 결과를 그대로 재사용한다 — 추가 fetch 없음(N+1 금지, §17).
 *  - 없는 학교/분석을 만들어내지 않는다. 비어 있으면 "자료 확인 중" 정적 상태.
 *
 * 안전장치:
 *  - hostname gate(eduholic.feel2real.co.kr) — 다른 Academy 공개 페이지에는 절대 실행되지 않는다.
 *  - 공용 섹션 렌더러 정본·상담 신청 버튼·pill CTA(eduholic_featured_cta.js)는 건드리지 않는다.
 *    #sec-featured 안, .featured-schoolcard 안에서만 동작한다.
 *  - prefers-reduced-motion → 첫 scene 고정. 화면 밖 → 타이머 정지(IntersectionObserver).
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var LANDING_PATH = '/eduholic-midterm-2026.html';
  var MIDTERM_LIVE_URL = '/eduholic-midterm-2026.html';
  var SCENE_MS = 4500;

  if (window.location.hostname !== EDUHOLIC_HOST) return;
  if (window.location.pathname === LANDING_PATH) return;

  function reduceMotion() {
    return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }
  function text(node, value) { if (node && node.textContent !== value) node.textContent = value; }

  function applyHeading() {
    var head = document.querySelector('#sec-featured .featured-head');
    if (!head || head.dataset.mtHomeLiveHead === 'true') return;
    var title = head.querySelector('h2.featured-title');
    if (!title) return;
    head.dataset.mtHomeLiveHead = 'true';

    text(head.querySelector('.featured-eyebrow-text'), '내신 LIVE');
    text(title, '2학기 영어 내신 LIVE');

    var schools = Array.prototype.slice
      .call(document.querySelectorAll('#sec-featured [data-featured-card]'))
      .map(function (c) { return (c.getAttribute('data-school') || '').trim(); })
      .filter(Boolean);
    var desc = head.querySelector('.featured-desc');
    if (desc) {
      desc.textContent = (schools.length ? schools.join(' · ') + ' — ' : '')
        + '학교별 시험 준비가 지금 진행되고 있습니다.';
    }
  }

  // 카드 본문(academy_public.js 가 채움)에서 실제 분석 행을 읽어 scene 배열로 만든다.
  function readScenes(card) {
    var school = (card.getAttribute('data-school') || '').trim();
    var body = card.querySelector('[data-featured-card-body]');
    if (!body) return null;

    if (body.querySelector('.featured-schoolcard-loading')) return 'pending';
    if (body.querySelector('.featured-schoolcard-empty')) {
      return [{ tag: '준비 중', head: school + ' 시험 준비', lines: ['출제 자료를 확인하고 있습니다'] }];
    }
    var rows = Array.prototype.slice.call(body.querySelectorAll('.featured-schoolcard-row'));
    if (!rows.length) return null;

    var countEl = body.querySelector('.featured-schoolcard-count');
    var count = countEl ? (countEl.textContent.match(/\d+/) || ['0'])[0] : String(rows.length);
    var firstRow = rows[0].textContent.replace(/[›»]/g, '').trim();
    var firstLink = rows[0].querySelector('a');
    var href = firstLink ? firstLink.getAttribute('href') : MIDTERM_LIVE_URL;
    var gradeMatch = firstRow.match(/(\d+)\s*학년/);
    var gradeLabel = gradeMatch ? gradeMatch[1] + '학년' : '';

    return [
      { tag: '● LIVE', head: school + (gradeLabel ? ' · ' + gradeLabel : ''),
        lines: ['이번 시험 범위 확인', '학교별 시험자료 분석 중'] },
      { tag: '출제 경향', head: school + ' 출제 경향 분석',
        lines: ['출제자 Digital Twin ' + count + '건', firstRow] },
      { tag: '수업 반영', head: school + ' 수업 준비',
        lines: ['분석 결과를 수업에 반영합니다'], href: href, hrefLabel: '준비 현황 보기 →' }
    ];
  }

  function renderScene(stage, scene) {
    stage.innerHTML = '';
    var tag = document.createElement('span');
    tag.className = 'mt-hl-tag';
    tag.textContent = scene.tag;
    stage.appendChild(tag);
    var head = document.createElement('b');
    head.className = 'mt-hl-head';
    head.textContent = scene.head;
    stage.appendChild(head);
    scene.lines.forEach(function (l) {
      var p = document.createElement('span');
      p.className = 'mt-hl-line';
      p.textContent = l;
      stage.appendChild(p);
    });
    if (scene.href) {
      var a = document.createElement('a');
      a.className = 'mt-hl-link';
      a.href = scene.href;
      if (/^https?:|^\//.test(scene.href) && scene.href.indexOf(LANDING_PATH) === -1) {
        a.target = '_blank';
        a.rel = 'noopener';
      }
      a.textContent = scene.hrefLabel || '자세히 →';
      stage.appendChild(a);
    }
  }

  function upgradeCard(card) {
    if (card.dataset.mtHomeLive === 'true') return;
    var scenes = readScenes(card);
    if (scenes === 'pending' || scenes === null) return;
    card.dataset.mtHomeLive = 'true';

    var kind = card.querySelector('.featured-schoolcard-kind');
    if (kind) kind.textContent = '학교별 준비 LIVE';

    var body = card.querySelector('[data-featured-card-body]');
    body.innerHTML = '';
    var stage = document.createElement('div');
    stage.className = 'mt-hl-stage';
    body.appendChild(stage);

    var i = 0;
    renderScene(stage, scenes[i]);

    if (reduceMotion() || scenes.length < 2) return;

    var timer = null;
    var running = false;
    function step() {
      if (!running) return;
      i = (i + 1) % scenes.length;
      stage.classList.add('is-swap');
      timer = setTimeout(function () {
        renderScene(stage, scenes[i]);
        stage.classList.remove('is-swap');
        timer = setTimeout(step, SCENE_MS);
      }, 260);
    }
    function start() { if (running) return; running = true; timer = setTimeout(step, SCENE_MS); }
    function stop() { running = false; if (timer) { clearTimeout(timer); timer = null; } }

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { if (e.isIntersecting) start(); else stop(); });
      }, { threshold: 0.2 }).observe(card);
    } else {
      start();
    }
  }

  function scan() {
    if (window.location.hostname !== EDUHOLIC_HOST) return;
    applyHeading();
    Array.prototype.slice.call(document.querySelectorAll('#sec-featured [data-featured-card]'))
      .forEach(upgradeCard);
  }

  function injectStyle() {
    if (document.getElementById('eduholicMidtermHomeLiveStyle')) return;
    var style = document.createElement('style');
    style.id = 'eduholicMidtermHomeLiveStyle';
    style.textContent = [
      '#sec-featured .featured-schoolcard-kind{display:inline-flex;align-items:center;gap:6px;}',
      '#sec-featured .mt-hl-stage{display:flex;flex-direction:column;gap:6px;min-height:132px;',
      '  transition:opacity .24s ease, transform .24s ease;}',
      '#sec-featured .mt-hl-stage.is-swap{opacity:0;transform:translateY(6px);}',
      '#sec-featured .mt-hl-tag{align-self:flex-start;font-size:11px;font-weight:800;letter-spacing:.02em;',
      '  padding:3px 9px;border-radius:999px;background:#eaf0fe;color:#2b3a9e;}',
      '#sec-featured .mt-hl-head{font-size:15px;font-weight:800;letter-spacing:-.02em;color:#0a0a0c;}',
      '#sec-featured .mt-hl-line{font-size:13px;line-height:1.55;color:#67707c;}',
      '#sec-featured .mt-hl-link{margin-top:4px;align-self:flex-start;font-size:12.5px;font-weight:800;',
      '  color:#1d4ed8;text-decoration:none;}',
      '#sec-featured .mt-hl-link:focus-visible{outline:3px solid #93c5fd;outline-offset:3px;}',
      '@media (prefers-reduced-motion:reduce){#sec-featured .mt-hl-stage{transition:none;}}'
    ].join('');
    document.head.appendChild(style);
  }

  function start() {
    injectStyle();
    scan();
    var pv = document.getElementById('publicView');
    if (pv && window.MutationObserver) {
      var mo = new MutationObserver(function () { scan(); });
      mo.observe(pv, { childList: true, subtree: true });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
