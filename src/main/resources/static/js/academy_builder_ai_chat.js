/**
 * BuildIt P1 — Academy Builder AI 채팅 패널 (Qoder 담당).
 *
 * 계약: docs/BUILDIT_P1_API_CONTRACT_AI_CHAT.md (Muse 확정본).
 *   POST /api/tutor/academy/ai/chat                 → { reply, proposal|null, sessionId, needsApproval }
 *   POST /api/tutor/academy/ai/chat/apply            → { versionId, appliedAt }
 *   GET  /api/tutor/academy/ai/chat/versions         → [{ id, versionNo, createdBy, createdAt, note }]
 *   POST /api/tutor/academy/ai/chat/versions/{id}/restore → { versionId, appliedAt }
 * academyId는 보내지 않는다 — 서버가 인증된 principal로 결정한다.
 *
 * ⚠ 미리보기·발행·뷰 토글(PC/태블릿/모바일 390px)은 기존 구현을 그대로 쓴다
 *   (academy_builder.js + academy_builder.html 의 setView). 이 파일은 재정의하지 않는다.
 * ⚠ academy_builder.js 는 IIFE 라 내부를 호출할 수 없다. 적용/복원은 서버가 DB를 바꾸므로
 *   성공 후 새로고침으로 SSR 모델을 다시 읽는다(로컬 currentPreview 를 부분 갱신하면
 *   서버 상태와 갈라진다).
 * ⚠ XSS: 서버가 내려준 문자열은 innerHTML 에 넣지 않는다 — textContent 만 사용한다
 *   (newstart-chat.js 의 기존 규칙과 동일).
 */
