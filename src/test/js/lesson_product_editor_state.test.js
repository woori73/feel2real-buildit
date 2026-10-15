const assert = require('node:assert/strict');
const test = require('node:test');

const editorState = require('../../main/resources/static/js/lesson_product_editor_state.js');

function lesson(id, name, overrides) {
  return Object.assign({}, editorState.DEFAULT_DRAFT, {
    lessonId: id,
    name,
    authoringRevision: 2,
    currentCount: 3,
    isVisible: true
  }, overrides || {});
}

test('given A is edited when switching B then A then A draft is retained', () => {
  let state = editorState.createState([lesson(1, 'A'), lesson(2, 'B')]);
  state = editorState.selectItem(state, 'lesson:2', { name: 'A edited' });
  state = editorState.selectItem(state, 'lesson:1', { name: 'B edited' });

  assert.equal(editorState.getActiveItem(state).draft.name, 'A edited');
  assert.equal(state.items[1].draft.name, 'B edited');
});

test('given active input when typing then draft changes without changing preview', () => {
  const state = editorState.createState([lesson(1, 'Before')]);
  const changed = editorState.updateActiveDraft(state, { name: 'After' });

  assert.equal(editorState.getActiveItem(changed).draft.name, 'After');
  assert.equal(editorState.getActiveItem(changed).appliedPreview.name, 'Before');
});

test('given two drafts when applying then only active preview changes', () => {
  let state = editorState.createState([lesson(1, 'A'), lesson(2, 'B')]);
  state = editorState.updateActiveDraft(state, { highlight: 'A new' });
  state = editorState.applyActivePreview(state);

  assert.equal(state.items[0].appliedPreview.highlight, 'A new');
  assert.equal(state.items[1].appliedPreview.highlight, '');
});

test('given a changed theme when applying then the theme follows Apply', () => {
  let state = editorState.createState([lesson(1, 'A')]);
  state = editorState.updateActiveDraft(state, { theme: 'PURPLE' });

  assert.equal(editorState.getActiveItem(state).appliedPreview.theme, 'NAVY');
  state = editorState.applyActivePreview(state);
  assert.equal(editorState.getActiveItem(state).appliedPreview.theme, 'PURPLE');
});

test('given existing lessons when adding then a stable keyed draft is appended last and activated', () => {
  const state = editorState.addDraft(
    editorState.createState([lesson(1, 'A')]),
    { name: 'New' },
    () => '11111111-1111-4111-8111-111111111111'
  );

  assert.equal(state.items.length, 2);
  assert.equal(state.items[1].creationKey, '11111111-1111-4111-8111-111111111111');
  assert.equal(state.items[1].clientKey, state.activeKey);
  assert.equal(state.items[1].draft.name, 'New');
  // 편집기가 소유하지 않는 값은 기본값을 넣지 않고 비운 채로 보낸다.
  assert.equal(state.items[1].draft.lessonType, null);
  assert.equal(state.items[1].draft.targetGroup, null);
  assert.equal(state.items[1].draft.schoolName, null);
  assert.equal(state.items[1].draft.gradeLabel, null);
  assert.equal(state.items[1].draft.priceAmount, null);
  assert.equal(state.items[1].draft.priceUnit, null);
  assert.equal(state.items[1].draft.priceSummary, null);
  assert.equal(state.items[1].draft.maxCapacity, null);
  assert.equal(state.items[1].draft.scheduleType, null);
  assert.equal(state.items[1].draft.scheduleSummary, null);
  assert.equal(state.items[1].draft.status, null);
  assert.equal(state.items[1].draft.theme, 'NAVY');
});

