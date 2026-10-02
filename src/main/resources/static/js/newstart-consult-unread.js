(function () {
  'use strict';
  // 홈 화면의 "1:1 비공개 상담" 카드 배지. 인증 JS에 의존하지 않고 same-origin fetch 한 번으로
  // 못 본 운영자 답변 수만 확인한다 — 401/403(비로그인·비감사회원)이면 조용히 숨긴다.
  var badge = document.getElementById('ns-consult-unread');
  if (!badge) return;
  fetch('/api/newstart/consultations/unread-count', { credentials: 'same-origin' })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (d) {
      var n = d && Number(d.unreadAnswerCount);
      if (n > 0) { badge.textContent = '새 답변 ' + n + '건'; badge.hidden = false; }
      else { badge.hidden = true; }
    })
    .catch(function () { badge.hidden = true; });
})();
