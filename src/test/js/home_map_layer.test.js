const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const CSS = fs.readFileSync(path.join(__dirname, '../../main/resources/static/css/home_winter.css'), 'utf8');
const JS = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/home_winter.js'), 'utf8');

test('given 서비스 지도 점이 확대될 때 z-index를 받으면 when 지도 칸 스타일을 읽으면 then 지도 칸이 한 층(isolation)으로 묶여 점이 따라오는 문의 버튼·상단 메뉴 위로 올라오지 않는다', () => {
  // given: 점마다 z-index를 준다
  assert.match(JS, /a\.style\.zIndex = Math\.round\(sc \* 100\)/);
  // when
  const rule = CSS.match(/\.hw-net \{[^}]*\}/)[0];
  // then
  assert.match(rule, /isolation: isolate/);
});