// 간소화된 편집 화면(카드 라벨/수업명/한 줄 설명/이런 학생에게/핵심 특징/대상)은 lessonType,
// targetGroup, maxCapacity, scheduleType, weeklyCount, weekdays, startDate, endDate,
// durationWeeks, durationMonths, scheduleSummary, status 에 대한 입력 control이 없다.
// lesson_product_editor.js의 readForm()은 폼에 없는 필드를 patch 객체에서 아예 생략하므로
// (form.elements.namedItem(field)가 null이면 continue), 그 결과를 updateActiveDraft에 넘겨도
// 서버에서 불러온 기존 값이 null로 덮어써지면 안 된다.
test('given a lesson with server-only fields when the form omits those keys then existing values survive the merge', () => {
  const serverLesson = lesson(1, '기존 수업', {
    lessonType: 'SUBSCRIPTION',
    targetGroup: 'MIDDLE',
    maxCapacity: 12,
    scheduleType: 'WEEKLY',
    weeklyCount: 2,
    weekdays: ['MON', 'WED'],
    startDate: '2026-03-02',
    endDate: '2026-07-31',
    durationWeeks: 20,
    durationMonths: null,
    scheduleSummary: '매주 월/수 저녁 7시',
    status: 'PUBLISHED'
  });
  let state = editorState.createState([serverLesson]);
  // readForm()이 만드는 patch: 화면에 있는 필드만 담고, 위 서버 전용 필드는 키 자체가 없다.
  const formOnlyPatch = {
    tabShortName: 'F2R Starter',
    name: '기존 수업 (수정됨)',
    kicker: '한 줄 설명',
    learnerNeed: '이런 학생에게',
    highlight: '핵심 특징',
    gradeLabel: '고등학교 1학년'
  };
  state = editorState.updateActiveDraft(state, formOnlyPatch);
  const draft = editorState.getActiveItem(state).draft;

  assert.equal(draft.name, '기존 수업 (수정됨)');
  assert.equal(draft.lessonType, 'SUBSCRIPTION');
  assert.equal(draft.targetGroup, 'MIDDLE');
  assert.equal(draft.maxCapacity, 12);
  assert.equal(draft.scheduleType, 'WEEKLY');
  assert.equal(draft.weeklyCount, 2);
  assert.deepEqual(draft.weekdays, ['MON', 'WED']);
  assert.equal(draft.startDate, '2026-03-02');
  assert.equal(draft.endDate, '2026-07-31');
  assert.equal(draft.durationWeeks, 20);
  assert.equal(draft.scheduleSummary, '매주 월/수 저녁 7시');
  assert.equal(draft.status, 'PUBLISHED');
});

test('given six-plus lessons when creating state then no state cap is imposed', () => {
  const lessons = Array.from({ length: 8 }, (_, index) => lesson(index + 1, `L${index + 1}`));
  const state = editorState.createState(lessons);

  assert.equal(state.items.length, 8);
  assert.deepEqual(state.items.map((item) => item.lessonId), [1, 2, 3, 4, 5, 6, 7, 8]);
});

test('given snapshots when selecting dirty state then save and preview dirty are derived independently', () => {
  let state = editorState.createState([lesson(1, 'A')]);
  state = editorState.updateActiveDraft(state, { name: ' A ', highlight: 'Changed' });
  let item = editorState.getActiveItem(state);

  assert.equal(editorState.isSaveDirty(item), true);
  assert.equal(editorState.isPreviewDirty(item), true);
  state = editorState.applyActivePreview(state);
  item = editorState.getActiveItem(state);
  assert.equal(editorState.getItemView(item).saveDirty, true);
  assert.equal(editorState.getItemView(item).previewDirty, false);
});

test('given a failed save when no reconciliation occurs then all local state is preserved', () => {
  let state = editorState.createState([lesson(1, 'A'), lesson(2, 'B')]);
  state = editorState.updateActiveDraft(state, { name: 'Unsaved A' });
  const before = structuredClone(state);

  editorState.buildSaveRequest(state);
  assert.deepEqual(state, before);
});

