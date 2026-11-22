/* 학습지 만들기·수정(/dashboard/worksheets/edit[?id=]). STEP 1 = 범위 트리 + 문제 옵션, STEP 2 = 시험지 미리보기 + 문제 목록·문제 추가·디자인 설정. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const businessId = WS.param('businessId');
  const worksheetId = WS.param('id');
  const api = (path, query) => '/api/tutor/easy/worksheets' + (path || '') + '?businessId=' + encodeURIComponent(businessId) + (query || '');
  let allSets = [], source = 'NAESIN', checkedSets = new Set(), inSets = [], candidates = [], picked = [], columns = 2, queryNo = 0, focusNo = 0;

  function msg(t, ok) { const m = $('msg'); m.textContent = t || ''; m.className = 'pp-msg' + (ok ? ' ok' : ''); m.style.display = t ? 'block' : 'none'; }
  const refs = () => picked.map(q => q.ref);
  const pressed = (box, attr) => Array.from(box.querySelectorAll('[aria-pressed="true"]')).map(b => b.dataset[attr]);
  const today = () => { const d = new Date(); return d.getFullYear() + '.' + String(d.getMonth() + 1).padStart(2, '0') + '.' + String(d.getDate()).padStart(2, '0'); };

  function counts() {
    $('remain').textContent = WS.remaining(candidates, refs()).length;
    $('pickedCount').textContent = picked.length;
  }

  /* STEP 1 — 범위 트리 */
  function renderTree() {
    const leaf = s => '<label class="pp-leaf"><input type="checkbox" data-set="' + WS.esc(s.setKey) + '"' + (checkedSets.has(s.setKey) ? ' checked' : '') + '> ' + WS.esc(s.leaf) + '<span class="pp-count">' + s.questionCount + '문항</span></label>';
    const tree = WS.tree(allSets.filter(s => s.source === source));
    $('tree').innerHTML = tree.map((g, gi) => {
      const keys = g.children.flatMap(c => c.sets.map(s => s.setKey));
      const open = gi === 0 || keys.some(k => checkedSets.has(k));
      return '<details' + (open ? ' open' : '') + '><summary><input type="checkbox" data-all="' + WS.esc(keys.join('|')) + '"' + (keys.every(k => checkedSets.has(k)) ? ' checked' : '') + ' aria-label="' + WS.esc(g.name) + ' 전체"> ' + WS.esc(g.name) + '<span class="pp-count">' + keys.length + '개</span></summary>'
        + g.children.map(c => c.name ? '<details open><summary><input type="checkbox" data-all="' + WS.esc(c.sets.map(s => s.setKey).join('|')) + '"' + (c.sets.every(s => checkedSets.has(s.setKey)) ? ' checked' : '') + ' aria-label="' + WS.esc(c.name) + ' 전체"> ' + WS.esc(c.name) + '</summary>' + c.sets.map(leaf).join('') + '</details>' : c.sets.map(leaf).join('')).join('') + '</details>';
    }).join('') || '<p class="pp-empty">자료가 없습니다.</p>';
    $('tree').querySelectorAll('[data-set]').forEach(i => i.addEventListener('change', () => { i.checked ? checkedSets.add(i.dataset.set) : checkedSets.delete(i.dataset.set); renderTree(); search(true); }));
    $('tree').querySelectorAll('[data-all]').forEach(i => {
      i.addEventListener('click', e => e.stopPropagation());
      i.addEventListener('change', () => { i.dataset.all.split('|').forEach(k => i.checked ? checkedSets.add(k) : checkedSets.delete(k)); renderTree(); search(true); });
    });
  }

  /** 고른 범위·조건으로 후보를 다시 센다. 범위가 바뀌면 유형 단추와 난이도별 수도 새로 그린다. */
  async function search(setsChanged) {
    const sets = Array.from(checkedSets).filter(k => k.startsWith(source + ':'));
    const no = ++queryNo;
    if (!sets.length) { inSets = []; candidates = []; $('types').innerHTML = '<span class="pp-muted">왼쪽에서 범위를 먼저 골라 주세요.</span>'; levelCounts(); counts(); return; }
    try {
      if (setsChanged) {
        const d = await WS.json(api('/bank/questions', sets.map(s => '&sets=' + encodeURIComponent(s)).join('')));
        if (no !== queryNo) return;
        inSets = d.questions;
        const keep = new Set(pressed($('types'), 'type'));
        $('types').innerHTML = Object.keys(d.typeCounts).map(t => '<button type="button" class="pp-chip" data-type="' + WS.esc(t) + '" aria-pressed="' + keep.has(t) + '">' + WS.esc(t) + '<small>' + d.typeCounts[t] + '</small></button>').join('');
        $('types').querySelectorAll('[data-type]').forEach(b => b.addEventListener('click', () => { b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true')); search(false); }));
        levelCounts();
      }
      const types = pressed($('types'), 'type'), levels = pressed($('levels'), 'level');
      candidates = inSets.filter(q => (!types.length || types.includes(q.type)) && (!levels.length || !q.level || levels.includes(q.level)));
      counts();
    } catch (e) { msg(e.message); }
  }

  function levelCounts() { const s = WS.stats(inSets); ['하', '중', '상'].forEach(l => { $('lvCount' + l).textContent = s.levels[l]; }); }

  function pick() {
    msg('');
    const room = 100 - picked.length;
    const want = Math.min(Number($('count').value) || 0, room);
    const added = ($('balanced').checked ? WS.pickBalanced : WS.pick)(candidates, refs(), want);
    if (!added.length) { msg(room <= 0 ? '한 학습지에는 100문제까지 담을 수 있습니다.' : '조건에 맞는 남은 문항이 없습니다. 범위나 유형을 바꿔 보세요.'); return false; }
    picked = picked.concat(added);
    counts();
    return true;
  }

  /* STEP 2 — 미리보기와 문제 목록 */
  function renderPaper() {
    $('paperHead').innerHTML = WS.paperHead($('title').value, $('description').value, picked.length, today());
    $('paperBody').className = 'pp-paper-body' + (columns === 1 ? ' is-one' : '');
    $('paperBody').innerHTML = picked.map((q, i) => WS.questionHtml(q, i + 1)).join('') || '<p class="pp-empty">담은 문항이 없습니다.</p>';
    const s = WS.stats(picked);
    $('statTotal').textContent = s.total; $('finalCount').textContent = s.total;
    $('statTypes').textContent = Object.keys(s.types).map(t => t + ' ' + s.types[t]).join(' · ');
    $('finalLevels').textContent = '(하 ' + s.levels.하 + ' · 중 ' + s.levels.중 + ' · 상 ' + s.levels.상 + (s.levels.없음 ? ' · 구분 없음 ' + s.levels.없음 : '') + ')';
    const max = Math.max(1, s.levels.하, s.levels.중, s.levels.상, s.levels.없음);
    $('statBars').innerHTML = [['구분 없음', s.levels.없음], ['하', s.levels.하], ['중', s.levels.중], ['상', s.levels.상]].map(([n, v]) => '<div class="pp-bar"><span>' + v + '</span><i style="height:' + Math.round(v / max * 46) + 'px"></i><span>' + n + '</span></div>').join('');
    $('picked').innerHTML = picked.map((q, i) => '<li' + (focusNo === i + 1 ? ' class="is-focus"' : '') + '><span class="pp-qno">' + (i + 1) + '</span>'
      + '<span class="pp-qinfo" data-focus="' + (i + 1) + '"><span><b>' + WS.esc(q.type) + '</b>' + (q.level ? ' · ' + q.level : '') + ' · ' + WS.esc(q.preview || '') + '</span><span class="pp-muted">' + WS.esc(q.setTitle) + ' ' + q.no + '번</span></span>'
      + '<span class="pp-qbtns"><button type="button" data-move="-1" data-i="' + i + '"' + (i === 0 ? ' disabled' : '') + ' aria-label="위로">▲</button><button type="button" data-move="1" data-i="' + i + '"' + (i === picked.length - 1 ? ' disabled' : '') + ' aria-label="아래로">▼</button>'
      + '<button type="button" data-swap="' + i + '">바꾸기</button><button type="button" data-drop="' + i + '" aria-label="빼기">✕</button></span></li>').join('');
    $('picked').querySelectorAll('[data-move]').forEach(b => b.addEventListener('click', () => { picked = WS.move(picked, Number(b.dataset.i), Number(b.dataset.move)); renderPaper(); }));
    $('picked').querySelectorAll('[data-drop]').forEach(b => b.addEventListener('click', () => { picked.splice(Number(b.dataset.drop), 1); renderPaper(); renderAdd(); counts(); }));
    $('picked').querySelectorAll('[data-swap]').forEach(b => b.addEventListener('click', () => swap(Number(b.dataset.swap), b)));
    $('picked').querySelectorAll('[data-focus]').forEach(b => b.addEventListener('click', () => { focusNo = Number(b.dataset.focus); renderPaper(); const el = document.getElementById('q' + focusNo); if (el) { el.classList.add('is-focus'); el.scrollIntoView({ block: 'center', behavior: 'smooth' }); } }));
  }

  function renderAdd() {
    const left = WS.remaining(candidates, refs()).slice(0, 60);
    $('addList').innerHTML = left.map((q, i) => '<li><span class="pp-qno">+</span><span class="pp-qinfo"><span><b>' + WS.esc(q.type) + '</b>' + (q.level ? ' · ' + q.level : '') + ' · ' + WS.esc(q.preview || '') + '</span><span class="pp-muted">' + WS.esc(q.setTitle) + ' ' + q.no + '번</span></span>'
      + '<span class="pp-qbtns"><button type="button" data-add="' + WS.esc(q.ref) + '">담기</button></span></li>').join('') || '<li class="pp-muted" style="display:block">더 담을 문항이 없습니다. 「이전」에서 범위를 넓혀 보세요.</li>';
    $('addList').querySelectorAll('[data-add]').forEach(b => b.addEventListener('click', () => {
      if (picked.length >= 100) { msg('한 학습지에는 100문제까지 담을 수 있습니다.'); return; }
      picked.push(candidates.find(q => q.ref === b.dataset.add)); renderPaper(); renderAdd(); counts();
    }));
  }

  /** 바꾸기: 같은 묶음·같은 유형에서 아직 쓰지 않은 문항으로 바꾼다. */
  async function swap(i, b) {
    msg(''); b.disabled = true;
    const cur = picked[i];
    try {
      const d = await WS.json(api('/bank/questions', '&sets=' + encodeURIComponent(cur.setKey) + '&types=' + encodeURIComponent(cur.type)));
      const next = WS.replacement(cur, d.questions, refs());
      if (!next) { msg('같은 범위·같은 유형에 바꿀 문항이 더 없습니다.'); b.disabled = false; return; }
      picked[i] = next; renderPaper(); renderAdd();
    } catch (e) { b.disabled = false; msg(e.message); }
  }

  function go(step) {
    msg('');
    if (step === 2 && !picked.length && !pick()) { if (!$('msg').textContent) msg('왼쪽에서 범위를 고른 뒤 문항을 담아 주세요.'); return; }
    document.querySelectorAll('[data-step]').forEach(s => { s.hidden = s.dataset.step !== String(step); });
    $('stepNo').textContent = step;
    if (step === 2) { renderPaper(); renderAdd(); }
    counts();
    window.scrollTo(0, 0);
  }

  function panel(name) {
    document.querySelectorAll('[data-panel]').forEach(b => b.setAttribute('aria-selected', String(b.dataset.panel === name)));
    document.querySelectorAll('[data-panel-body]').forEach(d => { d.hidden = d.dataset.panelBody !== name; });
  }

  async function save() {
    msg('');
    if (!$('title').value.trim()) { panel('design'); $('title').focus(); msg('디자인 설정에서 학습지 이름을 적어 주세요.'); return; }
    $('saveBtn').disabled = true;
    const data = { title: $('title').value, description: $('description').value, status: $('status').value, refs: refs(), columns };
    try {
      const saved = worksheetId ? await WS.json(api('/' + worksheetId), WS.body('PUT', data)) : await WS.json(api(''), WS.body('POST', data));
      location.href = '/dashboard/worksheets/view?businessId=' + encodeURIComponent(businessId) + '&id=' + saved.worksheetId;
    } catch (e) { $('saveBtn').disabled = false; msg(e.message); }
  }

  async function boot() {
    if (!businessId) { location.replace('/dashboard/worksheets'); return; }
    $('backLink').href = '/dashboard/worksheets?businessId=' + encodeURIComponent(businessId);
    document.querySelectorAll('[data-rail]').forEach(a => { a.href = a.getAttribute('href').split('?')[0] + '?businessId=' + encodeURIComponent(businessId); });
    allSets = await WS.json(api('/bank/sets'));
    const preset = allSets.find(s => s.setKey === WS.param('set'));
    if (preset) { source = preset.source; checkedSets.add(preset.setKey); }
    document.querySelectorAll('[data-source]').forEach(b => {
      b.setAttribute('aria-selected', String(b.dataset.source === source));
      b.addEventListener('click', () => { source = b.dataset.source; document.querySelectorAll('[data-source]').forEach(x => x.setAttribute('aria-selected', String(x === b))); renderTree(); search(true); });
    });
    renderTree();
    await search(true);
    $('levels').querySelectorAll('[data-level]').forEach(b => b.addEventListener('click', () => { b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true')); search(false); }));
    $('pickBtn').addEventListener('click', () => { if (pick()) msg('문항을 담았습니다. 다른 범위에서 더 담거나 「다음 단계」를 눌러 주세요.', true); });
    $('nextBtn').addEventListener('click', () => go(2));
    $('prevBtn').addEventListener('click', () => go(1));
    $('saveBtn').addEventListener('click', save);
    document.querySelectorAll('[data-panel]').forEach(b => b.addEventListener('click', () => panel(b.dataset.panel)));
    document.querySelectorAll('[data-columns]').forEach(b => b.addEventListener('click', () => { columns = Number(b.dataset.columns); document.querySelectorAll('[data-columns]').forEach(x => x.setAttribute('aria-pressed', String(x === b))); renderPaper(); }));
    ['title', 'description'].forEach(id => $(id).addEventListener('input', () => { $('paperHead').innerHTML = WS.paperHead($('title').value, $('description').value, picked.length, today()); }));
    if (worksheetId) {
      const d = await WS.json(api('/' + worksheetId));
      $('pageTitle').textContent = '학습지 수정';
      $('title').value = d.worksheet.title; $('description').value = d.worksheet.description || ''; $('status').value = d.worksheet.status;
      columns = d.worksheet.columns === 1 ? 1 : 2;
      document.querySelectorAll('[data-columns]').forEach(x => x.setAttribute('aria-pressed', String(Number(x.dataset.columns) === columns)));
      picked = d.questions;
      go(2);
      if (d.missingCount) msg('출처에서 없어진 문항 ' + d.missingCount + '개는 빠졌습니다.');
    }
    counts();
  }
  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
