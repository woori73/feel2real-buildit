/* 학습지 만들기·고치기(/dashboard/worksheets/edit[?id=]): 1 범위 고르기 → 2 문항 편집 → 3 저장. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const businessId = WS.param('businessId');
  const worksheetId = WS.param('id');
  const api = (path, query) => '/api/tutor/easy/worksheets' + (path || '') + '?businessId=' + encodeURIComponent(businessId) + (query || '');
  let allSets = [];
  let candidates = [];   // 지금 조건에 맞는 문항
  let picked = [];       // 학습지에 넣은 문항(순서대로)
  let queryNo = 0;

  function msg(t, ok) { const m = $('msg'); m.textContent = t || ''; m.className = 'ws-msg' + (ok ? ' ok' : ''); m.style.display = t ? 'block' : 'none'; }
  const checked = box => Array.from(box.querySelectorAll('input:checked')).map(i => i.value);
  const refs = () => picked.map(q => q.ref);

  function counts() {
    $('remain').textContent = WS.remaining(candidates, refs()).length;
    $('pickedCount').textContent = picked.length;
    $('finalCount').textContent = picked.length;
  }

  function renderGroups() {
    const source = $('source').value;
    const groups = Array.from(new Set(allSets.filter(s => s.source === source).map(s => s.group)));
    $('group').innerHTML = groups.map(g => '<option>' + WS.esc(g) + '</option>').join('');
    $('levelField').hidden = source !== 'NAESIN';
    $('groupLabel').textContent = source === 'NAESIN' ? '학교·학년' : '묶음';
    if (source !== 'NAESIN') $('levels').querySelectorAll('input').forEach(i => { i.checked = false; });
    renderSets();
  }

  function renderSets(preselect) {
    const source = $('source').value, group = $('group').value;
    $('sets').innerHTML = allSets.filter(s => s.source === source && s.group === group).map(s =>
      '<label><input type="checkbox" value="' + WS.esc(s.setKey) + '"' + (preselect === s.setKey ? ' checked' : '') + '> <span>' + WS.esc(s.title) + ' <span class="ws-muted">(' + s.questionCount + '문항)</span></span></label>').join('')
      || '<span class="ws-muted">자료가 없습니다.</span>';
    $('sets').querySelectorAll('input').forEach(i => i.addEventListener('change', () => search(true)));
    search(true);
  }

  /** 고른 조건으로 후보를 다시 센다. 묶음이 바뀌면 유형 단추도 새로 그린다. */
  async function search(setsChanged) {
    const sets = checked($('sets'));
    const no = ++queryNo;
    if (!sets.length) { candidates = []; $('types').innerHTML = '<span class="ws-muted">회차·단원을 먼저 골라 주세요.</span>'; counts(); return; }
    const types = setsChanged ? [] : checked($('types'));
    const q = sets.map(s => '&sets=' + encodeURIComponent(s)).join('') + types.map(t => '&types=' + encodeURIComponent(t)).join('')
      + checked($('levels')).map(l => '&levels=' + encodeURIComponent(l)).join('');
    try {
      const d = await WS.json(api('/bank/questions', q));
      if (no !== queryNo) return;
      candidates = d.questions;
      if (setsChanged) {
        $('types').innerHTML = Object.keys(d.typeCounts).map(t => '<label><input type="checkbox" value="' + WS.esc(t) + '"> ' + WS.esc(t) + ' <span class="ws-muted">' + d.typeCounts[t] + '</span></label>').join('');
        $('types').querySelectorAll('input').forEach(i => i.addEventListener('change', () => search(false)));
      }
      counts();
    } catch (e) { msg(e.message); }
  }

  function pick() {
    msg('');
    const room = 100 - picked.length;
    const added = WS.pick(candidates, refs(), Math.min(Number($('count').value) || 0, room));
    if (!added.length) { msg(room <= 0 ? '한 학습지에는 100문항까지 넣을 수 있습니다.' : '조건에 맞는 남은 문항이 없습니다. 범위나 유형을 바꿔 보세요.'); return; }
    picked = picked.concat(added);
    counts();
    msg(added.length + '문항을 넣었습니다. 다른 범위에서 더 뽑거나 「다음」을 눌러 주세요.', true);
  }

  function renderPicked() {
    $('picked').innerHTML = picked.map((q, i) => '<li><div class="ws-edit-head"><span class="ws-edit-meta"><b>' + (i + 1) + '</b>'
      + WS.esc(q.setTitle) + ' ' + q.no + '번 · ' + WS.esc(q.type) + (q.level ? ' · 난이도 ' + q.level : '') + '</span>'
      + '<span class="ws-actions"><button type="button" class="ws-btn small" data-move="-1" data-i="' + i + '"' + (i === 0 ? ' disabled' : '') + ' aria-label="위로">▲</button>'
      + '<button type="button" class="ws-btn small" data-move="1" data-i="' + i + '"' + (i === picked.length - 1 ? ' disabled' : '') + ' aria-label="아래로">▼</button>'
      + '<button type="button" class="ws-btn small" data-swap="' + i + '">바꾸기</button>'
      + '<button type="button" class="ws-btn small danger" data-drop="' + i + '">빼기</button></span></div>'
      + '<span class="ws-muted">' + WS.esc(q.preview || '') + '</span>'
      + '<details><summary>문항 보기</summary>' + WS.questionHtml(q, i + 1) + '</details></li>').join('')
      || '<li class="ws-muted">고른 문항이 없습니다. 「범위 더 고르기」에서 문항을 뽑아 주세요.</li>';
    $('picked').querySelectorAll('[data-move]').forEach(b => b.addEventListener('click', () => { picked = WS.move(picked, Number(b.dataset.i), Number(b.dataset.move)); renderPicked(); }));
    $('picked').querySelectorAll('[data-drop]').forEach(b => b.addEventListener('click', () => { picked.splice(Number(b.dataset.drop), 1); renderPicked(); counts(); }));
    $('picked').querySelectorAll('[data-swap]').forEach(b => b.addEventListener('click', () => swap(Number(b.dataset.swap), b)));
  }

  /** 바꾸기: 같은 묶음·같은 유형에서 아직 쓰지 않은 문항으로 바꾼다. */
  async function swap(i, b) {
    msg('');
    b.disabled = true;
    const cur = picked[i];
    try {
      const d = await WS.json(api('/bank/questions', '&sets=' + encodeURIComponent(cur.setKey) + '&types=' + encodeURIComponent(cur.type)));
      const next = WS.replacement(cur, d.questions, refs());
      if (!next) { msg('같은 범위·같은 유형에 바꿀 문항이 더 없습니다.'); b.disabled = false; return; }
      picked[i] = next;
      renderPicked();
    } catch (e) { b.disabled = false; msg(e.message); }
  }

  function go(step) {
    msg('');
    if (step > 1 && !picked.length) { msg('문항을 먼저 뽑아 주세요.'); return; }
    document.querySelectorAll('[data-step]').forEach(s => { s.hidden = s.dataset.step !== String(step); });
    document.querySelectorAll('[data-step-label]').forEach(l => { if (l.dataset.stepLabel === String(step)) l.setAttribute('aria-current', 'step'); else l.removeAttribute('aria-current'); });
    if (step === 2) renderPicked();
    counts();
    window.scrollTo(0, 0);
  }

  async function save() {
    msg('');
    $('saveBtn').disabled = true;
    const data = { title: $('title').value, description: $('description').value, status: $('status').value, refs: refs() };
    try {
      const saved = worksheetId ? await WS.json(api('/' + worksheetId), WS.body('PUT', data)) : await WS.json(api(''), WS.body('POST', data));
      location.href = '/dashboard/worksheets/view?businessId=' + encodeURIComponent(businessId) + '&id=' + saved.worksheetId;
    } catch (e) { $('saveBtn').disabled = false; msg(e.message); }
  }

  async function boot() {
    if (!businessId) { location.replace('/dashboard/worksheets'); return; }
    $('backLink').href = '/dashboard/worksheets?businessId=' + encodeURIComponent(businessId);
    allSets = await WS.json(api('/bank/sets'));
    const preset = allSets.find(s => s.setKey === WS.param('set'));
    if (preset) $('source').value = preset.source;
    renderGroups();
    if (preset) { $('group').value = preset.group; renderSets(preset.setKey); }
    if (worksheetId) {
      const d = await WS.json(api('/' + worksheetId));
      $('pageTitle').textContent = '학습지 고치기';
      $('title').value = d.worksheet.title;
      $('description').value = d.worksheet.description || '';
      $('status').value = d.worksheet.status;
      picked = d.questions;
      if (d.missingCount) msg('출처에서 없어진 문항 ' + d.missingCount + '개는 빠졌습니다.');
      go(2);
    }
    $('source').addEventListener('change', renderGroups);
    $('group').addEventListener('change', () => renderSets());
    $('levels').querySelectorAll('input').forEach(i => i.addEventListener('change', () => search(false)));
    $('pickBtn').addEventListener('click', pick);
    $('saveBtn').addEventListener('click', save);
    document.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => go(Number(b.dataset.go))));
    counts();
  }

  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
