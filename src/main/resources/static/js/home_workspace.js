/* Homepage panes reuse existing content, links and permissions. No iframe or API proxy. */
(function () {
  'use strict';
  var PANES = {
    home: ['홈', '우리 학교 시험 준비부터 영어 복습, AI 콘텐츠까지.'],
    request: ['맞춤문제 신청', '기출 시험자료와 출제시 요구사항으로 우리 학교에 맞게.'],
    wiki: ['모의고사 위키', '문제 원본·지문해설·복습 문제를 한곳에서.'],
    game: ['영어 복습 게임', '서수니 튜터와 함께 문제를 풀고 복습하세요.'],
    agent: ['AI AGENT', '인간과 AI가 함께 만드는 이야기와 콘텐츠.'],
    dashboard: ['대시보드', '학원 업무와 제작을 이어가는 나의 작업 공간.'],
    chart: ['빌드잇 차트', '전국의 색깔 있는 영어교육자를 발견합니다.'],
    guide: ['이용 안내', '신청·자료 제출·입금·제공과 환불 안내.']
  };
  var HASHES = { home: 'home', request: 'request', wiki: 'wiki', 'review-game': 'game',
    'ai-content': 'agent', dashboard: 'dashboard', 'chart-showcase': 'chart', guide: 'guide' };

  function init(doc, win) {
    var sidebar = doc.getElementById('homeSidebar');
    if (!sidebar) return null;
    var shell = sidebar.closest('.home-shell');
    var toggle = doc.getElementById('enBurger');
    var title = doc.getElementById('homePaneTitle');
    var description = doc.getElementById('homePaneDescription');
    var panels = doc.querySelectorAll('[data-home-panel]');
    var links = sidebar.querySelectorAll('[data-home-nav]');
    var initialTitle = doc.title;
    var current = null;

    function hashId() {
      try { return decodeURIComponent(win.location.hash.slice(1)); } catch (_) { return ''; }
    }
    function resolve(id) {
      if (Object.prototype.hasOwnProperty.call(HASHES, id)) return HASHES[id];
      var target = doc.getElementById(id);
      var panel = target && target.closest('[data-home-panel]');
      if (!panel) return 'home';
      var groups = panel.dataset.homePanel.split(' ');
      return groups.indexOf(current) >= 0 ? current : groups[0];
    }
    function closeMenu() {
      sidebar.classList.remove('menu-open');
      toggle.setAttribute('aria-expanded', 'false');
      toggle.setAttribute('aria-label', '메뉴 열기');
    }
    function select(focus) {
      var id = hashId();
      var pane = resolve(id);
      if (!Object.prototype.hasOwnProperty.call(PANES, pane)) pane = 'home';
      if (current === 'game' && pane !== 'game') {
        var game = doc.getElementById('homeGameStage');
        var close = doc.getElementById('homeGameClose');
        if (game && game.querySelector('iframe') && close) close.click();
      }
      panels.forEach(function (panel) {
        panel.hidden = panel.dataset.homePanel.split(' ').indexOf(pane) < 0;
        if (panel.hidden) panel.querySelectorAll('video').forEach(function (video) { video.pause(); });
      });
      links.forEach(function (link) {
        if (link.dataset.homeNav === pane) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
      });
      title.textContent = PANES[pane][0];
      description.textContent = PANES[pane][1];
      doc.title = pane === 'home' ? initialTitle : PANES[pane][0] + ' | Feel2Real';
      current = pane;
      closeMenu();
      if (focus) {
        var anchor = !Object.prototype.hasOwnProperty.call(HASHES, id) && doc.getElementById(id);
        var target = anchor && anchor.closest('[data-home-panel]') ? anchor : title;
        target.scrollIntoView({ block: 'start', behavior: 'auto' });
        if (target === title) title.focus({ preventScroll: true });
      }
    }
    toggle.addEventListener('click', function () {
      var open = sidebar.classList.toggle('menu-open');
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    });
    doc.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && sidebar.classList.contains('menu-open')) {
        closeMenu(); toggle.focus();
      }
    });
    win.addEventListener('hashchange', function () { select(true); });
    // Re-selecting the current pane also closes the mobile menu and returns focus to content.
    doc.querySelectorAll('a[href^="#"]').forEach(function (link) {
      link.addEventListener('click', function () {
        if (link.getAttribute('href') === win.location.hash) select(true);
      });
    });
    // Async media callbacks must not restart a video after its pane has been hidden.
    doc.addEventListener('play', function (event) {
      var panel = event.target.closest && event.target.closest('[data-home-panel]');
      if (panel && panel.hidden && event.target.pause) event.target.pause();
    }, true);
    var pause = doc.getElementById('homeNoticePause');
    var ticker = doc.getElementById('homeNoticeTrack');
    if (pause && ticker) pause.addEventListener('click', function () {
      var stopped = ticker.classList.toggle('is-paused');
      pause.setAttribute('aria-pressed', String(stopped));
      pause.setAttribute('aria-label', stopped ? '공지 흐름 재생' : '공지 흐름 일시정지');
      pause.textContent = stopped ? '▶' : 'Ⅱ';
    });
    select(false);
    shell.classList.add('home-ready');
    return { select: select, current: function () { return current; } };
  }
  var api = { PANES: PANES, init: init };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.f2rHomeWorkspace = api;
  if (typeof document !== 'undefined') init(document, window);
})();