test('given a successful create when reconciling then identity revision and snapshot update without duplication or preview replacement', () => {
  let state = editorState.createState([], {
    keyFactory: () => '22222222-2222-4222-8222-222222222222'
  });
  state = editorState.updateActiveDraft(state, { name: 'Saved', highlight: 'Unapplied' });
  const responseItem = Object.assign({}, state.items[0].draft, {
    lessonId: 91,
    creationKey: state.items[0].creationKey,
    authoringRevision: 1
  });
  state = editorState.reconcileSaveSuccess(state, { savedItems: [responseItem] });

  assert.equal(state.items.length, 1);
  assert.equal(state.items[0].lessonId, 91);
  assert.equal(state.items[0].authoringRevision, 1);
  assert.equal(editorState.isSaveDirty(state.items[0]), false);
  assert.equal(state.items[0].appliedPreview.highlight, '');
});

test('given unapplied text was saved when reconciling then preview stays dirty until Apply', () => {
  let state = editorState.createState([lesson(1, 'Before')]);
  state = editorState.updateActiveDraft(state, { name: 'Saved but unapplied' });
  state = editorState.reconcileSaveSuccess(state, {
    savedItems: [lesson(1, 'Saved but unapplied', { authoringRevision: 3 })]
  });

  assert.equal(editorState.isSaveDirty(state.items[0]), false);
  assert.equal(editorState.isPreviewDirty(state.items[0]), true);
  state = editorState.applyActivePreview(state);
  assert.equal(editorState.isPreviewDirty(state.items[0]), false);
});

test('given mixed items when building request then every new and only dirty existing are included with safe authoring fields', () => {
  let state = editorState.createState([lesson(1, 'Dirty'), lesson(2, 'Clean')]);
  state = editorState.updateActiveDraft(state, { name: 'Dirty changed' });
  state = editorState.addDraft(state, {}, () => '33333333-3333-4333-8333-333333333333');
  const request = editorState.buildSaveRequest(state);

  assert.equal(request.items.length, 2);
  assert.equal(request.items[0].lessonId, 1);
  assert.equal(request.items[0].expectedAuthoringRevision, 2);
  assert.equal(request.items[1].creationKey, '33333333-3333-4333-8333-333333333333');
  for (const item of request.items) {
    assert.equal('currentCount' in item, false);
    assert.equal('isVisible' in item, false);
  }
});

test('given preview-only dirty existing and untouched new when building request then clean existing is omitted and new is included', () => {
  let state = editorState.createState([lesson(1, 'Existing')]);
  state = editorState.updateActiveDraft(state, { highlight: 'Preview only' });
  state = editorState.applyActivePreview(state);
  state = editorState.updateActiveDraft(state, { highlight: '' });
  state = editorState.addDraft(state, {}, () => '44444444-4444-4444-8444-444444444444');
  const request = editorState.buildSaveRequest(state);

  assert.equal(editorState.isSaveDirty(state.items[0]), false);
  assert.equal(editorState.isPreviewDirty(state.items[0]), true);
  assert.equal(request.items.length, 1);
  assert.equal(request.items[0].creationKey, '44444444-4444-4444-8444-444444444444');
});

test('given Academy me responds 204 when resolving load action then Builder redirect wins over draft creation', () => {
  assert.deepEqual(editorState.academyLoadAction(204), {
    type: 'redirect',
    location: '/builder'
  });
  assert.deepEqual(editorState.academyLoadAction(200), { type: 'continue' });
});

test('given a stale save when rebasing then latest server revision updates while local draft and preview stay intact', () => {
  // given
  let state = editorState.createState([lesson(1, 'server-v1', { authoringRevision: 1 })]);
  state = editorState.updateActiveDraft(state, { name: 'local-draft' });
  state = editorState.applyActivePreview(state);
  state = editorState.updateActiveDraft(state, { name: 'local-after-preview' });

  // when
  const rebased = editorState.rebaseServerState(state, [
    lesson(1, 'server-v2', { authoringRevision: 2 })
  ]);

  // then
  assert.equal(rebased.items[0].authoringRevision, 2);
  assert.equal(rebased.items[0].serverSnapshot.name, 'server-v2');
  assert.equal(rebased.items[0].draft.name, 'local-after-preview');
  assert.equal(rebased.items[0].appliedPreview.name, 'local-draft');
  assert.equal(editorState.isSaveDirty(rebased.items[0]), true);
  assert.equal(editorState.isPreviewDirty(rebased.items[0]), true);
});

