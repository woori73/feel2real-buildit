/* Approved homepage intro. Native controls remain usable when autoplay is unavailable. */
(function () {
  'use strict';
  var video = document.getElementById('examPromoVideo');
  if (!video) return;
  var sound = document.getElementById('examPromoSound');
  var status = document.getElementById('examPromoStatus');
  var fallback = document.getElementById('examPromoFallback');

  function notice(text) { status.textContent = text; status.hidden = !text; }
  function syncSound() {
    sound.textContent = video.muted ? '소리 켜기' : '소리 끄기';
    sound.setAttribute('aria-pressed', String(!video.muted));
  }
  function play() {
    var result = video.play();
    if (result && result.catch) result.catch(function () {
      if (!video.error) notice('재생 버튼을 눌러 홍보 영상을 확인해 주세요.');
    });
  }
  function failed() {
    video.pause();
    if (fallback) { fallback.hidden = false; video.hidden = true; }
    sound.hidden = true;
    notice('영상을 불러오지 못했습니다. 아래에서 서비스 안내와 문의·신청을 이용해 주세요.');
  }
  sound.hidden = false;
  sound.addEventListener('click', function () { video.muted = !video.muted; syncSound(); play(); });
  video.addEventListener('volumechange', syncSound);
  video.addEventListener('playing', function () { notice(''); });
  video.addEventListener('error', failed);
  document.querySelectorAll('[data-home-agent]').forEach(function (button) {
    button.addEventListener('click', function () { video.pause(); });
  });
  document.addEventListener('visibilitychange', function () { if (document.hidden) video.pause(); });
  window.addEventListener('pagehide', function () { video.pause(); });
  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      if (!entries[0].isIntersecting) video.pause();
    });
    observer.observe(video);
    window.addEventListener('pagehide', function () { observer.disconnect(); }, { once: true });
  }
  syncSound();
  if (video.error) failed();
  else if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) play();
})();
