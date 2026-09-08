/* 압구정에듀홀릭 홈페이지 — 「2학기 영어내신」(#sec-featured) 를 "2학기 영어 내신 LIVE" 로 전환한다.
 *
 * 목적(작업지시서 §8~§12):
 *  - 섹션 제목/부제를 "2학기 영어 내신 LIVE" 로 바꾸고, 학교 카드를 작은 LIVE window 로 만든다.
 *  - 카드 내부 scene(Wiki 자료 → 출제 경향 분석 → 실제 수업 영상)이 자동으로 바뀐다.
 *  - 출제 경향 데이터는 academy_public.js 가 이미 채운 카드 본문(Digital Twin, /api/analyzes)을
 *    그대로 재사용한다 — 카드마다 재조회하지 않는다(N+1 금지).
 *
 * 실제 수업 영상 scene 의 소스 (우선순위):
 *  1) 그 학교/학년에 **명시적으로 연결된** 내신 LIVE 영상(midterm_live_video_links) = source of truth.
 *     캠페인 스코프 /live-videos 를 1회만 조회한다(카드마다 fetch 아님). 서버가 visible + READY +
 *     소유 + school/grade 를 이미 판정했다. 외부 링크(URL만)면 iframe embed 없이 CTA 로,
 *     직접 업로드면 인라인 <video> 로 보여준다.
 *  2) 명시적 연결이 없으면 academy_public.js 가 넘겨준(F2RAcademyPublicVideos) LESSON 영상 중
 *     파일명이 학교명을 포함하는 것을 fallback 으로 쓴다(#249, 추가 fetch 없음).
 *
 * 안전장치:
 *  - hostname gate(eduholic.feel2real.co.kr) — 다른 Academy 공개 페이지에는 절대 실행되지 않는다.
 *  - 공용 섹션 렌더러 정본·상담 신청 버튼·pill CTA(eduholic_featured_cta.js)는 건드리지 않는다.
 *    #sec-featured 안, .featured-schoolcard 안에서만 동작한다.
 *  - prefers-reduced-motion → 첫 scene 고정. 화면 밖 → 타이머 정지 + 영상 pause(IntersectionObserver).
 *  - 영상은 lazy(preload=none, data-src), muted+playsinline, 동시 재생 금지(pauseOtherVideos).
 */
