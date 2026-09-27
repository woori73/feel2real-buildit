(function () {
  'use strict';

  var LIST_URL = '/api/newstart/requests';
  var ADMIN_REVIEW_URL = '/api/admin/newstart/requests/';
  var PAGE_SIZE = 20;

  var KIND_LABEL = {
    QUESTION: '[질문·조사 요청]',
    SITE_IMPROVEMENT: '[사이트 개선 요청]'
  };

  var STATUS_LABEL = {
    RECEIVED: '접수',
    REVIEWING: '검토 중',
    ANSWERED: '답변 완료',
    ACCEPTED: '채택',
    IN_PROGRESS: '반영 진행 중',
    RESOLVED: '해결 완료',
    REJECTED: '거절',
    DUPLICATE: '중복'
  };

  /* 배지 색은 "회원에게 지금 무엇을 보여야 하는가"로 나눈다: 대기 / 진행 / 종료 / 닫힘. */
  var STATUS_TONE = {
    RECEIVED: 'is-open', REVIEWING: 'is-open', ANSWERED: 'is-done', ACCEPTED: 'is-done',
    IN_PROGRESS: 'is-done', RESOLVED: 'is-done', REJECTED: 'is-closed', DUPLICATE: 'is-closed'
  };

  var ADMIN_STATUS_CHOICES = ['RECEIVED', 'REVIEWING', 'ANSWERED', 'ACCEPTED', 'IN_PROGRESS',
    'RESOLVED', 'REJECTED', 'DUPLICATE'];

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

  /* ── 인증 상태: 헤더(account_menu.js)와 동일한 판정 소스를 재사용한다. 채팅과 같은 이유다 —
     /api/auth/me를 따로 호출하면 헤더와 서로 다른 시점의 결과를 볼 수 있다. */
  function loadCurrentUser() {
    var refresh = window.refreshAccountArea;
    if (typeof refresh !== 'function') { showLoginRequired(); return; }
    refresh().then(function (user) {
      if (!user || !user.userId) { showLoginRequired(); return; }
      P.userId = user.userId;
      P.admin = user.role === 'ADMIN';
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
    loadList(false);
  }

  /* ── 목록 ── */
  function listUrl(kind, page) {
    var params = ['page=' + page, 'size=' + PAGE_SIZE];
    if (kind) params.push('kind=' + kind);
    return LIST_URL + '?' + params.join('&');
  }

  function loadList(append) {
    if (P.loading) return;
    P.loading = true;
    showStatus('불러오는 중…');
    fetch(listUrl(P.kind, P.page), { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }); })
      .then(function (data) {
        P.loading = false;
        showStatus('');
        var rows = data.requests || [];
        if (!append) P.list.textContent = '';
        rows.forEach(function (row) { P.list.appendChild(listItem(row)); });
        P.empty.hidden = rows.length > 0 || P.page > 0 || append;
        P.hasMore = !!data.hasMore;
        P.more.hidden = !P.hasMore;
        if (!rows.length && !append) setText(P.status, '등록된 요청이 아직 없습니다.');
      })
      .catch(function () {
        P.loading = false;
        showStatus('목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      });
  }

  function listItem(row) {
    var li = el('li', 'ns-req-item');
    var line = el('div', 'ns-req-line');
    line.appendChild(el('span', 'ns-req-kind', KIND_LABEL[row.kind] || row.kind));
    line.appendChild(el('span', 'ns-req-state ' + (STATUS_TONE[row.status] || ''), STATUS_LABEL[row.status] || row.status));
    li.appendChild(line);
    var btn = el('button', 'ns-req-open', row.title);
    btn.type = 'button';
    btn.setAttribute('aria-label', '요청 상세 보기: ' + row.title);
    btn.addEventListener('click', function () { openDetail(row.id); });
    li.appendChild(btn);
    li.appendChild(el('p', 'ns-req-meta', (row.author || '회원') + ' · ' + shortDate(row.createdAt)));
    return li;
  }

  function shortDate(iso) {
    return iso ? String(iso).slice(0, 16).replace('T', ' ') : '';
  }

  /* ── 상세 ── */
  function openDetail(requestId) {
    fetch(LIST_URL + '/' + requestId, { credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; }); })
      .then(function (res) {
        if (res.status !== 200 || !res.body.request) {
          showStatus(res.body.errorMessage || '요청을 불러오지 못했습니다.');
          return;
        }
        renderDetail(res.body.request);
      })
      .catch(function () { showStatus('상세 정보를 불러오지 못했습니다.'); });
  }

  function field(label, value) {
    if (!value) return null;
    var wrap = el('div', 'ns-req-field');
    wrap.appendChild(el('p', 'ns-req-label', label));
    wrap.appendChild(el('p', 'ns-req-value', value));
    return wrap;
  }

  function renderDetail(req) {
    var host = P.detail;
    host.textContent = '';
    host.hidden = false;

    var line = el('div', 'ns-req-line');
    line.appendChild(el('span', 'ns-req-kind', KIND_LABEL[req.kind] || req.kind));
    line.appendChild(el('span', 'ns-req-state ' + (STATUS_TONE[req.status] || ''), STATUS_LABEL[req.status] || req.status));
    host.appendChild(line);
    host.appendChild(el('h2', 'ns-req-title', req.title));
    host.appendChild(el('p', 'ns-req-meta', (req.author || '회원') + ' · ' + shortDate(req.createdAt)));

    [field('화면 / 위치', req.screenLocation), field('A to Z STEP', req.aToZStep),
      field('내용', req.problem), field('개선 제안', req.proposal)]
      .forEach(function (node) { if (node) host.appendChild(node); });

    if (req.answer) {
      var answerWrap = el('div', 'ns-req-field');
      answerWrap.appendChild(el('p', 'ns-req-label', '운영자 답변'));
      answerWrap.appendChild(el('p', 'ns-req-answer', req.answer));
      if (req.resolvedAt) answerWrap.appendChild(el('p', 'ns-req-meta', '처리일 ' + shortDate(req.resolvedAt)));
      host.appendChild(answerWrap);
    }
    if (req.githubRef || req.resolvedVersion) {
      host.appendChild(el('p', 'ns-req-meta',
        ['GitHub ' + (req.githubRef || '-'), '반영 버전 ' + (req.resolvedVersion || '-')].join(' · ')));
    }

    var logs = req.statusLogs || [];
    if (logs.length) {
      host.appendChild(el('p', 'ns-req-label', '검토 이력'));
      var ul = el('ul', 'ns-req-log');
      logs.forEach(function (log) {
        var label = log.fromStatus
          ? (STATUS_LABEL[log.fromStatus] || log.fromStatus) + ' → ' + (STATUS_LABEL[log.toStatus] || log.toStatus)
          : (STATUS_LABEL[log.toStatus] || log.toStatus);
        ul.appendChild(el('li', null, label + ' · ' + log.actor + ' · ' + shortDate(log.createdAt)
          + (log.reason ? ' · ' + log.reason : '')));
      });
      host.appendChild(ul);
    }

    if (P.admin) host.appendChild(adminPanel(req));
    /* 취소 버튼은 작성자 본인/ADMIN만 보여준다. 표시판정일 뿐이며 실제 권한은 서버가
       DELETE 요청의 principal로 다시 검사한다(403). */
    if (P.admin || (req.authorId && req.authorId === P.userId)) host.appendChild(withdrawButton(req.id));
    host.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function withdrawButton(requestId) {
    var wrap = el('div', 'ns-req-actions ns-req-withdraw');
    var btn = el('button', 'ns-btn ns-btn-ghost', '이 요청 취소(삭제)');
    btn.type = 'button';
    btn.addEventListener('click', function () {
      btn.disabled = true;
      fetch(LIST_URL + '/' + requestId, { method: 'DELETE', credentials: 'same-origin' })
        .then(function (r) { return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; }); })
        .then(function (res) {
          if (res.status === 200) {
            P.detail.hidden = true;
            P.page = 0;
            loadList(false);
            showStatus('요청을 취소했습니다.');
            return;
          }
          btn.disabled = false;
          showStatus(res.body.errorMessage || '취소하지 못했습니다.');
        })
        .catch(function () { btn.disabled = false; showStatus('취소하지 못했습니다.'); });
    });
    wrap.appendChild(btn);
    return wrap;
  }

  function adminPanel(req) {
    var panel = el('div', 'ns-req-admin');
    panel.appendChild(el('p', 'ns-req-label', '운영자 검토'));
    var select = el('select', 'ns-input');
    ADMIN_STATUS_CHOICES.forEach(function (code) {
      var option = el('option', null, STATUS_LABEL[code] + ' (' + code + ')');
      option.value = code;
      if (code === req.status) option.selected = true;
      select.appendChild(option);
    });
    var answer = el('textarea', 'ns-input');
    answer.rows = 3;
    answer.placeholder = '운영자 답변(회원에게 노출)';
    var reason = el('textarea', 'ns-input');
    reason.rows = 2;
    reason.placeholder = '사유 — 거절/중복일 때 필수';
    var github = el('input', 'ns-input');
    github.type = 'text';
    github.maxLength = 120;
    github.placeholder = 'GitHub issue/PR (반영 시 수기 기입)';
    var version = el('input', 'ns-input');
    version.type = 'text';
    version.maxLength = 40;
    version.placeholder = '반영 버전';
    var err = el('p', 'ns-req-err');
    err.hidden = true;
    var btn = el('button', 'ns-btn ns-btn-primary', '검토 결과 저장');
    btn.type = 'button';
    btn.addEventListener('click', function () {
      btn.disabled = true;
      fetch(ADMIN_REVIEW_URL + req.id + '/review', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: select.value, answer: answer.value, reason: reason.value,
          githubRef: github.value, resolvedVersion: version.value
        })
      })
        .then(function (r) { return r.json().catch(function () { return {}; }); })
        .then(function (data) {
          if (data.status === 'ok' && data.request) { renderDetail(data.request); return; }
          setText(err, data.errorMessage || '저장하지 못했습니다.');
          err.hidden = false;
          btn.disabled = false;
        })
        .catch(function () {
          setText(err, '저장하지 못했습니다.');
          err.hidden = false;
          btn.disabled = false;
        });
    });
    [select, answer, reason, github, version, btn].forEach(function (node) { panel.appendChild(node); });
    panel.appendChild(err);
    return panel;
  }

  /* ── 쓰기 ── */
  function submitRequest(e) {
    e.preventDefault();
    var kind = P.kindSelect.value;
    var payload = {
      kind: kind,
      title: P.titleInput.value,
      problem: P.problem.value,
      proposal: P.proposal.value,
      screenLocation: kind === 'SITE_IMPROVEMENT' ? P.location.value : '',
      aToZStep: kind === 'SITE_IMPROVEMENT' ? P.step.value : ''
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
        if (res.status === 200 && res.body.request) {
          P.form.reset();
          P.write.hidden = true;
          P.page = 0;
          loadList(false);
          showStatus('요청이 등록되었습니다. 운영자가 검토합니다.');
          return;
        }
        /* 403 = THANKS_MEMBER 미부여 — 서버 안내 문구를 그대로 보여 판단 근거를 준다. */
        setText(P.writeErr, res.body.errorMessage || '등록하지 못했습니다.');
        P.writeErr.hidden = false;
      })
      .catch(function () {
        P.submit.disabled = false;
        setText(P.writeErr, '등록하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        P.writeErr.hidden = false;
      });
  }

  function syncKindFields() {
    P.improveFields.hidden = P.kindSelect.value !== 'SITE_IMPROVEMENT';
  }

  function init() {
    P.login = id('ns-req-login');
    P.list = id('ns-req-list');
    P.empty = id('ns-req-empty');
    P.more = id('ns-req-more');
    P.status = id('ns-req-status');
    P.detail = id('ns-req-detail');
    P.newBtn = id('ns-req-new');
    P.write = id('ns-req-write');
    P.form = id('ns-req-form');
    P.kindSelect = id('ns-req-kind');
    P.titleInput = id('ns-req-title');
    P.improveFields = id('ns-req-improve-fields');
    P.location = id('ns-req-location');
    P.step = id('ns-req-step');
    P.problem = id('ns-req-problem');
    P.proposal = id('ns-req-proposal');
    P.submit = id('ns-req-submit');
    P.writeErr = id('ns-req-write-err');
    if (!P.list) return;

    P.kind = '';
    P.page = 0;
    P.loading = false;
    P.hasMore = false;

    Array.prototype.forEach.call(document.querySelectorAll('[data-ns-req-kind]'), function (tab) {
      tab.addEventListener('click', function () {
        Array.prototype.forEach.call(document.querySelectorAll('[data-ns-req-kind]'), function (other) {
          other.classList.remove('is-on');
          other.setAttribute('aria-selected', 'false');
        });
        tab.classList.add('is-on');
        tab.setAttribute('aria-selected', 'true');
        P.kind = tab.getAttribute('data-ns-req-kind') || '';
        P.page = 0;
        P.detail.hidden = true;
        loadList(false);
      });
    });

    P.more.addEventListener('click', function () {
      if (!P.hasMore) return;
      P.page += 1;
      loadList(true);
    });
    P.newBtn.addEventListener('click', function () {
      P.write.hidden = false;
      P.write.open = true;
      syncKindFields();
    });
    P.kindSelect.addEventListener('change', syncKindFields);
    P.form.addEventListener('submit', submitRequest);

    loadCurrentUser();
  }

  window.F2RNewStartRequests = {
    KIND_LABEL: KIND_LABEL,
    STATUS_LABEL: STATUS_LABEL,
    STATUS_TONE: STATUS_TONE,
    listUrl: listUrl
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
