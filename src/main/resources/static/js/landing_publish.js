/**
 * 랜딩 builder-try 폼 → /builder 진입 배선.
 *
 * 흐름: 폼 수집 → 로그인 확인
 *      → (비로그인) 폼값 sessionStorage 저장 + /register?returnUrl=/builder 게이트
 *      → (로그인 tutor + 기존 Academy 있음) 기존 공개 URL로 이동
 *      → (로그인 tutor + 기존 Academy 없음) 폼값 sessionStorage 저장 + /builder 이동.
 *
 * 실제 create(POST /api/tutor/academy JSON)는 여기서 하지 않는다 — /builder 페이지의
 * '발행' 버튼(academy_builder.js)이 보관된 폼값으로 실행한다.
 */
(function () {
  const PENDING_KEY = 'pendingAcademyPublish';
  const PENDING_PREVIEW_KEY = 'pendingAcademyPreview';
  const BUILDER_URL = '/builder';
  const FUTURE_SECTION_IDS = [
    'sec-reviews',
    'sec-curriculum',
    'sec-effect',
    'sec-digitaltwin',
    'sec-market',
    'sec-payment'
  ];

  // 시/도만 선택(구/군 미선택=ALL). setup 화면과 동일한 시/도 목록.
  const CITIES = ['서울시', '부산시', '대구시', '인천시', '광주시', '대전시', '울산시', '세종시',
    '경기도', '강원도', '충청북도', '충청남도', '전라북도', '전라남도', '경상북도', '경상남도', '제주도'];

  // 폼 과목 버튼(한글) → subjectId. /api/subjects의 nameKo와 매칭.
  let subjectNameToId = {};

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    injectTargetAndRegion();
    await loadSubjectMap();
    wirePublishButton();
  }

  /** 대상(targetGroups) + 지역(1개) selector를 폼에 주입한다(발행 필수값). */
  function injectTargetAndRegion() {
    const anchor = document.getElementById('subjectBtns');
    if (!anchor) return;
    const subjectBlock = anchor.closest('.builder-field');
    if (!subjectBlock) return;

    const targets = [
      ['ELEMENTARY', '초등'], ['MIDDLE', '중등'], ['HIGH', '고등'],
      ['UNIVERSITY', '대학'], ['ADULT', '성인']
    ];
    const wrap = document.createElement('div');
    wrap.className = 'builder-field builder-audience-field';
    wrap.innerHTML = `
      <div class="builder-audience-targets">
        <label><i class="fa-solid fa-users" aria-hidden="true"></i>대상 <small>복수 선택</small></label>
        <div id="targetBtns">
          ${targets.map(([v, ko], i) =>
            `<button type="button" class="subj-btn${i === 1 ? ' active' : ''}" data-target="${v}" onclick="this.classList.toggle('active')">${ko}</button>`
          ).join('')}
        </div>
      </div>
      <div class="builder-region-field">
        <label for="regionCity"><i class="fa-solid fa-location-dot" aria-hidden="true"></i>지역</label>
        <select id="regionCity">
          ${CITIES.map((c) => `<option value="${c}">${c}</option>`).join('')}
        </select>
      </div>
    `;
    subjectBlock.insertAdjacentElement('afterend', wrap);
  }

  async function loadSubjectMap() {
    try {
      const res = await fetch('/api/subjects', { credentials: 'same-origin' });
      if (!res.ok) return;
      const subjects = await res.json();
      subjectNameToId = {};
      subjects.forEach((s) => {
        if (s.nameKo) subjectNameToId[s.nameKo] = s.subjectId;
      });
    } catch (_) { /* 발행 시 검증에서 걸러진다 */ }
  }

  function wirePublishButton() {
    const btn = document.getElementById('publishBtn');
    if (btn) btn.addEventListener('click', onPublishClick);
  }

  /** 폼 값을 수집해 발행 요청 객체로 만든다. 불충분하면 null + 안내. */
  function collectRequest() {
    const name = (val('academyNameInput') || '').trim();
    const intro = (val('sloganInput') || '').trim();
    const selectedSubjects = selectedSubjectLabels();
    const subjectEntries = selectedSubjects
      .map((label) => ({ label, subjectId: subjectNameToId[label] }))
      .filter((entry) => entry.subjectId != null);
    const targetGroups = Array.from(document.querySelectorAll('#targetBtns .subj-btn.active'))
      .map((el) => el.dataset.target);
    const city = val('regionCity');
    const monthlyTuition = monthlyTuitionLabel(val('tuitionInput'));

    if (!name) return fail('학원 이름을 입력해 주세요.');
    if (!intro) return fail('학원 슬로건(소개)을 입력해 주세요.');
    if (subjectEntries.length === 0) return fail('주력 과목을 1개 이상 선택해 주세요.');
    if (subjectEntries.length !== selectedSubjects.length) return fail('선택한 과목 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
    if (targetGroups.length === 0) return fail('대상을 1개 이상 선택해 주세요.');
    if (!city) return fail('지역을 선택해 주세요.');

    return {
      request: {
        name,
        intro,
        subjectIds: subjectEntries.map((entry) => Number(entry.subjectId)),
        targetGroups,
        regions: [{ city, district: null }],
        pageSettings: {
          builderDraft: { monthlyTuition },
          sectionVisibility: futureSectionsOff()
        }
      },
      preview: {
        name,
        intro,
        subjects: subjectEntries.map((entry) => ({
          subjectId: Number(entry.subjectId),
          nameKo: entry.label,
          nameEn: entry.label
        })),
        targetGroups,
        regions: [{ city, district: null, displayName: city }],
        lessons: [],
        pageSettings: {
          builderDraft: { monthlyTuition },
          sectionVisibility: futureSectionsOff()
        }
      }
    };
  }

  async function onPublishClick() {
    const user = await getUser();
    if (user && user.role === 'TUTOR') {
      const existingAcademy = await getMyAcademy();
      if (existingAcademy) {
        clearPending();
        location.href = publicAcademyUrl(existingAcademy);
        return;
      }
    }

    const collected = collectRequest();
    if (!collected) return;
    const { request, preview } = collected;

    if (!user) {
      // 게이트: 폼값 보관 후 회원가입으로. 복귀(returnUrl=/builder) 시 /builder에서 발행.
      savePending(request, preview);
      location.href = '/register?returnUrl=' + encodeURIComponent(BUILDER_URL);
      return;
    }
    if (user.role !== 'TUTOR') {
      toast('학원 발행은 강사(TUTOR) 계정만 가능합니다. 강사로 가입해 주세요.', 'fa-circle-info');
      return;
    }

    // 실제 create는 /builder의 발행 버튼에서. 여기선 폼값 보관 후 이동.
    savePending(request, preview);
    location.href = BUILDER_URL;
  }

  // --- auth ---
  async function getUser() {
    const cached = readCachedUser();
    if (cached && cached.role) return cached;
    try {
      const res = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (!res.ok) return null;
      const user = await res.json();
      try { sessionStorage.setItem('currentUser', JSON.stringify(user)); } catch (_) {}
      return user;
    } catch (_) {
      return null;
    }
  }

  function readCachedUser() {
    try { return JSON.parse(sessionStorage.getItem('currentUser') || 'null'); } catch (_) { return null; }
  }

  async function getMyAcademy() {
    try {
      const res = await fetch('/api/tutor/academy/me', { credentials: 'same-origin' });
      if (res.status === 404) return null;
      if (!res.ok) return null;
      return await res.json();
    } catch (_) {
      return null;
    }
  }

  function publicAcademyUrl(academy) {
    // prd: 서버 계산 publicUrl(https://{handle}.feel2real.co.kr/), local/dev/stg: /academies/{slug}.
    return academy.publicUrl || ('/academies/' + encodeURIComponent(academy.slug));
  }

  // --- pending 저장소 (/builder에서 readPending/clearPending) ---
  function savePending(request, preview) {
    try { sessionStorage.setItem(PENDING_KEY, JSON.stringify(request)); } catch (_) {}
    try { sessionStorage.setItem(PENDING_PREVIEW_KEY, JSON.stringify(preview || request)); } catch (_) {}
  }
  function futureSectionsOff() {
    return Object.fromEntries(FUTURE_SECTION_IDS.map((id) => [id, false]));
  }
  function clearPending() {
    try { sessionStorage.removeItem(PENDING_KEY); } catch (_) {}
    try { sessionStorage.removeItem(PENDING_PREVIEW_KEY); } catch (_) {}
  }

  // --- helpers ---
  function val(id) {
    const el = document.getElementById(id);
    return el ? el.value : '';
  }
  function monthlyTuitionLabel(rawValue) {
    const amount = Number.parseInt(String(rawValue || '').replace(/[^0-9]/g, ''), 10);
    if (!Number.isFinite(amount) || amount <= 0) return '—';
    return amount.toLocaleString('ko-KR') + '원';
  }
  function selectedSubjectLabels() {
    return Array.from(document.querySelectorAll('#subjectBtns .subj-btn.active'))
      .map((el) => (el.dataset.subj || '').trim())
      .filter(Boolean);
  }
  function toast(msg, icon) {
    if (typeof window.showToast === 'function') window.showToast(msg, icon);
    else alert(msg);
  }
  function fail(msg) {
    toast(msg, 'fa-triangle-exclamation');
    return null;
  }
})();
