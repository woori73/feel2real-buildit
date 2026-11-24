/* F2R 영어 교습소 홈페이지(eduholic) — 2026 2학기 기말고사 LIVE 배너.
 *
 * 2026-11-24 대표 지시: "기말고사로 바꾸고 popup 말고 홈페이지에서 눈에 띄게",
 * "이번 시험은 압구정중2, 신구중2, 압구정중1".
 * - 팝업 대신 첫 화면(#sec-hero) 바로 아래에 큰 배너를 넣는다. 팝업은 V1144에서 끈다.
 * - 학교 버튼 3개는 메인 사이트 학교별 디지털 기말고사로 간다(실제 기말 문제가 있는 곳).
 * - hostname gate: eduholic.feel2real.co.kr 에서만. 다른 학원 공개 페이지에는 나오지 않는다.
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var BANNER_ID = 'eduholicFinalBanner';
  var SCHOOLS = [
    { label: '압구정중 2학년', href: 'https://feel2real.co.kr/naesintest/school/apgujeong-m2' },
    { label: '신구중 2학년', href: 'https://feel2real.co.kr/naesintest/school/singu-m2' },
    { label: '압구정중 1학년', href: 'https://feel2real.co.kr/naesintest/school/apgujeong-m1' }
  ];

  if (window.location.hostname !== EDUHOLIC_HOST) return;

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function build() {
    var sec = el('section', 'efb');
    sec.id = BANNER_ID;
    sec.setAttribute('aria-labelledby', BANNER_ID + 'Title');
    var kicker = el('p', 'efb-kicker');
    kicker.appendChild(el('span', 'efb-dot'));
    kicker.appendChild(document.createTextNode('FINAL LIVE'));
    sec.appendChild(kicker);
    var h = el('h2', 'efb-title', '2026 2학기 기말고사 내신 LIVE');
    h.id = BANNER_ID + 'Title';
    sec.appendChild(h);
    sec.appendChild(el('p', 'efb-body',
      '학교별 시험 범위에 맞춘 디지털 기말 모의고사를 풀고, 바로 채점·해설로 복습하세요.'));
    var list = el('div', 'efb-schools');
    list.setAttribute('role', 'list');
    SCHOOLS.forEach(function (s) {
      var a = el('a', 'efb-school');
      a.href = s.href;
      a.setAttribute('role', 'listitem');
      a.setAttribute('aria-label', s.label + ' 2학기 기말 모의고사 풀기');
      a.appendChild(el('b', null, s.label));
      a.appendChild(el('span', null, '기말 모의고사 풀기 →'));
      list.appendChild(a);
    });
    sec.appendChild(list);
    return sec;
  }

  function injectStyle() {
    if (document.getElementById(BANNER_ID + 'Style')) return;
    var style = document.createElement('style');
    style.id = BANNER_ID + 'Style';
    style.textContent = [
      '.efb{max-width:1120px;margin:20px auto;padding:22px 20px;border-radius:18px;',
      'background:linear-gradient(135deg,#1b2a4e,#24407a);color:#fff;box-shadow:0 10px 28px rgb(20 35 70/25%);box-sizing:border-box}',
      '@media (max-width:1160px){.efb{margin:16px 16px}}',
      '.efb-kicker{display:flex;align-items:center;gap:8px;margin:0 0 6px;font-size:13px;font-weight:800;letter-spacing:.12em;color:#9fe3b8}',
      '.efb-dot{width:9px;height:9px;border-radius:50%;background:#2ecc71;box-shadow:0 0 0 0 rgb(46 204 113/60%)}',
      '@media (prefers-reduced-motion:no-preference){.efb-dot{animation:efbPulse 1.6s infinite}}',
      '@keyframes efbPulse{0%{box-shadow:0 0 0 0 rgb(46 204 113/60%)}70%{box-shadow:0 0 0 10px rgb(46 204 113/0)}100%{box-shadow:0 0 0 0 rgb(46 204 113/0)}}',
      '.efb-title{margin:0 0 8px;font-size:clamp(22px,4.2vw,32px);line-height:1.25;color:#fff;word-break:keep-all}',
      '.efb-body{margin:0 0 16px;color:#dbe4f5;line-height:1.55;word-break:keep-all}',
      '.efb-schools{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}',
      '@media (max-width:640px){.efb-schools{grid-template-columns:1fr}}',
      '.efb-school{display:flex;flex-direction:column;gap:4px;padding:14px 16px;border-radius:12px;background:#fff;color:#1b2a4e;text-decoration:none;min-height:44px}',
      '.efb-school b{font-size:18px}',
      '.efb-school span{font-size:14px;color:#2b5aa8;font-weight:700}',
      '.efb-school:hover{transform:translateY(-1px);box-shadow:0 6px 14px rgb(0 0 0/18%)}',
      '.efb-school:focus-visible{outline:3px solid #9fe3b8;outline-offset:2px}'
    ].join('');
    document.head.appendChild(style);
  }

  function tryInsert() {
    if (document.getElementById(BANNER_ID)) return true;
    var hero = document.getElementById('sec-hero');
    if (!hero) return false;
    injectStyle();
    hero.insertAdjacentElement('afterend', build());
    return true;
  }

  function start() {
    if (tryInsert()) return;
    // 섹션은 academy_sections.js 가 비동기로 그린다. 첫 화면이 생기면 한 번 넣고 멈춘다.
    var obs = new MutationObserver(function () {
      if (tryInsert()) obs.disconnect();
    });
    obs.observe(document.body, { childList: true, subtree: true });
    setTimeout(function () { obs.disconnect(); }, 15000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
