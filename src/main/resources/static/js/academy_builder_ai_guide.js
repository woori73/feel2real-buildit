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
      id: 'style',
      kind: 'style',
      title: '홈페이지 스타일',
      sectionKey: 'sec-hero',
      target: null,
      question: '어떤 느낌의 홈페이지를 만들고 싶으신가요? 아래에서 고르거나 직접 설명해 주세요.',
      guidance: '완성된 Academy처럼 첫 화면 → 원장 소개 → 교육철학 → 프로그램 → 수업 콘텐츠 순서로 함께 구성해 보겠습니다.'
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
      title: '프로그램 · 수업',
      sectionKey: 'sec-lessons',
      target: null,
      question: '',
      guidance: '대표 수업은 "수업상품 관리"에서 등록·수정합니다. 미리보기의 수업 카드를 클릭하면 관리 화면이 열립니다. 각 수업의 한 줄 소개는 카드에서 바로 편집할 수 있습니다.'
    },
    {
      id: 'videos',
      kind: 'guidance',
      title: '영상 · 콘텐츠',
      sectionKey: 'sec-videos',
      target: null,
      question: '',
      guidance: '수업 영상이 있다면 "수업 영상" 섹션에서 관리합니다. 영상이 아직 없다면 이 단계는 건너뛰고 나중에 추가해도 됩니다.'
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
    style: 'morning',
    hero: 'day',
    principal: 'day',
    philosophy: 'clouds',
    intro: 'clouds',
    programs: 'sunset',
    videos: 'night',
    review: 'stars'
  });
  function sceneForStep(id) {
    return STEP_SCENES[id] || 'day';
  }

  function stepById(id) {
    return STEPS.find(function (step) { return step.id === id; }) || null;
  }

  function stepIndex(id) {
    return STEP_IDS.indexOf(id);
  }

  function firstStepId() {
    return STEP_IDS[0];
  }

  function clampStepId(id) {
    return stepIndex(id) >= 0 ? id : firstStepId();
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
      if (STEP_IDS.indexOf(value) >= 0 && out.indexOf(value) < 0) out.push(value);
    });
    return out;
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
    sceneForStep,
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
