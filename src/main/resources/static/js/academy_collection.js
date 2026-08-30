/**
 * Horizontal Content Collection — 배선(interaction) 전용. 공개 학원 페이지(academy_public.js)가 소비한다.
 *
 * 마크업 정본은 academy_sections.js(renderCollectionShell / programCardHtml / videoCardHtml)이고,
 * 이 파일은 그 위에 "사용자가 직접 둘러보는" 상호작용만 얹는다.
 *
 *   Navigation  — 좌/우 화살표는 track.scrollBy 로 한 화면 남기고 이동(맥락 유지).
 *   Video Card  — 카드를 클릭하면 그 자리에서 재생(업로드 스트림 → <video>, 임베드 URL → <iframe>).
 *
 * 규칙(3-B)
 *   - Story(3-A)와 분리: 등장 애니메이션·자동 캐러셀 없음. 사용자 제스처만.
 *   - 세로 마우스 휠을 가로로 강제 변환하지 않는다(native overflow-x 우선, trackpad 가로 스크롤 그대로).
 *   - 새 제스처/캐러셀 라이브러리 없음. 모바일 스와이프는 CSS overflow-x + scroll-snap 이 담당.
 *   - 페이지 자체가 가로로 밀리지 않게: viewport 가 track 을 자르고 track 은 overscroll-behavior-x:contain.
 *   - 모듈이 로드되지 않아도 콘텐츠는 정적 shelf 로 완전히 보인다(화살표만 동작 안 함).
 *   - prefers-reduced-motion: 화살표 이동은 smooth 대신 즉시 점프.
 */
(function (global) {
  'use strict';

  var doc = global.document;
  if (!doc) return;

  function prefersReduced() {
    return typeof global.matchMedia === 'function'
      && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function collectionOf(node) {
    return node && node.closest ? node.closest('.f2r-collection') : null;
  }

  /** 한 번에 "보이는 카드 수 - 1" 만큼 이동해 방금 본 카드 하나를 남긴다(최소 1장). */
  function stepFor(track) {
    var card = track.querySelector('.f2r-collection-card');
    if (!card) return Math.round(track.clientWidth * 0.8);
    var style = global.getComputedStyle ? global.getComputedStyle(track) : null;
    var gap = style ? (parseFloat(style.columnGap || style.gap || '16') || 16) : 16;
    var cardW = card.offsetWidth + gap;
    var perView = Math.max(1, Math.floor(track.clientWidth / cardW));
    return cardW * Math.max(1, perView - 1);
  }

  function syncArrows(collection) {
    if (!collection || collection.hasAttribute('data-collection-static')) return;
    var track = collection.querySelector('[data-collection-track]');
    var prev = collection.querySelector('[data-collection-scroll="prev"]');
    var next = collection.querySelector('[data-collection-scroll="next"]');
    if (!track || !prev || !next) return;
    var max = track.scrollWidth - track.clientWidth - 1;
    prev.disabled = track.scrollLeft <= 0;
    next.disabled = track.scrollLeft >= max;
  }

  function eachCollection(root, fn) {
    var scope = root && root.querySelectorAll ? root : doc;
    var list = scope.querySelectorAll('.f2r-collection:not([data-collection-static])');
    Array.prototype.forEach.call(list, fn);
  }

  function init(root) {
    eachCollection(root, function (collection) {
      var track = collection.querySelector('[data-collection-track]');
      if (!track || track.dataset.collectionReady === 'true') return;
      track.dataset.collectionReady = 'true';
      track.addEventListener('scroll', function () { syncArrows(collection); }, { passive: true });
      syncArrows(collection);
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
    var dir = btn.dataset.collectionScroll === 'prev' ? -1 : 1;
    track.scrollBy({ left: dir * stepFor(track), behavior: prefersReduced() ? 'auto' : 'smooth' });
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
      doc.addEventListener('click', onArrowClick);
      doc.addEventListener('click', onPlayClick);
      global.addEventListener('resize', function () {
        eachCollection(doc, syncArrows);
      });
    }
    init(root);
  }

  global.F2RAcademyCollection = Object.freeze({
    wire: wire,
    init: init,
    syncArrows: syncArrows
  });
})(typeof window !== 'undefined' ? window : globalThis);
