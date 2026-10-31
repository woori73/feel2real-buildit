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
    community: ['커뮤니티', '시험 후기와 자료를 나누고 궁금한 점을 남겨 주세요.'],
    live: ['한 줄 인사', '함께 공부하는 분들에게 짧은 인사를 남겨 주세요.']
  };
  var navs = sidebar.querySelectorAll('[data-wiki-nav]');
  var panels = document.querySelectorAll('[data-wiki-panel]');
  var toggle = document.getElementById('wikiMenuToggle');
  var title = document.getElementById('wikiPaneTitle');
  var description = document.getElementById('wikiPaneDescription');
  var list = document.getElementById('reviewList');
  var search = document.getElementById('reviewSearch');
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
    if (key !== 'review') closeReader(false);
    if (focus) title.focus({ preventScroll: true });
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
    list.innerHTML = '<p class="muted">복습 과제를 불러오는 중입니다.</p>';
    try {
      var response = await fetch('/api/wiki/analysis-materials?grade=' + currentGrade());
      if (!response.ok) throw new Error();
      var items = await response.json();
      var canManage = window.WikiAdminManage ? await window.WikiAdminManage.isAdmin() : false;
      if (version !== loadVersion) return;
      admin = canManage;
      reviews = items.filter(function (item) { return /복습|자습/.test(item.title || ''); });
      byId = {};
      reviews.forEach(function (item) { byId[String(item.wikiAnalysisMaterialId)] = item; });
      renderReviews();
    } catch (_) {
      if (version === loadVersion) list.innerHTML = '<p class="error">복습 과제를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>';
    }
  }
  function renderReviews() {
    var term = search.value.trim().toLowerCase();
    var items = reviews.filter(function (it) { return [it.title, it.year, it.examType, it.description].join(' ').toLowerCase().indexOf(term) !== -1; });
    if (!items.length) {
      list.innerHTML = '<p class="muted">' + (term ? '검색어에 맞는 복습 과제가 없습니다.' : '이 학년의 복습 과제를 준비하고 있습니다.') + '</p>';
      return;
    }
    list.innerHTML = items.map(function (it) {
      var id = String(it.wikiAnalysisMaterialId);
      return '<article class="review-card"><button type="button" class="review-open" data-review-open="' + escapeHtml(id) + '">'
        + '<span class="file-badge">고' + escapeHtml(it.grade) + ' · 복습 과제</span>'
        + '<strong>' + escapeHtml(it.title) + '</strong>'
        + '<span class="file-meta">문제 풀기 · 정답과 해설 확인 →</span></button>'
        + (admin ? window.WikiAdminManage.buttonsHtml('analysis', id) : '') + '</article>';
    }).join('');
  }
  search.addEventListener('input', renderReviews);
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
    lastOpener = button;
    closeReader(false);
    var version = ++readVersion;
    button.disabled = true;
    try {
      // Use a local ID-based route; never trust a returned URL to select a destination.
      var response = await fetch('/api/wiki/analysis-materials/' + encodeURIComponent(item.wikiAnalysisMaterialId) + '/download', { credentials: 'same-origin' });
      if (version !== readVersion) return;
      if (response.status === 401 || response.status === 403) {
        alert('복습 과제는 로그인 후 이용하실 수 있습니다.');
        if (typeof window.openLoginModal === 'function') window.openLoginModal();
        return;
      }
      if (!response.ok) throw new Error();
      var ext = (item.originalFileName || '').split('.').pop().toLowerCase();
      if (ext !== 'html' && ext !== 'htm') {
        alert('이 자료는 HTML 열람 파일이 아닙니다. 분석 자료 메뉴에서 내려받아 주세요.');
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
    } finally { button.disabled = false; }
  });
  window.addEventListener('pagehide', function () { closeReader(false); });
  loadReviews();
  function escapeHtml(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]; });
  }
})();
