/* 학습지 목록(/dashboard/worksheets): 우리 학원 / 내 학습지 / 즐겨찾기 / 휴지통을 표로 보여 준다. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const EMPTY = { ACADEMY: '학습지가 없습니다. 「학습지 만들기」로 시작해 보세요.', MINE: '내가 만든 학습지가 없습니다.', FAVORITE: '즐겨찾기한 학습지가 없습니다.', TRASH: '휴지통이 비어 있습니다.' };
  let tab = 'ACADEMY', kind = '', rows = [];
  const biz = () => encodeURIComponent($('business').value);
  const api = (path, query) => '/api/tutor/easy/worksheets' + (path || '') + '?businessId=' + biz() + (query || '');
  function msg(t, ok) { const m = $('msg'); m.textContent = t || ''; m.className = 'pp-msg' + (ok ? ' ok' : ''); m.style.display = t ? 'block' : 'none'; }

  function row(w) {
    const view = '/dashboard/worksheets/view?businessId=' + biz() + '&id=' + w.worksheetId;
    const edit = '/dashboard/worksheets/edit?businessId=' + biz() + '&id=' + w.worksheetId;
    const trash = tab === 'TRASH';
    const more = trash ? '' : '<details class="pp-more"><summary aria-label="더보기">⋯</summary><div class="pp-more-menu">'
      + '<button type="button" data-act="favorite" data-on="' + (w.favorite ? '0' : '1') + '" data-id="' + w.worksheetId + '">' + (w.favorite ? '★ 즐겨찾기 빼기' : '☆ 즐겨찾기') + '</button>'
      + (w.canEdit ? '<a href="' + edit + '">수정</a><button type="button" class="danger" data-act="trash" data-id="' + w.worksheetId + '">휴지통으로</button>' : '') + '</div></details>';
    const actions = trash
      ? '<button type="button" class="pp-btn small" data-act="restore" data-id="' + w.worksheetId + '">복구</button><button type="button" class="pp-btn small danger" data-act="purge" data-id="' + w.worksheetId + '">영구 삭제</button>'
      : '<a class="pp-btn small" href="' + view + '">미리보기</a><a class="pp-btn small dark" href="' + view + '#assign">출제하기</a>' + more;
    return '<tr><td class="pp-inline">' + WS.esc(w.kind || '-') + '</td><td class="pp-inline">' + WS.esc(w.grade || '-') + '</td>'
      + '<td>' + (trash ? '<span class="pp-title">' + WS.esc(w.title) + '</span>' : '<a class="pp-title" href="' + view + '">' + WS.esc(w.title) + '</a>')
      + ' <span class="pp-badge ' + w.status + '">' + WS.STATUS[w.status] + '</span>' + (w.favorite ? ' <span aria-label="즐겨찾기">★</span>' : '')
      + '<span class="pp-sub">' + w.questionCount + '문제' + (w.scope ? ' | ' + WS.esc(w.scope) : '') + '</span></td>'
      + '<td class="pp-inline" data-label="' + (trash ? '지운 날' : '만든 날') + '">' + WS.day(trash ? w.deletedAt : w.createdAt) + '</td>'
      + '<td class="pp-inline" data-label="만든 사람">' + WS.esc(w.ownerName || '-') + '</td>'
      + '<td data-label="">' + WS.esc(WS.assignSummary(w.assigned)) + '</td>'
      + '<td><div class="pp-cell-actions">' + actions + '</div></td></tr>';
  }

  function render() {
    const word = $('search').value.trim().toLowerCase();
    const shown = rows.filter(w => (!kind || w.kind === kind) && (!word || (w.title + ' ' + (w.ownerName || '') + ' ' + (w.scope || '')).toLowerCase().includes(word)));
    $('list').innerHTML = shown.map(row).join('');
    $('empty').hidden = shown.length > 0;
    $('empty').textContent = rows.length ? '조건에 맞는 학습지가 없습니다.' : EMPTY[tab];
    $('list').querySelectorAll('[data-act]').forEach(b => b.addEventListener('click', () => act(b)));
  }

  async function act(b) {
    const id = b.dataset.id, a = b.dataset.act;
    if (a === 'purge' && !confirm('영구 삭제하면 되돌릴 수 없습니다. 지울까요?')) return;
    if (a === 'trash' && !confirm('휴지통으로 보낼까요? 휴지통에서 복구할 수 있습니다.')) return;
    b.disabled = true;
    try {
      if (a === 'favorite') await WS.json(api('/' + id + '/favorite', '&on=' + (b.dataset.on === '1')), { method: 'PUT' });
      if (a === 'trash') await WS.json(api('/' + id), { method: 'DELETE' });
      if (a === 'restore') await WS.json(api('/' + id + '/restore'), { method: 'POST' });
      if (a === 'purge') await WS.json(api('/' + id + '/purge'), { method: 'DELETE' });
      await load();
      if (a === 'trash') msg('휴지통으로 보냈습니다.', true);
      if (a === 'restore') msg('복구했습니다.', true);
    } catch (e) { b.disabled = false; msg(e.message); }
  }

  async function load() {
    msg('');
    $('newLink').href = '/dashboard/worksheets/edit?businessId=' + biz();
    document.querySelectorAll('[data-rail]').forEach(a => { a.href = a.getAttribute('href').split('?')[0] + '?businessId=' + biz(); });
    try { rows = await WS.json(api('', '&tab=' + tab)); render(); }
    catch (e) { rows = []; render(); msg(e.message); }
  }

  async function boot() {
    const list = await WS.json('/api/tutor/easy/worksheets/businesses');
    if (!list.length) { msg('연결된 사업체가 없습니다.'); return; }
    list.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    const wanted = WS.param('businessId');
    if (wanted && list.some(b => String(b.businessId) === wanted)) $('business').value = wanted;
    $('business').addEventListener('change', load);
    $('search').addEventListener('input', render);
    document.querySelectorAll('[data-tab]').forEach(b => b.addEventListener('click', () => {
      tab = b.dataset.tab;
      document.querySelectorAll('[data-tab]').forEach(x => x.setAttribute('aria-selected', String(x === b)));
      load();
    }));
    document.querySelectorAll('[data-kind]').forEach(b => b.addEventListener('click', () => {
      kind = b.dataset.kind;
      document.querySelectorAll('[data-kind]').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
      render();
    }));
    load();
  }
  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
