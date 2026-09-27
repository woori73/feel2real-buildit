(function () {
  'use strict';

  var FEED_URL = '/api/newstart/galmuri';
  var PAGE_SIZE = 20;

  /* 서버가 내린 tone만 쓴다(§4 신뢰 등급을 화면이 자체 판정하면 라벨과 색이 어긋난다). */
  var TONE_CLASS = { official: 'is-official', operator: 'is-operator', case: 'is-case' };

  var P = {};

  function id(name) { return document.getElementById(name); }

  function setText(el, value) { if (el) el.textContent = value || ''; }

  function el(tag, cls, value) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (value !== undefined && value !== null) node.textContent = value;
    return node;
  }

  function showStatus(message) { setText(P.status, message || ''); }

  /* 인증 판정 소스는 헤더(account_menu.js)와 동일 — 채팅·질문요청과 같은 이유다. */
  function loadCurrentUser() {
    var refresh = window.refreshAccountArea;
    if (typeof refresh !== 'function') { showLoginRequired(); return; }
    refresh().then(function (user) {
      if (!user || !user.userId) { showLoginRequired(); return; }
      enterFeed();
    }).catch(function () { showLoginRequired(); });
  }

  function showLoginRequired() {
    P.login.hidden = false;
    P.list.hidden = true;
  }

  function enterFeed() {
    P.login.hidden = true;
    loadFeed(false);
  }

  function feedUrl(page) {
    var params = ['page=' + page, 'size=' + PAGE_SIZE];
    if (P.category) params.push('category=' + encodeURIComponent(P.category));
    return FEED_URL + '?' + params.join('&');
  }

  function loadFeed(append) {
    if (P.loading) return;
    P.loading = true;
    showStatus('불러오는 중…');
    fetch(feedUrl(P.page), { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }); })
      .then(function (data) {
        P.loading = false;
        showStatus('');
        var rows = data.items || [];
        if (!append) P.list.textContent = '';
        rows.forEach(function (row) { P.list.appendChild(card(row)); });
        P.empty.hidden = rows.length > 0 || P.page > 0 || append;
        P.hasMore = !!data.hasMore;
        P.more.hidden = !P.hasMore;
        if (!rows.length && !append) setText(P.status, '갈무리된 항목이 아직 없습니다.');
      })
      .catch(function () {
        P.loading = false;
        showStatus('목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      });
  }

  function card(row) {
    var li = el('li', 'ns-gm-item');
    var line = el('div', 'ns-gm-line');
    line.appendChild(el('span', 'ns-gm-label ' + (TONE_CLASS[row.tone] || ''), row.label));
    if (row.category) line.appendChild(el('span', 'ns-gm-cat', row.category));
    li.appendChild(line);
    li.appendChild(el('h2', 'ns-gm-title', row.title));
    if (row.summary) li.appendChild(el('p', 'ns-gm-summary', row.summary));
    var btn = el('button', 'ns-gm-open', '전문 보기');
    btn.type = 'button';
    btn.setAttribute('aria-label', '갈무리 상세 보기: ' + row.title);
    btn.addEventListener('click', function () { openDetail(row.id); });
    li.appendChild(btn);
    li.appendChild(el('p', 'ns-gm-meta', metaLine(row)));
    return li;
  }

  function metaLine(row) {
    var parts = [];
    if (row.authority) parts.push('출처 ' + row.authority);
    if (row.sourceDate) parts.push('기준일 ' + row.sourceDate);
    if (row.effectiveTo) parts.push('효력 종료 ' + row.effectiveTo);
    return parts.join(' · ');
  }

  function openDetail(knowledgeId) {
    fetch(FEED_URL + '/' + knowledgeId, { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; }); })
      .then(function (res) {
        if (res.status !== 200 || !res.body.item) {
          showStatus(res.body.errorMessage || '항목을 불러오지 못했습니다.');
          return;
        }
        renderDetail(res.body.item);
      })
      .catch(function () { showStatus('상세 정보를 불러오지 못했습니다.'); });
  }

  function field(label, value) {
    if (!value) return null;
    var wrap = el('div', 'ns-gm-field');
    wrap.appendChild(el('p', 'ns-gm-label-text', label));
    wrap.appendChild(el('p', 'ns-gm-content', value));
    return wrap;
  }

  function renderDetail(item) {
    var host = P.detail;
    host.textContent = '';
    host.hidden = false;

    var line = el('div', 'ns-gm-line');
    line.appendChild(el('span', 'ns-gm-label ' + (TONE_CLASS[item.tone] || ''), item.label));
    if (item.category) line.appendChild(el('span', 'ns-gm-cat', item.category));
    host.appendChild(line);
    host.appendChild(el('h2', 'ns-gm-title', item.title));
    if (item.summary) host.appendChild(el('p', 'ns-gm-summary', item.summary));

    [field('갈무리 내용', item.content), field('출처 기관', item.authority)]
      .forEach(function (node) { if (node) host.appendChild(node); });

    var tags = item.tags || [];
    if (tags.length) {
      var wrap = el('div', 'ns-gm-tags');
      tags.forEach(function (tag) { wrap.appendChild(el('span', 'ns-gm-tag', tag)); });
      host.appendChild(wrap);
    }

    host.appendChild(el('p', 'ns-gm-meta', metaLine(item)));
    if (item.effectiveFrom) host.appendChild(el('p', 'ns-gm-meta', '효력 시작 ' + item.effectiveFrom));

    var link = linkRow(item.sourceUrl);
    if (link) host.appendChild(link);

    /* 갈무리는 read-only feed다 — 쓰기·삭제 UI가 없다(§6: 자유 게시판 아님). */
    host.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /* 원출처 링크는 운영자 승인 데이터라도 scheme을 검사한다 — javascript:/data:가 href로 들어오면
     클릭 실행 경로가 되므로 http/https만 받고 그 외에는 링크 자체를 렌더하지 않는다. */
  function safeHttpUrl(url) {
    return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : null;
  }

  function linkRow(url) {
    var href = safeHttpUrl(url);
    if (!href) return null;
    var wrap = el('div', 'ns-gm-field');
    var a = el('a', 'ns-btn ns-btn-ghost', '원출처 열기');
    // 속성으로 기록: 마크업만 봐도 target/rel이 확인 가능해야 역링크 회피가 사라지지 않는다.
    a.setAttribute('href', href);
    a.setAttribute('target', '_blank');
    a.setAttribute('rel', 'noopener noreferrer');
    wrap.appendChild(el('p', 'ns-gm-label-text', '원출처'));
    wrap.appendChild(a);
    return wrap;
  }

  function init() {
    P.login = id('ns-gm-login');
    P.list = id('ns-gm-list');
    P.empty = id('ns-gm-empty');
    P.more = id('ns-gm-more');
    P.status = id('ns-gm-status');
    P.detail = id('ns-gm-detail');
    if (!P.list) return;

    P.category = '';
    P.page = 0;
    P.loading = false;
    P.hasMore = false;

    P.more.addEventListener('click', function () {
      if (!P.hasMore) return;
      P.page += 1;
      loadFeed(true);
    });

    loadCurrentUser();
  }

  window.F2RNewStartGalmuri = { TONE_CLASS: TONE_CLASS, feedUrl: feedUrl, metaLine: metaLine };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
