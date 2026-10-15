const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = (p) => fs.readFileSync(path.resolve(__dirname, p), 'utf8');

test('builder and public academy share the mobile-first brand registry', () => {
  const builder = read('../../main/resources/templates/tutor/academy_builder.html');
  const publicHtml = read('../../main/resources/templates/public/academy_public.html');
  const builderJs = read('../../main/resources/static/js/academy_builder.js');
  const publicJs = read('../../main/resources/static/js/academy_public.js');
  assert.match(builder, /academy_brand\.css/);
  assert.match(builder, /academy_brand\.js/);
  assert.match(publicHtml, /academy_brand\.css/);
  assert.match(publicHtml, /academy_brand\.js/);
  assert.match(builderJs, /wireBrandDesign/);
  assert.match(builderJs, /previewBrandProposal/);
  assert.match(builderJs, /undoBrandDesign/);
  assert.match(publicJs, /BRAND\.apply\(root, academy\)/);
});

test('brand panel exposes desktop and mobile logo plus readable mobile typography controls', () => {
  const builder = read('../../main/resources/templates/tutor/academy_builder.html');
  ['logoSizeDesktop','logoSizeMobile','headingSizeMobile','bodySizeMobile'].forEach((key) => assert.match(builder, new RegExp('data-brand-key="'+key+'"')));
  assert.match(builder, /압구정에듀홀릭학원/);
  assert.match(builder, /data-brand-font="NOTO_SERIF_KR"/);
});
