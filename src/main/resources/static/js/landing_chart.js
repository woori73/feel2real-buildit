(function () {
  var esc = function (v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  /* --- 지역 선택 → /chart?province=&city= --- */
  var provinceSel = document.getElementById('chartProvince');
  var districtSel = document.getElementById('chartDistrict');
  var form = document.getElementById('chartRegionPicker');
  var tree = [];
  if (form && provinceSel && districtSel) {
    fetch('/api/chart/regions').then(function (r) { return r.ok ? r.json() : Promise.reject(); }).then(function (data) {
      tree = (data && data.provinces) || [];
      provinceSel.innerHTML = '<option value="">시·도 선택</option>' + tree.map(function (p) {
        return '<option value="' + esc(p.province) + '">' + esc(p.province) + ' (' + p.educatorCount + ')</option>';
      }).join('');
    }).catch(function () {});
    provinceSel.addEventListener('change', function () {
      var p = tree.filter(function (x) { return x.province === provinceSel.value; })[0];
      var opts = ['<option value="">시·군·구 전체</option>'];
      if (p) {
        p.cityDistricts.forEach(function (d) {
          if (d.cityDistrict) opts.push('<option value="' + esc(d.cityDistrict) + '">' + esc(d.cityDistrict) + ' (' + d.educatorCount + ')</option>');
        });
      }
      districtSel.innerHTML = opts.join('');
      districtSel.disabled = !p;
    });
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      if (!provinceSel.value) { provinceSel.focus(); return; }
      var p = new URLSearchParams({ province: provinceSel.value });
      if (districtSel.value) p.set('city', districtSel.value);
      location.href = '/chart?' + p.toString();
    });
  }

  /* --- BuildIt TOP 5 미리보기 (기존 publication) --- */
  var root = document.getElementById('landingChartTop5');
  if (!root) return;
  fetch('/api/chart/publication/current').then(function (r) { return r.ok ? r.json() : Promise.reject(); }).then(function (data) {
    var dateEl = document.getElementById('landingChartDate');
    if (dateEl) {
      dateEl.textContent = data.updatedAt ? '· ' + String(data.updatedAt).slice(0, 10).replace(/-/g, '.') : '';
    }
    var rows = (data.rankedTop100 || data.entries || []).slice(0, 5);
    root.innerHTML = rows.length ? rows.map(function (e) {
      return '<a class="chart-row" href="/chart/educators/' + encodeURIComponent(e.educatorId) + '">'
        + '<div class="chart-rank">' + esc(e.rank) + '</div><div class="chart-avatar"></div>'
        + '<div><div class="chart-name">' + esc(e.name) + ' <span>' + esc(e.role) + '</span></div>'
        + '<div class="chart-meta">' + esc(e.identitySummary || e.academyName || e.activityRegion) + '</div></div>'
        + '<div class="chart-new">' + (e.previousRank == null ? 'NEW' : (e.previousRank > e.rank ? '▲' : (e.previousRank < e.rank ? '▼' : ''))) + '</div></a>';
    }).join('') : '<div class="chart-row"><div class="chart-meta">전국 조사에서 선정된 교육자가 곧 공개됩니다.</div></div>';
  }).catch(function () {
    root.innerHTML = '<div class="chart-row"><div class="chart-meta">Chart를 불러오지 못했습니다.</div></div>';
  });
})();
