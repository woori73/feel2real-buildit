/* BuildIt Chart 공개 화면 — 지역 교육자 발견 + 평가 기준 + BuildIt TOP100.
   모든 교육자는 공개 상세 페이지 /chart/educators/{id} 로 연결한다(modal 없음). */
(function () {
  var esc = function (v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };
  var qs = function (id) { return document.getElementById(id); };
  var num = function (v) { return (v == null || isNaN(v)) ? '–' : v; };

  var avatar = function (url) {
    return url
      ? '<img class="ed-avatar" src="' + esc(url) + '" alt="" loading="lazy" referrerpolicy="no-referrer">'
      : '<svg class="ed-avatar avatar-default" viewBox="0 0 56 56" aria-hidden="true"><circle cx="28" cy="28" r="28" fill="var(--bg-soft)"/><circle cx="28" cy="22" r="9" fill="var(--ink-faint)"/><path d="M10 48c2-9.5 9.8-15 18-15s16 5.5 18 15" fill="var(--ink-faint)"/></svg>';
  };
  var regionText = function (e) {
    return esc(e.activityRegion || [e.province || e.activityProvince, e.cityDistrict || e.activityCityDistrict].filter(Boolean).join(' '));
  };
  var channelLinks = function (e) {
    var ch = e.officialChannels || {};
    var links = [
      ['홈페이지', ch.homepage || e.officialHomepage],
      ['블로그', ch.blog || e.officialBlog],
      ['YouTube', ch.youtube || e.officialYoutube],
      ['SNS', ch.social || e.officialSocial]
    ].filter(function (x) { return x[1]; })
      .map(function (x) { return '<a href="' + esc(x[1]) + '" target="_blank" rel="noopener noreferrer nofollow">' + x[0] + '</a>'; });
    return links.length ? '<div class="ed-channels">' + links.join('') + '</div>' : '';
  };

  /* ---------------- 지역 선택 + 지역 결과 ---------------- */
  var provinceSel = qs('provinceSelect'), districtSel = qs('districtSelect');
  var regionForm = qs('regionPicker'), regionResult = qs('regionResult');
  var grid = qs('educatorGrid'), loadMoreBtn = qs('loadMoreBtn');
  var regionTree = [];
  var pageState = { province: '', city: '', page: 0, size: 24, total: 0, rankMap: {} };

  function param() {
    var p = new URLSearchParams(location.search);
    return { province: p.get('province') || '', city: p.get('city') || p.get('cityDistrict') || '' };
  }
  function syncUrl() {
    var p = new URLSearchParams();
    if (pageState.province) p.set('province', pageState.province);
    if (pageState.city) p.set('city', pageState.city);
    var qsStr = p.toString();
    history.replaceState(null, '', location.pathname + (qsStr ? '?' + qsStr : '') + '#regionResult');
  }

  fetch('/api/chart/regions').then(function (r) { return r.ok ? r.json() : Promise.reject(); }).then(function (data) {
    regionTree = (data && data.provinces) || [];
    provinceSel.innerHTML = '<option value="">시·도 선택</option>' + regionTree.map(function (p) {
      return '<option value="' + esc(p.province) + '">' + esc(p.province) + ' (' + p.educatorCount + ')</option>';
    }).join('');
    var initial = param();
    if (initial.province && findProvince(initial.province)) {
      provinceSel.value = initial.province;
      fillDistricts(initial.province);
      if (initial.city) { districtSel.value = initial.city; }
      loadRegion(initial.province, initial.city, true);
    }
  }).catch(function () {
    qs('regionPickerNote').textContent = '지역 목록을 불러오지 못했습니다.';
  });

  function findProvince(name) { return regionTree.filter(function (p) { return p.province === name; })[0]; }
  function fillDistricts(provinceName) {
    var p = findProvince(provinceName);
    var opts = ['<option value="">시·군·구 전체</option>'];
    if (p) {
      p.cityDistricts.forEach(function (d) {
        if (!d.cityDistrict) return;
        opts.push('<option value="' + esc(d.cityDistrict) + '">' + esc(d.cityDistrict) + ' (' + d.educatorCount + ')</option>');
      });
    }
    districtSel.innerHTML = opts.join('');
    districtSel.disabled = !p;
  }
  provinceSel.addEventListener('change', function () { fillDistricts(provinceSel.value); });
  regionForm.addEventListener('submit', function (ev) {
    ev.preventDefault();
    if (!provinceSel.value) { provinceSel.focus(); return; }
    loadRegion(provinceSel.value, districtSel.value, true);
  });

  function loadRegion(province, city, reset) {
    if (reset) { pageState = { province: province, city: city, page: 0, size: 24, total: 0, rankMap: {} }; grid.innerHTML = ''; }
    regionResult.hidden = false;
    var label = province + (city ? ' ' + city : '');
    qs('regionResultTitle').textContent = label + '에서 BuildIt이 발견한 영어쌤';
    if (pageState.page === 0) {
      grid.innerHTML = '<p class="state">불러오는 중…</p>';
      var rp = new URLSearchParams({ province: province }); if (city) rp.set('cityDistrict', city);
      fetch('/api/chart/rankings?' + rp.toString()).then(function (r) { return r.ok ? r.json() : { educators: [] }; })
        .then(function (rk) {
          pageState.rankMap = {};
          (rk.educators || []).forEach(function (e) { pageState.rankMap[e.id] = e; });
          fetchPage(province, city);
        }).catch(function () { fetchPage(province, city); });
    } else {
      fetchPage(province, city);
    }
    syncUrl();
  }

  function fetchPage(province, city) {
    var sp = new URLSearchParams({ province: province, page: pageState.page, size: pageState.size });
    if (city) sp.set('cityDistrict', city);
    fetch('/api/chart/educators/search?' + sp.toString())
      .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
      .then(function (data) {
        pageState.total = data.totalElements || 0;
        if (pageState.page === 0) grid.innerHTML = '';
        var rows = data.content || [];
        if (pageState.page === 0 && !rows.length) {
          grid.innerHTML = '<p class="state">이 지역은 아직 BuildIt이 조사 중입니다. 다른 지역을 선택해 보세요.</p>';
        } else {
          grid.insertAdjacentHTML('beforeend', rows.map(educatorCard).join(''));
        }
        var shown = (data.page + 1) * data.size;
        loadMoreBtn.hidden = shown >= pageState.total;
        qs('regionResultSub').textContent =
          'BuildIt이 이 지역에서 발견하고 기록한 영어교육자 ' + pageState.total + '명입니다.';
      })
      .catch(function () { if (pageState.page === 0) grid.innerHTML = '<p class="state error">지역 교육자를 불러오지 못했습니다.</p>'; });
  }
  loadMoreBtn.addEventListener('click', function () { pageState.page += 1; fetchPage(pageState.province, pageState.city); });

  function educatorCard(e) {
    var rank = pageState.rankMap[e.id];
    var href = '/chart/educators/' + encodeURIComponent(e.id);
    var rankRow = '';
    if (rank) {
      rankRow = '<div class="ed-rank">'
        + '<span class="score-pill">BuildIt Score ' + num(rank.buildItScore) + '</span>'
        + '<span class="rank-pill">' + esc(rank.cityDistrict || e.cityDistrict || '지역') + ' #' + num(rank.regionRank) + '</span>'
        + '<span class="rank-pill">전국 #' + num(rank.overallRank) + '</span>'
        + (rank.top100 ? '<span class="top100-pill">BuildIt TOP100</span>' : '')
        + '</div>';
    } else if (e.researchPending) {
      rankRow = '<div class="ed-rank"><span class="pending-pill">정보 확인 중</span></div>';
    }
    return '<a class="educator-card" href="' + href + '">'
      + '<div class="ed-head">' + avatar(e.profileImageUrl)
      + '<div><div class="ed-name">' + esc(e.name) + ' <span>' + esc(e.role) + '</span></div>'
      + '<div class="ed-academy">' + esc(e.academyName || '소속 확인 중') + '</div>'
      + '<div class="ed-region">' + regionText(e) + '</div></div></div>'
      + rankRow
      + (e.identitySummary ? '<p class="ed-identity">' + esc(e.identitySummary) + '</p>' : '')
      + (e.educationPhilosophy ? '<p class="ed-philosophy">' + esc(e.educationPhilosophy) + '</p>' : '')
      + channelLinks(e)
      + '<span class="ed-more">선생님 기록 보기 →</span>'
      + '</a>';
  }

  /* ---------------- 평가 기준 ---------------- */
  fetch('/api/chart/criteria').then(function (r) { return r.ok ? r.json() : Promise.reject(); }).then(function (c) {
    qs('rankingDisclaimer').textContent = c.rankingDisclaimer || '';
    qs('criteriaList').innerHTML = (c.criteria || []).map(function (x) {
      return '<div class="criterion"><div class="criterion-head"><strong>' + esc(x.label) + '</strong>'
        + '<span class="criterion-weight">' + x.weight + '점</span></div>'
        + '<p>' + esc(x.description) + '</p></div>';
    }).join('') + '<p class="criteria-total">합계 ' + (c.maxScore || 100) + '점 · 기준 버전 ' + esc(c.criteriaVersion || '') + '</p>';
    qs('criteriaNegatives').innerHTML = '<h3>평가에 사용하지 않는 것</h3><ul>'
      + (c.notEvaluated || []).map(function (n) { return '<li>' + esc(n) + '</li>'; }).join('') + '</ul>';
  }).catch(function () { qs('criteria').hidden = true; });

  /* ---------------- BuildIt TOP100 (기존 publication) ---------------- */
  var topList = qs('topChartList'), restList = qs('restChartList'), restSection = qs('restSection');
  var discList = qs('discoveredList'), discSection = qs('discoveredSection');

  function trend(e) {
    if (e.previousRank == null) return { cls: 'new', label: 'NEW' };
    var d = e.previousRank - e.rank;
    return d > 0 ? { cls: 'up', label: '▲ ' + d } : d < 0 ? { cls: 'down', label: '▼ ' + Math.abs(d) } : { cls: 'flat', label: '—' };
  }
  function billboardRow(e, discovered) {
    var t = trend(e);
    var href = '/chart/educators/' + encodeURIComponent(e.educatorId);
    return '<a class="chart-row ' + (!discovered && e.rank <= 10 ? 'top10' : '') + '" href="' + href + '">'
      + '<div class="row-rank"><span class="rank-num">' + (discovered ? '★' : esc(e.rank)) + '</span></div>'
      + '<div class="row-photo">' + avatar(e.profileImageUrl) + '</div>'
      + '<div class="row-primary"><div class="row-name-line"><strong class="row-name">' + esc(e.name) + '</strong>'
      + '<span class="row-role">' + esc(e.role) + '</span></div>'
      + '<div class="row-academy">' + esc(e.academyName || '소속 확인 중') + '</div></div>'
      + '<div class="row-secondary"><span class="row-region">' + regionText(e) + '</span></div>'
      + '<div class="row-stats">' + (discovered ? '' : '<span class="row-trend ' + t.cls + '">' + esc(t.label) + '</span>')
      + '<span class="score-badge">' + num(e.buildItScore) + '점</span></div>'
      + '<div class="row-action"><span class="btn outline small">기록 보기</span></div></a>';
  }
  fetch('/api/chart/publication/current').then(function (r) { return r.ok ? r.json() : Promise.reject(); }).then(function (data) {
    var ranked = data.rankedTop100 || data.entries || [];
    var discovered = data.discovered || [];
    qs('topCount').textContent = ranked.length + '명';
    topList.innerHTML = ranked.length ? ranked.slice(0, 10).map(function (e) { return billboardRow(e, false); }).join('')
      : '<div class="empty">전국 조사에서 선정된 교육자가 곧 공개됩니다.</div>';
    var rest = ranked.slice(10);
    restSection.hidden = !rest.length;
    restList.innerHTML = rest.map(function (e) { return billboardRow(e, false); }).join('');
    discSection.hidden = !discovered.length;
    discList.innerHTML = discovered.map(function (e) { return billboardRow(e, true); }).join('');
  }).catch(function () { topList.innerHTML = '<p class="state error">TOP100을 불러오지 못했습니다.</p>'; });
})();
