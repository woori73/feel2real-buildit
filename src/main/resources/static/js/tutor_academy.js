(function () {
  const emptyState = document.getElementById('emptyState');
  const academyView = document.getElementById('academyView');
  const hero = document.getElementById('academyHero');
  const lessonList = document.getElementById('lessonList');
  const lmsCard = document.getElementById('lmsCard');
  const lmsLink = document.getElementById('lmsLink');
  const deleteAcademyButton = document.getElementById('deleteAcademyButton');
  let academy = null;

  init();

  async function init() {
    deleteAcademyButton?.addEventListener('click', deleteAcademy);
    await loadAcademy();
  }

  async function loadAcademy() {
    const res = await fetchAuth('/api/tutor/academy/me');
    if (res.status === 204) {
      academy = null;
      emptyState.classList.remove('hidden');
      academyView.classList.add('hidden');
      deleteAcademyButton?.classList.add('hidden');
      return;
    }
    if (!res.ok) throw new Error('Academy 조회 실패');
    academy = await res.json();
    renderAcademy(academy);
  }

  function renderAcademy(data) {
    emptyState.classList.add('hidden');
    academyView.classList.remove('hidden');
    deleteAcademyButton?.classList.remove('hidden');
    hero.innerHTML = `
      ${data.coverUrl ? `<img class="cover" src="${data.coverUrl}" alt="">` : ''}
      <div class="hero-content">
        <img class="logo" src="${data.logoUrl}" alt="${escapeHtml(data.name)} 로고">
        <h1>${escapeHtml(data.name)}</h1>
        <p>${escapeHtml(data.intro)}</p>
        <div class="badges">${badges(data)}</div>
        <p><a class="btn btn-ghost" href="/academies/${data.slug}">공개 페이지 보기</a></p>
      </div>`;
    renderLmsEntry(data.handle);
    renderLessons(data.lessons || []);
  }

  /**
   * LMS 이동 카드. handle이 없으면 /lms/{handle} 주소가 성립하지 않으므로(404) 카드를 감춘다.
   * 공개주소(handle)는 /tutor/academy/setup에서 설정한다.
   */
  function renderLmsEntry(handle) {
    if (!lmsCard || !lmsLink) return;
    if (!handle) {
      lmsCard.classList.add('hidden');
      return;
    }
    lmsLink.href = '/lms/' + encodeURIComponent(handle);
    lmsCard.classList.remove('hidden');
  }

  function badges(data) {
    const subjects = (data.subjects || []).map((s) => s.nameKo || s.nameEn);
    const regions = (data.regions || []).map((r) => r.displayName);
    return subjects.concat(data.targetGroups || [], regions)
      .map((v) => `<span class="badge">${escapeHtml(label(v))}</span>`).join('');
  }

  /**
   * 수업 운영 진입 목록. 판매용 카드(가격·정원·수정/삭제 메뉴)는 LMS 강좌관리로 옮겼고,
   * 여기엔 /tutor/lesson/{id}(신청·수강생 관리, 수업 커뮤니티)로 가는 링크만 남긴다.
   * <a>라서 키보드 접근은 브라우저가 기본 제공한다 — 별도 role/tabindex/keydown 처리가 필요 없다.
   */
  function renderLessons(lessons) {
    lessonList.innerHTML = lessons.length
      ? lessons.map(lessonLink).join('')
      : '<p class="muted">아직 등록된 수업이 없습니다. LMS 강좌관리에서 등록하세요.</p>';
  }

  function lessonLink(lesson) {
    return `<a class="lesson-card lesson-card-clickable" href="/tutor/lesson/${lesson.lessonId}">
      <h3>${escapeHtml(lesson.name)}</h3>
      <p class="muted">신청·수강생 관리</p>
    </a>`;
  }

  async function deleteAcademy() {
    if (!academy) return;
    const ok = confirm(
      `'${academy.name}' Academy를 삭제할까요?\n` +
      '삭제하면 공개 페이지와 수업 상품이 사라지며 되돌릴 수 없습니다.'
    );
    if (!ok) return;
    try {
      const res = await fetchAuth('/api/tutor/academy', { method: 'DELETE' });
      if (res.status === 409) {
        alert('신청/수강생이 있는 Academy는 삭제할 수 없습니다.');
        return;
      }
      if (!res.ok) throw new Error(await res.text());
      alert('Academy가 삭제되었습니다.');
      await loadAcademy();
    } catch (_) {
      alert('Academy 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    }
  }

  // hasLessonApplications/findLesson은 카드 메뉴의 수업 삭제 전용이었다. 삭제는 수업 상품 전용
  // 편집기(lesson_product_editor.js)가 계속 담당하므로 여기서는 제거한다.

  function label(value) {
    return ({ SUBSCRIPTION: '구독형', SHORT_TERM: '단기형', DRAFT: '초안', PUBLISHED: '공개', HIDDEN: '숨김', ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생', UNIVERSITY: '대학생', ADULT: '성인' }[value]) || value;
  }
  function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
})();
