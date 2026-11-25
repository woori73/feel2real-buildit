/* F2R 홈 대문 영상(2026-11-25). 대문 섹션(#study-entry)에 data-gate-video가 있을 때만 켠다 — 지금은 꺼 둔 상태.
 * - 영상 칸(hidden)을 열고 data-poster/data-src를 진짜 poster/src로 옮긴다(꺼져 있을 땐 파일 요청 0).
 * - 움직임 줄이기·데이터 절약이면 포스터만 보여 주고 영상은 받지 않는다.
 * - 화면에 보일 때만 재생, 벗어나면 멈춘다(IntersectionObserver). 없으면 포스터만.
 * - 전체 영상(data-gate-promo)은 「소리 켜기」 버튼으로 소리를 켜고 끈다.
 * JS가 꺼져 있으면 영상 칸은 hidden 그대로라 지금 디자인만 보인다. */
(function () {
  'use strict';

  function isEnabled(section) {
    return !!(section && section.hasAttribute && section.hasAttribute('data-gate-video'));
  }

  // 포스터만 보여 줄지: 움직임 줄이기 설정 또는 데이터 절약 모드
  function prefersStill(win) {
    var reduced = !!(win && win.matchMedia && win.matchMedia('(prefers-reduced-motion: reduce)').matches);
    var conn = win && win.navigator && win.navigator.connection;
    return reduced || !!(conn && conn.saveData);
  }

  // 영상 하나 준비: 포스터는 언제나, 영상 주소는 still이 아닐 때만 넣는다
  function prepareVideo(video, still) {
    var poster = video.getAttribute('data-poster');
    if (poster) video.setAttribute('poster', poster);
    if (still) return false;
    var sources = video.querySelectorAll('source[data-src]');
    for (var i = 0; i < sources.length; i++) sources[i].setAttribute('src', sources[i].getAttribute('data-src'));
    if (typeof video.load === 'function') video.load();
    return true;
  }

  // 소리 버튼: 누를 때마다 muted를 바꾸고 버튼 글자·aria-pressed를 맞춘다
  function toggleSound(video, button) {
    video.muted = !video.muted;
    var on = !video.muted;
    button.setAttribute('aria-pressed', on ? 'true' : 'false');
    button.textContent = on ? '소리 끄기' : '소리 켜기';
    return on;
  }

  function playSafe(video) {
    var p = video.play && video.play();
    if (p && typeof p.catch === 'function') p.catch(function () {});
  }

  function init(doc, win) {
    var section = doc && doc.getElementById && doc.getElementById('study-entry');
    if (!isEnabled(section)) return [];
    var still = prefersStill(win);
    // data-gate-video 값으로 켤 범위를 고른다: "promo"=전체 영상만, "card(s)"=4칸 배경만, 빈 값=둘 다.
    var scope = (section.getAttribute && section.getAttribute('data-gate-video')) || '';
    var wantPromo = scope === '' || scope.indexOf('promo') >= 0;
    var wantCards = scope === '' || scope.indexOf('card') >= 0;
    var slots = section.querySelectorAll('[data-gate-promo], [data-gate-media]');
    var videos = [];
    for (var i = 0; i < slots.length; i++) {
      var slot = slots[i];
      var isMedia = slot.hasAttribute && slot.hasAttribute('data-gate-media');
      if (isMedia ? !wantCards : !wantPromo) continue;
      var video = slot.querySelector('video');
      if (!video) continue;
      slot.hidden = false;
      if (slot.hasAttribute('data-gate-media') && slot.parentNode && slot.parentNode.classList) slot.parentNode.classList.add('has-video');
      if (prepareVideo(video, still)) videos.push(video);
    }
    var promo = section.querySelector('[data-gate-promo]');
    var sound = promo && promo.querySelector('[data-gate-sound]');
    var promoVideo = promo && promo.querySelector('video');
    if (sound && promoVideo) {
      if (still) sound.hidden = true;
      else sound.addEventListener('click', function () { toggleSound(promoVideo, sound); });
    }
    if (!videos.length || !win || typeof win.IntersectionObserver !== 'function') return videos;
    var io = new win.IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) playSafe(e.target);
        else if (e.target.pause) e.target.pause();
      });
    }, { threshold: 0.25 });
    videos.forEach(function (v) { io.observe(v); });
    return videos;
  }

  var api = { init: init, isEnabled: isEnabled, prefersStill: prefersStill, prepareVideo: prepareVideo, toggleSound: toggleSound };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { init(document, window); });
    else init(document, window);
  }
})();
