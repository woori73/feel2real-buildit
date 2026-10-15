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
 *
 *   자동 이동(autoplay/auto-advance) 없음 — Shopify 레퍼런스 확정: product grid/collection 을
 *   자동으로 넘기지 않는다. 데스크톱에서 카드가 다 들어가면 CSS 가 grid 로(가로 스크롤 없음),
 *   모바일에서는 네이티브 scroll-snap + swipe + 다음 카드 peek + 카운터가 어포던스를 담당한다.
 *
 * 규칙
 *   - 등장 애니메이션·자동 캐러셀 없음. 스크롤 리스너 없음(진행 카운터는 track 의 scroll 이벤트만).
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
    // 모바일 Chrome/Safari 는 autoplay 속성만으로는 소리 있는 영상을 막는다. 클릭은 유효한
    // user gesture 이므로 그 안에서 play() 를 직접 호출해야 첫 tap 으로 재생된다(§J). src 는
    // 방금 만든 새 요소에 한 번만 지정했다 — load()/src 재대입으로 최초 로드를 끊지 않는다(§10).
    if (node.tagName === 'VIDEO' && typeof node.play === 'function') {
      var played = node.play();
      if (played && typeof played.catch === 'function') {
        played.catch(function () { /* 차단 시 사용자가 컨트롤로 재생 */ });
      }
    }
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
  }

  global.F2RAcademyCollection = Object.freeze({
    wire: wire,
    init: init,
    update: update,
    syncArrows: update
  });
})(typeof window !== 'undefined' ? window : globalThis);
