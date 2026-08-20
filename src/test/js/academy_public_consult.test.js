const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

/**
 * 발행 학원 페이지 우상단 `상담 신청` → 학원별 연락 모달 배선.
 *
 * 모달 markup은 template(#publicView 밖), 옵션 채우기는 academy_public.js, 연락처 판정은
 * academy_business_info.js에 나뉘어 있어 한쪽만 고치면 버튼이 조용히 죽는다. 이 테스트가 세
 * 파일의 접점을 고정한다.
 */

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const publicTemplate = read('../../main/resources/templates/public/academy_public.html');
const publicJs = read('../../main/resources/static/js/academy_public.js');
const publicCss = read('../../main/resources/static/css/academy_public.css');

const compact = (value) => value.replace(/\s+/g, '');

test('given 발행 페이지 template when 상담 모달을 확인 then 옵션 컨테이너와 배경 닫기가 존재한다', () => {
  // given / when / then — render()가 #publicView를 innerHTML로 갈아치우므로 모달은 그 밖에 있어야 한다
  assert.match(publicTemplate, /id="consultModal"/);
  assert.match(publicTemplate, /id="consultOptions"/);
  assert.match(publicTemplate, /onclick="closeConsultModal\(event\)"/);
  assert.ok(publicTemplate.indexOf('id="consultModal"') > publicTemplate.indexOf('id="publicView"'));
  assert.ok(publicTemplate.indexOf('id="consultModal"') < publicTemplate.indexOf('<script src="/js/api.js">'));
});

test('given 우상단 상담 신청 버튼 when 방문자가 클릭 then requestConsult가 모달을 연다', () => {
  // given / when / then
  assert.match(publicJs, /onclick="requestConsult\(\)"/);
  assert.match(publicJs, /window\.requestConsult = function/);
  assert.match(publicJs, /modal\.style\.display = 'flex'/);
  assert.match(publicJs, /window\.closeConsultModal = function/);
});

test('given render when businessInfo를 읽으면 then 상담 연락 수단은 공용 helper에서만 파생한다', () => {
  // given / when / then — 마스킹 dummy 유입을 막는 판정 로직이 helper 한 곳에만 있어야 한다
  assert.match(publicJs, /consultChannels = BUSINESS_INFO\.contactChannels\(\s*academy\.pageSettings && academy\.pageSettings\.businessInfo\)/);
  assert.doesNotMatch(publicJs, /resolveDisplay/);
});

test('given 학원이 연락처를 입력하지 않은 경우 when 상담 신청을 누르면 then 기존 데모 안내를 유지한다', () => {
  // given / when / then — 상담 접수 백엔드는 아직 없으므로 빈 모달 대신 안내로 떨어뜨린다
  assert.match(publicJs, /if \(!consultChannels\.length\) \{\s*alert\('상담 신청 기능은 준비 중입니다\. \(데모\)'\);/);
});

test('given 모달 옵션 렌더 when 학원 입력값을 넣으면 then escape된 tel/mailto 링크로 노출된다', () => {
  // given / when / then
  assert.match(publicJs, /href="\$\{escapeHtml\(channel\.href\)\}"/);
  assert.match(publicJs, /escapeHtml\(channel\.value\)/);
  assert.ok(compact(publicCss).includes('.consult-contact-option{'));
});

test('given 좁은 화면 when 상담 모달을 열면 then 카드가 viewport 안에 머문다', () => {
  // given / when / then — #publicView 밖이라 공통 box-sizing reset을 못 받는다
  assert.ok(compact(publicCss).includes('#consultModal>div{box-sizing:border-box;'));
  assert.ok(compact(publicCss).includes('#consultModal>div{width:calc(100%-24px)!important;'));
});
