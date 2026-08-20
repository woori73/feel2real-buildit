const assert = require('node:assert/strict');
const test = require('node:test');

const utils = require('../../main/resources/static/js/academy_builder_text_utils.js');

function target(attributes, textValues) {
  return {
    innerText: textValues && textValues.innerText,
    textContent: textValues && textValues.textContent,
    getAttribute(name) {
      return Object.prototype.hasOwnProperty.call(attributes, name) ? attributes[name] : null;
    }
  };
}

test('given CRLF and CR input when normalizing multiline text then line endings become LF', () => {
  assert.equal(utils.normalizeMultilineText('first\r\nsecond\rthird'), 'first\nsecond\nthird');
});

test('given NBSP input when normalizing text then NBSP becomes a regular space', () => {
  assert.equal(utils.normalizeSingleLineText('Alpha\u00a0\u00a0Beta'), 'Alpha Beta');
  assert.equal(utils.normalizeMultilineText('Alpha\u00a0\u00a0Beta'), 'Alpha Beta');
});

test('given trailing horizontal whitespace when normalizing multiline text then each line is trimmed', () => {
  assert.equal(utils.normalizeMultilineText('first   \nsecond\t\t\nthird  '), 'first\nsecond\nthird');
});

test('given excessive blank lines when normalizing multiline text then blank lines collapse to one intentional gap', () => {
  assert.equal(utils.normalizeMultilineText('first\n\n\n\nsecond'), 'first\n\nsecond');
});

test('given whitespace-only values when normalizing text then result is empty string', () => {
  assert.equal(utils.normalizeSingleLineText('\r\n\t \u00a0'), '');
  assert.equal(utils.normalizeMultilineText('\r\n\t \u00a0'), '');
});

test('given newlines and tabs when normalizing single-line text then text is flattened', () => {
  assert.equal(utils.normalizeSingleLineText(' first\r\nsecond\tthird\nfourth '), 'first second third fourth');
});

test('given builder attributes when classifying fields then configured taxonomy is applied', () => {
  assert.equal(utils.builderTextFieldKind(target({ 'data-builder-text': 'name' })), 'singleLine');
  assert.equal(utils.builderTextFieldKind(target({ 'data-builder-text': 'intro' })), 'multiline');
  assert.equal(utils.builderTextFieldKind(target({ 'data-builder-text': 'unknown' })), 'singleLine');
  assert.equal(utils.builderTextFieldKind(target({ 'data-builder-draft': 'introHeading' })), 'singleLine');
  assert.equal(utils.builderTextFieldKind(target({ 'data-builder-draft': 'introBodySecondary' })), 'multiline');
  assert.equal(utils.builderTextFieldKind(target({ 'data-builder-draft': 'unknown' })), 'multiline');
  assert.equal(utils.builderTextFieldKind(target({})), 'multiline');
});

test('given text targets when checking single-line status then it follows field kind', () => {
  assert.equal(utils.isSingleLineTextTarget(target({ 'data-builder-draft': 'introHeading' })), true);
  assert.equal(utils.isSingleLineTextTarget(target({ 'data-builder-draft': 'introBodySecondary' })), false);
});

test('given editable target with innerText and textContent when extracting text then innerText is preferred', () => {
  assert.equal(utils.extractEditableText(target({}, { innerText: 'visible text', textContent: 'fallback text' })), 'visible text');
});

test('given editable target without innerText when extracting text then textContent is used', () => {
  assert.equal(utils.extractEditableText(target({}, { textContent: 'fallback text' })), 'fallback text');
  assert.equal(utils.extractEditableText({}), '');
  assert.equal(utils.extractEditableText(null), '');
});
