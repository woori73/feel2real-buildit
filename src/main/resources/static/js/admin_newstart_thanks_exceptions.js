(function () {
  'use strict';
  var body = document.getElementById('tx-body');
  var msg = document.getElementById('tx-msg');
  if (!body) return;

  function say(text, ok) {
    msg.textContent = text || '';
    msg.style.color = ok ? '#276749' : '#c53030';
  }
  function cell(text, cls) {
    var td = document.createElement('td');
    td.textContent = text == null ? '' : String(text);
    if (cls) td.className = cls;
    return td;
  }
  function post(url, data) {
    return fetch(url, {
      method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data || {})
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (json) { return { status: res.status, json: json }; });
    });
  }
  function act(url, data, okText) {
    post(url, data).then(function (r) {
      if (r.json && r.json.status === 'ok') { say(okText, true); load(); }
      else say((r.json && r.json.errorMessage) || '처리하지 못했습니다.', false);
    }).catch(function () { say('처리하지 못했습니다.', false); });
  }
  function mailLabel(s) {
    switch (s) {
      case 'SENT': return '발송됨';
      case 'FAILED': return '발송 실패';
      case 'NOT_CONFIGURED': return '메일 미설정';
      default: return '대기';
    }
  }

  function row(e) {
    var tr = document.createElement('tr');
    tr.appendChild(cell(e.occurredAt ? String(e.occurredAt).slice(0, 16).replace('T', ' ') : ''));
    tr.appendChild(cell(e.reasonLabel, 'tx-reason'));
    tr.appendChild(cell(e.nicknameInput));
    tr.appendChild(cell((e.userNickname || '') + ' · user ' + e.userId));
    var rosterCell = document.createElement('td');
    var selectedRosterId = e.rosterId;
    if (e.rosterId != null) {
      rosterCell.textContent = e.rosterNickname || ('roster ' + e.rosterId);
    } else if (e.candidates && e.candidates.length) {
      var sel = document.createElement('select');
      var first = document.createElement('option');
      first.value = ''; first.textContent = '명단 선택';
      sel.appendChild(first);
      e.candidates.forEach(function (c) {
        var o = document.createElement('option');
        o.value = c.rosterId; o.textContent = c.nickname + (c.linked ? ' (이미 연결됨)' : '') + ' #' + c.rosterId;
        sel.appendChild(o);
      });
      sel.addEventListener('change', function () { selectedRosterId = sel.value ? Number(sel.value) : null; });
      rosterCell.appendChild(sel);
    } else {
      rosterCell.textContent = '(일치 명단 없음)';
    }
    tr.appendChild(rosterCell);
    tr.appendChild(cell(mailLabel(e.emailStatus)));
    var act1 = document.createElement('td');
    var ok = document.createElement('button');
    ok.type = 'button'; ok.className = 'tx-btn primary'; ok.textContent = '연결 승인';
    ok.addEventListener('click', function () {
      if (!selectedRosterId) { say('연결할 명단을 선택해 주세요.', false); return; }
      if (!window.confirm('카페에서 확인했습니까?\n승인하면 이 회원에게 THANKS_MEMBER가 부여되고 명단과 1:1로 연결됩니다.')) return;
      act('/api/admin/newstart/thanks-exceptions/' + e.verificationId + '/resolve', { rosterId: selectedRosterId },
        '연결 승인했습니다. 회원은 바로 감사회원 기능을 사용합니다.');
    });
    var no = document.createElement('button');
    no.type = 'button'; no.className = 'tx-btn'; no.textContent = '종료';
    no.addEventListener('click', function () {
      var note = window.prompt('종료 사유(선택, 200자 이내)', '');
      if (note === null) return;
      act('/api/admin/newstart/thanks-exceptions/' + e.verificationId + '/dismiss', { note: note }, '종료했습니다.');
    });
    act1.appendChild(ok); act1.appendChild(no);
    tr.appendChild(act1);
    return tr;
  }

  function load() {
    fetch('/api/admin/newstart/thanks-exceptions?status=OPEN', { credentials: 'same-origin' })
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error('status ' + res.status)); })
      .then(function (list) {
        body.textContent = '';
        if (!list.length) {
          var tr = document.createElement('tr');
          var td = cell('처리할 인증 예외가 없습니다.');
          td.colSpan = 7;
          tr.appendChild(td);
          body.appendChild(tr);
          return;
        }
        list.forEach(function (e) { body.appendChild(row(e)); });
      })
      .catch(function () { say('목록을 불러오지 못했습니다.', false); });
  }
  load();
})();
