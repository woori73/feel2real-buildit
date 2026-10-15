const assert = require('node:assert/strict');
const test = require('node:test');

const guide = require('../../main/resources/static/js/academy_builder_editable_guide.js');

test('given Builder edit guide when selecting targets then only wired edit affordances are included', () => {
  // given / when
  const selector = guide.EDITABLE_SELECTOR;

  // then
  assert.match(selector, /\[data-builder-text\]/);
  assert.match(selector, /\[data-builder-draft\]/);
  assert.match(selector, /\[data-builder-logo\]/);
  assert.match(selector, /\[data-builder-basic\]/);
  assert.match(selector, /\[data-hero-video-edit\]/);
  assert.match(selector, /\[data-principal-video-edit\]/);
  assert.match(selector, /\[data-social-url-edit\]/);
  assert.match(selector, /\[data-business-info-field\]/);
  assert.doesNotMatch(selector, /\[data-business-info-footer\]/);
  assert.match(selector, /\.browser-url/);
  assert.doesNotMatch(selector, /data-builder-section-key/);
  assert.doesNotMatch(selector, /canvas-block/);
  assert.doesNotMatch(selector, /builder-coming-soon/);
});

test('given first Builder visit when coachmark state is read then it shows once after acknowledgement', () => {
  // given
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => values.set(key, value)
  };

  // when / then
  assert.equal(guide.shouldShowCoachmark(storage), true);
  guide.markCoachmarkSeen(storage);
  assert.equal(guide.shouldShowCoachmark(storage), false);
});

test('given unavailable browser storage when coachmark state is used then the Builder remains usable', () => {
  // given
  const storage = {
    getItem: () => { throw new Error('blocked'); },
    setItem: () => { throw new Error('blocked'); }
  };

  // when / then
  assert.equal(guide.shouldShowCoachmark(storage), true);
  assert.doesNotThrow(() => guide.markCoachmarkSeen(storage));
});
