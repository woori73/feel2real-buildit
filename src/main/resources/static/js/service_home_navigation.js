/* 입력 후 홈 이동 시 작성 내용의 저장 여부를 확인한다. 기존 저장·인증 동작은 유지한다. */
(function () {
  'use strict';
  if (window.f2rServiceHomeNavigation) return;
  window.f2rServiceHomeNavigation = true;
  var edited = false;
  function record(event) {
    var target = event.target;
    if (target && target.matches('input:not([type="hidden"]):not([type="password"]):not([readonly]), textarea:not([readonly]), select, [contenteditable="true"]')) edited = true;
  }
  document.addEventListener('input', record, true);
  document.addEventListener('change', record, true);
  document.addEventListener('click', function (event) {
    if (edited && event.target.closest('a[data-f2r-home]') && !window.confirm('입력한 내용이 있습니다. 저장 여부를 확인한 뒤 홈으로 이동할까요?')) event.preventDefault();
  }, true);
})();
