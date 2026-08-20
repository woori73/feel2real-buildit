const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const template = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'),
  'utf8');
const builderJs = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js'),
  'utf8');
const footerCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_business_footer.css'),
  'utf8');

test('given Builder blocks when fixed chrome is listed then Hero and Footer are both locked navigation items', () => {
  // given / when / then
  assert.match(template, /data-fixed-section-nav="sec-hero"[\s\S]*?>[\s\S]*?LOCK/);
  assert.match(template, /data-fixed-section-nav="sec-footer"[\s\S]*?>[\s\S]*?LOCK/);
  assert.doesNotMatch(template, /data-section-toggle="sec-footer"/);
});

test('given Builder layout when section rows are rendered then Footer is appended as a locked fixed section', () => {
  // given / when / then
  assert.match(builderJs, /layoutSectionIds[\s\S]*FIXED_FOOTER_SECTION_ID/);
  assert.match(builderJs, /id === 'sec-hero' \|\| id === FIXED_FOOTER_SECTION_ID/);
  assert.match(builderJs, /data-fixed-section-nav=/);
});

test('given edit guide when Footer is highlighted then actual fields are highlighted without a Footer wrapper stroke', () => {
  // given / when / then
  assert.match(template, /builder-editable-guide-active \[data-business-info-field\]/);
  assert.doesNotMatch(template, /builder-editable-guide-active \[data-business-info-footer\]/);
});

test('given Footer editing when the section is selected then it uses the standard section selection instead of a custom yellow stroke', () => {
  // given / when / then
  assert.match(builderJs, /builder-section-style-selected builder-footer-section-editing/);
  assert.doesNotMatch(footerCss, /academy-business-footer\.is-builder-selected/);
});
