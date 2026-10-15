/**
 * /easy/transactions staging — CSV 읽기(클라이언트 파싱) + 확정/폐기.
 * 원본 행은 staging에만 두고, 확정한 READY 행만 장부로 옮긴다.
 * 실제 이지샵 데이터 import는 Work 자료 도착 후 별도 작업에서 수행한다.
 */
(function () {
  const HEADERS = {
    occurredOn: ['거래일', '일자', '날짜', 'date'],
    direction: ['구분', '수입/지출', '입출금', 'direction'],
    amount: ['금액', '합계', 'amount'],
    counterparty: ['거래처', 'counterparty'],
    description: ['적요', '내용', 'description'],
    payMethod: ['결제수단', '수단', 'paymethod'],
    supplyAmount: ['공급가액', 'supply'],
    vatAmount: ['부가세', 'vat'],
    evidenceType: ['증빙', 'evidence']
  };

  const EVIDENCE_MAP = {
    '세금계산서': 'TAX_INVOICE', '신용카드': 'CREDIT_CARD', '카드': 'CREDIT_CARD',
    '현금영수증': 'CASH_RECEIPT', '계산서': 'INVOICE', '없음': 'NONE', '기타': 'OTHER'
  };

  document.addEventListener('DOMContentLoaded', init);
  function init() {
    const btn = document.getElementById('btnStage');
    if (btn) btn.addEventListener('click', stageFile);
    loadBatches().catch(err => console.error(err));
  }

  function parseCsv(text) {
    const rows = [];
    let field = '';
    let row = [];
    let quoted = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (quoted) {
        if (c === '"') {
          if (text[i + 1] === '"') { field += '"'; i++; }
          else quoted = false;
        } else field += c;
      } else if (c === '"') quoted = true;
      else if (c === ',') { row.push(field); field = ''; }
      else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
      else if (c === '\r') { /* 무시 */ }
      else field += c;
    }
    if (field !== '' || row.length) { row.push(field); rows.push(row); }
    return rows.filter(r => r.some(v => String(v).trim() !== ''));
  }

  function mapHeader(name) {
    const norm = String(name || '').trim().toLowerCase().replace(/\s+/g, '');
    for (const key of Object.keys(HEADERS)) {
      if (HEADERS[key].some(h => h.toLowerCase().replace(/\s+/g, '') === norm)) return key;
    }
    return null;
  }

  function parseDirection(v) {
    const s = String(v || '').trim();
    if (['수입', '입금', 'INCOME', 'inflow'].includes(s)) return 'INCOME';
    if (['지출', '출금', 'EXPENSE', 'outflow'].includes(s)) return 'EXPENSE';
    return s;
  }

  function parseEvidence(v) {
    const s = String(v || '').trim();
    if (!s) return null;
    return EVIDENCE_MAP[s] || s;
  }

  function num(v) {
    if (v == null || String(v).trim() === '') return null;
    const n = Number(String(v).replace(/[,원]/g, '').trim());
    return Number.isFinite(n) ? n : v;
  }

  async function stageFile() {
    const errEl = document.getElementById('impError');
    errEl.style.display = 'none';
    Easy.hideMsg('impOk');
    const file = document.getElementById('impFile').files[0];
    const businessId = Number(document.getElementById('impBusiness').value);
    if (!file) { Easy.showError('impError', 'CSV 파일을 선택해 주세요.'); return; }
    if (!businessId) { Easy.showError('impError', '사업체를 선택해 주세요.'); return; }
    const encoding = document.getElementById('impEncoding').value || 'utf-8';
    const text = await file.text().catch(async () => {
      const buf = await file.arrayBuffer();
      return new TextDecoder(encoding).decode(buf);
    });
    const grid = parseCsv(text);
    if (grid.length < 2) { Easy.showError('impError', '데이터 행이 없습니다 (헤더+1행 필요).'); return; }
    const header = grid[0].map(mapHeader);
    const rows = [];
    for (let i = 1; i < grid.length; i++) {
      const obj = {};
      header.forEach((key, idx) => { if (key) obj[key] = grid[i][idx]; });
      rows.push({
        occurredOn: obj.occurredOn ? String(obj.occurredOn).trim() : null,
        direction: obj.direction ? parseDirection(obj.direction) : null,
        amount: num(obj.amount),
        counterparty: obj.counterparty ? String(obj.counterparty).trim() : '',
        description: obj.description ? String(obj.description).trim() : '',
        payMethod: obj.payMethod ? String(obj.payMethod).trim() : '',
        supplyAmount: num(obj.supplyAmount),
        vatAmount: num(obj.vatAmount),
        evidenceType: parseEvidence(obj.evidenceType),
        rawData: JSON.stringify(grid[i])
      });
    }
    const res = await fetchAuth('/api/admin/easy/imports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        businessId, sourceName: file.name, sourceKind: 'CSV', rows
      })
    });
    if (!res.ok) {
      Easy.showError('impError', await res.text() || 'staging에 실패했습니다.');
      return;
    }
    const batch = await res.json();
    const ready = (batch.rows || []).filter(r => r.status === 'READY').length;
    const errors = (batch.rows || []).filter(r => r.status === 'ERROR').length;
    Easy.showOk('impOk', 'staging 완료 — READY ' + ready + '건'
      + (errors ? ', 오류 ' + errors + '건 (확인 후 제외)' : ''));
    document.getElementById('impFile').value = '';
    await loadBatches();
  }

  async function loadBatches() {
    const res = await fetchAuth('/api/admin/easy/imports');
    if (!res.ok) return;
    const batches = await res.json();
    const ul = document.getElementById('batchList');
    ul.innerHTML = '';
    const staged = batches.filter(b => b.status === 'STAGED').slice(0, 5);
    if (!staged.length) {
      ul.innerHTML = '<li class="empty">대기 중인 가져오기가 없습니다.</li>';
      return;
    }
    staged.forEach(b => {
      const li = document.createElement('li');
      li.className = 'item';
      const top = document.createElement('div');
      top.className = 'row';
      const t = document.createElement('span');
      t.className = 't';
      t.textContent = (b.sourceName || '가져오기') + ' · 전체 ' + b.totalRows + '건';
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = '대기';
      top.appendChild(t);
      top.appendChild(badge);
      const actions = document.createElement('div');
      actions.className = 'row';
      const view = document.createElement('button');
      view.type = 'button';
      view.className = 'btn-edit-link';
      view.textContent = '행 보기';
      view.addEventListener('click', () => viewBatch(b.batchId));
      const confirm = document.createElement('button');
      confirm.type = 'button';
      confirm.className = 'btn-edit-link';
      confirm.textContent = 'READY 확정';
      confirm.addEventListener('click', () => confirmBatch(b.batchId));
      const discard = document.createElement('button');
      discard.type = 'button';
      discard.className = 'btn-danger-link';
      discard.textContent = '폐기';
      discard.addEventListener('click', () => discardBatch(b.batchId));
      const btns = document.createElement('span');
      btns.appendChild(view);
      btns.appendChild(confirm);
      btns.appendChild(discard);
      const spacer = document.createElement('span');
      spacer.className = 's';
      spacer.textContent = '';
      actions.appendChild(spacer);
      actions.appendChild(btns);
      li.appendChild(top);
      li.appendChild(actions);
      const detail = document.createElement('ul');
      detail.className = 'item-list';
      detail.id = 'batchRows' + b.batchId;
      detail.style.display = 'none';
      li.appendChild(detail);
      ul.appendChild(li);
    });
  }

  async function viewBatch(batchId) {
    const ul = document.getElementById('batchRows' + batchId);
    if (!ul) return;
    if (ul.style.display !== 'none') { ul.style.display = 'none'; return; }
    const res = await fetchAuth('/api/admin/easy/imports/' + encodeURIComponent(batchId));
    if (!res.ok) return;
    const batch = await res.json();
    ul.innerHTML = '';
    (batch.rows || []).forEach(r => {
      const li = document.createElement('li');
      li.className = 'item';
      const top = document.createElement('div');
      top.className = 'row';
      const t = document.createElement('span');
      t.className = 't';
      t.textContent = '#' + r.rowNo + ' ' + (r.description || r.counterparty || '(빈 행)')
        + ' ' + (r.amount != null ? Easy.fmtWon(r.amount) : '');
      const badge = document.createElement('span');
      badge.className = 'badge ' + (r.status === 'READY' ? 'paid' : r.status === 'ERROR' ? 'unpaid' : '');
      badge.textContent = r.status === 'READY'
        ? ('READY' + (r.matchedAccountName ? ' · ' + r.matchedAccountName : ''))
        : (r.status === 'ERROR' ? ('오류 · ' + (r.error || '')) : r.status);
      top.appendChild(t);
      top.appendChild(badge);
      li.appendChild(top);
      ul.appendChild(li);
    });
    ul.style.display = 'block';
  }

  async function confirmBatch(batchId) {
    if (!window.confirm('READY 행을 장부에 확정할까요? 확정 후에는 개별 거래로 관리됩니다.')) return;
    const res = await fetchAuth('/api/admin/easy/imports/' + encodeURIComponent(batchId) + '/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    });
    if (!res.ok) {
      Easy.showError('impError', await res.text() || '확정에 실패했습니다.');
      return;
    }
    const batch = await res.json();
    Easy.showOk('impOk', '확정 완료 — ' + batch.confirmedRows + '건이 장부에 등록되었습니다.');
    await loadBatches();
  }

  async function discardBatch(batchId) {
    if (!window.confirm('이 가져오기를 폐기할까요? staging 행은 장부에 반영되지 않습니다.')) return;
    const res = await fetchAuth('/api/admin/easy/imports/' + encodeURIComponent(batchId), {
      method: 'DELETE'
    });
    if (!res.ok && res.status !== 204) {
      Easy.showError('impError', await res.text() || '폐기에 실패했습니다.');
      return;
    }
    await loadBatches();
  }
})();
