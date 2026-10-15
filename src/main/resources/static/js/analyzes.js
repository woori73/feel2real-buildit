(function () {
  const form = document.getElementById('analysisSearchForm');
  const list = document.getElementById('analysisList');
  const count = document.getElementById('analysisCount');

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    loadAnalyses();
  });

  loadSubjects();
  loadAnalyses();

  async function loadAnalyses() {
    const params = new URLSearchParams();
    add(params, 'keyword', value('keyword'));
    add(params, 'schoolName', value('schoolName'));
    add(params, 'grade', value('grade'));
    add(params, 'semester', value('semester'));
    add(params, 'examType', value('examType'));
    add(params, 'subjectId', value('subjectId'));
    list.innerHTML = '<p class="muted">분석 자료를 불러오는 중...</p>';
    try {
      const res = await fetch(`/api/analyzes?${params.toString()}`);
      if (!res.ok) throw new Error('분석 자료 목록 조회 실패');
      const items = await res.json();
      count.textContent = `${items.length}개`;
      list.innerHTML = items.length ? items.map(card).join('') : '<p class="muted">조건에 맞는 분석 자료가 없습니다.</p>';
    } catch (_) {
      count.textContent = '';
      list.innerHTML = '<p class="error">분석 자료를 불러오지 못했습니다.</p>';
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

  function card(item) {
    return `<article class="lesson-card">
      <h3>${escapeHtml(item.title)}</h3>
      <p class="muted">${escapeHtml(item.schoolName)} · ${item.grade}학년 · ${escapeHtml(item.semester)} · ${escapeHtml(item.examType)}</p>
      <div class="badges"><span class="badge">${escapeHtml(item.subject?.nameKo || item.subject?.nameEn || '과목')}</span></div>
      <p><a class="btn btn-secondary" href="${escapeAttr(item.detailUrl)}">분석 보기</a></p>
    </article>`;
  }

  function value(id) { return document.getElementById(id).value.trim(); }
  function add(params, key, val) { if (val) params.set(key, val); }
  function escapeHtml(value) {
    return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
  }
  function escapeAttr(value) { return escapeHtml(value).replace(/`/g, '&#96;'); }
})();