test('given an invalid inactive new draft when validating save then its tab and field are identified before fetch', () => {
  // given — 수업상품명만 필수다. 비활성 탭의 누락도 저장 전에 잡아야 한다.
  let state = editorState.createState([lesson(1, 'valid')]);
  state = editorState.addDraft(state, { name: '' }, () => '44444444-4444-4444-8444-444444444444');
  state = editorState.selectItem(state, 'lesson:1');

  // when
  const invalid = editorState.findFirstInvalidSaveItem(state);

  // then
  assert.deepEqual(invalid, {
    clientKey: 'creation:44444444-4444-4444-8444-444444444444',
    field: 'name',
    message: '수업상품명을 입력해 주세요.'
  });
});

test('given drafts without price capacity or schedule when validating save then nothing is blocked', () => {
  // given — 편집기가 받지 않는 값이 비어 있는 것은 정상이다.
  let state = editorState.createState([lesson(1, 'valid')]);
  state = editorState.addDraft(state, { name: '예시 기준 수업' },
    () => '55555555-5555-4555-8555-555555555555');

  // when
  const invalid = editorState.findFirstInvalidSaveItem(state);

  // then
  assert.equal(invalid, null);
});

test('given school and grade text when canonicalising then blanks become null and values are trimmed', () => {
  // given
  const source = { schoolName: '  현대고등학교  ', gradeLabel: '   ' };

  // when
  const canonical = editorState.canonicalDraft(source);

  // then
  assert.equal(canonical.schoolName, '현대고등학교');
  assert.equal(canonical.gradeLabel, null);
});

test('given valid editor price tuples when validating save then each tuple is accepted', () => {
  // given
  const validPrices = [
    { priceAmount: null, priceUnit: null },
    { priceAmount: 100000, priceUnit: 'MONTH' },
    { priceAmount: 500000, priceUnit: 'COURSE' },
    { priceAmount: null, priceUnit: 'NEGOTIABLE' }
  ];

  // when & then
  for (const price of validPrices) {
    const state = editorState.createState([], {
      newDraftValues: Object.assign({ name: '가격 검증 수업' }, price),
      keyFactory: () => `valid-${price.priceUnit || 'empty'}`
    });
    assert.equal(editorState.findFirstInvalidSaveItem(state), null);
  }
});

test('given invalid new editor price tuples when validating save then network submission is blocked', () => {
  // given
  const invalidPrices = [
    { priceAmount: 100000, priceUnit: null },
    { priceAmount: null, priceUnit: 'MONTH' },
    { priceAmount: null, priceUnit: 'COURSE' },
    { priceAmount: 100000, priceUnit: 'NEGOTIABLE' },
    { priceAmount: 0, priceUnit: 'MONTH' },
    { priceAmount: -1, priceUnit: 'COURSE' }
  ];

  // when & then
  for (const price of invalidPrices) {
    const state = editorState.createState([], {
      newDraftValues: Object.assign({ name: '잘못된 가격 수업' }, price),
      keyFactory: () => `invalid-${String(price.priceAmount)}-${String(price.priceUnit)}`
    });
    const invalid = editorState.findFirstInvalidSaveItem(state);
    assert.ok(invalid);
    assert.ok(['priceAmount', 'priceUnit'].includes(invalid.field));
  }
});

test('given unchanged legacy positive amount without unit when validating then it remains compatible', () => {
  // given — 기존 collection 데이터는 amount/unit이 독립 nullable이었다.
  const state = editorState.createState([
    lesson(1, 'legacy amount only', { priceAmount: 100000, priceUnit: null })
  ]);

  // when
  const invalid = editorState.findFirstInvalidSaveItem(state);

  // then
  assert.equal(editorState.isSaveDirty(state.items[0]), false);
  assert.equal(invalid, null);
});

