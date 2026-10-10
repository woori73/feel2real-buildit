/* BuildIt 2단계 — 테넌트 홈페이지 LIVE 모드.
 *
 * eduholic_midterm_home_live.js 의 테넌트 일반화판. "#sec-featured 를 LIVE 형으로
 * 전환하고, 학교별 카드를 scene 순환형으로 업그레이드한다"는 동작은 동일하고,
 * 하드코딩된 호스트·API·영상·문구만 학원별 설정(builderDraft['tenant.liveMode'])에서 읽는다.
 *
 * 설정 스키마 (JSON):
 *   { "enabled": true,
 *     "videoSrc": "/video/acme/main.mp4",
 *     "videoTitle": "...", "videoDesc": "...",
 *     "videoNotes": ["...", "..."],
 *     "landingPath": "/campaign.html",
 *     "headingEyebrow": "내신 LIVE",
 *     "headingTitle": "2학기 영어 내신 LIVE",
 *     "headingDesc": "...",
 *     "sceneIntervalMs": 4500,
 *     "schoolCardKindLabel": "학교별 준비 LIVE",
 *     "filterMarker": "homepage-hero-marker",
 *     "liveVideosApi": "/api/academies/{slug}/midterm-campaign/live-videos",
 *     "showcaseApi": "/api/academies/{slug}/midterm-campaign/showcase" }
 * enabled !== true 이면 아무 것도 하지 않는다.
 * 학교별 scene 은 기존 liveVideosApi/showcaseApi 응답을 source of truth 로 유지한다.
 * API 가 없으면(404 등) 빈 상태로 우아하게 동작한다.
 */
