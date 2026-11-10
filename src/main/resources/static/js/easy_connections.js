/**
 * F2R 장부 — 자동수집 연결(/easy/connections).
 * 권한은 서버(/api/admin/** + 사업체 접근 검증)가 최종 결정한다. 이 화면은 비밀번호·인증서·OTP를 받지 않는다.
 * 표시 이름·상태 변환은 easy_connections_view.js(EasyConnView)에 있다.
 */
(function () {
  const BASE = '/api/admin/easy/collections';
  const V = window.EasyConnView;
  const RUN_STATUS = { RUNNING: '진행 중', SUCCESS: '완료', PARTIAL: '일부 실패', FAILED: '실패' };
  const BADGE = { '연결됨': 'ok', '다시 인증 필요': 'warn', '파일 수집': 'file', '직접 로그인 수집': 'mcp' };
  const MCP_HINT = { HOMETAX: '홈택스', CARD: '카드 사용내역', BANK: '통장 거래내역', CREFIA: '카드매출' };
  let connections = [];
  let catalog = { sites: [], steps: {}, safetyRules: [] };
  let siteNames = {};
  let currentCat = null;
  let filePreview = null;

  function businessId() { return document.getElementById('business').value; }
  function $(id) { return document.getElementById(id); }

  function el(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  }

  function fmtTime(v) { return v ? String(v).replace('T', ' ').slice(0, 16) : '-'; }

  function message(id, text, isError) {
    const node = $(id);
    node.textContent = text;
    node.className = 'msg' + (isError ? ' err' : '');
    node.style.display = text ? 'block' : 'none';
  }

  async function call(method, path, body) {
    const opts = { method, headers: {} };
    if (body instanceof FormData) {
      opts.body = body;
    } else if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const sep = path.includes('?') ? '&' : '?';
    const res = await fetchAuth(BASE + path + sep + 'businessId=' + encodeURIComponent(businessId()), opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }
    if (!res.ok) {
      const msg = (data && (data.message || data.error)) || text || ('요청 실패 (' + res.status + ')');
      throw new Error(msg);
    }
    return data;
  }

  function button(label, onClick, cls) {
    const b = el('button', cls || 'btn-secondary', label);
    b.type = 'button';
    b.addEventListener('click', async () => {
      b.disabled = true;
      try { await onClick(); } finally { b.disabled = false; }
    });
    return b;
  }

  function linkButton(text, url, sub) {
    const a = el('a', 'link-btn' + (sub ? ' sub' : ''), text);
    a.href = url; a.target = '_blank'; a.rel = 'noopener noreferrer';
    return a;
  }

  // ------------------------------------------------------------ ① 한 줄 상태 · ③ 연결된 기관

  async function loadConnections() {
    message('listMsg', '');
    try {
      connections = await call('GET', '/connections');
      render();
    } catch (e) {
      message('listMsg', e.message, true);
    }
  }

  function render() {
    $('statusLine').textContent = V.summary(connections, siteNames);
    const rows = V.rows(connections, siteNames);
    $('connCount').textContent = rows.length + '곳';
    const body = $('connTable').querySelector('tbody');
    body.innerHTML = '';
    if (!rows.length) {
      const tr = el('tr'); const td = el('td', null, '아직 연결된 기관이 없습니다. 위에서 기관을 고르거나 아래에서 파일을 넣어 주세요.');
      td.colSpan = 6; tr.appendChild(td); body.appendChild(tr);
    }
    rows.forEach(r => body.appendChild(rowView(r)));
    renderAdvanced();
  }

  function cell(tr, header, content, cls) {
    const td = el('td', cls || null);
    td.setAttribute('data-h', header);
    if (content instanceof Node) td.appendChild(content); else td.textContent = content;
    tr.appendChild(td);
    return td;
  }

  function rowView(r) {
    const tr = el('tr');
    cell(tr, '기관', r.name);
    cell(tr, '수집 방식', r.method);
    cell(tr, '상태', el('span', 'badge ' + (BADGE[r.status] || 'file'), r.status));
    cell(tr, '마지막 수집', fmtTime(r.last));
    cell(tr, '장부에 들어간 건수', r.saved.toLocaleString('ko-KR') + '건', 'num');
    const actions = el('td');
    actions.appendChild(button('수집 이력', () => history(r)));
    if (r.kind === 'AUTO') actions.appendChild(moreMenu(r.connections[0]));
    tr.appendChild(actions);
    return tr;
  }

  /** 자동 연동 줄의 드문 조작은 「더보기」 안에 둔다. */
  function moreMenu(c) {
    const more = el('details', 'more');
    more.appendChild(el('summary', null, '더보기'));
    const disconnected = c.connectionStatus === 'DISCONNECTED';
    if (!disconnected) {
      const run = button('지금 수집', () => runNow(c));
      run.disabled = c.readiness !== 'READY';
      if (run.disabled) run.title = '연결됨 상태에서만 실행됩니다.';
      more.appendChild(run);
      more.appendChild(button(c.enabled ? '자동수집 끄기' : '자동수집 켜기', () => toggle(c)));
      more.appendChild(button('연결 해제', () => disconnect(c)));
    }
    more.appendChild(button(disconnected || c.authStatus === 'NEEDS_REAUTH' ? '재인증 등록' : '인증 참조 변경', () => reauth(c)));
    if (c.sourceCode === 'CODEF_BANK') more.appendChild(button('설정 수정', () => edit(c)));
    return more;
  }

  // ------------------------------------------------------------ ② 기관 연결하기

  function sitesOf(cat) { return catalog.sites.filter(s => s.category === cat); }

  function openCategory(cat) {
    currentCat = cat;
    document.querySelectorAll('#orgButtons .org-btn').forEach(b => b.classList.toggle('active', b.dataset.cat === cat));
    $('connectPanel').style.display = '';
    const list = sitesOf(cat);
    const pick = $('sitePick');
    pick.innerHTML = '';
    list.forEach(s => pick.add(new Option(s.name, s.code)));
    $('sitePickWrap').style.display = list.length > 1 ? '' : 'none';
    $('siteSteps').innerHTML = '';
    (catalog.steps[cat] || []).forEach(step => $('siteSteps').appendChild(el('li', null, V.plainStep(step))));
    $('siteRules').innerHTML = '';
    (catalog.safetyRules || []).forEach(rule => $('siteRules').appendChild(el('li', null, V.plainStep(rule))));
    $('bankFormWrap').style.display = cat === 'BANK' ? '' : 'none';
    showSite();
  }

  function showSite() {
    const site = catalog.sites.find(s => s.code === $('sitePick').value) || sitesOf(currentCat)[0];
    const links = $('siteLinks');
    links.innerHTML = '';
    if (!site) return;
    links.appendChild(linkButton('공식 사이트 열기', site.url));
    if (site.businessUrl) links.appendChild(linkButton('기업·사업자용 열기', site.businessUrl, true));
    $('siteNote').textContent = site.note ? '※ ' + site.note : '';
    $('siteGuide').textContent = '로그인은 직접 해 주세요. 로그인 후 Claude에게 "F2R 장부 ' + site.name + ' '
      + (MCP_HINT[currentCat] || '거래') + ' 수집해 줘"라고 하면 거래를 읽어 장부에 저장합니다.';
  }

  async function loadSites() {
    try {
      catalog = await call('GET', '/sites');
      siteNames = {};
      catalog.sites.forEach(s => { siteNames[s.code] = s.name; });
      const fileSite = $('fileSite');
      fileSite.innerHTML = '';
      catalog.sites.forEach(s => fileSite.add(new Option(s.name, s.code)));
      fileSite.add(new Option('기타 (직접 정리한 파일)', 'FILE'));
      showFileSite();
    } catch (e) {
      message('listMsg', e.message, true);
    }
  }

  // ------------------------------------------------------------ 수집 이력

  async function history(r) {
    const card = $('historyCard');
    card.style.display = '';
    $('historyTitle').textContent = '수집 이력 — ' + r.name;
    const table = $('runs');
    table.innerHTML = '';
    $('recommend').innerHTML = '';
    let runs = [];
    try {
      for (const c of r.connections) runs = runs.concat(await call('GET', '/connections/' + c.connectionId + '/runs'));
    } catch (e) { table.appendChild(el('caption', null, e.message)); return; }
    runs.sort((a, b) => String(b.startedAt).localeCompare(String(a.startedAt)));
    const head = el('tr');
    ['수집 시각', '상태', '받은 건수', '장부 저장', '중복 제외', '검토 필요', '자동 분류', '실패', ''].forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    if (!runs.length) {
      const tr = el('tr'); const td = el('td', null, '수집 이력이 없습니다.'); td.colSpan = 9; tr.appendChild(td); table.appendChild(tr);
    }
    runs.forEach(run => {
      const tr = el('tr');
      [fmtTime(run.startedAt), RUN_STATUS[run.status] || run.status, run.fetchedCount, run.ledgerSavedCount,
        run.duplicateCount, run.ledgerReviewCount, run.ledgerClassifiedCount, run.errorCount]
        .forEach(v => tr.appendChild(el('td', null, String(v))));
      const td = el('td');
      if (run.ledgerReviewCount > 0) td.appendChild(button('AI 추천', () => recommend(run)));
      tr.appendChild(td);
      table.appendChild(tr);
      if (run.errorSummary) {
        const er = el('tr'); const ed = el('td', null, '오류: ' + run.errorSummary); ed.colSpan = 9; er.appendChild(ed); table.appendChild(er);
      }
    });
    card.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async function recommend(run) {
    const box = $('recommend');
    box.innerHTML = '';
    try {
      const result = await call('GET', '/runs/' + run.runId + '/recommendations');
      box.appendChild(el('p', 'desc', result.message));
      const ul = el('ul', 'item-list');
      result.items.forEach(item => {
        ul.appendChild(el('li', 'item', item.direction + ' ' + Easy.fmtWon(item.amount) + ' · '
          + (item.counterparty || '-') + ' · ' + (item.description || '-') + ' → '
          + (item.accountName ? item.accountName + (item.reason ? ' (' + item.reason + ')' : '') : '추천 없음')));
      });
      box.appendChild(ul);
      const link = el('a', null, '검토함에서 반영하기 ›'); link.href = '/easy/reviews';
      box.appendChild(link);
    } catch (e) { box.appendChild(el('p', 'desc', e.message)); }
  }

  // ------------------------------------------------------------ 자동 연동(은행) 조작 — 기존 기능 유지

  function edit(c) {
    openCategory('BANK');
    $('bankForm').open = true;
    $('editId').value = c.connectionId;
    $('label').value = c.label || '';
    $('organization').value = c.organization || '0004';
    $('clientType').value = c.clientType || 'B';
    $('account').value = '';
    $('account').placeholder = '변경할 때만 다시 입력 (현재 ' + (c.accountMasked || '-') + ')';
    $('connectedId').value = '';
    $('cancelEdit').style.display = '';
    $('bankForm').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function resetForm() {
    ['editId', 'label', 'account', 'connectedId'].forEach(id => { $(id).value = ''; });
    $('account').placeholder = '';
    $('cancelEdit').style.display = 'none';
  }

  async function save() {
    message('formMsg', '');
    const id = $('editId').value;
    const body = {
      label: $('label').value.trim(),
      organization: $('organization').value,
      clientType: $('clientType').value,
      account: $('account').value.replace(/[^0-9]/g, ''),
      connectedId: $('connectedId').value.trim() || null
    };
    if (!body.account) {
      message('formMsg', '계좌번호를 입력하세요(수정할 때도 확인을 위해 다시 입력합니다).', true);
      return;
    }
    try {
      if (id) await call('PUT', '/connections/' + id + '/bank', body);
      else await call('POST', '/connections/bank', body);
      message('formMsg', '저장했습니다. 계좌번호는 화면에 뒤 4자리만 표시됩니다.', false);
      resetForm();
      await loadConnections();
    } catch (e) {
      message('formMsg', e.message, true);
    }
  }

  async function reauth(c) {
    const ref = prompt('기관 연동 계정 등록으로 받은 연결 ID를 입력하세요.\n비밀번호·인증서·OTP는 입력하지 마세요.');
    if (!ref) return;
    try {
      await call('PUT', '/connections/' + c.connectionId + '/auth', { connectionRef: ref.trim() });
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function runNow(c) {
    message('listMsg', '수집 중입니다…', false);
    try {
      const run = await call('POST', '/connections/' + c.connectionId + '/run');
      message('listMsg', '받은 ' + run.fetchedCount + '건 · 장부 저장 ' + run.ledgerSavedCount + '건 · 중복 '
        + run.duplicateCount + '건 · 검토 ' + run.ledgerReviewCount + '건 · 실패 ' + run.errorCount + '건', run.status === 'FAILED');
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function toggle(c) {
    try {
      await call('PUT', '/connections/' + c.connectionId + '/enabled', { enabled: !c.enabled });
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  async function disconnect(c) {
    if (!confirm('연결을 해제할까요?\n조회가 멈춥니다. 이미 저장된 장부·증빙은 그대로 남습니다.')) return;
    try {
      await call('POST', '/connections/' + c.connectionId + '/disconnect');
      await loadConnections();
    } catch (e) { message('listMsg', e.message, true); }
  }

  // ------------------------------------------------------------ ④ 파일로 넣기

  function showFileSite() {
    const box = $('fileSiteLink');
    box.innerHTML = '';
    const site = catalog.sites.find(s => s.code === $('fileSite').value);
    if (site) box.appendChild(linkButton('파일 받는 곳: ' + site.name, site.businessUrl || site.url, true));
  }

  function decode(buffer) {
    const utf8 = new TextDecoder('utf-8').decode(buffer);
    if (!utf8.includes('�')) return utf8;
    try { return new TextDecoder('euc-kr').decode(buffer); } catch (_) { return utf8; }
  }

  async function previewFile() {
    const file = $('fileInput').files[0];
    const box = $('filePreview');
    box.innerHTML = '';
    filePreview = null;
    $('fileSave').disabled = true;
    message('fileMsg', '');
    if (!file) return;
    const p = V.previewCsv(decode(await file.arrayBuffer()));
    if (!p.ok) { box.appendChild(el('p', 'desc', p.error)); return; }
    filePreview = p;
    box.appendChild(el('p', 'desc', '거래 ' + p.rows + '줄을 찾았습니다. 앞 5줄을 확인하세요.'));
    const table = el('table', 'run-table');
    const head = el('tr');
    p.header.forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    p.sample.forEach(r => { const tr = el('tr'); r.forEach(v => tr.appendChild(el('td', null, v))); table.appendChild(tr); });
    box.appendChild(table);
    $('fileSave').disabled = false;
  }

  async function saveFile() {
    const file = $('fileInput').files[0];
    if (!file || !filePreview) return;
    const today = new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10);
    const form = new FormData();
    form.append('file', file);
    form.append('label', V.fileLabel($('fileSite').value, filePreview.firstDate, today));
    message('fileMsg', '장부에 저장하는 중입니다…', false);
    try {
      const result = await call('POST', '/ingest/csv', form);
      const run = result.run;
      message('fileMsg', '장부 저장 ' + run.ledgerSavedCount + '건 · 중복 제외 ' + run.duplicateCount + '건 · 검토 필요 '
        + run.ledgerReviewCount + '건 · 실패 ' + run.errorCount + '건', run.status === 'FAILED');
      $('fileInput').value = '';
      $('fileSave').disabled = true;
      filePreview = null;
      await loadConnections();
    } catch (e) { message('fileMsg', e.message, true); }
  }

  // ------------------------------------------------------------ ⑤ 고급 설정

  function renderAdvanced() {
    const table = $('advTable');
    table.innerHTML = '';
    const head = el('tr');
    ['저장 이름', '수집원', '준비 상태', '인증', '기관코드·계좌', '수집 위치 기록', '마지막 오류', ''].forEach(h => head.appendChild(el('th', null, h)));
    table.appendChild(head);
    connections.forEach(c => {
      const tr = el('tr');
      [c.label, c.sourceCode, c.readiness, c.authStatus,
        [c.organization, c.clientType, c.accountMasked].filter(Boolean).join(' · ') || '-',
        c.checkpointExists ? '있음' : '없음', c.lastErrorCode || '-']
        .forEach(v => tr.appendChild(el('td', null, String(v))));
      const td = el('td');
      if (V.method(c) === 'AUTO') td.appendChild(button('인증 참조 변경', () => reauth(c)));
      tr.appendChild(td);
      table.appendChild(tr);
    });
  }

  async function init() {
    await Easy.guardAdmin();
    const businesses = await Easy.loadBusinesses();
    const select = $('business');
    businesses.forEach(b => select.add(new Option(b.name, String(b.businessId))));
    select.addEventListener('change', () => { $('historyCard').style.display = 'none'; loadConnections(); });
    document.querySelectorAll('#orgButtons .org-btn').forEach(b => b.addEventListener('click', () => openCategory(b.dataset.cat)));
    $('sitePick').addEventListener('change', showSite);
    $('toFile').addEventListener('click', () => {
      const site = $('sitePick').value || (sitesOf(currentCat)[0] || {}).code;
      if (site) { $('fileSite').value = site; showFileSite(); }
    });
    $('saveBtn').addEventListener('click', save);
    $('cancelEdit').addEventListener('click', resetForm);
    $('fileSite').addEventListener('change', showFileSite);
    $('fileInput').addEventListener('change', previewFile);
    $('fileSave').addEventListener('click', saveFile);
    if (businesses.length) {
      await loadSites();
      await loadConnections();
    }
  }

  init().catch(e => message('listMsg', e.message, true));
})();
