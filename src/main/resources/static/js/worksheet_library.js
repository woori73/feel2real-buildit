/* 자료실(/dashboard/worksheets/library): F2R에 이미 있는 자료를 왼쪽 묶음(학교·학년/단원) → 오른쪽 표로 보여 준다. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  let sets = [], source = 'NAESIN', group = '', unit = '';
  const biz = () => encodeURIComponent($('business').value);
  const api = (path, query) => '/api/tutor/easy/worksheets' + path + '?businessId=' + biz() + (query || '');
  function msg(t) { const m = $('msg'); m.textContent = t || ''; m.style.display = t ? 'block' : 'none'; }
  const leafRows = () => WS.tree(sets.filter(s => s.source === source));

  function render() {
    const tree = leafRows();
    if (!tree.some(g => g.name === group)) group = tree.length ? tree[0].name : '';
    $('groups').innerHTML = '<span class="pp-side-head">' + (WS.GROUP_HEAD[source] || '묶음') + '</span>' + tree.map(g =>
      '<button type="button" role="tab" data-group="' + WS.esc(g.name) + '" aria-selected="' + (g.name === group) + '">' + WS.esc(g.name)
      + '<small>' + g.children.reduce((n, c) => n + c.sets.length, 0) + '개</small></button>').join('');
    const node = tree.find(g => g.name === group);
    const units = node ? node.children.map(c => c.name).filter(Boolean) : [];
    if (!units.includes(unit)) unit = '';
    $('units').innerHTML = units.length ? '<button type="button" class="pp-chip" data-unit="" aria-pressed="' + (unit === '') + '">전체</button>'
      + units.map(u => '<button type="button" class="pp-chip" data-unit="' + WS.esc(u) + '" aria-pressed="' + (u === unit) + '">' + WS.esc(u) + '</button>').join('') : '';
    const word = $('search').value.trim().toLowerCase();
    const rows = [];
    (node ? node.children : []).forEach(c => { if (!unit || c.name === unit) c.sets.forEach(s => { if (!word || s.title.toLowerCase().includes(word)) rows.push([c.name, s]); }); });
    $('list').innerHTML = rows.map(([u, s]) => '<tr><td class="pp-inline">' + WS.esc(WS.KIND[source] || source) + '</td><td class="pp-inline">' + WS.esc(u || '-') + '</td>'
      + '<td><span class="pp-title">' + WS.esc(s.leaf) + '</span><span class="pp-sub">' + WS.esc(s.title) + '</span></td><td class="pp-inline" data-label="문항">' + s.questionCount + '</td>'
      + '<td><div class="pp-cell-actions"><button type="button" class="pp-btn small" data-preview="' + WS.esc(s.setKey) + '">미리보기</button>'
      + '<a class="pp-btn small dark" href="/dashboard/worksheets/edit?businessId=' + biz() + '&set=' + encodeURIComponent(s.setKey) + '">이 자료로 학습지 만들기</a></div></td></tr>').join('');
    $('empty').hidden = rows.length > 0;
    $('empty').textContent = source === 'CSAT' && !sets.some(s => s.source === 'CSAT')
      ? '내신위키 수능 자료실에 올라온 수능·평가원 모의평가 문항이 없습니다. 내신위키 › 수능에서 시험을 올리면 여기에 보입니다.' : '자료가 없습니다.';
    $('groups').querySelectorAll('[data-group]').forEach(b => b.addEventListener('click', () => { group = b.dataset.group; unit = ''; render(); }));
    $('units').querySelectorAll('[data-unit]').forEach(b => b.addEventListener('click', () => { unit = b.dataset.unit; render(); }));
    $('list').querySelectorAll('[data-preview]').forEach(b => b.addEventListener('click', () => preview(b.dataset.preview)));
  }

  async function preview(setKey) {
    const set = sets.find(s => s.setKey === setKey);
    try {
      const d = await WS.json(api('/bank/questions', '&sets=' + encodeURIComponent(setKey)));
      $('previewTitle').textContent = set.title + ' · ' + d.questions.length + '문항';
      $('previewBody').innerHTML = d.questions.map((q, i) => WS.questionHtml(q, i + 1)).join('');
      $('preview').hidden = false;
    } catch (e) { msg(e.message); }
  }

  async function load() {
    msg('');
    document.querySelectorAll('[data-rail]').forEach(a => { a.href = a.getAttribute('href').split('?')[0] + '?businessId=' + biz(); });
    try { sets = await WS.json(api('/bank/sets')); render(); } catch (e) { sets = []; render(); msg(e.message); }
  }

  async function boot() {
    const list = await WS.json('/api/tutor/easy/worksheets/businesses');
    if (!list.length) { msg('연결된 사업체가 없습니다.'); return; }
    list.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    const wanted = WS.param('businessId');
    if (wanted && list.some(b => String(b.businessId) === wanted)) $('business').value = wanted;
    $('business').addEventListener('change', load);
    $('search').addEventListener('input', render);
    $('previewClose').addEventListener('click', () => { $('preview').hidden = true; });
    $('preview').addEventListener('click', e => { if (e.target === $('preview')) $('preview').hidden = true; });
    document.querySelectorAll('[data-source]').forEach(b => b.addEventListener('click', () => {
      source = b.dataset.source; group = ''; unit = '';
      document.querySelectorAll('[data-source]').forEach(x => x.setAttribute('aria-selected', String(x === b)));
      $('bankPanel').hidden = source === 'WIKI';
      $('wikiPanel').hidden = source !== 'WIKI';
      if (source === 'WIKI') wiki.load(); else render();
    }));
    wiki.boot();
    if (WS.param('tab') === 'wiki') document.querySelector('[data-source="WIKI"]').click();
    load();
  }
  /* ---------- 내신위키 자료 ---------- */
  const wiki = (function () {
    let contexts = [], current = null;
    function pickContext(c) {
      current = c;
      $('wikiContexts').querySelectorAll('[data-ctx]').forEach(b => b.setAttribute('aria-selected', String(b.dataset.ctx === WM.label(c))));
      $('wikiTitle').textContent = WM.label(c);
      $('wikiList').innerHTML = '';
      $('wikiEmpty').hidden = false;
      $('wikiEmpty').textContent = '불러오는 중…';
      WM.materials(c).then(items => {
        if (current !== c) return;
        $('wikiList').innerHTML = items.map(m => '<tr><td class="pp-inline"><span class="pp-badge">' + WS.esc(WM.KIND[m.kind] || m.kind) + '</span></td>'
          + '<td><span class="pp-title">' + WS.esc(m.title || m.original_name || '(이름 없음)') + '</span><span class="pp-sub">' + WS.esc(m.original_name || '') + '</span></td>'
          + '<td class="pp-inline" data-label="올린 날">' + WS.day(m.created_at) + '</td>'
          + '<td><div class="pp-cell-actions"><a class="pp-btn small" target="_blank" rel="noopener" href="' + WS.esc(WM.openUrl(m)) + '">열기</a></div></td></tr>').join('');
        $('wikiEmpty').hidden = items.length > 0;
        $('wikiEmpty').textContent = '이 시험에 올린 자료가 없습니다.';
      }).catch(e => { $('wikiEmpty').textContent = e.message; });
    }
    function renderContexts() {
      const groups = WM.group(contexts);
      $('wikiContexts').innerHTML = '<span class="pp-side-head">시험</span>' + (groups.length ? groups.map(g => '<span class="pp-side-head">' + WS.esc(g.name) + '</span>'
        + g.contexts.map(c => '<button type="button" role="tab" data-ctx="' + WS.esc(WM.label(c)) + '" aria-selected="false">' + WS.esc(c.year + ' ' + c.semester + ' ' + c.exam)
          + '<small>' + WS.esc(WM.counts(c)) + '</small></button>').join('')).join('') : '<p class="pp-muted">아직 올린 자료가 없습니다.</p>');
      $('wikiContexts').querySelectorAll('[data-ctx]').forEach(b => b.addEventListener('click', () => pickContext(contexts.find(c => WM.label(c) === b.dataset.ctx))));
      if (contexts.length) pickContext(current && contexts.find(c => WM.label(c) === WM.label(current)) || contexts[0]);
      else { $('wikiEmpty').hidden = false; $('wikiEmpty').textContent = '「기출·시험범위 자료 올리기」로 첫 자료를 올려 보세요.'; }
    }
    async function load() {
      try { contexts = await WM.get('/material-contexts'); renderContexts(); }
      catch (e) { $('wikiContexts').innerHTML = ''; $('wikiEmpty').hidden = false; $('wikiEmpty').textContent = '내신위키를 불러오지 못했습니다: ' + e.message; }
    }
    function openUpload() {
      const c = current || {};
      $('wuSchool').value = c.school || ''; $('wuGrade').value = c.grade || ''; $('wuYear').value = c.year || new Date().getFullYear();
      $('wuSemester').value = c.semester || ''; $('wuExam').value = c.exam || ''; $('wuTitle').value = ''; $('wuFile').value = '';
      $('wikiUploadMsg').style.display = 'none';
      hint();
      $('wikiUpload').hidden = false;
    }
    function hint() { const r = WM.UPLOAD[$('wuType').value]; $('wuHint').textContent = r ? '받는 파일: ' + r.ext.join(' ') + ' · 30MB까지' : ''; $('wuFile').accept = r ? r.ext.join(',') : ''; }
    async function submit(ev) {
      ev.preventDefault();
      const v = { type: $('wuType').value, school: $('wuSchool').value, grade: $('wuGrade').value, year: $('wuYear').value, semester: $('wuSemester').value, exam: $('wuExam').value, title: $('wuTitle').value };
      const m = $('wikiUploadMsg');
      $('wuSubmit').disabled = true;
      try {
        await WM.upload(v, $('wuFile').files[0]);
        $('wikiUpload').hidden = true;
        current = { school: v.school.trim(), grade: v.grade.trim(), year: Number(v.year), semester: v.semester.trim(), exam: v.exam.trim() };
        await load();
        msg('');
      } catch (e) { m.textContent = e.message; m.className = 'pp-msg'; m.style.display = 'block'; }
      finally { $('wuSubmit').disabled = false; }
    }
    function boot() {
      Object.keys(WM.UPLOAD).forEach(k => $('wuType').add(new Option(WM.UPLOAD[k].label, k)));
      $('wuType').addEventListener('change', hint);
      $('wikiUploadBtn').addEventListener('click', openUpload);
      $('wikiUploadClose').addEventListener('click', () => { $('wikiUpload').hidden = true; });
      $('wikiUploadForm').addEventListener('submit', submit);
    }
    return { boot, load };
  })();

  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
