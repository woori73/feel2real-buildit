(function () {
  const form = document.getElementById('lessonSearchForm');
  const list = document.getElementById('lessonSearchList');
  const resultCount = document.getElementById('resultCount');

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    loadLessons();
  });

  loadSubjects();
  loadLessons();

  async function loadLessons() {
    const params = new URLSearchParams();
    add(params, 'subjectId', value('subjectId'));
    add(params, 'targetGroup', value('targetGroup'));
    add(params, 'lessonType', value('lessonType'));
    add(params, 'city', value('city'));
    add(params, 'district', value('district'));
    add(params, 'weekday', value('weekday'));
    add(params, 'maxPrice', value('maxPrice'));
    add(params, 'weeklyCount', value('weeklyCount'));
    list.innerHTML = '<p class="muted">검색 중...</p>';
    try {
      const res = await fetch(`/api/lessons/search?${params.toString()}`);
      if (!res.ok) throw new Error('검색 실패');
      const items = await res.json();
      resultCount.textContent = `${items.length}개`;
      list.innerHTML = items.length ? items.map(card).join('') : '<p class="muted">조건에 맞는 수업이 없습니다.</p>';
    } catch (e) {
      list.innerHTML = '<p class="error">수업 검색에 실패했습니다.</p>';
    }
  }

  async function loadSubjects() {
    const select = document.getElementById('subjectId');
    if (!select) return;
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

  // 정원·대상·유형은 비어 있을 수 있다. 값이 있는 항목만 한 줄로 모으고, 전부 없으면 줄을 생략한다.
  function lessonMeta(lesson) {
    const parts = [];
    if (lesson.maxCapacity != null) {
      parts.push(`정원 ${lesson.currentCount || 0}/${lesson.maxCapacity}명`);
    }
    if (lesson.targetGroup) parts.push(label(lesson.targetGroup));
    if (lesson.lessonType) parts.push(label(lesson.lessonType));
    return parts.length ? `<p>${parts.join(' · ')}</p>` : '';
  }

  function card(lesson) {
    // 정원 미정(null)은 무제한 접수다. `null <= 0`이 true라 그대로 두면 마감으로 표시된다.
    const unlimited = lesson.maxCapacity == null;
    const full = !unlimited && (lesson.full || lesson.remainingCapacity <= 0);
    return `<article class="lesson-card">
      <h3>${escapeHtml(lesson.lessonName)}</h3>
      <p class="muted">${escapeHtml(lesson.academyName)}</p>
      ${lesson.priceSummary ? `<p><strong>${escapeHtml(lesson.priceSummary)}</strong></p>` : ''}
      ${lesson.scheduleSummary ? `<p>${escapeHtml(lesson.scheduleSummary)}</p>` : ''}
      ${lessonMeta(lesson)}
      ${unlimited ? '' : `<p class="muted">남은 자리 ${lesson.remainingCapacity}명</p>`}
      <div style="display:flex;gap:8px;flex-wrap:wrap;">
        <a class="btn btn-secondary" href="/academies/${encodeURIComponent(lesson.academySlug)}">Academy 보기</a>
        <button class="btn btn-primary" ${full ? 'disabled' : ''} onclick="applyLesson(${lesson.lessonId}, this)">${full ? '정원 마감' : '신청하기'}</button>
      </div>
    </article>`;
  }

  // ─── 대화 선택 모달 ───────────────────────────────────────────────────────
  let _pendingLessonId = null;
  let _pendingButton = null;

  window.applyLesson = async function (lessonId, button) {
    const user = currentUser();
    if (!user || user.role !== 'STUDENT') {
      alert('학생 로그인 후 신청할 수 있습니다.');
      return;
    }
    _pendingLessonId = lessonId;
    _pendingButton = button;
    await openApplyModal();
  };

  async function openApplyModal() {
    const select = document.getElementById('conversationSelect');
    select.innerHTML = '<option value="">새 대화로 시작</option>';
    try {
      const res = await fetchAuth('/api/conversations');
      if (res.ok) {
        const conversations = await res.json();
        conversations.forEach((conv) => {
          const opt = document.createElement('option');
          opt.value = conv.conversationId;
          opt.textContent = conv.title || '(제목 없음)';
          select.appendChild(opt);
        });
      }
    } catch (_) {
      // 대화 목록 로드 실패 시 "새 대화로 시작"만 표시
    }
    const modal = document.getElementById('applyModal');
    modal.style.display = 'flex';
  }

  window.closeApplyModal = function () {
    document.getElementById('applyModal').style.display = 'none';
    _pendingLessonId = null;
    _pendingButton = null;
  };

  window.confirmApply = async function () {
    if (!_pendingLessonId) return;
    const conversationIdRaw = document.getElementById('conversationSelect').value;
    const conversationId = conversationIdRaw ? Number(conversationIdRaw) : null;
    const confirmBtn = document.getElementById('applyConfirmBtn');
    confirmBtn.disabled = true;
    confirmBtn.textContent = '신청 중...';
    try {
      const res = await fetchAuth('/api/lesson-applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lessonId: _pendingLessonId, conversationId })
      });
      if (!res.ok) throw new Error(await res.text());
      document.getElementById('applyModal').style.display = 'none';
      const body = await res.json();
      if (body.conversationId) {
        location.href = '/conversation/dashboard?id=' + body.conversationId;
      } else {
        await loadLessons();
      }
    } catch (e) {
      alert('신청 실패: 정원이 마감되었거나 이미 신청한 수업입니다.');
    } finally {
      confirmBtn.disabled = false;
      confirmBtn.textContent = '신청하기';
      _pendingLessonId = null;
      _pendingButton = null;
    }
  };

  function currentUser() { try { return JSON.parse(sessionStorage.getItem('currentUser') || 'null'); } catch (_) { return null; } }
  function value(id) { return document.getElementById(id).value.trim(); }
  function add(params, key, val) { if (val) params.set(key, val); }
  function label(value) { return ({ SUBSCRIPTION: '구독형', SHORT_TERM: '단기형', ELEMENTARY: '초등학생', MIDDLE: '중학생', HIGH: '고등학생', UNIVERSITY: '대학생', ADULT: '성인' }[value]) || value; }
  function escapeHtml(value) { return String(value || '').replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[ch])); }
})();
