/**
 * /easy/students — 원장 목록/등록/수정 + ?id= 상세(수납 합계·내역).
 */
(function () {
  let editingId = null;

  const STATUS_LABEL = { ENROLLED: '재원', WITHDRAWN: '퇴원', GRADUATED: '졸업' };

  async function init() {
    await Easy.guardAdmin();
    document.getElementById('btnSave').addEventListener('click', save);
    document.getElementById('toggleForm').addEventListener('click', toggleForm);
    const id = new URLSearchParams(location.search).get('id');
    if (id) {
      await showDetail(id);
    } else {
      await showList();
    }
  }

  function toggleForm() {
    const form = document.getElementById('studentForm');
    const btn = document.getElementById('toggleForm');
    const hidden = form.style.display === 'none';
    form.style.display = hidden ? 'block' : 'none';
    btn.textContent = hidden ? '접기 ›' : '펼치기 ›';
  }

  async function showList() {
    document.getElementById('listView').style.display = 'block';
    document.getElementById('detailView').style.display = 'none';
    const res = await fetchAuth('/api/admin/easy/students');
    if (!res.ok) throw new Error('목록 조회 실패');
    renderList(await res.json());
  }

  function renderList(students) {
    const ul = document.getElementById('studentList');
    ul.innerHTML = '';
    if (!students.length) {
      ul.innerHTML = '<li class="empty">등록된 학생이 없습니다.</li>';
      return;
    }
    students.forEach(s => {
      const a = document.createElement('a');
      a.className = 'item';
      a.href = '/easy/students?id=' + encodeURIComponent(s.studentId);
      const top = document.createElement('div');
      top.className = 'row';
      const name = document.createElement('span');
      name.className = 't';
      name.textContent = s.name + (s.school ? ' · ' + s.school + (s.grade ? ' ' + s.grade + '학년' : '') : '');
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = STATUS_LABEL[s.status] || s.status;
      top.appendChild(name);
      top.appendChild(badge);
      const sub = document.createElement('div');
      sub.className = 'row';
      const info = document.createElement('span');
      info.className = 's';
      const parts = [];
      if (s.balance > 0) parts.push('미수 ' + Easy.fmtWon(s.balance));
      else parts.push('미수 없음');
      parts.push('누적 납부 ' + Easy.fmtWon(s.paidTotal));
      info.textContent = parts.join(' · ');
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'btn-edit-link';
      edit.textContent = '수정';
      edit.addEventListener('click', ev => { ev.preventDefault(); startEdit(s); });
      sub.appendChild(info);
      sub.appendChild(edit);
      a.appendChild(top);
      a.appendChild(sub);
      ul.appendChild(a);
    });
  }

  function readForm() {
    return {
      name: document.getElementById('fName').value.trim(),
      status: document.getElementById('fStatus').value,
      school: document.getElementById('fSchool').value.trim(),
      grade: document.getElementById('fGrade').value === ''
        ? 0 : Number(document.getElementById('fGrade').value),
      phone: document.getElementById('fPhone').value.trim() || null,
      parentPhone: document.getElementById('fParentPhone').value.trim() || null,
      enrolledOn: document.getElementById('fEnrolledOn').value || null,
      memo: document.getElementById('fMemo').value.trim() || null
    };
  }

  async function save() {
    const errEl = document.getElementById('formError');
    errEl.style.display = 'none';
    Easy.hideMsg('formOk');
    const body = readForm();
    if (!body.name) {
      Easy.showError('formError', '학생 이름을 입력해 주세요.');
      return;
    }
    const url = editingId
      ? '/api/admin/easy/students/' + encodeURIComponent(editingId)
      : '/api/admin/easy/students';
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
    Easy.showOk('formOk', wasEdit ? '학생 정보가 수정되었습니다.' : '학생이 등록되었습니다.');
    await showList();
  }

  function resetForm() {
    editingId = null;
    document.getElementById('btnSave').textContent = '등록하기';
    ['fName', 'fSchool', 'fGrade', 'fPhone', 'fParentPhone', 'fEnrolledOn', 'fMemo'].forEach(id => {
      document.getElementById(id).value = '';
    });
    document.getElementById('fStatus').value = 'ENROLLED';
  }

  function startEdit(s) {
    editingId = s.studentId;
    document.getElementById('fName').value = s.name || '';
    document.getElementById('fStatus').value = s.status || 'ENROLLED';
    document.getElementById('fSchool').value = s.school || '';
    document.getElementById('fGrade').value = s.grade || '';
    document.getElementById('fPhone').value = s.phone || '';
    document.getElementById('fParentPhone').value = s.parentPhone || '';
    document.getElementById('fEnrolledOn').value = s.enrolledOn || '';
    document.getElementById('fMemo').value = s.memo || '';
    document.getElementById('btnSave').textContent = '수정 완료';
    document.getElementById('studentForm').style.display = 'block';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function showDetail(id) {
    document.getElementById('listView').style.display = 'none';
    document.getElementById('detailView').style.display = 'block';
    const res = await fetchAuth('/api/admin/easy/students/' + encodeURIComponent(id) + '/ledger');
    if (!res.ok) {
      alert('학생 정보를 불러오지 못했습니다.');
      location.href = '/easy/students';
      return;
    }
    const d = await res.json();
    const s = d.student;
    document.getElementById('detailName').textContent = s.name;
    const info = [];
    if (s.school) info.push(s.school + (s.grade ? ' ' + s.grade + '학년' : ''));
    info.push(STATUS_LABEL[s.status] || s.status);
    if (s.phone) info.push(s.phone);
    if (s.parentPhone) info.push('학부모 ' + s.parentPhone);
    if (d.businesses && d.businesses.length) info.push('소속: ' + d.businesses.join(', '));
    document.getElementById('detailInfo').textContent = info.join(' · ');
    renderDetailStats(s);
    renderDetailPayments(d.payments || []);
  }

  function statCards(grid, cards) {
    grid.innerHTML = '';
    cards.forEach(c => {
      const div = document.createElement('div');
      div.className = 'stat-card';
      const l = document.createElement('div');
      l.className = 'l';
      l.textContent = c.l;
      const n = document.createElement('div');
      n.className = 'n ' + c.c;
      n.textContent = c.n;
      div.appendChild(l);
      div.appendChild(n);
      grid.appendChild(div);
    });
  }

  function renderDetailStats(s) {
    const grid = document.getElementById('detailStats');
    statCards(grid, [
      { l: '청구액', n: Easy.fmtWon(s.billedTotal), c: '' },
      { l: '납부액', n: Easy.fmtWon(s.paidTotal), c: 'plus' },
      { l: '미수금', n: Easy.fmtWon(s.balance), c: s.balance > 0 ? 'warn' : '' }
    ]);
  }

  function paymentItem(p) {
    const li = document.createElement('li');
    li.className = 'item';
    const top = document.createElement('div');
    top.className = 'row';
    const t = document.createElement('span');
    t.className = 't';
    t.textContent = Easy.fmtMonth(p.targetMonth) + ' · ' + (p.businessName || '');
    const amt = document.createElement('span');
    amt.className = 'amt';
    amt.textContent = Easy.fmtWon(p.paidAmount) + ' / ' + Easy.fmtWon(p.billedAmount);
    top.appendChild(t);
    top.appendChild(amt);
    const sub = document.createElement('div');
    sub.className = 'row';
    const meta = document.createElement('span');
    meta.className = 's';
    meta.textContent = (p.paidOn ? p.paidOn + ' 납부' : '미납부') + (p.payMethod ? ' · ' + p.payMethod : '');
    const badge = document.createElement('span');
    badge.className = 'badge ' + Easy.statusClass(p.status);
    badge.textContent = Easy.statusLabel(p.status);
    sub.appendChild(meta);
    sub.appendChild(badge);
    li.appendChild(top);
    li.appendChild(sub);
    return li;
  }

  function renderDetailPayments(payments) {
    const now = new Date();
    const thisMonth = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
    const current = payments.filter(p => p.targetMonth === thisMonth);
    const past = payments.filter(p => p.targetMonth !== thisMonth);
    const ul = document.getElementById('detailPayments');
    ul.innerHTML = '';
    if (!payments.length) {
      ul.innerHTML = '<li class="empty">수납 내역이 없습니다.</li>';
      return;
    }
    if (current.length) {
      const head = document.createElement('li');
      head.className = 's';
      head.style.padding = '8px 0 4px';
      head.textContent = '이번 달 (' + Easy.fmtMonth(thisMonth) + ')';
      ul.appendChild(head);
      let billed = 0;
      let paid = 0;
      current.forEach(p => {
        billed += Number(p.billedAmount || 0);
        paid += Number(p.paidAmount || 0);
        ul.appendChild(paymentItem(p));
      });
      const sum = document.createElement('li');
      sum.className = 'item';
      const row = document.createElement('div');
      row.className = 'row';
      const t = document.createElement('span');
      t.className = 't';
      t.textContent = '이번 달 합계';
      const amt = document.createElement('span');
      amt.className = 'amt' + (billed - paid > 0 ? ' out' : '');
      amt.textContent = Easy.fmtWon(paid) + ' / ' + Easy.fmtWon(billed);
      row.appendChild(t);
      row.appendChild(amt);
      sum.appendChild(row);
      ul.appendChild(sum);
    }
    if (past.length) {
      const head = document.createElement('li');
      head.className = 's';
      head.style.padding = '8px 0 4px';
      head.textContent = '과거 내역';
      ul.appendChild(head);
      past.forEach(p => ul.appendChild(paymentItem(p)));
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    init().catch(err => console.error(err));
  });
})();
