/* 학습지 목록(/dashboard/worksheets): 우리 학원 / 내 학습지 / 즐겨찾기 / F2R 기본 자료 / 휴지통. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const HELP = {
    ACADEMY: '우리 학원 선생님들이 만든 학습지입니다. 숨김으로 둔 학습지는 만든 사람과 관리자에게만 보입니다.',
    MINE: '내가 만든 학습지입니다.',
    FAVORITE: '별표를 눌러 둔 학습지입니다.',
    BANK: 'F2R에 이미 있는 자료입니다. 「이 범위로 만들기」를 누르면 그 범위가 골라진 채로 학습지 만들기가 열립니다.',
    TRASH: '지운 학습지입니다. 복구하거나 영구 삭제할 수 있습니다. 배정된 학생이 있으면 영구 삭제되지 않습니다.'
  };
  let tab = 'ACADEMY';
  const biz = () => encodeURIComponent($('business').value);
  const api = (path, query) => '/api/tutor/easy/worksheets' + (path || '') + '?businessId=' + biz() + (query || '');

  function msg(t, ok) { const m = $('msg'); m.textContent = t || ''; m.className = 'ws-msg' + (ok ? ' ok' : ''); m.style.display = t ? 'block' : 'none'; }

  function row(w) {
    const view = '/dashboard/worksheets/view?businessId=' + biz() + '&id=' + w.worksheetId;
    const edit = '/dashboard/worksheets/edit?businessId=' + biz() + '&id=' + w.worksheetId;
    const actions = tab === 'TRASH'
      ? '<button type="button" class="ws-btn small" data-act="restore" data-id="' + w.worksheetId + '">복구</button>'
        + '<button type="button" class="ws-btn small danger" data-act="purge" data-id="' + w.worksheetId + '">영구 삭제</button>'
      : '<a class="ws-btn small" href="' + view + '">보기·인쇄</a>'
        + '<a class="ws-btn small" href="' + view + '#assign">배정</a>'
        + '<button type="button" class="ws-btn small" data-act="favorite" data-on="' + (w.favorite ? '0' : '1') + '" data-id="' + w.worksheetId + '" aria-pressed="' + w.favorite + '"><span class="ws-star">' + (w.favorite ? '★' : '☆') + '</span>&nbsp;즐겨찾기</button>'
        + (w.canEdit ? '<a class="ws-btn small" href="' + edit + '">고치기</a><button type="button" class="ws-btn small danger" data-act="trash" data-id="' + w.worksheetId + '">휴지통으로</button>' : '');
    return '<li><div class="ws-row-title">' + (tab === 'TRASH' ? WS.esc(w.title) : '<a href="' + view + '">' + WS.esc(w.title) + '</a>')
      + '<span class="ws-badge ' + w.status + '">' + WS.STATUS[w.status] + '</span></div>'
      + (w.description ? '<span class="ws-muted">' + WS.esc(w.description) + '</span>' : '')
      + '<span class="ws-muted">' + w.questionCount + '문항 · 만든 사람 ' + WS.esc(w.ownerName || '-') + (w.mine ? '(나)' : '')
      + ' · ' + (tab === 'TRASH' ? WS.day(w.deletedAt) + ' 지움' : WS.day(w.updatedAt) + ' 고침') + ' · ' + WS.assignSummary(w.assigned) + '</span>'
      + '<div class="ws-actions">' + actions + '</div></li>';
  }

  function bankRows(sets) {
    let html = '', group = null;
    sets.forEach(s => {
      const g = WS.SOURCE[s.source] + ' · ' + s.group;
      if (g !== group) { group = g; html += '<li class="ws-group-title">' + WS.esc(g) + '</li>'; }
      html += '<li><div class="ws-row-title">' + WS.esc(s.title) + '</div><span class="ws-muted">' + s.questionCount + '문항</span>'
        + '<div class="ws-actions"><a class="ws-btn small" href="/dashboard/worksheets/edit?businessId=' + biz() + '&set=' + encodeURIComponent(s.setKey) + '">이 범위로 만들기</a></div></li>';
    });
    return html;
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
    $('tabHelp').textContent = HELP[tab];
    $('newLink').href = '/dashboard/worksheets/edit?businessId=' + biz();
    try {
      if (tab === 'BANK') {
        $('list').innerHTML = bankRows(await WS.json(api('/bank/sets'))) || '<li class="ws-muted">자료가 없습니다.</li>';
        return;
      }
      const rows = await WS.json(api('', '&tab=' + tab));
      $('list').innerHTML = rows.map(row).join('') || '<li class="ws-muted">' + (tab === 'TRASH' ? '휴지통이 비어 있습니다.' : '학습지가 없습니다. 「새 학습지 만들기」로 시작해 보세요.') + '</li>';
      $('list').querySelectorAll('[data-act]').forEach(b => b.addEventListener('click', () => act(b)));
    } catch (e) { $('list').innerHTML = ''; msg(e.message); }
  }

  async function boot() {
    const list = await WS.json('/api/tutor/easy/worksheets/businesses');
    if (!list.length) { msg('연결된 사업체가 없습니다.'); return; }
    list.forEach(b => $('business').add(new Option(b.name, String(b.businessId))));
    const wanted = WS.param('businessId');
    if (wanted && list.some(b => String(b.businessId) === wanted)) $('business').value = wanted;
    $('business').addEventListener('change', load);
    document.querySelectorAll('[data-tab]').forEach(b => b.addEventListener('click', () => {
      tab = b.dataset.tab;
      document.querySelectorAll('[data-tab]').forEach(x => x.setAttribute('aria-selected', String(x === b)));
      load();
    }));
    load();
  }

  document.addEventListener('DOMContentLoaded', () => boot().catch(e => msg(e.message)));
})();
