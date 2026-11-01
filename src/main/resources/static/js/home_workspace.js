/* Homepage full menu (2026-11-01 light redesign). The home is one page; this only opens/closes the menu
 * panel, keeps focus inside it while open, and pauses the notice ticker. No iframe or API proxy. */
(function () {
  'use strict';
  var FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

  function init(doc, win) {
    var panel = doc.getElementById('homeMenuPanel');
    var toggle = doc.getElementById('enBurger');
    if (!panel || !toggle) return null;
    var root = panel.closest('.home-light');
    var opener = toggle;

    function isOpen() { return !panel.hidden; }
    function visibleFocusables() {
      return Array.prototype.filter.call(panel.querySelectorAll(FOCUSABLE), function (el) {
        return !el.closest('[hidden]') && el.offsetParent !== null;
      });
    }
    function open() {
      opener = doc.activeElement && doc.activeElement !== doc.body ? doc.activeElement : toggle;
      panel.hidden = false;
      toggle.setAttribute('aria-expanded', 'true');
      if (root) root.classList.add('menu-open');
      var close = doc.getElementById('homeMenuClose');
      if (close) close.focus();
    }
    function close(restoreFocus) {
      if (!isOpen()) return;
      panel.hidden = true;
      toggle.setAttribute('aria-expanded', 'false');
      if (root) root.classList.remove('menu-open');
      if (restoreFocus) (opener && opener.focus ? opener : toggle).focus();
    }

    toggle.addEventListener('click', function () { if (isOpen()) close(true); else open(); });
    panel.addEventListener('click', function (event) {
      var target = event.target;
      if (target.closest('[data-menu-close]')) { close(true); return; }
      // Choosing a destination (or 로그인, which opens its own dialog) closes the menu first.
      if (target.closest('a[href], [data-auth-login]')) close(false);
    });
    doc.addEventListener('keydown', function (event) {
      if (!isOpen()) return;
      if (event.key === 'Escape') { event.preventDefault(); close(true); return; }
      if (event.key !== 'Tab') return;
      var items = visibleFocusables();
      if (!items.length) return;
      var first = items[0], last = items[items.length - 1];
      if (event.shiftKey && doc.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && doc.activeElement === last) { event.preventDefault(); first.focus(); }
    });

    // A link to a closed <details> (e.g. 적중 사례 항목) opens it so the content is visible on arrival.
    function openTargetDetails() {
      var id;
      try { id = decodeURIComponent(win.location.hash.slice(1)); } catch (_) { return; }
      var target = id && doc.getElementById(id);
      if (target && target.tagName === 'DETAILS') target.open = true;
    }
    win.addEventListener('hashchange', openTargetDetails);
    openTargetDetails();

    var pause = doc.getElementById('homeNoticePause');
    var ticker = doc.getElementById('homeNoticeTrack');
    if (pause && ticker) pause.addEventListener('click', function () {
      var stopped = ticker.classList.toggle('is-paused');
      pause.setAttribute('aria-pressed', String(stopped));
      pause.setAttribute('aria-label', stopped ? '공지 흐름 재생' : '공지 흐름 일시정지');
      pause.textContent = stopped ? '▶' : 'Ⅱ';
    });
    if (root) root.classList.add('home-ready');
    return { open: open, close: close, isOpen: isOpen };
  }
  var api = { init: init };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.f2rHomeWorkspace = api;
  if (typeof document !== 'undefined') init(document, window);
})();
