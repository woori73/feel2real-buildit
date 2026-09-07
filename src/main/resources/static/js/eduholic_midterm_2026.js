/* 2026 2학기 내신 LIVE — 공개 페이지 렌더러.
 * 데이터: GET /api/academies/{handle}/midterm-campaign (미발행/부재 → 404 → 안내 폴백).
 * BuildIt Wiki(workspace)로 보내지 않는다 — 이 페이지가 프레젠테이션을 담당한다(§10·§11).
 */
(function () {
  'use strict';

  var root = document.getElementById('mtRoot');
  if (!root) return;
  var HANDLE = root.getAttribute('data-handle') || 'eduholic';
  var API = '/api/academies/' + encodeURIComponent(HANDLE) + '/midterm-campaign';
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

  // ── helpers ─────────────────────────────────────────────────────────
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function wrap(child) {
    var w = el('div', 'mt-wrap');
    if (child) w.appendChild(child);
    return w;
  }
  function fmtDate(iso) {
    if (!iso) return '';
    var p = String(iso).split('-');
    if (p.length < 3) return iso;
    return p[1] + '.' + p[2];
  }
  function gradeLabel(g) { return g ? g + '학년' : ''; }

  function renderFallback() {
    root.innerHTML = '';
    var s = el('section', 'mt-hero');
    var w = el('div', 'mt-wrap');
    w.appendChild(el('p', 'mt-eyebrow', 'APGUJEONG EDUHOLIC'));
    var h = el('h1', 'mt-serif');
    h.textContent = '내신 LIVE 준비 중입니다.';
    w.appendChild(h);
    w.appendChild(el('p', null, '학교별 중간고사 준비 현황을 곧 공개합니다. 상담은 아래에서 이어집니다.'));
    var a = el('a', null, '상담 문의 →');
    a.href = CONSULT_URL;
    a.style.cssText = 'display:inline-block;margin-top:12px;font-weight:800;color:var(--accent)';
    w.appendChild(a);
    s.appendChild(w);
    root.appendChild(s);
  }

  // ── main render ─────────────────────────────────────────────────────
  function render() {
    var c = state.campaign;
    root.innerHTML = '';
    root.classList.add('has-sticky');

    var home = el('div', 'mt-home');
    home.appendChild(renderHero(c));
    // 학교 컬렉션 섹션은 항상 렌더한다(id="mt-collections"). 0개여도 안내를 보여줘야
    // sticky CTA "학교별 준비 현황 보기"가 죽은 버튼이 되지 않는다.
    home.appendChild(renderCollections(c.schoolCollections || []));
    home.appendChild(renderHowWePrepare());
    if (c.campaignUpdateLogs && c.campaignUpdateLogs.length) {
      home.appendChild(renderLogSection('MIDTERM LOG', '시험기간 동안 자료가 계속 업데이트됩니다.', c.campaignUpdateLogs));
    }
    if (c.nextExam) home.appendChild(renderNextExam(c.nextExam));
    root.appendChild(home);

    var detail = el('div', 'mt-detail');
    detail.id = 'mtDetail';
    root.appendChild(detail);

    root.appendChild(renderFooter(c));
    root.appendChild(renderSticky());
  }

  function renderHero(c) {
    var s = el('section', 'mt-hero');
    var w = el('div', 'mt-wrap');
    w.appendChild(el('p', 'mt-eyebrow', c.hero && c.hero.eyebrow ? c.hero.eyebrow
      : (c.examYear + ' ' + c.semester + ' ' + c.examType + ' · APGUJEONG EDUHOLIC')));
    var h1 = el('h1', 'mt-serif');
    h1.textContent = c.hero && c.hero.title ? c.hero.title : '학교가 다르면, 시험 준비도 달라야 합니다.';
    w.appendChild(h1);
    w.appendChild(el('p', null, c.hero && c.hero.body ? c.hero.body
      : '학교 프린트부터 시험 직전 변형문제까지. 지금 실제로 진행 중인 에듀홀릭의 중간고사 준비를 공개합니다.'));

    if (c.liveChips && c.liveChips.length) {
      var row = el('div', 'mt-live-row');
      c.liveChips.slice(0, 6).forEach(function (chip) {
        var ch = el('span', 'mt-live-chip');
        ch.appendChild(el('i', 'mt-live-dot'));
        ch.appendChild(document.createTextNode(chip.label + ' '));
        if (chip.updatedOn) {
          var t = el('time', null, 'UPDATE ' + fmtDate(chip.updatedOn));
          t.dateTime = chip.updatedOn;
          ch.appendChild(t);
        }
        row.appendChild(ch);
      });
      w.appendChild(row);
    }
    s.appendChild(w);
    return s;
  }

  function renderCollections(collections) {
    var s = el('section', 'mt-section');
    s.id = 'mt-collections';
    var w = el('div', 'mt-wrap');
    w.appendChild(el('p', 'mt-kicker', 'Midterm Collections'));
    w.appendChild(el('h2', null, '학교별 준비 현황'));

    if (!collections.length) {
      w.appendChild(el('p', 'mt-lead',
        '학교별 준비 현황이 아직 등록되지 않았습니다. 준비가 시작되면 이곳에서 학교·학년별로 확인할 수 있어요.'));
      var box = el('div', 'mt-collections-empty');
      box.appendChild(el('p', null, '아래 「HOW WE PREPARE」에서 에듀홀릭이 시험을 준비하는 방식을 먼저 볼 수 있습니다.'));
      var ca = el('a', 'mt-empty-cta', '상담 문의 →');
      ca.href = CONSULT_URL;
      ca.rel = 'noopener';
      box.appendChild(ca);
      w.appendChild(box);
      s.appendChild(w);
      return s;
    }

    w.appendChild(el('p', 'mt-lead', '학교와 학년을 고르면 준비 현황과 실제 자료가 열립니다.'));
    var scroller = el('div', 'mt-collections');
    collections.forEach(function (col) {
      var card = el('button', 'mt-collection-card');
      card.type = 'button';
      card.appendChild(el('span', 'mt-exam-title', col.examTitle || (state.campaign.examYear + ' ' + state.campaign.examType)));
      card.appendChild(el('span', 'mt-school', col.schoolName + ' ' + gradeLabel(col.grade)));
      if (col.statusLabel) card.appendChild(el('span', 'mt-meta', col.statusLabel));
      var meta = el('span', 'mt-meta');
      meta.appendChild(document.createTextNode('자료 '));
      meta.appendChild(el('strong', null, String(col.materialCount || 0)));
      if (col.lastUpdate) meta.appendChild(document.createTextNode('  ·  최근 ' + fmtDate(col.lastUpdate)));
      card.appendChild(meta);
      card.appendChild(el('span', 'mt-view', 'VIEW COLLECTION →'));
      card.addEventListener('click', function () { location.hash = '#school-' + col.id; });
      scroller.appendChild(card);
    });
    w.appendChild(scroller);
    s.appendChild(w);
    return s;
  }

  function renderHowWePrepare() {
    var s = el('section', 'mt-section');
    var w = el('div', 'mt-wrap');
    w.appendChild(el('p', 'mt-kicker', 'How We Prepare'));
    w.appendChild(el('h2', null, '더 많이 푸는 것이 아니라, 틀릴 핵심을 여러 형태로 다시 만납니다.'));
    var steps = [
      ['SOURCE', '학교 프린트·교과서·시험범위에서 시작합니다.'],
      ['ANALYZE', '학교·학년별 출제 포인트를 분석합니다.'],
      ['BUILD', '학생이 실제로 공부할 학습자료를 제작합니다.'],
      ['VARIATION', '같은 내용을 여러 문제 형태로 반복합니다.'],
      ['CLASSROOM', '수업에서 오답과 반응을 확인합니다.'],
      ['FINAL', '시험 직전 필요한 것만 다시 압축합니다.']
    ];
    var ul = el('ul', 'mt-steps');
    steps.forEach(function (st) {
      var li = el('li');
      li.appendChild(el('b', null, st[0]));
      li.appendChild(el('span', null, st[1]));
      ul.appendChild(li);
    });
    w.appendChild(ul);
    s.appendChild(w);
    return s;
  }

  function renderLogSection(kicker, lead, logs) {
    var s = el('section', 'mt-section');
    var w = el('div', 'mt-wrap');
    w.appendChild(el('p', 'mt-kicker', kicker));
    w.appendChild(el('p', 'mt-lead', lead));
    var ul = el('ul', 'mt-log');
    logs.forEach(function (lg) {
      var li = el('li');
      var t = el('time', null, fmtDate(lg.date));
      if (lg.date) t.dateTime = lg.date;
      li.appendChild(t);
      var box = el('div');
      box.appendChild(el('div', 'mt-log-h', lg.headline));
      if (lg.body) box.appendChild(el('div', 'mt-log-b', lg.body));
      li.appendChild(box);
      ul.appendChild(li);
    });
    w.appendChild(ul);
    s.appendChild(w);
    return s;
  }

  function renderNextExam(ne) {
    var s = el('section', 'mt-section');
    var box = el('div', 'mt-next');
    box.appendChild(el('p', 'mt-kicker', 'Next'));
    box.appendChild(el('h2', null, ne.nextExamName || ne.headline || '다음 시험'));
    if (ne.headline && ne.nextExamName) box.appendChild(el('p', null, ne.headline));
    if (ne.description) box.appendChild(el('p', null, ne.description));
    var a = el('a', null, (ne.ctaLabel || '기말고사 준비 상담') + ' →');
    a.href = ne.ctaDestination || CONSULT_URL;
    a.rel = 'noopener';
    box.appendChild(a);
    s.appendChild(wrapNode(box));
    return s;
  }
  function wrapNode(node) { var w = el('div', 'mt-wrap'); w.appendChild(node); return w; }

  function renderFooter(c) {
    var f = el('footer', 'mt-footer');
    f.textContent = '압구정에듀홀릭 · ' + c.examYear + ' ' + c.semester + ' ' + c.examType + ' 내신 LIVE';
    return f;
  }

  function renderSticky() {
    var d = el('div', 'mt-sticky');
    var w = el('div', 'mt-wrap');
    var label = (state.campaign.hero && state.campaign.hero.ctaLabel)
      ? state.campaign.hero.ctaLabel : '학교별 준비 현황 보기';
    // 실제 <a href="#mt-collections"> — JS 없이도 앵커 스크롤이 되고 Enter 는 기본 동작.
    var a = el('a', null, label);
    a.href = '#mt-collections';
    a.setAttribute('aria-label', label);
    a.addEventListener('click', function (e) {
      e.preventDefault();
      goToCollections();
    });
    a.addEventListener('keydown', function (e) {
      // 링크는 Space 로 활성화되지 않으므로 직접 처리(Enter 는 브라우저 기본).
      if (e.key === ' ' || e.key === 'Spacebar' || e.key === 'Enter') {
        e.preventDefault();
        goToCollections();
      }
    });
    w.appendChild(a);
    d.appendChild(w);
    return d;
  }

  /**
   * 학교별 준비 현황 섹션으로 이동한다. 학교 컬렉션이 0개여도 섹션(안내 문구)이 존재하므로
   * 항상 목적지가 있다. 상세(#school-*) 화면이면 먼저 홈으로 돌아간 뒤 스크롤한다.
   */
  function goToCollections() {
    var wasDetail = (location.hash || '').indexOf('#school-') === 0;
    if (location.hash) { location.hash = ''; } // hashchange → route() 가 홈을 다시 보여준다

    var run = function () {
      var target = document.getElementById('mt-collections')
        || (root && root.querySelector('.mt-home'));
      if (target && target.scrollIntoView) {
        var reduce = window.matchMedia
          && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        target.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
      }
      // 스크린리더 사용자가 목적지(안내 포함)에서 컨텍스트를 잃지 않도록 제목에 포커스.
      var h = target && target.querySelector('h2');
      if (h) {
        h.setAttribute('tabindex', '-1');
        try { h.focus({ preventScroll: true }); } catch (_) { h.focus(); }
      }
    };
    // 상세 화면이었으면 route()(hashchange 태스크)가 홈을 다시 보인 뒤 스크롤해야 한다.
    if (wasDetail) { setTimeout(run, 0); } else { run(); }
  }

  // ── routing: collection detail ─────────────────────────────────────
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
  }

  function renderDetail(container, col) {
    container.innerHTML = '';
    var w = el('div', 'mt-wrap');

    var back = el('button', 'mt-back', '← 전체 학교');
    back.type = 'button';
    back.addEventListener('click', function () { location.hash = ''; });
    w.appendChild(back);

    var head = el('div', 'mt-detail-head');
    head.appendChild(el('span', 'mt-exam-title', col.examTitle || (state.campaign.examYear + ' ' + state.campaign.examType)));
    head.appendChild(el('h1', 'mt-serif', col.schoolName + ' ' + gradeLabel(col.grade)));
    if (col.statusLabel) head.appendChild(el('p', 'mt-lead', col.statusLabel));
    w.appendChild(head);

    // 준비 상태
    if (col.prepStatuses && col.prepStatuses.length) {
      w.appendChild(el('p', 'mt-kicker', 'Now Preparing'));
      if (col.prepHeadline) w.appendChild(el('p', 'mt-lead', col.prepHeadline));
      var ul = el('ul', 'mt-status-list');
      col.prepStatuses.forEach(function (p) {
        var li = el('li');
        li.appendChild(el('span', null, p.label));
        li.appendChild(el('span', 'mt-badge ' + p.state, badgeText(p.state)));
        ul.appendChild(li);
      });
      w.appendChild(ul);
    }

    // 원장 브리핑
    if (col.briefing) w.appendChild(renderBriefing(col.briefing));

    // 이번 시험 특징
    if (col.examFeatureNote) {
      var fs = el('div');
      fs.style.marginTop = '26px';
      fs.appendChild(el('p', 'mt-kicker', '이번 시험 특징'));
      col.examFeatureNote.split(/\n+/).forEach(function (line) {
        if (line.trim()) fs.appendChild(el('p', 'mt-lead', line.trim()));
      });
      w.appendChild(fs);
    }

    // 자료 Showcase
    if (col.materials && col.materials.length) {
      var ms = el('div');
      ms.style.marginTop = '26px';
      ms.appendChild(el('p', 'mt-kicker', "This Week's Materials"));
      var grid = el('div', 'mt-materials');
      col.materials.forEach(function (mat) { grid.appendChild(materialCard(mat)); });
      ms.appendChild(grid);
      w.appendChild(ms);
    }

    // 업데이트 로그
    if (col.updateLogs && col.updateLogs.length) {
      var ls = el('div');
      ls.style.marginTop = '26px';
      ls.appendChild(el('p', 'mt-kicker', 'Recent Update'));
      var lul = el('ul', 'mt-log');
      col.updateLogs.forEach(function (lg) {
        var li = el('li');
        var t = el('time', null, fmtDate(lg.date));
        if (lg.date) t.dateTime = lg.date;
        li.appendChild(t);
        var box = el('div');
        box.appendChild(el('div', 'mt-log-h', lg.headline));
        if (lg.body) box.appendChild(el('div', 'mt-log-b', lg.body));
        li.appendChild(box);
        lul.appendChild(li);
      });
      ls.appendChild(lul);
      w.appendChild(ls);
    }

    var s = el('section', 'mt-section');
    s.appendChild(w);
    container.appendChild(s);
  }

  function badgeText(state) {
    return state === 'DONE' ? '완료' : state === 'IN_PROGRESS' ? '진행 중' : '준비 예정';
  }

  function renderBriefing(b) {
    var box = el('div', 'mt-briefing');
    box.appendChild(el('p', 'mt-kicker', '박정민 원장 LIVE Briefing'));
    if (b.videoUrl) {
      var frame = el('div', 'mt-video-frame');
      var v = document.createElement('video');
      v.preload = 'none';
      v.playsInline = true;
      v.controls = false;
      if (b.thumbnailUrl) v.poster = b.thumbnailUrl;
      var src = document.createElement('source');
      src.src = b.videoUrl;
      src.type = 'video/mp4';
      v.appendChild(src);
      frame.appendChild(v);
      var play = el('button', 'mt-video-play');
      play.type = 'button';
      play.setAttribute('aria-label', '브리핑 영상 재생');
      play.appendChild(el('span', null, '▶'));
      play.addEventListener('click', function () {
        v.controls = true;
        play.remove();
        v.play();
      });
      frame.appendChild(play);
      box.appendChild(frame);
    } else if (b.thumbnailUrl) {
      var img = document.createElement('img');
      img.src = b.thumbnailUrl;
      img.loading = 'lazy';
      img.alt = '';
      box.appendChild(img);
    }
    if (b.title) box.appendChild(el('h3', null, b.title));
    if (b.description) box.appendChild(el('p', null, b.description));
    if (b.shootDate) box.appendChild(el('p', 'mt-shoot', '촬영 ' + fmtDate(b.shootDate)));
    return box;
  }

  function materialCard(mat) {
    var card = el('button', 'mt-material-card');
    card.type = 'button';
    if (mat.thumbnailUrl) {
      var img = document.createElement('img');
      img.className = 'mt-thumb';
      img.src = mat.thumbnailUrl;
      img.loading = 'lazy';
      img.alt = '';
      card.appendChild(img);
    } else {
      var ph = el('span', 'mt-thumb ph', '📄');
      card.appendChild(ph);
    }
    var body = el('div');
    body.appendChild(el('span', 'mt-cat', catLabel(mat.category)));
    body.appendChild(el('h4', null, mat.displayTitle));
    if (mat.blurb) body.appendChild(el('p', null, mat.blurb));
    if (mat.access === 'MEMBER_LOCKED') body.appendChild(el('span', 'mt-lock', '🔒 로그인 회원 자료'));
    else if (mat.access === 'PREVIEW') body.appendChild(el('span', 'mt-lock', '미리보기'));
    card.appendChild(body);
    card.addEventListener('click', function () { openSheet(mat); });
    return card;
  }

  function catLabel(cat) {
    var map = {
      SCHOOL_PRINT: '학교 프린트 분석', ANALYSIS: '본문 분석지', WORKSHEET: '내신 학습지',
      VOCABULARY: '어휘', GRAMMAR: '어법', VARIANT: '변형문제', FINAL_REVIEW: '시험 직전자료'
    };
    return map[cat] || '학습자료';
  }

  // ── material preview sheet ─────────────────────────────────────────
  var lastFocus = null;
  function openSheet(mat) {
    lastFocus = document.activeElement;
    var backdrop = el('div', 'mt-sheet-backdrop');
    var sheet = el('div', 'mt-sheet');
    sheet.setAttribute('role', 'dialog');
    sheet.setAttribute('aria-modal', 'true');
    sheet.setAttribute('aria-label', mat.displayTitle);

    var close = el('button', 'mt-sheet-close', '×');
    close.type = 'button';
    close.setAttribute('aria-label', '닫기');
    sheet.appendChild(close);

    sheet.appendChild(el('span', 'mt-cat', catLabel(mat.category)));
    sheet.appendChild(el('h3', null, mat.displayTitle));
    if (mat.blurb) sheet.appendChild(el('p', null, mat.blurb));
    if (mat.principalComment) {
      var q = el('div', 'mt-comment');
      q.appendChild(el('div', null, '박정민 원장'));
      q.appendChild(el('div', null, '“' + mat.principalComment + '”'));
      sheet.appendChild(q);
    }
    if (mat.checklist && mat.checklist.length) {
      var ul = el('ul', 'mt-check');
      mat.checklist.forEach(function (item) { ul.appendChild(el('li', null, item)); });
      sheet.appendChild(ul);
    }
    if (mat.fileUrl) {
      var a = el('a', 'mt-file-btn', '실제 자료 보기');
      a.href = mat.fileUrl;
      a.target = '_blank';
      a.rel = 'noopener';
      sheet.appendChild(a);
    } else if (mat.access === 'MEMBER_LOCKED') {
      sheet.appendChild(el('p', 'mt-file-note', '로그인 회원에게 전체 자료가 공개됩니다.'));
    } else if (mat.access === 'PREVIEW') {
      sheet.appendChild(el('p', 'mt-file-note', '전체 자료는 수업에서 배부됩니다. 여기서는 구성만 확인합니다.'));
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
})();
