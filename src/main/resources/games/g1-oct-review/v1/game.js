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