(function () {
  'use strict';

  var EDUHOLIC_HOST = 'eduholic.feel2real.co.kr';
  var LANDING_PATH = '/eduholic-midterm-2026.html';
  var MIDTERM_LIVE_URL = '/eduholic-midterm-2026.html';
  // 캠페인 스코프 1회 조회(카드마다가 아님). 서버가 visible + READY + 소유 + school/grade 를 판정.
  var LIVE_VIDEOS_API = '/api/academies/eduholic/midterm-campaign/live-videos';
  var SCENE_MS = 4500;
  var schoolVideos = {};        // school → 파일명으로 매칭된 LESSON 영상(fallback, #249)
  var videosReady = false;      // F2RAcademyPublicVideos 색인 완료 여부

  if (window.location.hostname !== EDUHOLIC_HOST) return;
  if (window.location.pathname === LANDING_PATH) return;

  // school(문자열) → 그 학교에 명시적으로 연결된 내신 LIVE 영상 link 배열(서버 정렬 유지).
  // null = 아직 미조회, {} = 조회 완료·없음.
  var liveVideosBySchool = null;
  var liveVideosRequested = false;

  function loadLiveVideos(afterLoad) {
    if (liveVideosRequested) return;
    liveVideosRequested = true;
    fetch(LIVE_VIDEOS_API, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) {
        var map = {};
        (Array.isArray(list) ? list : []).forEach(function (v) {
          var s = String(v && v.school || '').trim();
          if (!s) return;
          (map[s] = map[s] || []).push(v);
        });
        liveVideosBySchool = map;
      })
      .catch(function () { liveVideosBySchool = {}; })
      .then(function () { if (typeof afterLoad === 'function') afterLoad(); });
  }

  /** 그 학교의 대표 LIVE 링크(서버 정렬 sort_order → link_id 의 첫 항목). 없으면 null. */
  function pickLiveLink(school) {
    var list = liveVideosBySchool && liveVideosBySchool[school];
    return list && list.length ? list[0] : null;
  }

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

  function normalizedSchool(value) {
    return String(value || '').replace(/중학교/g, '중').replace(/\s+/g, '').toLowerCase();
  }
  function indexSchoolVideos() {
    var items = window.F2RAcademyPublicVideos;
    if (!Array.isArray(items)) return false;
    (items || []).filter(function (v) {
      return v && v.purpose === 'LESSON' && v.availabilityStatus === 'READY' && v.videoUrl;
    }).forEach(function (video) {
      var name = normalizedSchool(video.originalFilename);
      Array.prototype.slice.call(document.querySelectorAll('#sec-featured [data-featured-card]')).forEach(function (card) {
        var school = (card.getAttribute('data-school') || '').trim();
        if (!schoolVideos[school] && name.indexOf(normalizedSchool(school)) >= 0) schoolVideos[school] = video;
      });
    });
    videosReady = true;
    return true;
  }

  function pauseOtherVideos(active) {
    Array.prototype.slice.call(document.querySelectorAll('#sec-featured .mt-hl-video')).forEach(function (video) {
      if (video !== active) video.pause();
    });
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

    var scenes = [
      { tag: 'Wiki', head: school + (gradeLabel ? ' · ' + gradeLabel : ''),
        lines: ['최근 시험 자료', '학교별 시험자료 분석 중'], href: href, hrefLabel: 'Wiki 자료 보기 →' },
      { tag: '출제 경향', head: school + ' 출제 경향 분석',
        lines: ['출제자 Digital Twin ' + count + '건', firstRow] }
    ];

    // 실제 수업 영상 scene — 명시적 LIVE 연결(source of truth) 우선, 없으면 파일명 매칭 fallback.
    var liveLink = pickLiveLink(school);
    if (liveLink && liveLink.externalUrl && !liveLink.videoUrl) {
      // 외부 링크: iframe embed 하지 않고 CTA 로만(§13). 차단 URL 로 카드가 깨지지 않는다.
      scenes.push({ tag:'실제 수업 영상', head: school + ' 수업 현장', lines: [],
        href: liveLink.externalUrl, hrefLabel: '영상 보기 →' });
    } else if (liveLink && liveLink.videoUrl) {
      scenes.push({ tag:'실제 수업 영상', head: school + ' 수업 현장', lines: [], video: liveLink });
    } else if (schoolVideos[school]) {
      scenes.push({ tag:'실제 수업 영상', head: school + ' 수업 현장', lines: [], video: schoolVideos[school] });
    }
    return scenes;
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
    if (scene.video) {
      var media = document.createElement('div');
      media.className = 'mt-hl-media';
      var video = document.createElement('video');
      video.className = 'mt-hl-video';
      video.controls = true; video.muted = true; video.defaultMuted = true;
      video.playsInline = true; video.preload = 'none';
      video.setAttribute('data-src', scene.video.videoUrl);
      if (scene.video.thumbnailUrl) video.poster = scene.video.thumbnailUrl;
      video.addEventListener('play', function () { pauseOtherVideos(video); });
      media.appendChild(video); stage.appendChild(media);
    }
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
    function prepareVisibleVideo(near) {
      var video = stage.querySelector('.mt-hl-video');
      if (!video) return;
      if (!near) { video.pause(); return; }
      if (!video.getAttribute('src') && video.dataset.src) video.setAttribute('src', video.dataset.src);
    }

    if (reduceMotion() || scenes.length < 2) return;

    var timer = null;
    var running = false;
    function step() {
      if (!running) return;
      i = (i + 1) % scenes.length;
      stage.classList.add('is-swap');
      timer = setTimeout(function () {
        renderScene(stage, scenes[i]);
        prepareVisibleVideo(true);
        stage.classList.remove('is-swap');
        timer = setTimeout(step, SCENE_MS);
      }, 260);
    }
    function start() { if (running) return; running = true; timer = setTimeout(step, SCENE_MS); }
    function stop() { running = false; if (timer) { clearTimeout(timer); timer = null; } }

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { if (e.isIntersecting) { prepareVisibleVideo(true); start(); } else { prepareVisibleVideo(false); stop(); } });
      }, { threshold: 0.2 }).observe(card);
    } else {
      start();
    }
  }

  function scan() {
    if (window.location.hostname !== EDUHOLIC_HOST) return;
    applyHeading();
    if (!videosReady) indexSchoolVideos();
    if (!videosReady) return;
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
      '#sec-featured .mt-hl-media{width:100%;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:#090d18;}',
      '#sec-featured .mt-hl-video{display:block;width:100%;height:100%;object-fit:contain;background:#090d18;}',
      '@media (prefers-reduced-motion:reduce){#sec-featured .mt-hl-stage{transition:none;}}'
    ].join('');
    document.head.appendChild(style);
  }

  function start() {
    injectStyle();
    indexSchoolVideos();
    // 명시적 LIVE 연결은 캠페인 스코프로 1회만 조회한다. 뒤늦게 도착하면 이미 그린 카드도
    // 표식을 지우고 한 번 더 올려 "실제 수업 영상" scene 을 더한다(초기 1회, 카드마다 fetch 아님).
    loadLiveVideos(function () {
      Array.prototype.slice
        .call(document.querySelectorAll('#sec-featured [data-featured-card]'))
        .forEach(function (c) { delete c.dataset.mtHomeLive; });
      scan();
    });
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
