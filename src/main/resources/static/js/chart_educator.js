/* BuildIt Chart 교육자 공개 상세 — /chart/educators/{id}
   /api/chart/educators/{id} (사실) + /api/chart/rankings/{id} (순위) 로 채운다. */
(function () {
  var root = document.querySelector('.educator-detail');
  if (!root) return;
  var id = root.getAttribute('data-educator-id');
  var esc = function (v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };
  var qs = function (x) { return document.getElementById(x); };
  var num = function (v) { return (v == null || isNaN(v)) ? '–' : v; };

  function showText(section, target, text) {
    if (text && String(text).trim()) { qs(target).innerHTML = '<p>' + esc(text) + '</p>'; qs(section).hidden = false; }
  }
  function showList(section, target, items) {
    var arr = (items || []).filter(function (x) { return x && String(x).trim(); });
    if (arr.length) { qs(target).innerHTML = arr.map(function (x) { return '<li>' + esc(x) + '</li>'; }).join(''); qs(section).hidden = false; }
  }
  function avatar(url) {
    return url
      ? '<img src="' + esc(url) + '" alt="" referrerpolicy="no-referrer">'
      : '<svg class="avatar-default" viewBox="0 0 96 96" aria-hidden="true"><circle cx="48" cy="48" r="48" fill="var(--bg-soft)"/><circle cx="48" cy="38" r="16" fill="var(--ink-faint)"/><path d="M16 84c4-17 17-27 32-27s28 10 32 27" fill="var(--ink-faint)"/></svg>';
  }

  fetch('/api/chart/educators/' + encodeURIComponent(id))
    .then(function (r) { if (r.status === 404) { location.href = '/chart'; return Promise.reject('404'); } return r.ok ? r.json() : Promise.reject(); })
    .then(function (d) {
      qs('detailAvatar').innerHTML = avatar(d.profileImageUrl);
      if (d.identitySummary) qs('detailIdentity').textContent = d.identitySummary;

      showText('blockPhilosophy', 'detailPhilosophy', d.educationPhilosophy);
      showText('blockCurriculum', 'detailCurriculum', d.curriculum);
      showText('blockTeaching', 'detailTeaching', d.distinctiveTeaching);
      showText('blockRecent', 'detailRecent', d.recentActivity);
      showList('blockStrengths', 'detailStrengths', d.strengths);
      showList('blockImprove', 'detailImprove', d.improvementSuggestions);

      var ev = (d.evidence || []).filter(function (x) { return x && x.sourceUrl; });
      if (ev.length) {
        qs('detailEvidence').innerHTML = ev.map(function (a) {
          return '<article class="content-card"><p class="content-meta">' + esc(a.evidenceKind || a.contentType || '공개 활동') + '</p>'
            + '<h4>' + esc(a.summary || '확인된 교육 활동') + '</h4>'
            + '<footer><span>' + esc(a.activityDate || '') + '</span>'
            + '<a href="' + esc(a.sourceUrl) + '" target="_blank" rel="noopener noreferrer nofollow">근거 보기 →</a></footer></article>';
        }).join('');
        qs('blockEvidence').hidden = false;
      }

      var ch = d.officialChannels || {};
      var chLinks = [['홈페이지', ch.homepage], ['블로그', ch.blog], ['YouTube', ch.youtube], ['SNS', ch.social]]
        .filter(function (x) { return x[1]; })
        .map(function (x) { return '<a href="' + esc(x[1]) + '" target="_blank" rel="noopener noreferrer nofollow">' + x[0] + ' →</a>'; });
      if (chLinks.length) { qs('detailChannels').innerHTML = chLinks.join(''); qs('blockChannels').hidden = false; }

      var meta = [];
      if (d.lastResearchedAt) meta.push('최근 조사 ' + String(d.lastResearchedAt).slice(0, 10).replace(/-/g, '.'));
      if (d.eligibilityStatus) meta.push(d.researchPending ? '정보 확인 중' : '평가 가능');
      qs('detailMeta').textContent = meta.join(' · ');
      qs('detailDisclaimer').textContent = d.disclaimer || '';
      if (d.researchPending) qs('detailPending').hidden = false;
    })
    .catch(function (e) { if (e !== '404') { var m = qs('detailMeta'); if (m) m.textContent = '정보를 불러오지 못했습니다.'; } });

  fetch('/api/chart/rankings/' + encodeURIComponent(id))
    .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
    .then(function (rk) {
      qs('detailRankingDisclaimer').textContent = rk.disclaimer || '';
      if (!rk.ranked) return;
      qs('rankingFigures').innerHTML =
        '<div class="fig score"><em>BuildIt Score</em><strong>' + num(rk.buildItScore) + '</strong></div>'
        + '<div class="fig"><em>' + esc(rk.cityDistrict || rk.province || '지역') + '</em><strong>#' + num(rk.regionRank) + '</strong>'
        + '<span>' + num(rk.regionTotal) + '명 중</span></div>'
        + '<div class="fig"><em>전국</em><strong>#' + num(rk.overallRank) + '</strong>'
        + '<span>' + num(rk.overallTotal) + '명 중</span></div>'
        + (rk.top100 ? '<div class="fig top100"><strong>BuildIt TOP100</strong></div>' : '');
      qs('detailRanking').hidden = false;
    })
    .catch(function () { /* 순위 없음: 영역 미표시 */ });
})();
