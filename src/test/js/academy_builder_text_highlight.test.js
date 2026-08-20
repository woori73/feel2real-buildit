const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { loadBrowserScript } = require('./helpers/browser_script_harness');

const BUILDER = path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js');

test('given hero academy name when highlighting then only the editable data node is selected', () => {
  // given
  const loaded = loadBrowserScript(BUILDER);
  const hooks = loaded.window.F2RAcademyBuilderTestHooks;

  // when
  const selectors = Array.from(hooks.textHighlightSelectors('name'));

  // then
  assert.deepEqual(selectors, ['[data-builder-text="name"]']);
  assert.ok(!selectors.includes('.cv-h1'));
});

test('given academy intro when highlighting then only the editable data nodes are selected', () => {
  // given
  const loaded = loadBrowserScript(BUILDER);
  const hooks = loaded.window.F2RAcademyBuilderTestHooks;

  // when
  const selectors = Array.from(hooks.textHighlightSelectors('intro'));

  // then
  assert.deepEqual(selectors, ['[data-builder-text="intro"]']);
});
