(function initLandingPreview(root, factory) {
  const api = factory();

  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  }

  if (!root || !root.document) return;

  root.LandingPreview = api;
  root.updatePreview = function updateLandingPreview() {
    return api.updatePreview(root.document);
  };
  root.selectSubject = function selectLandingSubject(button) {
    return api.selectSubject(root.document, button);
  };
  root.addEventListener('load', function initializeLanding() {
    root.updatePreview();
    api.initLandingMotion(root.document, root);
  });
})(typeof window !== 'undefined' ? window : null, function landingPreviewFactory() {
  const HERO_ROLES = Object.freeze([
    Object.freeze({ word: '강사', particle: '를' }),
    Object.freeze({ word: '원장', particle: '을' })
  ]);

  function formatTuition(rawValue) {
    const amount = Number.parseInt(String(rawValue || '').replace(/[^0-9]/g, ''), 10);
    if (!Number.isFinite(amount) || amount <= 0) return '—';
    return amount.toLocaleString('ko-KR') + '원';
  }

  function compactAcademyHandle(rawName) {
    const compact = String(rawName || '')
      .trim()
      .replace(/\s+/g, '')
      .replace(/[^\p{L}\p{N}-]/gu, '');
    return compact || 'my-academy';
  }

  function buildPreviewState(values) {
    const name = String(values && values.name || '').trim() || '내 학원';
    const slogan = String(values && values.slogan || '').trim() || '성장을 만드는 수업';
    const subjects = Array.isArray(values && values.subjects)
      ? values.subjects.map((subject) => String(subject || '').trim()).filter(Boolean)
      : [];

    return {
      name,
      slogan,
      subjectSummary: (subjects.length ? subjects : ['영어']).join(' · ') + ' 전문',
      tuition: formatTuition(values && values.tuition),
      publicHost: compactAcademyHandle(name) + '.feel2real.co.kr'
    };
  }

  function activeSubjectLabels(documentRef) {
    return Array.from(documentRef.querySelectorAll('#subjectBtns .subj-btn.active'))
      .map((button) => (button.dataset.subj || '').trim())
      .filter(Boolean);
  }

  function setText(documentRef, id, value) {
    const element = documentRef.getElementById(id);
    if (element) element.textContent = value;
  }

  function updatePreview(documentRef) {
    const nameInput = documentRef.getElementById('academyNameInput');
    const sloganInput = documentRef.getElementById('sloganInput');
    const tuitionInput = documentRef.getElementById('tuitionInput');
    if (!nameInput || !sloganInput || !tuitionInput) return null;

    const state = buildPreviewState({
      name: nameInput.value,
      slogan: sloganInput.value,
      subjects: activeSubjectLabels(documentRef),
      tuition: tuitionInput.value
    });

    setText(documentRef, 'previewLogoName', state.name);
    setText(documentRef, 'previewHeroName', state.name);
    setText(documentRef, 'previewSlogan', state.slogan);
    setText(documentRef, 'previewSubject', state.subjectSummary);
    setText(documentRef, 'previewTuition', state.tuition);
    setText(documentRef, 'previewUrl', state.publicHost);
    setText(documentRef, 'charCount', state.name.length + ' / 20');
    return state;
  }

  function selectSubject(documentRef, button) {
    if (!button || !button.classList) return null;
    button.classList.toggle('active');
    if (!documentRef.querySelector('#subjectBtns .subj-btn.active')) {
      button.classList.add('active');
    }
    return updatePreview(documentRef);
  }

  function roleForIndex(index) {
    const normalizedIndex = Math.abs(Number.isFinite(index) ? Math.trunc(index) : 0) % HERO_ROLES.length;
    return HERO_ROLES[normalizedIndex];
  }

  function playBrandSequence(word, windowRef) {
    word.classList.remove('is-resetting', 'is-playing');
    void word.offsetWidth;
    word.classList.add('is-playing');

    windowRef.setTimeout(function resetBrandSequence() {
      word.classList.remove('is-playing');
      word.classList.add('is-resetting');

      windowRef.setTimeout(function replayBrandSequence() {
        word.classList.remove('is-resetting');
        playBrandSequence(word, windowRef);
      }, 420);
    }, 6800);
  }

  function initLandingMotion(documentRef, windowRef) {
    if (!documentRef || !windowRef || typeof windowRef.matchMedia !== 'function') return;
    const reduceMotion = windowRef.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) return;

    Array.from(documentRef.querySelectorAll('.brand-word')).forEach(function initializeBrand(word, index) {
      windowRef.setTimeout(function startBrandSequence() {
        playBrandSequence(word, windowRef);
      }, index * 180);
    });

    const roleWord = documentRef.getElementById('heroRoleWord');
    const roleParticle = documentRef.getElementById('heroRoleParticle');
    if (!roleWord || !roleParticle) return;

    let roleIndex = 0;
    windowRef.setInterval(function rotateHeroRole() {
      roleIndex += 1;
      const role = roleForIndex(roleIndex);
      roleWord.classList.add('is-changing');
      roleParticle.classList.add('is-changing');
      windowRef.setTimeout(function replaceHeroRole() {
        roleWord.textContent = role.word;
        roleParticle.textContent = role.particle;
        roleWord.classList.remove('is-changing');
        roleParticle.classList.remove('is-changing');
      }, 220);
    }, 2200);
  }

  return {
    activeSubjectLabels,
    buildPreviewState,
    compactAcademyHandle,
    formatTuition,
    initLandingMotion,
    roleForIndex,
    selectSubject,
    updatePreview
  };
});
