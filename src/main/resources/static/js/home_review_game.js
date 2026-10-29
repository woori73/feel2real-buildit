/*
 * 홈 복습 게임: "게임 시작"을 누를 때만 같은 출처의 게임 페이지(/games/…)를 iframe으로 불러온다.
 * 페이지를 열자마자 약 10MB를 받지 않게 하고, 닫으면 iframe을 지워 메모리를 돌려준다.
 * 게임은 저장(XP·오답 노트)에 localStorage를 쓰므로 같은 출처 권한이 필요하다. 게임 페이지 자체는 CSP로 같은 출처만 쓴다.
 */
(function () {
  'use strict';
  var SANDBOX = 'allow-scripts allow-same-origin allow-pointer-lock allow-popups';

  function init(doc, win) {
    var root = doc.getElementById('homeGame');
    if (!root) return null;
    var src = root.getAttribute('data-game-src');
    var cover = doc.getElementById('homeGameCover');
    var stage = doc.getElementById('homeGameStage');
    var tools = doc.getElementById('homeGameTools');
    var frame = null;

    function pauseOtherMedia() {
      ['examPromoVideo', 'homeAgentTrailer'].forEach(function (id) {
        var v = doc.getElementById(id);
        if (v && v.pause) v.pause();
      });
    }

    function start() {
      if (frame || !src) return;
      frame = doc.createElement('iframe');
      frame.src = src;
      frame.title = '고1 10월 영어 복습 게임';
      frame.setAttribute('sandbox', SANDBOX);
      frame.setAttribute('allow', 'fullscreen');
      frame.setAttribute('allowfullscreen', '');
      stage.appendChild(frame);
      stage.hidden = false;
      tools.hidden = false;
      cover.hidden = true;
      pauseOtherMedia();
      if (frame.focus) frame.focus();
    }

    function close() {
      if (!frame) return;
      stage.removeChild(frame);
      frame = null;
      stage.hidden = true;
      tools.hidden = true;
      cover.hidden = false;
    }

    // 아이폰 Safari는 iframe 전체 화면을 지원하지 않는다. 그때는 게임 페이지를 새 창으로 연다.
    function fullscreen() {
      if (!frame) return;
      var openWindow = function () { win.open(src, '_blank', 'noopener'); };
      if (!frame.requestFullscreen) { openWindow(); return; }
      var result = frame.requestFullscreen();
      if (result && result.catch) result.catch(openWindow);
    }

    doc.getElementById('homeGameStart').addEventListener('click', start);
    doc.getElementById('homeGameClose').addEventListener('click', close);
    doc.getElementById('homeGameFullscreen').addEventListener('click', fullscreen);
    return { start: start, close: close, fullscreen: fullscreen, frame: function () { return frame; } };
  }

  var api = { SANDBOX: SANDBOX, init: init };
  if (typeof window !== 'undefined') window.f2rHomeReviewGame = api;
  if (typeof document === 'undefined' || !document.getElementById) return;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { init(document, window); });
  else init(document, window);
})();