(function () {
  'use strict';

  var API = {
    chat: '/api/tutor/academy/ai/chat',
    apply: '/api/tutor/academy/ai/chat/apply',
    versions: '/api/tutor/academy/ai/chat/versions',
    create: '/api/tutor/academy/ai/chat/create',
    createApply: '/api/tutor/academy/ai/chat/create/apply'
  };

  /**
   * 생성 세션 보관 키. 새로고침(P0 미리보기 핸드오프) 뒤에도 승인 카드를 복원해야 해서
   * sessionStorage 에 둔다. pendingAcademyPreview 는 academy_builder.js 가 읽는 기존 키다 —
   * 같은 문자열을 써야 기존 P0 미리보기 렌더러가 초안을 그대로 그린다.
   */
  var CREATE_SESSION_KEY = 'aichatCreateSession';
  var PENDING_PREVIEW_KEY = 'pendingAcademyPreview';
  var BUILDER_URL = '/builder';

  /**
   * P0 미리보기가 읽는 preview 골격 (academy_builder.js previewFromRequest 실측본과 동일 형태).
   * sectionOrder 는 DEFAULT_SECTION_ORDER, FUTURE 섹션은 기본 OFF — 기존 상수를 그대로 옮긴다.
   */
  var DEFAULT_SECTION_ORDER = [
    'sec-hero', 'sec-featured', 'sec-intro', 'sec-principal', 'sec-teachers', 'sec-philosophy',
    'sec-videos', 'sec-minilectures', 'sec-reviews', 'sec-lessons', 'sec-curriculum',
    'sec-effect', 'sec-digitaltwin', 'sec-market', 'sec-payment', 'sec-cta', 'sec-social'
  ];
  var FUTURE_SECTION_IDS = ['sec-reviews', 'sec-curriculum', 'sec-effect', 'sec-market', 'sec-payment'];

  /**
   * 표시용 라벨 — 2026-10-11 계약 v2(allowlist 정합본) 기준.
   *   intro            = Academy 최상위 필드(엔티티 intro 컬럼). builderDraft 아님.
   *   principalCareer  = 단일 키(약력은 개행 결합 문자열, academy_builder.js:2383).
   *   heroAccent / brandPrimary / brandAccent = allowlist 에서 제거됨 → 제안에 안 나온다.
   * 한글명은 기존 렌더러 문구를 그대로 쓴다(HERO_STATS, academy_sections.js:745-747).
   * 여기에 없는 키는 라벨을 지어내지 않고 키명을 그대로 노출한다.
   */
  var FIELD_LABELS = {
    intro: '학원 소개',
    monthlyTuition: '월 수강료',
    scoreImprovement: '성적 향상률',
    operationYears: '운영 경력',
    principalDesc: '원장 소개',
    principalQuote: '원장 한마디',
    principalCareer: '원장 약력',
    philosophyItem1: '교육철학 1',
    philosophyItem2: '교육철학 2',
    philosophyItem3: '교육철학 3',
    philosophyItem4: '교육철학 4',
    ctaPhone: '상담 전화',
    ctaKakaoUrl: '카카오톡 상담 링크',
    // 소개(notes) 섹션 — academy_sections.js:240-247 렌더러 역할 그대로(키워드/제목/본문).
    introHeading: '소개 키워드',
    introMotto: '소개 제목',
    introBodySecondary: '소개 본문'
  };

  var state = {
    sessionId: null,
    busy: false,
    pending: null,
    restored: false,
    mode: 'modify',
    createSession: null
  };

  var el = {};

  function init() {
    el.panel = document.getElementById('builderAiChatPanel');
    el.launcher = document.getElementById('builderAiChatLauncher');
    if (!el.panel) return;

    el.close = document.getElementById('builderAiChatClose');
    el.newSession = document.getElementById('builderAiChatNew');
    el.tabChat = document.getElementById('builderAiChatTabChat');
    el.tabVersions = document.getElementById('builderAiChatTabVersions');
    el.chatView = document.getElementById('builderAiChatView');
    el.versionsView = document.getElementById('builderAiChatVersionsView');
    el.list = document.getElementById('builderAiChatList');
    el.empty = document.getElementById('builderAiChatEmpty');
    el.emptyCreate = document.getElementById('builderAiChatEmptyCreate');
    el.form = document.getElementById('builderAiChatForm');
    el.input = document.getElementById('builderAiChatInput');
    el.send = document.getElementById('builderAiChatSend');
    el.status = document.getElementById('builderAiChatStatus');
    el.versionList = document.getElementById('builderAiChatVersionList');
    el.versionEmpty = document.getElementById('builderAiChatVersionEmpty');
    el.versionStatus = document.getElementById('builderAiChatVersionStatus');
    el.modeModify = document.getElementById('builderAiChatModeModify');
    el.modeCreate = document.getElementById('builderAiChatModeCreate');
    el.nameField = document.getElementById('builderAiChatNameField');
    el.nameInput = document.getElementById('builderAiChatName');

    // 기존 홈페이지가 이미 있으면 신규 생성은 서버가 400 으로 막는다 — 미리 안내만 하고
    // 생성 모드 진입 자체는 허용하지 않는다(중복 생성 시도 방지).
    if (hasExistingAcademy()) {
      if (el.modeCreate) {
        el.modeCreate.disabled = true;
        el.modeCreate.title = '이미 홈페이지가 있습니다. 채팅 수정을 사용하세요.';
      }
    }
    if (el.modeModify) el.modeModify.addEventListener('click', function () { setMode('modify'); });
    if (el.modeCreate) el.modeCreate.addEventListener('click', function () { setMode('create'); });

    if (el.launcher) {
      el.launcher.hidden = false;
      el.launcher.addEventListener('click', function () { openPanel(); });
    }
    if (el.close) el.close.addEventListener('click', closePanel);
    if (el.newSession) el.newSession.addEventListener('click', startNewSession);
    if (el.tabChat) el.tabChat.addEventListener('click', function () { switchTab('chat'); });
    if (el.tabVersions) el.tabVersions.addEventListener('click', function () { switchTab('versions'); });

    if (el.form) {
      el.form.addEventListener('submit', function (event) {
        event.preventDefault();
        sendMessage();
      });
    }
    if (el.input) {
      el.input.addEventListener('input', autoGrow);
      // Enter 전송 / Shift+Enter 줄바꿈 — newstart-chat.js 와 동일 규칙.
      el.input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && !event.shiftKey) {
          event.preventDefault();
          sendMessage();
        }
      });
    }

    restoreCreateSession();

    // entry 페이지에서 샘플을 선택하고 넘어온 경우 AI 채팅에 자동 입력
    try {
      var entrySample = sessionStorage.getItem('buildit_entry_sample');
      if (entrySample) {
        sessionStorage.removeItem('buildit_entry_sample');
        // 템플릿 ID를 state에 저장하여 생성 요청 시 함께 전송
        state.pendingTemplateId = entrySample;
        var sampleNames = {
          'tmpl-open-school': '오픈스쿨 템플릿(종합학원형)',
          'tmpl-demo-school': '데모스쿨 템플릿(모바일 중심형)',
          'tmpl-minimal-landing': '미니멀 랜딩(소규모 학원형)',
          'comp-bundle': '컴포넌트 조합(커스텀형)'
        };
        var sampleName = sampleNames[entrySample] || entrySample;
        openPanel();
        if (typeof switchTab === 'function') switchTab('chat');
        var modeCreate = document.getElementById('builderAiChatModeCreate');
        if (modeCreate) modeCreate.click();
        if (el.input) {
          el.input.value = sampleName + ' 스타일로 학원 홈페이지를 만들어줘';
          if (typeof autoGrow === 'function') autoGrow();
        }
        var nameInput = document.getElementById('builderAiChatName');
        if (nameInput) nameInput.focus();
        setStatus('선택한 샘플이 입력되었습니다. 학원 이름을 입력하고 전송하세요.');
      }
    } catch (e) {}
  }

  /* ── 신규 홈페이지 생성 (계약 v3 §5·§6) ─────────────────────────────── */

  /** academy_builder.js 의 readSsrAcademy 와 같은 신호로 기존 홈페이지 존재를 판정한다. */
  function hasExistingAcademy() {
    var source = document.getElementById('pageContent');
    return !!(source && source.dataset && source.dataset.academySlug);
  }

  function setMode(mode) {
    // 요청 진행 중에는 모드를 바꾸지 않는다 — in-flight 응답이 다른 모드에 붙는 race 방지.
    if (state.busy) return;
    state.mode = mode === 'create' ? 'create' : 'modify';
    var create = state.mode === 'create';
    if (el.modeModify) el.modeModify.classList.toggle('is-active', !create);
    if (el.modeCreate) el.modeCreate.classList.toggle('is-active', create);
    if (el.nameField) el.nameField.hidden = !create;
    if (el.input) {
      el.input.placeholder = create
        ? '예: 중학생 대상 영어학원 홈페이지 만들어 줘. 파란색 계열로'
        : '예: 원장 소개를 더 따뜻하게 다듬어줘';
      el.input.focus();
    }
    updateEmptyHint();
  }

  /** 빈 목록 안내를 모드에 맞게 교차 노출한다(메시지가 있으면 둘 다 숨긴다). */
  function updateEmptyHint() {
    var hasMsg = !!(el.list && el.list.querySelector('.aichat-msg'));
    var create = state.mode === 'create';
    if (el.empty) el.empty.hidden = hasMsg || create;
    if (el.emptyCreate) el.emptyCreate.hidden = hasMsg || !create;
  }

  /** 메시지·카드가 붙으면 빈 안내를 모두 숨긴다. */
  function hideEmptyHints() {
    if (el.empty) el.empty.hidden = true;
    if (el.emptyCreate) el.emptyCreate.hidden = true;
  }

  function readCreateSession() {
    try {
      var raw = window.sessionStorage.getItem(CREATE_SESSION_KEY);
      if (!raw) return null;
      var session = JSON.parse(raw);
      return session && session.draft ? session : null;
    } catch (_) {
      return null;
    }
  }

  function writeCreateSession(session) {
    state.createSession = session;
    try {
      if (session) window.sessionStorage.setItem(CREATE_SESSION_KEY, JSON.stringify(session));
      else window.sessionStorage.removeItem(CREATE_SESSION_KEY);
    } catch (_) { /* 저장 실패해도 인메모리 세션으로 동작한다. */ }
  }

  /**
   * 생성 초안을 기존 P0 미리보기가 읽는 형태로 옮겨 담는다 (계약 §5 → academy_builder.js
   * previewFromRequest 실측 형태). 렌더러를 재정의하지 않고 기존 캔버스를 그대로 쓴다.
   * targetGroups 는 생성 전용 문자열이라 표시용으로만 쉼표 분리한다(서버 전송값은 builderDraft 원본).
   */
  function writePendingPreview(draft, academyNameFallback) {
    var builderDraft = (draft && draft.builderDraft) || {};
    var visibility = {};
    FUTURE_SECTION_IDS.forEach(function (id) { visibility[id] = false; });
    var preview = {
      name: builderDraft.academyName || academyNameFallback || '새 학원',
      intro: typeof builderDraft.intro === 'string' ? builderDraft.intro : '',
      subjects: [],
      targetGroups: splitList(builderDraft.targetGroups),
      regions: [],
      handle: null,
      pageSettings: {
        builderDraft: builderDraft,
        sectionVisibility: visibility
      },
      displayUrl: null,
      sectionVisibility: visibility,
      sectionOrder: DEFAULT_SECTION_ORDER.slice()
    };
    try {
      window.sessionStorage.setItem(PENDING_PREVIEW_KEY, JSON.stringify(preview));
    } catch (_) { /* 미리보기 핸드오프 실패는 승인 흐름을 막지 않는다. */ }
  }

  function splitList(value) {
    if (Array.isArray(value)) return value;
    if (typeof value !== 'string') return [];
    return value.split(',').map(function (part) { return part.trim(); }).filter(function (part) { return !!part; });
  }

  function clearPendingPreview() {
    try {
      window.sessionStorage.removeItem(PENDING_PREVIEW_KEY);
    } catch (_) { /* no-op */ }
  }

  function restoreCreateSession() {
    var session = readCreateSession();
    if (!session) return;
    state.createSession = session;
    renderCreateCard(session, true);
  }

  function sendCreate() {
    if (!el.input || state.busy) return;
    var message = String(el.input.value || '').trim();
    var academyName = el.nameInput ? String(el.nameInput.value || '').trim() : '';
    if (!message) return;
    if (!academyName) {
      setStatus('학원 이름을 먼저 입력해 주세요.');
      if (el.nameInput) el.nameInput.focus();
      return;
    }
    if (state.createSession && !window.confirm('진행 중인 생성 초안이 있습니다. 새 요청으로 대체할까요?')) {
      return;
    }

    state.busy = true;
    if (el.send) el.send.disabled = true;
    appendMessage('mine', message);
    el.input.value = '';
    autoGrow();
    setStatus('홈페이지 초안을 만들고 있습니다…');

    postJson(API.create, { message: message, academyName: academyName, templateId: state.pendingTemplateId || null })
      .then(function (data) {
        state.pendingTemplateId = null;
        setStatus('');
        appendMessage('ai', (data && data.reply) || '');
        var draft = data && data.draft ? data.draft : null;
        if (!draft) return;
        var session = {
          academyName: academyName,
          draft: draft,
          idempotencyKey: null,
          halted: false,
          createdAt: new Date().toISOString()
        };
        writeCreateSession(session);
        // 계약 §5 → 지시 3항: 초안은 기존 P0 미리보기로 보여 준 뒤 승인을 받는다.
        writePendingPreview(draft, academyName);
        toast('초안을 미리보기에 반영합니다.', 'fa-wand-magic-sparkles');
        window.setTimeout(function () { window.location.reload(); }, 900);
      })
      .catch(function (error) {
        setStatus('');
        appendError(error);
      })
      .then(function () {
        state.busy = false;
        if (el.send) el.send.disabled = false;
      });
  }

  /** 승인 카드. 복원(restored) 여부와 무관하게 동일하게 그린다. */
  function renderCreateCard(session, restored) {
    if (!el.list) return;
    hideEmptyHints();

    var draft = session.draft || {};
    var builderDraft = draft.builderDraft || {};

    var item = document.createElement('div');
    item.className = 'aichat-msg aichat-create';

    var head = document.createElement('div');
    head.className = 'aichat-create-head';
    var badge = document.createElement('span');
    badge.className = 'aichat-create-badge';
    badge.textContent = draft.designPreset || '프리셋 미정';
    head.appendChild(badge);
    var tag = document.createElement('span');
    tag.className = 'aichat-create-tag';
    tag.textContent = restored ? '진행 중이던 생성 초안' : '새 홈페이지 초안';
    head.appendChild(tag);
    item.appendChild(head);

    if (draft.summary) {
      var summary = document.createElement('p');
      summary.className = 'aichat-create-summary';
      summary.textContent = draft.summary;
      item.appendChild(summary);
    }

    var chips = document.createElement('div');
    chips.className = 'aichat-create-chips';
    ['academyName', 'targetGroups', 'ctaEnabled'].forEach(function (key) {
      if (builderDraft[key] == null || builderDraft[key] === '') return;
      var chip = document.createElement('span');
      chip.className = 'aichat-create-chip';
      chip.textContent = CREATE_CHIP_LABELS[key] + ': ' + builderDraft[key];
      chips.appendChild(chip);
    });
    if (chips.childElementCount) item.appendChild(chips);

    if (Array.isArray(draft.warnings) && draft.warnings.length) {
      var warnings = document.createElement('ul');
      warnings.className = 'aichat-create-warnings';
      draft.warnings.forEach(function (warning) {
        var li = document.createElement('li');
        li.textContent = warning;
        warnings.appendChild(li);
      });
      item.appendChild(warnings);
    }

    var hint = document.createElement('p');
    hint.className = 'aichat-create-hint';
    hint.textContent = '미리보기에서 초안을 확인한 후 승인해 주세요. 색상·섹션 구성은 저장 후 Builder에서 다듬을 수 있습니다.';
    item.appendChild(hint);

    var error = document.createElement('p');
    error.className = 'aichat-create-error';
    error.hidden = true;
    item.appendChild(error);

    var actions = document.createElement('div');
    actions.className = 'aichat-proposal-actions';
    var approve = document.createElement('button');
    approve.type = 'button';
    approve.className = 'aichat-btn aichat-btn-primary';
    approve.textContent = '이 초안으로 생성 (DRAFT 저장)';
    approve.addEventListener('click', function () { approveCreate(approve, error); });
    var discard = document.createElement('button');
    discard.type = 'button';
    discard.className = 'aichat-btn aichat-btn-ghost';
    discard.textContent = '폐기';
    discard.addEventListener('click', function () {
      writeCreateSession(null);
      clearPendingPreview();
      window.location.reload();
    });
    actions.appendChild(approve);
    actions.appendChild(discard);
    item.appendChild(actions);

    if (session.halted) {
      approve.disabled = true;
      error.hidden = false;
      error.textContent = '이미 처리된 요청입니다. 추가 생성 요청은 중단되었습니다.';
    }

    el.list.appendChild(item);
    scrollListToBottom();
  }

  var CREATE_CHIP_LABELS = {
    academyName: '학원 이름',
    targetGroups: '대상',
    ctaEnabled: '상담 신청'
  };

  /**
   * 계약 §6. idempotencyKey 는 승인 시도마다 한 번만 만들고 세션에 고정한다 —
   * 네트워크 재시도에도 같은 키를 쓴다. 409 면 새 키로 바꾸지 않고 중단한다.
   * sessionId 는 보내지 않는다(추적용만).
   */
  function approveCreate(button, errorEl) {
    var session = state.createSession;
    if (!session || state.busy) return;
    var draft = session.draft || {};
    var builderDraft = draft.builderDraft || {};
    var approveLabel = button ? button.textContent : '';

    if (!session.idempotencyKey) {
      session.idempotencyKey = newUuid();
      writeCreateSession(session);
    }

    state.busy = true;
    if (button) { button.disabled = true; button.textContent = '생성 중…'; }
    setStatus('홈페이지를 저장하고 있습니다…');

    postJson(API.createApply, {
      idempotencyKey: session.idempotencyKey,
      academyName: builderDraft.academyName || session.academyName,
      designPreset: draft.designPreset || null,
      intro: typeof builderDraft.intro === 'string' ? builderDraft.intro : '',
      builderDraft: builderDraft
    })
      .then(function (data) {
        state.pendingTemplateId = null;
        setStatus('');
        toast((data && data.message) || '홈페이지가 생성되었습니다.', 'fa-circle-check');
        writeCreateSession(null);
        clearPendingPreview();
        try { sessionStorage.setItem('builditAfterAiCreate', '1'); } catch (_) { /* 안내만 생략 */ }
        // DRAFT Academy 가 서버에 생겼으므로 Builder 를 다시 열어 SSR 모델로 편집한다.
        window.setTimeout(function () { window.location.href = BUILDER_URL; }, 900);
      })
      .catch(function (error) {
        setStatus('');
        if (error && error.status === 409) {
          // 중복 적용 — 지시 2항: 추가 생성 요청 중단. 키를 새로 만들지 않는다.
          session.halted = true;
          writeCreateSession(session);
          if (button) { button.disabled = true; button.textContent = '중복 요청 — 중단됨'; }
          if (errorEl) {
            errorEl.hidden = false;
            errorEl.textContent = '이미 처리된 요청입니다. 추가 생성 요청은 중단되었습니다.';
          }
          return;
        }
        if (button) { button.disabled = false; button.textContent = approveLabel; }
        if (errorEl) {
          errorEl.hidden = false;
          errorEl.textContent = (error && error.message) || '생성하지 못했습니다. 잠시 후 다시 시도해 주세요.';
        }
      })
      .then(function () {
        state.busy = false;
      });
  }

  function newUuid() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    var bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    var hex = Array.from(bytes, function (byte) { return byte.toString(16).padStart(2, '0'); });
    return hex.slice(0, 4).join('') + '-' + hex.slice(4, 6).join('') + '-'
      + hex.slice(6, 8).join('') + '-' + hex.slice(8, 10).join('') + '-' + hex.slice(10).join('');
  }

  function openPanel() {
    el.panel.classList.add('is-open');
    el.panel.setAttribute('aria-hidden', 'false');
    document.body.classList.add('aichat-open');
    if (el.launcher) el.launcher.setAttribute('aria-expanded', 'true');
    if (el.input) el.input.focus();
  }

  function closePanel() {
    el.panel.classList.remove('is-open');
    el.panel.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('aichat-open');
    if (el.launcher) el.launcher.setAttribute('aria-expanded', 'false');
  }

  function switchTab(which) {
    var versions = which === 'versions';
    if (el.tabChat) el.tabChat.classList.toggle('is-active', !versions);
    if (el.tabVersions) el.tabVersions.classList.toggle('is-active', versions);
    if (el.chatView) el.chatView.hidden = versions;
    if (el.versionsView) el.versionsView.hidden = !versions;
    if (versions) loadVersions();
  }

  function autoGrow() {
    if (!el.input) return;
    el.input.style.height = 'auto';
    el.input.style.height = Math.min(el.input.scrollHeight, 96) + 'px';
  }

  function setStatus(text) {
    if (el.status) el.status.textContent = text || '';
  }

  function setVersionStatus(text) {
    if (el.versionStatus) el.versionStatus.textContent = text || '';
  }

  function scrollListToBottom() {
    if (!el.list) return;
    el.list.scrollTop = el.list.scrollHeight;
  }

  function startNewSession() {
    state.sessionId = null;
    state.pending = null;
    state.restored = false;
    if (el.list) el.list.innerHTML = '';
    setStatus('');
    // 진행 중인 생성 초안 카드는 "새 대화"로도 사라지면 안 되므로 다시 그린다.
    restoreCreateSession();
    updateEmptyHint();
    if (el.input) el.input.focus();
  }

  /* ── 채팅 ─────────────────────────────────────────────────────────── */

  function sendMessage() {
    if (!el.input || state.busy) return;
    // 신규 생성 모드면 수정용 chat 대신 /create 로 분기한다(계약 §5).
    if (state.mode === 'create') { sendCreate(); return; }
    var message = String(el.input.value || '').trim();
    if (!message) return;

    state.busy = true;
    if (el.send) el.send.disabled = true;
    appendMessage('mine', message);
    el.input.value = '';
    autoGrow();
    setStatus('AI가 학원 정보를 확인하고 있습니다…');

    postJson(API.chat, { message: message, sessionId: state.sessionId })
      .then(function (data) {
        if (data && data.sessionId != null) state.sessionId = String(data.sessionId);
        setStatus('');
        var proposal = data && data.proposal ? data.proposal : null;
        appendMessage('ai', (data && data.reply) || '', proposal);
        if (proposal && data && data.needsApproval) {
          state.pending = { sessionId: state.sessionId, changes: normalizeChanges(proposal.changes) };
        }
      })
      .catch(function (error) {
        setStatus('');
        appendError(error);
      })
      .then(function () {
        state.busy = false;
        if (el.send) el.send.disabled = false;
        if (el.input) el.input.focus();
      });
  }

  function normalizeChanges(changes) {
    if (!Array.isArray(changes)) return [];
    return changes.filter(function (change) {
      return change && typeof change.key === 'string' && change.key.length > 0;
    });
  }

  function appendMessage(kind, text, proposal) {
    if (!el.list) return;
    hideEmptyHints();

    var item = document.createElement('div');
    item.className = 'aichat-msg aichat-msg-' + kind;

    var body = document.createElement('div');
    body.className = 'aichat-msg-body';
    body.textContent = text || '';
    item.appendChild(body);

    if (proposal) item.appendChild(renderProposal(proposal));

    el.list.appendChild(item);
    scrollListToBottom();
  }

  function appendError(error) {
    if (!el.list) return;
    hideEmptyHints();
    var item = document.createElement('div');
    item.className = 'aichat-msg aichat-msg-error';
    var body = document.createElement('div');
    body.className = 'aichat-msg-body';
    body.textContent = (error && error.message) || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    item.appendChild(body);
    el.list.appendChild(item);
    scrollListToBottom();
  }

  /**
   * 제안 카드. changes[] = { key, before, after } (계약 §1).
   * before/after 를 jsdiff 로 단어 단위 비교해 무엇이 바뀌는지 보여준다.
   */
  function renderProposal(proposal) {
    var wrap = document.createElement('div');
    wrap.className = 'aichat-proposal';

    if (proposal.summary) {
      var summary = document.createElement('p');
      summary.className = 'aichat-proposal-summary';
      summary.textContent = proposal.summary;
      wrap.appendChild(summary);
    }

    var changes = normalizeChanges(proposal.changes);
    changes.forEach(function (change) {
      wrap.appendChild(renderChangeRow(change));
    });

    if (!changes.length) {
      var none = document.createElement('p');
      none.className = 'aichat-proposal-empty';
      none.textContent = '제안된 변경 항목이 없습니다.';
      wrap.appendChild(none);
      return wrap;
    }

    var actions = document.createElement('div');
    actions.className = 'aichat-proposal-actions';

    var apply = document.createElement('button');
    apply.type = 'button';
    apply.className = 'aichat-btn aichat-btn-primary';
    apply.textContent = '적용하기';
    apply.addEventListener('click', function () { applyChanges(apply, changes); });

    var dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'aichat-btn aichat-btn-ghost';
    dismiss.textContent = '적용 안 함';
    dismiss.addEventListener('click', function () {
      wrap.classList.add('is-dismissed');
      state.pending = null;
      setStatus('적용하지 않았습니다. 학원 정보는 바뀌지 않았습니다.');
    });

    actions.appendChild(apply);
    actions.appendChild(dismiss);
    wrap.appendChild(actions);
    return wrap;
  }

  function renderChangeRow(change) {
    var row = document.createElement('div');
    row.className = 'aichat-change';

    var head = document.createElement('div');
    head.className = 'aichat-change-head';
    var label = document.createElement('span');
    label.className = 'aichat-change-label';
    label.textContent = FIELD_LABELS[change.key] || change.key;
    head.appendChild(label);
    if (!FIELD_LABELS[change.key]) {
      var key = document.createElement('code');
      key.className = 'aichat-change-key';
      key.textContent = change.key;
      head.appendChild(key);
    }
    row.appendChild(head);

    var before = change.before == null ? '' : String(change.before);
    var after = change.after == null ? '' : String(change.after);
    row.appendChild(renderDiff(before, after));
    return row;
  }

  /**
   * 전후 비교. jsdiff(vendored, BSD-3-Clause)의 Diff.diffWords 를 쓰고,
   * 로드되지 않았으면 before/after 를 나란히 보여주는 것으로 대체한다(기능 중단 방지).
   */
  function renderDiff(before, after) {
    var box = document.createElement('div');
    box.className = 'aichat-diff';

    var diffLib = window.Diff;
    var parts = null;
    if (diffLib && typeof diffLib.diffWords === 'function') {
      try {
        parts = diffLib.diffWords(before, after);
      } catch (_) {
        parts = null;
      }
    }

    if (!parts) {
      var plainBefore = document.createElement('p');
      plainBefore.className = 'aichat-diff-line';
      plainBefore.appendChild(makeTag('현재'));
      plainBefore.appendChild(document.createTextNode(before === '' ? '(비어 있음)' : before));
      var plainAfter = document.createElement('p');
      plainAfter.className = 'aichat-diff-line';
      plainAfter.appendChild(makeTag('변경'));
      plainAfter.appendChild(document.createTextNode(after === '' ? '(비어 있음)' : after));
      box.appendChild(plainBefore);
      box.appendChild(plainAfter);
      return box;
    }

    var beforeLine = document.createElement('p');
    beforeLine.className = 'aichat-diff-line';
    beforeLine.appendChild(makeTag('현재'));
    var afterLine = document.createElement('p');
    afterLine.className = 'aichat-diff-line';
    afterLine.appendChild(makeTag('변경'));

    parts.forEach(function (part) {
      var text = part.value == null ? '' : String(part.value);
      if (!text) return;
      if (!part.removed) {
        afterLine.appendChild(document.createTextNode(text));
      }
      if (!part.added) {
        beforeLine.appendChild(document.createTextNode(text));
      }
      if (part.removed) {
        beforeLine.appendChild(makeMark(text, 'del'));
      }
      if (part.added) {
        afterLine.appendChild(makeMark(text, 'ins'));
      }
    });

    box.appendChild(beforeLine);
    box.appendChild(afterLine);
    return box;
  }

  function makeTag(text) {
    var tag = document.createElement('span');
    tag.className = 'aichat-diff-tag';
    tag.textContent = text;
    return tag;
  }

  function makeMark(text, kind) {
    var mark = document.createElement(kind === 'del' ? 'del' : 'ins');
    mark.className = 'aichat-diff-' + kind;
    mark.textContent = text;
    return mark;
  }

  function applyChanges(button, changes) {
    if (state.busy) return;
    var payload = {
      sessionId: state.sessionId,
      confirmedChanges: changes.map(function (change) {
        return { key: change.key, before: change.before, after: change.after };
      })
    };

    state.busy = true;
    if (button) button.disabled = true;
    setStatus('적용 중입니다…');

    postJson(API.apply, payload)
      .then(function (data) {
        state.pendingTemplateId = null;
        setStatus('');
        state.pending = null;
        var versionId = data && data.versionId != null ? data.versionId : null;
        toast('적용했습니다.' + (versionId == null ? '' : ' (버전 ' + versionId + ' 저장)')
          + ' 미리보기를 다시 불러옵니다.', 'fa-circle-check');
        // 서버가 DB 를 바꿨으므로 SSR 모델을 다시 읽는다. 이 패널은 academy_builder.js 의
        // currentPreview 를 갱신할 방법이 없다(IIFE). reload 훅이 생기면 그쪽으로 교체 예정.
        window.setTimeout(function () { window.location.reload(); }, 900);
      })
      .catch(function (error) {
        setStatus('');
        if (button) button.disabled = false;
        appendError(error);
      })
      .then(function () {
        state.busy = false;
      });
  }

  /* ── 이력 / 복원 ──────────────────────────────────────────────────── */

  function loadVersions() {
    if (!el.versionList) return;
    setVersionStatus('불러오는 중…');
    el.versionList.innerHTML = '';
    if (el.versionEmpty) el.versionEmpty.hidden = true;

    fetch(API.versions, { credentials: 'same-origin' })
      .then(function (res) { return toData(res); }, networkError)
      .then(function (data) {
        setVersionStatus('');
        var versions = Array.isArray(data) ? data : [];
        if (!versions.length) {
          if (el.versionEmpty) el.versionEmpty.hidden = false;
          return;
        }
        versions.forEach(function (version) {
          el.versionList.appendChild(renderVersionRow(version));
        });
      })
      .catch(function (error) {
        setVersionStatus((error && error.message) || '버전 목록을 불러오지 못했습니다.');
      });
  }

  function renderVersionRow(version) {
    var row = document.createElement('div');
    row.className = 'aichat-version';

    var info = document.createElement('div');
    info.className = 'aichat-version-info';

    var title = document.createElement('strong');
    title.className = 'aichat-version-no';
    title.textContent = '버전 ' + (version && version.versionNo != null ? version.versionNo : '-');
    info.appendChild(title);

    var meta = document.createElement('div');
    meta.className = 'aichat-version-meta';
    meta.textContent = [
      version && version.note ? version.note : '',
      version && version.createdBy ? version.createdBy : '',
      formatDateTime(version && version.createdAt)
    ].filter(function (part) { return !!part; }).join(' · ');
    info.appendChild(meta);

    var restore = document.createElement('button');
    restore.type = 'button';
    restore.className = 'aichat-btn aichat-btn-ghost';
    restore.textContent = '복원';
    restore.addEventListener('click', function () { restoreVersion(version, restore); });

    row.appendChild(info);
    row.appendChild(restore);
    return row;
  }

  function restoreVersion(version, button) {
    if (state.busy) return;
    if (version == null || version.id == null) return;
    if (!window.confirm('버전 ' + (version.versionNo != null ? version.versionNo : version.id)
      + ' 상태로 되돌립니다. 현재 상태도 새 버전으로 저장됩니다. 계속할까요?')) {
      return;
    }

    state.busy = true;
    if (button) button.disabled = true;
    setVersionStatus('복원 중입니다…');

    postJson(API.versions + '/' + encodeURIComponent(version.id) + '/restore', {})
      .then(function (data) {
        setVersionStatus('');
        var versionId = data && data.versionId != null ? data.versionId : null;
        toast('복원했습니다.' + (versionId == null ? '' : ' (복원 전 상태가 버전 ' + versionId + '로 저장)')
          + ' 미리보기를 다시 불러옵니다.', 'fa-clock-rotate-left');
        window.setTimeout(function () { window.location.reload(); }, 900);
      })
      .catch(function (error) {
        setVersionStatus('');
        if (button) button.disabled = false;
        setVersionStatus((error && error.message) || '복원하지 못했습니다.');
      })
      .then(function () {
        state.busy = false;
      });
  }

  /* ── 공통 ─────────────────────────────────────────────────────────── */

  function postJson(url, body) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body || {})
    }).then(function (res) { return toData(res); }, networkError);
  }

  /** fetch 자체가 실패(오프라인·DNS·CORS 등) — 기술 문구 대신 안내 문구로 바꾼다. */
  function networkError() {
    var error = new Error('네트워크 연결을 확인해 주세요. 잠시 후 다시 시도해 주세요.');
    error.status = 0;
    throw error;
  }

  /**
   * 계약 §오류 코드: 400(Academy 없음 / 세션 없음 / 허용 외 필드 / 위험 콘텐츠 / 생성 검증 실패),
   * 403(타 학원 세션), 409(중복 idempotencyKey), 503(AI 생성 실패).
   * 서버는 오류 본문을 여러 형태로 내려준다 —
   *   · ChatResponse(JSON, /chat 400) → 거부 사유는 reply 필드 (B6)
   *   · ResponseStatusException → ProblemDetail.detail (createApply 400/409)
   *   · GlobalExceptionHandler 의 Map → message 필드 (503 등)
   *   · GlobalExceptionHandler 의 ResponseEntity&lt;String&gt; → plain string (400/403)
   * 그중 실제 안내 문구를 골라 화면에 보여준다. 5xx 의 Spring 기본 상태 문구는 쓰지 않는다.
   */
  function toData(res) {
    return res.text().then(function (raw) {
      var data = null;
      if (raw) { try { data = JSON.parse(raw); } catch (_) { data = null; } }
      if (res.ok) return data;

      var message = null;
      if (data && typeof data === 'object') {
        message = data.message || data.errorMessage || null;
        // B6: /chat 은 거부 사유를 예외가 아니라 ChatResponse(JSON, status 400)의 reply 로 싣는다.
        if (!message && data.reply) message = data.reply;
        // ResponseStatusException → ProblemDetail.detail (createApply 400/409). 4xx 에서만 보조로 쓴다
        // (error 필드는 Boot 기본 오류 속성, 5xx 는 상태 문구라 쓰지 않는다).
        if (!message && res.status < 500) message = data.detail || data.error || null;
      }
      // plain-string 오류 본문(400/403) — HTML 오류 페이지나 과도하게 긴 본문은 안내로 쓰지 않는다.
      if (!message && raw && !data && raw.charAt(0) !== '<' && raw.length <= 300) {
        message = raw.trim();
      }
      if (!message) {
        if (res.status === 403) message = '이 학원의 정보를 수정할 권한이 없습니다.';
        else if (res.status === 401) message = '로그인이 필요합니다. 새로고침 후 다시 시도해 주세요.';
        else if (res.status >= 500) message = '서버에 잠시 문제가 있습니다. 조금 후 다시 시도해 주세요.';
        else message = '요청을 처리하지 못했습니다. (HTTP ' + res.status + ')';
      }
      // 상태코드를 error 에 실어 보낸다 — approveCreate 가 409(중복 적용)를 구별해 중단한다.
      var error = new Error(message);
      error.status = res.status;
      throw error;
    });
  }

  function formatDateTime(iso) {
    if (!iso) return '';
    var date = new Date(iso);
    if (isNaN(date.getTime())) return String(iso);
    var pad = function (value) { return String(value).padStart(2, '0'); };
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
      + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  /** academy_builder.html 의 전역 showToast 를 쓴다. 없으면 상태줄로 대체한다. */
  function toast(message, icon) {
    if (typeof window.showToast === 'function') {
      window.showToast(message, icon || 'fa-circle-check');
      return;
    }
    setStatus(message);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
