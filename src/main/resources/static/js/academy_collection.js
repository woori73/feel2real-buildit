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
 *   Video Card  — 카드를 클릭하면 그 자리에서 재생(업로드 스트림 → <video>, 임베드 URL → <iframe>).
 *
 * 규칙(V2)
 *   - Story(3-A)와 분리: 등장 애니메이션·자동 캐러셀 없음. 사용자 제스처만.
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

    var embed = trigger.dataset.videoEmbed;
    var src = trigger.dataset.videoSrc;
    var node;
    if (embed) {
      node = doc.createElement('iframe');
      node.src = embed;
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
    node.className = 'f2r-video-card-player';
    trigger.replaceWith(node);
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
