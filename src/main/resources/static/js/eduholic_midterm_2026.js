/* 2026 2학기 중간고사 내신 LIVE — 압구정에듀홀릭 공개 페이지 렌더러.
 *
 * 데이터: GET /api/academies/{handle}/midterm-campaign (미발행/부재 → 404 → 간결한 폴백).
 *         GET /api/academies/{handle}/midterm-campaign/wiki-sources
 *           → 발행된 학교 컬렉션 컨텍스트와 정확히 일치하는 실제 TUTOR Wiki 자료 목록(read-only).
 *             실패/부재 시 [] — 페이지는 계속 동작한다.
 * 이 페이지는 프레젠테이션만 담당한다 — 제작/관리 workspace 로 보내지 않는다.
 *
 * 역할 구분: Wiki = 자료 저장소 / Midterm Campaign = 이번 시험 큐레이션 / 이 페이지 = 공개 화면.
 * Wiki 원본을 직접 조회하지 않는다 — 서버가 안전한 공개 DTO(thumbnailUrl/fileUrl/access,
 * wiki-sources 는 title/school/grade/exam + previewUrl)로 내려준 것만 그린다(PR #220 보안 유지).
 *
 * 2026-09 (LIVE Experience 개편):
 *  - 첫 화면 = 왼쪽 "왜 이렇게 준비하는가"(20년+ 현장 경험 × 학교별 시험자료 × AI Wiki) +
 *    AI Wiki activity 패널(실제 wiki-sources / 학교 컬렉션 데이터로 학교별 분석 흐름을 순환).
 *  - 없는 학교/Lesson/문법/문장을 만들어내지 않는다. 데이터가 없으면 "학교 자료 확인 중" 정적 상태.
 *  - 자동 순환은 setTimeout 체인. prefers-reduced-motion → 첫 상태 고정. 화면 밖 → 타이머 정지.
 */
