(function () {
  const lessonId = Number(location.pathname.split('/').filter(Boolean).pop());
  const lessonDetail = document.getElementById('lessonDetail');
  const studentList = document.getElementById('studentList');
  const editLessonProductLink = document.getElementById('editLessonProductLink');

  init();

  async function init() {
    if (!lessonId) {
      renderError('수업 ID를 확인할 수 없습니다.');
      return;
    }
    if (editLessonProductLink) {
      editLessonProductLink.href = '/tutor/academy/lessons/editor?lessonId='
        + encodeURIComponent(lessonId);
    }
    await Promise.all([loadLesson(), loadApplications()]);
  }

  async function loadLesson() {
    try {
      const res = await fetchAuth('/api/tutor/academy/lessons/' + lessonId);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const lesson = await res.json();
      renderLesson(lesson);
    } catch (_) {
      renderError('수업 정보를 불러오지 못했습니다.');
    }
  }

  async function loadApplications() {
    try {
      const res = await fetchAuth('/api/tutor/lessons/' + lessonId + '/applications');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const items = await res.json();
      studentList.innerHTML = items.length ? items.map(studentCard).join('') : '<p class="muted">이 수업을 신청한 학생이 아직 없습니다.</p>';
      bindStudents();
    } catch (_) {
      studentList.innerHTML = '<p class="error">신청 목록을 불러오지 못했습니다.</p>';
    }
  }

  function renderLesson(lesson) {
    lessonDetail.innerHTML = `
      <div class="lesson-detail-head">
        <div>
          <p class="eyebrow">${label(lesson.targetGroup || 'HIGH')} · ${label(lesson.lessonType)} · ${label(lesson.status)}</p>
          <h1>${escapeHtml(lesson.name)}</h1>
          <p class="muted">${escapeHtml(lesson.description || '설명이 없습니다.')}</p>
        </div>
        <div class="lesson-detail-actions">
          <span class="badge">정원 ${lesson.currentCount || 0}/${lesson.maxCapacity}명</span>
          <a class="btn btn-secondary" href="/lessons/${lessonId}/community">수업 커뮤니티</a>
        </div>
      </div>
      <div class="lesson-detail-grid">
        <div><strong>가격</strong><p>${escapeHtml(lesson.priceSummary)}</p></div>
        <div><strong>일정</strong><p>${escapeHtml(lesson.scheduleSummary)}</p></div>
        <div><strong>요일</strong><p>${formatWeekdays(lesson.weekdays)}</p></div>
        <div><strong>기간</strong><p>${formatPeriod(lesson)}</p></div>
      </div>`;
  }

  function renderError(message) {
    lessonDetail.innerHTML = `<p class="error">${escapeHtml(message)}</p><p><a class="btn btn-ghost" href="/tutor/academy">내 Academy로 돌아가기</a></p>`;
  }

  function studentCard(item) {
    const clickable = Boolean(item.conversationId);
    return `<article class="lesson-card student-application-card ${clickable ? 'lesson-card-clickable' : ''}"
        ${clickable ? `data-conversation-id="${item.conversationId}" role="button" tabindex="0"` : ''}
        aria-label="${escapeHtml(item.studentName)} 학생 대화 보기">
      <h3>${escapeHtml(item.studentName)}</h3>
      <p class="muted">${escapeHtml(item.conversationTitle || item.lessonName || '')}</p>
      <p>상태: ${labelApplicationStatus(item.status)}</p>
      <p class="muted">신청일 ${escapeHtml(formatDate(item.appliedAt))}</p>
      <p>${clickable ? '대화로 이동 →' : '연결된 대화 없음'}</p>
    </article>`;
  }

  function bindStudents() {
    document.querySelectorAll('[data-conversation-id]').forEach((card) => {
      const open = () => location.href = '/conversation/dashboard?id=' + card.dataset.conversationId;
      card.addEventListener('click', open);
      card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          open();
        }
      });
    });
  }

  function formatWeekdays(weekdays) {
    const values = weekdays || [];
    return values.length ? values.map(label).join(', ') : '협의';
  }

  function formatPeriod(lesson) {
    if (lesson.startDate && lesson.endDate) return `${lesson.startDate} ~ ${lesson.endDate}`;
    if (lesson.durationWeeks) return `${lesson.durationWeeks}주`;
    if (lesson.durationMonths) return `${lesson.durationMonths}개월`;
    return '협의';
  }

  function labelApplicationStatus(status) {
    return ({ APPROVED: '신청 완료', CANCELED: '취소됨' }[status]) || status;
  }

  function label(value) {
    return ({
      SUBSCRIPTION: '구독형', SHORT_TERM: '단기형',
      DRAFT: '초안', PUBLISHED: '공개', HIDDEN: '숨김',
      ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생', UNIVERSITY: '대학생', ADULT: '성인',
      MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목', FRIDAY: '금', SATURDAY: '토', SUNDAY: '일'
    }[value]) || value;
  }

  function formatDate(dateStr) { if (!dateStr) return ''; return new Date(dateStr).toLocaleDateString('ko-KR'); }
  function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
})();
