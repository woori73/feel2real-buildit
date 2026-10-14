/* 빌보드 형식 한 줄: 순위 · 사진 · 증감 화살표 · 이름/소개 · 지난주/지역 */
function bbRow(e, esc) {
  var rank = Number(e.rank);
  var prev = e.previousRank == null ? null : Number(e.previousRank);
  var arrow = prev == null ? 'NEW' : (prev > rank ? '▲' : (prev < rank ? '▼' : '→'));
  var name = esc(e.name);
  var photo = e.profileImageUrl
    ? '<img src="' + esc(e.profileImageUrl) + '" alt="" loading="lazy" referrerpolicy="no-referrer">'
    : '<span>' + esc(String(e.name || '').charAt(0)) + '</span>';
  var region = e.activityRegion ? '<div><dt>지역</dt><dd>' + esc(e.activityRegion) + '</dd></div>' : '';
  return '<a class="bb-row' + (rank === 1 ? ' bb-first' : '') + '" href="/chart/educators/' + encodeURIComponent(e.educatorId) + '">'
    + '<div class="bb-rank">' + esc(e.rank) + '</div><div class="bb-photo">' + photo + '</div>'
    + '<div class="bb-trend" aria-hidden="true">' + arrow + '</div>'
    + '<div class="bb-main"><strong>' + name + '</strong><span>' + esc(e.identitySummary || e.academyName || e.role) + '</span></div>'
    + '<dl class="bb-stats"><div><dt>지난주</dt><dd>' + (prev == null ? '신규' : esc(prev)) + '</dd></div>' + region + '</dl></a>';
}

if (typeof document !== 'undefined') (function () {
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
      dateEl.textContent = data.updatedAt ? String(data.updatedAt).slice(0, 10).replace(/-/g, '.') : '';
    }
    var rows = (data.rankedTop100 || data.entries || []).slice(0, 5);
    root.innerHTML = rows.length ? rows.map(function (e) { return bbRow(e, esc); }).join('')
      : '<div class="bb-empty">전국 조사에서 선정된 교육자가 곧 공개됩니다.</div>';
  }).catch(function () {
    root.innerHTML = '<div class="bb-empty">Chart를 불러오지 못했습니다.</div>';
  });
})();
if (typeof module !== 'undefined' && module.exports) module.exports = { bbRow: bbRow };