(function () {
  'use strict';

  document.documentElement.classList.add('mt-js');

  var root = document.getElementById('mtRoot');
  if (!root) return;
  var HANDLE = root.getAttribute('data-handle') || 'eduholic';
  var API = '/api/academies/' + encodeURIComponent(HANDLE) + '/midterm-campaign';
  var WIKI_SOURCES_API = API + '/wiki-sources';
  var HOME_URL = 'https://eduholic.feel2real.co.kr/';
  var CONSULT_URL = 'https://eduholic.feel2real.co.kr/';

  var state = { campaign: null, wikiSources: [] };

  function loadJson(url) {
    return fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (r) {
        if (r.status === 404) return null;
        if (!r.ok) throw new Error('load failed: ' + r.status);
        return r.json();
      });
  }

  Promise.all([
    loadJson(API),
    // wiki-sources 는 부가 데이터 — 어떤 실패도 페이지를 막지 않는다.
    loadJson(WIKI_SOURCES_API).catch(function () { return []; })
  ])
    .then(function (results) {
      var campaign = results[0];
      if (!campaign) { renderFallback(); return; }
      state.campaign = campaign;
      state.wikiSources = Array.isArray(results[1]) ? results[1] : [];
      render();
    })
    .catch(function () { renderFallback(); });

  // ── helpers ────────────────────────────────────────────────────────────
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  function firstSentence(s, cap) {
    if (!s) return '';
    var t = String(s).trim().split(/(?<=[.!?。])\s|\n/)[0].trim();
    if (cap && t.length > cap) t = t.slice(0, cap).trim() + '…';
    return t;
  }
  function clip(s, cap) {
    var t = String(s == null ? '' : s).trim();
    return cap && t.length > cap ? t.slice(0, cap).trim() + '…' : t;
  }
  function schoolTitle(col) {
    var g = col.grade ? col.grade + '학년' : '';
    return (col.schoolName || '').trim() + (g ? ' ' + g : '');
  }
  function dParts(iso) {
    var p = String(iso || '').split('-');
    return p.length >= 3 ? { m: parseInt(p[1], 10), d: parseInt(p[2], 10) } : null;
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
  function reduceMotion() {
    return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }

  function firstBriefingSchool(c) {
    return (c.schoolCollections || []).filter(function (s) {
      return s.briefing && s.briefing.videoUrl;
    })[0] || null;
  }

  // 공개 자료 목록 — 캠페인 자료 + 학교별 자료를 한 줄로 모은다. 학교별 자료는 학교 정보를
  // '자료에 붙는 작은 metadata'(예: "신구중 2학년 · 9.7 업데이트")로만 표시한다. 별도 학교 섹션 없음.
  function collectMaterials(c) {
    var out = [];
    (c.campaignMaterials || []).forEach(function (m) { out.push({ mat: m, meta: '', school: '' }); });
    (c.schoolCollections || []).forEach(function (col) {
      var tag = schoolTitle(col);
      if (col.lastUpdate) tag += ' · ' + shortDate(col.lastUpdate) + ' 업데이트';
      (col.materials || []).forEach(function (m) {
        out.push({ mat: m, meta: tag, school: (col.schoolName || '').trim() });
      });
    });
    return out;
  }

  // ── AI Wiki activity — 실제 데이터로만 학교별 분석 흐름(scene)을 만든다 ──────────
  // scene = 한 학교·학년의 (실제 wiki-source 제목 + 실제 학교 컬렉션 출제 특징/준비 단계).
  // 없는 학교/Lesson/문법/문장을 생성하지 않는다.
  function buildWikiScenes(c) {
    var order = [];
    var byKey = {};
    function ensure(school, grade, examLabel) {
      var key = school + '|' + grade;
      if (!byKey[key]) {
        byKey[key] = { school: school, grade: String(grade || ''), examLabel: examLabel || '',
          sources: [], note: '', prep: [] };
        order.push(key);
      }
      return byKey[key];
    }

    (state.wikiSources || []).forEach(function (s) {
      var school = (s.school || '').trim();
      if (!school) return;
      var examLabel = [s.year, s.semester, s.exam].filter(Boolean).join(' ');
      var sc = ensure(school, s.grade, examLabel);
      if (!sc.examLabel) sc.examLabel = examLabel;
      sc.sources.push({ label: (s.kindLabel || '자료').trim(), title: (s.title || '').trim() });
    });

    var campaignExam = [c.examYear, c.semester, c.examType].filter(Boolean).join(' ');
    (c.schoolCollections || []).forEach(function (col) {
      var school = (col.schoolName || '').trim();
      if (!school) return;
      var sc = ensure(school, col.grade, campaignExam);
      if (!sc.note) sc.note = (col.examFeatureNote || '').trim();
      var prep = (col.prepStatuses || []).map(function (p) { return (p.label || '').trim(); }).filter(Boolean);
      if (prep.length) sc.prep = prep;
      (col.materials || []).forEach(function (m) {
        sc.sources.push({ label: catLabel(m.category), title: (m.displayTitle || '').trim() });
      });
    });

    return order.map(function (k) { return byKey[k]; });
  }

  // scene → 진행 단계. 학교/학년/자료 제목/출제 특징/준비 단계 라벨은 전부 실제 데이터.
  // 데이터가 없는 연결부만 일반 문구("핵심 문장·어법 정리")를 쓴다.
  function sceneSteps(scene) {
    var head = scene.school + (scene.grade ? ' ' + scene.grade + '학년' : '');
    var steps = [{ k: 'scan', t: head + ' · 시험범위 Wiki 확인' }];
    var src = scene.sources.filter(function (s) { return s.label || s.title; })[0];
    if (src) {
      steps.push({ k: 'open', t: src.label + ' 열람' });
      if (src.title) steps.push({ k: 'read', t: clip(src.title, 42) });
    }
    if (scene.note) {
      steps.push({ k: 'note', t: '출제 특징 · ' + clip(scene.note, 40) });
    } else if (scene.prep.length) {
      scene.prep.slice(0, 2).forEach(function (p) { steps.push({ k: 'note', t: clip(p, 40) }); });
    } else {
      steps.push({ k: 'note', t: '핵심 문장·어법 정리' });
    }
    steps.push({ k: 'apply', t: '출제 포인트 수업 준비에 반영' });
    return steps;
  }

  function sceneSchoolNames(scenes) {
    var seen = {};
    var names = [];
    scenes.forEach(function (s) {
      if (s.school && !seen[s.school]) { seen[s.school] = 1; names.push(s.school); }
    });
    return names;
  }

  // ── 폴백 (미발행/부재) — 짧게 ──────────────────────────────────────────
  function renderFallback() {
    root.innerHTML = '';
    root.appendChild(renderTopbar());
    var s = el('section', 'mt-head');
    var w = el('div', 'mt-wrap');
    w.appendChild(el('h1', 'mt-head-title', '2학기 중간고사 준비를 곧 공개합니다.'));
    var a = el('a', 'mt-textlink', '상담 문의');
    a.href = CONSULT_URL; a.rel = 'noopener';
    w.appendChild(a);
    s.appendChild(w);
    root.appendChild(s);
  }

  // ── 렌더 ──────────────────────────────────────────────────────────────
  function render() {
    var c = state.campaign;
    var scenes = buildWikiScenes(c);
    root.innerHTML = '';
    root.appendChild(renderTopbar());

    var home = el('div', 'mt-home');
    home.appendChild(renderLiveOpening(c, scenes));

    var mats = collectMaterials(c);
    if (mats.length) home.appendChild(renderMaterialShowcase(mats));

    var bfSchool = firstBriefingSchool(c);
    if (bfSchool) home.appendChild(renderBriefingMoment(bfSchool));

    if (c.campaignUpdateLogs && c.campaignUpdateLogs.length) {
      home.appendChild(renderUpdates(c.campaignUpdateLogs));
    }
    if (c.nextExam) home.appendChild(renderNextExam(c.nextExam));
    if (mats.length) home.appendChild(renderConsult(c));
    root.appendChild(home);

    root.appendChild(renderFooter(c));
    wireReveal(root);
    wireWikiSimulation(root, scenes);
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

  // ── 첫 화면 — 왼쪽 "왜 이렇게 준비하는가" + 오른쪽 AI Wiki activity ─────────
  function renderLiveOpening(c, scenes) {
    var s = el('section', 'mt-block mt-lead');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var grid = el('div', 'mt-lead-grid');

    var why = el('div', 'mt-lead-why');
    why.setAttribute('data-reveal-item', '');
    why.appendChild(el('p', 'mt-lead-kicker', c.examYear + '년 ' + c.semester + ' ' + c.examType));

    var schools = sceneSchoolNames(scenes);
    var h1 = el('h1', 'mt-lead-title');
    h1.textContent = schools.length
      ? schools.join(' · ') + '\n시험 준비가 시작됐습니다'
      : ((c.hero && c.hero.title && c.hero.title.trim()) || '학교별 시험 준비가 시작됐습니다');
    why.appendChild(h1);

    var body = (c.hero && c.hero.body && c.hero.body.trim())
      || '20년 이상 현장을 지켜온 박정민 원장의 내신 경험과, 학교별 시험자료를 분석하는 AI Wiki 시스템을 함께 씁니다.';
    why.appendChild(el('p', 'mt-lead-body', body));

    var proof = el('div', 'mt-lead-proof');
    ['20년+ 현장 경험', '학교별 시험자료', 'AI Wiki'].forEach(function (t) {
      proof.appendChild(el('span', 'mt-lead-chip', t));
    });
    why.appendChild(proof);
    grid.appendChild(why);

    grid.appendChild(buildWikiPanel(scenes));
    w.appendChild(grid);
    s.appendChild(w);
    return s;
  }

  function buildWikiPanel(scenes) {
    var panel = el('div', 'mt-wiki');
    panel.setAttribute('data-reveal-item', '');
    panel.setAttribute('data-mt-wiki', '');

    var top = el('div', 'mt-wiki-top');
    var live = el('span', 'mt-wiki-live');
    live.appendChild(el('i', null, null));
    live.appendChild(document.createTextNode('AI Wiki'));
    top.appendChild(live);
    top.appendChild(el('span', 'mt-wiki-school', scenes.length ? '' : '학교 자료 확인 중'));
    panel.appendChild(top);

    if (!scenes.length) {
      panel.appendChild(el('p', 'mt-wiki-idle', '학교별 시험 자료를 확인하고 있습니다.'));
      return panel;
    }
    panel.appendChild(el('ol', 'mt-wiki-steps'));
    return panel;
  }

  // 순환 엔진 — setTimeout 체인(자동 캐러셀 라이브러리 없음). 화면 밖·reduced-motion 정지.
  function wireWikiSimulation(scope, scenes) {
    var panel = scope.querySelector('[data-mt-wiki]');
    if (!panel || !scenes.length) return;
    var schoolEl = panel.querySelector('.mt-wiki-school');
    var stepsEl = panel.querySelector('.mt-wiki-steps');
    if (!stepsEl) return;

    var si = 0;
    var timer = null;
    var running = false;

    function paintScene(scene, revealAll) {
      var steps = sceneSteps(scene);
      schoolEl.textContent = scene.school
        + (scene.grade ? ' ' + scene.grade + '학년' : '')
        + (scene.examLabel ? ' · ' + scene.examLabel : '');
      stepsEl.innerHTML = '';
      steps.forEach(function (st) {
        var li = el('li', 'mt-wiki-step' + (revealAll ? ' is-on' : ''));
        li.setAttribute('data-k', st.k);
        li.appendChild(el('span', 'mt-wiki-mark', null));
        li.appendChild(el('span', 'mt-wiki-text', st.t));
        stepsEl.appendChild(li);
      });
      return steps.length;
    }

    function focusDocs(school) {
      var cards = scope.querySelectorAll('[data-mt-school]');
      for (var i = 0; i < cards.length; i++) {
        cards[i].classList.toggle('is-focus', cards[i].getAttribute('data-mt-school') === school);
      }
    }

    function runScene() {
      var scene = scenes[si];
      var total = paintScene(scene, false);
      focusDocs(scene.school);
      var lis = stepsEl.querySelectorAll('.mt-wiki-step');
      var k = 0;
      function tick() {
        if (!running) return;
        if (k > 0 && lis[k - 1]) { lis[k - 1].classList.remove('is-on'); lis[k - 1].classList.add('is-done'); }
        if (k < total) {
          if (lis[k]) lis[k].classList.add('is-on');
          k++;
          timer = setTimeout(tick, 1300);
        } else {
          timer = setTimeout(function () {
            si = (si + 1) % scenes.length;
            runScene();
          }, 1700);
        }
      }
      tick();
    }

    function start() {
      if (running) return;
      running = true;
      runScene();
    }
    function stop() {
      running = false;
      if (timer) { clearTimeout(timer); timer = null; }
    }

    if (reduceMotion() || scenes.length === 1 && sceneSteps(scenes[0]).length <= 1) {
      paintScene(scenes[0], true);
      focusDocs(scenes[0].school);
      return;
    }

    if ('IntersectionObserver' in window) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) {
          if (e.isIntersecting) start(); else stop();
        });
      }, { threshold: 0.15 });
      io.observe(panel);
    } else {
      start();
    }
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) stop();
      else if (isInView(panel)) start();
    });
  }

  function isInView(node) {
    var r = node.getBoundingClientRect();
    return r.bottom > 0 && r.top < (window.innerHeight || 0);
  }

  function renderMaterialShowcase(items) {
    var s = el('section', 'mt-block mt-block--soft');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var h2 = el('h2', 'mt-h2', '이번 주에 올라온 자료');
    h2.setAttribute('data-reveal-item', '');
    w.appendChild(h2);
    var railwrap = el('div', 'mt-railwrap');
    railwrap.setAttribute('data-reveal-item', '');
    var rail = el('div', 'mt-rail mt-rail--docs');
    items.forEach(function (it) { rail.appendChild(docCard(it.mat, it.meta, it.school)); });
    railwrap.appendChild(rail);
    railwrap.appendChild(railNav(rail));
    w.appendChild(railwrap);
    s.appendChild(w);
    return s;
  }

  function docCard(mat, meta, school) {
    var card = el('button', 'mt-doc');
    card.type = 'button';
    if (school) card.setAttribute('data-mt-school', school);
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
    if (meta) cap.appendChild(el('span', 'mt-doc-meta', meta));
    var sub = firstSentence(mat.blurb, 40);
    if (sub) cap.appendChild(el('span', 'mt-doc-sub', sub));
    card.appendChild(cap);
    card.addEventListener('click', function () { openSheet(mat); });
    return card;
  }

  // 영상 프레임 — 실제 영상 비율에 맞춘다(억지 crop 없음). metadata 로드/poster 자연 크기로
  // --mt-video-ar 을 채우고 [0.7, 1.9] 로 클램프.
  function videoFrame(b, ariaLabel) {
    var frame = el('div', 'mt-video');
    var v = document.createElement('video');
    v.preload = 'none'; v.playsInline = true; v.controls = false; v.setAttribute('playsinline', '');
    if (b.thumbnailUrl) v.poster = b.thumbnailUrl;
    var src = document.createElement('source');
    src.src = b.videoUrl; src.type = 'video/mp4';
    v.appendChild(src);
    frame.appendChild(v);
    function setAR(w, h) {
      if (!w || !h) return;
      var ar = Math.min(1.9, Math.max(0.7, w / h));
      frame.style.setProperty('--mt-video-ar', ar.toFixed(4));
    }
    v.addEventListener('loadedmetadata', function () { setAR(v.videoWidth, v.videoHeight); });
    if (b.thumbnailUrl) {
      var probe = new Image();
      probe.onload = function () { if (!v.videoWidth) setAR(probe.naturalWidth, probe.naturalHeight); };
      probe.src = b.thumbnailUrl;
    }
    var play = el('button', 'mt-video-play');
    play.type = 'button';
    play.setAttribute('aria-label', ariaLabel);
    play.appendChild(el('span', null, '▶'));
    play.addEventListener('click', function () {
      v.controls = true; v.preload = 'auto'; play.remove();
      var r = v.play();
      if (r && r.catch) r.catch(function () { v.controls = true; });
    });
    frame.appendChild(play);
    return frame;
  }

  function renderBriefingMoment(col) {
    var b = col.briefing;
    var s = el('section', 'mt-block mt-briefing');
    var w = el('div', 'mt-wrap');
    w.setAttribute('data-reveal', '');
    var frame = videoFrame(b, schoolTitle(col) + ' 브리핑 영상 재생');
    frame.setAttribute('data-reveal-item', '');
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
    var h2 = el('h2', 'mt-h2', '상담 문의');
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
  // 자동 캐러셀·타임라인 없음. reveal 은 되돌리지 않는다.
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
    setTimeout(function () { pending.slice().forEach(function (g) { reveal(g); io.unobserve(g); drop(g); }); }, 2500);
  }
})();
