/* 학습지 보기·인쇄·배정(/dashboard/worksheets/view?id=). 배정은 관리자 계정에서만 열린다(서버 확인). */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const businessId = WS.param('businessId');
  const worksheetId = WS.param('id');
  const q = '?businessId=' + encodeURIComponent(businessId);
  const adminApi = path => '/api/admin/easy/worksheets' + path + q;

  function msg(t, ok) { const m = $('msg'); m.textContent = t || ''; m.className = 'ws-msg ws-noprint' + (ok ? ' ok' : ''); m.style.display = t ? 'block' : 'none'; }

  function renderPanel(p) {
    $('assignBody').hidden = false;
    $('assigned').innerHTML = p.assigned.map(a => '<li><span><b>' + WS.esc(a.studentName) + '</b> <span class="ws-muted">'
      + WS.esc([a.school, a.grade ? a.grade + '학년' : '', a.className].filter(Boolean).join(' · ')) + ' · ' + WS.day(a.assignedAt) + ' 배정</span></span>'
      + '<span class="ws-actions"><select data-status="' + a.assignmentId + '" aria-label="' + WS.esc(a.studentName) + ' 배정 상태">'
      + Object.keys(WS.ASSIGN).map(k => '<option value="' + k + '"' + (k === a.status ? ' selected' : '') + '>' + WS.ASSIGN[k] + '</option>').join('')
      + '</select><button type="button" class="ws-btn small danger" data-remove="' + a.assignmentId + '">빼기</button></span></li>').join('')
      || '<li class="ws-muted">아직 배정한 학생이 없습니다.</li>';
    $('classPick').innerHTML = '<option value="">반을 고르지 않음</option>' + p.classes.map(c => '<option value="' + c.classId + '">' + WS.esc(c.name) + ' (' + c.studentCount + '명)</option>').join('');
    $('students').innerHTML = p.students.map(s => '<label><input type="checkbox" value="' + s.studentId + '"> ' + WS.esc(s.name)
      + ' <span class="ws-muted">' + WS.esc([s.school, s.grade ? s.grade + '학년' : ''].filter(Boolean).join(' ')) + '</span></label>').join('')
      || '<span class="ws-muted">배정할 수 있는 재원생이 없습니다.</span>';
    $('assigned').querySelectorAll('[data-status]').forEach(s => s.addEventListener('change', async () => {
      try { await WS.json(adminApi('/assignments/' + s.dataset.status) + '&status=' + s.value, { method: 'PUT' }); msg('배정 상태를 바꿨습니다.', true); }
      catch (e) { msg(e.message); loadPanel(); }
    }));
    $('assigned').querySelectorAll('[data-remove]').forEach(b => b.addEventListener('click', async () => {
      if (!confirm('이 학생을 배정에서 뺄까요?')) return;
      try { await WS.json(adminApi('/assignments/' + b.dataset.remove), { method: 'DELETE' }); loadPanel(); }
      catch (e) { msg(e.message); }
    }));
  }

  async function loadPanel() {
    const res = await fetchAuth(adminApi('/' + worksheetId + '/assignments'), { redirectOnAuthError: false });
    if (res.status === 401 || res.status === 403) { $('assignHelp').textContent = '학생·반 배정은 학생 명단을 보는 관리자 계정에서 할 수 있습니다.'; return; }
    if (!res.ok) { $('assignHelp').textContent = await res.text(); return; }
    renderPanel(await res.json());
  }

  async function assign() {
    const studentIds = Array.from($('students').querySelectorAll('input:checked')).map(i => Number(i.value));
    const classId = $('classPick').value ? Number($('classPick').value) : null;
    $('assignBtn').disabled = true;
    try {
      const d = await WS.json(adminApi('/' + worksheetId + '/assignments'), WS.body('POST', { studentIds, classId }));
      renderPanel(d.panel);
      msg(d.assignedCount ? d.assignedCount + '명에게 배정했습니다.' : '이미 모두 배정되어 있습니다.', true);
    } catch (e) { msg(e.message); }
    $('assignBtn').disabled = false;
  }

  async function boot() {
    if (!businessId || !worksheetId) { location.replace('/dashboard/worksheets'); return; }
    $('backLink').href = '/dashboard/worksheets' + q;
    const d = await WS.json('/api/tutor/easy/worksheets/' + worksheetId + q);
    const w = d.worksheet;
    document.title = w.title + ' | F2R';
    $('title').textContent = w.title;
    $('meta').textContent = [WS.STATUS[w.status], '만든 사람 ' + (w.ownerName || '-'), w.description].filter(Boolean).join(' · ');
    $('countLabel').textContent = d.questions.length + '문항';
    $('questions').innerHTML = d.questions.map((x, i) => WS.questionHtml(x, i + 1)).join('');
    $('answers').innerHTML = '<b>정답</b><br>' + d.questions.map((x, i) => '<span>' + (i + 1) + '. ' + WS.esc(x.answer) + '</span>').join('');
    if (d.missingCount) msg('출처에서 없어진 문항 ' + d.missingCount + '개는 빠졌습니다.');
    if (w.canEdit && !w.deletedAt) { $('editLink').hidden = false; $('editLink').href = '/dashboard/worksheets/edit' + q + '&id=' + worksheetId; }
    $('withAnswers').addEventListener('change', () => { $('answers').hidden = !$('withAnswers').checked; $('answers').classList.toggle('ws-hide-print', !$('withAnswers').checked); });
    $('printBtn').addEventListener('click', () => window.print());
    $('assignBtn').addEventListener('click', assign);
    if (w.deletedAt) { $('assignHelp').textContent = '휴지통에 있는 학습지입니다. 복구한 뒤 배정할 수 있습니다.'; return; }
    loadPanel();
  }

  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
