(function () {
  'use strict';
  // 홈 "1:1 비공개 상담" CTA 보조 표시: 못 본 운영자 답변 수 배지 + 진행 중 상담 요약.
  // 인증 JS에 의존하지 않고 same-origin fetch로 확인하며, 401(비로그인)이면 조용히 숨긴다.
  var badge = document.getElementById('ns-consult-unread');
  var sub = document.getElementById('ns-consult-sub');
  if (!badge) return;
  badge.hidden = true;

  fetch('/api/newstart/consultations/unread-count', { credentials: 'same-origin' })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (d) {
      var n = d && Number(d.unreadAnswerCount);
      if (n > 0) { badge.textContent = '새 답변 ' + n + '건'; badge.hidden = false; }
    })
    .catch(function () { badge.hidden = true; });

  if (!sub) return;
  fetch('/api/newstart/consultations', { credentials: 'same-origin' })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (list) {
      if (!Array.isArray(list) || list.length === 0) return;
      var waiting = list.filter(function (c) { return c.status === 'NEW' || c.status === 'AWAITING_ANSWER' || c.status === 'MEMBER_FOLLOWUP'; }).length;
      sub.textContent = '내 상담 ' + list.length + '건' + (waiting > 0 ? ' · 운영자 답변 대기 ' + waiting + '건' : '');
      sub.hidden = false;
    })
    .catch(function () { sub.hidden = true; });
})();
