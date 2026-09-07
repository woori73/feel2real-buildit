(function () {
  var VIRTUAL_WIDTH = 1100;
  var frame = document.getElementById('qwHtmlFrame');
  var viewport = document.getElementById('qwHtmlFrameViewport');
  if (!frame || !viewport) return;

  function isMobile() { return window.matchMedia('(max-width: 600px)').matches; }

  function reset() {
    viewport.removeAttribute('style');
    frame.style.removeProperty('position');
    frame.style.removeProperty('left');
    frame.style.removeProperty('top');
    frame.style.removeProperty('width');
    frame.style.removeProperty('max-width');
    frame.style.removeProperty('height');
    frame.style.removeProperty('transform');
    frame.style.removeProperty('transform-origin');
  }

  function fit() {
    if (!isMobile()) { reset(); return; }
    var dialog = viewport.parentElement;
    var actions = dialog.querySelector('.qw-modal-actions');
    var head = dialog.querySelector('.qw-modal-head');
    var availableHeight = dialog.clientHeight
      - (head ? head.offsetHeight + 8 : 0)
      - (actions ? actions.offsetHeight + 8 : 0);

    viewport.style.cssText = 'position:relative;flex:1 1 auto;min-height:0;min-width:0;overflow:hidden;width:100%;';
    var visualWidth = viewport.clientWidth;
    if (!visualWidth) return;
    var scale = visualWidth / VIRTUAL_WIDTH;

    frame.style.position = 'absolute';
    frame.style.left = '0';
    frame.style.top = '0';
    frame.style.width = VIRTUAL_WIDTH + 'px';
    frame.style.maxWidth = 'none';
    frame.style.height = Math.max(1, availableHeight / scale) + 'px';
    frame.style.transformOrigin = '0 0';
    frame.style.transform = 'scale(' + scale + ')';
  }

  frame.addEventListener('load', function () {
    requestAnimationFrame(function () {
      fit();
      // The browsing context is recreated at x=0; no child-DOM access is used.
      frame.focus({ preventScroll: true });
    });
  });
  window.addEventListener('resize', fit);
  window.addEventListener('orientationchange', fit);
  fit();
})();

// Question Wiki AI layout rules are isolated from the mobile iframe fitter.
// Load after question_wiki_ai.js so the normalizer can wrap generation/save fetches
// before the user starts a generation request.
(function () {
  if (document.querySelector('script[data-qw-ai-layout-fix]')) return;
  var script = document.createElement('script');
  script.src = '/js/question_wiki_ai_layout_fix.js?v=20260907-1';
  script.defer = true;
  script.dataset.qwAiLayoutFix = '1';
  document.head.appendChild(script);
})();
