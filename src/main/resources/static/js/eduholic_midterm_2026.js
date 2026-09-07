/* 2026 2학기 중간고사 내신 LIVE — 압구정에듀홀릭 공개 쇼윈도 렌더러.
 *
 * 데이터: GET /api/academies/{handle}/midterm-campaign (미발행/부재 → 404 → 간결한 폴백).
 * 이 페이지는 프레젠테이션만 담당한다 — 제작/관리 workspace 로 보내지 않는다.
 *
 * 역할 구분: Wiki = 자료 저장소 / Midterm Campaign = 이번 시험 큐레이션 / 이 페이지 = 학부모 쇼윈도.
 * Wiki 원본을 직접 조회하지 않는다 — 운영자가 관리화면에서 이번 시험에 보여줄 자료만 골라
 * 서버가 안전한 공개 DTO(thumbnailUrl/fileUrl/access)로 내려준 것만 그린다(PR #220 보안 유지).
 */
(function () {
  'use strict';

  document.documentElement.classList.add('mt-js');

  var root = document.getElementById('mtRoot');
  if (!root) return;
  var HANDLE = root.getAttribute('data-handle') || 'eduholic';
  var API = '/api/academies/' + encodeURIComponent(HANDLE) + '/midterm-campaign';
  var HOME_URL = 'https://eduholic.feel2real.co.kr/';
  var CONSULT_URL = 'https://eduholic.feel2real.co.kr/';

  var state = { campaign: null };

  fetch(API, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
    .then(function (r) {
      if (r.status === 404) return null;
      if (!r.ok) throw new Error('load failed: ' + r.status);
      return r.json();
    })
    .then(function (data) {
      if (!data) { renderFallback(); return; }
      state.campaign = data;
      render();
      window.addEventListener('hashchange', route);
      route();
    })
    .catch(function () { renderFallback(); });

  // ── helpers ────────────────────────────────────────────────────────────
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function frag() { return document.createDocumentFragment(); }
  function firstSentence(s, cap) {
    if (!s) return '';
    var t = String(s).trim().split(/(?<=[.!?。])\s|\n/)[0].trim();
    if (cap && t.length > cap) t = t.slice(0, cap).trim() + '…';
    return t;
  }
  function schoolTitle(col) {
    var g = col.grade ? col.grade + '학년' : '';
    return (col.schoolName || '').trim() + (g ? ' ' + g : '');
  }
  function dParts(iso) {
    var p = String(iso || '').split('-');
    return p.length >= 3 ? { m: parseInt(p[1], 10), d: parseInt(p[2], 10), iso: iso } : null;
  }
  function shortDate(iso) { var x = dParts(iso); return x ? x.m + '.' + x.d : ''; }
  function humanDate(iso) {
    var x = dParts(iso);
    if (!x) return '';
    var now = new Date();
    var t = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var that = new Date(iso + 'T00:00:00');
    var diff = Math.round((t - that) / 86400000);
    if (diff === 0) return '오늘';
    if (diff === 1) return '어제';
    return x.m + '월 ' + x.d + '일';
  }
  var CAT_LABEL = {
    SCHOOL_PRINT: '학교 프린트', ANALYSIS: '본문 분석', WORKSHEET: '학습지',
    VOCABULARY: '어휘', GRAMMAR: '어법', VARIANT: '변형문제', FINAL_REVIEW: '시험 직전 자료',
    DIGITAL_TWIN: '출제자 분석'
  };
  function catLabel(c) { return CAT_LABEL[c] || '학습자료'; }
  function isLocked(access) { return access === 'MEMBER_LOCKED' || access === 'PREVIEW'; }
  function lockText(access) { return access === 'PREVIEW' ? '미리보기' : '로그인 회원 자료'; }

  function firstSchoolThumb(col) {
    var m = (col.materials || []).filter(function (x) { return x.thumbnailUrl; })[0];
    return m ? m.thumbnailUrl : null;
  }
  // HERO media 는 실제 "사람/현장"(원장 브리핑 영상 썸네일)만 쓴다(§15·§20). 문서 썸네일을
  // 풀블리드 cover 로 늘리면 약해 보인다 — 자료는 아래 showcase 에서 통째로 크게 보여준다.
  // 브리핑 영상이 아직 없으면 절제된 타이포 히어로(빈 화면 아님).
  function pickHeroImage(c) {
    var s = (c.schoolCollections || []);
    for (var i = 0; i < s.length; i++) {
      if (s[i].briefing && s[i].briefing.thumbnailUrl) return s[i].briefing.thumbnailUrl;
    }
    return null;
  }
  function firstBriefingSchool(c) {
    return (c.schoolCollections || []).filter(function (s) {
      return s.briefing && s.briefing.videoUrl;
    })[0] || null;
  }

  // ── 폴백 (미발행/부재) — 짧게 ──────────────────────────────────────────
  function renderFallback() {
    root.innerHTML = '';
    root.appendChild(renderTopbar());
    var s = el('section', 'mt-hero mt-hero--plain');
    var b = el('div', 'mt-hero-body');
    b.appendChild(el('h1', null, '2학기 중간고사 준비를 곧 공개합니다.'));
    b.appendChild(el('p', null, '학교별 준비 현황과 자료가 준비되는 대로 이곳에 올라옵니다.'));
    var a = el('a', 'mt-empty-cta', '상담 문의');
    a.href = CONSULT_URL; a.rel = 'noopener';
    a.style.marginTop = '18px';
    b.appendChild(a);
    s.appendChild(b);
    root.appendChild(s);
  }

  // ── 렌더 ──────────────────────────────────────────────────────────────
  function render() {
    var c = state.campaign;
    root.innerHTML = '';
    root.classList.add('has-sticky');
    root.appendChild(renderTopbar());

    var home = el('div', 'mt-home');
    home.appendChild(renderHero(c));
    // 학교 컬렉션 섹션은 항상 렌더한다(id="mt-collections"). 0개여도 안내가 있어야
    // sticky CTA 의 목적지가 죽지 않는다(PR #225 regression).
    home.appendChild(renderCollections(c.schoolCollections || []));

    if (c.campaignMaterials && c.campaignMaterials.length) {
      home.appendChild(renderMaterialShowcase(c.campaignMaterials));
    }
    var bfSchool = firstBriefingSchool(c);
    if (bfSchool) home.appendChild(renderBriefingMoment(bfSchool));

    if (c.campaignUpdateLogs && c.campaignUpdateLogs.length) {
      home.appendChild(renderUpdates(c.campaignUpdateLogs));
    }
    if (c.nextExam) home.appendChild(renderNextExam(c.nextExam));
    // 학교 컬렉션이 0개면 위 "우리 학교" 안내에 이미 상담 동선이 있으므로 별도 상담 섹션은 생략(§13).
    var hasContent = (c.schoolCollections && c.schoolCollections.length)
      || (c.campaignMaterials && c.campaignMaterials.length);
    if (hasContent) home.appendChild(renderConsult(c));
    root.appendChild(home);

    var detail = el('div', 'mt-detail');
    detail.id = 'mtDetail';
    root.appendChild(detail);

    root.appendChild(renderFooter(c));
    root.appendChild(renderSticky());

    wireReveal(root);
  }

  function renderTopbar() {
    var bar = el('header', 'mt-topbar');
    var wm = el('a', 'mt-wordmark');
    wm.href = HOME_URL; wm.rel = 'noopener';
    wm.appendChild(el('b', null, '압구정에듀홀릭'));
    bar.appendChild(wm);
    var c = state.campaign;
    var season = c ? (c.examYear + ' ' + c.semester + ' ' + c.examType) : '2026 2학기 중간고사';
    bar.appendChild(el('span', 'mt-season', season));
    return bar;
  }

  function renderHero(c) {
    var img = pickHeroImage(c);
    var s = el('section', 'mt-hero ' + (img ? 'mt-hero--media' : 'mt-hero--plain'));
    s.setAttribute('data-reveal', '');

    if (img) {
      var fig = el('div', 'mt-hero-figure');
      var im = document.createElement('img');
      im.src = img; im.alt = ''; im.loading = 'eager'; im.decoding = 'async';
      fig.appendChild(im);
      s.appendChild(fig);
    }

    var body = el('div', 'mt-hero-body');
    var h1 = el('h1', null, (c.hero && c.hero.title && c.hero.title.trim())
      ? c.hero.title.trim()
      : '2학기 중간고사, 지금 준비하고 있습니다.');
    h1.setAttribute('data-reveal-item', '');
    body.appendChild(h1);

    // media 히어로는 copy 를 최소화한다(설명하지 않는다). plain 히어로만 한 문장을 둔다.
    var sub = img ? '' : firstSentence(c.hero && c.hero.body, 64);
    if (sub) {
      var p = el('p', null, sub);
      p.setAttribute('data-reveal-item', '');
      body.appendChild(p);
    }

    var chips = (c.liveChips || []).filter(function (ch) { return ch.updatedOn; }).slice(0, 4);
    if (chips.length) {
      var strip = el('div', 'mt-livestrip');
      strip.setAttribute('data-reveal-item', '');
      chips.forEach(function (ch) {
        var span = el('span');
        span.appendChild(el('i'));
        span.appendChild(document.createTextNode(String(ch.label || '').replace(/\s*(\d+)\s*$/, ' $1학년')));
        var t = el('time', null, ' ' + shortDate(ch.updatedOn));
        t.dateTime = ch.updatedOn;
        span.appendChild(t);
        strip.appendChild(span);
      });
      body.appendChild(strip);
    }
    s.appendChild(body);
    return s;
  }

  function renderCollections(collections) {
    var s = el('section', 'mt-block mt-block--tight');
    s.id = 'mt-collections';
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var h2 = el('h2', 'mt-h2', '우리 학교');
    h2.setAttribute('data-reveal-item', '');
    w.appendChild(h2);

    if (!collections.length) {
      var note = el('p', 'mt-collections-empty', '아직 등록된 학교가 없습니다. 학교별 준비가 시작되면 이곳에서 확인할 수 있습니다.');
      note.setAttribute('data-reveal-item', '');
      var cta = el('a', 'mt-empty-cta', '상담 문의');
      cta.href = CONSULT_URL; cta.rel = 'noopener';
      note.appendChild(document.createElement('br'));
      note.appendChild(cta);
      w.appendChild(note);
      s.appendChild(w);
      return s;
    }

    var note2 = el('p', 'mt-note', '학교와 학년을 고르면 준비 현황과 실제 자료가 열립니다.');
    note2.setAttribute('data-reveal-item', '');
    w.appendChild(note2);

    var railwrap = el('div', 'mt-railwrap');
    railwrap.setAttribute('data-reveal-item', '');
    var rail = el('div', 'mt-rail');
    collections.forEach(function (col) {
      var card = el('button', 'mt-school-card');
      card.type = 'button';
      var thumb = firstSchoolThumb(col);
      if (thumb) {
        var im = document.createElement('img');
        im.className = 'mt-thumb'; im.src = thumb; im.alt = ''; im.loading = 'lazy';
        card.appendChild(im);
      } else {
        card.appendChild(el('span', 'mt-thumb ph', '자료 준비 중'));
      }
      var cb = el('div', 'mt-card-body');
      cb.appendChild(el('span', 'mt-school-name', schoolTitle(col)));
      if (col.statusLabel) cb.appendChild(el('span', 'mt-status', col.statusLabel));
      var meta = el('span', 'mt-card-meta');
      meta.appendChild(document.createTextNode('자료 '));
      meta.appendChild(el('strong', null, String(col.materialCount || 0)));
      if (col.lastUpdate) meta.appendChild(document.createTextNode('  ·  최근 ' + shortDate(col.lastUpdate)));
      cb.appendChild(meta);
      card.appendChild(cb);
      card.addEventListener('click', function () { location.hash = '#school-' + col.id; });
      rail.appendChild(card);
    });
    railwrap.appendChild(rail);
    railwrap.appendChild(railNav(rail));
    w.appendChild(railwrap);
    s.appendChild(w);
    return s;
  }

  function renderMaterialShowcase(materials) {
    var s = el('section', 'mt-block mt-block--soft');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var h2 = el('h2', 'mt-h2', '이번 주에 올라온 자료');
    h2.setAttribute('data-reveal-item', '');
    w.appendChild(h2);
    var railwrap = el('div', 'mt-railwrap');
    railwrap.setAttribute('data-reveal-item', '');
    var rail = el('div', 'mt-rail mt-rail--docs');
    materials.forEach(function (mat) { rail.appendChild(docCard(mat)); });
    railwrap.appendChild(rail);
    railwrap.appendChild(railNav(rail));
    w.appendChild(railwrap);
    s.appendChild(w);
    return s;
  }

  function docCard(mat) {
    var card = el('button', 'mt-doc');
    card.type = 'button';
    var sheet = el('div', 'mt-doc-sheet' + (mat.thumbnailUrl ? '' : ' ph'), mat.thumbnailUrl ? null : '📄');
    if (mat.thumbnailUrl) {
      var im = document.createElement('img');
      im.src = mat.thumbnailUrl; im.alt = ''; im.loading = 'lazy';
      sheet.appendChild(im);
    }
    if (isLocked(mat.access)) sheet.appendChild(el('span', 'mt-lockchip', lockText(mat.access)));
    card.appendChild(sheet);
    var cap = el('div', 'mt-doc-cap');
    cap.appendChild(el('span', 'mt-cat', catLabel(mat.category)));
    cap.appendChild(el('span', 'mt-doc-title', mat.displayTitle || ''));
    var sub = firstSentence(mat.blurb, 40);
    if (sub) cap.appendChild(el('span', 'mt-doc-sub', sub));
    card.appendChild(cap);
    card.addEventListener('click', function () { openSheet(mat); });
    return card;
  }

  function renderBriefingMoment(col) {
    var b = col.briefing;
    var s = el('section', 'mt-block mt-briefing');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var frame = el('div', 'mt-video');
    frame.setAttribute('data-reveal-item', '');
    var v = document.createElement('video');
    v.preload = 'none'; v.playsInline = true; v.controls = false; v.setAttribute('playsinline', '');
    if (b.thumbnailUrl) v.poster = b.thumbnailUrl;
    var src = document.createElement('source');
    src.src = b.videoUrl; src.type = 'video/mp4';
    v.appendChild(src);
    frame.appendChild(v);
    var play = el('button', 'mt-video-play');
    play.type = 'button';
    play.setAttribute('aria-label', schoolTitle(col) + ' 브리핑 영상 재생');
    play.appendChild(el('span', null, '▶'));
    play.addEventListener('click', function () {
      v.controls = true; play.remove();
      var r = v.play();
      if (r && r.catch) r.catch(function () { v.controls = true; });
    });
    frame.appendChild(play);
    w.appendChild(frame);

    var cap = el('div', 'mt-briefing-cap');
    cap.setAttribute('data-reveal-item', '');
    cap.appendChild(el('b', null, b.title || (schoolTitle(col) + ' 준비 브리핑')));
    var line = schoolTitle(col);
    if (b.shootDate) line += ' · 촬영 ' + shortDate(b.shootDate);
    cap.appendChild(el('span', null, line));
    if (b.description) cap.appendChild(el('span', null, firstSentence(b.description, 80)));
    w.appendChild(cap);
    s.appendChild(w);
    return s;
  }

  function renderUpdates(logs) {
    var s = el('section', 'mt-block');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var h2 = el('h2', 'mt-h2', '업데이트');
    h2.setAttribute('data-reveal-item', '');
    w.appendChild(h2);
    var ul = el('ul', 'mt-log');
    ul.setAttribute('data-reveal-item', '');
    logs.slice(0, 12).forEach(function (lg) {
      var li = el('li');
      var t = el('time', null, humanDate(lg.date));
      if (lg.date) t.dateTime = lg.date;
      li.appendChild(t);
      var box = el('div');
      box.appendChild(el('div', 'mt-log-h', lg.headline || ''));
      if (lg.body) box.appendChild(el('div', 'mt-log-b', lg.body));
      li.appendChild(box);
      ul.appendChild(li);
    });
    w.appendChild(ul);
    s.appendChild(w);
    return s;
  }

  function renderNextExam(ne) {
    var s = el('section', 'mt-block mt-next');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var h2 = el('h2', 'mt-h2', ne.nextExamName || ne.headline || '다음 시험');
    h2.setAttribute('data-reveal-item', '');
    w.appendChild(h2);
    if (ne.headline && ne.nextExamName) {
      var hp = el('p', null, ne.headline); hp.setAttribute('data-reveal-item', ''); w.appendChild(hp);
    }
    if (ne.description) {
      var dp = el('p', null, ne.description); dp.setAttribute('data-reveal-item', ''); w.appendChild(dp);
    }
    var a = el('a', null, ne.ctaLabel || '기말고사 상담');
    a.href = ne.ctaDestination || CONSULT_URL; a.rel = 'noopener';
    a.setAttribute('data-reveal-item', '');
    w.appendChild(a);
    s.appendChild(w);
    return s;
  }

  function renderConsult(c) {
    var s = el('section', 'mt-block mt-block--tight mt-consult');
    var w = el('div', 'mt-read');
    w.setAttribute('data-reveal', '');
    var h2 = el('h2', 'mt-h2', '우리 아이 학교로 상담하기');
    h2.setAttribute('data-reveal-item', '');
    w.appendChild(h2);
    var p = el('p', null, '학교와 학년을 알려주시면 현재 준비 현황을 함께 봅니다.');
    p.setAttribute('data-reveal-item', '');
    w.appendChild(p);
    var a = el('a', null, '상담 문의');
    a.href = CONSULT_URL; a.rel = 'noopener';
    a.setAttribute('data-reveal-item', '');
    w.appendChild(a);
    s.appendChild(w);
    return s;
  }

  function renderFooter(c) {
    var f = el('footer', 'mt-footer');
    f.textContent = '압구정에듀홀릭 · ' + c.examYear + ' ' + c.semester + ' ' + c.examType + ' 내신 LIVE';
    return f;
  }

  // ── 횡스크롤 화살표 (데스크톱) — 자동 이동 없음, scrollBy 만 ────────────
  function railNav(rail) {
    var nav = el('div', 'mt-rail-nav');
    var prev = el('button', null, '‹'); prev.type = 'button'; prev.setAttribute('aria-label', '이전');
    var next = el('button', null, '›'); next.type = 'button'; next.setAttribute('aria-label', '다음');
    function reduce() {
      return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    }
    function step(dir) {
      var by = Math.max(240, rail.clientWidth * 0.8) * dir;
      rail.scrollBy({ left: by, behavior: reduce() ? 'auto' : 'smooth' });
    }
    function sync() {
      prev.disabled = rail.scrollLeft <= 2;
      next.disabled = rail.scrollLeft + rail.clientWidth >= rail.scrollWidth - 2;
    }
    prev.addEventListener('click', function () { step(-1); });
    next.addEventListener('click', function () { step(1); });
    rail.addEventListener('scroll', sync, { passive: true });
    setTimeout(sync, 0);
    nav.appendChild(prev); nav.appendChild(next);
    return nav;
  }

  // ── sticky 하단 CTA — 실제 <a href="#mt-collections"> ──────────────────
  function renderSticky() {
    var d = el('div', 'mt-sticky');
    var label = (state.campaign && state.campaign.hero && state.campaign.hero.ctaLabel)
      ? state.campaign.hero.ctaLabel : '우리 학교 보기';
    var a = el('a', null, label);
    a.href = '#mt-collections';
    a.setAttribute('aria-label', label);
    a.addEventListener('click', function (e) { e.preventDefault(); goToCollections(); });
    a.addEventListener('keydown', function (e) {
      if (e.key === ' ' || e.key === 'Spacebar' || e.key === 'Enter') { e.preventDefault(); goToCollections(); }
    });
    d.appendChild(a);
    return d;
  }

  /**
   * 학교 섹션(id="mt-collections")으로 이동한다. 0개여도 안내 섹션이 존재하므로 목적지가 항상 있다.
   * 상세(#school-*) 화면이면 먼저 홈으로 돌아간 뒤(route() 태스크 이후) 스크롤한다.
   */
  function goToCollections() {
    var wasDetail = (location.hash || '').indexOf('#school-') === 0;
    if (location.hash) { location.hash = ''; }
    var run = function () {
      var target = document.getElementById('mt-collections')
        || (root && root.querySelector('.mt-home'));
      if (target && target.scrollIntoView) {
        var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        target.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
      }
      var h = target && target.querySelector('h2');
      if (h) {
        h.setAttribute('tabindex', '-1');
        try { h.focus({ preventScroll: true }); } catch (_) { h.focus(); }
      }
    };
    if (wasDetail) { setTimeout(run, 0); } else { run(); }
  }

  // ── 라우팅: 학교 상세 ─────────────────────────────────────────────────
  function route() {
    var m = /^#school-(\d+)$/.exec(location.hash || '');
    var home = root.querySelector('.mt-home');
    var detail = document.getElementById('mtDetail');
    if (!home || !detail) return;
    if (!m) {
      home.classList.remove('hidden');
      detail.classList.remove('active');
      detail.innerHTML = '';
      return;
    }
    var id = Number(m[1]);
    var col = (state.campaign.schoolCollections || []).filter(function (x) { return x.id === id; })[0];
    if (!col) { location.hash = ''; return; }
    home.classList.add('hidden');
    detail.classList.add('active');
    renderDetail(detail, col);
    window.scrollTo(0, 0);
    wireReveal(detail);
  }

  function renderDetail(container, col) {
    container.innerHTML = '';
    var s = el('section', 'mt-block');
    var w = el('div', 'mt-wrap');

    var back = el('button', 'mt-back', '← 전체 학교');
    back.type = 'button';
    back.addEventListener('click', function () { location.hash = ''; });
    w.appendChild(back);

    var head = el('div', 'mt-detail-head');
    head.setAttribute('data-reveal', '');
    var ex = el('span', 'mt-exam', col.examTitle || (state.campaign.examYear + ' ' + state.campaign.examType));
    ex.setAttribute('data-reveal-item', '');
    head.appendChild(ex);
    var h1 = el('h1', null, schoolTitle(col));
    h1.setAttribute('data-reveal-item', '');
    head.appendChild(h1);
    if (col.prepHeadline || col.statusLabel) {
      var lead = el('p', 'mt-lead', col.prepHeadline || col.statusLabel);
      lead.setAttribute('data-reveal-item', '');
      head.appendChild(lead);
    }
    w.appendChild(head);

    if (col.prepStatuses && col.prepStatuses.length) {
      var ul = el('ul', 'mt-prep');
      col.prepStatuses.forEach(function (p) {
        var li = el('li');
        li.appendChild(el('span', null, p.label));
        li.appendChild(el('span', 'mt-badge ' + p.state, badgeText(p.state)));
        ul.appendChild(li);
      });
      w.appendChild(ul);
    }

    if (col.briefing) w.appendChild(detailBriefing(col));

    if (col.examFeatureNote) {
      var fs = el('div', 'mt-feature');
      fs.appendChild(el('h2', 'mt-h2', '이번 시험'));
      col.examFeatureNote.split(/\n+/).forEach(function (line) {
        if (line.trim()) fs.appendChild(el('p', null, line.trim()));
      });
      w.appendChild(fs);
    }

    if (col.materials && col.materials.length) {
      var ms = el('div', 'mt-feature');
      ms.appendChild(el('h2', 'mt-h2', '준비한 자료'));
      var railwrap = el('div', 'mt-railwrap');
      var rail = el('div', 'mt-rail mt-rail--docs');
      col.materials.forEach(function (mat) { rail.appendChild(docCard(mat)); });
      railwrap.appendChild(rail);
      railwrap.appendChild(railNav(rail));
      ms.appendChild(railwrap);
      w.appendChild(ms);
    }

    if (col.updateLogs && col.updateLogs.length) {
      var ls = el('div', 'mt-feature');
      ls.appendChild(el('h2', 'mt-h2', '업데이트'));
      var lul = el('ul', 'mt-log');
      col.updateLogs.forEach(function (lg) {
        var li = el('li');
        var t = el('time', null, humanDate(lg.date));
        if (lg.date) t.dateTime = lg.date;
        li.appendChild(t);
        var box = el('div');
        box.appendChild(el('div', 'mt-log-h', lg.headline || ''));
        if (lg.body) box.appendChild(el('div', 'mt-log-b', lg.body));
        li.appendChild(box);
        lul.appendChild(li);
      });
      ls.appendChild(lul);
      w.appendChild(ls);
    }

    s.appendChild(w);
    container.appendChild(s);
  }

  function detailBriefing(col) {
    var b = col.briefing;
    var box = el('div', 'mt-feature');
    box.appendChild(el('h2', 'mt-h2', '원장 브리핑'));
    if (b.videoUrl) {
      var frame = el('div', 'mt-video');
      var v = document.createElement('video');
      v.preload = 'none'; v.playsInline = true; v.controls = false; v.setAttribute('playsinline', '');
      if (b.thumbnailUrl) v.poster = b.thumbnailUrl;
      var src = document.createElement('source');
      src.src = b.videoUrl; src.type = 'video/mp4';
      v.appendChild(src);
      frame.appendChild(v);
      var play = el('button', 'mt-video-play');
      play.type = 'button';
      play.setAttribute('aria-label', '브리핑 영상 재생');
      play.appendChild(el('span', null, '▶'));
      play.addEventListener('click', function () {
        v.controls = true; play.remove(); var r = v.play(); if (r && r.catch) r.catch(function () {});
      });
      frame.appendChild(play);
      box.appendChild(frame);
    } else if (b.thumbnailUrl) {
      var img = document.createElement('img');
      img.src = b.thumbnailUrl; img.loading = 'lazy'; img.alt = '';
      img.style.borderRadius = 'var(--device-radius)';
      box.appendChild(img);
    }
    var cap = el('div', 'mt-briefing-cap');
    cap.style.marginTop = '12px';
    if (b.title) cap.appendChild(el('b', null, b.title));
    if (b.description) cap.appendChild(el('span', null, b.description));
    if (b.shootDate) cap.appendChild(el('span', null, '촬영 ' + shortDate(b.shootDate)));
    box.appendChild(cap);
    return box;
  }

  function badgeText(st) {
    return st === 'DONE' ? '완료' : st === 'IN_PROGRESS' ? '진행 중' : '준비 예정';
  }

  // ── 자료 미리보기 시트 ────────────────────────────────────────────────
  var lastFocus = null;
  function openSheet(mat) {
    lastFocus = document.activeElement;
    var backdrop = el('div', 'mt-sheet-backdrop');
    var sheet = el('div', 'mt-sheet');
    sheet.setAttribute('role', 'dialog');
    sheet.setAttribute('aria-modal', 'true');
    sheet.setAttribute('aria-label', mat.displayTitle || '자료');

    var close = el('button', 'mt-sheet-close', '×');
    close.type = 'button';
    close.setAttribute('aria-label', '닫기');
    sheet.appendChild(close);

    sheet.appendChild(el('span', 'mt-cat', catLabel(mat.category)));
    sheet.appendChild(el('h3', null, mat.displayTitle || ''));
    if (mat.blurb) sheet.appendChild(el('p', null, mat.blurb));

    if (mat.principalComment) {
      var q = el('div', 'mt-comment');
      q.appendChild(el('b', null, '박정민 원장'));
      q.appendChild(document.createTextNode(mat.principalComment));
      sheet.appendChild(q);
    }
    if (mat.checklist && mat.checklist.length) {
      var ul = el('ul', 'mt-check');
      mat.checklist.forEach(function (item) { ul.appendChild(el('li', null, item)); });
      sheet.appendChild(ul);
    }
    if (mat.fileUrl) {
      var a = el('a', 'mt-file-btn', '실제 자료 열기');
      a.href = mat.fileUrl; a.target = '_blank'; a.rel = 'noopener';
      sheet.appendChild(a);
    } else if (mat.access === 'MEMBER_LOCKED') {
      sheet.appendChild(el('p', 'mt-file-note', '로그인 회원에게 전체 자료가 공개됩니다.'));
    } else if (mat.access === 'PREVIEW') {
      sheet.appendChild(el('p', 'mt-file-note', '전체 자료는 수업에서 배부됩니다. 여기서는 구성만 봅니다.'));
    }

    backdrop.appendChild(sheet);
    document.body.appendChild(backdrop);
    document.body.style.overflow = 'hidden';
    close.focus();

    function dismiss() {
      backdrop.remove();
      document.body.style.overflow = '';
      if (lastFocus && lastFocus.focus) lastFocus.focus();
      document.removeEventListener('keydown', onKey);
    }
    function onKey(e) {
      if (e.key === 'Escape') dismiss();
      if (e.key === 'Tab') {
        var f = sheet.querySelectorAll('button, a[href]');
        if (!f.length) return;
        var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    }
    close.addEventListener('click', dismiss);
    backdrop.addEventListener('click', function (e) { if (e.target === backdrop) dismiss(); });
    document.addEventListener('keydown', onKey);
  }

  // ── 스크롤 진입 1회 reveal (IntersectionObserver + 안전한 폴백, prefers-reduced-motion 존중) ──
  // 자동 캐러셀·타임라인 없음. reveal 은 되돌리지 않는다(지나간 섹션을 흐리게 하지 않는다).
  // IO 콜백이 유실되어도(짧은 페이지·앵커 진입·백그라운드 탭) 콘텐츠가 영영 숨지 않도록
  // scroll/resize 확인 + 지연 catch-all 을 둔다.
  function wireReveal(scope) {
    var groups = [].slice.call(scope.querySelectorAll('[data-reveal]:not([data-reveal-wired])'));
    if (!groups.length) return;
    groups.forEach(function (g) { g.setAttribute('data-reveal-wired', ''); });

    var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    function reveal(g) {
      if (g.classList.contains('is-revealed')) return;
      var items = g.querySelectorAll('[data-reveal-item]');
      for (var i = 0; i < items.length; i++) items[i].style.setProperty('--reveal-order', i);
      g.classList.add('is-revealed');
    }
    if (reduce || !('IntersectionObserver' in window)) {
      groups.forEach(reveal);
      return;
    }

    var pending = groups.slice();
    function drop(g) {
      var i = pending.indexOf(g);
      if (i >= 0) pending.splice(i, 1);
      if (!pending.length) window.removeEventListener('scroll', onScroll);
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { reveal(entry.target); io.unobserve(entry.target); drop(entry.target); }
      });
    }, { rootMargin: '0px 0px -10% 0px' });

    function sweep() {
      pending.slice().forEach(function (g) {
        var r = g.getBoundingClientRect();
        if (r.top < window.innerHeight * 0.92 && r.bottom > 0) { reveal(g); io.unobserve(g); drop(g); }
      });
    }
    function onScroll() { sweep(); }

    groups.forEach(function (g) { io.observe(g); });
    window.addEventListener('scroll', onScroll, { passive: true });
    sweep();
    requestAnimationFrame(sweep);
    setTimeout(sweep, 300);
    // catch-all: IO/scroll 이 모두 조용해도(짧은 페이지·백그라운드 탭) 남은 그룹을 곧 보여준다.
    setTimeout(function () { pending.slice().forEach(function (g) { reveal(g); io.unobserve(g); drop(g); }); }, 2500);
  }
})();
