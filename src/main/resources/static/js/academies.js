(function () {
  const form = document.getElementById('academySearchForm');
  const list = document.getElementById('academyList');
  const count = document.getElementById('academyCount');

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    loadAcademies();
  });

  loadSubjects();
  loadAcademies();

  async function loadAcademies() {
    const params = new URLSearchParams();
    add(params, 'subjectId', value('subjectId'));
    add(params, 'targetGroup', value('targetGroup'));
    add(params, 'city', value('city'));
    add(params, 'district', value('district'));
    list.innerHTML = '<p class="muted">Academy를 불러오는 중...</p>';
    try {
      const res = await fetch(`/api/academies?${params.toString()}`);
      if (!res.ok) throw new Error('Academy 목록 조회 실패');
      const items = await res.json();
      count.textContent = `${items.length}개`;
      list.innerHTML = items.length ? items.map(card).join('') : '<p class="muted">조건에 맞는 Academy가 없습니다.</p>';
    } catch (_) {
      count.textContent = '';
      list.innerHTML = '<p class="error">Academy 목록을 불러오지 못했습니다.</p>';
    }
  }

  async function loadSubjects() {
    const select = document.getElementById('subjectId');
    try {
      const res = await fetch('/api/subjects');
      if (!res.ok) throw new Error();
      const subjects = await res.json();
      select.innerHTML = '<option value="">전체</option>' + subjects
        .map((subject) => `<option value="${subject.subjectId}">${escapeHtml(subject.nameKo || subject.nameEn)}</option>`)
        .join('');
    } catch (_) {
      select.innerHTML = '<option value="">전체</option>';
    }
  }

  function card(academy) {
    const subjects = (academy.subjects || []).map((subject) => subject.nameKo || subject.nameEn).filter(Boolean);
    const targets = (academy.targetGroups || []).map(label);
    const regions = (academy.regions || []).map((region) => region.displayName).filter(Boolean);
    return `<article class="lesson-card">
      <img class="logo" src="${escapeAttr(academy.logoUrl)}" alt="">
      <h3>${escapeHtml(academy.name)}</h3>
      <p class="muted">${escapeHtml(regions.join(', ') || '지역 미등록')}</p>
      <div class="badges">${subjects.map((subject) => `<span class="badge">${escapeHtml(subject)}</span>`).join('')}</div>
      <div class="badges">${targets.map((target) => `<span class="badge">${escapeHtml(target)}</span>`).join('')}</div>
      <p><a class="btn btn-secondary" href="/academies/${encodeURIComponent(academy.slug)}">Academy 보기</a></p>
    </article>`;
  }

  function value(id) { return document.getElementById(id).value.trim(); }
  function add(params, key, val) { if (val) params.set(key, val); }
  function label(value) {
    return ({ ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생', UNIVERSITY: '대학생', ADULT: '성인' }[value]) || value;
  }
  function escapeHtml(value) {
    return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
  }
  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }
})();
