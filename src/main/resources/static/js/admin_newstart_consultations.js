(function () {
  'use strict';

  var BASE = '/api/admin/newstart/consultations';
  var MAX_ANSWER = 2000;

  var body = document.getElementById('cn-body');
  var msg = document.getElementById('cn-msg');
  var detail = document.getElementById('cn-detail');
  if (!body) return;

  function say(text, ok) {
    msg.textContent = text || '';
    msg.style.color = ok ? '#276749' : '#c53030';
  }
  function el(tag, cls, value) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (value !== undefined && value !== null) node.textContent = value;
    return node;
  }
  function cell(text, cls) {
    var td = el('td', cls, text == null ? '' : String(text));
    return td;
  }
  function shortDate(iso) { return iso ? String(iso).slice(0, 16).replace('T', ' ') : ''; }

  function post(url, data) {
    return fetch(url, {
      method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data || {})
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (json) { return { status: res.status, json: json }; });
    });
  }

  /* ── 목록 ── */
  function row(e) {
    var tr = document.createElement('tr');
    tr.appendChild(cell(shortDate(e.lastMessageAt)));

    var stTd = document.createElement('td');
    stTd.appendChild(el('span', 'cn-state ' + (e.status || ''), e.statusLabel || e.status));
    tr.appendChild(stTd);

    tr.appendChild(cell(e.memberNickname));
    tr.appendChild(cell(e.memberQuestionUnread > 0 ? ('새 질문 ' + e.memberQuestionUnread + '건') : '-',
      e.memberQuestionUnread > 0 ? 'cn-unread' : ''));
    tr.appendChild(cell(e.preview));

    var tdOpen = document.createElement('td');
    var open = el('button', 'cn-open', '상담 열기');
    open.type = 'button';
    open.addEventListener('click', function () { openThread(e.consultationId); });
    tdOpen.appendChild(open);
    tr.appendChild(tdOpen);
    return tr;
  }

  function load() {
    fetch(BASE, { credentials: 'same-origin' })
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error('status ' + res.status)); })
      .then(function (list) {
        body.textContent = '';
        if (!list.length) {
          var tr = document.createElement('tr');
          var td = cell('접수된 상담이 없습니다.');
          td.colSpan = 6;
          tr.appendChild(td);
          body.appendChild(tr);
          return;
        }
        list.forEach(function (e) { body.appendChild(row(e)); });
      })
      .catch(function () { say('목록을 불러오지 못했습니다.', false); });
  }

  /* ── 상세(§3 한 화면) ── */
  function openThread(consultationId) {
    fetch(BASE + '/' + consultationId, { credentials: 'same-origin' })
      .then(function (res) { return res.json().catch(function () { return {}; }).then(function (b) { return { status: res.status, body: b }; }); })
      .then(function (res) {
        if (res.status !== 200 || !res.body.consultationId) { say(res.body.errorMessage || '상담을 불러오지 못했습니다.', false); return; }
        renderThread(res.body);
        load(); /* 열람 시 상태·읽음 갱신 반영 */
      })
      .catch(function () { say('상담을 불러오지 못했습니다.', false); });
  }

  function renderThread(t) {
    detail.textContent = '';
    detail.hidden = false;

    detail.appendChild(el('div', 'cn-h', '회원 닉네임'));
    var nick = el('p', 'cn-field', t.memberNickname + '  ·  상태: ' + (t.statusLabel || t.status));
    detail.appendChild(nick);

    /* 현재 A to Z 단계 */
    detail.appendChild(el('div', 'cn-h', '현재 A to Z 단계'));
    if (t.atozTitle && t.atozUrl) {
      var a = el('a', 'cn-field', (t.atozStep != null ? (t.atozStep + 1) + '. ' : '') + t.atozTitle + ' →');
      a.href = t.atozUrl; a.style.color = '#2b6cb0'; a.style.fontWeight = '700';
      detail.appendChild(a);
    } else {
      detail.appendChild(el('p', 'cn-muted', '이 상담에서 연결된 A to Z 단계가 없습니다.'));
    }

    /* 연결을 허용한 채무상황(§1·§8: linkDebt일 때만) */
    detail.appendChild(el('div', 'cn-h', '회원이 연결을 허용한 채무상황'));
    if (t.debt) {
      var debt = el('div', 'cn-debt');
      if (t.debt.debtSummary) debt.appendChild(el('p', 'cn-field', t.debt.debtSummary));
      if (t.debt.stage) debt.appendChild(el('p', 'cn-field', '단계: ' + t.debt.stage));
      if (!t.debt.debtSummary && !t.debt.stage) debt.appendChild(el('p', 'cn-muted', '연결은 허용했지만 채무상황 입력이 아직 없습니다.'));
      detail.appendChild(debt);
    } else {
      detail.appendChild(el('p', 'cn-muted', '회원이 이번 상담에 채무상황 연결을 허용하지 않았습니다.'));
    }

    /* 관련 지피지기 사례(§3) */
    detail.appendChild(el('div', 'cn-h', '관련 지피지기 실제 사례'));
    if (t.relatedCases && t.relatedCases.length) {
      var ul = el('ul', 'cn-cases');
      t.relatedCases.forEach(function (c) {
        var li = document.createElement('li');
        if (c.url) {
          var link = el('a', null, c.title || '사례 보기');
          link.href = c.url; link.target = '_blank'; link.rel = 'noopener noreferrer';
          li.appendChild(link);
        } else {
          li.appendChild(el('strong', null, c.title || '사례'));
        }
        if (c.summary) li.appendChild(el('div', 'cn-muted', c.summary));
        ul.appendChild(li);
      });
      detail.appendChild(ul);
    } else {
      detail.appendChild(el('p', 'cn-muted', '이 단계에 등록된 실제 사례가 아직 없습니다.'));
    }

    /* 이번 질문 + 이전 상담 대화(시간순) */
    detail.appendChild(el('div', 'cn-h', '상담 대화 (시간순)'));
    var thread = el('div');
    (t.messages || []).forEach(function (m) { thread.appendChild(bubble(m)); });
    if (!(t.messages || []).length) thread.appendChild(el('p', 'cn-muted', '대화가 없습니다.'));
    detail.appendChild(thread);

    detail.appendChild(answerBox(t.consultationId));
    detail.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function bubble(m) {
    if (m.authorRole === 'AI_RESEARCH') {
      var ai = el('div', 'cn-box ai');
      ai.appendChild(el('div', 'cn-tag', '지피지기 조사 결과 (운영자 전용 · 회원에게 자동 전송되지 않음)'));
      ai.appendChild(el('div', null, m.content));
      ai.appendChild(el('div', 'cn-muted', shortDate(m.createdAt)));
      return ai;
    }
    var isMember = m.authorRole === 'MEMBER';
    var box = el('div', 'cn-box ' + (isMember ? 'member' : 'operator'));
    box.appendChild(el('div', 'cn-tag', isMember ? '회원 질문' : '운영자 답변'));
    box.appendChild(el('div', null, m.content));
    box.appendChild(el('div', 'cn-muted', shortDate(m.createdAt)));
    return box;
  }

  /* §4·§5: 조사(운영자 전용) → 읽고 수정·보완 → [회원에게 답변]로만 전달 */
  function answerBox(consultationId) {
    var wrap = el('div');
    wrap.appendChild(el('div', 'cn-h', '운영자 답변'));

    var research = el('button', 'cn-btn ai', '지피지기에게 조사시키기');
    research.type = 'button';
    research.addEventListener('click', function () {
      research.disabled = true;
      say('질문을 익명화해 지피지기에 조사 중…', true);
      post(BASE + '/' + consultationId + '/research', {})
        .then(function (r) {
          research.disabled = false;
          if (r.status === 200 && r.json.messageId) { say('조사 결과를 상담 아래 운영자 전용으로 추가했습니다.', true); openThread(consultationId); }
          else say((r.json && r.json.errorMessage) || '조사하지 못했습니다.', false);
        })
        .catch(function () { research.disabled = false; say('조사하지 못했습니다.', false); });
    });

    var area = el('textarea', 'cn-answer');
    area.maxLength = MAX_ANSWER;
    area.placeholder = '조사 결과와 운영자 경험을 읽고, 회원에게 보낼 답변을 작성·보완하세요. 개인정보 최소·익명 원칙 유지.';

    var send = el('button', 'cn-btn primary', '회원에게 답변');
    send.type = 'button';
    send.addEventListener('click', function () {
      if (!area.value.trim()) { say('답변 내용을 입력해 주세요.', false); return; }
      if (!window.confirm('이 답변을 회원의 비공개 상담 스레드로 전달합니다. 보내시겠습니까?')) return;
      send.disabled = true;
      post(BASE + '/' + consultationId + '/answer', { content: area.value })
        .then(function (r) {
          send.disabled = false;
          if (r.status === 200 && r.json.messageId) { say('회원에게 답변을 전달했습니다.', true); area.value = ''; openThread(consultationId); }
          else { say((r.json && r.json.errorMessage) || '전달하지 못했습니다.', false); send.disabled = false; }
        })
        .catch(function () { send.disabled = false; say('전달하지 못했습니다.', false); });
    });

    wrap.appendChild(research);
    wrap.appendChild(area);
    wrap.appendChild(send);
    return wrap;
  }

  load();
})();