test('given school-only edit on legacy positive amount without unit when validating then price is preserved', () => {
  // given
  let state = editorState.createState([
    lesson(1, 'legacy amount only', { priceAmount: 100000, priceUnit: null })
  ]);

  // when
  state = editorState.updateActiveDraft(state, { schoolName: '현대고등학교' });
  const invalid = editorState.findFirstInvalidSaveItem(state);
  const request = editorState.buildSaveRequest(state);

  // then
  assert.equal(invalid, null);
  assert.equal(request.items[0].schoolName, '현대고등학교');
  assert.equal(request.items[0].priceAmount, 100000);
  assert.equal(request.items[0].priceUnit, null);
});

test('given grade-only edit on legacy positive amount without unit when validating then price is preserved', () => {
  // given
  let state = editorState.createState([
    lesson(1, 'legacy amount only', { priceAmount: 100000, priceUnit: null })
  ]);

  // when
  state = editorState.updateActiveDraft(state, { gradeLabel: '고등학교 2학년' });
  const invalid = editorState.findFirstInvalidSaveItem(state);
  const request = editorState.buildSaveRequest(state);

  // then
  assert.equal(invalid, null);
  assert.equal(request.items[0].gradeLabel, '고등학교 2학년');
  assert.equal(request.items[0].priceAmount, 100000);
  assert.equal(request.items[0].priceUnit, null);
});

test('given legacy amount-only price when converted to a valid tuple then validation accepts it', () => {
  // given
  let state = editorState.createState([
    lesson(1, 'legacy amount only', { priceAmount: 100000, priceUnit: null })
  ]);

  // when
  state = editorState.updateActiveDraft(state, { priceUnit: 'MONTH' });

  // then
  assert.equal(editorState.findFirstInvalidSaveItem(state), null);
});

test('given fixed price when cleared to null tuple then validation accepts it', () => {
  // given
  let state = editorState.createState([
    lesson(1, 'fixed price', { priceAmount: 100000, priceUnit: 'COURSE' })
  ]);

  // when
  state = editorState.updateActiveDraft(state, { priceAmount: null, priceUnit: null });

  // then
  assert.equal(editorState.findFirstInvalidSaveItem(state), null);
});

test('given legacy positive amount without unit when price is edited then the tuple is rejected', () => {
  // given
  let state = editorState.createState([
    lesson(1, 'legacy amount only', { priceAmount: 100000, priceUnit: null })
  ]);

  // when
  state = editorState.updateActiveDraft(state, { priceAmount: 120000 });
  const invalid = editorState.findFirstInvalidSaveItem(state);

  // then
  assert.deepEqual(invalid, {
    clientKey: 'lesson:1',
    field: 'priceUnit',
    message: '가격 단위를 선택해 주세요.'
  });
});

test('given Enter in a single-line field when deciding then submit is blocked while textarea and buttons pass', () => {
  // given
  const decide = editorState.enterSubmitDecision;

  // when & then
  assert.equal(decide('Enter', 'INPUT'), 'block');
  assert.equal(decide('Enter', 'SELECT'), 'block');
  assert.equal(decide('Enter', 'TEXTAREA'), 'allow');
  assert.equal(decide('Enter', 'BUTTON'), 'allow');
});

test('given non-Enter keys or a missing target when deciding then typing is never blocked', () => {
  // given
  const decide = editorState.enterSubmitDecision;

  // when & then
  assert.equal(decide('a', 'INPUT'), 'allow');
  assert.equal(decide('Tab', 'INPUT'), 'allow');
  assert.equal(decide('Escape', 'SELECT'), 'allow');
  assert.equal(decide('Enter', undefined), 'block');
  assert.equal(decide('Enter', 'textarea'), 'allow');
});

test('given a selected lesson when removing then the tab goes away and a neighbour is activated', () => {
  // given
  let state = editorState.createState([lesson(1, 'A'), lesson(2, 'B'), lesson(3, 'C')]);
  state = editorState.selectItem(state, 'lesson:2');

  // when
  state = editorState.removeItem(state, 'lesson:2');

  // then
  assert.equal(state.items.length, 2);
  assert.deepEqual(state.items.map((i) => i.draft.name), ['A', 'C']);
  assert.equal(state.activeKey, 'lesson:3');
});

