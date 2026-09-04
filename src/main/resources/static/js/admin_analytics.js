/** 관리자 통합 Analytics(/admin/analytics). F2R 전체/Wiki 방문(UV/PV) + Wiki 다운로드 통계. */
(function () {
  let currentUser = null;

  const CATEGORY_LABELS = { ORIGINAL: '모의고사 원본', ANALYSIS: '분석 자료', LEARNING: '학습 자료' };

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
    ].map(([label, n]) => `<div class="analytics-metric"><div class="n">${n ?? 0}</div><div class="l">${label}</div></div>`).join('');
  }

  async function loadAnalytics() {
    try {
      const res = await fetchAuth('/api/admin/analytics/summary');
      if (!res.ok) throw new Error();
      const d = await res.json();

      renderMetrics('siteVisitorsMetrics', d.siteVisitors);
      renderMetrics('wikiVisitorsMetrics', d.wikiVisitors);
      renderMetrics('wikiDownloadsMetrics', d.wikiDownloads);
      renderMetrics('sitePvMetrics', d.siteViews);
      renderMetrics('wikiPvMetrics', d.wikiViews);

      document.getElementById('statsSince').textContent = d.statsSince
        ? `통계 수집 시작일(${fmtDate(d.statsSince)}) 이후 데이터입니다.`
        : '아직 수집된 방문 데이터가 없습니다.';

      const visitsBody = document.getElementById('recentVisitsBody');
      const visits = d.recentWikiVisits || [];
      visitsBody.innerHTML = visits.length
        ? visits.map(v => `
            <tr>
              <td>${escapeHtml(v.displayName)}</td>
              <td>${roleBadge(v.role)}</td>
              <td><span class="area-badge">${escapeHtml(v.area)}</span></td>
              <td>${escapeHtml(v.path)}</td>
              <td>${fmtDateTime(v.visitedAt)}</td>
            </tr>`).join('')
        : '<tr><td colspan="5" class="analytics-empty">아직 Wiki 방문 기록이 없습니다.</td></tr>';

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

  (async () => {
    currentUser = await ensureCurrentUser();
    if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/home'; return; }
    document.getElementById('userName').textContent = window.userDisplayName(currentUser);
    loadAnalytics();
  })();
})();
