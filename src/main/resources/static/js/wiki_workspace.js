/** Sidebar navigation and authenticated, sandboxed HTML reader. */
(function () {
  'use strict';
  var sidebar = document.getElementById('wikiSidebar');
  if (!sidebar) return;
  var PANES = {
    home: ['위키 홈', '필요한 자료를 고르고, 한 문항씩 이해해 보세요.'],
    review: ['복습 과제', '지문별 문제를 풀고, 바로 아래에서 정답과 해설을 확인하세요.'],
    guides: ['이번 회차 해설', '현재 회차의 문제풀이와 구문독해 자료를 모았습니다.'],
    originals: ['문제지 · 정답지', '선택한 학년의 모의고사 원본을 확인하세요.'],
    analysis: ['분석 · 변형문제', '지문을 이해하고 내신형 변형문제로 연습하세요.'],
    learning: ['어휘 · Workbook', '함께 나누는 어휘와 학습 자료를 확인하세요.'],
    threads: ['문항별 Wiki Thread', '분석과 해설, 변형문제 제작을 한 흐름으로 연결합니다.'],
    live: ['한 줄 인사', '함께 공부하는 분들에게 짧은 인사를 남겨 주세요.']
  };
  var navs = sidebar.querySelectorAll('[data-wiki-nav]');
  var panels = document.querySelectorAll('[data-wiki-panel]');
  var toggle = document.getElementById('wikiMenuToggle');
  var title = document.getElementById('wikiPaneTitle');
  var description = document.getElementById('wikiPaneDescription');
  var list = document.getElementById('reviewList');
  var monthTitle = document.getElementById('reviewMonthTitle');
  var monthSwitch = document.getElementById('reviewMonthSwitch');
  var status = document.getElementById('reviewStatus');
  var retry = document.getElementById('reviewRetry');
  var query = new URLSearchParams(location.search || '');
  var month = query.get('month') === '9' ? 9 : 10;
  var pendingQuestion = query.get('question');
  var loading = false;
  var reader = document.getElementById('reviewReader');
  var frame = document.getElementById('reviewFrame');
  var reviews = [];
  var byId = {};
  var admin = false;
  var objectUrl = null;
  var loadVersion = 0;
  var readVersion = 0;
  var lastOpener = null;

  function selectPane(focus) {
    var key = location.hash.slice(1);
    if (!Object.prototype.hasOwnProperty.call(PANES, key)) key = 'home';
    panels.forEach(function (panel) { panel.hidden = panel.dataset.wikiPanel !== key; });
    navs.forEach(function (link) {
      if (link.dataset.wikiNav === key) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    title.textContent = PANES[key][0];
    description.textContent = PANES[key][1];
    sidebar.classList.remove('menu-open');
    toggle.setAttribute('aria-expanded', 'false');
    var intro = document.getElementById('wikiIntro');
    if (intro) intro.dataset.pane = key;
    if (key !== 'review') closeReader(false);
    else if (loadVersion > 0) loadReviews();
    if (focus) {
      title.scrollIntoView({ block: 'start', behavior: 'auto' });
      title.focus({ preventScroll: true });
    }
  }
  toggle.addEventListener('click', function () {
    var open = sidebar.classList.toggle('menu-open');
    toggle.setAttribute('aria-expanded', String(open));
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && sidebar.classList.contains('menu-open')) {
      sidebar.classList.remove('menu-open');
      toggle.setAttribute('aria-expanded', 'false');
      toggle.focus();
    }
  });
  window.addEventListener('hashchange', function () { selectPane(true); });
  navs.forEach(function (link) { link.addEventListener('click', function () {
    if (link.hash === location.hash) selectPane(true);
  }); });
  selectPane(false);

  function currentGrade() { return window.wikiHub ? window.wikiHub.currentGrade() : 1; }
  async function loadReviews() {
    var version = ++loadVersion;
    closeReader(false);
    loading = true;
    reviews = [];
    renderReviews();
    status.textContent = '복습 과제를 불러오는 중입니다.';
    retry.hidden = true;
    try {
      var response = await fetch('/api/wiki/analysis-materials?grade=' + currentGrade(), { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) throw new Error();
      var items = await response.json();
      if (!Array.isArray(items)) throw new Error();
      var canManage = window.WikiAdminManage ? await window.WikiAdminManage.isAdmin().catch(function () { return false; }) : false;
      if (version !== loadVersion) return;
      admin = canManage;
      reviews = items.filter(function (item) { return /복습|자습/.test(item.title || ''); });
      byId = {};
      reviews.forEach(function (item) { byId[String(item.wikiAnalysisMaterialId)] = item; });
      loading = false;
      status.textContent = '';
      renderReviews();
      if (pendingQuestion) {
        var direct = visibleReviews().find(function (it) { return String(questionNumber(it)) === pendingQuestion; });
        pendingQuestion = null;
        if (direct) openReview(direct, null);
      }
    } catch (_) {
      if (version === loadVersion) {
        status.textContent = '자료 연결이 지연되고 있습니다. 다시 불러오기를 눌러 주세요.';
        retry.hidden = false;
      }
    }
  }
  function questionNumber(it) {
    var match = String(it.title || '').match(/(\d+)(?:[~～-]\d+)?번/);
    return match ? Number(match[1]) : 999;
  }
  function visibleReviews() {
    var monthPattern = month === 10 ? /10월|10모/ : /9월|9모/;
    return reviews.filter(function (it) {
      return monthPattern.test(it.title || '') && !/검토본|이전 편집|시안/.test(it.title || '');
    }).sort(function (a, b) { return questionNumber(a) - questionNumber(b); });
  }
  function renderReviews() {
    monthTitle.textContent = '2026년 ' + month + '모 복습과제';
    monthSwitch.textContent = month === 10 ? '← 2026년 9모' : '2026년 10모 →';
    var items = visibleReviews();
    if (loading && month === 10) {
      list.innerHTML = [20,21,22,23,24,29,30,31,32,33,34,35,36,37,38,39,40,41].map(function (n) {
        return '<article class="review-card"><button class="review-open" type="button" disabled><strong>'
          + (n === 41 ? '41~42' : n) + '번</strong><span>불러오는 중</span></button></article>';
      }).join('');
      return;
    }
    if (!items.length) {
      list.innerHTML = '<p class="muted">이 학년의 2026년 ' + month + '모 복습과제가 아직 없습니다.</p>';
      return;
    }
    list.innerHTML = items.map(function (it) {
      var id = String(it.wikiAnalysisMaterialId);
      var number = questionNumber(it);
      var label = number === 999 ? '전체 과제' : (number === 41 ? '41~42' : number) + '번';
      return '<article class="review-card"><button type="button" class="review-open" data-review-open="' + escapeHtml(id) + '" aria-label="' + label + ' 복습 과제">'
        + '<strong>' + label + '</strong><span>문제 · 정답 · 해설</span></button>'
        + (admin ? window.WikiAdminManage.buttonsHtml('analysis', id) : '') + '</article>';
    }).join('');
  }
  monthSwitch.addEventListener('click', function () {
    month = month === 10 ? 9 : 10;
    pendingQuestion = null;
    loadReviews();
  });
  retry.addEventListener('click', loadReviews);
  document.getElementById('gradeTabs').addEventListener('click', function (e) {
    if (e.target.closest('.grade-tab')) loadReviews();
  });
  // Both lists refresh after an upload/edit/delete; do not attach handlers repeatedly.
  if (window.wikiHub) {
    var previousReload = window.wikiHub.reloadAnalyses;
    window.wikiHub.reloadAnalyses = function () { previousReload(); return loadReviews(); };
  }
  if (window.WikiAdminManage) window.WikiAdminManage.bind(list, {
    onEdit: function (kind, id) { if (byId[id] && window.wikiAdminUpload) window.wikiAdminUpload.edit('analysis', byId[id]); },
    reload: function () { if (window.wikiHub) window.wikiHub.reloadAnalyses(); else loadReviews(); }
  });
  function closeReader(restoreFocus) {
    ++readVersion;
    frame.removeAttribute('src');
    reader.hidden = true;
    if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
    if (restoreFocus && lastOpener && lastOpener.isConnected) lastOpener.focus();
  }
  document.getElementById('reviewReaderClose').addEventListener('click', function () { closeReader(true); });
  list.addEventListener('click', async function (e) {
    var button = e.target.closest('[data-review-open]');
    if (!button) return;
    var item = byId[button.dataset.reviewOpen];
    if (!item) return;
    openReview(item, button);
  });
  async function openReview(item, button) {
    lastOpener = button;
    closeReader(false);
    var version = ++readVersion;
    if (button) button.disabled = true;
    try {
      // Use a local ID-based route; never trust a returned URL to select a destination.
      var response = await fetch('/api/wiki/analysis-materials/' + encodeURIComponent(item.wikiAnalysisMaterialId) + '/download', { credentials: 'same-origin', cache: 'no-store' });
      if (version !== readVersion) return;
      if (response.status === 401 || response.status === 403) {
        alert('복습 과제는 로그인 후 이용하실 수 있습니다.');
        if (typeof window.openLoginModal === 'function') window.openLoginModal();
        return;
      }
      if (!response.ok) throw new Error();
      var ext = (item.originalFileName || '').split('.').pop().toLowerCase();
      if (ext !== 'html' && ext !== 'htm') {
        alert('이 자료는 사이트에서 열람할 수 있는 HTML 과제가 아닙니다.');
        return;
      }
      var text = await response.text();
      if (version !== readVersion) return;
      objectUrl = URL.createObjectURL(new Blob([text], { type: 'text/html;charset=utf-8' }));
      frame.src = objectUrl;
      document.getElementById('reviewReaderTitle').textContent = item.title;
      frame.title = item.title;
      reader.hidden = false;
      reader.scrollIntoView({ block: 'start', behavior: 'auto' });
      document.getElementById('reviewReaderClose').focus({ preventScroll: true });
    } catch (_) {
      if (version === readVersion) alert('자료를 열지 못했습니다. 다시 시도해 주세요.');
    } finally { if (button) button.disabled = false; }
  }
  window.addEventListener('pagehide', function () { closeReader(false); });
  loadReviews();
  function escapeHtml(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]; });
  }
})();

