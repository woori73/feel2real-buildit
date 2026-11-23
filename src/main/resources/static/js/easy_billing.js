/**
 * /easy/billing — 수강료 청구 링크 만들기·회수 + 통장 입금 대조(시험 모드).
 */
(function () {
  const API = '/api/admin/easy/billing';
  const REASONS = {
    CODE_AMOUNT: '확인 번호·금액 일치',
    NAME_AMOUNT: '학생 이름·금액 일치',
    AMOUNT_MISMATCH: '번호나 이름은 맞지만 금액이 다름',
    AMOUNT_ONLY: '금액만 같음',
    MULTIPLE_BILLS: '맞는 청구가 여러 개',
    MULTIPLE_DEPOSITS: '같은 청구에 맞는 입금이 여러 개'
  };
  const LINK_STATES = { NONE: '링크 없음', ACTIVE: '링크 열림', EXPIRED: '기한 지남', REVOKED: '회수됨' };

  async function init() {
    await Easy.guardAdmin();
    const status = await (await fetchAuth(API + '/status')).json();
    if (!status.enabled) {
      document.getElementById('offCard').style.display = 'block';
      return;
    }
    document.getElementById('onArea').style.display = 'block';
    const businesses = await Easy.loadBusinesses();
    const sel = document.getElementById('mBusiness');
    businesses.forEach(b => {
      const opt = document.createElement('option');
      opt.value = String(b.businessId);
      opt.textContent = b.name;
      sel.appendChild(opt);
    });
    const today = new Date();
    const iso = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    document.getElementById('bMonth').value = iso(today).slice(0, 7);
    document.getElementById('mTo').value = iso(today);
    document.getElementById('mFrom').value = iso(new Date(today.getTime() - 30 * 86400000));
    document.getElementById('bMonth').addEventListener('change', loadBills);
    document.getElementById('btnRun').addEventListener('click', runMatch);
    document.getElementById('btnCopy').addEventListener('click', copyUrl);
    await Promise.all([loadBills(), loadMatches()]);
  }

  async function post(path, body) {
    return fetchAuth(API + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {})
    });
  }

  function row(left, right, cls) {
    const div = document.createElement('div');
    div.className = 'row';
    const a = document.createElement('span');
    a.className = cls || 's';
    a.textContent = left;
    div.appendChild(a);
    if (right instanceof Node) {
      div.appendChild(right);
    } else {
      const b = document.createElement('span');
      b.className = cls === 't' ? 'amt' : 's';
      b.textContent = right || '';
      div.appendChild(b);
    }
    return div;
  }

  function button(label, cls, onClick) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = cls;
    btn.textContent = label;
    btn.addEventListener('click', onClick);
    return btn;
  }

  function empty(ul, text) {
    ul.innerHTML = '';
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = text;
    ul.appendChild(li);
  }

  async function loadBills() {
    const month = document.getElementById('bMonth').value;
    const ul = document.getElementById('billList');
    if (!month) return;
    const res = await fetchAuth(API + '/links?month=' + encodeURIComponent(month));
    if (!res.ok) { empty(ul, '청구 목록을 불러오지 못했습니다.'); return; }
    const rows = await res.json();
    if (!rows.length) { empty(ul, '이 달에 등록된 청구가 없습니다. 수납 관리에서 먼저 등록하세요.'); return; }
    ul.innerHTML = '';
    rows.forEach(r => {
      const li = document.createElement('li');
      li.className = 'item';
      li.appendChild(row(r.studentName + ' · ' + Easy.fmtMonth(r.targetMonth),
        '남은 금액 ' + Easy.fmtWon(r.balance), 't'));
      const state = LINK_STATES[r.linkState] || r.linkState;
      li.appendChild(row(r.businessName + ' · ' + state
        + (r.linkState === 'ACTIVE' ? ' · 확인 번호 ' + r.depositorCode + ' · ' + String(r.expiresAt).slice(0, 10) + '까지' : ''), ''));
      const btns = document.createElement('span');
      if (r.balance > 0) {
        btns.appendChild(button(r.linkState === 'ACTIVE' ? '새로 만들기' : '링크 만들기', 'btn-edit-link', () => createLink(r)));
      }
      if (r.linkState === 'ACTIVE') {
        btns.appendChild(button('회수', 'btn-danger-link', () => revokeLink(r)));
      }
      li.appendChild(row(r.balance > 0 ? '' : '납부 완료', btns));
      ul.appendChild(li);
    });
  }

  async function createLink(r) {
    Easy.hideMsg('linkError');
    if (r.linkState === 'ACTIVE' && !confirm('이미 보낸 링크는 열리지 않게 됩니다. 새로 만들까요?')) return;
    const res = await post('/links', {
      paymentId: r.paymentId,
      bankName: document.getElementById('bBank').value,
      bankAccountNo: document.getElementById('bAccount').value,
      bankAccountHolder: document.getElementById('bHolder').value,
      ttlDays: Number(document.getElementById('bTtl').value) || null
    });
    if (!res.ok) { Easy.showError('linkError', await res.text() || '링크를 만들지 못했습니다.'); return; }
    const created = await res.json();
    document.getElementById('newUrl').value = location.origin + created.url;
    document.getElementById('newCode').textContent = created.depositorCode;
    document.getElementById('newLinkBox').style.display = 'block';
    await loadBills();
  }

  async function revokeLink(r) {
    if (!confirm('이 링크를 회수할까요? 학부모가 더 이상 열 수 없습니다.')) return;
    await post('/links/' + encodeURIComponent(r.linkId) + '/revoke');
    await loadBills();
  }

  async function copyUrl() {
    const input = document.getElementById('newUrl');
    try {
      await navigator.clipboard.writeText(input.value);
    } catch (_) {
      input.select();
      document.execCommand('copy');
    }
    document.getElementById('btnCopy').textContent = '복사했습니다';
  }

  async function runMatch() {
    Easy.hideMsg('runError');
    Easy.hideMsg('runOk');
    const res = await post('/matches/run', {
      businessId: Number(document.getElementById('mBusiness').value),
      from: document.getElementById('mFrom').value || null,
      to: document.getElementById('mTo').value || null
    });
    if (!res.ok) { Easy.showError('runError', await res.text() || '대조하지 못했습니다.'); return; }
    const s = await res.json();
    Easy.showOk('runOk', '통장 입금 ' + s.scanned + '건 확인 · 이미 처리 ' + s.alreadyHandled
      + '건 · 납부 기록 ' + s.autoRecorded + '건 · 확인 필요 ' + s.review + '건');
    await Promise.all([loadBills(), loadMatches()]);
  }

  async function loadMatches() {
    const [review, applied] = await Promise.all([
      fetchAuth(API + '/matches?applied=false').then(r => r.json()),
      fetchAuth(API + '/matches?applied=true').then(r => r.json())
    ]);
    renderMatches(document.getElementById('reviewList'), review, false);
    renderMatches(document.getElementById('appliedList'), applied, true);
  }

  function renderMatches(ul, rows, applied) {
    if (!rows.length) { empty(ul, applied ? '기록된 입금이 없습니다.' : '확인할 입금이 없습니다.'); return; }
    ul.innerHTML = '';
    rows.forEach(m => {
      const li = document.createElement('li');
      li.className = 'item';
      li.appendChild(row(m.occurredOn + ' · ' + (m.counterparty || m.description || '보낸 사람 없음'),
        Easy.fmtWon(m.amount), 't'));
      li.appendChild(row((REASONS[m.reason] || m.reason)
        + (m.studentName ? ' · ' + (applied ? '기록' : '후보') + ': ' + m.studentName + ' ' + Easy.fmtMonth(m.targetMonth)
          + (applied ? '' : ' (남은 금액 ' + Easy.fmtWon(m.balance) + ')') : ' · 후보 없음'), ''));
      const btns = document.createElement('span');
      if (applied) {
        btns.appendChild(button('기록 취소', 'btn-danger-link', () => act(m, 'cancel', '이 납부 기록을 취소할까요?')));
      } else {
        if (m.paymentId) {
          btns.appendChild(button('납부로 기록', 'btn-edit-link', () => act(m, 'confirm', m.studentName + ' 수납에 ' + Easy.fmtWon(m.amount) + '을 납부로 기록할까요?')));
        }
        btns.appendChild(button('수강료 아님', 'btn-danger-link', () => act(m, 'dismiss', '수강료가 아닌 입금으로 넘길까요?')));
      }
      li.appendChild(row(m.description && m.counterparty ? m.description : '', btns));
      ul.appendChild(li);
    });
  }

  async function act(m, action, question) {
    if (!confirm(question)) return;
    const res = await post('/matches/' + encodeURIComponent(m.matchId) + '/' + action);
    if (!res.ok) alert(await res.text() || '처리하지 못했습니다.');
    await Promise.all([loadBills(), loadMatches()]);
  }

  init().catch(e => console.error(e));
})();
