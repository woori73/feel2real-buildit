/* 기존 학생 원장·진행표 출결을 요일별로 읽고, 원장이 선택한 요일만 별도로 저장한다. */
(function () {
  'use strict';
  var DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
  var LABEL = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };
  var SOURCE = { MANUAL: '직접 수정', ROSTER: '이번 주 제공 진행표', ATTENDANCE: '최근 4주 등원 기록', CLASS: '반 시간표' };
  var businessId, students = [], classes = [], selectedClass = '', selectedDay = null;
  var editor = null, dialogGeneration = 0, bound = false;
  var $ = function (id) { return document.getElementById(id); };
  function el(tag, text, cls) { var n = document.createElement(tag); if (text != null) n.textContent = text; if (cls) n.className = cls; return n; }
  function link(text, href) { var a = el('a', text); a.href = href; return a; }
  function button(text, action) { var b = el('button', text); b.type = 'button'; b.addEventListener('click', action); return b; }
  function url(studentId, suffix) { return '/api/admin/easy/attendance/students/' + encodeURIComponent(studentId) + '/' + suffix + '?businessId=' + encodeURIComponent(businessId); }
  function classUrl(c) { return '/easy/attendance?businessId=' + encodeURIComponent(businessId) + '&tab=classes&classId=' + encodeURIComponent(c.classId); }
  function daysText(days) { return (days || []).map(function (d) { return LABEL[d]; }).filter(Boolean).join('·') || '요일 미정'; }
  function sourceText(s) { return SOURCE[s.weekdaySource] || '요일 확인 필요'; }

  function render() {
    var tabs = $('ahWeekdays'); tabs.replaceChildren();
    ['ALL'].concat(DAYS, ['UNSET']).forEach(function (day) {
      var count = students.filter(function (s) { return day === 'ALL' || (day === 'UNSET' ? !(s.weekdays || []).length : (s.weekdays || []).includes(day)); }).length;
      var b = button((day === 'ALL' ? '전체' : day === 'UNSET' ? '요일 미정' : LABEL[day]) + ' ' + count + '명', function () { selectedDay = day; render(); });
      b.setAttribute('aria-pressed', String(selectedDay === day)); tabs.append(b);
    });
    var query = $('ahStudentSearch').value.trim().toLocaleLowerCase('ko');
    var rows = students.filter(function (s) {
      return (selectedDay === 'ALL' || (selectedDay === 'UNSET' ? !(s.weekdays || []).length : (s.weekdays || []).includes(selectedDay)))
        && (!selectedClass || (s.classes || []).includes(selectedClass))
        && (!query || [s.name, s.school].concat(s.classes || []).join(' ').toLocaleLowerCase('ko').includes(query));
    });
    $('ahRosterCount').textContent = (selectedDay === 'ALL' ? '전체' : selectedDay === 'UNSET' ? '요일 미정' : LABEL[selectedDay] + '요일') + (selectedClass ? ' · ' + selectedClass : '') + ' · ' + rows.length + '명';
    var tbody = $('ahRosterRows'); tbody.replaceChildren();
    if (!rows.length) { var empty = el('td', '해당 조건의 학생이 없습니다. 다른 요일이나 전체를 선택하세요.'); empty.colSpan = 5; var tr = el('tr'); tr.append(empty); tbody.append(tr); }
    rows.forEach(function (s) {
      var tr = el('tr');
      var name = el('td'); name.dataset.label = '학생';
      name.append(link(s.name || '이름 미등록', '/easy/students?id=' + encodeURIComponent(s.studentId)), el('small', [s.school, s.grade ? s.grade + '학년' : ''].filter(Boolean).join(' · ') || '학교·학년 미등록', 'ah-sub'));
      var group = el('td', (s.classes || []).join(' · ') || '배정된 반 없음'); group.dataset.label = '소속 반';
      var days = el('td'); days.dataset.label = '등원 요일'; days.append(el('strong', daysText(s.weekdays)), el('small', sourceText(s), 'ah-sub'));
      var record = el('td'); record.dataset.label = '등원 기록'; record.append(el('span', '이번 달 ' + s.monthCheckIns + '회'), el('small', s.lastCheckIn ? '최근 ' + s.lastCheckIn : '최근 등원 기록 없음', 'ah-sub'));
      var actions = el('td', null, 'ah-row-actions'); actions.dataset.label = '관리';
      actions.append(button('기록 보기', function () { openHistory(s); }), button('수정', function () { openEditor(s); }));
      tr.append(name, group, days, record, actions); tbody.append(tr);
    });
    $('ahClearClass').hidden = !selectedClass;
    var classBox = $('ahClassRows'); classBox.replaceChildren();
    if (!classes.length) classBox.append(el('p', '등록된 반이 없습니다.', 'ah-empty'));
    classes.forEach(function (c) {
      var box = el('div', null, 'ah-class-card');
      var mine = students.filter(function (s) { return (s.classes || []).includes(c.name); });
      var choose = button(c.name + ' · 학생 ' + c.memberCount + '명', function () { selectedClass = c.name; selectedDay = 'ALL'; render(); });
      choose.setAttribute('aria-pressed', String(selectedClass === c.name));
      box.append(choose, el('p', daysText(c.weekdays) + (c.startTime ? ' · ' + c.startTime.slice(0, 5) + (c.endTime ? '~' + c.endTime.slice(0, 5) : '') : ' · 시간 미등록'), 'ah-sub'));
      box.append(el('p', mine.map(function (s) { return s.name; }).join(' · ') || '학생 배정 정보 없음', 'ah-class-names'));
      box.append(link('반 수정·학생 배정', classUrl(c))); classBox.append(box);
    });
  }

  function openDialog(student, title) {
    var g = ++dialogGeneration;
    editor = null; $('ahWeekdayForm').hidden = true; $('ahStudentHistory').hidden = true;
    $('ahStudentDialogTitle').textContent = student.name + ' · ' + title;
    $('ahStudentDialogMessage').textContent = '불러오는 중입니다.';
    $('ahStudentOriginal').href = '/easy/students?id=' + encodeURIComponent(student.studentId);
    if (!$('ahStudentDialog').open) $('ahStudentDialog').showModal();
    return g;
  }

  async function openEditor(student) {
    var g = openDialog(student, '등원 요일 수정');
    try {
      var res = await fetch(url(student.studentId, 'weekdays'), { credentials: 'same-origin', cache: 'no-store' });
      if (!res.ok) throw new Error('요일 정보를 불러오지 못했습니다. 다시 열어 주세요.');
      var saved = await res.json(); if (g !== dialogGeneration) return;
      editor = { studentId: student.studentId, revision: saved.revision };
      var chosen = saved.weekdays == null ? student.weekdays || [] : saved.weekdays;
      var fields = $('ahWeekdayChecks'); fields.replaceChildren();
      DAYS.forEach(function (day) { var label = el('label'); var input = el('input'); input.type = 'checkbox'; input.value = day; input.name = 'weekday'; input.checked = chosen.includes(day); label.append(input, ' ' + LABEL[day]); fields.append(label); });
      $('ahStudentDialogMessage').textContent = '현재 기준: ' + sourceText(student) + ' · 요일을 모두 해제하면 ‘요일 미정’으로 저장됩니다.';
      $('ahWeekdayForm').hidden = false; $('ahWeekdaySave').disabled = false; $('ahWeekdayReset').disabled = false;
    } catch (e) { if (g === dialogGeneration) $('ahStudentDialogMessage').textContent = e.message; }
  }

  async function saveWeekdays(reset) {
    if (!editor) return;
    var g = dialogGeneration;
    $('ahWeekdaySave').disabled = true; $('ahWeekdayReset').disabled = true;
    var days = reset ? null : Array.from($('ahWeekdayChecks').querySelectorAll('input:checked')).map(function (i) { return i.value; });
    try {
      var res = await fetch(url(editor.studentId, 'weekdays'), { method: 'PUT', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({weekdays: days, revision: editor.revision}) });
      if (!res.ok) throw new Error(res.status === 409 ? '다른 창에서 수정되었습니다. 닫고 다시 열어 확인해 주세요.' : '저장하지 못했습니다. 다시 시도해 주세요.');
      if (g !== dialogGeneration) { window.dispatchEvent(new CustomEvent('academy-schedule-saved')); return; }
      $('ahStudentDialog').close(); window.dispatchEvent(new CustomEvent('academy-schedule-saved'));
    } catch (e) { if (g === dialogGeneration) { $('ahStudentDialogMessage').textContent = e.message; $('ahWeekdaySave').disabled = false; $('ahWeekdayReset').disabled = false; } }
  }

  async function openHistory(student) {
    var g = openDialog(student, '출결 기록');
    try {
      var res = await fetch(url(student.studentId, 'events'), { credentials: 'same-origin', cache: 'no-store' });
      if (!res.ok) throw new Error('출결 기록을 불러오지 못했습니다.');
      var rows = await res.json(); if (g !== dialogGeneration) return;
      if (!Array.isArray(rows)) throw new Error('출결 기록 형식을 확인할 수 없습니다.');
      var box = $('ahStudentHistory'); box.replaceChildren(); box.hidden = false;
      $('ahStudentDialogMessage').textContent = rows.length ? '최근 기록 ' + Math.min(rows.length, 30) + '건 · 정정 이력 포함' : '이 사업체의 출결 기록이 없습니다.';
      rows.slice(0, 30).forEach(function (r) { var entry = el('p'); entry.append(el('strong', String(r.occurredAt || '').replace('T', ' ').slice(0, 16) + ' · ' + r.label), el('small', [r.note, r.correctionReason ? '정정 사유: ' + r.correctionReason : ''].filter(Boolean).join(' · '), 'ah-sub')); box.append(entry); });
    } catch (e) { if (g === dialogGeneration) $('ahStudentDialogMessage').textContent = e.message; }
  }

  window.AcademyStudents = {
    show: function (biz, rows, groups, plan) {
      businessId = biz; students = rows; classes = (groups || []).filter(function (c) { return c.status === 'ACTIVE'; });
      $('ahRosterSource').textContent = plan && plan.available
        ? '이번 주 ' + plan.weekStart + ' ~ ' + plan.weekEnd + ' · 제공된 진행표의 예정 일정 포함 · 직접 수정한 요일 우선' + (plan.unmatchedNames ? ' · 학생 연결 확인 필요 ' + plan.unmatchedNames + '명' : '')
        : (plan && plan.unavailable ? '제공된 진행표를 읽지 못해 최근 등원 기록 기준으로 표시합니다.' : '이번 주 제공 일정이 없어 최근 등원 기록 기준으로 표시합니다.');
      if (!selectedDay) selectedDay = window.AcademyHomeView.seoulClock(new Date()).day;
      if (!bound) {
        $('ahStudentSearch').addEventListener('input', render);
        $('ahClearClass').addEventListener('click', function () { selectedClass = ''; render(); });
        $('ahStudentDialogClose').addEventListener('click', function () { $('ahStudentDialog').close(); });
        $('ahStudentDialog').addEventListener('close', function () { ++dialogGeneration; editor = null; });
        $('ahWeekdayForm').addEventListener('submit', function (e) { e.preventDefault(); saveWeekdays(false); });
        $('ahWeekdayReset').addEventListener('click', function () { saveWeekdays(true); }); bound = true;
      }
      $('ahStudentsPanel').hidden = false; render();
    }
  };
})();
