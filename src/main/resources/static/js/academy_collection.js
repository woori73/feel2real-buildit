/**
 * Horizontal Content Collection — 배선(interaction) 전용. 공개 학원 페이지(academy_public.js)가 소비한다.
 *
 * 마크업 정본은 academy_sections.js(renderCollectionShell / programCardHtml / videoCardHtml)이고,
 * 이 파일은 그 위에 "사용자가 직접 둘러보는" 상호작용만 얹는다.
 *
 *   Navigation  — 좌/우 화살표는 track.scrollBy(x, y)(레거시 시그니처)로 한 화면 남기고 이동.
 *                 부드러움은 CSS `.f2r-collection-track{scroll-behavior}` 가 담당.
 *   Progress    — 스크롤 위치 → "선두 카드 번호 / 전체" 카운터(NN / MM).
 *   Focus       — 뷰포트 가장자리에서 절반 이상 잘린 카드는 `is-dimmed`(미세 축소·감광)로 shelf 느낌.
 *   Playback    — [data-collection-play] 클릭 시, 감싸는 .f2r-media-viewport "안에" 플레이어를
 *                 덧붙이고 트리거를 숨긴다(치환하지 않는다). 뷰포트가 크기·비율·overflow 를
 *                 소유하므로 재생해도 외곽 크기가 바뀌지 않는다. Actual Class(Phone Card)와
 *                 대표 수업(Program Card) 영상이 같은 계약을 공유한다. 업로드 → <video>,
 *                 임베드 URL → <iframe>. 원본 비율은 CSS object-fit:contain 으로 보존(crop 금지).
 *   Affordance  — [data-collection-auto] (V2-11): 첫 진입 시 이 영역이 "옆으로 더 있다"는 걸
 *                 실제 움직임으로 알린다. "hint" = 다음 카드 쪽으로 살짝 밀었다 복귀(Actual Class /
 *                 Mini Lectures, 1회). "advance" = Card1 을 읽을 시간 → Card2 → … → 마지막 카드
 *                 에서 정지(처음으로 복귀 안 함, Programs). 무한 캐러셀 아님. 사용자 입력
 *                 (pointerdown/touch/wheel/key) 또는 하위 영상 재생 → 즉시 중단·재개 없음.
 *                 진입 신호는 IO 하나에 의존하지 않는다 — sceneFlow data-scene=active +
 *                 IntersectionObserver(낮은 threshold) + guarded timeout(getBoundingClientRect).
 *                 위치는 scrollLeft 를 동기적으로 세팅(화살표 네비와 동일). 부드러움은 CSS
 *                 scroll-behavior. prefers-reduced-motion → 실행 안 함. 데스크톱 4-카드
 *                 그리드면 스크롤이 없어 no-op.
 *
 * 규칙(V2)
 *   - Story(3-A)와 분리: 등장 애니메이션 없음. 자동 이동은 "affordance 1회"뿐(무한 캐러셀 금지).
 *   - 세로 마우스 휠을 가로로 강제 변환하지 않는다(native overflow-x 우선, trackpad 가로 스크롤 그대로).
 *   - 새 제스처/캐러셀 라이브러리 없음. 모바일 스와이프는 CSS overflow-x + scroll-snap 이 담당.
 *   - 페이지 자체가 가로로 밀리지 않게: viewport 가 track 을 자르고 track 은 overscroll-behavior-x:contain.
 *   - 매 scroll 마다 layout write 없음 — read 만, rAF 1회로 배칭. transform/opacity 만 애니메이트.
 *   - 모듈이 로드되지 않으면 `html.js-collection` 도 안 붙어 카드가 축소 없이 정적 shelf 로 완전히 보인다.
 *   - prefers-reduced-motion: CSS media query 에서 축소/트랜지션 제거.
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (!doc) return;

  function collectionOf(node) {
    return node && node.closest ? node.closest('.f2r-collection') : null;
  }

  function pad2(n) { return n < 10 ? '0' + n : String(n); }

  /** 카드 stride(연속 카드 왼쪽 간격 = 카드폭 + gap)와 개수. */
  function metrics(track) {
    var cards = track.querySelectorAll('.f2r-collection-card');
    if (cards.length < 2) {
      return { stride: cards[0] ? cards[0].offsetWidth : Math.round(track.clientWidth * 0.8), count: cards.length, cards: cards };
    }
    return { stride: cards[1].offsetLeft - cards[0].offsetLeft, count: cards.length, cards: cards };
  }

  function perView(track, stride) {
    return stride > 0 ? Math.max(1, Math.floor((track.clientWidth + 1) / stride)) : 1;
  }

  /** 선두(왼쪽 기준) 카드 인덱스. 진행 카운터와 스냅 의미가 일치한다. */
  function leadIndex(track, m) {
    if (!m.stride) return 0;
    return Math.max(0, Math.min(m.count - 1, Math.round(track.scrollLeft / m.stride)));
  }

  function update(collection) {
    if (!collection || collection.hasAttribute('data-collection-static')) return;
    var track = collection.querySelector('[data-collection-track]');
    if (!track) return;
    var m = metrics(track);
    var lead = leadIndex(track, m);
    var vw = track.clientWidth;
    var sl = track.scrollLeft;

    for (var i = 0; i < m.cards.length; i += 1) {
      var card = m.cards[i];
      var left = card.offsetLeft - sl;
      var w = card.offsetWidth || 1;
      var seen = (Math.min(left + w, vw) - Math.max(left, 0)) / w;
      card.classList.toggle('is-dimmed', seen < 0.6);
      card.classList.toggle('is-active', i === lead);
    }

    var cur = collection.querySelector('[data-collection-current]');
    var tot = collection.querySelector('[data-collection-total]');
    if (cur) cur.textContent = pad2(lead + 1);
    if (tot) tot.textContent = pad2(m.count);

    var max = track.scrollWidth - track.clientWidth - 1;
    var prev = collection.querySelector('[data-collection-scroll="prev"]');
    var next = collection.querySelector('[data-collection-scroll="next"]');
    if (prev) prev.disabled = sl <= 0;
    if (next) next.disabled = sl >= max;
  }

  function eachCollection(root, fn) {
    var scope = root && root.querySelectorAll ? root : doc;
    Array.prototype.forEach.call(scope.querySelectorAll('.f2r-collection:not([data-collection-static])'), fn);
  }

  var RO = typeof global.ResizeObserver === 'function'
    ? new global.ResizeObserver(function (entries) {
        for (var i = 0; i < entries.length; i += 1) {
          var c = collectionOf(entries[i].target);
          if (c) update(c);
        }
      })
    : null;

  function init(root) {
    eachCollection(root, function (collection) {
      var track = collection.querySelector('[data-collection-track]');
      if (!track || track.dataset.collectionReady === 'true') return;
      track.dataset.collectionReady = 'true';

      var scheduled = false;
      track.addEventListener('scroll', function () {
        if (scheduled) return;
        scheduled = true;
        var run = function () { scheduled = false; update(collection); };
        if (typeof global.requestAnimationFrame === 'function') global.requestAnimationFrame(run);
        else run();
      }, { passive: true });

      if (RO) RO.observe(track);
      // 썸네일이 늦게 로드되면 stride/scrollWidth 가 바뀐다 — 로드 시점에 한 번 더 맞춘다(자기 예약 루프 아님).
      Array.prototype.forEach.call(track.querySelectorAll('img'), function (img) {
        if (!img.complete) img.addEventListener('load', function () { update(collection); }, { once: true });
      });

      update(collection);
      if (typeof global.requestAnimationFrame === 'function') {
        global.requestAnimationFrame(function () { update(collection); });
      }
    });
  }

  function onArrowClick(event) {
    var btn = event.target && event.target.closest
      ? event.target.closest('[data-collection-scroll]') : null;
    if (!btn) return;
    var collection = collectionOf(btn);
    var track = collection ? collection.querySelector('[data-collection-track]') : null;
    if (!track) return;
    event.preventDefault();
    var m = metrics(track);
    var pv = perView(track, m.stride);
    var dir = btn.dataset.collectionScroll === 'prev' ? -1 : 1;
    // 레거시 2-인자 scrollBy(x, y). 옵션-딕셔너리 형태는 일부 Chromium 에서 overflow 컨테이너 대상일 때
    // 무시된다(레거시 시그니처는 정상). 한 번에 "보이는 카드 수 - 1"장 이동 — 맥락 유지. 네이티브가 clamp.
    track.scrollBy(dir * m.stride * Math.max(1, pv - 1), 0);
    // scroll 이벤트가 늦거나 누락되는 환경 대비 — 다음 프레임에 상태를 직접 맞춘다.
    if (typeof global.requestAnimationFrame === 'function') {
      global.requestAnimationFrame(function () { update(collection); });
    }
  }

  function onPlayClick(event) {
    var trigger = event.target && event.target.closest
      ? event.target.closest('[data-collection-play]') : null;
    if (!trigger) return;
    event.preventDefault();

    // Media Viewport 계약: 뷰포트(크기·비율·overflow·radius 소유)는 그대로 두고, 그 "안에서"
    // 썸네일/버튼을 플레이어로 교체한다. 트리거를 replaceWith 하면 뷰포트가 사라져 플레이어가
    // 부모 없이(또는 뷰포트 밖) 원본 해상도로 렌더되어 프레임을 벗어난다(V2-8 이전 버그).
    var viewport = trigger.closest ? trigger.closest('.f2r-media-viewport') : null;
    if (!viewport) viewport = trigger.parentNode;
    if (!viewport) return;
    if (viewport.querySelector('.f2r-media-player')) return; // 이미 재생 중

    var embed = trigger.dataset.videoEmbed;
    var src = trigger.dataset.videoSrc;
    var node;
    if (embed) {
      node = doc.createElement('iframe');
      node.src = embed + (embed.indexOf('?') === -1 ? '?' : '&') + 'autoplay=1&playsinline=1&rel=0';
      node.setAttribute('title', '수업 영상');
      node.setAttribute('loading', 'lazy');
      node.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share');
      node.setAttribute('allowfullscreen', '');
    } else if (src) {
      node = doc.createElement('video');
      node.src = src;
      if (trigger.dataset.videoPoster) node.poster = trigger.dataset.videoPoster;
      node.controls = true;
      node.autoplay = true;
      node.playsInline = true;
      node.setAttribute('playsinline', '');
    } else {
      return;
    }
    node.className = 'f2r-media-player';
    viewport.appendChild(node);
    trigger.hidden = true;
  }

  // ── Horizontal Affordance (V2-11 corrective) ─────────────────────────────
  // 첫 진입 시 "옆으로 더 있다"를 실제 움직임으로 알린다. 무한 캐러셀 아님.
  //   hint    = 다음 카드 쪽으로 살짝 밀었다 복귀(Actual Class / Mini Lectures) — 1회.
  //   advance = Card1 을 읽을 시간 → Card2 → … → 마지막 카드에서 정지(처음으로 복귀 안 함, Programs).
  // 사용자 입력(pointerdown/touch/wheel/key) 또는 하위 영상 재생 → 즉시 중단, 재개 없음.
  // prefers-reduced-motion → 아예 실행 안 함. 데스크톱에서 카드가 한 화면에 다 보이면(그리드)
  //   스크롤할 게 없어 runAuto 가 조용히 no-op.
  //
  // 진입 신호를 IO 하나에 의존하지 않는다(#7): (1) V2-10 sceneFlow 의 data-scene=active,
  //   (2) IntersectionObserver(낮은 threshold), (3) guarded timeout + getBoundingClientRect.
  var REDUCED = typeof global.matchMedia === 'function'
    && global.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // 위치를 동기적으로 세팅한다(어느 환경에서든 카드가 실제로 이동한다 — 배경 탭/rAF throttle
  // 에도 영향 없음). 부드러움은 CSS .f2r-collection-track{scroll-behavior:smooth} 가 담당한다
  // (지원 안 하면 즉시 점프 — 그래도 카드는 움직여서 "가로 컬렉션"이라는 걸 알린다).
  // 화살표 네비게이션(onArrowClick)이 쓰는 것과 같은 방식.
  function autoScrollTo(track, left) {
    left = Math.max(0, Math.round(left));
    track.scrollLeft = left;
  }

  function anyVideoPlaying(track) {
    var vids = track.getElementsByTagName('video');
    for (var i = 0; i < vids.length; i += 1) if (!vids[i].paused && !vids[i].ended) return true;
    return false;
  }

  function runAuto(collection) {
    var track = collection.querySelector('[data-collection-track]');
    if (!track || track.dataset.autoDone === 'true') return;
    track.dataset.autoDone = 'true';
    if (anyVideoPlaying(track)) return;                 // 재생 중이면 시작하지 않는다
    var mode = collection.getAttribute('data-collection-auto');
    var m = metrics(track);
    var stride = m.stride || Math.round(track.clientWidth * 0.85);
    var maxLeft = Math.max(0, track.scrollWidth - track.clientWidth - 1);
    if (maxLeft < 12 || stride < 12) return;            // 스크롤할 게 없으면(그리드/짧은 목록) no-op

    var timers = [];
    var cancelled = false;
    function after(ms, fn) { timers.push(global.setTimeout(function () { if (!cancelled) fn(); }, ms)); }
    var stopEvents = ['pointerdown', 'touchstart', 'wheel', 'keydown'];
    function cancel() {
      if (cancelled) return;
      cancelled = true;
      for (var i = 0; i < timers.length; i += 1) global.clearTimeout(timers[i]);
      timers = [];
      stopEvents.forEach(function (t) { track.removeEventListener(t, cancel); });
      track.removeEventListener('play', cancel, true);
    }
    // 사용자 직접 입력 → 즉시 양보(스크롤 이벤트가 아니라 입력만 본다). 하위 <video> 재생 → 중단.
    stopEvents.forEach(function (t) { track.addEventListener(t, cancel, { passive: true }); });
    track.addEventListener('play', cancel, true);

    if (mode !== 'advance') {
      // hint — 다음 카드 쪽으로 살짝 밀었다 복귀(1회)
      var peek = Math.min(Math.round(stride * 0.16), maxLeft);
      after(400, function () { autoScrollTo(track, peek); });
      after(1100, function () { autoScrollTo(track, 0); });
      after(1700, cancel);
      return;
    }

    // advance — Card1 을 먼저 읽을 시간 → 2 → 3 → 4 … 마지막 카드에서 정지(복귀 없음)
    var count = Math.min(m.count || 0, 6);
    if (count < 2) { cancel(); return; }
    var dwell = 2600;
    var t = 1700;
    for (var c = 1; c < count; c += 1) {
      (function (idx) {
        after(t, function () { autoScrollTo(track, Math.min(idx * stride, maxLeft)); });
      })(c);
      t += dwell;
    }
    after(t + 200, cancel);
  }

  function sectionOf(node) {
    var n = node;
    while (n && n.nodeType === 1) {
      if (n.tagName === 'SECTION' && (n.getAttribute('data-scene') != null || n.id)) return n;
      n = n.parentNode;
    }
    return null;
  }

  function initAuto(root) {
    if (REDUCED) return;
    var scope = root && root.querySelectorAll ? root : doc;
    Array.prototype.forEach.call(
      scope.querySelectorAll('.f2r-collection[data-collection-auto]:not([data-collection-static])'),
      function (collection) {
        var track = collection.querySelector('[data-collection-track]');
        if (!track || track.dataset.autoReady === 'true') return;
        track.dataset.autoReady = 'true';

        var started = false;
        var mo = null;
        var io = null;
        var fb1 = null;
        var fb2 = null;
        function teardown() {
          if (mo) mo.disconnect();
          if (io) io.disconnect();
          global.clearTimeout(fb1);
          global.clearTimeout(fb2);
        }
        function start() {
          if (started) return;
          started = true;
          teardown();
          runAuto(collection);
        }
        function inView(minRatio) {
          var r = collection.getBoundingClientRect();
          var vh = global.innerHeight || (doc.documentElement && doc.documentElement.clientHeight) || 800;
          if (r.bottom <= 0 || r.top >= vh) return false;
          var vis = Math.min(r.bottom, vh) - Math.max(r.top, 0);
          return vis >= Math.min(r.height, vh) * (minRatio || 0.3);
        }

        // (1) V2-10 sceneFlow — 섹션이 active 가 되는 신호(실기기에서 검증됨)
        var section = sectionOf(collection);
        if (section && typeof global.MutationObserver === 'function') {
          mo = new global.MutationObserver(function () {
            if (section.getAttribute('data-scene') === 'active') start();
          });
          mo.observe(section, { attributes: true, attributeFilter: ['data-scene'] });
          if (section.getAttribute('data-scene') === 'active') start();
        }
        // (2) IntersectionObserver — 낮은 threshold, 여러 신호 중 하나
        if (typeof global.IntersectionObserver === 'function') {
          io = new global.IntersectionObserver(function (entries) {
            for (var i = 0; i < entries.length; i += 1) {
              if (entries[i].isIntersecting && entries[i].intersectionRatio >= 0.25) { start(); return; }
            }
          }, { threshold: [0, 0.12, 0.25, 0.5] });
          io.observe(collection);
        }
        // (3) guarded timeout — IO/scene 둘 다 안 와도 rect 로 직접 판정(스크롤 리스너 아님)
        fb1 = global.setTimeout(function () {
          if (started) return;
          if (inView(0.3)) start();
          else fb2 = global.setTimeout(function () { if (!started && inView(0.3)) start(); }, 4000);
        }, 1800);
      }
    );
  }

  var wired = false;
  function wire(root) {
    if (!wired) {
      wired = true;
      if (doc.documentElement && doc.documentElement.classList) {
        doc.documentElement.classList.add('js-collection');
      }
      doc.addEventListener('click', onArrowClick);
      doc.addEventListener('click', onPlayClick);
      global.addEventListener('resize', function () { eachCollection(doc, update); });
    }
    init(root);
    initAuto(root);
  }

  global.F2RAcademyCollection = Object.freeze({
    wire: wire,
    init: init,
    update: update,
    syncArrows: update
  });
})(typeof window !== 'undefined' ? window : globalThis);
