/* 2026 2학기 중간고사 공개 LIVE.
 * 첫 화면 = Shopify Atelier hero(featured 자료 1건).
 * 그 아래 = 학교/학년 LIVE FRAME collection — wiki-sources 를 (school, grade) 로 groupBy 한
 *   Shopify product frame 안에서 실제 scene 이 자동 전환된다:
 *     MATERIAL(실제 Wiki HTML preview)  →  ACADEMY AMBIENT(학원 현장 영상, 있을 때만)  →
 *     DIGITAL_TWIN(그 학교 출제 경향 분석)
 * FRAME 외곽은 고정, media 만 crossfade 로 교체(image-rollover 문법). 페이지 전체 carousel 없음.
 * ACADEMY AMBIENT 는 academy-level 현장 visual 이다 — 특정 학교/학년에 귀속되지 않고, 없으면
 *   MATERIAL → 분석 흐름이 그대로 동작한다(optional scene).
 * API/preview 보안 경계는 서버가 소유하며 이 스크립트는 previewUrl / 공개 analyze contentUrl /
 *   공개 AcademyVideo API 가 준 videoUrl 외 파일 정보를 사용하지 않는다.
 */
(function () {
  'use strict';

  var root = document.getElementById('mtRoot');
  if (!root) return;

  var HANDLE = root.getAttribute('data-handle') || 'eduholic';
  var SOURCES_API = '/api/academies/' + encodeURIComponent(HANDLE)
    + '/midterm-campaign/wiki-sources';
  // Phase 4e — 상단 Showcase: 서버가 실제 Wiki 자료 HTML 에서 bounded parsing 으로 뽑은 학교별
  // 시험범위 텍스트(lesson / 문법 포인트 / 예문). 비면 기존 iframe hero 로 폴백.
  var SHOWCASE_API = '/api/academies/' + encodeURIComponent(HANDLE)
    + '/midterm-campaign/showcase';
  // Phase 4c — (학교, 학년, 역할) 로 연결된 학교·학년 현장 영상. 서버가 이미 visible + READY +
  // ownership 을 판정해 내려준다. 없으면 FRAME 은 자료 → 분석 흐름 그대로.
  var LIVE_VIDEOS_API = '/api/academies/' + encodeURIComponent(HANDLE)
    + '/midterm-campaign/live-videos';
  // 공개 AcademyVideo API 는 handle 이 아니라 slug 로 키잉된다. slug 는 이미 모든 공개 이미지/영상
  // URL 에 노출돼 있어 정적 config 로 둔다(신규 DTO/필드/엔드포인트 없음).
  var SLUG = root.getAttribute('data-slug') || '';
  var VIDEOS_API = SLUG
    ? '/api/academies/' + encodeURIComponent(SLUG) + '/videos'
    : null;
  // 운영자가 LIVE ambient 로 허용한 AcademyVideo id 목록. 파일명/영상 용도/길이로 자동 분류하지
  // 않는다 — 명시적으로 나열된 id 만 쓴다. 비면 ambient scene 없음(video 는 optional).
  var AMBIENT_IDS = (root.getAttribute('data-ambient-video-ids') || '')
    .split(',')
    .map(function (s) { return parseInt(s, 10); })
    .filter(function (n) { return n > 0; });
  // 영상별 LIVE scene 시작 초. 형식 "id:sec,id:sec" — 편집기가 아니라, 승인된 영상을 어느
  // 구간부터 보여줄지 지정하는 정적 config. 없으면 0초부터.
  var AMBIENT_STARTS = {};
  (root.getAttribute('data-ambient-video-starts') || '').split(',').forEach(function (pair) {
    var kv = pair.split(':');
    var id = parseInt(kv[0], 10);
    var sec = parseFloat(kv[1]);
    if (id > 0 && sec >= 0) AMBIENT_STARTS[id] = sec;
  });
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
  var VIDEO_MS = 9000;               // video scene(STUDENT_STUDY / LESSON_INTERACTION / AMBIENT) 표시 시간. 영상은 loop.
  var SHOWCASE_MS = 7000;            // 상단 Showcase 학교 scene 표시 시간.

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
      // 초기 렌더는 단일 파이프라인이다. render() 는 root 를 비우고(root.innerHTML=''),
      // renderLiveCollection() 은 root 에 append 한다 — 이 둘이 독립 Promise 로 경쟁하면
      // LIVE 가 먼저 붙은 뒤 늦게 도착한 showcase 응답이 render() 로 LIVE 를 지운다.
      // showcase / ambient / live 세 helper 는 모두 실패 시 [] 를 반환하므로(reject 없음)
      // Promise.all 로 모아 순서를 고정한다: 항상 render() → renderLiveCollection().
      return Promise.all([showcaseScenes(), ambientVideos(), liveVideos()])
        .then(function (r) {
          render(latestSource(sources), r[0]);
          // LIVE 렌더 중 예기치 못한 오류가 나도 이미 그려진 hero/showcase 는 지우지 않는다.
          try {
            renderLiveCollection(sources, r[1], r[2]);
          } catch (e) { /* MATERIAL frame 없이도 상단은 유지 */ }
        });
    })
    .catch(function () {
      // wiki-sources 자체 실패만 여기로 온다(helper 들은 [] 로 흡수). 짧은 시즌 화면.
      render(null, []);
    });

  /** 서버가 Wiki HTML 에서 추출한 학교별 시험범위 scene. 실패/미설정 시 []. */
  function showcaseScenes() {
    return fetch(SHOWCASE_API, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) { return Array.isArray(list) ? list : []; })
      .catch(function () { return []; });
  }

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

  function render(source, scenes) {
    scenes = Array.isArray(scenes) ? scenes.filter(function (s) { return s && s.school; }) : [];
    var hasHero = !!source || scenes.length > 0;

    root.innerHTML = '';
    root.appendChild(renderTopbar());

    var main = el('main', 'mt-main');
    if (hasHero) main.classList.add('mt-main--feature');

    var intro = el('div', 'mt-intro');
    if (source && !scenes.length) {
      var meta = metadata(source);
      if (meta) intro.appendChild(el('p', 'midterm-feature__meta', meta));
    }
    intro.appendChild(el('h1', 'mt-title', hasHero ? TITLE : SEASON));
    if (hasHero) intro.appendChild(el('p', 'mt-note', NOTE));
    main.appendChild(intro);

    if (scenes.length) {
      main.appendChild(renderShowcase(scenes));         // Phase 4e — 학교별 시험범위 rotation
    } else if (source) {
      main.appendChild(renderFeature(source));          // 폴백 — 기존 iframe hero
    }

    root.appendChild(main);
  }

  /* ── 상단 Showcase — 학교별 시험범위(Wiki 추출 텍스트) rotation ────────────── */

  function chip(text) {
    return el('span', 'midterm-showcase__chip', text);
  }

  function renderShowcaseScene(scene) {
    var card = el('div', 'midterm-showcase__scene');

    var id = el('div', 'midterm-showcase__id');
    var school = String(scene.school || '').trim();
    id.appendChild(el('span', 'midterm-showcase__school',
      /(중학교|고등학교|중$|고$|초등학교)/.test(school) ? school : school + '학교'));
    var gl = gradeLabel(scene.grade);
    var right = [gl, String(scene.examLabel || '').trim()].filter(Boolean).join(' · ');
    if (right) id.appendChild(el('span', 'midterm-showcase__exam', right));
    card.appendChild(id);

    var body = el('div', 'midterm-showcase__body');
    var lesson = String(scene.lesson || '').trim();
    if (lesson) body.appendChild(el('p', 'midterm-showcase__lesson', lesson));

    var grammar = Array.isArray(scene.grammarPoints) ? scene.grammarPoints.filter(Boolean) : [];
    var sentences = Array.isArray(scene.sampleSentences) ? scene.sampleSentences.filter(Boolean) : [];

    if (grammar.length) {
      body.appendChild(el('p', 'midterm-showcase__label', '핵심 문법'));
      var chips = el('div', 'midterm-showcase__chips');
      grammar.slice(0, 3).forEach(function (g) { chips.appendChild(chip(g)); });
      body.appendChild(chips);
    }
    if (sentences.length) {
      body.appendChild(el('p', 'midterm-showcase__label', '핵심 문장'));
      var list = el('div', 'midterm-showcase__sentences');
      sentences.slice(0, 3).forEach(function (s) {
        list.appendChild(el('p', 'midterm-showcase__sentence', s));
      });
      body.appendChild(list);
    }
    if (!lesson && !grammar.length && !sentences.length) {
      body.appendChild(el('p', 'midterm-showcase__pending', '시험범위 자료 준비 중'));
    }
    card.appendChild(body);
    card.appendChild(el('p', 'midterm-showcase__foot', '실제 학교 시험범위 자료 기반'));
    return card;
  }

  function renderShowcase(scenes) {
    var section = el('section', 'midterm-showcase');
    section.setAttribute('aria-label', '학교별 중간고사 시험범위');

    var surface = el('div', 'midterm-showcase__surface');
    var built = scenes.map(function (scene, i) {
      var card = renderShowcaseScene(scene);
      if (i === 0) card.classList.add('is-active');
      surface.appendChild(card);
      return card;
    });
    section.appendChild(surface);

    if (built.length > 1) {
      var dots = el('div', 'midterm-showcase__dots');
      dots.setAttribute('aria-hidden', 'true');
      built.forEach(function (_, i) {
        dots.appendChild(el('span', 'midterm-showcase__dot' + (i === 0 ? ' is-on' : '')));
      });
      section.appendChild(dots);
      wireShowcaseRotation(section, built);
    }
    return section;
  }

  /** 학교 scene 자동 순환. 뷰포트 밖이면 정지. reduced-motion 이면 첫 scene 정적. */
  function wireShowcaseRotation(section, cards) {
    var timer = null;
    var active = false;
    var index = 0;

    function show(i) {
      index = ((i % cards.length) + cards.length) % cards.length;
      cards.forEach(function (c, ci) { c.classList.toggle('is-active', ci === index); });
      var dots = section.querySelectorAll('.midterm-showcase__dot');
      dots.forEach(function (d, di) { d.classList.toggle('is-on', di === index); });
    }
    function next() {
      if (!active) return;
      show(index + 1);
      timer = setTimeout(next, SHOWCASE_MS);
    }
    function play() {
      if (active || reduceMotion) return;
      active = true;
      timer = setTimeout(next, SHOWCASE_MS);
    }
    function pause() {
      active = false;
      clearTimeout(timer);
      timer = null;
    }

    if (reduceMotion || !hasIO) return;
    new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) play(); else pause();
      });
    }, { threshold: 0.25 }).observe(section);
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

  /**
   * 운영자가 허용한(AMBIENT_IDS) READY AcademyVideo 만, 나열 순서대로. 실패/미설정 시 [].
   * 공개 목록 응답이 준 videoUrl(= slug 경로)만 쓰고 영상 용도/파일명/길이로 선별하지 않는다.
   */
  function ambientVideos() {
    if (!VIDEOS_API || !AMBIENT_IDS.length) return Promise.resolve([]);
    return fetch(VIDEOS_API, {
      headers: { Accept: 'application/json' }, credentials: 'same-origin'
    })
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) {
        var byId = {};
        (Array.isArray(list) ? list : []).forEach(function (v) {
          if (v && v.status === 'READY' && typeof v.videoUrl === 'string' && v.videoUrl) {
            byId[v.videoId] = v;
          }
        });
        return AMBIENT_IDS.map(function (id) { return byId[id]; }).filter(Boolean);
      })
      .catch(function () { return []; });
  }

  /**
   * (학교, 학년, 역할) 로 연결된 학교·학년 현장 영상. 서버가 visible + READY + ownership 을 이미
   * 판정했고 여기서는 순서만 신뢰한다(sort_order → link_id tie-break 는 서버 정렬). 실패 시 [].
   */
  function liveVideos() {
    return fetch(LIVE_VIDEOS_API, {
      headers: { Accept: 'application/json' }, credentials: 'same-origin'
    })
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) { return Array.isArray(list) ? list : []; })
      .catch(function () { return []; });
  }

  /** frameLinks 에서 role 의 대표(첫) 영상. 서버 정렬을 그대로 신뢰한다. */
  function pickRole(frameLinks, role) {
    for (var i = 0; i < frameLinks.length; i++) {
      if (frameLinks[i] && frameLinks[i].role === role
        && typeof frameLinks[i].videoUrl === 'string' && frameLinks[i].videoUrl) {
        return frameLinks[i];
      }
    }
    return null;
  }

  /**
   * video scene 1개를 stage 에 추가한다. 세 종류(STUDENT_STUDY/LESSON_INTERACTION/AMBIENT) 공통.
   * OUTER FRAME · MEDIA STAGE 크기는 그대로 두고 video element 의 fit 만 방향에 맞춘다 —
   * portrait/phone footage 는 contain(수업 맥락 유지, 남는 공간은 stage 배경), landscape 는
   * stage 비율에 가깝게 contain. cover 로 상체만 크게 잘리는 panorama 실패를 만들지 않는다.
   * videoId 하드코딩 없이 runtime videoWidth/videoHeight 로만 판정한다.
   */
  function addVideoScene(stage, scenes, url, poster, startAt) {
    var vscene = el('div', 'midterm-live__scene');
    vscene.setAttribute('data-kind', 'video');
    var video = document.createElement('video');
    video.className = 'midterm-live__video';
    video.src = url;
    if (poster) video.poster = poster;
    video.muted = true;
    video.defaultMuted = true;
    video.loop = true;
    video.playsInline = true;
    video.preload = 'metadata';
    video.setAttribute('muted', '');
    video.setAttribute('playsinline', '');
    video.setAttribute('aria-hidden', 'true');
    video.setAttribute('tabindex', '-1');
    function markOrientation() {
      if (!video.videoWidth || !video.videoHeight) return;
      var portrait = video.videoHeight > video.videoWidth;
      vscene.classList.toggle('is-portrait', portrait);
      vscene.classList.toggle('is-landscape', !portrait);
    }
    video.addEventListener('loadedmetadata', markOrientation);
    markOrientation();
    vscene.appendChild(video);
    stage.appendChild(vscene);
    scenes.push({ el: vscene, kind: 'video', ms: VIDEO_MS, sub: SEASON, video: video,
      startAt: startAt > 0 ? startAt : 0 });
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

  function renderLiveCollection(sources, ambient, liveLinks) {
    var frames = resolveFrames(sources);
    if (!frames.length) return;
    var videos = Array.isArray(ambient) ? ambient : [];
    var links = Array.isArray(liveLinks) ? liveLinks : [];

    var section = el('section', 'midterm-live');
    section.setAttribute('aria-label', '학교별 중간고사 준비 현황');
    section.classList.add('midterm-live--n' + frames.length);

    var head = el('header', 'midterm-live__head');
    head.appendChild(el('h2', 'midterm-live__title', LIVE_TITLE));
    section.appendChild(head);

    var collection = el('div', 'midterm-live__collection');
    var ambientQueue = videos.slice();     // specific 이 없는 FRAME 에만, 순서대로, 반복 없이 배정.
    frames.forEach(function (frame, index) {
      var frameLinks = links.filter(function (l) {
        return l && String(l.school || '').trim() === frame.school
          && gradeNumber(l.grade) === frame.grade;
      });
      var hasSpecific = frameLinks.some(function (l) {
        return l && (l.role === 'STUDENT_STUDY' || l.role === 'LESSON_INTERACTION');
      });
      var ambientForFrame = hasSpecific ? null : (ambientQueue.shift() || null);
      collection.appendChild(renderFrame(frame, index, ambientForFrame, frameLinks));
    });
    section.appendChild(collection);

    root.appendChild(section);
  }

  function renderFrame(frame, index, ambientVideo, frameLinks) {
    var article = el('article', 'midterm-live__frame');
    article.classList.add('is-role-' + ROLES[index % ROLES.length]);
    article.setAttribute('aria-label', frame.school + ' ' + frame.gradeLabel + ' 준비 현황');

    // OUTER PRODUCT FRAME(article) → MEDIA STAGE(stage) → SCENE → 실제 media / 아래 작은 product meta.
    var stage = el('div', 'midterm-live__stage');
    article.appendChild(stage);

    var info = el('div', 'midterm-live__info');
    var meta = el('p', 'midterm-live__meta', frame.school + ' · ' + frame.gradeLabel);
    var sub = el('p', 'midterm-live__sub', SEASON);
    info.appendChild(meta);
    info.appendChild(sub);
    article.appendChild(info);

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

    // video scene(들) — 우선순위: 그 학교/학년의 specific 영상 > academy ambient > 없음.
    //   specific STUDENT_STUDY 대표 1개 있으면        → STUDENT_STUDY scene
    //   specific LESSON_INTERACTION 대표 1개 있으면    → LESSON_INTERACTION scene
    //   specific 이 하나도 없고 ambient 배정 가능하면  → AMBIENT scene
    // 역할 이름은 scene 순서 결정용 내부 값일 뿐 화면에 표시하지 않는다. specific 영상은 별도
    // 시작 offset 이 없으므로 0 부터.
    var links = Array.isArray(frameLinks) ? frameLinks : [];
    var studyClip = pickRole(links, 'STUDENT_STUDY');
    var interactionClip = pickRole(links, 'LESSON_INTERACTION');
    if (studyClip) {
      addVideoScene(stage, scenes, studyClip.videoUrl, studyClip.thumbnailUrl, 0);
    }
    if (interactionClip) {
      addVideoScene(stage, scenes, interactionClip.videoUrl, interactionClip.thumbnailUrl, 0);
    }
    if (!studyClip && !interactionClip && ambientVideo && ambientVideo.videoUrl) {
      addVideoScene(stage, scenes, ambientVideo.videoUrl, ambientVideo.thumbnailUrl,
        AMBIENT_STARTS[ambientVideo.videoId] || 0);
    }

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

    // 한 FRAME 안에서 동시에 두 영상이 재생되지 않게 — scene 이 바뀌거나 FRAME 이 뷰포트를
    // 벗어나면 이 FRAME 의 모든 ambient 영상을 멈춘다(§19·§20).
    function pauseVideos() {
      article.querySelectorAll('.midterm-live__video').forEach(function (v) {
        try { v.pause(); } catch (e) {}
      });
    }

    function playVideo(v, startAt) {
      try {
        var t = startAt > 0 ? startAt : 0;
        var d = v.duration;                // metadata 로딩 전이면 NaN — 그대로 best-effort seek.
        if (d && t >= d) t = 0;            // 설정 시작점이 실제 길이 이상이면 안전 fallback.
        v.currentTime = t;                 // 재활성화마다 설정 시작점(없으면 0)부터.
        var p = v.play();
        if (p && typeof p.catch === 'function') p.catch(function () {});
      } catch (e) {}
    }

    function activateScene(i) {
      clearTimeout(twinTimer);
      var list = article.querySelectorAll('.midterm-live__scene');
      if (!list.length) return;
      index = ((i % list.length) + list.length) % list.length;
      list.forEach(function (s, si) { s.classList.toggle('is-active', si === index); });
      pauseVideos();
      var current = scenes[index];
      if (current && subEl) subEl.textContent = current.sub;
      syncDots();
      if (current && current.kind === 'twin' && current.frame && !reduceMotion) {
        cycleTwinViews(current.frame);
      }
      if (current && current.kind === 'video' && current.video && !reduceMotion) {
        playVideo(current.video, current.startAt);
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
      // 활성 scene 이 twin 이면 내부 crop 을, video 면 재생을 재개(뷰포트 재진입).
      var current = scenes[index];
      if (current && current.kind === 'twin' && current.frame) cycleTwinViews(current.frame);
      if (current && current.kind === 'video' && current.video) playVideo(current.video, current.startAt);
      var count = article.querySelectorAll('.midterm-live__scene').length;
      if (count < 2) return;
      var ms = (current && current.ms) || MATERIAL_MS;
      sceneTimer = setTimeout(next, ms);
    }

    function pause() {
      active = false;
      article.classList.remove('is-live');
      clearTimeout(sceneTimer);
      clearTimeout(twinTimer);
      pauseVideos();                       // 뷰포트 밖 FRAME 의 영상은 항상 정지(§19).
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
