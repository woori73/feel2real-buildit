/* Optional approved promo. Never requests a video until Suni has actually rendered a frame. */
(function () {
  'use strict';
  var slot = document.getElementById('examPromo');
  var video = document.getElementById('examPromoVideo');
  if (!slot || !video || !/^\/video\/landing\/[A-Za-z0-9][A-Za-z0-9_-]*\.mp4$/.test(slot.dataset.src || '')) return;
  var attempted = false;
  function restore() { video.pause(); slot.hidden = true; }
  function start() {
    var source = ['homeVideo', 'homeVideoAlt'].map(function (id) { return document.getElementById(id); }).find(function (v) {
      return v && v.readyState >= 2 && v.classList.contains('is-on') && /\/suni\/idle\.mp4(?:\?|$)/.test(v.currentSrc || v.src);
    });
    var suni = document.querySelector('[data-home-agent="suni"][aria-pressed="true"]');
    if (attempted || !suni || !source || source.readyState < 2 || !source.classList.contains('is-on') ||
        !/\/suni\/idle\.mp4(?:\?|$)/.test(source.currentSrc || source.src)) return;
    attempted = true;
    observer.disconnect();
    video.src = slot.dataset.src;
    video.muted = true;
    slot.hidden = false;
    var play = video.play();
    if (play && play.catch) play.catch(restore);
  }
  var observer = new MutationObserver(start);
  ['homeVideo', 'homeVideoAlt'].forEach(function (id) {
    var character = document.getElementById(id);
    if (character) { character.addEventListener('playing', start); observer.observe(character, {attributes:true, attributeFilter:['class']}); }
  });
  video.addEventListener('error', restore);
  video.addEventListener('ended', restore);
  document.querySelectorAll('[data-home-agent]').forEach(function (button) {
    button.addEventListener('click', restore);
  });
  window.addEventListener('pagehide', function () { observer.disconnect(); restore(); });
  start();
})();
