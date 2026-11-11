/*
 * 정보 화면(/dashboard/info). 이미 올린 자료를 원문 그대로 읽고, 학생명단은 선택 학원의 학생 명단과 대조한다.
 * 쓰기는 두 가지뿐이다: 원장이 보류 행을 연결·제외 확인(/api/academy-home/roster/links),
 * 추가 후보를 기존 AI 업무 비서 '학생 등록' 초안으로 만들고 원장이 승인(/api/dashboard/assistant/v1/drafts).
 */
(function () {
  'use strict';
  var V = window.AcademyInfoView;
  var $ = function (id) { return document.getElementById(id); };
  var ACADEMY_KEY = 'academycraft.academy';
  var state = { user: null, context: null, generation: 0, notes: {} };

  function el(tag, text, cls) { var e = document.createElement(tag); if (text != null) e.textContent = text; if (cls) e.className = cls; return e; }
  function link(text, href, cls) { var a = el('a', text, cls); a.href = href; return a; }
  function stored() { try { return sessionStorage.getItem(ACADEMY_KEY); } catch (_) { return null; } }

  async function api(path, options) {
    var res = await fetch(path, Object.assign({ credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } }, options || {}));
    var body = await res.json().catch(function () { return {}; });
    if (res.status === 401) { location.href = '/admin/login?returnUrl=' + encodeURIComponent('/dashboard/info'); throw new Error('로그인이 필요합니다.'); }
    if (!res.ok) throw new Error(body.message || body.error || '요청 실패 (' + res.status + ')');
    return body;
  }
  function post(path, body) { return api(path, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify(body) }); }

  function renderBlocks(box, read) {
    var r = read.result;
    box.replaceChildren();
    if (r.status === 'READ') {
      box.append(el('p', '읽기 경로: ' + r.route + (r.pageCount ? ' · 전체 ' + r.pageCount + '쪽' : '') + (r.truncated ? ' · 길이 제한으로 뒷부분 생략(원본에서 확인)' : ''), 'ai-route'));
      r.blocks.forEach(function (b) {
        if (b.type === 'PARAGRAPH') box.append(el('p', b.text, 'ai-para'));
        else if (b.type === 'PAGE') { var s = el('section', null, 'ai-page'); s.append(el('h4', b.page + '쪽'), el('p', b.text, 'ai-para')); box.append(s); }
        else if (b.type === 'TABLE') {
          var wrap = el('div', null, 'ai-table-wrap'); wrap.tabIndex = 0; wrap.setAttribute('role', 'region'); wrap.setAttribute('aria-label', '표 (가로로 스크롤)');
          var t = el('table'); var thead = el('thead'), tr = el('tr');
          (b.header || []).forEach(function (h) { var th = el('th', h); th.scope = 'col'; tr.append(th); });
          thead.append(tr); t.append(thead);
          var tbody = el('tbody');
          (b.rows || []).forEach(function (row) { var r2 = el('tr'); row.forEach(function (c) { r2.append(el('td', c)); }); tbody.append(r2); });
          t.append(tbody); wrap.append(t); box.append(wrap);
        }
      });
    } else {
      box.append(el('p', V.STATUS_TEXT[r.status] + ' · ' + r.reason, 'ai-reason'));
      var images = V.pageImages(read, 5);
      if (read.preview && read.extension !== 'pdf') { var img = el('img'); img.src = read.preview; img.alt = '확인용 원본 이미지'; img.loading = 'lazy'; img.className = 'ai-image'; box.append(img); }
      images.forEach(function (src, i) { var img = el('img'); img.src = src; img.alt = '확인용 원본 ' + (i + 1) + '쪽'; img.loading = 'lazy'; img.className = 'ai-image'; box.append(img); });
    }
    var actions = el('div', null, 'ai-actions');
    actions.append(link('원본 열기', read.original, 'ah-text-link'));
    var again = el('button', '다시 읽기', 'ai-btn-sub'); again.type = 'button';
    again.onclick = function () { openBody(box, read.projectId, read.fileId, true); };
    actions.append(again);
    box.append(actions);
  }

  async function openBody(box, projectId, fileId, force) {
    if (box.dataset.loaded === '1' && !force) return;
    box.replaceChildren(el('p', '원문을 읽는 중입니다…', 'ah-note'));
    try {
      var read = await api('/api/academy-home/materials/text?projectId=' + encodeURIComponent(projectId) + '&fileId=' + encodeURIComponent(fileId));
      renderBlocks(box, read);
      box.dataset.loaded = '1';
    } catch (e) {
      box.replaceChildren(el('p', '원문을 불러오지 못했습니다: ' + e.message, 'ai-reason'));
      var again = el('button', '다시 읽기', 'ai-btn-sub'); again.type = 'button'; again.onclick = function () { openBody(box, projectId, fileId, true); };
      box.append(again);
    }
  }

  function fileList(item) {
    var ul = el('ul', null, 'ai-files');
    item.files.forEach(function (f) {
      var li = el('li');
      li.append(el('span', f.role, 'ah-chip'), el('span', ' ' + f.filename + ' · ' + V.size(f.sizeBytes) + ' ', 'ai-filename'),
        link('원본', '/api/studio/files/' + encodeURIComponent(f.fileId) + '/content?projectId=' + encodeURIComponent(item.projectId), 'ah-text-link'));
      ul.append(li);
    });
    return ul;
  }

  function card(item) {
    var art = el('article', null, 'ah-panel ai-card');
    art.append(el('span', V.KIND_TEXT[item.kind] || '자료', 'ah-chip'));
    art.append(el('h2', item.title, 'ai-title'));
    art.append(el('p', item.context, 'ah-sub'));
    if (item.typedNote) {
      var note = el('details', null, 'ai-note');
      note.append(el('summary', '등록할 때 적은 설명 (파일 원문 아님)'), el('p', item.typedNote, 'ai-para'));
      art.append(note);
    }
    var primary = V.primaryFile(item);
    if (primary) {
      var d = el('details', null, 'ai-body');
      d.append(el('summary', '본문 읽기'));
      var box = el('div', null, 'ai-body-in');
      d.append(box);
      d.addEventListener('toggle', function () { if (d.open) openBody(box, item.projectId, primary.fileId, false); });
      art.append(d);
    } else art.append(el('p', '연결된 원본 파일이 없습니다.', 'ah-note'));
    if (item.kind === 'ROSTER' && item.year) art.append(reconcilePanel(item));
    if (item.files.length) { art.append(el('h3', '원본 파일', 'ai-h3')); art.append(fileList(item)); }
    return art;
  }

  // ---- 학생명단 ↔ 학원 학생 대조 ----
  function reconcilePanel(item) {
    var d = el('details', null, 'ai-reconcile');
    d.append(el('summary', '명단 반영 확인 · ' + (state.context ? '선택 학원 학생과 대조' : '학원 연결 후 가능')));
    var box = el('div', null, 'ai-body-in');
    d.append(box);
    d.addEventListener('toggle', function () { if (d.open) loadReconcile(box, item.year); });
    return d;
  }

  async function loadReconcile(box, year) {
    var g = state.generation;
    box.replaceChildren(el('p', '대조하는 중입니다…', 'ah-note'));
    if (!state.context) { box.replaceChildren(el('p', '연결된 학원이 없어 대조할 수 없습니다.', 'ai-reason')); return; }
    try {
      var data = await api('/api/academy-home/roster/reconcile?year=' + encodeURIComponent(year) + '&academyId=' + encodeURIComponent(state.context.academyId));
      if (g !== state.generation) return;
      renderReconcile(box, year, data);
    } catch (e) { if (g === state.generation) box.replaceChildren(el('p', '대조하지 못했습니다: ' + e.message, 'ai-reason')); }
  }

  function renderReconcile(box, year, data) {
    var report = data.report, owner = data.role === 'ACADEMY_OWNER';
    box.replaceChildren();
    // 승인 결과처럼 다시 대조해도 남겨야 할 알림(연도별 마지막 한 줄).
    if (state.notes[year]) box.append(el('p', state.notes[year], 'ai-sum'));
    box.append(el('p', V.reconcileSummary(report), report.existing + report.added + report.hold + report.excluded === report.sourceNames ? 'ai-sum' : 'ai-reason'));
    box.append(el('p', '이 학원 재원생 ' + data.activeStudentCount + '명(학원 홈 재원생 수와 같은 기준). 이름만 같은 학생은 자동으로 합치지 않습니다.', 'ah-note'));
    if (!owner) box.append(el('p', '연결·제외 확인과 학생 등록 승인은 이 학원 원장 연결 계정에서만 할 수 있습니다.', 'ai-reason'));
    V.groupRows(report.rows).forEach(function (group) {
      var sec = el('section', null, 'ai-group');
      sec.append(el('h4', group.label + ' ' + group.rows.length + '명'));
      var ul = el('ul', null, 'ai-rows');
      var newChecks = [];
      group.rows.forEach(function (row) {
        var li = el('li');
        li.append(el('strong', row.name), el('span', ' · ' + row.occurrences + '회 기록 · ' + row.reason, 'ah-sub'));
        if (owner && row.status === 'HOLD') {
          var form = el('form', null, 'ai-row-form');
          var sel = el('select'); sel.setAttribute('aria-label', row.name + ' 연결할 학생');
          (row.candidates || []).filter(function (c) { return c.active; }).forEach(function (c) { var o = el('option', c.name + ' (#' + c.id + (c.grade ? ' · ' + c.grade : '') + (c.schoolName ? ' · ' + c.schoolName : '') + ')'); o.value = c.id; sel.append(o); });
          var linkBtn = el('button', '이 학생으로 연결', 'ai-btn'); linkBtn.type = 'submit'; linkBtn.disabled = !sel.options.length;
          var exBtn = el('button', '명단에서 제외', 'ai-btn-sub'); exBtn.type = 'button';
          form.append(sel, linkBtn, exBtn);
          form.addEventListener('submit', function (ev) { ev.preventDefault(); confirmLink(box, year, row.name, 'LINKED', Number(sel.value)); });
          exBtn.onclick = function () { confirmLink(box, year, row.name, 'EXCLUDED', null); };
          li.append(form);
        }
        if (owner && row.status === 'NEW') { var cb = el('input'); cb.type = 'checkbox'; cb.value = row.name; cb.setAttribute('aria-label', row.name + ' 학생 등록 초안에 포함'); li.prepend(cb); newChecks.push(cb); }
        if (owner && row.status === 'EXISTING' && row.linkedStudentId) li.append(document.createTextNode(' '), link('학생 보기', '/academycraft/screens/MBR-01-student-list.html?studentId=' + row.linkedStudentId, 'ah-text-link'));
        ul.append(li);
      });
      sec.append(ul);
      if (newChecks.length) {
        var make = el('button', '선택한 이름을 학생 등록 초안으로', 'ai-btn'); make.type = 'button';
        var out = el('div', null, 'ai-drafts');
        make.onclick = function () { makeDrafts(newChecks.filter(function (c) { return c.checked; }).map(function (c) { return c.value; }), make, out, box, year); };
        sec.append(make, out);
      }
      box.append(sec);
    });
  }

  async function confirmLink(box, year, name, status, studentId) {
    var g = state.generation;
    try {
      var data = await post('/api/academy-home/roster/links', { academyId: state.context.academyId, year: Number(year), name: name, status: status, studentId: studentId });
      if (g === state.generation) renderReconcile(box, year, data);
    } catch (e) { alertMsg(e.message); }
  }

  /** 기존 AI 업무 비서 초안(학생 등록) → 원장 승인 후에만 실제 저장. 이름만 넣고 다른 사실은 지어내지 않는다. */
  async function makeDrafts(names, button, out, box, year) {
    if (!names.length) { alertMsg('학생 등록 초안으로 만들 이름을 고르세요.'); return; }
    button.disabled = true;
    out.replaceChildren();
    for (var i = 0; i < names.length; i++) {
      try {
        var d = await post('/api/dashboard/assistant/v1/drafts', { operation: 'STUDENT_CREATE', academyId: state.context.academyId, businessId: null, input: { name: names[i] } });
        out.append(draftRow(d, box, year));
      } catch (e) { out.append(el('p', names[i] + ' 초안 실패: ' + e.message, 'ai-reason')); }
    }
    out.append(el('p', '초안은 10분 안에 승인해야 저장됩니다. 승인하지 않은 초안은 저장되지 않습니다.', 'ah-note'));
  }

  function draftRow(d, box, year) {
    var row = el('div', null, 'ai-draft');
    row.append(el('span', '학생 등록 초안 · ' + (d.preview && d.preview.name) + ' · 승인 대기'));
    ['approve', 'cancel'].forEach(function (action) {
      var b = el('button', action === 'approve' ? '승인하고 등록' : '취소', action === 'approve' ? 'ai-btn' : 'ai-btn-sub'); b.type = 'button';
      b.onclick = async function () {
        row.querySelectorAll('button').forEach(function (x) { x.disabled = true; });
        try {
          var r = await post('/api/dashboard/assistant/v1/drafts/' + encodeURIComponent(d.id) + '/' + action, { planHash: d.planHash });
          row.replaceChildren(el('span', (d.preview && d.preview.name) + ' · ' + (r.status === 'VERIFIED' ? '저장 확인됨' : r.status === 'CANCELLED' ? '취소함' : '결과 확인 필요(' + r.status + ')')));
          if (r.status === 'VERIFIED') {
            var name = d.preview && d.preview.name, recordId = r.result && Number(r.result.recordId);
            state.notes[year] = name + ' 학생 등록을 원장 승인으로 저장했습니다. 다시 대조한 결과입니다.';
            // 이 행에서 방금 만든 학생은 확실히 같은 사람이므로 바로 연결한다(저장된 레코드 ID 기준, 이름 추측 아님).
            if (recordId > 0) {
              try { renderReconcile(box, year, await post('/api/academy-home/roster/links', { academyId: state.context.academyId, year: Number(year), name: name, status: 'LINKED', studentId: recordId })); return; }
              catch (e) { state.notes[year] += ' 자동 연결은 실패해 보류로 남았습니다: ' + e.message; }
            }
            loadReconcile(box, year);
          }
        } catch (e) { row.append(el('span', ' 실패: ' + e.message + ' — 자동으로 다시 실행하지 않습니다.', 'ai-reason')); }
      };
      row.append(b);
    });
    return row;
  }

  function alertMsg(text) { $('aiAlert').hidden = !text; $('aiAlert').textContent = text || ''; }

  async function init() {
    try { state.user = await ensureCurrentUser(); } catch (_) { state.user = null; }
    if (!state.user || (state.user.role !== 'ADMIN' && state.user.role !== 'TUTOR')) { location.href = '/admin/login?returnUrl=' + encodeURIComponent('/dashboard/info'); return; }
    var g = ++state.generation;
    try {
      var q = stored() ? '?academyId=' + encodeURIComponent(stored()) : '';
      state.context = await api('/academycraft/api/rea/integration/context' + q);
      var opt = (state.context.academyOptions || []).find(function (o) { return Number(o.id) === Number(state.context.academyId); });
      $('aiAcademyName').textContent = opt ? opt.name : '학원 #' + state.context.academyId;
    } catch (_) { state.context = null; $('aiAcademyName').textContent = '학원 연결 필요'; }
    try {
      var data = await api('/api/academy-home/materials');
      if (g !== state.generation) return;
      $('aiList').replaceChildren();
      if (data.notice) $('aiList').append(el('p', data.notice, 'ah-empty'));
      (data.items || []).forEach(function (item) { $('aiList').append(card(item)); });
    } catch (e) { $('aiList').replaceChildren(el('p', '자료 목록을 불러오지 못했습니다: ' + e.message, 'ai-reason')); }
    $('aiMain').setAttribute('aria-busy', 'false');
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
