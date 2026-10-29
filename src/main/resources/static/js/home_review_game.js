/*
 * 홈 복습 게임: "게임 시작"을 누를 때만 같은 출처의 게임 페이지(/games/…)를 iframe으로 불러온다.
 * 페이지를 열자마자 약 10MB를 받지 않게 하고, 닫으면 iframe을 지워 메모리를 돌려준다.
 * 게임은 저장(XP·오답 노트)에 localStorage를 쓰므로 같은 출처 권한이 필요하다. 게임 페이지 자체는 CSP로 같은 출처만 쓴다.
 *
 * 휴대폰(폭 760px 이하): 게임은 세로 화면을 가로 900칸 기준으로 그려 화면 폭에 맞춰 줄이므로, 폭이 넓을수록 글자가 커진다.
 * 그래서 카드 안이 아니라 화면 전체(여백·테두리 없음)로 열고, 지원되면 브라우저 전체 화면까지 켠다.
 */
(function () {
  'use strict';
  var SANDBOX = 'allow-scripts allow-same-origin allow-pointer-lock allow-popups';
  var COMPACT_QUERY = '(max-width: 760px)';

  function init(doc, win) {
    var root = doc.getElementById('homeGame');
    if (!root) return null;
    var src = root.getAttribute('data-game-src');
    var cover = doc.getElementById('homeGameCover');
    var stage = doc.getElementById('homeGameStage');
    var tools = doc.getElementById('homeGameTools');
    var frame = null;
    var immersive = false;

    function compact() {
      return !!(win.matchMedia && win.matchMedia(COMPACT_QUERY).matches);
    }

    function pauseOtherMedia() {
      ['examPromoVideo', 'homeAgentTrailer'].forEach(function (id) {
        var v = doc.getElementById(id);
        if (v && v.pause) v.pause();
      });
    }

    // 게임은 자기 화면에 포커스가 있을 때만 문제를 진행한다. 홈페이지 버튼을 누르면 포커스가 밖으로 나가므로 바로 되돌려 준다.
    function focusGame() {
      if (!frame) return;
      try { if (frame.contentWindow && frame.contentWindow.focus) frame.contentWindow.focus(); } catch (_) { /* 로딩 전 */ }
      if (frame.focus) frame.focus();
    }

    function isFullscreen() {
      return !!doc.fullscreenElement;
    }

    // 브라우저 전체 화면(주소창 숨김). 아이폰 Safari처럼 지원하지 않으면 화면 전체 레이어만 쓴다.
    function enterFullscreen(target) {
      if (!target.requestFullscreen) return null;
      var result = target.requestFullscreen();
      return result && result.catch ? result : null;
    }

    function setImmersive(on) {
      immersive = on;
      root.classList.toggle('is-immersive', on);
      // 레이어 뒤 홈페이지가 같이 스크롤되지 않게 막고, 닫으면 원래대로 돌린다.
      doc.documentElement.style.overflow = on ? 'hidden' : '';
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
      if (compact()) {
        setImmersive(true);
        var pending = enterFullscreen(root);
        if (pending) pending.catch(function () { /* 화면 전체 레이어로 계속 */ });
      }
      focusGame();
    }

    function close() {
      if (!frame) return;
      if (isFullscreen() && doc.exitFullscreen) {
        var exiting = doc.exitFullscreen();
        if (exiting && exiting.catch) exiting.catch(function () {});
      }
      stage.removeChild(frame);
      frame = null;
      setImmersive(false);
      stage.hidden = true;
      tools.hidden = true;
      cover.hidden = false;
    }

    // PC: 게임 영역만 전체 화면. 안 되면 게임 페이지를 새 창으로 연다.
    // 휴대폰: 이미 화면 전체 레이어이므로 브라우저 전체 화면을 켜고 끈다(안 되면 그대로 둔다).
    function fullscreen() {
      if (!frame) return;
      if (immersive) {
        if (isFullscreen()) { if (doc.exitFullscreen) doc.exitFullscreen(); focusGame(); return; }
        var pending = enterFullscreen(root);
        if (pending) pending.catch(function () {});
        focusGame();
        return;
      }
      var openWindow = function () { win.open(src, '_blank', 'noopener'); };
      if (!frame.requestFullscreen) { openWindow(); return; }
      var result = enterFullscreen(frame);
      if (result) result.catch(openWindow);
    }

    // 전체 화면이 켜지고 꺼지는 동안에도 포커스가 바뀔 수 있으므로 끝난 뒤 한 번 더 되돌린다.
    if (doc.addEventListener) doc.addEventListener('fullscreenchange', focusGame);
    doc.getElementById('homeGameStart').addEventListener('click', start);
    doc.getElementById('homeGameClose').addEventListener('click', close);
    doc.getElementById('homeGameFullscreen').addEventListener('click', fullscreen);
    return { start: start, close: close, fullscreen: fullscreen, frame: function () { return frame; },
      immersive: function () { return immersive; } };
  }

  var api = { SANDBOX: SANDBOX, COMPACT_QUERY: COMPACT_QUERY, init: init };
  if (typeof window !== 'undefined') window.f2rHomeReviewGame = api;
  if (typeof document === 'undefined' || !document.getElementById) return;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { init(document, window); });
  else init(document, window);
})();
