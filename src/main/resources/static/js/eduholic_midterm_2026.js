/* 2026 2학기 중간고사 공개 LIVE.
 * 첫 화면 = Shopify Atelier hero(featured 자료 1건).
 * 그 아래 = 학교/학년 LIVE FRAME collection — wiki-sources 를 (school, grade) 로 groupBy 한
 *   Shopify product frame 안에서 실제 scene 이 자동 전환된다:
 *     MATERIAL(실제 Wiki HTML preview)  →  DIGITAL_TWIN(그 학교 출제 경향 분석)
 * FRAME 외곽은 고정, media 만 crossfade 로 교체(image-rollover 문법). 페이지 전체 carousel 없음.
 * API/preview 보안 경계는 서버가 소유하며 이 스크립트는 previewUrl / 공개 analyze contentUrl 외
 * 파일 정보를 사용하지 않는다.
 */
(function () {
  'use strict';

  var root = document.getElementById('mtRoot');
  if (!root) return;

  var HANDLE = root.getAttribute('data-handle') || 'eduholic';
  var SOURCES_API = '/api/academies/' + encodeURIComponent(HANDLE)
    + '/midterm-campaign/wiki-sources';
  var HOME_URL = 'https://eduholic.feel2real.co.kr/';
  var SEASON = '2026 2학기 중간고사';
  var TITLE = '중간고사 준비 중';
  var NOTE = '학교 프린트 내용을 반영했습니다.';
  var LIVE_TITLE = '학교별 준비 LIVE';
  var FRAME_MAX = 6;                  // 화면에 배치하는 학교/학년 FRAME 상한.
  var MATERIAL_MAX = 3;               // FRAME 당 MATERIAL scene 상한(자료 복제 금지).
  var ROLES = ['a', 'b', 'c', 'd'];   // collage presentation role (DB taxonomy 아님).
  var MATERIAL_MS = 8000;             // MATERIAL scene 표시 시간.
  var TWIN_MS = 18000;                // DIGITAL_TWIN scene 표시 시간(내부 crop 3뷰 순환).
  var TWIN_VIEW_MS = 4500;            // DIGITAL_TWIN 내부 crop 전환 간격.
  var TWIN_VIEWS = 3;

  var reduceMotion = window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var hasIO = 'IntersectionObserver' in window;
  var analyzeCache = {};              // schoolName → Promise<analyze[]>

  fetch(SOURCES_API, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  })
    .then(function (response) {
      if (response.status === 404) return [];
      if (!response.ok) throw new Error('wiki sources load failed: ' + response.status);
      return response.json();
    })
    .then(function (payload) {
      var sources = Array.isArray(payload) ? payload : [];
      render(latestSource(sources));
      // LIVE collection = 전체 exposed 자료를 (school, grade) FRAME 으로. hero 도 자기 학교
      // FRAME 에 자연스럽게 포함된다(Shopify featured product 가 collection 에도 있는 것과 같다).
      renderLiveCollection(sources);
    })
    .catch(function () {
      render(null);
    });

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function timestamp(source) {
    return Date.parse(source && source.createdAt ? source.createdAt : '') || 0;
  }

  function latestSource(sources) {
    return sources
      .filter(function (source) {
        return source && typeof source.previewUrl === 'string' && source.previewUrl;
      })
      .slice()
      .sort(function (a, b) { return timestamp(b) - timestamp(a); })[0] || null;
  }

  function gradeNumber(value) {
    var m = String(value == null ? '' : value).match(/\d+/);
    return m ? parseInt(m[0], 10) : null;
  }

  function gradeLabel(grade) {
    var value = String(grade == null ? '' : grade).trim();
    if (!value) return '';
    return /학년$/.test(value) ? value : value + '학년';
  }

  function updateDate(value) {
    if (!value) return '';
    var date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return (date.getMonth() + 1) + '.' + date.getDate() + ' 업데이트';
  }

  function metadata(source) {
    var schoolGrade = [String(source.school || '').trim(), gradeLabel(source.grade)]
      .filter(Boolean)
      .join(' ');
    var date = updateDate(source.createdAt);
    return [schoolGrade, date].filter(Boolean).join(' · ');
  }

  function renderTopbar() {
    var topbar = el('header', 'mt-topbar');
    var wordmark = el('a', 'mt-wordmark', '압구정에듀홀릭');
    wordmark.href = HOME_URL;
    wordmark.rel = 'noopener';
    topbar.appendChild(wordmark);
    topbar.appendChild(el('span', 'mt-season', SEASON));
    return topbar;
  }

  function render(source) {
    root.innerHTML = '';
    root.appendChild(renderTopbar());

    var main = el('main', 'mt-main');
    if (source) main.classList.add('mt-main--feature');

    var intro = el('div', 'mt-intro');
    if (source) {
      var meta = metadata(source);
      if (meta) intro.appendChild(el('p', 'midterm-feature__meta', meta));
    }
    intro.appendChild(el('h1', 'mt-title', source ? TITLE : SEASON));
    if (source) intro.appendChild(el('p', 'mt-note', NOTE));
    main.appendChild(intro);

    if (source) main.appendChild(renderFeature(source));

    root.appendChild(main);
  }

  function renderFeature(source) {
    var section = el('section', 'midterm-feature');
    section.setAttribute('aria-label', '실제 중간고사 준비 자료');

    // 지식상품 패키지: page bg → product surface → clipped media window → 실제 Wiki HTML.
    var surface = el('div', 'midterm-feature__surface');
    var visual = el('div', 'midterm-feature__visual');
    visual.appendChild(safeFrame(source.previewUrl, 'midterm-feature__document', '중간고사 준비 자료'));
    surface.appendChild(visual);
    section.appendChild(surface);

    wireDocumentMotion(section);
    return section;
  }

  /* ── 학교/학년 LIVE FRAME collection ─────────────────────────────────────── */

  /** wiki-sources 를 (school, grade) 로 groupBy. 순서 = 각 group 첫 등장 순. */
  function resolveFrames(sources) {
    var order = [];
    var byKey = {};
    sources.forEach(function (source) {
      var school = String(source.school || '').trim();
      var grade = gradeNumber(source.grade);
      if (!school || !grade) return;
      var key = school + '|' + grade;
      if (!byKey[key]) {
        byKey[key] = { school: school, grade: grade, gradeLabel: gradeLabel(source.grade), materials: [] };
        order.push(key);
      }
      byKey[key].materials.push(source);
    });
    return order.slice(0, FRAME_MAX).map(function (key) { return byKey[key]; });
  }

  /** school 당 1회 캐시. Wiki-sources 의 school 문자열로 부분일치 조회된다. */
  function analyzesForSchool(school) {
    if (!analyzeCache[school]) {
      analyzeCache[school] = fetch('/api/analyzes?schoolName=' + encodeURIComponent(school), {
        headers: { Accept: 'application/json' }, credentials: 'same-origin'
      })
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (list) { return Array.isArray(list) ? list : []; })
        .catch(function () { return []; });
    }
    return analyzeCache[school];
  }

  /** school + grade 정확 일치하는 최신 analyze 의 공개 contentUrl. 없으면 null. */
  function twinContentUrl(frame) {
    return analyzesForSchool(frame.school).then(function (list) {
      var match = list
        .filter(function (a) {
          var s = a && a.subject;
          var englishOk = !s || s.nameEn === 'english' || s.nameKo === '영어';
          return englishOk && gradeNumber(a.grade) === frame.grade
            && typeof a.contentUrl === 'string' && a.contentUrl;
        })
        .sort(function (a, b) { return timestamp(b) - timestamp(a); })[0];
      return match ? match.contentUrl : null;
    });
  }

  function renderLiveCollection(sources) {
    var frames = resolveFrames(sources);
    if (!frames.length) return;

    var section = el('section', 'midterm-live');
    section.setAttribute('aria-label', '학교별 중간고사 준비 현황');
    section.classList.add('midterm-live--n' + frames.length);

    var head = el('header', 'midterm-live__head');
    head.appendChild(el('h2', 'midterm-live__title', LIVE_TITLE));
    section.appendChild(head);

    var collection = el('div', 'midterm-live__collection');
    frames.forEach(function (frame, index) {
      collection.appendChild(renderFrame(frame, index));
    });
    section.appendChild(collection);

    root.appendChild(section);
  }

  function renderFrame(frame, index) {
    var article = el('article', 'midterm-live__frame');
    article.classList.add('is-role-' + ROLES[index % ROLES.length]);
    article.setAttribute('aria-label', frame.school + ' ' + frame.gradeLabel + ' 준비 현황');

    var stage = el('div', 'midterm-live__stage');
    article.appendChild(stage);

    var meta = el('p', 'midterm-live__meta', frame.school + ' · ' + frame.gradeLabel);
    var sub = el('p', 'midterm-live__sub', SEASON);
    article.appendChild(meta);
    article.appendChild(sub);

    // MATERIAL scene(들) — 자료 복제 없이 실제 exposed 자료만.
    var scenes = [];
    frame.materials.slice(0, MATERIAL_MAX).forEach(function (source, i) {
      var scene = el('div', 'midterm-live__scene');
      scene.setAttribute('data-kind', 'material');
      scene.classList.add('is-role-' + ROLES[i % ROLES.length]);
      scene.appendChild(safeFrame(source.previewUrl, 'midterm-live__doc', '중간고사 준비 자료'));
      stage.appendChild(scene);
      scenes.push({ el: scene, kind: 'material', ms: MATERIAL_MS, sub: SEASON });
    });
    if (scenes.length) scenes[0].el.classList.add('is-active');

    // DIGITAL_TWIN scene — 공개 analyze contentUrl(그 학교 출제 경향 분석). school+grade 정확 일치만.
    twinContentUrl(frame).then(function (contentUrl) {
      if (!contentUrl) return;
      var scene = el('div', 'midterm-live__scene');
      scene.setAttribute('data-kind', 'twin');
      var twin = safeFrame(contentUrl, 'midterm-live__twin', '출제 경향 분석');
      twin.setAttribute('data-twin-view', '0');
      scene.appendChild(twin);
      stage.appendChild(scene);
      scenes.push({ el: scene, kind: 'twin', ms: TWIN_MS, sub: '출제 경향 분석', frame: twin });
      if (scenes.length === 1) {
        scene.classList.add('is-active');
        sub.textContent = '출제 경향 분석';
      }
      renderDots(article, scenes.length);
    });

    renderDots(article, scenes.length);
    wireSceneEngine(article, sub, scenes);
    return article;
  }

  function renderDots(article, count) {
    var existing = article.querySelector('.midterm-live__dots');
    if (existing) existing.remove();
    if (count < 2) return;
    var dots = el('div', 'midterm-live__dots');
    dots.setAttribute('aria-hidden', 'true');
    for (var i = 0; i < count; i++) {
      dots.appendChild(el('span', 'midterm-live__dot' + (i === 0 ? ' is-on' : '')));
    }
    (article.querySelector('.midterm-live__stage') || article).appendChild(dots);
  }

  /** FRAME 내부 scene 만 자동 전환. 뷰포트 밖이면 모든 타이머 정지. reduced-motion 이면 정적. */
  function wireSceneEngine(article, subEl, scenes) {
    var sceneTimer = null;
    var twinTimer = null;
    var active = false;
    var index = 0;

    function syncDots() {
      var dots = article.querySelectorAll('.midterm-live__dot');
      dots.forEach(function (d, di) { d.classList.toggle('is-on', di === index); });
    }

    function activateScene(i) {
      clearTimeout(twinTimer);
      var list = article.querySelectorAll('.midterm-live__scene');
      if (!list.length) return;
      index = ((i % list.length) + list.length) % list.length;
      list.forEach(function (s, si) { s.classList.toggle('is-active', si === index); });
      var current = scenes[index];
      if (current && subEl) subEl.textContent = current.sub;
      syncDots();
      if (current && current.kind === 'twin' && current.frame && !reduceMotion) {
        cycleTwinViews(current.frame);
      }
    }

    function cycleTwinViews(twin) {
      var view = 0;
      function step() {
        view = (view + 1) % TWIN_VIEWS;
        twin.setAttribute('data-twin-view', String(view));
        twinTimer = setTimeout(step, TWIN_VIEW_MS);
      }
      twinTimer = setTimeout(step, TWIN_VIEW_MS);
    }

    function next() {
      var count = article.querySelectorAll('.midterm-live__scene').length;
      if (count < 2) return;
      activateScene(index + 1);
      var ms = (scenes[index] && scenes[index].ms) || MATERIAL_MS;
      sceneTimer = setTimeout(next, ms);
    }

    function play() {
      if (active || reduceMotion) return;
      active = true;
      article.classList.add('is-live');
      var count = article.querySelectorAll('.midterm-live__scene').length;
      if (count < 2) return;
      var ms = (scenes[index] && scenes[index].ms) || MATERIAL_MS;
      sceneTimer = setTimeout(next, ms);
      // 활성 twin scene 이면 내부 crop 도 재개.
      var current = scenes[index];
      if (current && current.kind === 'twin' && current.frame) cycleTwinViews(current.frame);
    }

    function pause() {
      active = false;
      article.classList.remove('is-live');
      clearTimeout(sceneTimer);
      clearTimeout(twinTimer);
    }

    if (reduceMotion || !hasIO) {
      article.classList.add('is-in-view');       // 첫 scene 정적 표시
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          article.classList.add('is-in-view');
          play();
        } else {
          pause();
        }
      });
    }, { threshold: 0.4 });
    observer.observe(article);
  }

  /** preview 보안 계약: server-controlled URL 만, sandbox / no interaction / no scroll. */
  function safeFrame(src, className, title) {
    var frame = document.createElement('iframe');
    frame.className = className;
    frame.src = src;
    frame.title = title;
    frame.setAttribute('sandbox', '');
    frame.setAttribute('tabindex', '-1');
    frame.setAttribute('aria-hidden', 'true');
    frame.setAttribute('scrolling', 'no');
    frame.setAttribute('loading', 'lazy');
    return frame;
  }

  function wireDocumentMotion(section) {
    if (reduceMotion) return;

    function start() {
      if (!section.classList.contains('is-in-view')) {
        section.classList.add('is-in-view');
      }
    }

    if (!hasIO) {
      start();
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          start();
          observer.disconnect();
        }
      });
    }, { threshold: 0.28 });
    observer.observe(section);
  }
})();