(function () {
  'use strict';

  // eduholic 호스트는 기존 eduholic_midterm_home_live.js 가 담당한다.
  if (window.location.hostname === 'eduholic.feel2real.co.kr') return;
  if (!window.F2RTenantSettings) return;

  window.F2RTenantSettings.get('tenant.liveMode').then(function (cfg) {
    if (!cfg) return;
    start(cfg);
  }).catch(function () {});

  function detectSlug() {
    try {
      var root = document.getElementById('publicView');
      if (root && root.dataset && root.dataset.slug) return root.dataset.slug;
      var parts = String(location.pathname || '').split('/').filter(Boolean);
      if (parts.length) return decodeURIComponent(parts[parts.length - 1] || '');
      var m = String(location.hostname || '').match(/^([a-z0-9][a-z0-9-]*)\.feel2real\.co\.kr$/);
      if (m && m[1] !== 'www') return m[1];
    } catch (_) {}
    return '';
  }

  function start(cfg) {
    var LANDING_PATH = cfg.landingPath || '';
    var VIDEO_SRC = cfg.videoSrc || '';
    var VIDEO_TITLE = cfg.videoTitle || '';
    var VIDEO_DESC = cfg.videoDesc || '';
    var VIDEO_NOTES = Array.isArray(cfg.videoNotes) ? cfg.videoNotes : [];
    var SCENE_MS = cfg.sceneIntervalMs > 0 ? cfg.sceneIntervalMs : 4500;
    var FILTER_MARKER = cfg.filterMarker || '';
    var KIND_LABEL = cfg.schoolCardKindLabel || '학교별 준비 LIVE';
    var slug = detectSlug();
    var LIVE_VIDEOS_API = String(cfg.liveVideosApi || '/api/academies/{slug}/midterm-campaign/live-videos').split('{slug}').join(slug);
    var SHOWCASE_API = String(cfg.showcaseApi || '/api/academies/{slug}/midterm-campaign/showcase').split('{slug}').join(slug);

    if (LANDING_PATH && window.location.pathname === LANDING_PATH) return;
    // MAIN FEATURE 영상이 없으면 LIVE 전환의 의미가 없으므로 조용히 종료한다.
    if (!VIDEO_SRC) return;

    var schoolVideos = {};
    var videosReady = false;
    var liveVideosBySchool = null;
    var showcaseBySchool = null;
    var campaignRequested = false;

    function loadCampaign(afterLoad) {
      if (campaignRequested) return;
      campaignRequested = true;
      var done = 0;
      function tick() { if (++done === 2 && typeof afterLoad === 'function') afterLoad(); }
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
        .then(tick);
      fetch(SHOWCASE_API, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (list) {
          var map = {};
          (Array.isArray(list) ? list : []).forEach(function (s) {
            var k = String(s && s.school || '').trim();
            if (k && !map[k]) map[k] = s;
          });
          showcaseBySchool = map;
        })
        .catch(function () { showcaseBySchool = {}; })
        .then(tick);
    }

    function pickLiveLink(school) {
      var list = liveVideosBySchool && liveVideosBySchool[school];
      return list && list.length ? list[0] : null;
    }

    function isMainFeatureVideo(video) {
      return !!(FILTER_MARKER && video && video.availabilityStatus === 'READY' && video.videoUrl
        && String(video.originalFilename || '').indexOf(FILTER_MARKER) >= 0);
    }

    function renderMainFeature() {
      var section = document.getElementById('sec-featured');
      var head = section && section.querySelector('.featured-head');
      if (!section || !head || section.querySelector('[data-tenant-main-feature]')) return;

      var feature = document.createElement('article');
      feature.className = 'tn-main-feature';
      feature.setAttribute('data-tenant-main-feature', '');

      // 표시 순서: 제목 → 설명 → 안내 → 동영상
      var copy = document.createElement('div');
      copy.className = 'tn-main-feature__copy';
      copy.innerHTML = '<strong></strong><small></small><p class="tn-main-feature__note"></p>';
      copy.querySelector('strong').textContent = VIDEO_TITLE;
      copy.querySelector('small').textContent = VIDEO_DESC;
      var note = copy.querySelector('.tn-main-feature__note');
      VIDEO_NOTES.forEach(function (lineText) {
        var line = document.createElement('span');
        line.className = 'tn-main-feature__note-line';
        line.textContent = lineText;
        note.appendChild(line);
      });
      feature.appendChild(copy);

      var media = document.createElement('div');
      media.className = 'tn-main-feature__media';
      var video = document.createElement('video');
      video.className = 'tn-main-feature__video';
      video.controls = true;
      video.setAttribute('controlsList', 'nodownload');
      video.playsInline = true;
      video.preload = 'metadata';
      video.setAttribute('aria-label', VIDEO_TITLE);
      video.src = VIDEO_SRC;
      video.addEventListener('play', function () { pauseOtherVideos(video); });
      media.appendChild(video);
      feature.appendChild(media);
      head.insertAdjacentElement('afterend', feature);

      var cards = section.querySelector('[data-featured-cards]');
      if (cards && !section.querySelector('.tn-main-feature__schools-title')) {
        var schoolsTitle = document.createElement('h3');
        schoolsTitle.className = 'tn-main-feature__schools-title';
        schoolsTitle.textContent = '학교별 내신 대비';
        cards.insertAdjacentElement('beforebegin', schoolsTitle);
      }
    }

    function pickSyllabus(school) {
      var s = showcaseBySchool && showcaseBySchool[school];
      if (!s) return null;
      if (String(s.sourceId || '').indexOf('final-') === 0) return null;
      var lesson = String(s.lesson || '').trim();
      var grammar = (Array.isArray(s.grammarPoints) ? s.grammarPoints : []).filter(Boolean);
      var sentences = (Array.isArray(s.sampleSentences) ? s.sampleSentences : []).filter(Boolean);
      if (!lesson && !grammar.length && !sentences.length) return null;
      return { lesson: lesson, grammar: grammar, sentences: sentences, grade: s.grade };
    }

    function youtubeIdFromEmbed(embedUrl) {
      var m = String(embedUrl || '').match(/\/embed\/([A-Za-z0-9_-]{6,20})/);
      return m ? m[1] : null;
    }
    function reduceMotion() { return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches); }
    function text(node, value) { if (node && node.textContent !== value) node.textContent = value; }

    function applyHeading() {
      var head = document.querySelector('#sec-featured .featured-head');
      if (!head || head.dataset.tnHomeLiveHead === 'true') return;
      var title = head.querySelector('h2.featured-title');
      if (!title) return;
      head.dataset.tnHomeLiveHead = 'true';
      if (cfg.headingEyebrow) text(head.querySelector('.featured-eyebrow-text'), cfg.headingEyebrow);
      if (cfg.headingTitle) text(title, cfg.headingTitle);
      var desc = head.querySelector('.featured-desc');
      if (desc && cfg.headingDesc) desc.textContent = cfg.headingDesc;
    }

    function normalizedSchool(value) {
      return String(value || '').replace(/중학교/g, '중').replace(/\s+/g, '').toLowerCase();
    }

    function indexSchoolVideos() {
      var items = window.F2RAcademyPublicVideos;
      if (!Array.isArray(items)) return false;
      schoolVideos = {};
      (items || []).filter(function (v) {
        return v && v.purpose === 'LESSON' && v.availabilityStatus === 'READY' && v.videoUrl
          && !isMainFeatureVideo(v);
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
      Array.prototype.slice.call(document.querySelectorAll('#sec-featured .tn-hl-video')).forEach(function (video) {
        if (video !== active) { try { video.pause(); } catch (e) {} }
      });
      Array.prototype.slice.call(document.querySelectorAll('#sec-featured .tn-hl-embed')).forEach(function (frame) {
        if (frame.getAttribute('src') && frame.getAttribute('src') !== 'about:blank') frame.setAttribute('src', 'about:blank');
      });
    }

    function readScenes(card) {
      var school = (card.getAttribute('data-school') || '').trim();
      var body = card.querySelector('[data-featured-card-body]');
      if (!body) return null;
      if (body.querySelector('.featured-schoolcard-loading')) return 'pending';
      var rows = Array.prototype.slice.call(body.querySelectorAll('.featured-schoolcard-row'));
      var empty = !!body.querySelector('.featured-schoolcard-empty');
      if (!rows.length && !empty) return null;
      var scenes = [];
      var syllabus = pickSyllabus(school);
      if (syllabus) {
        var lines = [];
        if (syllabus.lesson) lines.push(syllabus.lesson);
        if (syllabus.grammar.length) lines.push('핵심 문법 · ' + syllabus.grammar.slice(0, 2).join(' · '));
        if (!lines.length && syllabus.sentences.length) lines.push(syllabus.sentences[0]);
        scenes.push({ tag: '시험범위', head: school + (syllabus.grade ? ' · ' + gradeText(syllabus.grade) : ''), lines: lines.slice(0, 2), href: LANDING_PATH || undefined, hrefLabel: '시험범위 Wiki 보기 →' });
      }
      if (rows.length) {
        var countEl = body.querySelector('.featured-schoolcard-count');
        var count = countEl ? (countEl.textContent.match(/\d+/) || ['0'])[0] : String(rows.length);
        var firstRow = rows[0].textContent.replace(/[›»]/g, '').trim();
        var firstLink = rows[0].querySelector('a');
        var href = firstLink ? firstLink.getAttribute('href') : (LANDING_PATH || undefined);
        scenes.push({ tag: '출제 경향', head: school + ' 최근 시험 출제 경향', lines: ['출제자 분석 ' + count + '건', firstRow], href: href, hrefLabel: '분석 보기 →' });
      }
      var liveLink = pickLiveLink(school);
      if (liveLink && (liveLink.videoUrl || liveLink.embedUrl || liveLink.externalUrl)) scenes.push(videoScene(school, liveLink));
      else if (schoolVideos[school]) scenes.push({ tag: '수업 영상', head: school + ' 실제 수업', lines: [], video: schoolVideos[school] });
      if (!scenes.length) return [{ tag: '준비 중', head: school + ' 시험 준비', lines: ['출제 자료를 확인하고 있습니다'] }];
      return scenes;
    }

    function gradeText(g) {
      var m = String(g == null ? '' : g).match(/\d+/);
      return m ? m[0] + '학년' : '';
    }
    function videoScene(school, link) {
      if (link.videoUrl) return { tag: '수업 영상', head: school + ' 실제 수업', lines: [], video: link };
      var ytId = link.provider === 'YOUTUBE' ? youtubeIdFromEmbed(link.embedUrl) : null;
      if (ytId) return { tag: '수업 영상', head: school + ' 실제 수업', lines: [], embed: 'https://www.youtube-nocookie.com/embed/' + ytId + '?mute=1&playsinline=1&rel=0&modestbranding=1' };
      if (link.provider === 'NAVER' && link.embedUrl) return { tag: '수업 영상', head: school + ' 실제 수업', lines: [], embed: link.embedUrl + (link.embedUrl.indexOf('?') >= 0 ? '&' : '?') + 'autoPlay=false' };
      return { tag: '수업 영상', head: school + ' 실제 수업', lines: [], href: link.externalUrl || link.embedUrl, hrefLabel: '영상 보기 →' };
    }

    function renderScene(stage, scene) {
      stage.innerHTML = '';
      var tag = document.createElement('span'); tag.className = 'tn-hl-tag'; tag.textContent = scene.tag; stage.appendChild(tag);
      var head = document.createElement('b'); head.className = 'tn-hl-head'; head.textContent = scene.head; stage.appendChild(head);
      scene.lines.forEach(function (l) { var p = document.createElement('span'); p.className = 'tn-hl-line'; p.textContent = l; stage.appendChild(p); });
      if (scene.video) {
        var media = document.createElement('div'); media.className = 'tn-hl-media';
        var video = document.createElement('video'); video.className = 'tn-hl-video'; video.controls = true; video.muted = true; video.defaultMuted = true; video.playsInline = true; video.preload = 'none'; video.setAttribute('data-src', scene.video.videoUrl); if (scene.video.thumbnailUrl) video.poster = scene.video.thumbnailUrl; video.addEventListener('play', function () { pauseOtherVideos(video); }); media.appendChild(video); stage.appendChild(media);
      }
      if (scene.embed) {
        var box = document.createElement('div'); box.className = 'tn-hl-media';
        var frame = document.createElement('iframe'); frame.className = 'tn-hl-embed'; frame.title = '수업 영상'; frame.setAttribute('data-src', scene.embed); frame.setAttribute('loading', 'lazy'); frame.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture'); frame.setAttribute('allowfullscreen', ''); box.appendChild(frame); stage.appendChild(box);
      }
      if (scene.href) {
        var a = document.createElement('a'); a.className = 'tn-hl-link'; a.href = scene.href;
        if (/^https?:|^\//.test(scene.href) && (!LANDING_PATH || scene.href.indexOf(LANDING_PATH) === -1)) { a.target = '_blank'; a.rel = 'noopener'; }
        a.textContent = scene.hrefLabel || '자세히 →'; stage.appendChild(a);
      }
    }

    function upgradeCard(card) {
      if (card.dataset.tnHomeLive === 'true') return;
      var scenes = readScenes(card);
      if (scenes === 'pending' || scenes === null) return;
      card.dataset.tnHomeLive = 'true';
      var kind = card.querySelector('.featured-schoolcard-kind'); if (kind) kind.textContent = KIND_LABEL;
      var body = card.querySelector('[data-featured-card-body]'); body.innerHTML = '';
      var stage = document.createElement('div'); stage.className = 'tn-hl-stage'; body.appendChild(stage);
      var i = 0; renderScene(stage, scenes[i]);
      function prepareVisibleVideo(near) {
        var video = stage.querySelector('.tn-hl-video');
        if (video) { if (!near) { try { video.pause(); } catch (e) {} } else if (!video.getAttribute('src') && video.dataset.src) video.setAttribute('src', video.dataset.src); }
        var frame = stage.querySelector('.tn-hl-embed');
        if (frame) { if (!near) frame.setAttribute('src', 'about:blank'); else if ((!frame.getAttribute('src') || frame.getAttribute('src') === 'about:blank') && frame.dataset.src) { pauseOtherVideos(null); frame.setAttribute('src', frame.dataset.src); } }
      }
      if (reduceMotion() || scenes.length < 2) return;
      var timer = null; var running = false;
      function step() { if (!running) return; i = (i + 1) % scenes.length; stage.classList.add('is-swap'); timer = setTimeout(function () { renderScene(stage, scenes[i]); prepareVisibleVideo(true); stage.classList.remove('is-swap'); timer = setTimeout(step, SCENE_MS); }, 260); }
      function start() { if (running) return; running = true; timer = setTimeout(step, SCENE_MS); }
      function stop() { running = false; if (timer) { clearTimeout(timer); timer = null; } }
      if ('IntersectionObserver' in window) new IntersectionObserver(function (entries) { entries.forEach(function (e) { if (e.isIntersecting) { prepareVisibleVideo(true); start(); } else { prepareVisibleVideo(false); stop(); } }); }, { threshold: 0.2 }).observe(card);
      else start();
    }

    function scan() {
      // eduholic 호스트는 이 스크립트가 애초에 실행되지 않으므로 여기서는 검사하지 않는다.
      applyHeading();
      if (!videosReady) indexSchoolVideos();
      if (!videosReady) return;
      renderMainFeature();
      Array.prototype.slice.call(document.querySelectorAll('#sec-featured [data-featured-card]')).forEach(upgradeCard);
    }

    function injectStyle() {
      if (document.getElementById('tenantHomeLiveStyle')) return;
      var style = document.createElement('style'); style.id = 'tenantHomeLiveStyle';
      style.textContent = [
        '#sec-featured .featured-schoolcard-kind{display:inline-flex;align-items:center;gap:6px;}',
        '#sec-featured .tn-hl-stage{display:flex;flex-direction:column;gap:6px;min-height:132px;transition:opacity .24s ease, transform .24s ease;}',
        '#sec-featured .tn-hl-stage.is-swap{opacity:0;transform:translateY(6px);}',
        '#sec-featured .tn-hl-tag{align-self:flex-start;font-size:11px;font-weight:800;letter-spacing:.02em;padding:3px 9px;border-radius:999px;background:#eaf0fe;color:#2b3a9e;}',
        '#sec-featured .tn-hl-head{font-size:15px;font-weight:800;letter-spacing:-.02em;color:#0a0a0c;}',
        '#sec-featured .tn-hl-line{font-size:13px;line-height:1.55;color:#67707c;}',
        '#sec-featured .tn-hl-link{margin-top:4px;align-self:flex-start;font-size:12.5px;font-weight:800;color:#1d4ed8;text-decoration:none;}',
        '#sec-featured .tn-hl-link:focus-visible{outline:3px solid #93c5fd;outline-offset:3px;}',
        '#sec-featured .tn-hl-media{width:100%;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:#090d18;}',
        '#sec-featured .tn-hl-video{display:block;width:100%;height:100%;object-fit:contain;background:#090d18;}',
        '#sec-featured .tn-hl-embed{display:block;width:100%;height:100%;border:0;background:#090d18;}',
        '#sec-featured .tn-main-feature{width:min(100%,980px);margin:34px auto 44px;}',
        '#sec-featured .tn-main-feature__media{width:100%;aspect-ratio:16/9;overflow:hidden;border-radius:24px;background:#090d18;box-shadow:0 24px 60px rgba(15,23,42,.18);}',
        '#sec-featured .tn-main-feature__video{display:block;width:100%;height:100%;border:0;object-fit:contain;background:#090d18;}',
        '#sec-featured .tn-main-feature__copy{display:grid;gap:6px;margin-bottom:12px;text-align:left;}',
        '#sec-featured .tn-main-feature__copy strong{font-size:20px;line-height:1.35;color:#111827;}',
        '#sec-featured .tn-main-feature__note-line{display:block;}',
        '#sec-featured .tn-main-feature__copy small{font-size:14px;line-height:1.65;color:#67707c;}',
        '#sec-featured .tn-main-feature__note{margin:0;display:grid;gap:6px;font-size:12px;line-height:1.6;color:#8a929c;word-break:keep-all;overflow-wrap:anywhere;}',
        '#sec-featured .tn-main-feature__schools-title{width:min(100%,980px);margin:0 auto 18px;font-size:20px;line-height:1.35;color:#111827;}',
        '@media(max-width:768px){#sec-featured .tn-main-feature{margin:26px auto 34px;}#sec-featured .tn-main-feature__media{border-radius:16px;box-shadow:0 14px 34px rgba(15,23,42,.16);}#sec-featured .tn-main-feature__copy{margin-bottom:10px;}#sec-featured .tn-main-feature__copy strong,#sec-featured .tn-main-feature__schools-title{font-size:18px;}}',
        '@media (prefers-reduced-motion:reduce){#sec-featured .tn-hl-stage{transition:none;}}'
      ].join('');
      document.head.appendChild(style);
    }

    function boot() {
      injectStyle();
      indexSchoolVideos();
      loadCampaign(function () {
        Array.prototype.slice.call(document.querySelectorAll('#sec-featured [data-featured-card]')).forEach(function (c) { delete c.dataset.tnHomeLive; });
        scan();
      });
      scan();
      var pv = document.getElementById('publicView');
      if (pv && window.MutationObserver) {
        var mo = new MutationObserver(function () { scan(); });
        mo.observe(pv, { childList: true, subtree: true });
      }
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
    else boot();
  }
})();
