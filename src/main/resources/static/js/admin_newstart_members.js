(function () {
  'use strict';

  // NewStart 회원관리(ADMIN): 검색 → 상세 → 1회용 임시 비밀번호 초기화.
  // 임시 비밀번호는 응답 본문으로 한 번만 받는다. 이 파일도 값을 변수·storage·로그에 남기지 않고
  // 화면을 닫으면 DOM에서 지운다(재조회 API가 서버에 없다).
  var PAGE_SIZE = 20;
  var TYPE_LABELS = { THANKS_MEMBER: '감사회원', GENERAL: '일반회원' };
  var STATUS_LABELS = { ACTIVE: '활성', RESET_REQUIRED: '임시 비밀번호(재설정 필요)' };

  var form = document.getElementById('nm-search-form');
  var input = document.getElementById('nm-query');
  var body = document.getElementById('nm-body');
  var msg = document.getElementById('nm-msg');
  var detail = document.getElementById('nm-detail');
  var resetBtn = document.getElementById('nm-reset-btn');
  var tempBox = document.getElementById('nm-temp');
  var tempValue = document.getElementById('nm-temp-value');
  var tempClose = document.getElementById('nm-temp-close');
  var prevBtn = document.getElementById('nm-prev');
  var nextBtn = document.getElementById('nm-next');
  var pageInfo = document.getElementById('nm-page-info');
  var totalInfo = document.getElementById('nm-total');
  if (!form || !body || !detail) return;

  var state = { query: '', page: 0, totalPages: 0, userId: null };

  function say(text, ok) {
    if (!msg) return;
    msg.textContent = text || '';
    msg.style.color = ok ? '#276749' : '#c53030';
  }

  function cell(text, className) {
    var td = document.createElement('td');
    td.textContent = text == null ? '' : String(text);
    if (className) td.className = className;
    return td;
  }

  function badge(label, className) {
    var td = document.createElement('td');
    var span = document.createElement('span');
    span.className = 'nm-badge ' + className;
    span.textContent = label;
    td.appendChild(span);
    return td;
  }

  // 서버는 LocalDateTime 문자열("2026-10-02T11:22:00")을 보낸다. 타임존 변환 없이 표시만 한다.
  function fmt(value) {
    if (!value) return '기록 없음';
    return String(value).slice(0, 16).replace('T', ' ');
  }

  function get(url) {
    return window.fetchAuth(url).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (json) {
        return { ok: res.ok, status: res.status, json: json };
      });
    });
  }

  function post(url) {
    return window.fetchAuth(url, { method: 'POST' }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (json) {
        return { ok: res.ok, status: res.status, json: json };
      });
    });
  }

  // keepMessage: 초기화 성공 안내처럼 이미 뜬 문구를 목록 갱신이 덮어쓰지 않게 한다.
  function search(query, page, keepMessage) {
    state.query = query;
    state.page = page;
    var url = '/api/admin/newstart/members?query=' + encodeURIComponent(query)
      + '&page=' + page + '&size=' + PAGE_SIZE;
    if (keepMessage) {
      get(url).then(function (r) { if (r.ok) renderList(r.json); }).catch(function () {});
      return;
    }
    say('검색 중…', true);
    get(url).then(function (r) {
      if (!r.ok) { say('검색하지 못했습니다. (' + r.status + ')', false); return; }
      say('', true);
      renderList(r.json);
    }).catch(function () { say('검색하지 못했습니다.', false); });
  }

  function renderList(page) {
    var members = (page && page.members) || [];
    state.totalPages = (page && page.totalPages) || 0;
    body.textContent = '';
    if (!members.length) {
      var tr = document.createElement('tr');
      var td = cell('조건에 맞는 회원이 없습니다. 로그인 ID나 카페 닉네임 일부로 다시 검색해 주세요.');
      td.colSpan = 7;
      tr.appendChild(td);
      body.appendChild(tr);
    }
    members.forEach(function (m) { body.appendChild(memberRow(m)); });
    pageInfo.textContent = (state.page + 1) + ' / ' + Math.max(state.totalPages, 1);
    totalInfo.textContent = '총 ' + ((page && page.totalElements) || 0) + '명';
    prevBtn.disabled = state.page <= 0;
    nextBtn.disabled = state.page + 1 >= state.totalPages;
  }

  function memberRow(m) {
    var tr = document.createElement('tr');
    if (m.userId === state.userId) tr.className = 'nm-row-selected';
    tr.appendChild(cell(m.loginId));
    tr.appendChild(cell(m.cafeNickname));
    tr.appendChild(badge(TYPE_LABELS[m.memberType] || m.memberType,
      m.memberType === 'THANKS_MEMBER' ? 'thanks' : 'general'));
    tr.appendChild(badge(STATUS_LABELS[m.accountStatus] || m.accountStatus,
      m.accountStatus === 'RESET_REQUIRED' ? 'reset' : 'active'));
    tr.appendChild(cell(fmt(m.joinedAt)));
    tr.appendChild(cell(fmt(m.lastLoginAt)));
    var action = document.createElement('td');
    var open = document.createElement('button');
    open.type = 'button';
    open.className = 'nm-btn';
    open.textContent = '상세';
    open.addEventListener('click', function () {
      // 다른 회원의 상세를 여는 순간 앞선 초기화의 임시 값은 화면에서 지운다(재조회 불가 규칙).
      hideTemp();
      openDetail(m.userId);
    });
    action.appendChild(open);
    tr.appendChild(action);
    return tr;
  }

  function openDetail(userId) {
    state.userId = userId;
    get('/api/admin/newstart/members/' + userId).then(function (r) {
      if (!r.ok) { say('상세 정보를 불러오지 못했습니다. (' + r.status + ')', false); return; }
      detail.hidden = false;
      fill('nm-d-loginId', r.json.loginId);
      fill('nm-d-nickname', r.json.cafeNickname);
      fill('nm-d-memberType', TYPE_LABELS[r.json.memberType] || r.json.memberType);
      fill('nm-d-accountStatus', STATUS_LABELS[r.json.accountStatus] || r.json.accountStatus);
      fill('nm-d-joinedAt', fmt(r.json.joinedAt));
      fill('nm-d-lastLoginAt', fmt(r.json.lastLoginAt));
      fill('nm-d-rosterLinked', r.json.thanksRosterLinked ? '연결됨' : '연결 없음');
      fill('nm-d-lastReset', r.json.lastResetAt
        ? fmt(r.json.lastResetAt) + ' · ' + (r.json.lastResetBy || '') : '초기화 기록 없음');
      var hint = document.getElementById('nm-d-hint');
      hint.textContent = r.json.passwordResetRequired
        ? '이 회원은 현재 임시 비밀번호 상태입니다. 회원이 직접 새 비밀번호를 정할 때까지 NewStart 기능이 차단됩니다.'
        : '비밀번호를 초기화하면 회원은 다음 로그인에서 새 비밀번호를 반드시 정해야 합니다.';
      resetBtn.disabled = false;
    }).catch(function () { say('상세 정보를 불러오지 못했습니다.', false); });
  }

  function fill(id, value) {
    var el = document.getElementById(id);
    if (el) el.textContent = value == null ? '-' : String(value);
  }

  resetBtn.addEventListener('click', function () {
    if (!state.userId) { say('먼저 회원 목록에서 [상세]를 눌러 대상을 선택해 주세요.', false); return; }
    var userId = state.userId;
    if (!window.confirm('선택한 회원의 비밀번호를 1회용 임시 비밀번호로 초기화합니다.\n'
      + '임시 비밀번호는 한 번만 표시되고 다시 볼 수 없습니다.\n 카페에서 본인 확인을 마친 뒤에만 진행하세요.')) {
      return;
    }
    resetBtn.disabled = true;
    post('/api/admin/newstart/members/' + userId + '/password-reset').then(function (r) {
      resetBtn.disabled = false;
      if (!r.ok) { say('초기화하지 못했습니다. (' + r.status + ')', false); return; }
      openDetail(userId);
      if (tempValue) tempValue.textContent = r.json.temporaryPassword || '';
      if (tempBox) tempBox.hidden = false;
      say('초기화했습니다. 표시된 임시 비밀번호를 회원에게 전달하세요.', true);
      // 목록 배지 상태만 갱신한다(성공 안내 문구를 지우지 않는다).
      search(state.query, state.page, true);
    }).catch(function () {
      resetBtn.disabled = false;
      say('초기화하지 못했습니다.', false);
    });
  });

  function hideTemp() {
    if (tempValue) tempValue.textContent = '';
    if (tempBox) tempBox.hidden = true;
  }

  if (tempClose) tempClose.addEventListener('click', function () {
    hideTemp();
    say('임시 비밀번호 표시를 지웠습니다. 같은 값을 다시 볼 수 없습니다.', true);
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    openDetailClose();
    search((input.value || '').trim(), 0);
  });

  function openDetailClose() {
    hideTemp();
    detail.hidden = true;
    state.userId = null;
  }

  prevBtn.addEventListener('click', function () {
    if (state.page > 0) search(state.query, state.page - 1);
  });
  nextBtn.addEventListener('click', function () {
    if (state.page + 1 < state.totalPages) search(state.query, state.page + 1);
  });

  // 화면을 열면 즉시 최근 회원 목록이 뜬다 — 검색어를 요구하지 않는다.
  search('', 0);
})();
