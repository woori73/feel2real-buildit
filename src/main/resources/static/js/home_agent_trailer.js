/* A trailer is opt-in and manual: no private Studio requests or background playback. */
(function () {
  'use strict';
  var trailer = document.getElementById('homeAgentTrailer');
  if (!trailer) return;
  var status = document.getElementById('homeAgentTrailerStatus');
  var promo = document.getElementById('examPromoVideo');
  var playButton = document.getElementById('homeAgentTrailerPlay');
  if (playButton) playButton.addEventListener('click', function () {
    if (!trailer.paused) { trailer.pause(); return; }
    var result = trailer.play();
    if (result && result.catch) result.catch(function () {
      if (!trailer.error) {
        status.textContent = '영상의 재생 버튼을 눌러 다시 시도해 주세요.';
        status.hidden = false;
      }
    });
  });
  trailer.addEventListener('play', function () {
    if (promo) promo.pause();
    if (playButton) playButton.textContent = '일시정지';
  });
  trailer.addEventListener('playing', function () { status.hidden = true; });
  trailer.addEventListener('pause', function () { if (playButton) playButton.textContent = '트레일러 재생'; });
  if (promo) promo.addEventListener('play', function () { trailer.pause(); });
  trailer.addEventListener('error', function () {
    trailer.pause();
    trailer.hidden = true;
    if (playButton) playButton.hidden = true;
    status.textContent = '트레일러를 불러오지 못했습니다. 아래 대표 이미지를 확인해 주세요.';
    status.hidden = false;
  });
  document.querySelectorAll('[data-home-agent]').forEach(function (button) {
    button.addEventListener('click', function () { trailer.pause(); });
  });
  document.addEventListener('visibilitychange', function () { if (document.hidden) trailer.pause(); });
  window.addEventListener('pagehide', function () { trailer.pause(); });
  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      if (!entries[0].isIntersecting) trailer.pause();
    });
    observer.observe(trailer);
    window.addEventListener('pagehide', function () { observer.disconnect(); }, { once: true });
  }
})();
