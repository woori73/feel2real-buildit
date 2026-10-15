/**
 * /easy 대시보드 — 사업체 전환 + 월 손익 5카드 + 최근 거래 + 미수 학생.
 */
(function () {
  let businesses = [];
  let businessId = null;
  let month = currentMonth();

  function currentMonth() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
  }

  function shiftMonth(delta) {
    const parts = month.split('-');
    const d = new Date(Number(parts[0]), Number(parts[1]) - 1 + delta, 1);
    month = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
    load();
  }

  async function init() {
    await Easy.guardAdmin();
    businesses = await Easy.loadBusinesses();
    if (!businesses.length) throw new Error('활성 사업체가 없습니다.');
    businessId = businesses[0].businessId;
    renderSwitch();
    document.getElementById('prevMonth').addEventListener('click', () => shiftMonth(-1));
    document.getElementById('nextMonth').addEventListener('click', () => shiftMonth(1));
    await load();
  }

  function renderSwitch() {
    const wrap = document.getElementById('bizSwitch');
    wrap.innerHTML = '';
    const tabs = businesses.map(b => ({ businessId: b.businessId, name: b.name }));
    tabs.forEach(t => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = t.name;
      if (t.businessId === businessId) btn.classList.add('active');
      btn.addEventListener('click', () => {
        businessId = t.businessId;
        renderSwitch();
        load();
        notifyScope();
      });
      wrap.appendChild(btn);
    });
    notifyScope();
  }

  /** 경영·검토 영역(easy_insight.js)과 사업체 선택을 공유한다. */
  function notifyScope() {
    window.__easyBusinessId = businessId;
    document.dispatchEvent(new CustomEvent('easy-scope', { detail: { businessId } }));
  }

  async function load() {
    document.getElementById('monthLabel').textContent = Easy.fmtMonth(month);
    const url = '/api/admin/easy/workspace/monthly?month=' + encodeURIComponent(month)
      + '&businessId=' + encodeURIComponent(businessId);
    const res = await fetchAuth(url);
    if (!res.ok) throw new Error('요약을 불러오지 못했습니다.');
    const s = await res.json();
    const scopeName = businessId
      ? (businesses.find(b => b.businessId === businessId) || {}).name || ''
      : '전체';
    document.getElementById('scopeLabel').textContent =
      '범위: ' + scopeName + ' · ' + Easy.fmtMonth(month);
    renderStats(s);
    renderWorkspaceStatus(s);
    renderRecent((s.recentTransactions || []).map(item => item.transaction));
    renderOverdue(s.overdueStudents || []);
  }

  function renderWorkspaceStatus(s) {
    const grid = document.getElementById('workspaceStatusGrid');
    const items = [
      ['COLLECTED', s.collectedCount],
      ['IMPORT STAGED', s.importStagedCount],
      ['LEDGER SAVED', s.ledgerSavedCount],
      ['JOURNAL POSTED', s.journalPostedCount],
      ['OPEN REVIEW', s.openReviewCount],
      ['EVIDENCE REVIEW', s.evidenceReviewCount]
    ];
    grid.innerHTML = '';
    items.forEach(item => {
      const div = document.createElement('div');
      div.className = 'stat-card';
      const label = document.createElement('div');
      label.className = 'l';
      label.textContent = item[0];
      const count = document.createElement('div');
      count.className = 'n';
      count.textContent = Number(item[1] || 0).toLocaleString('ko-KR') + '건';
      div.appendChild(label);
      div.appendChild(count);
      grid.appendChild(div);
    });
    document.getElementById('closingBadge').textContent =
      '회계기간 ' + (s.closingStatus || 'OPEN');
    const sources = s.sources || {};
    document.getElementById('workspaceSources').textContent =
      '금액: ' + (sources.amounts || '-') + ' · 상태: 기존 수집/가져오기/분개/검토/마감 테이블';
  }

  function renderStats(s) {
    const grid = document.getElementById('statGrid');
    const profitCls = s.profit < 0 ? 'minus' : 'plus';
    grid.innerHTML = '';
    const cards = [
      { l: '이번 달 수입', n: Easy.fmtWon(s.income), c: 'plus' },
      { l: '이번 달 지출', n: Easy.fmtWon(s.expense), c: 'minus' },
      { l: '이번 달 손익', n: Easy.fmtWon(s.profit), c: profitCls },
      { l: '미수금', n: Easy.fmtWon(s.receivable), c: s.receivable > 0 ? 'warn' : '' },
      { l: '학생 수', n: (s.studentCount || 0) + '명', c: '' }
    ];
    cards.forEach(c => {
      const div = document.createElement('div');
      div.className = 'stat-card';
      const l = document.createElement('div');
      l.className = 'l';
      l.textContent = c.l;
      const n = document.createElement('div');
      n.className = 'n ' + c.c;
      n.textContent = c.n;
      div.appendChild(l);
      div.appendChild(n);
      grid.appendChild(div);
    });
  }

  function renderRecent(txns) {
    const ul = document.getElementById('recentList');
    ul.innerHTML = '';
    if (!txns.length) {
      ul.innerHTML = '<li class="empty">이번 달 거래가 없습니다.</li>';
      return;
    }
    txns.slice(0, 8).forEach(t => {
      const li = document.createElement('li');
      li.className = 'item';
      const top = document.createElement('div');
      top.className = 'row';
      const title = document.createElement('span');
      title.className = 't';
      title.textContent = (t.description || t.category || '거래') + ' · ' + (t.businessName || '');
      const amt = document.createElement('span');
      amt.className = 'amt ' + (t.direction === 'INCOME' ? 'in' : 'out');
      amt.textContent = (t.direction === 'INCOME' ? '+' : '-') + Easy.fmtWon(t.amount);
      top.appendChild(title);
      top.appendChild(amt);
      const sub = document.createElement('div');
      sub.className = 'row';
      const s = document.createElement('span');
      s.className = 's';
      s.textContent = (t.occurredOn || '') + ' · ' + (t.category || '');
      const badge = document.createElement('span');
      badge.className = 'badge ' + (t.direction === 'INCOME' ? 'in' : 'out');
      badge.textContent = t.direction === 'INCOME' ? '수입' : '지출';
      sub.appendChild(s);
      sub.appendChild(badge);
      li.appendChild(top);
      li.appendChild(sub);
      ul.appendChild(li);
    });
  }

  function renderOverdue(students) {
    const ul = document.getElementById('overdueList');
    ul.innerHTML = '';
    if (!students.length) {
      ul.innerHTML = '<li class="empty">미수금이 없습니다.</li>';
      return;
    }
    students.forEach(s => {
      const a = document.createElement('a');
      a.className = 'item';
      a.href = '/easy/students?id=' + encodeURIComponent(s.studentId);
      const top = document.createElement('div');
      top.className = 'row';
      const name = document.createElement('span');
      name.className = 't';
      name.textContent = s.name + (s.school ? ' · ' + s.school : '');
      const amt = document.createElement('span');
      amt.className = 'amt out';
      amt.textContent = Easy.fmtWon(s.balance);
      top.appendChild(name);
      top.appendChild(amt);
      a.appendChild(top);
      ul.appendChild(a);
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    init().catch(err => console.error(err));
  });
})();
