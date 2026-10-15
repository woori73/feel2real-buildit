const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const builderTemplate = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'),
  'utf8'
);
const publicCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_public.css'),
  'utf8'
);

function inlineStyle(template) {
  const match = template.match(/<style>([\s\S]*?)<\/style>/);
  assert.ok(match, 'builder template must contain its inline style block');
  return match[1];
}

function selectorAtLineStart(selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^\\s*${escaped}\\s*\\{`, 'm');
}

test('given builder public preview when shared public sections render then builder does not override their canonical design', () => {
  // given
  const style = inlineStyle(builderTemplate);
  const canonicalSelectors = [
    '.cv-gnb',
    '.cv-nav',
    '.cv-nav a',
    '.cv-btn-primary',
    '.cv-eyebrow',
    '.cv-h1',
    '.cv-h1 .accent',
    '.cv-sub',
    '.cv-cta'
  ];

  // when / then
  canonicalSelectors.forEach((selector) => {
    assert.doesNotMatch(
      style,
      selectorAtLineStart(selector),
      `${selector} must be styled only by academy_public.css`
    );
  });
  assert.match(publicCss, /\.cv-gnb\{[^}]*display:grid;grid-template-columns:1fr auto 1fr;/);
  assert.match(publicCss, /\.cv-gnb>\.eh-logo-component\{grid-column:2;\}/);
});

test('given academy logo image when builder preview renders then public contain sizing is preserved', () => {
  // given
  const style = inlineStyle(builderTemplate);

  // when / then
  assert.doesNotMatch(
    style,
    /\.builder-public-preview \[data-builder-logo\] img\s*\{/,
    'builder editing affordance must not crop or stretch the public logo'
  );
  // 로고가 남은 위치는 GNB/Footer의 .eh-logo-symbol뿐이다(sec-intro 로고는 과목·특징으로 교체됨).
  assert.match(publicCss, /\.eh-logo-symbol img\{[^}]*width:auto;[^}]*object-fit:contain;/);
});
