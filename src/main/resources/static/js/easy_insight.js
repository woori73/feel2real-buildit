/**
 * /easy 경영 영역 — 올해/작년 동기 비교 + AI가 확인할 장부 + 브리핑 + 종소세 자리.
 * 사업체 선택은 easy_dashboard.js의 easy-scope 이벤트로 공유받는다.
 * 숫자는 서버 실측만 표시하고 세액 계산·예측은 하지 않는다.
 */
(function () {
  let businessId = null;

  function scopeQuery() {
    return businessId ? '?businessId=' + encodeURIComponent(businessId) : '';
  }

  async function loadExecutive() {
    const res = await fetchAuth('/api/admin/easy/executive-summary' + scopeQuery());
    if (!res.ok) throw new Error('경영 요약을 불러오지 못했습니다.');
    const s = await res.json();
    renderYoy(s);
    renderBrief(s.briefing || []);
    renderTax(s.incomeTaxPreview);
  }

  function renderYoy(s) {
    const range = document.getElementById('yoyRange');
    if (range) range.textContent = fmtRange(s.currentFrom, s.currentTo);
    const grid = document.getElementById('yoyGrid');
    if (!grid) return;
    grid.innerHTML = '';
    const rows = [
      { l: '매출', cur: s.currentIncome, prev: s.previousIncome, diff: s.incomeDiff, rate: s.incomeRate },
      { l: '비용', cur: s.currentExpense, prev: s.previousExpense, diff: s.expenseDiff, rate: s.expenseRate },
      { l: '장부상 손익', cur: s.currentProfit, prev: s.previousProfit, diff: s.profitDiff, rate: s.profitRate }
    ];
    rows.forEach(r => {
      const div = document.createElement('div');
      div.className = 'yoy-row';
      const head = document.createElement('div');
      head.className = 'yoy-head';
      head.textContent = r.l;
      const nums = document.createElement('div');
      nums.className = 'yoy-nums';
      nums.innerHTML = '';
      const cur = document.createElement('span');
      cur.className = 'yoy-cur';
      cur.textContent = '올해 ' + Easy.fmtWon(r.cur);
      const prev = document.createElement('span');
      prev.className = 'yoy-prev';
      prev.textContent = '작년 동기 ' + Easy.fmtWon(r.prev);
      const diff = document.createElement('span');
      diff.className = 'yoy-diff ' + (r.diff < 0 ? 'minus' : r.diff > 0 ? 'plus' : '');
      diff.textContent = (r.diff >= 0 ? '+' : '-') + Easy.fmtWon(Math.abs(r.diff))
        + (r.rate === null || r.rate === undefined ? ' (비교 기준 없음)' : ' (' + r.rate.toFixed(1) + '%)');
      nums.appendChild(cur);
      nums.appendChild(prev);
      nums.appendChild(diff);
      div.appendChild(head);
      div.appendChild(nums);
      grid.appendChild(div);
    });
  }

  function fmtRange(from, to) {
    if (!from || !to) return '';
    return from + ' ~ ' + to;
  }

  async function loadReviews() {
    const res = await fetchAuth('/api/admin/easy/reviews/summary' + scopeQuery());
    if (!res.ok) throw new Error('검토 현황을 불러오지 못했습니다.');
    const s = await res.json();
    renderReviews(s);
  }

  function renderReviews(s) {
    const grid = document.getElementById('reviewGrid');
    if (!grid) return;
    grid.innerHTML = '';
    const cards = [
      { l: '장부 미입력', n: s.importUnconfirmed },
      { l: '계정과목 미분류', n: s.unclassified },
      { l: '증빙 확인 필요', n: s.evidence },
      { l: '사업장 확인 필요', n: s.business },
      { l: '원천징수 확인', n: s.withholding },
      { l: '중복 의심', n: s.duplicateSuspect }
    ];
    cards.forEach(c => {
      const div = document.createElement('div');
      div.className = 'review-card' + (c.n > 0 ? ' has' : '');
      const n = document.createElement('div');
      n.className = 'n';
      n.textContent = c.n + '건';
      const l = document.createElement('div');
      l.className = 'l';
      l.textContent = c.l;
      div.appendChild(n);
      div.appendChild(l);
      grid.appendChild(div);
    });
    const total = document.createElement('a');
    total.className = 'review-total';
    total.href = '/easy/transactions';
    total.textContent = '확인하기 ›';
    grid.appendChild(total);
  }

  function renderBrief(lines) {
    const ul = document.getElementById('briefList');
    if (!ul) return;
    ul.innerHTML = '';
    if (!lines.length) {
      ul.innerHTML = '<li class="empty">브리핑할 내용이 없습니다.</li>';
      return;
    }
    lines.forEach(text => {
      const li = document.createElement('li');
      li.textContent = text;
      ul.appendChild(li);
    });
  }

  function renderTax(preview) {
    const el = document.getElementById('taxPreview');
    if (!el) return;
    if (!preview || preview.status === 'PENDING_REAL_DATA') {
      el.textContent = '올해 현재 장부 기준 종합소득세 예상 — '
        + ((preview && preview.note) || '실제 장부·신고 자료 확보 후 계산합니다.');
      return;
    }
    el.textContent = preview.note || '';
  }

  async function loadYearPl() {
    const year = new Date().getFullYear();
    const res = await fetchAuth('/api/admin/easy/pl' + scopeQuery()
      + (businessId ? '&year=' + year : '?year=' + year));
    if (!res.ok) throw new Error('연도 손익을 불러오지 못했습니다.');
    const p = await res.json();
    renderYearPl(p);
  }

  function drillUrl(p, accountId, direction) {
    const params = new URLSearchParams();
    if (p.businessId) params.set('businessId', p.businessId);
    params.set('from', p.year + '-01-01');
    params.set('to', p.year + '-12-31');
    if (accountId) params.set('accountId', accountId);
    if (direction) params.set('direction', direction);
    return '/easy/transactions?' + params.toString();
  }

  function renderYearPl(p) {
    const label = document.getElementById('yearPlLabel');
    if (label) label.textContent = p.year + '년 · 분개 ' + (p.journalBalanced ? '균형' : '불일치')
      + (p.matchesLedger ? '' : ' · 장부 불일치(미분류 확인)');
    const grid = document.getElementById('yearPlGrid');
    if (!grid) return;
    grid.innerHTML = '';
    const totals = [
      { l: '매출', n: p.income, dir: 'INCOME' },
      { l: '비용', n: p.expense, dir: 'EXPENSE' },
      { l: '장부상 손익', n: p.profit, dir: null }
    ];
    totals.forEach(t => {
      const div = document.createElement('div');
      div.className = 'yoy-row';
      const head = document.createElement('div');
      head.className = 'yoy-head';
      head.textContent = t.l;
      div.appendChild(head);
      const val = document.createElement('div');
      if (t.dir) {
        const a = document.createElement('a');
        a.className = 'yoy-cur';
        a.href = drillUrl(p, null, t.dir);
        a.textContent = Easy.fmtWon(t.n);
        val.appendChild(a);
      } else {
        const s = document.createElement('span');
        s.className = 'yoy-cur';
        s.textContent = Easy.fmtWon(t.n);
        val.appendChild(s);
      }
      const prev = document.createElement('div');
      prev.className = 'yoy-prev';
      const rate = t.dir === 'INCOME' ? p.prevYear.incomeRate
        : t.dir === 'EXPENSE' ? p.prevYear.expenseRate : p.prevYear.profitRate;
      prev.textContent = '전년 ' + Easy.fmtWon(t.dir === 'INCOME' ? p.prevYear.income
        : t.dir === 'EXPENSE' ? p.prevYear.expense : p.prevYear.profit)
        + (rate === null || rate === undefined ? ' (비교 기준 없음)' : ' (' + rate.toFixed(1) + '%)');
      div.appendChild(val);
      div.appendChild(prev);
      grid.appendChild(div);
    });
    (p.byAccount || []).slice(0, 5).forEach(a => {
      const div = document.createElement('div');
      div.className = 'yoy-row';
      const head = document.createElement('div');
      head.className = 'yoy-head';
      head.textContent = '주요 비용·수익: ' + a.accountName;
      const link = document.createElement('a');
      link.className = 'yoy-cur';
      link.href = drillUrl(p, a.accountId, null);
      link.textContent = Easy.fmtWon(a.total);
      div.appendChild(head);
      div.appendChild(link);
      grid.appendChild(div);
    });
  }

  async function reload() {
    await Promise.all([loadExecutive(), loadReviews(), loadYearPl()])
      .catch(err => console.error(err));
  }

  document.addEventListener('easy-scope', e => {
    businessId = (e.detail && e.detail.businessId) || null;
    reload();
  });

  document.addEventListener('DOMContentLoaded', () => {
    businessId = window.__easyBusinessId || null;
    const btn = document.getElementById('btnRebuild');
    if (btn) {
      btn.addEventListener('click', async () => {
        if (!confirm('확인 필요 항목을 다시 찾겠습니까?')) return;
        btn.disabled = true;
        try {
          const res = await fetchAuth(
            '/api/admin/easy/reviews/rebuild' + scopeQuery(), { method: 'POST' });
          if (!res.ok) throw new Error('다시 찾기에 실패했습니다.');
          const s = await res.json();
          renderReviews(s);
        } catch (err) {
          alert(err.message || '다시 찾기에 실패했습니다.');
        } finally {
          btn.disabled = false;
        }
      });
    }
    reload();
  });
})();
