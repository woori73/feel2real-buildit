const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const template = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/landing.html'),
  'utf8'
);
const script = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/landing_buildit.js'),
  'utf8'
);
const thumbnail = path.resolve(
  __dirname,
  '../../main/resources/static/img/landing/buildit-promo-thumbnail.png'
);

test('given the landing hero then it keeps the brand message and exposes a lazy promo card', () => {
  assert.match(template, /원장과 강사를 브랜드로/);
  assert.match(template, /숨은 영어교육자를 발굴하고, 성장시키고, 투자로 연결합니다\./);
  assert.match(template, /data-youtube-id="fK_BdoK8O64"/);
  assert.match(template, /buildit-promo-thumbnail\.png/);
  assert.doesNotMatch(template, /youtube(?:-nocookie)?\.com\/embed/);
  assert.ok(fs.statSync(thumbnail).size > 0);
});

test('given the promo thumbnail when clicked then the player is created in the same card', () => {
  assert.match(script, /youtube-nocookie\.com\/embed/);
  assert.match(script, /card\.replaceChildren\(iframe\)/);
  assert.match(script, /\?autoplay=1/);
});
