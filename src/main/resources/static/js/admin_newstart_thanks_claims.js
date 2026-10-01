(function () {
  'use strict';
  var body = document.getElementById('tc-body');
  var msg = document.getElementById('tc-msg');
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
  function flag(label, on) {
    var span = document.createElement('div');
    span.textContent = label + ': ' + (on ? '일치' : '불일치');
    span.className = on ? 'tc-ok' : 'tc-no';
    return span;
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

  function row(c) {
    var tr = document.createElement('tr');
    tr.appendChild(cell('#' + c.claimId + (c.requestedAt ? ' · ' + String(c.requestedAt).slice(0, 16).replace('T', ' ') : '')));
    tr.appendChild(cell((c.userNickname || '') + ' (' + (c.userName || '') + ') · ' + (c.loginId || '') + ' · user ' + c.userId));
    tr.appendChild(cell((c.rosterNickname || '') + ' · ' + (c.rosterMaskedId || '')));
    tr.appendChild(cell(c.verifyCode, 'tc-code'));
    var info = document.createElement('td');
    info.appendChild(flag('이메일 접두부', c.emailPrefixMatch));
    info.appendChild(flag('닉네임', c.nicknameEqualsRoster));
    tr.appendChild(info);
    var act1 = document.createElement('td');
    var ok = document.createElement('button');
    ok.type = 'button'; ok.className = 'tc-btn primary'; ok.textContent = '승인';
    ok.addEventListener('click', function () {
      if (!window.confirm('카페 쪽지/댓글의 인증코드와 보낸 계정을 확인했습니까?\n승인하면 이 회원에게 THANKS_MEMBER가 부여됩니다.')) return;
      act('/api/admin/newstart/thanks-claims/' + c.claimId + '/approve', {}, '승인했습니다. 회원은 다음 요청부터 감사회원 기능을 사용합니다.');
    });
    var no = document.createElement('button');
    no.type = 'button'; no.className = 'tc-btn'; no.textContent = '반려';
    no.addEventListener('click', function () {
      var reason = window.prompt('반려 사유(선택, 200자 이내)', '');
      if (reason === null) return;
      act('/api/admin/newstart/thanks-claims/' + c.claimId + '/reject', { reason: reason }, '반려했습니다.');
    });
    act1.appendChild(ok); act1.appendChild(no);
    tr.appendChild(act1);
    return tr;
  }

  function load() {
    fetch('/api/admin/newstart/thanks-claims?status=PENDING', { credentials: 'same-origin' })
      .then(function (res) { return res.ok ? res.json() : Promise.reject(new Error('status ' + res.status)); })
      .then(function (list) {
        body.textContent = '';
        if (!list.length) {
          var tr = document.createElement('tr');
          var td = cell('대기 중인 본인확인 요청이 없습니다.');
          td.colSpan = 6;
          tr.appendChild(td);
          body.appendChild(tr);
          return;
        }
        list.forEach(function (c) { body.appendChild(row(c)); });
      })
      .catch(function () { say('목록을 불러오지 못했습니다.', false); });
  }
  load();
})();
