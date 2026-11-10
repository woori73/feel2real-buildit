/**
 * F2R 장부 — 자동수집 연결 관리(/easy/connections).
 * 권한은 서버(/api/admin/** + 사업체 접근 검증)가 최종 결정한다. 이 화면은 비밀번호·인증서·OTP를 받지 않는다.
 */
(function () {
  const BASE = '/api/admin/easy/collections';
  const READINESS = {
    READY: ['연결됨', 'ok'],
    NOT_CONFIGURED: ['인증 등록 필요', 'warn'],
    UNVERIFIED: ['연동 준비 중(계약·검증 전)', 'warn'],
    NEEDS_REAUTH: ['재인증 필요', 'warn'],
    DISABLED: ['꺼짐', 'off']
  };
  const AUTH = {
    NOT_REGISTERED: '인증 미등록',
    REGISTERED: '인증 참조 등록됨',
    NEEDS_REAUTH: '재인증 필요',
    DISCONNECTED: '연결 해제됨'
  };
  const RUN_STATUS = { RUNNING: '진행 중', SUCCESS: '완료', PARTIAL: '일부 실패', FAILED: '실패' };
  let connections = [];

  function businessId() { return document.getElementById('business').value; }

  function el(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function fmtTime(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-'; }

  function message(id, text, isError) {
    const node = document.getElementById(id);
    node.textContent = text;
    node.className = 'msg' + (isError ? ' err' : '');
    node.style.display = text ? 'block' : 'none';
  }

  async function call(method, path, body) {
    const opts = { method, headers: {} };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const sep = path.includes('?') ? '&' : '?';
    const res = await fetchAuth(BASE + path + sep + 'businessId=' + encodeURIComponent(businessId()), opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!res.ok) {
      const msg = (data && (data.message || data.error)) || text || ('요청 실패 (' + res.status + ')');
      throw new Error(msg);
    }
    return data;
  }

  async function loadConnections() {
    message('listMsg', '');
    try {
      connections = await call('GET', '/connections');
      render();
    } catch (e) {
      message('listMsg', e.message, true);
    }
  }

  function render() {
    const box = document.getElementById('connections');
    box.innerHTML = '';
    document.getElementById('connCount').textContent = connections.length + '개';
    if (!connections.length) {
      box.appendChild(el('p', 'desc', '등록된 연결이 없습니다. 위에서 은행 계좌를 등록하세요.'));
      return;
    }
    connections.forEach(c => box.appendChild(row(c)));
  }

  function row(c) {
    const wrap = el('div', 'conn-row');
    const head = el('div', 'conn-head');
    head.appendChild(el('strong', null, c.label + ' · ' + c.sourceName));
    const readiness = c.connectionStatus === 'DISCONNECTED'
      ? ['연결 해제됨', 'off'] : (READINESS[c.readiness] || [c.readiness, 'off']);
    head.appendChild(el('span', 'badge ' + readiness[1], readiness[0]));
    wrap.appendChild(head);

    const meta = el('div', 'conn-meta');
    const parts = [];
    if (c.organization) parts.push('기관코드 ' + c.organization + (c.clientType === 'P' ? ' · 개인' : ' · 기업'));
    if (c.accountMasked) parts.push('계좌 ' + c.accountMasked);
    parts.push(AUTH[c.authStatus] || c.authStatus || '-');
    parts.push('자동수집 ' + (c.enabled ? '켜짐' : '꺼짐'));
    parts.push('마지막 성공 ' + fmtTime(c.lastSuccessAt));
    if (c.latestRunStatus) {
      parts.push('최근 실행 ' + (RUN_STATUS[c.latestRunStatus] || c.latestRunStatus)
        + ' · 장부 저장 ' + (c.latestRunLedgerSavedCount || 0) + '건 · 검토 ' + (c.latestRunLedgerReviewCount || 0) + '건');
    }
    if (c.lastErrorCode) parts.push('마지막 오류 ' + c.lastErrorCode);
    meta.textContent = parts.join(' | ');
    wrap.appendChild(meta);

    const actions = el('div', 'conn-actions');
    const disconnected = c.connectionStatus === 'DISCONNECTED';
    const isBank = c.sourceCode === 'CODEF_BANK';
    if (isBank) actions.appendChild(button('설정 수정', () => edit(c)));
    actions.appendChild(button(c.authStatus === 'NEEDS_REAUTH' || disconnected ? '재인증 등록' : '인증 참조 변경', () => reauth(c)));
    if (!disconnected) {
      const run = button('지금 수집', () => runNow(c));
      run.disabled = c.readiness !== 'READY';
      if (run.disabled) run.title = '연결됨 상태에서만 실행됩니다.';
      actions.appendChild(run);
      actions.appendChild(button(c.enabled ? '자동수집 끄기' : '자동수집 켜기', () => toggle(c)));
      actions.appendChild(button('연결 해제', () => disconnect(c)));
    }
    actions.appendChild(button('수집 이력', () => history(c)));
    wrap.appendChild(actions);
    return wrap;
  }

  function button(label, onClick) {
    const b = el('button', 'btn-secondary', label);
    b.type = 'button';
    b.addEventListener('click', async () => {
      b.disabled = true;
      try { await onClick(); } finally { b.disabled = false; }
    });
    return b;
  }

  function edit(c) {
    document.getElementById('editId').value = c.connectionId;
    document.getElementById('label').value = c.label || '';
    document.getElementById('organization').value = c.organization || '0004';
    document.getElementById('clientType').value = c.clientType || 'B';
    document.getElementById('account').value = '';
    document.getElementById('account').placeholder = '변경할 때만 다시 입력 (현재 ' + (c.accountMasked || '-') + ')';
    document.getElementById('connectedId').value = '';
    document.getElementById('formTitle').textContent = '은행 연결 설정 수정';
    document.getElementById('cancelEdit').style.display = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function resetForm() {
    ['editId', 'label', 'account', 'connectedId'].forEach(id => { document.getElementById(id).value = ''; });
    document.getElementById('account').placeholder = '';
    document.getElementById('formTitle').textContent = '은행 계좌 연결 등록';
    document.getElementById('cancelEdit').style.display = 'none';
  }

  async function save() {
    message('formMsg', '');
    const id = document.getElementById('editId').value;
    const body = {
      label: document.getElementById('label').value.trim(),
      organization: document.getElementById('organization').value,
      clientType: document.getElementById('clientType').value,
      account: document.getElementById('account').value.replace(/[^0-9]/g, ''),
      connectedId: document.getElementById('connectedId').value.trim() || null
    };
    if (!body.account) {
      message('formMsg', '계좌번호를 입력하세요(수정할 때도 확인을 위해 다시 입력합니다).', true);
      return;
    }
    try {
      if (id) await call('PUT', '/connections/' + id + '/bank', body);
      else await call('POST', '/connections/bank', body);
      message('formMsg', '저장했습니다. 계좌번호는 화면에 뒤 4자리만 표시됩니다.', false);
      resetForm();
      await loadConnections();
    } catch (e) {
      message('formMsg', e.message, true);
    }
  }

  async function reauth(c) {
    const ref = prompt('CODEF 계정 등록으로 받은 연결 ID(connectedId)를 입력하세요.\n비밀번호·인증서·OTP는 입력하지 마세요.');
    if (!ref) return;
    try {
      await call('PUT', '/connections/' + c.connectionId + '/auth', { connectionRef: ref.trim() });
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function runNow(c) {
    message('listMsg', '수집 중입니다…', false);
    try {
      const run = await call('POST', '/connections/' + c.connectionId + '/run');
      message('listMsg', '수집 ' + run.fetchedCount + '건 · 장부 저장 ' + run.ledgerSavedCount + '건 · 중복 '
        + run.duplicateCount + '건 · 검토 ' + run.ledgerReviewCount + '건 · 분류 ' + run.ledgerClassifiedCount
        + '건 · 실패 ' + run.errorCount + '건', run.status === 'FAILED');
      await loadConnections();
      await history(c);
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function toggle(c) {
    try {
      await call('PUT', '/connections/' + c.connectionId + '/enabled', { enabled: !c.enabled });
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function disconnect(c) {
    if (!confirm('연결을 해제할까요?\n조회가 멈추고 인증 참조가 비활성화됩니다. 이미 저장된 장부·증빙은 그대로 남습니다.')) return;
    try {
      await call('POST', '/connections/' + c.connectionId + '/disconnect');
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function history(c) {
    const card = document.getElementById('historyCard');
    card.style.display = '';
    document.getElementById('historyTitle').textContent = '수집 이력 — ' + c.label;
    const table = document.getElementById('runs');
    table.innerHTML = '';
    document.getElementById('recommend').innerHTML = '';
    let runs = [];
    try { runs = await call('GET', '/connections/' + c.connectionId + '/runs'); }
    catch (e) { table.appendChild(el('caption', null, e.message)); return; }
    const head = el('tr');
    ['시작', '상태', '응답 수신', '장부 저장 완료일', '수집', '장부 저장', '기존 중복', '미분류·검토', '분류 완료', '실패', ''].forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    if (!runs.length) {
      const tr = el('tr'); const td = el('td', null, '수집 이력이 없습니다.'); td.colSpan = 11; tr.appendChild(td); table.appendChild(tr);
      return;
    }
    runs.forEach(r => {
      const tr = el('tr');
      [fmtTime(r.startedAt), RUN_STATUS[r.status] || r.status, fmtTime(r.fetchedAt), r.ledgerSavedThrough || '-',
        r.fetchedCount, r.ledgerSavedCount, r.duplicateCount, r.ledgerReviewCount, r.ledgerClassifiedCount, r.errorCount]
        .forEach(v => tr.appendChild(el('td', null, String(v))));
      const td = el('td');
      if (r.ledgerReviewCount > 0) td.appendChild(button('AI 추천', () => recommend(r)));
      tr.appendChild(td);
      table.appendChild(tr);
      if (r.errorSummary) {
        const er = el('tr'); const ed = el('td', null, '오류: ' + r.errorSummary); ed.colSpan = 11; er.appendChild(ed); table.appendChild(er);
      }
    });
  }

  async function recommend(run) {
    const box = document.getElementById('recommend');
    box.innerHTML = '';
    try {
      const result = await call('GET', '/runs/' + run.runId + '/recommendations');
      box.appendChild(el('p', 'desc', result.message));
      const ul = el('ul', 'item-list');
      result.items.forEach(item => {
        ul.appendChild(el('li', 'item', item.direction + ' ' + Easy.fmtWon(item.amount) + ' · '
          + (item.counterparty || '-') + ' · ' + (item.description || '-') + ' → '
          + (item.accountName ? item.accountName + (item.reason ? ' (' + item.reason + ')' : '') : '추천 없음')));
      });
      box.appendChild(ul);
      const link = el('a', null, '검토함에서 반영하기 ›'); link.href = '/easy/reviews';
      box.appendChild(link);
    } catch (e) { box.appendChild(el('p', 'desc', e.message)); }
  }

  async function init() {
    await Easy.guardAdmin();
    const businesses = await Easy.loadBusinesses();
    const select = document.getElementById('business');
    businesses.forEach(b => select.add(new Option(b.name, String(b.businessId))));
    select.addEventListener('change', () => { document.getElementById('historyCard').style.display = 'none'; loadConnections(); });
    document.getElementById('saveBtn').addEventListener('click', save);
    document.getElementById('cancelEdit').addEventListener('click', resetForm);
    if (businesses.length) await loadConnections();
  }

  init().catch(e => message('listMsg', e.message, true));
})();
