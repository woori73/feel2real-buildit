/* NewStart 홈 비회원 게이트 — 홈은 공개, 실제 서비스는 회원 전용.
 * 서버(NewStartMembersOnlyInterceptor)가 내부 URL 직접 접근을 막고, 이 스크립트는 홈 안에서
 * 비회원 클릭을 자체 로그인으로 보내며 #해시와 홈 인라인 기능(채무상황·지피지기 Agent)의
 * 복귀 위치를 보존한다. 로그인 여부는 /api/auth/me 하나로만 확인한다. */
(function () {
  'use strict';
  var guest = false;

  function goLogin(next) {
    window.location.href = '/newstart/login?next=' + encodeURIComponent(next);
  }

  fetch('/api/auth/me', { credentials: 'same-origin', cache: 'no-store' })
    .then(function (res) { guest = res.status === 401; })
    .catch(function () {});

  // 내부 서비스 링크: /newstart/<하위경로>(로그인 제외). 홈 내부 앵커(#…)는 해당하지 않는다.
  document.addEventListener('click', function (e) {
    if (!guest || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    var t = e.target.closest ? e.target : e.target.parentElement;
    var a = t && t.closest('a[href]');
    if (a) {
      var u;
      try { u = new URL(a.getAttribute('href'), window.location.origin); } catch (err) { return; }
      if (u.origin === window.location.origin && /^\/newstart\/(?!login(?:[/?#]|$))./.test(u.pathname)) {
        e.preventDefault();
        e.stopImmediatePropagation();
        goLogin(u.pathname + u.search + u.hash);
        return;
      }
    }
    var opener = t && t.closest('[data-ns-open="ns-status-panel"]');
    if (opener) {
      e.preventDefault();
      e.stopImmediatePropagation();
      goLogin('/newstart#ns-status-panel');
    }
  }, true);

  // 지피지기 Agent(홈 인라인) 질문 제출.
  var aiForm = document.getElementById('ns-ai-form');
  if (aiForm) {
    aiForm.addEventListener('submit', function (e) {
      if (!guest) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      goLogin('/newstart#ns-ai');
    }, true);
  }
})();
