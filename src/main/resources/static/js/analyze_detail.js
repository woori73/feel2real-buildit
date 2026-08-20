(function () {
  const id = location.pathname.split('/').filter(Boolean).pop();
  loadDetail();

  async function loadDetail() {
    try {
      const res = await fetch(`/api/analyzes/${encodeURIComponent(id)}`);
      if (!res.ok) throw new Error('분석 자료 조회 실패');
      const item = await res.json();
      document.title = item.title + ' | 학교 시험 분석';
      document.getElementById('analysisTitle').textContent = item.title;
      document.getElementById('analysisMeta').textContent = `${item.schoolName} · ${item.grade}학년 · ${item.semester} · ${item.examType}`;
      document.getElementById('analysisBadges').innerHTML = `<span class="badge">${escapeHtml(item.subject?.nameKo || item.subject?.nameEn || '과목')}</span>`;
      document.getElementById('analysisFrame').src = item.contentUrl;
    } catch (_) {
      document.getElementById('analysisTitle').textContent = '분석 자료를 찾을 수 없습니다.';
      document.getElementById('analysisMeta').textContent = '공개 중이 아니거나 삭제된 분석 자료입니다.';
      document.getElementById('analysisFrame').remove();
    }
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch]));
  }
})();
