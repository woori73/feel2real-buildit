(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.F2RLessonProductEditorState = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  const AUTHORING_FIELDS = Object.freeze([
    'name',
    'description',
    'schoolName',
    'gradeLabel',
    'lessonType',
    'targetGroup',
    'priceAmount',
    'priceUnit',
    'priceSummary',
    'maxCapacity',
    'scheduleType',
    'weeklyCount',
    'weekdays',
    'startDate',
    'endDate',
    'durationWeeks',
    'durationMonths',
    'scheduleSummary',
    'status',
    'tabGuidanceLabel',
    'tabShortName',
    'kicker',
    'highlight',
    'learnerNeed',
    'coreDetailTitle',
    'explanation1',
    'explanation2',
    'explanation3',
    'theme',
    // 대표 미디어. hidden input으로 폼에 실어야 readForm()이 집어간다 —
    // readForm은 이 목록을 돌며 form.elements.namedItem(field)를 찾고, 없으면 건너뛴다.
    'mediaImageKey',
    'mediaVideoId',
    'otVideoId'
  ]);

  // 편집기는 문구/배경과 LMS 관리 표에 필요한 학교·학년·가격을 소유한다.
  // 정원·일정·수업 유형·수강 대상은 폼에 없으므로 전부 null로 보낸다.
  // status는 보내지 않고 서버가 PUBLISHED로 정규화한다.
  const DEFAULT_DRAFT = Object.freeze({
    name: '',
    description: '',
    schoolName: null,
    gradeLabel: null,
    lessonType: null,
    targetGroup: null,
    priceAmount: null,
    priceUnit: null,
    priceSummary: null,
    maxCapacity: null,
    scheduleType: null,
    weeklyCount: null,
    weekdays: Object.freeze([]),
    startDate: null,
    endDate: null,
    durationWeeks: null,
    durationMonths: null,
    scheduleSummary: null,
    status: null,
    tabGuidanceLabel: '',
    tabShortName: '',
    kicker: '',
    highlight: '',
    learnerNeed: '',
    coreDetailTitle: '',
    explanation1: '',
    explanation2: '',
    explanation3: '',
    theme: 'NAVY',
    mediaImageKey: null,
    mediaVideoId: null,
    otVideoId: null
  });

  const NUMBER_FIELDS = new Set([
    'priceAmount',
    'maxCapacity',
    'weeklyCount',
    'durationWeeks',
    'durationMonths',
    'mediaVideoId',
    'otVideoId'
  ]);

  function cloneDraft(source) {
    const value = source && typeof source === 'object' ? source : {};
    const draft = {};
    for (const field of AUTHORING_FIELDS) {
      const fallback = DEFAULT_DRAFT[field];
      const candidate = value[field] == null && fallback != null ? fallback : value[field];
      draft[field] = Array.isArray(candidate) ? candidate.slice() : candidate;
    }
    return draft;
  }

  function canonicalDraft(source) {
    const draft = cloneDraft(source);
    const canonical = {};
    for (const field of AUTHORING_FIELDS) {
      const value = draft[field];
      if (field === 'weekdays') {
        canonical[field] = Array.from(new Set(Array.isArray(value) ? value : []))
          .map(String)
          .sort();
      } else if (NUMBER_FIELDS.has(field)) {
        canonical[field] = value === '' || value == null ? null : Number(value);
      } else if (typeof value === 'string') {
        const trimmed = value.trim();
        canonical[field] = trimmed === '' ? null : trimmed;
      } else {
        canonical[field] = value == null ? null : value;
      }
    }
    canonical.theme = canonical.theme || 'NAVY';
    return canonical;
  }

  function sameDraft(left, right) {
    return JSON.stringify(canonicalDraft(left)) === JSON.stringify(canonicalDraft(right));
  }

  function createState(lessons, options) {
    const source = Array.isArray(lessons) ? lessons : [];
    const config = options && typeof options === 'object' ? options : {};
    let items = source.map(createExistingItem);
    let activeKey = null;
    let selectionError = null;

    if (config.activeLessonId != null) {
      const selected = items.find((item) => String(item.lessonId) === String(config.activeLessonId));
      if (selected) activeKey = selected.clientKey;
      else selectionError = '선택한 수업상품을 찾을 수 없습니다.';
    } else if (items.length > 0) {
      activeKey = items[0].clientKey;
    }

    if (config.newDraft === true || items.length === 0) {
      const added = createNewItem(config.newDraftValues, config.keyFactory);
      items = items.concat(added);
      activeKey = added.clientKey;
    }

    return { items, activeKey, selectionError };
  }

  function createExistingItem(lesson) {
    const source = lesson && typeof lesson === 'object' ? lesson : {};
    const draft = cloneDraft(source);
    return {
      clientKey: `lesson:${source.lessonId}`,
      lessonId: source.lessonId,
      creationKey: source.creationKey || null,
      authoringRevision: effectiveRevision(source.authoringRevision),
      serverSnapshot: cloneDraft(draft),
      draft: cloneDraft(draft),
      appliedPreview: cloneDraft(draft)
    };
  }

  function createNewItem(overrides, keyFactory) {
    const creationKey = makeCreationKey(keyFactory);
    const draft = cloneDraft(Object.assign({}, DEFAULT_DRAFT, overrides || {}));
    return {
      clientKey: `creation:${creationKey}`,
      lessonId: null,
      creationKey,
      authoringRevision: 0,
      serverSnapshot: cloneDraft(DEFAULT_DRAFT),
      draft: cloneDraft(draft),
      appliedPreview: cloneDraft(draft)
    };
  }

  function makeCreationKey(keyFactory) {
    const generated = typeof keyFactory === 'function'
      ? keyFactory()
      : globalCryptoUuid();
    const key = String(generated || '').trim();
    if (!key) throw new Error('creationKey must not be blank');
    return key;
  }

  function globalCryptoUuid() {
    if (typeof globalThis !== 'undefined'
        && globalThis.crypto
        && typeof globalThis.crypto.randomUUID === 'function') {
      return globalThis.crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (character) {
      const random = Math.floor(Math.random() * 16);
      const value = character === 'x' ? random : (random & 0x3) | 0x8;
      return value.toString(16);
    });
  }

  function effectiveRevision(value) {
    const revision = Number(value);
    return Number.isFinite(revision) && revision >= 0 ? revision : 0;
  }

  function getActiveItem(state) {
    if (!state || !Array.isArray(state.items)) return null;
    return state.items.find((item) => item.clientKey === state.activeKey) || null;
  }

  function replaceItem(state, clientKey, transform) {
    if (!state || !Array.isArray(state.items)) return state;
    let changed = false;
    const items = state.items.map((item) => {
      if (item.clientKey !== clientKey) return item;
      changed = true;
      return transform(item);
    });
    return changed ? Object.assign({}, state, { items }) : state;
  }

  function updateActiveDraft(state, patch) {
    const active = getActiveItem(state);
    if (!active) return state;
    const changes = patch && typeof patch === 'object' ? patch : {};
    return replaceItem(state, active.clientKey, (item) => Object.assign({}, item, {
      draft: cloneDraft(Object.assign({}, item.draft, changes))
    }));
  }

  function selectItem(state, clientKey, activeDraft) {
    if (!state || !Array.isArray(state.items)) return state;
    if (!state.items.some((item) => item.clientKey === clientKey)) return state;
    const captured = activeDraft == null ? state : updateActiveDraft(state, activeDraft);
    return Object.assign({}, captured, { activeKey: clientKey, selectionError: null });
  }

  function applyActivePreview(state) {
    const active = getActiveItem(state);
    if (!active) return state;
    return replaceItem(state, active.clientKey, (item) => Object.assign({}, item, {
      appliedPreview: cloneDraft(item.draft)
    }));
  }

  function addDraft(state, overrides, keyFactory) {
    const current = state && Array.isArray(state.items)
      ? state
      : { items: [], activeKey: null, selectionError: null };
    const item = createNewItem(overrides, keyFactory);
    return Object.assign({}, current, {
      items: current.items.concat(item),
      activeKey: item.clientKey,
      selectionError: null
    });
  }

  // 선택한 수업을 목록에서 뺀다. 삭제 후 남는 게 없으면 빈 신규 draft를 하나 만들어
  // 편집기가 빈 화면이 되지 않게 한다.
  function removeItem(state, clientKey, keyFactory) {
    const current = state && Array.isArray(state.items)
      ? state
      : { items: [], activeKey: null, selectionError: null };
    const index = current.items.findIndex((item) => item.clientKey === clientKey);
    if (index < 0) return current;

    const items = current.items.slice(0, index).concat(current.items.slice(index + 1));
    if (items.length === 0) {
      const fresh = createNewItem({}, keyFactory);
      return Object.assign({}, current, {
        items: [fresh],
        activeKey: fresh.clientKey,
        selectionError: null
      });
    }
    const nextActive = items[Math.min(index, items.length - 1)];
    return Object.assign({}, current, {
      items,
      activeKey: current.activeKey === clientKey ? nextActive.clientKey : current.activeKey,
      selectionError: null
    });
  }

  // 배경 스타일은 학원 공통이다. 모든 수업의 draft와 미리보기에 같은 값을 적용한다.
  function setCommonTheme(state, theme) {
    const current = state && Array.isArray(state.items) ? state : { items: [] };
    const items = current.items.map((item) => {
      if (item.draft.theme === theme && item.appliedPreview.theme === theme) return item;
      return Object.assign({}, item, {
        draft: Object.assign({}, item.draft, { theme }),
        appliedPreview: Object.assign({}, item.appliedPreview, { theme })
      });
    });
    return Object.assign({}, current, { items });
  }

  function isSaveDirty(item) {
    return Boolean(item) && !sameDraft(item.draft, item.serverSnapshot);
  }

  function isPreviewDirty(item) {
    return Boolean(item) && !sameDraft(item.draft, item.appliedPreview);
  }

  function getItemView(item) {
    if (!item) return null;
    return Object.assign({}, item, {
      saveDirty: isSaveDirty(item),
      previewDirty: isPreviewDirty(item)
    });
  }

  function buildSaveRequest(state) {
    const items = state && Array.isArray(state.items) ? state.items : [];
    return {
      items: items
        .filter((item) => item.lessonId == null || isSaveDirty(item))
        .map(toRequestItem)
    };
  }

  function findFirstInvalidSaveItem(state) {
    const items = state && Array.isArray(state.items) ? state.items : [];
    for (const item of items) {
      if (item.lessonId != null && !isSaveDirty(item)) continue;
      const validation = validateDraft(item.draft, priceFieldsChanged(item));
      if (validation) return Object.assign({ clientKey: item.clientKey }, validation);
    }
    return null;
  }

  function validateDraft(source, validatePrice) {
    const draft = canonicalDraft(source);
    if (!draft.name) return invalid('name', '수업상품명을 입력해 주세요.');
    if (draft.schoolName && draft.schoolName.length > 100) {
      return invalid('schoolName', '학교명은 100자 이하로 입력해 주세요.');
    }
    if (draft.gradeLabel && draft.gradeLabel.length > 50) {
      return invalid('gradeLabel', '학년은 50자 이하로 입력해 주세요.');
    }
    // Collection은 amount/unit 독립 nullable 호환 계약을 유지한다. 편집기에서 새로 만드는
    // draft 또는 가격 두 필드 중 하나를 실제로 바꾼 기존 item에만 더 엄격한 tuple을 적용한다.
    if (validatePrice) return validateEditorPrice(draft);
    return null;
  }

  function priceFieldsChanged(item) {
    if (!item || item.lessonId == null) return true;
    const draft = canonicalDraft(item.draft);
    const snapshot = canonicalDraft(item.serverSnapshot);
    return draft.priceAmount !== snapshot.priceAmount || draft.priceUnit !== snapshot.priceUnit;
  }

  function validateEditorPrice(draft) {
    const amount = draft.priceAmount;
    const unit = draft.priceUnit;
    if (amount != null && (!Number.isFinite(amount) || amount <= 0)) {
      return invalid('priceAmount', '가격은 1원 이상으로 입력해 주세요.');
    }
    if (amount == null && unit == null) return null;
    if (amount == null && unit === 'NEGOTIABLE') return null;
    if (amount != null && (unit === 'MONTH' || unit === 'COURSE')) return null;
    if (amount == null && (unit === 'MONTH' || unit === 'COURSE')) {
      return invalid('priceAmount', '가격을 입력해 주세요.');
    }
    if (amount != null && unit === 'NEGOTIABLE') {
      return invalid('priceAmount', '가격 협의일 때는 가격을 비워 주세요.');
    }
    return invalid('priceUnit', '가격 단위를 선택해 주세요.');
  }

  function invalid(field, message) {
    return { field, message };
  }

  function toRequestItem(item) {
    const request = canonicalDraft(item.draft);
    if (item.lessonId == null) {
      request.lessonId = null;
      request.creationKey = item.creationKey;
      request.expectedAuthoringRevision = null;
    } else {
      request.lessonId = item.lessonId;
      request.creationKey = null;
      request.expectedAuthoringRevision = effectiveRevision(item.authoringRevision);
    }
    return request;
  }

  function reconcileSaveSuccess(state, response) {
    const savedItems = response && Array.isArray(response.savedItems) ? response.savedItems : [];
    if (!state || !Array.isArray(state.items) || savedItems.length === 0) return state;

    const byLessonId = new Map();
    const byCreationKey = new Map();
    for (const saved of savedItems) {
      if (saved.lessonId != null) byLessonId.set(String(saved.lessonId), saved);
      if (saved.creationKey) byCreationKey.set(String(saved.creationKey), saved);
    }

    const items = state.items.map((item) => {
      const saved = item.lessonId != null
        ? byLessonId.get(String(item.lessonId))
        : byCreationKey.get(String(item.creationKey));
      if (!saved) return item;
      return Object.assign({}, item, {
        lessonId: saved.lessonId,
        creationKey: saved.creationKey || item.creationKey,
        authoringRevision: effectiveRevision(saved.authoringRevision),
        serverSnapshot: cloneDraft(saved)
      });
    });
    return Object.assign({}, state, { items });
  }

  function rebaseServerState(state, lessons) {
    if (!state || !Array.isArray(state.items)) return state;
    const latestById = new Map((Array.isArray(lessons) ? lessons : [])
      .filter((lesson) => lesson && lesson.lessonId != null)
      .map((lesson) => [String(lesson.lessonId), lesson]));
    const items = state.items.map((item) => {
      if (item.lessonId == null) return item;
      const latest = latestById.get(String(item.lessonId));
      if (!latest) return item;
      return Object.assign({}, item, {
        creationKey: latest.creationKey || item.creationKey,
        authoringRevision: effectiveRevision(latest.authoringRevision),
        serverSnapshot: cloneDraft(latest)
      });
    });
    return Object.assign({}, state, { items });
  }

  function academyLoadAction(status) {
    return Number(status) === 204
      ? { type: 'redirect', location: '/builder' }
      : { type: 'continue' };
  }

  // 단일행 입력에서 Enter가 폼을 암묵적으로 제출해 수업상품이 저장되는 것을 막는다.
  // textarea는 줄바꿈을 유지하고, 버튼은 사용자가 의도적으로 활성화한 경우이므로 통과시킨다.
  function enterSubmitDecision(key, tagName) {
    if (key !== 'Enter') return 'allow';
    const tag = String(tagName || '').toUpperCase();
    return tag === 'TEXTAREA' || tag === 'BUTTON' ? 'allow' : 'block';
  }

  return {
    AUTHORING_FIELDS,
    DEFAULT_DRAFT,
    cloneDraft,
    canonicalDraft,
    createState,
    getActiveItem,
    selectItem,
    updateActiveDraft,
    applyActivePreview,
    addDraft,
    removeItem,
    setCommonTheme,
    isSaveDirty,
    isPreviewDirty,
    getItemView,
    buildSaveRequest,
    findFirstInvalidSaveItem,
    reconcileSaveSuccess,
    rebaseServerState,
    academyLoadAction,
    enterSubmitDecision
  };
});
