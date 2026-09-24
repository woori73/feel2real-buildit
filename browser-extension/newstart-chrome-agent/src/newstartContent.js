/*
 * NewStart Chrome Agent — NewStart 페이지 content script (Phase 2 PoC).
 *
 * feel2real.co.kr/newstart* 페이지의 연결 배지(id="ns-chrome-badge")를 갱신한다.
 *  - 확장이 설치/실행 중일 때만 이 스크립트가 돈다 → 백그라운드에 GET_STATUS 조회.
 *  - 미설치 시 템플릿의 기본 문구("확장 미설치")가 그대로 남아 Guided Agent가 계속 동작(§13 fallback).
 * 데이터를 모으지 않는다: 응답은 {installed, version, 마지막 단계 번호}뿐.
 */
'use strict';

(function () {
  var VERSION = '0.1.0';

  function run() {
    var el = document.getElementById('ns-chrome-badge');
    if (!el) return;
    try {
      chrome.runtime.sendMessage({ type: 'GET_STATUS' }, function (status) {
        if (chrome.runtime.lastError || !status || !status.installed) return;
        var text = 'Chrome Agent 연결됨 (v' + (status.version || VERSION) + ')';
        if (status.last && typeof status.last.step === 'number') {
          text += ' · 마지막 확인 단계 ' + status.last.step;
        }
        el.textContent = text;
        el.setAttribute('data-ns-agent', 'connected');
      });
    } catch (e) { /* extension reload 직전 등 — 기본 문구 유지 */ }
  }

  /* 페이지 자체 JS가 postMessage로 연결 상태를 문의하는 경우의 최소 응답 계약.
     (e.source===window 검증, 응답은 설치 여부·버전만 — §6/§19) */
  window.addEventListener('message', function (e) {
    if (e.source !== window) return;
    var d = e.data;
    if (!d || d.source !== 'newstart-page-ping') return;
    try {
      window.postMessage({ source: 'newstart-agent', installed: true, version: VERSION },
        location.origin);
    } catch (err) { /* noop */ }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', run);
  } else {
    run();
  }
})();
