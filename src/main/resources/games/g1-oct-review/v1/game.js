/* Starts the Unity build. Same flow as the project's F2RAcademy WebGL template; no remote services. */
(function () {
  'use strict';
  var NIMBLE_URL = 'https://app.nimblefox.ai/g/g1y7QeOYd8';
  var canvas = document.getElementById('unity-canvas');
  var loading = document.getElementById('loading');
  var error = document.getElementById('error');

  function showError(message) {
    error.textContent = '학습 미션을 불러오지 못했어요. 페이지를 다시 열어 주세요.\n' + (message || '') + '\n\n';
    var link = document.createElement('a');
    link.href = NIMBLE_URL;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = 'Nimble에서 같은 게임 열기 ↗';
    error.appendChild(link);
    error.style.display = 'block';
    loading.style.display = 'none';
  }

  var config = {
    dataUrl: 'Build/WebGLBuild.data',
    frameworkUrl: 'Build/WebGLBuild.framework.js',
    codeUrl: 'Build/WebGLBuild.wasm',
    streamingAssetsUrl: 'StreamingAssets',
    // Same names as the published build so the game's own storage paths stay unchanged.
    companyName: 'Nimble Fox',
    productName: 'Game',
    productVersion: '1.0',
    matchWebGLToCanvasSize: true,
    showBanner: function (message, type) { if (type === 'error') showError(message); }
  };

  // The game only advances while its own page has focus (document.hasFocus). Inside the homepage iframe a tap on the
  // homepage toolbar (전체 화면) moves focus out, and Unity blocks the touch default so a later tap never brings it back:
  // the next question then waits in its 준비 state with an invisible passage. Any touch or click in the game takes focus back.
  function reclaimFocus() {
    if (document.hasFocus()) return;
    window.focus();
    canvas.focus();
  }
  ['touchstart', 'pointerdown', 'mousedown'].forEach(function (type) {
    window.addEventListener(type, reclaimFocus, { capture: true, passive: true });
  });

  var loader = document.createElement('script');
  loader.src = 'Build/WebGLBuild.loader.js';
  loader.onload = function () {
    createUnityInstance(canvas, config, function (progress) {
      loading.querySelector('progress').value = progress;
    }).then(function () {
      loading.style.display = 'none';
      canvas.focus();
    }).catch(function (e) { showError(String(e && e.message || e)); });
  };
  loader.onerror = function () { showError('게임 파일을 읽을 수 없어요.'); };
  document.body.appendChild(loader);
})();
