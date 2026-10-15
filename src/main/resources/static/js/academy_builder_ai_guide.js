(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.F2RAcademyAiGuide = api;
})(typeof window !== 'undefined' ? window : globalThis, function () {
  'use strict';

  // ── AI Guided Academy Builder (Phase 5B) ─────────────────────────────────
  // "Academy 홈페이지 제작 전용 AI Guide". 범용 챗봇이 아니라, Builder 입장부터 홈페이지
  // 완성까지 정해진 단계(STEP)를 따라 안내하는 진행자다. 이 모듈은 순수 로직만 갖는다 —
  // DOM/fetch/저장은 academy_builder.js의 wireAiGuide가 담당한다(academy_builder_editable_guide.js
  // 와 같은 분리 규칙). 그래서 node에서 단독으로 단위 테스트할 수 있다.
  //
  // 절대 원칙:
  //   - Guide는 자동 Publish 하지 않는다. 자동 section visibility 변경도 안 한다.
  //   - 문구 "적용"은 사용자가 눌러야 하고, 그때도 기존 인라인 편집과 같은 경로로만 반영된다.
  //   - 존재하지 않는 field를 상상해서 만들지 않는다 — 아래 STEPS의 target은 전부 실제
  //     builderDraft key(또는 top-level intro)이며 서버 allowlist(AcademyAiGuideService)와 일치한다.

  const SESSION_STORAGE_KEY = 'f2r.builder.aiGuide.session.v1';
  const PANEL_STATE_STORAGE_KEY = 'f2r.builder.aiGuide.panel.v1';

  // 고정 4개 스타일을 강제하지 않는다 — 버튼 + 자유 입력 둘 다 받는다(§5).
  const STYLE_OPTIONS = Object.freeze([
    '전문적인 입시학원',
    '친근한 학생관리형',
    '원장 브랜딩 중심',
    '프로그램·수업 중심'
  ]);

  /**
   * Guide 단계. 전부 현재 Academy Builder가 "실제로 편집 가능한" 영역이다.
   *  - kind 'style'    : 스타일/톤 질문(문구 target 없음). 답은 세션 desiredStyle에 저장.
   *  - kind 'text'     : 질문 → AI 후보 추천 → 사용자 선택/직접작성/건너뛰기. target은 실제 key.
   *  - kind 'guidance' : 해당 영역의 역할만 설명하고 이동/강조(모달로 관리하는 영역).
   *  - kind 'finish'   : 마무리. 원하면 기존 [AI로 Academy 전체 점검](Phase 5A)으로 연결.
   */
  const STEPS = Object.freeze([
    {
      id: 'naming',
      kind: 'naming',
      title: 'Academy 이름 / 주소',
      sectionKey: 'sec-hero',
      target: null,
      question: '먼저 우리 학원의 이름과 홈페이지 주소부터 정해볼까요?',
      guidance: '반가워요! 방문자가 가장 먼저 보는 건 학원 이름이고, 홈페이지 주소는 한번 정하면 잘 안 바꾸는 값이에요. 여기부터 시작할게요.'
    },
    {
      id: 'style',
      kind: 'style',
      title: '홈페이지 스타일',
      sectionKey: 'sec-hero',
      target: null,
      question: '어떤 느낌의 홈페이지를 만들고 싶으신가요? 아래에서 고르거나 직접 설명해 주세요.',
      guidance: '좋아요! 이제 우리 학원 홈페이지의 첫인상을 만들어볼게요. 첫 화면 → 원장 소개 → 교육철학 → 학원 소개 → 대표 수업 순서로 함께 구성합니다.'
    },
    {
      id: 'hero',
      kind: 'text',
      title: '첫 화면(Hero)',
      sectionKey: 'sec-hero',
      target: 'intro',
      long: true,
      question: '첫 화면에서 가장 강조하고 싶은 학원의 강점은 무엇인가요?',
      guidance: '첫 화면은 방문자가 3초 안에 "여기 뭐 하는 곳인지"를 파악하는 자리입니다. 한두 문장으로 핵심 강점을 보여줍니다.'
    },
    {
      id: 'principal',
      kind: 'text',
      title: '원장 소개',
      sectionKey: 'sec-principal',
      target: 'principalDesc',
      long: true,
      question: '원장님을 한 문단으로 소개한다면 어떤 점을 알리고 싶으신가요? (실제로 강조할 수 있는 경력·전문성이 있다면 함께 알려주세요.)',
      guidance: '원장 소개는 학부모가 신뢰를 판단하는 자리입니다. 검증할 수 없는 실적·수치는 넣지 않습니다.'
    },
    {
      id: 'philosophy',
      kind: 'text',
      title: '교육철학',
      sectionKey: 'sec-philosophy',
      target: 'philosophyTitle',
      long: true,
      question: '이 학원이 가르치는 방식을 한 줄로 표현한다면요?',
      guidance: '교육철학은 "왜 이 학원인가"에 답하는 자리입니다. 아래 4개의 철학 카드는 각 카드를 직접 클릭해 수정하거나 카드별 ✨ AI로 다듬기를 쓸 수 있습니다.'
    },
    {
      id: 'intro',
      kind: 'text',
      title: '학원 소개',
      sectionKey: 'sec-intro',
      target: 'introBodySecondary',
      long: true,
      question: '학원 소개 문단에서 어떤 이야기를 전하고 싶으신가요?',
      guidance: '학원 소개 섹션은 과목·특징을 조금 더 풀어서 설명하는 자리입니다.'
    },
    {
      id: 'programs',
      kind: 'guidance',
      title: '대표 수업 · 영상',
      sectionKey: 'sec-lessons',
      target: null,
      question: '',
      guidance: '대표 수업은 "수업상품 관리"에서 등록·수정합니다(미리보기의 수업 카드를 클릭). 수업 영상이 있으면 "수업 영상" 섹션에서 관리하고, 없으면 나중에 추가해도 됩니다.'
    },
    {
      id: 'review',
      kind: 'finish',
      title: '전체 검토',
      sectionKey: null,
      target: null,
      question: '',
      guidance: '기본 홈페이지 작성이 끝났습니다. 원하시면 [AI로 Academy 전체 점검]으로 전체 구성과 문구를 한 번에 진단받을 수 있습니다.'
    }
  ]);

  const STEP_IDS = STEPS.map(function (step) { return step.id; });
  const TEXT_TARGETS = STEPS
    .filter(function (step) { return step.kind === 'text' && step.target; })
    .map(function (step) { return step.target; });

  // Phase 5B.1 — 각 Guide step이 암시하는 World Environment scene(academy_world.js SCENES id).
  // Bildi가 그 step 섹션으로 날아갈 때 하늘이 함께 바뀐다. STEP 객체를 건드리지 않고 분리 map
  // 으로 둔다(기존 STEPS 계약 유지).
  var STEP_SCENES = Object.freeze({
    naming: 'morning',
    style: 'morning',
    hero: 'day',
    principal: 'day',
    philosophy: 'clouds',
    intro: 'clouds',
    programs: 'sunset',
    videos: 'sunset',
    review: 'stars'
  });
  function sceneForStep(id) {
    return STEP_SCENES[migrateStepId(id)] || 'day';
  }

  // Phase 5B.2 — step 재정렬. 기존 세션의 step id 를 새 구조로 매핑해 진행 상태가 깨지지 않게 한다.
  // 'videos'(별도 단계) → 'programs'(대표 수업·영상 통합)로 흡수. 'style'/'hero' 등은 그대로.
  var STEP_ID_MIGRATIONS = Object.freeze({ videos: 'programs' });
  function migrateStepId(id) {
    return STEP_ID_MIGRATIONS[id] || id;
  }

  function stepById(id) {
    var mapped = migrateStepId(id);
    return STEPS.find(function (step) { return step.id === mapped; }) || null;
  }

  function stepIndex(id) {
    return STEP_IDS.indexOf(id);
  }

  function firstStepId() {
    return STEP_IDS[0];
  }

  function clampStepId(id) {
    var mapped = migrateStepId(id);
    return stepIndex(mapped) >= 0 ? mapped : firstStepId();
  }

  function nextStepId(id) {
    const i = stepIndex(id);
    if (i < 0) return firstStepId();
    return i + 1 < STEP_IDS.length ? STEP_IDS[i + 1] : null;
  }

  function prevStepId(id) {
    const i = stepIndex(id);
    return i > 0 ? STEP_IDS[i - 1] : null;
  }

  function uniqueList(values) {
    const out = [];
    if (!Array.isArray(values)) return out;
    values.forEach(function (value) {
      var id = migrateStepId(value);
      if (STEP_IDS.indexOf(id) >= 0 && out.indexOf(id) < 0) out.push(id);
    });
    return out;
  }

  var TEMP_HANDLE_RE = /^academy-[a-z0-9]{6,24}$/;
  var REAL_HANDLE_RE = /^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$/;
  // starterDraftForDirectBuilder() 가 신규 Academy 에 넣는 placeholder 이름 — "설정됨"으로 보지 않는다.
  var PLACEHOLDER_NAMES = Object.freeze(['새 학원']);

  function namingName(preview) {
    return preview ? String(preview.name == null ? '' : preview.name).trim() : '';
  }
  function namingHandle(preview) {
    return preview ? String(preview.handle == null ? '' : preview.handle).trim().toLowerCase() : '';
  }
  function hasRealHandle(preview) {
    var handle = namingHandle(preview);
    return REAL_HANDLE_RE.test(handle) && !TEMP_HANDLE_RE.test(handle);
  }

  /**
   * 이미 확정된 이름/주소가 있으면 naming 온보딩 단계를 건너뛴다. "발행된 Academy + 실제
   * handle(임시 academy-xxxxxx 아님) + 이름" 이면 확정으로 본다. 신규/미설정은 첫 단계로 노출.
   */
  function isNamingComplete(preview) {
    if (!preview) return false;
    var published = !!(preview.publicPathUrl || preview.publicUrl);
    return published && namingName(preview).length > 0 && hasRealHandle(preview);
  }

  /**
   * "이름 + 실제 handle 이 이미 설정된" Academy 인가(발행 여부 무관). 여기 해당하면 §6 대로
   * naming 을 강제로 다시 보여주지 않고 저장된 Guide 진행 상태를 존중한다.
   */
  function isNamingConfigured(preview) {
    var name = namingName(preview);
    if (name.length < 2 || PLACEHOLDER_NAMES.indexOf(name) >= 0) return false;
    return hasRealHandle(preview);
  }

  /**
   * §7·§8 — Builder 진입 시 naming(STEP 1)부터 시작해야 하는가.
   *
   * 근본 원인: Guide 세션은 academy 로 스코프되지 않은 단일 sessionStorage 키에 저장된다.
   *   (1) #195 이전에 Builder 를 연 사용자는 currentStepId 가 style/hero 등인 세션을 갖고 있고,
   *       createSession() 은 그 값을 유효한 스텝으로 clamp 할 뿐 새로 생긴 naming 으로 되돌리지 않는다.
   *   (2) 키가 academy 별이 아니라, 다른 academy 로 Guide 를 진행한 브라우저에서 신규 academy 를
   *       열면 그 진행 상태를 그대로 물려받아 naming 을 건너뛴다.
   * → 이름/주소가 아직 설정되지 않았고 세션이 naming 을 완료/건너뛴 적도 없다면 naming 으로 되돌린다.
   *   이미 이름/주소가 설정돼 있으면(isNamingConfigured) 저장된 진행 상태를 존중한다.
   */
  function shouldStartAtNaming(preview, session) {
    var safe = createSession(session);
    if (safe.currentStepId === 'naming') return false; // 이미 naming
    if (safe.completedStepIds.indexOf('naming') >= 0) return false;
    if (safe.skippedStepIds.indexOf('naming') >= 0) return false;
    if (isNamingConfigured(preview)) return false;
    return true;
  }

  function trimTo(value, max) {
    const text = String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
    return text.length > max ? text.slice(0, max) : text;
  }

  /**
   * 저장된(혹은 없는) 세션을 정규화한다. V1은 영구 DB를 만들지 않는다 — sessionStorage 하나로
   * 새로고침 복원까지만 처리한다(§6). 알 수 없는 필드/값은 안전하게 떨어뜨린다.
   */
  function createSession(saved) {
    const raw = saved && typeof saved === 'object' ? saved : {};
    const currentStepId = clampStepId(raw.currentStepId);
    return {
      desiredStyle: trimTo(raw.desiredStyle, 200),
      strongestPoint: trimTo(raw.strongestPoint, 400),
      tone: trimTo(raw.tone, 200),
      currentStepId: currentStepId,
      completedStepIds: uniqueList(raw.completedStepIds),
      skippedStepIds: uniqueList(raw.skippedStepIds),
      // 처음 진입인지(온보딩 문구를 보여줄지) 판단용. 명시적으로 false여야 "이미 봤음".
      onboarded: raw.onboarded === true,
      startedAt: typeof raw.startedAt === 'number' ? raw.startedAt : Date.now()
    };
  }

  function markCompleted(session, stepId) {
    const next = createSession(session);
    if (STEP_IDS.indexOf(stepId) < 0) return next;
    next.completedStepIds = uniqueList(next.completedStepIds.concat(stepId));
    next.skippedStepIds = next.skippedStepIds.filter(function (id) { return id !== stepId; });
    return next;
  }

  function markSkipped(session, stepId) {
    const next = createSession(session);
    if (STEP_IDS.indexOf(stepId) < 0) return next;
    next.skippedStepIds = uniqueList(next.skippedStepIds.concat(stepId));
    next.completedStepIds = next.completedStepIds.filter(function (id) { return id !== stepId; });
    return next;
  }

  function goToStep(session, stepId) {
    const next = createSession(session);
    next.currentStepId = clampStepId(stepId);
    return next;
  }

  /** 진행률 요약 — "3 / 8" 같은 표시와 완료/현재/남음 목록을 만든다(§14). */
  function progress(session) {
    const safe = createSession(session);
    const currentIndex = stepIndex(safe.currentStepId);
    const done = safe.completedStepIds.length;
    return {
      total: STEP_IDS.length,
      position: currentIndex + 1,
      currentStepId: safe.currentStepId,
      completed: safe.completedStepIds.slice(),
      skipped: safe.skippedStepIds.slice(),
      remaining: STEP_IDS.filter(function (id) {
        return safe.completedStepIds.indexOf(id) < 0
          && safe.skippedStepIds.indexOf(id) < 0
          && id !== safe.currentStepId;
      }),
      doneCount: done,
      allHandled: STEP_IDS.every(function (id) {
        return safe.completedStepIds.indexOf(id) >= 0 || safe.skippedStepIds.indexOf(id) >= 0;
      })
    };
  }

  /** builderDraft에서 한 key(또는 top-level intro)의 현재 값. academy_builder.js draftText와 동일 규칙. */
  function draftValue(preview, key) {
    if (!preview) return '';
    if (key === 'intro') return String(preview.intro == null ? '' : preview.intro).trim();
    const draft = preview.pageSettings && preview.pageSettings.builderDraft;
    const value = draft && draft[key];
    return String(value == null ? '' : value).trim();
  }

  /**
   * 이미 상당 부분 만들어 둔 사용자를 위해, 각 text 단계의 target이 이미 채워져 있는지 본다(§15).
   * AI가 기존 내용을 덮어쓰지 않도록, wireAiGuide는 이 집합의 단계에서 "기존 유지 / 다시 검토"를
   * 먼저 물어본다.
   */
  function stepsWithExistingContent(preview) {
    return STEPS
      .filter(function (step) {
        return step.kind === 'text' && step.target && draftValue(preview, step.target).length > 0;
      })
      .map(function (step) { return step.id; });
  }

  function hasAnyExistingContent(preview) {
    return stepsWithExistingContent(preview).length > 0;
  }

  /**
   * provider 응답(문자열)에서 문구 후보 배열을 방어적으로 뽑는다. 서버(AcademyAiGuideService)가
   * 이미 검증하지만, 손상된 응답이 그대로 렌더되지 않도록 클라이언트에서도 한 번 더 거른다.
   * JSON 배열 → 실패 시 줄바꿈/번호 목록으로 폴백.
   */
  function parseCandidates(raw, options) {
    const opts = options || {};
    const max = Number(opts.maxLength) > 0 ? Number(opts.maxLength) : 220;
    const limit = Number(opts.limit) > 0 ? Number(opts.limit) : 3;
    const text = String(raw == null ? '' : raw).trim();
    let values = [];
    if (Array.isArray(raw)) {
      values = raw.slice();
    } else {
      const start = text.indexOf('[');
      const end = text.lastIndexOf(']');
      if (start >= 0 && end > start) {
        try {
          const parsed = JSON.parse(text.slice(start, end + 1));
          if (Array.isArray(parsed)) values = parsed;
        } catch (_) { /* 폴백으로 내려간다 */ }
      }
      // 배열이 없으면 "1. …/2) …/- …" 같은 목록만 폴백으로 받는다. 리스트 마커도 줄바꿈도
      // 없는 단일 문장은(설명/사과문일 가능성이 높아) 후보로 취급하지 않는다.
      if (!values.length && text) {
        const looksLikeList = /\n/.test(text) || /^\s*(?:[-*•]|\d+[.)])\s+/.test(text);
        if (looksLikeList) {
          values = text.split(/\r?\n+/)
            .map(function (line) {
              return line.replace(/^\s*(?:[-*•]|\d+[.)])\s*/, '').replace(/^["“]|["”]$/g, '');
            })
            .filter(function (line) { return line.trim(); });
        }
      }
    }
    const out = [];
    const seen = Object.create(null);
    values.forEach(function (value) {
      if (out.length >= limit) return;
      const cleaned = String(value == null ? '' : value)
        .replace(/\s+/g, ' ')
        .replace(/^["“]|["”]$/g, '')
        .trim();
      if (!cleaned || cleaned.length > max) return;
      const dedupeKey = cleaned.toLowerCase();
      if (seen[dedupeKey]) return;
      seen[dedupeKey] = true;
      out.push(cleaned);
    });
    return out;
  }

  function isGuideTextTarget(target) {
    return TEXT_TARGETS.indexOf(target) >= 0;
  }

  return Object.freeze({
    SESSION_STORAGE_KEY,
    PANEL_STATE_STORAGE_KEY,
    STYLE_OPTIONS,
    STEPS,
    STEP_IDS,
    TEXT_TARGETS,
    STEP_SCENES,
    STEP_ID_MIGRATIONS,
    migrateStepId,
    sceneForStep,
    isNamingComplete,
    isNamingConfigured,
    shouldStartAtNaming,
    hasRealHandle,
    stepById,
    stepIndex,
    firstStepId,
    nextStepId,
    prevStepId,
    createSession,
    markCompleted,
    markSkipped,
    goToStep,
    progress,
    draftValue,
    stepsWithExistingContent,
    hasAnyExistingContent,
    parseCandidates,
    isGuideTextTarget
  });
});
