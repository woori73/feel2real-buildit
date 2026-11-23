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
    $('groups').innerHTML = '<span class="pp-side-head">' + (source === 'NAESIN' ? '학교 · 학년' : '단원') + '</span>' + tree.map(g =>
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
    $('list').innerHTML = rows.map(([u, s]) => '<tr><td class="pp-inline">' + (source === 'NAESIN' ? '실전 예상' : '어법') + '</td><td class="pp-inline">' + WS.esc(u || '-') + '</td>'
      + '<td><span class="pp-title">' + WS.esc(s.leaf) + '</span><span class="pp-sub">' + WS.esc(s.title) + '</span></td><td class="pp-inline" data-label="문항">' + s.questionCount + '</td>'
      + '<td><div class="pp-cell-actions"><button type="button" class="pp-btn small" data-preview="' + WS.esc(s.setKey) + '">미리보기</button>'
      + '<a class="pp-btn small dark" href="/dashboard/worksheets/edit?businessId=' + biz() + '&set=' + encodeURIComponent(s.setKey) + '">이 자료로 학습지 만들기</a></div></td></tr>').join('');
    $('empty').hidden = rows.length > 0;
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
      render();
    }));
    load();
  }
  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
