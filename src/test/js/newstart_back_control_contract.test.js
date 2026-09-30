'use strict';

// NewStart 내부 상세 화면의 "모바일에서 누르기 쉬운 BACK" 계약.
// 1) 6개 화면에 data-ns-back 컨트롤과 스크립트가 붙어 있어야 하고,
// 2) BACK은 history.back()을 쓰되 직접 진입 시 href 목적지로 빠져나와야 하며(화면 갇힘 방지),
// 3) 기존 a-to-z / practical-tool의 "← NewStart 홈"은 이 작업으로 바뀌면 안 된다.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const tpl = (name) => fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/newstart/' + name), 'utf8');
const js = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/newstart-back.js'), 'utf8');
const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/newstart.css'), 'utf8');

const screens = [
  ['chat.html', '/newstart'],
  ['fss-helper.html', '/newstart'],
  ['galmuri.html', '/newstart'],
  ['questions.html', '/newstart'],
  ['updates.html', '/newstart'],
  ['update_detail.html', '/newstart/updates'],
];

test('NewStart 내부 상세 6화면에 BACK 컨트롤과 newstart-back.js가 연결된다', () => {
  screens.forEach(([file, expectedHref]) => {
    const html = tpl(file);
    assert.match(html, new RegExp('<a class="ns-back" href="' + expectedHref + '" data-ns-back>← 뒤로</a>'), file);
    assert.match(html, /<script src="\/js\/newstart-back\.js\?v=\d{8}-\d+" defer><\/script>/, file);
  });
});

test('BACK은 조건부 history.back()이고, 기록이 없으면 href 목적지로 fallback한다', () => {
  assert.match(js, /window\.history\.back\(\)/);
  // preventDefault는 useHistory가 true일 때만 걸린다(무조건 방지 = 직접 진입 시 화면 갇힘).
  assert.match(js, /if \(!useHistory\) return;/);
  assert.match(js, /window\.history\.length > 1/);
  assert.match(js, /document\.referrer/);
  assert.match(js, /sessionStorage\.getItem\(KEY\)/);
  assert.match(js, /path\.indexOf\('\/newstart'\) === 0/);
});

test('BACK 타깃은 모바일触摸 최소 44px이고 기존 홈 링크 화면은 그대로다', () => {
  assert.match(css, /\.ns-back\s*\{[^}]*min-height:\s*44px/);
  assert.match(tpl('a-to-z.html'), /<a class="ns-back" href="\/newstart">← NewStart 홈<\/a>/);
  assert.match(tpl('practical-tool.html'), /<a class="ns-back" href="\/newstart">← NewStart 홈<\/a>/);
  assert.doesNotMatch(tpl('a-to-z.html'), /data-ns-back/);
  assert.doesNotMatch(tpl('practical-tool.html'), /data-ns-back/);
});
