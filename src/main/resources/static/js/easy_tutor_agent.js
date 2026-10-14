(function () {
  'use strict';

  const state = { businessId: null, sessionId: null, businesses: [], busy: false };
  const el = id => document.getElementById(id);
  const api = path => '/api/tutor/easy' + path;
  const won = value => Number(value || 0).toLocaleString('ko-KR') + '원';
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[ch]);

  async function responseError(response, fallback) {
    try {
      const body = await response.json();
      return body.message || body.detail || body.error || fallback;
    } catch (_) {
      try { return (await response.text()) || fallback; } catch (_) { return fallback; }
    }
  }

  async function json(url, options) {
    const response = await fetchAuth(url, options);
    if (!response.ok) throw new Error(await responseError(response, '요청을 처리하지 못했습니다.'));
    if (response.status === 204) return null;
    return response.json();
  }

  async function guardTutor() {
    const user = await ensureCurrentUser();
    if (!user || user.role !== 'TUTOR') {
      location.href = user ? dashboardPathForRole(user.role) : '/?login=required&returnUrl=%2Ftutor%2Feasy';
      throw new Error('TUTOR 로그인이 필요합니다.');
    }
    return user;
  }

  function sessionKey() { return 'easyAgentSession:' + state.businessId; }

  async function loadBusinesses() {
    state.businesses = await json(api('/businesses'));
    const select = el('businessSelect');
    select.innerHTML = '';
    state.businesses.forEach(item => select.add(new Option(item.name, item.businessId)));
    if (!state.businesses.length) {
      setStatus('연결된 장부 없음', true);
      el('chatInput').disabled = true;
      el('sendButton').disabled = true;
      el('uploadButton').disabled = true;
      el('chatMessages').innerHTML = '<div class="chat-empty">활성 Academy와 연결된 Easy 장부가 없습니다. 관리자에게 연결을 요청해 주세요.</div>';
      return false;
    }
    state.businessId = Number(select.value);
    return true;
  }

  async function restoreOrCreateSession() {
    state.sessionId = Number(sessionStorage.getItem(sessionKey())) || null;
    if (state.sessionId) {
      try {
        const current = await json(api('/agent/sessions/' + state.sessionId + '?businessId=' + state.businessId));
        renderHistory(current.messages || []);
        return;
      } catch (_) {
        sessionStorage.removeItem(sessionKey());
      }
    }
    const created = await json(api('/agent/sessions?businessId=' + state.businessId), { method: 'POST' });
    state.sessionId = created.sessionId;
    sessionStorage.setItem(sessionKey(), String(state.sessionId));
    renderHistory([]);
  }

  function setStatus(text, offline) {
    const node = el('connectionState');
    node.textContent = text;
    node.classList.toggle('offline', Boolean(offline));
  }

  function setBusy(value) {
    state.busy = value;
    el('chatMessages').setAttribute('aria-busy', String(value));
    el('sendButton').disabled = value || !state.businessId;
    el('chatInput').disabled = value || !state.businessId;
  }

  function renderHistory(messages) {
    const log = el('chatMessages');
    log.innerHTML = '';
    if (!messages.length) {
      log.innerHTML = '<div class="chat-empty">무엇을 도와드릴까요? CSV를 올리거나 장부에 관해 질문해 보세요.</div>';
      return;
    }
    messages.forEach(renderMessage);
    scrollChat();
  }

  function payloadOf(message) {
    if (!message || !message.payloadJson) return null;
    try { return JSON.parse(message.payloadJson); } catch (_) { return null; }
  }

  function renderMessage(message) {
    const log = el('chatMessages');
    const empty = log.querySelector('.chat-empty');
    if (empty) empty.remove();
    const bubble = document.createElement('article');
    bubble.className = 'message ' + (message.role === 'USER' ? 'user' : 'assistant');
    bubble.textContent = message.content || '';
    if (message.createdAt) {
      const meta = document.createElement('span');
      meta.className = 'message-meta';
      meta.textContent = new Date(message.createdAt).toLocaleString('ko-KR');
      bubble.appendChild(meta);
    }
    log.appendChild(bubble);
    const payload = payloadOf(message);
    if (message.kind === 'PROPOSAL' && payload) renderProposal(payload);
    if (message.kind === 'RESULT' && Array.isArray(payload)) renderTransactions(payload);
    if (message.kind === 'RESULT' && payload && Array.isArray(payload.rows)) renderBookPayload(payload);
  }

  function renderProposal(action) {
    const card = document.createElement('section');
    card.className = 'proposal-card';
    card.dataset.actionId = action.actionId;
    card.dataset.status = action.status || 'PROPOSED';
    const type = action.type === 'SELECT_ACCOUNT' ? '계정과목 변경 제안' : '장부 반영 제안';
    card.innerHTML = '<h3>' + esc(type) + '</h3><p>' + esc(action.summary) + '</p>'
      + '<div class="proposal-scope">금액·거래일·원본 CSV는 변경하지 않습니다.'
      + (action.expiresAt ? ' · ' + esc(new Date(action.expiresAt).toLocaleString('ko-KR')) + '까지' : '') + '</div>'
      + '<div class="proposal-actions"><button type="button" class="proposal-reject">거절</button>'
      + '<button type="button" class="proposal-approve">승인하고 반영</button></div><div class="proposal-result"></div>';
    card.querySelector('.proposal-approve').addEventListener('click', () => decideAction(card, 'approve'));
    card.querySelector('.proposal-reject').addEventListener('click', () => decideAction(card, 'reject'));
    el('chatMessages').appendChild(card);
  }

  async function decideAction(card, decision) {
    if (card.dataset.status !== 'PROPOSED') return;
    const buttons = card.querySelectorAll('button');
    buttons.forEach(button => { button.disabled = true; });
    const result = card.querySelector('.proposal-result');
    result.textContent = decision === 'approve' ? '승인 처리 중…' : '거절 처리 중…';
    try {
      const action = await json(api('/agent/actions/' + card.dataset.actionId + '/' + decision + '?businessId=' + state.businessId), { method: 'POST' });
      card.dataset.status = action.status;
      result.textContent = action.status === 'APPLIED' ? '승인되어 반영했습니다.' : '제안을 거절했습니다.';
      if (action.status === 'APPLIED') await Promise.all([loadBatches(), loadTrialBalance()]);
    } catch (error) {
      result.textContent = error.message;
      card.dataset.status = 'FAILED';
      result.style.color = '#b42318';
    }
  }

  function renderTransactions(rows) {
    if (!rows.length) return;
    const list = document.createElement('section');
    list.className = 'transaction-results';
    rows.slice(0, 20).forEach(row => {
      const card = document.createElement('article');
      card.className = 'transaction-card';
      const label = row.description || row.counterparty || '거래 #' + row.rowId;
      card.innerHTML = '<div class="transaction-top"><strong>' + esc(label) + '</strong><span class="amount">' + esc(won(row.amount)) + '</span></div>'
        + '<div class="transaction-sub">' + esc(row.occurredOn || '') + ' · 거래 ID ' + esc(row.rowId)
        + (row.matchedAccountName ? ' · 추천 ' + esc(row.matchedAccountName) : ' · 미분류') + '</div>';
      const button = document.createElement('button');
      button.type = 'button';
      button.textContent = '이 거래 계정과목 바꾸기';
      button.addEventListener('click', () => {
        el('chatInput').value = '거래 ' + row.rowId + '을(를) [계정과목]으로 분류해줘';
        el('chatInput').focus();
      });
      card.appendChild(button);
      list.appendChild(card);
    });
    el('chatMessages').appendChild(list);
  }

  function trialRows(report) { return report.rows || report.accounts || []; }

  function renderBookPayload(report, append) {
    const rows = trialRows(report);
    if (!rows.length) return;
    const target = el('bookResult');
    const table = '<table class="trial-table"><thead><tr><th>계정</th><th>차변</th><th>대변</th></tr></thead><tbody>'
      + rows.slice(0, 50).map(row => '<tr><td>' + esc(row.accountName || row.name || '계정') + '</td><td>'
        + esc(won(row.debit || row.debitTotal)) + '</td><td>' + esc(won(row.credit || row.creditTotal)) + '</td></tr>').join('')
      + '</tbody></table>';
    if (append) target.insertAdjacentHTML('beforeend', table); else target.innerHTML = table;
  }

  async function send(text) {
    const value = String(text || '').trim();
    if (!value || state.busy) return;
    el('chatError').hidden = true;
    renderMessage({ role: 'USER', kind: 'TEXT', content: value });
    setBusy(true);
    try {
      const result = await json(api('/agent/sessions/' + state.sessionId + '/messages?businessId=' + state.businessId), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: value })
      });
      renderMessage(result.message);
      if (result.pendingAction && result.message.kind !== 'PROPOSAL') renderProposal(result.pendingAction);
      setStatus('연결됨', false);
    } catch (error) {
      el('chatError').textContent = error.message;
      el('chatError').hidden = false;
      setStatus('요청 실패', true);
    } finally {
      setBusy(false);
      scrollChat();
    }
  }

  function scrollChat() { const log = el('chatMessages'); log.scrollTop = log.scrollHeight; }

  async function upload(event) {
    event.preventDefault();
    const file = el('csvFile').files[0];
    if (!file) return;
    const data = new FormData();
    data.append('file', file);
    el('uploadButton').disabled = true;
    el('uploadResult').textContent = '원본을 보존하고 분류 규칙을 적용하는 중…';
    try {
      const batch = await json(api('/imports?businessId=' + state.businessId), { method: 'POST', body: data });
      el('uploadResult').textContent = batch.totalRows + '건 업로드 완료 · 장부 반영 전';
      el('csvFile').value = '';
      await loadBatches();
      await send('배치 ' + batch.batchId + '에서 아직 분류하지 않은 거래 보여줘');
    } catch (error) {
      el('uploadResult').textContent = '업로드 실패: ' + error.message;
    } finally { el('uploadButton').disabled = false; }
  }

  async function loadBatches() {
    const batches = await json(api('/imports?businessId=' + state.businessId));
    const target = el('batchList');
    target.innerHTML = '';
    if (!batches.length) { target.innerHTML = '<p class="side-note">업로드된 CSV가 없습니다.</p>'; return; }
    for (const summary of batches.slice(0, 8)) {
      let batch = summary;
      if (!Array.isArray(batch.rows)) batch = await json(api('/imports/' + batch.batchId + '?businessId=' + state.businessId));
      const card = document.createElement('article');
      card.className = 'batch-card';
      card.innerHTML = '<div class="batch-title"><span>' + esc(batch.sourceName || 'CSV') + '</span><span>' + esc(batch.status) + '</span></div>'
        + '<div class="batch-meta">배치 ' + esc(batch.batchId) + ' · ' + esc(batch.totalRows) + '건 · 반영 ' + esc(batch.confirmedRows) + '건</div>';
      (batch.rows || []).slice(0, 5).forEach(row => {
        const item = document.createElement('div');
        item.className = 'batch-row';
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = '#' + row.rowId + ' ' + (row.description || row.counterparty || '거래') + ' · ' + won(row.amount);
        button.addEventListener('click', () => {
          el('chatInput').value = '배치 ' + batch.batchId + '의 거래 ' + row.rowId + '을(를) 임차료로 분류해줘';
          el('chatInput').focus();
        });
        item.appendChild(button);
        const meta = document.createElement('div');
        meta.textContent = (row.matchedAccountName || '미분류') + ' · ' + row.status;
        item.appendChild(meta);
        card.appendChild(item);
      });
      target.appendChild(card);
    }
  }

  function monthRange() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const last = new Date(year, now.getMonth() + 1, 0).getDate();
    return { from: year + '-' + month + '-01', to: year + '-' + month + '-' + String(last).padStart(2, '0'), label: year + '년 ' + Number(month) + '월' };
  }

  async function loadTrialBalance() {
    if (!state.businessId) return;
    const range = monthRange();
    el('bookPeriod').textContent = range.label;
    const query = new URLSearchParams({ businessId: state.businessId, from: range.from, to: range.to });
    try {
      const report = await json(api('/accounting/trial-balance?' + query));
      const rows = trialRows(report);
      el('bookResult').innerHTML = '<p class="' + (report.balanced ? 'balance-ok' : 'agent-error') + '">'
        + (report.balanced ? '차변·대변이 일치합니다.' : '차변·대변 차이를 확인해 주세요.') + '</p>';
      renderBookPayload({ rows }, true);
    } catch (error) { el('bookResult').innerHTML = '<p class="agent-error">' + esc(error.message) + '</p>'; }
  }

  async function changeBusiness() {
    state.businessId = Number(el('businessSelect').value);
    setStatus('연결 중', false);
    await restoreOrCreateSession();
    await Promise.all([loadBatches(), loadTrialBalance()]);
    setStatus('연결됨', false);
  }

  function bind() {
    el('businessSelect').addEventListener('change', () => changeBusiness().catch(showFatal));
    el('chatForm').addEventListener('submit', event => {
      event.preventDefault(); const input = el('chatInput'); const value = input.value; input.value = ''; send(value);
    });
    el('chatInput').addEventListener('keydown', event => {
      if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); el('chatForm').requestSubmit(); }
    });
    document.querySelectorAll('[data-prompt]').forEach(button => button.addEventListener('click', () => send(button.dataset.prompt)));
    el('uploadForm').addEventListener('submit', upload);
    el('refreshBatches').addEventListener('click', () => loadBatches().catch(showFatal));
    el('trialBalanceButton').addEventListener('click', () => { send('시산표 확인해줘'); loadTrialBalance(); });
  }

  function showFatal(error) { el('chatError').textContent = error.message; el('chatError').hidden = false; setStatus('오류', true); }

  async function init() {
    await guardTutor();
    bind();
    if (!await loadBusinesses()) return;
    await changeBusiness();
  }

  window.EasyTutorAgent = { payloadOf, trialRows, monthRange };
  document.addEventListener('DOMContentLoaded', () => init().catch(showFatal));
})();
