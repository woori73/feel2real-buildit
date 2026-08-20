const assert = require('node:assert/strict');
const test = require('node:test');

const editor = require('../../main/resources/static/js/academy_business_info_editor.js');

test('given idle editor when footer is selected then raw snapshot enters editing without fallback values', () => {
  // given
  const raw = { businessName: '', email: '' };

  // when
  const state = editor.begin(raw);

  // then
  assert.equal(state.phase, 'editing');
  assert.deepEqual(state.snapshotRaw, raw);
  assert.deepEqual(state.draftRaw, raw);
  assert.notEqual(state.snapshotRaw, raw);
  assert.equal(state.dirty, false);
});

test('given footer states when deciding outside selection then dirty auto-applies while saving remains blocked', () => {
  // given / when / then
  assert.equal(editor.outsideDecision(editor.idle()), 'allow');
  assert.equal(editor.outsideDecision(editor.begin({})), 'cancel-allow');
  assert.equal(editor.outsideDecision(editor.withDraft(editor.begin({}), { phone: '0' }, true)), 'apply-allow');
  assert.equal(editor.outsideDecision(editor.beginSaving(editor.begin({})).state), 'block-saving');
});

test('given keyboard input when editing then Escape cancels, modified Enter applies, plain Enter blocks, and Tab remains usable', () => {
  // given
  const editing = editor.begin({});

  // when / then
  assert.equal(editor.keyDecision(editing, 'Escape', false, false), 'cancel');
  assert.equal(editor.keyDecision(editing, 'Enter', true, false), 'apply');
  assert.equal(editor.keyDecision(editing, 'Enter', false, false), 'block');
  assert.equal(editor.keyDecision(editing, 'Tab', false, false), 'allow');
  assert.equal(editor.keyDecision(editor.idle(), 'Enter', false, true), 'allow');
});

test('given Apply is already saving when Apply repeats then only the first transition is accepted', () => {
  // given
  const first = editor.beginSaving(editor.begin({ businessName: '학원명' }));

  // when
  const repeated = editor.beginSaving(first.state);

  // then
  assert.equal(first.accepted, true);
  assert.equal(first.state.phase, 'saving');
  assert.equal(repeated.accepted, false);
  assert.equal(repeated.state.phase, 'saving');
});

test('given authoritative request storage throws when persisting then preview cache is never written', () => {
  // given
  let previewWrites = 0;
  const options = pendingOptions({
    writeRequest: () => { throw new Error('quota'); },
    writePreview: () => { previewWrites += 1; }
  });

  // when / then
  assert.throws(() => editor.persistPending(options), /quota/);
  assert.equal(previewWrites, 0);
});

test('given preview cache storage throws after request commit when persisting then authoritative request and next preview still succeed', () => {
  // given
  let committedRequest = null;
  const options = pendingOptions({
    writeRequest: (value) => { committedRequest = value; },
    writePreview: () => { throw new Error('cache quota'); }
  });

  // when
  const result = editor.persistPending(options);

  // then
  assert.equal(committedRequest.pageSettings.businessInfo.businessName, '마스킹 학원');
  assert.equal(result.nextPreview.pageSettings.businessInfo.businessName, '마스킹 학원');
  assert.equal(result.previewCacheWritten, false);
});

test('given PUT failure when returning to editing then draft and dirty state remain available', () => {
  // given
  const dirty = editor.withDraft(editor.begin({ businessName: '' }), { businessName: '마스킹 학원' }, true);
  const saving = editor.beginSaving(dirty).state;

  // when
  const failed = editor.fail(saving, '저장 실패', 'businessName');

  // then
  assert.equal(failed.phase, 'editing');
  assert.equal(failed.draftRaw.businessName, '마스킹 학원');
  assert.equal(failed.dirty, true);
  assert.equal(failed.errorField, 'businessName');
});

test('given inline footer editing when one value changes then only that raw field changes and the draft becomes dirty', () => {
  // given
  const editing = editor.begin({ businessName: '학원명', representative: '대표자' });

  // when
  const changed = editor.withFieldValue(editing, 'representative', '새 대표자');

  // then
  assert.equal(changed.draftRaw.businessName, '학원명');
  assert.equal(changed.draftRaw.representative, '새 대표자');
  assert.equal(changed.dirty, true);
});

test('given a changed inline footer value when Escape reverts that field then its snapshot raw is restored', () => {
  // given
  const changed = editor.withFieldValue(
    editor.begin({ businessName: '', representative: '대표자' }),
    'businessName',
    '화면에만 입력한 값');

  // when
  const reverted = editor.revertField(changed, 'businessName');

  // then
  assert.equal(reverted.draftRaw.businessName, '');
  assert.equal(reverted.draftRaw.representative, '대표자');
  assert.equal(reverted.dirty, false);
});

function pendingOptions(overrides) {
  return Object.assign({
    request: { pageSettings: { sectionVisibility: { 'sec-intro': true } } },
    cachedPreview: { pageSettings: {} },
    currentPreview: null,
    raw: { businessName: '마스킹 학원' },
    previewFromRequest: (request) => ({ pageSettings: request.pageSettings }),
    mergePreview: (preview, request) => ({
      pageSettings: Object.assign({}, preview.pageSettings, {
        businessInfo: request.pageSettings.businessInfo
      })
    }),
    writeRequest: () => {},
    writePreview: () => {}
  }, overrides);
}
