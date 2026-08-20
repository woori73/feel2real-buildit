const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');
const compact = (value) => value.replace(/\s+/g, '');

const publicJs = read('../../main/resources/static/js/academy_public.js');
const publicCss = compact(read('../../main/resources/static/css/academy_public.css'));
const footerCss = compact(read('../../main/resources/static/css/academy_business_footer.css'));

test('given 공개 학원 페이지 when 방문 통계를 렌더 then footer counter로 표시한다', () => {
  assert.match(publicJs, /<span id="visitStatistics" class="counter"/);
  assert.match(publicJs, /<span class="d"><\/span>오늘/);
  assert.doesNotMatch(publicJs, /visit-statistics-floating|visit-stat-chart/);
  assert.doesNotMatch(publicCss, /visit-statistics-floating|visit-stat-chart/);
  assert.match(footerCss, /\.academy-business-footer\.counter\{/);
});
