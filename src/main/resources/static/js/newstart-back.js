(function () {
  'use strict';
  var KEY = 'newstartLastPath';
  var current = window.location.pathname + window.location.search;
  var last = null;
  try {
    last = sessionStorage.getItem(KEY);
    sessionStorage.setItem(KEY, current);
  } catch (e) {
    // 저장소가 막힌 환경(일부 프라이빗 모드)에서는 referrer와 대체 링크만 사용한다.
  }

  var refPath = '';
  try {
    if (document.referrer) refPath = new URL(document.referrer).pathname;
  } catch (e) {
    refPath = '';
  }

  // 직전 화면이 같은 NewStart 흐름일 때만 뒤로가기를 쓴다. 뒤로갈 기록이 없으면(직접 URL 진입,
  // 외부에서 온 경우) href 목적지(홈 또는 목록)로 보내 화면에 갇히지 않게 한다.
  function inNewStartFlow(path) {
    return !!path && path !== current && path.indexOf('/newstart') === 0;
  }
  var useHistory = window.history.length > 1 && (inNewStartFlow(refPath) || inNewStartFlow(last));

  document.querySelectorAll('[data-ns-back]').forEach(function (link) {
    if (!useHistory) return;
    link.addEventListener('click', function (event) {
      event.preventDefault();
      window.history.back();
    });
  });
})();
