/**
 * /easy/payments — 월 수납 등록/수정/삭제 + 필터 목록.
 */
(function () {
  let editingId = null;

  async function init() {
    await Easy.guardAdmin();
    const [businesses, students] = await Promise.all([
      Easy.loadBusinesses(),
      loadStudents()
    ]);
    window._easyBusinesses = businesses;
    window._easyStudents = students;
    fillSelect(document.getElementById('fBusiness'), businesses, 'businessId', 'name', false);
    fillSelect(document.getElementById('qStudent'), students, 'studentId', 'name', true);
    fillSelect(document.getElementById('fStudent'), students, 'studentId', 'name', false);
    const d = new Date();
    document.getElementById('fMonth').value =
      d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
    document.getElementById('btnSave').addEventListener('click', save);
    document.getElementById('btnSearch').addEventListener('click', search);
    document.getElementById('toggleForm').addEventListener('click', toggleForm);
    const sid = new URLSearchParams(location.search).get('studentId');
    if (sid) document.getElementById('qStudent').value = sid;
    await search();
  }

  async function loadStudents() {
    const res = await fetchAuth('/api/admin/easy/students');
    if (!res.ok) throw new Error('학생 목록 조회 실패');
    return res.json();
  }

  function fillSelect(select, items, valueKey, labelKey, withAll) {
    select.innerHTML = '';
    if (withAll) {
      const all = document.createElement('option');
      all.value = '';
      all.textContent = '전체';
      select.appendChild(all);
    }
    items.forEach(it => {
      const opt = document.createElement('option');
      opt.value = String(it[valueKey]);
      opt.textContent = it[labelKey];
      select.appendChild(opt);
    });
  }

  function toggleForm() {
    const form = document.getElementById('payForm');
    const btn = document.getElementById('toggleForm');
    const hidden = form.style.display === 'none';
    form.style.display = hidden ? 'block' : 'none';
    btn.textContent = hidden ? '접기 ›' : '펼치기 ›';
  }

  function readForm() {
    const paidRaw = document.getElementById('fPaid').value;
    return {
      studentId: Number(document.getElementById('fStudent').value),
      businessId: Number(document.getElementById('fBusiness').value),
      targetMonth: document.getElementById('fMonth').value,
      billedAmount: Number(document.getElementById('fBilled').value),
      paidAmount: paidRaw === '' ? 0 : Number(paidRaw),
      paidOn: document.getElementById('fPaidOn').value || null,
      payMethod: document.getElementById('fPayMethod').value.trim(),
      memo: document.getElementById('fMemo').value.trim() || null
    };
  }

  async function save() {
    const errEl = document.getElementById('formError');
    errEl.style.display = 'none';
    const body = readForm();
    if (!body.studentId || !body.businessId || !body.targetMonth || !(body.billedAmount >= 0)) {
      Easy.showError('formError', '학생·사업체·대상월·청구 금액을 확인해 주세요.');
      return;
    }
    const url = editingId
      ? '/api/admin/easy/payments/' + encodeURIComponent(editingId)
      : '/api/admin/easy/payments';
    const res = await fetchAuth(url, {
      method: editingId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      Easy.showError('formError', await res.text() || '저장에 실패했습니다.');
      return;
    }
    resetForm();
    await search();
  }

  function resetForm() {
    editingId = null;
    document.getElementById('btnSave').textContent = '등록하기';
    ['fBilled', 'fPaid', 'fPaidOn', 'fPayMethod', 'fMemo'].forEach(id => {
      document.getElementById(id).value = '';
    });
  }

  async function search() {
    const params = new URLSearchParams();
    const sid = document.getElementById('qStudent').value;
    const month = document.getElementById('qMonth').value;
    const status = document.getElementById('qStatus').value;
    if (sid) params.set('studentId', sid);
    if (month) params.set('month', month);
    if (status) params.set('status', status);
    const res = await fetchAuth('/api/admin/easy/payments?' + params.toString());
    if (!res.ok) throw new Error('목록 조회 실패');
    render(await res.json());
  }

  function render(payments) {
    const ul = document.getElementById('payList');
    ul.innerHTML = '';
    if (!payments.length) {
      ul.innerHTML = '<li class="empty">수납 내역이 없습니다.</li>';
      return;
    }
    payments.forEach(p => {
      const li = document.createElement('li');
      li.className = 'item';
      const top = document.createElement('div');
      top.className = 'row';
      const t = document.createElement('span');
      t.className = 't';
      t.textContent = (p.studentName || '') + ' · ' + Easy.fmtMonth(p.targetMonth);
      const amt = document.createElement('span');
      amt.className = 'amt';
      amt.textContent = Easy.fmtWon(p.paidAmount) + ' / ' + Easy.fmtWon(p.billedAmount);
      top.appendChild(t);
      top.appendChild(amt);
      const sub = document.createElement('div');
      sub.className = 'row';
      const meta = document.createElement('span');
      meta.className = 's';
      meta.textContent = (p.businessName || '')
        + (p.paidOn ? ' · ' + p.paidOn + ' 납부' : '')
        + (p.payMethod ? ' · ' + p.payMethod : '');
      const badge = document.createElement('span');
      badge.className = 'badge ' + Easy.statusClass(p.status);
      badge.textContent = Easy.statusLabel(p.status);
      sub.appendChild(meta);
      sub.appendChild(badge);
      const actions = document.createElement('div');
      actions.className = 'row';
      const link = document.createElement('a');
      link.className = 's';
      link.href = '/easy/students?id=' + encodeURIComponent(p.studentId);
      link.textContent = '원장 보기 ›';
      const btns = document.createElement('span');
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'btn-edit-link';
      edit.textContent = '수정';
      edit.addEventListener('click', () => startEdit(p));
      const del = document.createElement('button');
      del.type = 'button';
      del.className = 'btn-danger-link';
      del.textContent = '삭제';
      del.addEventListener('click', () => remove(p));
      btns.appendChild(edit);
      btns.appendChild(del);
      actions.appendChild(link);
      actions.appendChild(btns);
      li.appendChild(top);
      li.appendChild(sub);
      li.appendChild(actions);
      ul.appendChild(li);
    });
  }

  function startEdit(p) {
    editingId = p.paymentId;
    document.getElementById('fStudent').value = String(p.studentId);
    document.getElementById('fBusiness').value = String(p.businessId);
    document.getElementById('fMonth').value = p.targetMonth || '';
    document.getElementById('fBilled').value = String(p.billedAmount);
    document.getElementById('fPaid').value = String(p.paidAmount);
    document.getElementById('fPaidOn').value = p.paidOn || '';
    document.getElementById('fPayMethod').value = p.payMethod || '';
    document.getElementById('fMemo').value = p.memo || '';
    document.getElementById('btnSave').textContent = '수정 완료';
    document.getElementById('payForm').style.display = 'block';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remove(p) {
    if (!window.confirm('정말 삭제할까요?\n' + (p.studentName || '') + ' ' + Easy.fmtMonth(p.targetMonth))) return;
    const res = await fetchAuth('/api/admin/easy/payments/' + encodeURIComponent(p.paymentId), {
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
