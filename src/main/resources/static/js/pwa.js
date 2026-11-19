/** F2R 웹앱: service worker 등록(지원하는 브라우저에서만). 개인 자료는 저장하지 않는다(sw.js 참고). */
(function () {
  if (!('serviceWorker' in navigator) || location.protocol === 'file:') return;
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => { /* 등록 실패해도 화면은 그대로 쓴다 */ });
  });
})();
