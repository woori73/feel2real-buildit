/* F2R 영어 교습소 홈페이지(eduholic) — 2026 2학기 기말고사 LIVE 배너.
 *
 * 2026-11-24 대표 지시: "기말고사로 바꾸고 popup 말고 홈페이지에서 눈에 띄게",
 * "이번 시험은 압구정중2, 신구중2, 압구정중1".
 * - 팝업 대신 첫 화면(#sec-hero) 바로 아래에 큰 배너를 넣는다. 팝업은 V1144에서 끈다.
 * - 학교 버튼 3개는 메인 사이트 학교별 디지털 기말고사로 간다(실제 기말 문제가 있는 곳).
 * - 2026-11-24 대표 지시 "문제풀기도 학원에 두기", "대문에 있는 압구정중2 인강 등을 옮겨놓기":
 *   메인 홈 「디지털 영어 기말고사」의 학교 표(범위·회차), 압구정중2 직보 AI 인강, 문제 풀기·재원생 앱을 함께 싣는다.
 *   (메인 홈은 광고용으로 그대로 둔다.)
 * - hostname gate: eduholic.feel2real.co.kr 에서만. 다른 학원 공개 페이지에는 나오지 않는다.
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var BANNER_ID = 'eduholicFinalBanner';
  var MAIN = 'https://feel2real.co.kr';
  var SCHOOLS = [
    { label: '압구정중 2학년', range: 'Lesson 7·8 · 16회', href: MAIN + '/naesintest/school/apgujeong-m2' },
    { label: '신구중 2학년', range: 'Lesson 7·8 · 12회', href: MAIN + '/naesintest/school/singu-m2' },
    { label: '압구정중 1학년', range: 'Lesson 3~5 · 7회', href: MAIN + '/naesintest/school/apgujeong-m1' }
  ];
  var MORE = [
    { title: '2028 입시상담 AI', desc: '2028학년도 바뀌는 입시안 소개·상담', href: MAIN + '/admissions/guide' },
    { title: 'New iBT · Digital SAT', desc: 'TOEFL iBT 영역별 연습 · 디지털 SAT 연습 시험', href: MAIN + '/testprep' },
    { title: '2027 수능 영어 해설', desc: '45문항 모션 해설과 PDF', href: MAIN + '/csat/2027-english' }
  ];
  // 메인 홈 「학교별 직보 쇼츠 인강」과 같은 영상·포스터(같은 앱이 서빙하므로 경로 그대로).
  var SHORTS = {
    src: '/video/%EC%84%9C%EC%88%98%EB%8B%88_7%EA%B3%BC_%EC%82%AC%EC%97%AD%EB%8F%99%EC%82%AC_%EC%B9%A0%ED%8C%90_r4.mp4',
    poster: '/video/naesin-apm2-l7-causative-r4.jpg',
    label: '압구정중2 영어 7과 사역동사 목적격 보어 직보 AI 인강, 54초',
    more: MAIN + '/naesintest/school/apgujeong-m2#shorts'
  };

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
      a.appendChild(el('small', null, s.range));
      a.appendChild(el('span', null, '기말 모의고사 풀기 →'));
      list.appendChild(a);
    });
    sec.appendChild(list);

    var row = el('div', 'efb-row');
    var shorts = el('div', 'efb-shorts');
    shorts.appendChild(el('h3', null, '압구정중2 직보 AI 인강'));
    var v = el('video', 'efb-video');
    v.src = SHORTS.src;
    v.poster = SHORTS.poster;
    v.controls = true;
    v.preload = 'none';
    v.setAttribute('playsinline', '');
    v.setAttribute('aria-label', SHORTS.label);
    // 학원 공개 페이지 공용 CSS가 video 크기를 덮어써서(가로로 늘어남) 세로 9:16을 직접 고정한다.
    ['width:100%', 'max-width:240px', 'height:auto', 'aspect-ratio:9/16', 'object-fit:contain', 'margin:0 auto 10px', 'display:block']
      .forEach(function (kv) { var i = kv.indexOf(':'); v.style.setProperty(kv.slice(0, i), kv.slice(i + 1), 'important'); });
    shorts.appendChild(v);
    var more = el('a', 'efb-link', '압구정중2 직보 AI 인강 4편 보기 →');
    more.href = SHORTS.more;
    shorts.appendChild(more);
    row.appendChild(shorts);

    var tools = el('div', 'efb-tools');
    tools.appendChild(el('h3', null, '문제 풀기'));
    tools.appendChild(el('p', null, '내신·수능·iBT·SAT·TOEIC 문제를 디지털로 풀고 바로 채점합니다. 재원생은 앱을 설치하면 원장님이 배정한 시험을 바로 봅니다.'));
    var learn = el('a', 'efb-btn', '문제 풀기 →');
    learn.href = MAIN + '/learn';
    tools.appendChild(learn);
    var app = el('a', 'efb-btn efb-btn-line', '재원생 앱 설치 →');
    app.href = MAIN + '/naesintest/app';
    tools.appendChild(app);
    // 2026-11-24 대표 지시 "2028년 입시 상담과 ibt sat 등도 보여주기": 메인 사이트의 같은 서비스로 가는 카드(문제 풀기 칸 안).
    var more = el('div', 'efb-more');
    MORE.forEach(function (m) {
      var a = el('a', 'efb-more-item');
      a.href = m.href;
      a.appendChild(el('b', null, m.title));
      a.appendChild(el('span', null, m.desc));
      more.appendChild(a);
    });
    tools.appendChild(more);
    row.appendChild(tools);
    sec.appendChild(row);

    var loc = el('div', 'efb-loc');
    var locText = el('div', 'efb-loc-text');
    locText.appendChild(el('p', 'efb-loc-tag', '11월 25일 새 주소로 이전'));
    locText.appendChild(el('h3', null, 'F2R 영어 교습소 오시는 길'));
    locText.appendChild(el('p', 'efb-loc-addr', NEW_ADDR + ' (신사동 616-4)'));
    var tel = el('a', 'efb-btn', '전화 02-6324-1453');
    tel.href = 'tel:02-6324-1453';
    locText.appendChild(tel);
    var nmap = el('a', 'efb-btn efb-btn-line', '네이버 지도에서 보기 ↗');
    nmap.href = NAVER_MAP;
    nmap.setAttribute('target', '_blank');
    nmap.setAttribute('rel', 'noopener');
    locText.appendChild(nmap);
    loc.appendChild(locText);
    var frame = el('iframe', 'efb-map');
    frame.setAttribute('src', MAP_EMBED);
    frame.setAttribute('loading', 'lazy');
    frame.setAttribute('title', 'F2R 영어 교습소 위치 지도: ' + NEW_ADDR);
    frame.setAttribute('referrerpolicy', 'no-referrer-when-downgrade');
    loc.appendChild(frame);
    sec.appendChild(loc);
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
      '.efb-school small{font-size:13px;color:#5a6780}',
      '.efb-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.3fr);gap:14px;margin-top:16px}',
      '@media (max-width:640px){.efb-row{grid-template-columns:1fr}}',
      '.efb-shorts,.efb-tools{padding:14px;border-radius:12px;background:rgb(255 255 255/8%)}',
      '.efb-row h3{margin:0 0 10px;font-size:17px;color:#fff}',
      '.efb-tools p{margin:0 0 12px;color:#dbe4f5;line-height:1.5;word-break:keep-all}',
      '.efb-video{display:block;width:100%;max-width:260px;aspect-ratio:9/16;margin:0 auto 10px;border-radius:12px;background:#000;object-fit:cover}',
      '.efb-link{color:#9fe3b8;font-weight:700;text-decoration:none}',
      '.efb-btn{display:inline-flex;align-items:center;min-height:44px;padding:0 18px;margin:0 8px 8px 0;border-radius:999px;background:#2ecc71;color:#0f2a1a;font-weight:800;text-decoration:none}',
      '.efb-btn-line{background:transparent;color:#fff;border:1.5px solid rgb(255 255 255/60%)}',
      '.efb-link:focus-visible,.efb-btn:focus-visible{outline:3px solid #9fe3b8;outline-offset:2px}',
      '.efb-loc{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.2fr);gap:14px;margin-top:14px;padding:14px;border-radius:12px;background:rgb(255 255 255/8%)}',
      '@media (max-width:640px){.efb-loc{grid-template-columns:1fr}}',
      '.efb-loc h3{margin:0 0 6px;font-size:17px;color:#fff}',
      '.efb-loc-tag{display:inline-block;margin:0 0 8px;padding:3px 10px;border-radius:999px;background:#e74c3c;color:#fff;font-size:12px;font-weight:800}',
      '.efb-loc-addr{margin:0 0 12px;color:#dbe4f5;word-break:keep-all}',
      '.efb-map{width:100%;min-height:200px;border:0;border-radius:10px;background:#0f1a33}',
      '.efb-more{display:grid;grid-template-columns:1fr;gap:10px;margin-top:8px}',
      '.efb-more-item{display:flex;flex-direction:column;gap:4px;padding:12px 14px;border-radius:12px;border:1px solid rgb(255 255 255/25%);color:#fff;text-decoration:none;min-height:44px}',
      '.efb-more-item b{font-size:16px}',
      '.efb-more-item span{font-size:13px;color:#c9d5ee}',
      '.efb-more-item:hover{background:rgb(255 255 255/8%)}',
      '.efb-more-item:focus-visible{outline:3px solid #9fe3b8;outline-offset:2px}',
      '.efb-school:hover{transform:translateY(-1px);box-shadow:0 6px 14px rgb(0 0 0/18%)}',
      '.efb-school:focus-visible{outline:3px solid #9fe3b8;outline-offset:2px}'
    ].join('');
    document.head.appendChild(style);
  }

  // 2026-11-24 대표 지시: 메인 홈 코디 칸에 있던 교습소 이전 줄·주소·지도를 교습소 홈페이지로 옮긴다.
  var NEW_ADDR = '서울 강남구 언주로173길 12, 압구정빌딩 4층';
  var ADDR_Q = '%EC%84%9C%EC%9A%B8%20%EA%B0%95%EB%82%A8%EA%B5%AC%20%EC%96%B8%EC%A3%BC%EB%A1%9C173%EA%B8%B8%2012';
  var MAP_EMBED = 'https://maps.google.com/maps?q=' + ADDR_Q + '&z=17&output=embed';
  var NAVER_MAP = 'https://map.naver.com/p/search/' + ADDR_Q;

  // 2026-11-24 대표 지시 "학원 입장시 동영상도 홈페이지에 있는 F2R 영어 모집 영상으로 변경":
  // 첫 화면 태블릿 영상을 메인 홈 홍보 영상(F2R 영어 기말대비·예비고1 반 모집)으로 바꾼다. 가로 영상이라 잘리지 않게 contain.
  var RECRUIT = {
    src: '/video/landing/f2r-class-ad-20261120.mp4',
    poster: '/img/landing/f2r-class-ad-20261120.jpg'
  };
  function swapHeroVideo(hero) {
    var v = hero.querySelector('video.tablet-video');
    if (!v || v.getAttribute('data-f2r-recruit') === '1') return;
    v.setAttribute('data-f2r-recruit', '1');
    var s = v.querySelector('source');
    if (s) s.setAttribute('src', RECRUIT.src); else v.setAttribute('src', RECRUIT.src);
    v.setAttribute('poster', RECRUIT.poster);
    v.setAttribute('aria-label', 'F2R 영어 기말대비·예비고1 반 모집 영상');
    v.style.setProperty('object-fit', 'contain', 'important');
    v.style.setProperty('background', '#000', 'important');
    if (typeof v.load === 'function') v.load();
  }

  // 2026-11-25 대표 지시 "상단 로고 교체, 영상 첫화면에 박정민 원장 사진 삭제하고 화면 최적화,
  // 두번째 라이브는 중복에 중간 내용이 나오니 삭제. 나중에 다른 내용으로":
  // - 로고: 옛 EduHolic 그림 대신 'F2R 영어' 로고(대표 제공). 로고에 이름이 있어 옆 학원 이름 글자는 숨긴다.
  // - 첫 화면 태블릿: 원장 사진·사진 위 글자를 빼고, 가로(16:9) 화면으로 바꿔 모집 영상이 꽉 차게 반복 재생.
  // - 「2학기 영어 내신 LIVE」(sec-featured)는 위 기말 배너와 겹치고 중간고사 영상이 나와 숨긴다(지우지 않음).
  var F2R_LOGO = '/img/eduholic/f2r-english-logo.png'; // 대표가 준 'F2R 영어 · Feel to Real English' 로고(2026-11-25)
  var PATCH_STYLE_ID = 'f2r-eduholic-patch-style';
  function patchStyle() {
    if (document.getElementById(PATCH_STYLE_ID)) return;
    var st = document.createElement('style');
    st.id = PATCH_STYLE_ID;
    st.textContent = [
      '#sec-hero .tablet{aspect-ratio:auto!important;max-width:min(560px,100%)!important;width:100%!important;padding:10px!important;border-radius:20px!important}',
      '#sec-hero .tablet .cam{display:none!important}',
      '#sec-hero .tablet-screen{aspect-ratio:16/9!important;height:auto!important;border-radius:12px!important}',
      '#sec-hero .tablet-screen::after{display:none!important}',
      '#sec-hero .tablet-photo,#sec-hero .tablet-cap{display:none!important}',
      '#sec-hero .tablet-video{opacity:1!important;z-index:2!important;object-fit:contain!important}',
      '@media (max-width:760px){#sec-hero{padding-top:24px!important}}',
      '#sec-featured{display:none!important}',
      '.eh-logo-symbol.f2r-logo-box{width:auto!important;max-width:none!important;min-width:0!important;flex:0 0 auto!important;justify-content:flex-start!important;height:auto!important;padding:0!important;background:none!important;border:0!important;box-shadow:none!important}',
      '.eh-logo-symbol img[data-f2r-logo="1"]{display:block!important;width:auto!important;height:34px!important;max-width:none!important;object-fit:contain!important}',
      '@media (max-width:760px){.eh-logo-symbol img[data-f2r-logo="1"]{height:44px!important}}',
      '.eh-logo-component:has(img[data-f2r-logo="1"]){overflow:visible!important;width:auto!important;max-width:none!important}',
      '.eh-logo-component:has(img[data-f2r-logo="1"]) .eh-logo-text-area{display:none!important}'
    ].join('\n');
    document.head.appendChild(st);
  }
  function swapLogos() {
    var imgs = document.querySelectorAll('.eh-logo-symbol img');
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      if (img.getAttribute('data-f2r-logo') === '1') continue;
      img.setAttribute('data-f2r-logo', '1');
      img.setAttribute('src', F2R_LOGO);
      img.setAttribute('alt', 'F2R 영어 로고');
      if (img.parentNode && img.parentNode.classList) img.parentNode.classList.add('f2r-logo-box');
    }
  }
  function patchHero(hero) {
    var v = hero.querySelector('video.tablet-video');
    if (v && !v.loop) v.loop = true; // 사진으로 돌아가지 않고 영상만 반복
  }

  function tryInsert() {
    patchStyle();
    swapLogos();
    var heroNow = document.getElementById('sec-hero');
    if (heroNow) { swapHeroVideo(heroNow); patchHero(heroNow); }
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
      swapLogos();
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
