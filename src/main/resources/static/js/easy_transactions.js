/**
 * /easy/transactions — 거래 등록/수정/삭제 + 필터 목록.
 * 삭제는 confirm() 확인 절차를 거친다.
 */
(function () {
  let businesses = [];
  let accounts = [];
  let editingId = null;

  function today() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
      + '-' + String(d.getDate()).padStart(2, '0');
  }

  async function init() {
    await Easy.guardAdmin();
    businesses = await Easy.loadBusinesses();
    accounts = await loadAccounts();
    fillBusinessSelect(document.getElementById('fBusiness'), false);
    fillBusinessSelect(document.getElementById('qBusiness'), true);
    fillBusinessSelect(document.getElementById('impBusiness'), false);
    syncAccountOptions();
    syncCategoryList();
    document.getElementById('fDate').value = today();
    const first = new Date();
    first.setDate(1);
    document.getElementById('qFrom').value = first.getFullYear() + '-'
      + String(first.getMonth() + 1).padStart(2, '0') + '-01';
    document.getElementById('qTo').value = today();
    document.getElementById('btnSave').addEventListener('click', save);
    document.getElementById('btnSearch').addEventListener('click', search);
    document.getElementById('btnExport').addEventListener('click', exportCsv);
    document.getElementById('toggleForm').addEventListener('click', toggleForm);
    document.getElementById('fDirection').addEventListener('change', onDirectionChange);
    document.getElementById('fSupply').addEventListener('input', autoTotal);
    document.getElementById('fVat').addEventListener('input', autoTotal);
    if (businesses.length) {
      document.getElementById('fBusiness').value = String(businesses[0].businessId);
    }
    await search();
  }

  async function loadAccounts() {
    try {
      const res = await fetchAuth('/api/admin/easy/accounts');
      if (!res.ok) return [];
      return res.json();
    } catch (_) {
      return [];
    }
  }

  function onDirectionChange() {
    syncAccountOptions();
    syncCategoryList();
  }

  /** 수입/지출에 맞는 계정 master 후보 (직접 입력도 가능). */
  function syncAccountOptions() {
    const dir = document.getElementById('fDirection').value;
    const select = document.getElementById('fAccount');
    const current = select.value;
    select.innerHTML = '';
    const none = document.createElement('option');
    none.value = '';
    none.textContent = '미지정';
    select.appendChild(none);
    accounts
      .filter(a => !a.direction || a.direction === dir)
      .forEach(a => {
        const opt = document.createElement('option');
        opt.value = String(a.accountId);
        opt.textContent = a.name;
        select.appendChild(opt);
      });
    if (current) select.value = current;
  }

  function syncCategoryList() {
    const dir = document.getElementById('fDirection').value;
    const list = document.getElementById('categoryList');
    list.innerHTML = '';
    accounts
      .filter(a => !a.direction || a.direction === dir)
      .forEach(a => {
        const opt = document.createElement('option');
        opt.value = a.name;
        list.appendChild(opt);
      });
  }

  /** 공급가액 입력 시 합계 자동 계산 (부가세 미입력은 0). */
  function autoTotal() {
    const supplyRaw = document.getElementById('fSupply').value;
    if (supplyRaw === '') return;
    const vatRaw = document.getElementById('fVat').value;
    document.getElementById('fAmount').value =
      String(Number(supplyRaw) + (vatRaw === '' ? 0 : Number(vatRaw)));
  }

  function exportCsv() {
    const params = new URLSearchParams();
    const from = document.getElementById('qFrom').value;
    const to = document.getElementById('qTo').value;
    const biz = document.getElementById('qBusiness').value;
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (biz) params.set('businessId', biz);
    window.location.href = '/api/admin/easy/transactions/export?' + params.toString();
  }

  function toggleForm() {
    const form = document.getElementById('txnForm');
    const btn = document.getElementById('toggleForm');
    const hidden = form.style.display === 'none';
    form.style.display = hidden ? 'block' : 'none';
    btn.textContent = hidden ? '접기 ›' : '펼치기 ›';
  }

  function fillBusinessSelect(select, withAll) {
    select.innerHTML = '';
    if (withAll) {
      const all = document.createElement('option');
      all.value = '';
      all.textContent = '전체';
      select.appendChild(all);
    }
    businesses.forEach(b => {
      const opt = document.createElement('option');
      opt.value = String(b.businessId);
      opt.textContent = b.name;
      select.appendChild(opt);
    });
  }

  function readForm() {
    const supplyRaw = document.getElementById('fSupply').value;
    const vatRaw = document.getElementById('fVat').value;
    const accountRaw = document.getElementById('fAccount').value;
    const evidenceRaw = document.getElementById('fEvidence').value;
    return {
      businessId: Number(document.getElementById('fBusiness').value),
      occurredOn: document.getElementById('fDate').value,
      direction: document.getElementById('fDirection').value,
      amount: Number(document.getElementById('fAmount').value),
      category: document.getElementById('fCategory').value.trim() || '기타',
      counterparty: document.getElementById('fCounterparty').value.trim(),
      description: document.getElementById('fDesc').value.trim(),
      payMethod: document.getElementById('fPayMethod').value.trim(),
      memo: document.getElementById('fMemo').value.trim(),
      receiptRef: '',
      accountId: accountRaw === '' ? null : Number(accountRaw),
      supplyAmount: supplyRaw === '' ? null : Number(supplyRaw),
      vatAmount: vatRaw === '' ? null : Number(vatRaw),
      evidenceType: evidenceRaw === '' ? null : evidenceRaw
    };
  }

  async function save() {
    const errEl = document.getElementById('formError');
    errEl.style.display = 'none';
    Easy.hideMsg('formOk');
    const body = readForm();
    if (!body.occurredOn || !body.businessId || !(body.amount >= 0)) {
      Easy.showError('formError', '거래일·사업체·금액을 확인해 주세요.');
      return;
    }
    const url = editingId
      ? '/api/admin/easy/transactions/' + encodeURIComponent(editingId)
      : '/api/admin/easy/transactions';
    const res = await fetchAuth(url, {
      method: editingId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      Easy.showError('formError', await res.text() || '저장에 실패했습니다.');
      return;
    }
    const wasEdit = !!editingId;
    resetForm();
    Easy.showOk('formOk', wasEdit ? '거래가 수정되었습니다.' : '거래가 등록되었습니다.');
    await search();
  }

  function resetForm() {
    editingId = null;
    document.getElementById('btnSave').textContent = '등록하기';
    ['fAmount', 'fCounterparty', 'fDesc', 'fMemo', 'fSupply', 'fVat', 'fCategory'].forEach(id => {
      document.getElementById(id).value = '';
    });
    document.getElementById('fAccount').value = '';
    document.getElementById('fEvidence').value = '';
    document.getElementById('fDate').value = today();
  }

  async function search() {
    const params = new URLSearchParams();
    const from = document.getElementById('qFrom').value;
    const to = document.getElementById('qTo').value;
    const biz = document.getElementById('qBusiness').value;
    const dir = document.getElementById('qDirection').value;
    const cat = document.getElementById('qCategory').value.trim();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (biz) params.set('businessId', biz);
    if (dir) params.set('direction', dir);
    if (cat) params.set('category', cat);
    const res = await fetchAuth('/api/admin/easy/transactions?' + params.toString());
    if (!res.ok) throw new Error('목록 조회 실패');
    render(await res.json());
  }

  function render(txns) {
    const ul = document.getElementById('txnList');
    ul.innerHTML = '';
    if (!txns.length) {
      ul.innerHTML = '<li class="empty">거래가 없습니다.</li>';
      return;
    }
    txns.forEach(t => {
      const li = document.createElement('li');
      li.className = 'item';
      const top = document.createElement('div');
      top.className = 'row';
      const title = document.createElement('span');
      title.className = 't';
      title.textContent = (t.description || t.category) + ' · ' + (t.businessName || '');
      const amt = document.createElement('span');
      amt.className = 'amt ' + (t.direction === 'INCOME' ? 'in' : 'out');
      amt.textContent = (t.direction === 'INCOME' ? '+' : '-') + Easy.fmtWon(t.amount);
      top.appendChild(title);
      top.appendChild(amt);
      const sub = document.createElement('div');
      sub.className = 'row';
      const s = document.createElement('span');
      s.className = 's';
      const bits = [(t.occurredOn || ''), (t.accountName || t.category || '')];
      if (t.accountName && t.category && t.accountName !== t.category) bits.push(t.category);
      if (t.counterparty) bits.push(t.counterparty);
      if (t.supplyAmount != null) {
        bits.push('공급 ' + Easy.fmtWon(t.supplyAmount)
          + ' + 부가세 ' + Easy.fmtWon(t.vatAmount || 0));
      }
      if (t.evidenceType) bits.push(evidenceLabel(t.evidenceType));
      s.textContent = bits.filter(Boolean).join(' · ');
      const badge = document.createElement('span');
      badge.className = 'badge ' + (t.direction === 'INCOME' ? 'in' : 'out');
      badge.textContent = t.direction === 'INCOME' ? '수입' : '지출';
      sub.appendChild(s);
      sub.appendChild(badge);
      const actions = document.createElement('div');
      actions.className = 'row';
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'btn-edit-link';
      edit.textContent = '수정';
      edit.addEventListener('click', () => startEdit(t));
      const del = document.createElement('button');
      del.type = 'button';
      del.className = 'btn-danger-link';
      del.textContent = '삭제';
      del.addEventListener('click', () => remove(t));
      const spacer = document.createElement('span');
      spacer.className = 's';
      spacer.textContent = (t.payMethod || '') + (t.memo ? ' · ' + t.memo : '');
      actions.appendChild(spacer);
      const btns = document.createElement('span');
      btns.appendChild(edit);
      btns.appendChild(del);
      actions.appendChild(btns);
      li.appendChild(top);
      li.appendChild(sub);
      li.appendChild(actions);
      ul.appendChild(li);
    });
  }

  const EVIDENCE_LABEL = {
    TAX_INVOICE: '세금계산서',
    CREDIT_CARD: '신용카드',
    CASH_RECEIPT: '현금영수증',
    INVOICE: '계산서',
    NONE: '없음',
    OTHER: '기타'
  };

  function evidenceLabel(code) {
    return EVIDENCE_LABEL[code] || String(code || '');
  }

  function startEdit(t) {
    editingId = t.txnId;
    document.getElementById('fDate').value = t.occurredOn || today();
    document.getElementById('fBusiness').value = String(t.businessId);
    document.getElementById('fDirection').value = t.direction;
    onDirectionChange();
    document.getElementById('fAmount').value = String(t.amount);
    document.getElementById('fCategory').value = t.category || '';
    document.getElementById('fAccount').value = t.accountId ? String(t.accountId) : '';
    document.getElementById('fSupply').value = t.supplyAmount == null ? '' : String(t.supplyAmount);
    document.getElementById('fVat').value = t.vatAmount == null ? '' : String(t.vatAmount);
    document.getElementById('fEvidence').value = t.evidenceType || '';
    document.getElementById('fCounterparty').value = t.counterparty || '';
    document.getElementById('fDesc').value = t.description || '';
    document.getElementById('fPayMethod').value = t.payMethod || '';
    document.getElementById('fMemo').value = t.memo || '';
    document.getElementById('btnSave').textContent = '수정 완료';
    document.getElementById('txnForm').style.display = 'block';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remove(t) {
    const label = (t.description || t.category) + ' ' + Easy.fmtWon(t.amount);
    if (!window.confirm('정말 삭제할까요?\n' + label)) return;
    const res = await fetchAuth('/api/admin/easy/transactions/' + encodeURIComponent(t.txnId), {
      method: 'DELETE'
    });
    if (!res.ok && res.status !== 204) {
      alert(await res.text() || '삭제에 실패했습니다.');
      return;
    }
    await search();
  }

  document.addEventListener('DOMContentLoaded', () => {
    init().catch(err => console.error(err));
  });
})();