test('given the last lesson when removing then an empty draft replaces it', () => {
  // given
  let state = editorState.createState([lesson(1, 'only')]);

  // when
  state = editorState.removeItem(state, 'lesson:1', () => '99999999-9999-4999-8999-999999999999');

  // then
  assert.equal(state.items.length, 1);
  assert.equal(state.items[0].lessonId, null);
  assert.equal(state.items[0].draft.name, '');
  assert.equal(state.activeKey, state.items[0].clientKey);
});

test('given an unknown key when removing then state is unchanged', () => {
  // given
  const state = editorState.createState([lesson(1, 'A')]);

  // when
  const next = editorState.removeItem(state, 'lesson:404');

  // then
  assert.equal(next, state);
});

test('given a common theme change when applying then every lesson draft and preview follow', () => {
  // given — 배경 스타일은 수업별이 아니라 학원 공통이다.
  let state = editorState.createState([lesson(1, 'A'), lesson(2, 'B')]);

  // when
  state = editorState.setCommonTheme(state, 'TEAL');

  // then
  for (const item of state.items) {
    assert.equal(item.draft.theme, 'TEAL');
    assert.equal(item.appliedPreview.theme, 'TEAL');
  }
});

/* ── 대표 미디어 ────────────────────────────────────────────── */

test('given a lesson without media when attaching an image then the draft is dirty', () => {
  // given — 미디어가 AUTHORING_FIELDS에 없으면 readForm이 건너뛰어 dirty로 잡히지 않는다.
  let state = editorState.createState([lesson(1, 'A')]);

  // when
  state = editorState.updateActiveDraft(state, { mediaImageKey: 'hero/abc.jpg' });

  // then
  const item = editorState.getActiveItem(state);
  assert.equal(item.draft.mediaImageKey, 'hero/abc.jpg');
  assert.equal(editorState.isSaveDirty(item), true);
});

test('given an image when switching to a video then only the video id remains dirty', () => {
  // given
  let state = editorState.createState([lesson(1, 'A', { mediaImageKey: 'hero/abc.jpg' })]);

  // when
  state = editorState.updateActiveDraft(state, { mediaImageKey: null, mediaVideoId: 12 });

  // then
  const item = editorState.getActiveItem(state);
  assert.equal(item.draft.mediaImageKey, null);
  assert.equal(item.draft.mediaVideoId, 12);
});

test('given a video id as string when canonicalising then it becomes a number', () => {
  // given — hidden input은 항상 문자열을 준다. 숫자로 정규화되지 않으면 저장 직후
  // 서버 응답과 비교할 때 매번 dirty로 잡힌다.
  const canonical = editorState.canonicalDraft({ mediaVideoId: '12' });

  // then
  assert.equal(canonical.mediaVideoId, 12);
});

test('given an empty media id when canonicalising then it becomes null', () => {
  // given — 해제 직후 hidden input은 빈 문자열이다.
  const canonical = editorState.canonicalDraft({ mediaVideoId: '', mediaImageKey: '' });

  // then
  assert.equal(canonical.mediaVideoId, null);
  assert.equal(canonical.mediaImageKey, null);
});

test('given hero media when attaching an OT video then hero stays unchanged', () => {
  // given — 대표 미디어와 OT 영상은 별도 authoring field다.
  let state = editorState.createState([
    lesson(1, 'A', { mediaImageKey: 'hero/abc.jpg', mediaVideoId: null, otVideoId: null })
  ]);

  // when
  state = editorState.updateActiveDraft(state, { otVideoId: '34' });

  // then
  const item = editorState.getActiveItem(state);
  assert.equal(item.draft.mediaImageKey, 'hero/abc.jpg');
  assert.equal(item.draft.mediaVideoId, null);
  assert.equal(item.draft.otVideoId, '34');
  assert.equal(editorState.canonicalDraft(item.draft).otVideoId, 34);
});
