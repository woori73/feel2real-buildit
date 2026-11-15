const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '../../main/resources/templates');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('landing: 예상문제 의뢰는 기존 카톡·이메일로 연결된다(자체 접수 폼 없음)', () => {
  // given
  const html = read('public/landing.html');
  // when / then
  assert.ok(html.includes('https://open.kakao.com/o/sMf4ifRi'));
  assert.ok(html.includes('mailto:woorinaesin@naver.com'));
  assert.ok(!html.includes('/exam-request'));
});

test('landing: /wiki 는 모의고사위키로만 표기하고 폐기 서비스는 노출하지 않는다', () => {
  // given
  const html = read('public/landing.html');
  // when / then
  // 대시보드 바로가기 칸(2026-11-16 삭제)에만 붙여 쓴 「모의고사위키」가 있었다. 메뉴의 「모의고사 위키」로 확인한다.
  assert.ok(html.includes('>모의고사위키<') || html.includes('>모의고사 위키<'));
  ['DesignCraft', 'PhotoCraft', '기출분석', '출제방향', '학습자료실', '고객센터'].forEach((w) => assert.ok(!html.includes(w), w));
});
