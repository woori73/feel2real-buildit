/* 압구정에듀홀릭 홈페이지 — 「2학기 영어내신」(#sec-featured) 를 "2학기 영어 내신 LIVE" 로 전환한다.
 *
 * 목적(작업지시서 §2·§8~§12):
 *  - 섹션 제목/부제를 "2학기 영어 내신 LIVE" 로 바꾸고, 학교 카드를 작은 LIVE window 로 만든다.
 *  - 카드 내부 scene: 시험범위 Wiki → 최근 시험 출제 경향 → 실제 수업 영상.
 *    각 단계는 **실제로 서로 다른 데이터가 존재할 때만** 만든다(가짜 scene 금지).
 *  - 시험범위 Wiki = 캠페인 showcase(서버가 Wiki 자료에서 추출한 이번 학기 시험범위 텍스트).
 *  - 출제 경향 = academy_public.js 가 이미 채운 카드 본문(출제자 분석) 재사용 — 카드마다
 *    재조회하지 않는다(N+1 금지).
 *  - showcase 의 sourceId 가 'final-'(분석 완성본)이면 출제 경향과 사실상 동일 자료이므로
 *    별도 시험범위 scene 을 만들지 않는다(source identity 비교, §2).
 *
 * 실제 수업 영상 scene 의 소스 (우선순위):
 *  1) 그 학교/학년에 **명시적으로 연결된** 내신 LIVE 영상(midterm_live_video_links) = source of truth.
 *     캠페인 스코프 /live-videos 를 1회만 조회한다(카드마다 fetch 아님). 서버가 visible + READY +
 *     소유 + school/grade 를 이미 판정했다. 직접 업로드는 인라인 <video>, YouTube·네이버TV 는
 *     카드 안 embed player, embed 불가한 외부 링크만 "영상 보기" 링크.
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
  // 캠페인 스코프 조회 2개(카드마다가 아님 — N+1 아님). 서버가 visible/READY/소유/scope 를 판정.
  var LIVE_VIDEOS_API = '/api/academies/eduholic/midterm-campaign/live-videos';
  var SHOWCASE_API = '/api/academies/eduholic/midterm-campaign/showcase';
  var SCENE_MS = 4500;
  var schoolVideos = {};        // school → 파일명으로 매칭된 LESSON 영상(fallback, #249)
  var videosReady = false;      // F2RAcademyPublicVideos 색인 완료 여부

  if (window.location.hostname !== EDUHOLIC_HOST) return;
  if (window.location.pathname === LANDING_PATH) return;

  // school → 명시적으로 연결된 내신 LIVE 영상 link 배열 / 시험범위 showcase scene.
  // null = 미조회, {} = 조회 완료·없음.
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
          if (k && !map[k]) map[k] = s;      // 학교당 첫(대표) scene
        });
        showcaseBySchool = map;
      })
      .catch(function () { showcaseBySchool = {}; })
      .then(tick);
  }

  /** 그 학교의 대표 LIVE 링크(서버 정렬 sort_order → link_id 의 첫 항목). 없으면 null. */
  function pickLiveLink(school) {
    var list = liveVideosBySchool && liveVideosBySchool[school];
    return list && list.length ? list[0] : null;
  }

  /**
   * MAIN FEATURE는 카드 정렬을 바꾸지 않고 신구중 3학년에 명시 연결된 첫 LIVE 자산을 쓴다.
   * 파일명/길이로 추론하지 않으며 관리자 sort_order가 대표 자산 선택의 source of truth다.
   */
  function pickMainFeatureLink() {
    var entries = [];
    Object.keys(liveVideosBySchool || {}).forEach(function (school) {
      (liveVideosBySchool[school] || []).forEach(function (link) { entries.push(link); });
    });
    return entries.find(function (link) {
      return normalizedSchool(link && link.school) === normalizedSchool('신구중')
        && Number(String(link && link.grade || '').replace(/[^0-9]/g, '')) === 3
        && !!(link.videoUrl || link.embedUrl || link.externalUrl);
    }) || null;
  }

  function renderMainFeature() {
    var section = document.getElementById('sec-featured');
    var head = section && section.querySelector('.featured-head');
    if (!section || !head || section.querySelector('[data-midterm-main-feature]')) return;
    var link = pickMainFeatureLink();
    if (!link) return;

    var scene = videoScene('신구중3', link);
    var feature = document.createElement('article');
    feature.className = 'mt-main-feature';
    feature.setAttribute('data-midterm-main-feature', '');
    var media = document.createElement('div');
    media.className = 'mt-main-feature__media';

    if (scene.video) {
      var video = document.createElement('video');
      video.className = 'mt-main-feature__video';
      video.controls = true;
      video.playsInline = true;
      video.preload = 'metadata';
      video.setAttribute('aria-label', '신구중3 2학기 내신 대비 실제 제작 과정');
      video.src = scene.video.videoUrl;
      if (scene.video.thumbnailUrl) video.poster = scene.video.thumbnailUrl;
      video.addEventListener('play', function () { pauseOtherVideos(video); });
      media.appendChild(video);
    } else if (scene.embed) {
      var frame = document.createElement('iframe');
      frame.className = 'mt-main-feature__embed';
      frame.title = '신구중3 2학기 내신 대비 실제 제작 과정';
      frame.src = scene.embed;
      frame.loading = 'lazy';
      frame.setAttribute('allow', 'encrypted-media; picture-in-picture');
      frame.setAttribute('allowfullscreen', '');
      media.appendChild(frame);
    } else {
      var mediaLink = document.createElement('a');
      mediaLink.className = 'mt-main-feature__link';
      mediaLink.href = scene.href;
      mediaLink.target = '_blank';
      mediaLink.rel = 'noopener';
      mediaLink.textContent = '53초 제작 영상 보기 →';
      media.appendChild(mediaLink);
    }
    feature.appendChild(media);

    var copy = document.createElement('div');
    copy.className = 'mt-main-feature__copy';
    copy.innerHTML = '<strong>신구중3 실제 제작 과정 · 53초</strong>'
      + '<span>학교 자료 × Digital Twin × AI</span>'
      + '<small>학교 프린트와 수업 Insight를 읽고, 출제 패턴을 분석해 예상문제·정답·해설을 만듭니다.</small>';
    feature.appendChild(copy);
    head.insertAdjacentElement('afterend', feature);

    var cards = section.querySelector('[data-featured-cards]');
    if (cards && !section.querySelector('.mt-main-feature__schools-title')) {
      var schoolsTitle = document.createElement('h3');
      schoolsTitle.className = 'mt-main-feature__schools-title';
      schoolsTitle.textContent = '학교별 내신 대비';
      cards.insertAdjacentElement('beforebegin', schoolsTitle);
    }
  }

  /** 그 학교의 시험범위 showcase — 실제 추출 내용이 있고 분석 완성본이 아닐 때만(§2). */
  function pickSyllabus(school) {
    var s = showcaseBySchool && showcaseBySchool[school];
    if (!s) return null;
    if (String(s.sourceId || '').indexOf('final-') === 0) return null;   // 출제 경향과 동일 자료.
    var lesson = String(s.lesson || '').trim();
    var grammar = (Array.isArray(s.grammarPoints) ? s.grammarPoints : []).filter(Boolean);
    var sentences = (Array.isArray(s.sampleSentences) ? s.sampleSentences : []).filter(Boolean);
    if (!lesson && !grammar.length && !sentences.length) return null;     // 추출 내용 없음 → scene 없음.
    return { lesson: lesson, grammar: grammar, sentences: sentences, grade: s.grade };
  }

  function youtubeIdFromEmbed(embedUrl) {
    var m = String(embedUrl || '').match(/\/embed\/([A-Za-z0-9_-]{6,20})/);
    return m ? m[1] : null;
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

    var desc = head.querySelector('.featured-desc');
    if (desc) {
      desc.textContent = '학교별 실제 자료를 읽고, 학교에 맞는 예상문제를 만듭니다.';
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

  // 동시에 여러 학교 영상 재생 금지(§3·§14) — 다른 카드의 <video> 는 멈추고 embed 는 src 를 비운다.
  function pauseOtherVideos(active) {
    Array.prototype.slice.call(document.querySelectorAll('#sec-featured .mt-hl-video')).forEach(function (video) {
      if (video !== active) { try { video.pause(); } catch (e) {} }
    });
    Array.prototype.slice.call(document.querySelectorAll('#sec-featured .mt-hl-embed')).forEach(function (frame) {
      if (frame.getAttribute('src') && frame.getAttribute('src') !== 'about:blank') {
        frame.setAttribute('src', 'about:blank');
      }
    });
  }

  /**
   * scene 배열 구성 — 시험범위 Wiki → 출제 경향 → 실제 수업 영상. 각 단계는 실제로 서로 다른
   * 데이터가 있을 때만 넣는다(§2). 출제 경향 데이터는 academy_public.js 가 이미 채운 카드
   * 본문의 분석 행에서만 읽는다(재조회 없음).
   */
  function readScenes(card) {
    var school = (card.getAttribute('data-school') || '').trim();
    var body = card.querySelector('[data-featured-card-body]');
    if (!body) return null;

    if (body.querySelector('.featured-schoolcard-loading')) return 'pending';
    var rows = Array.prototype.slice.call(body.querySelectorAll('.featured-schoolcard-row'));
    var empty = !!body.querySelector('.featured-schoolcard-empty');
    if (!rows.length && !empty) return null;

    var scenes = [];

    // 1) 시험범위 Wiki — 캠페인 showcase(추출된 이번 학기 시험범위). 없으면 이 scene 을 만들지 않는다.
    var syllabus = pickSyllabus(school);
    if (syllabus) {
      var lines = [];
      if (syllabus.lesson) lines.push(syllabus.lesson);
      if (syllabus.grammar.length) lines.push('핵심 문법 · ' + syllabus.grammar.slice(0, 2).join(' · '));
      if (!lines.length && syllabus.sentences.length) lines.push(syllabus.sentences[0]);
      scenes.push({
        tag: '시험범위', head: school + (syllabus.grade ? ' · ' + gradeText(syllabus.grade) : ''),
        lines: lines.slice(0, 2),
        href: MIDTERM_LIVE_URL, hrefLabel: '시험범위 Wiki 보기 →'
      });
    }

    // 2) 최근 시험 출제 경향 — academy_public.js 가 채운 분석 행. 없으면 만들지 않는다.
    if (rows.length) {
      var countEl = body.querySelector('.featured-schoolcard-count');
      var count = countEl ? (countEl.textContent.match(/\d+/) || ['0'])[0] : String(rows.length);
      var firstRow = rows[0].textContent.replace(/[›»]/g, '').trim();
      var firstLink = rows[0].querySelector('a');
      var href = firstLink ? firstLink.getAttribute('href') : MIDTERM_LIVE_URL;
      scenes.push({ tag: '출제 경향', head: school + ' 최근 시험 출제 경향',
        lines: ['출제자 분석 ' + count + '건', firstRow],
        href: href, hrefLabel: '분석 보기 →' });
    }

    // 3) 실제 수업 영상 — 명시적 LIVE 연결(source of truth) 우선, 없으면 파일명 매칭 fallback.
    //    embed 가능(YouTube / 네이버TV)하면 카드 안 player, 아니면 링크 카드.
    var liveLink = pickLiveLink(school);
    if (liveLink && (liveLink.videoUrl || liveLink.embedUrl || liveLink.externalUrl)) {
      scenes.push(videoScene(school, liveLink));
    } else if (schoolVideos[school]) {
      scenes.push({ tag: '수업 영상', head: school + ' 실제 수업', lines: [], video: schoolVideos[school] });
    }

    if (!scenes.length) {
      return [{ tag: '준비 중', head: school + ' 시험 준비', lines: ['출제 자료를 확인하고 있습니다'] }];
    }
    return scenes;
  }

  function gradeText(g) {
    var m = String(g == null ? '' : g).match(/\d+/);
    return m ? m[0] + '학년' : '';
  }

  function videoScene(school, link) {
    if (link.videoUrl) {
      return { tag: '수업 영상', head: school + ' 실제 수업', lines: [], video: link };
    }
    var ytId = link.provider === 'YOUTUBE' ? youtubeIdFromEmbed(link.embedUrl) : null;
    if (ytId) {
      return { tag: '수업 영상', head: school + ' 실제 수업', lines: [],
        embed: 'https://www.youtube-nocookie.com/embed/' + ytId + '?mute=1&playsinline=1&rel=0&modestbranding=1' };
    }
    if (link.provider === 'NAVER' && link.embedUrl) {
      return { tag: '수업 영상', head: school + ' 실제 수업', lines: [],
        embed: link.embedUrl + (link.embedUrl.indexOf('?') >= 0 ? '&' : '?') + 'autoPlay=false' };
    }
    return { tag: '수업 영상', head: school + ' 실제 수업', lines: [],
      href: link.externalUrl || link.embedUrl, hrefLabel: '영상 보기 →' };
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
    if (scene.embed) {
      // 카드 안 embed player(YouTube / 네이버TV). lazy: 보일 때 data-src → src, 안 보이면 src 비움.
      var box = document.createElement('div');
      box.className = 'mt-hl-media';
      var frame = document.createElement('iframe');
      frame.className = 'mt-hl-embed';
      frame.title = '수업 영상';
      frame.setAttribute('data-src', scene.embed);
      frame.setAttribute('loading', 'lazy');
      frame.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture');
      frame.setAttribute('allowfullscreen', '');
      box.appendChild(frame); stage.appendChild(box);
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
    // 보이는 scene 의 영상/embed 만 로드/재생하고, 안 보이면 정지한다(§3·§14 — lazy·offscreen pause).
    function prepareVisibleVideo(near) {
      var video = stage.querySelector('.mt-hl-video');
      if (video) {
        if (!near) { try { video.pause(); } catch (e) {} }
        else if (!video.getAttribute('src') && video.dataset.src) {
          video.setAttribute('src', video.dataset.src);
        }
      }
      var frame = stage.querySelector('.mt-hl-embed');
      if (frame) {
        if (!near) frame.setAttribute('src', 'about:blank');
        else if ((!frame.getAttribute('src') || frame.getAttribute('src') === 'about:blank')
          && frame.dataset.src) {
          pauseOtherVideos(null);          // 동시 재생 금지 — 다른 카드 영상/embed 정지
          frame.setAttribute('src', frame.dataset.src);
        }
      }
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
      '#sec-featured .mt-hl-embed{display:block;width:100%;height:100%;border:0;background:#090d18;}',
      '#sec-featured .mt-main-feature{width:min(100%,980px);margin:34px auto 44px;}',
      '#sec-featured .mt-main-feature__media{width:100%;aspect-ratio:16/9;overflow:hidden;border-radius:24px;',
      '  background:#090d18;box-shadow:0 24px 60px rgba(15,23,42,.18);}',
      '#sec-featured .mt-main-feature__video,#sec-featured .mt-main-feature__embed{display:block;width:100%;',
      '  height:100%;border:0;object-fit:contain;background:#090d18;}',
      '#sec-featured .mt-main-feature__link{display:flex;width:100%;height:100%;align-items:center;justify-content:center;',
      '  color:#fff;font-weight:800;text-decoration:none;}',
      '#sec-featured .mt-main-feature__copy{display:grid;gap:5px;margin-top:18px;text-align:left;}',
      '#sec-featured .mt-main-feature__copy strong{font-size:20px;line-height:1.35;color:#111827;}',
      '#sec-featured .mt-main-feature__copy span{font-size:14px;font-weight:800;color:#2b3a9e;}',
      '#sec-featured .mt-main-feature__copy small{font-size:14px;line-height:1.65;color:#67707c;}',
      '#sec-featured .mt-main-feature__schools-title{width:min(100%,980px);margin:0 auto 18px;font-size:20px;',
      '  line-height:1.35;color:#111827;}',
      '@media(max-width:768px){#sec-featured .mt-main-feature{margin:26px auto 34px;}',
      '  #sec-featured .mt-main-feature__media{border-radius:16px;box-shadow:0 14px 34px rgba(15,23,42,.16);}',
      '  #sec-featured .mt-main-feature__copy{margin-top:14px;}',
      '  #sec-featured .mt-main-feature__copy strong,#sec-featured .mt-main-feature__schools-title{font-size:18px;}}',
      '@media (prefers-reduced-motion:reduce){#sec-featured .mt-hl-stage{transition:none;}}'
    ].join('');
    document.head.appendChild(style);
  }

  function start() {
    injectStyle();
    indexSchoolVideos();
    // 시험범위 showcase + 명시적 LIVE 연결을 캠페인 스코프로 각각 1회만 조회한다(카드마다 fetch
    // 아님). 도착하면 이미 그린 카드도 표식을 지우고 한 번 더 올려 시험범위/영상 scene 을 더한다.
    loadCampaign(function () {
      Array.prototype.slice
        .call(document.querySelectorAll('#sec-featured [data-featured-card]'))
        .forEach(function (c) { delete c.dataset.mtHomeLive; });
      renderMainFeature();
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
