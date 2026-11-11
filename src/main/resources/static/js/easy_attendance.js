/**
 * F2R 출결부(/easy/attendance) — 반별 출결부·월별 현황·반 관리·진행표 가져오기.
 * 권한은 서버(/api/admin/** + 사업체 접근 검증)가 최종 결정한다. 삭제 기능은 없다(정정·종료·해제).
 */
(function () {
  const BASE = '/api/admin/easy/attendance';
  const V = window.EasyAttendanceView;
  let classes = [];
  let students = [];
  let selectedClass = null;
  let preview = null;
  let mappings = [];

  const $ = id => document.getElementById(id);
  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }
  function businessId() { return $('business').value; }
  function todayKst() { return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10); }

  function message(id, text, isError) {
    const n = $(id);
    n.textContent = text || '';
    n.className = 'msg' + (isError ? ' err' : '');
    n.style.display = text ? 'block' : 'none';
  }

  async function call(method, path, body) {
    const opts = { method, headers: {} };
    if (body !== undefined) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    const sep = path.includes('?') ? '&' : '?';
    const res = await fetchAuth(BASE + path + sep + 'businessId=' + encodeURIComponent(businessId()), opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!res.ok) throw new Error((data && (data.message || data.error)) || text || ('요청 실패 (' + res.status + ')'));
    return data;
  }

  function button(label, onClick, cls) {
    const b = el('button', cls || 'btn-secondary small-btn', label);
    b.type = 'button';
    b.addEventListener('click', async () => {
      b.disabled = true;
      try { await onClick(); } catch (e) { message('msg', e.message, true); } finally { b.disabled = false; }
    });
    return b;
  }

  // ------------------------------------------------------------ 탭

  function showTab(name) {
    document.querySelectorAll('#tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.id === 'tab-' + name));
    message('msg', '');
  }

  // ------------------------------------------------------------ 반 목록 공통

  async function loadClasses() {
    classes = await call('GET', '/classes?includeEnded=' + ($('showEnded').checked ? 'true' : 'false'));
    const active = classes.filter(c => c.status === 'ACTIVE');
    ['sheetClass', 'monthClass'].forEach(id => fillClassSelect($(id), active, false));
    fillClassSelect($('impClass'), active, true);
    renderClassList();
  }

  function fillClassSelect(select, list, optional) {
    const prev = select.value;
    select.innerHTML = '';
    if (optional) select.add(new Option('반 없이 넣기', ''));
    if (!list.length && !optional) select.add(new Option('먼저 「반 관리」에서 반을 만드세요', ''));
    list.forEach(c => select.add(new Option(c.name, String(c.classId))));
    if ([...select.options].some(o => o.value === prev)) select.value = prev;
  }

  async function loadStudents() {
    const res = await fetchAuth('/api/admin/easy/students');
    students = res.ok ? await res.json() : [];
  }

  // ------------------------------------------------------------ 출결부

  async function loadSheet() {
    const classId = $('sheetClass').value;
    const table = $('sheetTable');
    table.innerHTML = '';
    if (!classId) { $('sheetInfo').textContent = '반을 먼저 만드세요.'; return; }
    const date = $('sheetDate').value || todayKst();
    const rows = await call('GET', '/sheet?classId=' + classId + '&date=' + date);
    const c = classes.find(x => String(x.classId) === classId);
    $('sheetInfo').textContent = (c ? c.name + ' · ' : '') + date + ' · 학생 ' + rows.length + '명' + (c && V.classLine(c) ? ' · ' + V.classLine(c) : '');
    const head = el('tr');
    ['학생', '오늘 기록', '기록하기'].forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    if (!rows.length) {
      const tr = el('tr'); const td = el('td', null, '이 날짜에 배정된 학생이 없습니다. 「반 관리」에서 학생을 배정하세요.'); td.colSpan = 3; tr.appendChild(td); table.appendChild(tr);
    }
    rows.forEach(r => {
      const tr = el('tr');
      tr.appendChild(el('td', null, r.name + (r.school ? ' · ' + r.school : '')));
      const evTd = el('td');
      if (!r.events.length) evTd.appendChild(el('span', 'muted', '기록 없음'));
      r.events.forEach(ev => {
        const wrap = el('div');
        const chip = el('span', 'ev ' + ev.eventType, ev.label + ' ' + V.timeText(ev));
        chip.title = [ev.note, ev.correctionReason ? '정정 사유: ' + ev.correctionReason : ''].filter(Boolean).join(' / ');
        wrap.appendChild(chip);
        wrap.appendChild(button('정정', () => openCorrect(wrap, ev)));
        evTd.appendChild(wrap);
      });
      tr.appendChild(evTd);
      const actions = el('td');
      const box = el('div', 'att-btns');
      V.TYPES.forEach(([type, label]) => box.appendChild(button(label, () => record(r, type, date))));
      actions.appendChild(box);
      tr.appendChild(actions);
      table.appendChild(tr);
    });
  }

  async function record(row, type, date) {
    const note = prompt(row.name + ' · ' + V.label(type) + ' 메모 (없으면 비워 두세요)', '');
    if (note === null) return;
    const now = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(11, 16);
    const occurredAt = date === todayKst() ? null : date + 'T' + now;
    await call('POST', '/events', { classId: Number($('sheetClass').value), studentId: row.studentId, eventType: type, occurredAt, note });
    message('msg', '저장했습니다.', false);
    await loadSheet();
  }

  function openCorrect(wrap, ev) {
    if (wrap.querySelector('.correct-box')) return;
    const box = el('div', 'correct-box');
    const select = el('select');
    V.TYPES.forEach(([type, label]) => select.add(new Option(label, type)));
    select.value = ev.eventType;
    const reason = el('input'); reason.placeholder = '정정 사유 (꼭 적어 주세요)'; reason.maxLength = 200;
    box.appendChild(select); box.appendChild(reason);
    box.appendChild(button('정정 저장', async () => {
      if (!reason.value.trim()) { message('msg', '정정 사유를 적어 주세요.', true); return; }
      await call('POST', '/events/' + ev.eventId + '/correct', { eventType: select.value, reason: reason.value.trim() });
      message('msg', '정정했습니다. 이전 기록은 이력으로 남습니다.', false);
      await loadSheet();
    }, 'btn-primary small-btn'));
    wrap.appendChild(box);
  }

  // ------------------------------------------------------------ 월별 현황

  async function loadMonth() {
    const classId = $('monthClass').value;
    const table = $('monthTable');
    table.innerHTML = '';
    if (!classId) return;
    const month = $('monthValue').value || todayKst().slice(0, 7);
    const data = await call('GET', '/monthly?classId=' + classId + '&month=' + month);
    const g = V.monthGrid(data);
    const head = el('tr');
    head.appendChild(el('th', null, '학생'));
    g.days.forEach(d => head.appendChild(el('th', null, String(d))));
    g.countHeaders.forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    if (!g.rows.length) {
      const tr = el('tr'); const td = el('td', null, '이 달에 배정된 학생이 없습니다.'); td.colSpan = g.days.length + 6; tr.appendChild(td); table.appendChild(tr);
    }
    g.rows.forEach(r => {
      const tr = el('tr');
      tr.appendChild(el('td', null, r.name));
      r.cells.forEach(c => tr.appendChild(el('td', null, c)));
      r.counts.forEach(c => tr.appendChild(el('td', null, String(c))));
      table.appendChild(tr);
    });
  }

  // ------------------------------------------------------------ 반 관리

  function renderDayPicker() {
    const box = $('cDays');
    box.innerHTML = '';
    V.WEEKDAYS.forEach(([code, label]) => {
      const l = el('label'); const cb = el('input'); cb.type = 'checkbox'; cb.value = code;
      l.appendChild(cb); l.appendChild(document.createTextNode(label)); box.appendChild(l);
    });
  }

  function classForm() {
    return {
      name: $('cName').value.trim(), teacherName: $('cTeacher').value.trim(), room: $('cRoom').value.trim(),
      description: $('cDesc').value.trim(), startTime: $('cStart').value || null, endTime: $('cEnd').value || null,
      capacity: $('cCap').value ? Number($('cCap').value) : null,
      weekdays: [...document.querySelectorAll('#cDays input:checked')].map(cb => cb.value)
    };
  }

  function resetClassForm() {
    ['cId', 'cName', 'cTeacher', 'cRoom', 'cDesc', 'cStart', 'cEnd', 'cCap'].forEach(id => { $(id).value = ''; });
    document.querySelectorAll('#cDays input').forEach(cb => { cb.checked = false; });
    $('classFormTitle').textContent = '반 만들기';
    $('classCancel').style.display = 'none';
  }

  async function saveClass() {
    const form = classForm();
    if (!form.name) { message('msg', '반 이름을 적어 주세요.', true); return; }
    const id = $('cId').value;
    if (id) await call('PUT', '/classes/' + id, form); else await call('POST', '/classes', form);
    message('msg', '저장했습니다.', false);
    resetClassForm();
    await loadClasses();
  }

  function editClass(c) {
    $('cId').value = c.classId; $('cName').value = c.name || ''; $('cTeacher').value = c.teacherName || '';
    $('cRoom').value = c.room || ''; $('cDesc').value = c.description || '';
    $('cStart').value = (c.startTime || '').slice(0, 5); $('cEnd').value = (c.endTime || '').slice(0, 5);
    $('cCap').value = c.capacity || '';
    document.querySelectorAll('#cDays input').forEach(cb => { cb.checked = (c.weekdays || []).includes(cb.value); });
    $('classFormTitle').textContent = '반 정보 수정 — ' + c.name;
    $('classCancel').style.display = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function renderClassList() {
    const box = $('classList');
    box.innerHTML = '';
    if (!classes.length) { box.appendChild(el('p', 'muted', '아직 반이 없습니다. 위에서 반을 만드세요.')); return; }
    classes.forEach(c => {
      const item = el('div', 'class-item');
      const title = el('strong', null, c.name + (c.status === 'ENDED' ? ' (종료)' : ''));
      item.appendChild(title);
      item.appendChild(el('div', 'muted', [V.classLine(c), '학생 ' + c.memberCount + '명'].filter(Boolean).join(' · ')));
      if (c.status === 'ACTIVE') {
        const row = el('div', 'att-btns');
        row.appendChild(button('학생 보기·배정', () => openMembers(c)));
        row.appendChild(button('수정', async () => editClass(c)));
        row.appendChild(button('반 종료', async () => {
          if (!confirm('「' + c.name + '」 반을 종료할까요?\n학생 배정이 모두 해제되고 되돌릴 수 없습니다. 출결 기록은 남습니다.')) return;
          await call('POST', '/classes/' + c.classId + '/end');
          message('msg', '반을 종료했습니다.', false);
          $('memberCard').style.display = 'none';
          await loadClasses();
        }));
        item.appendChild(row);
      }
      box.appendChild(item);
    });
  }

  async function openMembers(c) {
    selectedClass = c;
    $('memberCard').style.display = '';
    $('memberTitle').textContent = '반 학생 — ' + c.name;
    const members = await call('GET', '/classes/' + c.classId + '/members');
    const inClass = new Set(members.map(m => m.studentId));
    const add = $('addStudent');
    add.innerHTML = '';
    students.filter(s => s.status !== 'WITHDRAWN' && !inClass.has(s.studentId))
      .forEach(s => add.add(new Option(s.name + (s.school ? ' · ' + s.school : ''), String(s.studentId))));
    if (!add.options.length) add.add(new Option('배정할 학생이 없습니다', ''));
    const table = $('memberTable');
    table.innerHTML = '';
    const head = el('tr');
    ['학생', '배정일', ''].forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    members.forEach(m => {
      const tr = el('tr');
      tr.appendChild(el('td', null, m.name + (m.school ? ' · ' + m.school : '')));
      tr.appendChild(el('td', null, m.assignedOn));
      const td = el('td');
      const box = el('div', 'att-btns');
      box.appendChild(button('빼기', async () => {
        if (!confirm(m.name + ' 학생을 이 반에서 뺄까요? 학생 등록과 출결 기록은 남습니다.')) return;
        await call('POST', '/classes/' + c.classId + '/members/' + m.studentId + '/release');
        await refreshAfterMember();
      }));
      const target = el('select');
      classes.filter(x => x.status === 'ACTIVE' && x.classId !== c.classId).forEach(x => target.add(new Option(x.name, String(x.classId))));
      if (target.options.length) {
        box.appendChild(target);
        box.appendChild(button('이동', async () => {
          const to = classes.find(x => String(x.classId) === target.value);
          if (!confirm(m.name + ' 학생을 「' + c.name + '」에서 「' + to.name + '」(으)로 옮길까요?')) return;
          await call('POST', '/classes/' + c.classId + '/members/' + m.studentId + '/move', { targetClassId: Number(target.value) });
          await refreshAfterMember();
        }));
      }
      td.appendChild(box);
      tr.appendChild(td);
      table.appendChild(tr);
    });
  }

  async function refreshAfterMember() {
    await loadClasses();
    const c = classes.find(x => x.classId === selectedClass.classId);
    if (c) await openMembers(c);
  }

  async function assignStudent() {
    const sid = $('addStudent').value;
    if (!selectedClass || !sid) return;
    await call('POST', '/classes/' + selectedClass.classId + '/members', { studentId: Number(sid) });
    message('msg', '배정했습니다.', false);
    await refreshAfterMember();
  }

  // ------------------------------------------------------------ 진행표 가져오기

  async function loadPreview() {
    message('impMsg', '');
    const year = $('impYear').value.trim();
    if (!/^\d{4}$/.test(year)) { message('impMsg', '연도를 4자리로 적어 주세요.', true); return; }
    $('impInfo').textContent = '진행표를 읽는 중입니다…';
    preview = await call('GET', '/roster-import/preview?year=' + year);
    mappings = V.defaultMappings(preview.names);
    $('impInfo').textContent = year + '년 진행표 · 이름 ' + preview.names.length + '개 · 수업한 날 ' + preview.lessonDays + '건'
      + (preview.futureLessonDays ? ' (오늘 이후 예정 ' + preview.futureLessonDays + '건 제외)' : '') + '. ' + preview.rule;
    const table = $('impTable');
    table.innerHTML = '';
    const head = el('tr');
    ['진행표 이름', '수업한 날', '기간', '처리'].forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    preview.names.forEach((n, i) => {
      const tr = el('tr');
      tr.appendChild(el('td', null, n.name));
      tr.appendChild(el('td', null, n.lessonDays + '일'));
      tr.appendChild(el('td', null, n.firstDate + ' ~ ' + n.lastDate));
      const td = el('td');
      const select = el('select');
      if (n.suggestedAction === 'CHOOSE') select.add(new Option('— 같은 이름 학생이 여러 명: 골라 주세요 —', ''));
      n.candidates.forEach(c => select.add(new Option('기존 학생 연결: ' + c.name + (c.school ? ' · ' + c.school : '') + (c.status === 'WITHDRAWN' ? ' (퇴원)' : ''), 'MATCH:' + c.studentId)));
      select.add(new Option('새 학생으로 등록', 'CREATE'));
      select.add(new Option('건너뛰기 (넣지 않음)', 'SKIP'));
      const m = mappings[i];
      select.value = m.action === 'MATCH' ? 'MATCH:' + m.studentId : (m.action || '');
      select.addEventListener('change', () => {
        const v = select.value;
        mappings[i] = v.startsWith('MATCH:') ? { name: n.name, action: 'MATCH', studentId: Number(v.slice(6)) }
          : { name: n.name, action: v, studentId: null };
      });
      td.appendChild(select);
      tr.appendChild(td);
      table.appendChild(tr);
    });
    $('impApplyRow').style.display = preview.names.length ? '' : 'none';
  }

  async function applyImport() {
    if (!preview) return;
    const problems = V.mappingProblems(mappings);
    if (!problems.ok) {
      message('impMsg', '처리 방법을 고르지 않은 이름이 있습니다: ' + problems.unchosen.concat(problems.noStudent).slice(0, 5).join(', '), true);
      return;
    }
    if (!confirm(preview.year + '년 진행표를 출결부에 넣을까요?\n출결은 지울 수 없고, 잘못 들어간 기록은 사유를 적어 정정해야 합니다.')) return;
    const classId = $('impClass').value ? Number($('impClass').value) : null;
    const result = await call('POST', '/roster-import/apply', {
      businessId: Number(businessId()), year: preview.year, previewHash: preview.previewHash, classId, mappings
    });
    message('impMsg', V.applySummary(result), false);
    preview = null;
    $('impApplyRow').style.display = 'none';
    await loadStudents();
    await loadClasses();
  }

  // ------------------------------------------------------------ 시작

  async function init() {
    await Easy.guardAdmin();
    const businesses = await Easy.loadBusinesses();
    businesses.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    const link = V.deepLink(location.search);
    if (link.businessId && businesses.some(b => String(b.businessId) === link.businessId)) $('business').value = link.businessId;
    $('sheetDate').value = link.date || todayKst();
    $('monthValue').value = todayKst().slice(0, 7);
    renderDayPicker();
    document.querySelectorAll('#tabs button').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));
    const guarded = fn => async () => { try { await fn(); } catch (e) { message('msg', e.message, true); } };
    $('business').addEventListener('change', guarded(async () => { await loadClasses(); $('sheetTable').innerHTML = ''; }));
    $('sheetLoad').addEventListener('click', guarded(loadSheet));
    $('monthLoad').addEventListener('click', guarded(loadMonth));
    $('classSave').addEventListener('click', guarded(saveClass));
    $('classCancel').addEventListener('click', resetClassForm);
    $('showEnded').addEventListener('change', guarded(loadClasses));
    $('addStudentBtn').addEventListener('click', guarded(assignStudent));
    $('impPreview').addEventListener('click', async () => { try { await loadPreview(); } catch (e) { $('impInfo').textContent = ''; message('impMsg', e.message, true); } });
    $('impApply').addEventListener('click', async () => { try { await applyImport(); } catch (e) { message('impMsg', e.message, true); } });
    if (businesses.length) {
      await loadStudents();
      await loadClasses();
      if (link.classId && [...$('sheetClass').options].some(o => o.value === link.classId)) $('sheetClass').value = link.classId;
      if ($('sheetClass').value) await loadSheet();
    }
  }

  init().catch(e => message('msg', e.message, true));
})();
