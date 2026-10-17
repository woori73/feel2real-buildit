/** 관리자 통합 Analytics(/admin/analytics). F2R 전체/Wiki/NewStart 방문(UV/PV) + Wiki 다운로드 통계. */
(function () {
  let currentUser = null;

  const CATEGORY_LABELS = { ORIGINAL: '모의고사 원본', ANALYSIS: '분석 자료', LEARNING: '학습 자료' };
  const SERVICE_LABELS = {
    HOME: '메인/랜딩', CHART: 'BuildIt Chart', WEB: 'BuildIt Web',
    ACADEMY: 'Academy', WIKI: 'Wiki', NEWSTART: 'NewStart', OTHER: '기타'
  };

  function fmtNumber(v) {
    return Number(v || 0).toLocaleString('ko-KR');
  }

  function fmtDate(v) {
    if (!v) return '';
    const d = new Date(v);
    if (isNaN(d)) return '';
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function fmtDateTime(v) {
    if (!v) return '';
    const d = new Date(v);
    if (isNaN(d)) return '';
    return fmtDate(v) + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  function escapeHtml(str) {
    return String(str ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function categoryLabel(category) {
    return CATEGORY_LABELS[category] || escapeHtml(category);
  }

  function roleBadge(role) {
    const cls = role === 'GUEST' ? 'role-badge guest' : 'role-badge';
    return `<span class="${cls}">${escapeHtml(role)}</span>`;
  }

  function renderMetrics(elId, metric) {
    const m = metric || {};
    document.getElementById(elId).innerHTML = [
      ['오늘', m.today], ['7일', m.recent7], ['30일', m.recent30], ['누적', m.total]
    ].map(([label, n]) => `<div class="analytics-metric"><div class="n">${fmtNumber(n)}</div><div class="l">${label}</div></div>`).join('');
  }

  async function loadAnalytics() {
    try {
      const res = await fetchAuth('/api/admin/analytics/summary');
      if (!res.ok) throw new Error();
      const d = await res.json();

      renderMetrics('siteVisitorsMetrics', d.siteVisitors);
      renderMetrics('wikiVisitorsMetrics', d.wikiVisitors);
      renderMetrics('newStartVisitorsMetrics', d.newStartVisitors);
      renderMetrics('wikiDownloadsMetrics', d.wikiDownloads);
      renderMetrics('sitePvMetrics', d.siteViews);
      renderMetrics('wikiPvMetrics', d.wikiViews);
      renderMetrics('botRequestsMetrics', d.botRequests);

      document.getElementById('statsSince').textContent = d.statsSince
        ? `통계 수집 시작일(${fmtDate(d.statsSince)}) 이후 데이터입니다. 방문자 식별 방식 개선 이전 데이터에는 세션 기준 집계와 Bot 방문이 포함될 수 있습니다.`
        : '아직 수집된 방문 데이터가 없습니다.';

      const serviceBody = document.getElementById('serviceTrafficBody');
      const serviceTraffic = d.serviceTraffic || [];
      serviceBody.innerHTML = serviceTraffic.length
        ? serviceTraffic.map(v => `
            <tr>
              <td><span class="area-badge">${escapeHtml(SERVICE_LABELS[v.service] || v.service)}</span></td>
              <td class="num">${fmtNumber(v.visitors?.today)}</td>
              <td class="num">${fmtNumber(v.views?.today)}</td>
              <td class="num">${fmtNumber(v.visitors?.recent7)}</td>
              <td class="num">${fmtNumber(v.views?.recent7)}</td>
              <td class="num">${fmtNumber(v.visitors?.recent30)}</td>
              <td class="num">${fmtNumber(v.views?.recent30)}</td>
              <td class="num">${fmtNumber(v.visitors?.total)}</td>
              <td class="num">${fmtNumber(v.views?.total)}</td>
            </tr>`).join('')
        : '<tr><td colspan="9" class="analytics-empty">아직 서비스별 방문 기록이 없습니다.</td></tr>';

      const visitsBody = document.getElementById('recentVisitsBody');
      const visits = d.recentVisits || [];
      visitsBody.innerHTML = visits.length
        ? visits.map(v => `
            <tr>
              <td>${escapeHtml(v.displayName)}</td>
              <td>${roleBadge(v.role)}</td>
              <td><span class="area-badge">${escapeHtml(v.area)}</span></td>
              <td>${escapeHtml(v.path)}</td>
              <td>${fmtDateTime(v.visitedAt)}</td>
            </tr>`).join('')
        : '<tr><td colspan="5" class="analytics-empty">아직 방문 기록이 없습니다.</td></tr>';

      const downloadsBody = document.getElementById('recentDownloadsBody');
      const downloads = d.recentDownloads || [];
      downloadsBody.innerHTML = downloads.length
        ? downloads.map(v => `
            <tr>
              <td>${escapeHtml(v.displayName)}</td>
              <td>${roleBadge(v.role)}</td>
              <td>${escapeHtml(v.materialTitle)}</td>
              <td>${categoryLabel(v.materialCategory)}</td>
              <td>${fmtDateTime(v.downloadedAt)}</td>
            </tr>`).join('')
        : '<tr><td colspan="5" class="analytics-empty">아직 다운로드 기록이 없습니다.</td></tr>';

      const topBody = document.getElementById('topDownloadsBody');
      const top = d.topDownloads || [];
      topBody.innerHTML = top.length
        ? top.map(v => `
            <tr>
              <td>${escapeHtml(v.materialTitle)}</td>
              <td>${categoryLabel(v.materialCategory)}</td>
              <td>${v.downloadCount ?? 0}</td>
            </tr>`).join('')
        : '<tr><td colspan="3" class="analytics-empty">아직 다운로드 기록이 없습니다.</td></tr>';
    } catch {
      document.getElementById('statsSince').textContent = 'Analytics 데이터를 불러오지 못했습니다.';
    }
  }


  // ───────── 세분화(서비스·페이지·추세·전환): /api/admin/analytics/breakdown ─────────
  function periodCells(r) {
    return `<td class="num">${fmtNumber(r.visitors?.today)}</td><td class="num">${fmtNumber(r.views?.today)}</td>`
      + `<td class="num">${fmtNumber(r.visitors?.recent7)}</td><td class="num">${fmtNumber(r.views?.recent7)}</td>`
      + `<td class="num">${fmtNumber(r.visitors?.recent30)}</td><td class="num">${fmtNumber(r.views?.recent30)}</td>`
      + `<td class="num">${fmtNumber(r.visitors?.total)}</td><td class="num">${fmtNumber(r.views?.total)}</td>`;
  }

  function renderRows(bodyId, rows) {
    document.getElementById(bodyId).innerHTML = rows.length
      ? rows.map(r => `<tr><td>${escapeHtml(r.label)}</td>${periodCells(r)}</tr>`).join('')
      : '<tr><td colspan="9" class="analytics-empty">표시할 데이터가 없습니다.</td></tr>';
  }

  /** 일별 PV/UV 선 그래프(SVG). 값은 모두 숫자이고 날짜 라벨만 문자열이다. */
  function renderTrend(points) {
    const box = document.getElementById('anTrend');
    const W = 760, H = 220, L = 40, R = 10, T = 12, B = 26;
    const max = Math.max(1, ...points.map(p => Math.max(p.views, p.visitors)));
    const x = i => L + (points.length <= 1 ? 0 : i * (W - L - R) / (points.length - 1));
    const y = v => T + (H - T - B) * (1 - v / max);
    const line = (key, color) => `<polyline fill="none" stroke="${color}" stroke-width="2" points="${points.map((p, i) => x(i).toFixed(1) + ',' + y(p[key]).toFixed(1)).join(' ')}"/>`;
    const ticks = [0, 0.5, 1].map(f => `<g><line x1="${L}" x2="${W - R}" y1="${y(max * f).toFixed(1)}" y2="${y(max * f).toFixed(1)}" stroke="#e2e8f0"/><text x="${L - 6}" y="${(y(max * f) + 4).toFixed(1)}" font-size="11" text-anchor="end" fill="#718096">${fmtNumber(Math.round(max * f))}</text></g>`).join('');
    const labels = points.map((p, i) => (i % 7 === 0 || i === points.length - 1)
      ? `<text x="${x(i).toFixed(1)}" y="${H - 8}" font-size="11" text-anchor="middle" fill="#718096">${escapeHtml(String(p.date).slice(5))}</text>` : '').join('');
    const empty = points.every(p => p.views === 0 && p.visitors === 0);
    box.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="최근 30일 PV·UV 추세">${ticks}${line('views', '#3b5bdb')}${line('visitors', '#2f9e44')}${labels}</svg>`
      + (empty ? '<p class="an-note">이 기간에 수집된 방문이 없습니다.</p>' : '');
  }

  function renderConversions(items) {
    document.getElementById('anConversions').innerHTML = items.map(c => {
      if (!c.collected) {
        return `<div class="analytics-card off"><h3>${escapeHtml(c.label)}</h3><div class="an-off">미수집</div><p class="an-note">${escapeHtml(c.note || '')}</p></div>`;
      }
      const m = c.metric || {};
      return `<div class="analytics-card"><h3>${escapeHtml(c.label)}</h3><div class="analytics-metrics">`
        + [['오늘', m.today], ['7일', m.recent7], ['30일', m.recent30], ['누적', m.total]]
          .map(([l, n]) => `<div class="analytics-metric"><div class="n">${fmtNumber(n)}</div><div class="l">${l}</div></div>`).join('')
        + `</div><p class="an-note">${escapeHtml(c.note || '')}</p></div>`;
    }).join('');
  }

  async function loadBreakdown(service) {
    try {
      const res = await fetchAuth('/api/admin/analytics/breakdown' + (service ? '?service=' + encodeURIComponent(service) : ''));
      if (!res.ok) throw new Error();
      const d = await res.json();
      if (!service) {
        renderRows('anServicesBody', d.services || []);
        renderRows('anPagesBody', d.pageGroups || []);
        document.getElementById('anPagesNote').textContent = (d.pagesWithoutRoute || []).length
          ? '전용 페이지가 없어 집계하지 않는 항목: ' + d.pagesWithoutRoute.join(', ') + ' (홈페이지 섹션 지표로 확인 예정)' : '';
        document.getElementById('anTopPathsBody').innerHTML = (d.topPaths || []).length
          ? d.topPaths.map(t => `<tr><td>${escapeHtml(t.path)}</td><td>${escapeHtml(t.service)}</td><td class="num">${fmtNumber(t.views30)}</td><td class="num">${fmtNumber(t.visitors30)}</td></tr>`).join('')
          : '<tr><td colspan="4" class="analytics-empty">최근 30일 방문 기록이 없습니다.</td></tr>';
        renderConversions(d.conversions || []);
        const defs = d.definitions || {};
        document.getElementById('anDefUv').textContent = defs.uv || '';
        document.getElementById('anDefPv').textContent = defs.pv || '';
        document.getElementById('anDefBot').textContent = defs.bot || '';
        document.getElementById('anDefAdd').textContent = defs.additivity || '';
        document.getElementById('anDefPeriods').textContent = defs.periods || '';
        const sel = document.getElementById('anTrendService');
        if (sel.options.length <= 1) {
          (d.services || []).forEach(s => { const o = document.createElement('option'); o.value = s.key; o.textContent = s.label; sel.appendChild(o); });
        }
      }
      renderTrend(d.trend || []);
    } catch {
      ['anServicesBody', 'anPagesBody'].forEach(id => { document.getElementById(id).innerHTML = '<tr><td colspan="9" class="analytics-empty">세분화 통계를 불러오지 못했습니다.</td></tr>'; });
    }
  }

  (async () => {
    currentUser = await ensureCurrentUser();
    if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/home'; return; }
    document.getElementById('userName').textContent = window.userDisplayName(currentUser);
    loadAnalytics();
    loadBreakdown('');
    document.getElementById('anTrendService').addEventListener('change', e => loadBreakdown(e.target.value));
  })();
})();
