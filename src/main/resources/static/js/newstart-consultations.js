(function () {
  'use strict';

  var LIST_URL = '/api/newstart/consultations';
  var MAX_CHARS = 2000;

  var STATUS_TONE = {
    NEW: 'is-new',
    AWAITING_ANSWER: 'is-await',
    OPERATOR_ANSWERED: 'is-answered',
    MEMBER_FOLLOWUP: 'is-followup'
  };

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
  function shortDate(iso) { return iso ? String(iso).slice(0, 16).replace('T', ' ') : ''; }

  /* ── 인증 상태: 헤더(account_menu.js)와 동일한 판정 소스를 재사용. 상담은 로그인한 모든 회원이 쓰고, 본인 소유 판정은 서버 API가 한다. */
  function loadCurrentUser() {
    var refresh = window.refreshAccountArea;
    if (typeof refresh !== 'function') { showLoginRequired(); return; }
    refresh().then(function (user) {
      if (!user || !user.userId) { showLoginRequired(); return; }
      enterBoard();
    }).catch(function () { showLoginRequired(); });
  }

  function showLoginRequired() {
    P.login.hidden = false;
    P.list.hidden = true;
    P.newBtn.hidden = true;
  }

  function enterBoard() {
    P.login.hidden = true;
    P.newBtn.hidden = false;
    loadList();
  }

  /* ── 목록 ── */
  function loadList() {
    if (P.loading) return;
    P.loading = true;
    showStatus('불러오는 중…');
    fetch(LIST_URL, { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return null; }).then(function (b) { return { status: r.status, body: b }; }); })
      .then(function (res) {
        P.loading = false;
        if (res.status === 401 || res.status === 403) { showLoginRequired(); return; }
        if (res.status !== 200 || !Array.isArray(res.body)) { showStatus('목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); return; }
        showStatus('');
        renderList(res.body);
      })
      .catch(function () { P.loading = false; showStatus('목록을 불러오지 못했습니다.'); });
  }

  function renderList(rows) {
    P.list.textContent = '';
    P.empty.hidden = rows.length > 0;
    // 첫 방문(상담 0건)이면 곧바로 작성할 수 있도록 작성 폼을 연다.
    if (rows.length === 0 && P.write && P.write.hidden) { P.write.hidden = false; P.write.open = true; }
    rows.forEach(function (row) { P.list.appendChild(listItem(row)); });
  }

  function listItem(row) {
    var li = el('li', 'ns-cns-item');
    var line = el('div', 'ns-cns-line');
    line.appendChild(el('span', 'ns-cns-state ' + (STATUS_TONE[row.status] || ''), row.statusLabel || row.status));
    if (row.newAnswerCount > 0) line.appendChild(el('span', 'ns-cns-badge', '새 답변 ' + row.newAnswerCount + '건'));
    li.appendChild(line);
    var btn = el('button', 'ns-cns-open', row.preview || '상담 보기');
    btn.type = 'button';
    btn.setAttribute('aria-label', '상담 열기: ' + (row.preview || ''));
    btn.addEventListener('click', function () { openDetail(row.consultationId); });
    li.appendChild(btn);
    var meta = (row.linkDebt ? '채무상황 연결됨 · ' : '') + shortDate(row.lastMessageAt);
    li.appendChild(el('p', 'ns-cns-meta', meta));
    return li;
  }

  /* ── 상세 스레드 ── */
  function openDetail(consultationId) {
    fetch(LIST_URL + '/' + consultationId, { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; }); })
      .then(function (res) {
        if (res.status !== 200 || !res.body.consultationId) {
          showStatus(res.body.errorMessage || '상담을 불러오지 못했습니다.');
          return;
        }
        renderThread(res.body);
        /* 읽음 처리되었으므로 배지/목록을 조용히 갱신한다. */
        loadList();
      })
      .catch(function () { showStatus('상담을 불러오지 못했습니다.'); });
  }

  function renderThread(thread) {
    var host = P.detail;
    host.textContent = '';
    host.hidden = false;

    var line = el('div', 'ns-cns-line');
    line.appendChild(el('span', 'ns-cns-state ' + (STATUS_TONE[thread.status] || ''), thread.statusLabel || thread.status));
    host.appendChild(line);
    host.appendChild(el('h2', 'ns-cns-title', '상담 #' + thread.consultationId
      + (thread.linkDebt ? ' · 내 채무상황 연결됨' : '')));

    /* A to Z는 ADMIN 전용 화면이라 회원 화면에는 단계 링크를 두지 않는다(서버도 회원 응답에 URL을 싣지 않음). */

    var threadWrap = el('div', 'ns-cns-thread');
    (thread.messages || []).forEach(function (m) { threadWrap.appendChild(messageBubble(m)); });
    if (!(thread.messages || []).length) threadWrap.appendChild(el('p', 'ns-cns-note', '아직 대화가 없습니다.'));
    host.appendChild(threadWrap);

    host.appendChild(replyForm(thread.consultationId));
    host.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function messageBubble(m) {
    var isMember = m.authorRole === 'MEMBER';
    var box = el('div', 'ns-cns-msg ' + (isMember ? 'member' : 'operator'));
    box.appendChild(el('p', 'ns-cns-msg-role', isMember ? '나의 질문' : '운영자 답변'));
    box.appendChild(el('p', null, m.content));
    box.appendChild(el('p', 'ns-cns-msg-time', shortDate(m.createdAt)));
    return box;
  }

  function replyForm(consultationId) {
    var form = el('form', 'ns-cns-reply');
    var area = el('textarea', 'ns-input');
    area.rows = 4;
    area.maxLength = MAX_CHARS;
    area.required = true;
    area.placeholder = '운영자 답변을 보고 추가로 질문할 내용을 적어 주세요.';
    var err = el('p', 'ns-cns-err');
    err.hidden = true;
    var btn = el('button', 'ns-btn ns-btn-primary', '추가 질문 보내기');
    btn.type = 'submit';
    form.appendChild(area);
    form.appendChild(btn);
    form.appendChild(err);
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      btn.disabled = true;
      err.hidden = true;
      fetch(LIST_URL + '/' + consultationId + '/messages', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: area.value })
      })
        .then(function (r) { return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; }); })
        .then(function (res) {
          if (res.status === 200 && res.body.consultationId) { renderThread(res.body); loadList(); return; }
          setText(err, res.body.errorMessage || '보내지 못했습니다.');
          err.hidden = false;
          btn.disabled = false;
        })
        .catch(function () { setText(err, '보내지 못했습니다. 잠시 후 다시 시도해 주세요.'); err.hidden = false; btn.disabled = false; });
    });
    return form;
  }

  /* ── 새 상담 작성 ── */
  function submitCreate(e) {
    e.preventDefault();
    var payload = {
      content: P.content.value,
      linkDebt: P.linkDebt.checked
    };
    setText(P.writeErr, ''); P.writeErr.hidden = true;
    P.submit.disabled = true;
    fetch(LIST_URL, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; }); })
      .then(function (res) {
        P.submit.disabled = false;
        if (res.status === 201 && res.body.consultationId) {
          P.form.reset();
          P.write.hidden = true;
          loadList();
          renderThread(res.body);
          showStatus('상담이 전달되었습니다. 운영자가 확인해 답변합니다.');
          return;
        }
        setText(P.writeErr, res.body.errorMessage || '보내지 못했습니다.');
        P.writeErr.hidden = false;
      })
      .catch(function () {
        P.submit.disabled = false;
        setText(P.writeErr, '보내지 못했습니다. 잠시 후 다시 시도해 주세요.');
        P.writeErr.hidden = false;
      });
  }

  function init() {
    P.login = id('ns-cns-login');
    P.list = id('ns-cns-list');
    P.empty = id('ns-cns-empty');
    P.status = id('ns-cns-status');
    P.detail = id('ns-cns-detail');
    P.newBtn = id('ns-cns-new');
    P.write = id('ns-cns-write');
    P.form = id('ns-cns-form');
    P.content = id('ns-cns-content');
    P.linkDebt = id('ns-cns-linkdebt');
    P.submit = id('ns-cns-submit');
    P.writeErr = id('ns-cns-write-err');
    if (!P.list) return;

    P.loading = false;

    P.newBtn.addEventListener('click', function () {
      P.write.hidden = false;
      P.write.open = true;
      P.content.focus();
    });
    P.form.addEventListener('submit', submitCreate);

    loadCurrentUser();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
