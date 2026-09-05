const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

/**
 * Builder 미리보기 `상담 신청` → 상담 연락처(phone/email) 편집 모달 배선.
 *
 * 저장 위치가 footer 사업자 정보와 같은 `pageSettings.businessInfo`라, 모달이 나머지 5개
 * 필드를 실어 보내지 않으면 저장 시 조용히 지워진다. 그 접점을 여기서 고정한다.
 */

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const builderJs = read('../../main/resources/static/js/academy_builder.js');
const builderTemplate = read('../../main/resources/templates/tutor/academy_builder.html');

const compact = (value) => value.replace(/\s+/g, '');

test('given Builder 미리보기 when 상담 신청 버튼을 렌더링 then 클릭 가능하고 편집 hook을 갖는다', () => {
  // given / when / then — 미리보기의 다른 cv-btn-primary는 pointer-events:none으로 죽어 있다
  assert.match(builderJs, /class="cv-btn-primary builder-consult-edit" data-consult-contact-edit/);
  assert.ok(compact(builderTemplate).includes(
    '.builder-public-preview.cv-btn-primary.builder-consult-edit{pointer-events:auto;cursor:pointer;}'));
});

test('given 상담 신청 클릭 when 핸들러가 붙어 있으면 then 연락처 모달을 연다', () => {
  // given / when / then
  assert.match(builderJs, /closest\('\[data-consult-contact-edit\]'\)/);
  assert.match(builderJs, /function openConsultContactModal\(\)/);
  assert.match(builderJs, /wireConsultContactEditing\(\);/);
});

test('given 연락처 모달 template when 입력 요소를 확인 then 전화·이메일 local·도메인 select가 있다', () => {
  // given / when / then
  assert.match(builderTemplate, /id="builderConsultContactModal"/);
  assert.match(builderTemplate, /id="builderConsultPhoneInput"/);
  assert.match(builderTemplate, /id="builderConsultEmailLocalInput"/);
  assert.match(builderTemplate, /id="builderConsultEmailDomainSelect"/);
  assert.match(builderTemplate, /id="builderConsultEmailCustomField" hidden/);
  assert.match(builderTemplate, /id="builderConsultEmailCustomInput"/);
});

test('given 도메인 select when 옵션을 그리면 then preset 뒤 마지막에 직접입력이 온다', () => {
  // given / when / then — 직접입력을 고르면 숨겨둔 커스텀 입력이 열려야 한다
  assert.match(builderJs, /function renderConsultDomainOptions\(selected\)/);
  assert.match(builderJs, /options\.push\(`<option value="\$\{escapeHtml\(BUSINESS_INFO\.EMAIL_DOMAIN_CUSTOM\)\}"/);
  assert.match(builderJs, /직접입력<\/option>/);
  assert.match(builderJs, /function syncConsultCustomDomainField\(\)/);
  assert.match(builderJs, /field\.hidden = !custom;/);
});

test('given 전화번호 입력 when 타이핑하면 then 공용 helper로 자동 포맷한다', () => {
  // given / when / then
  assert.match(builderJs, /phone\.addEventListener\('input'[\s\S]{0,200}BUSINESS_INFO\.formatPhoneNumber\(phone\.value\)/);
});

test('given 적용 when 저장 payload를 만들면 then 나머지 businessInfo 필드를 보존한 채 phone·email만 덮어쓴다', () => {
  // given / when / then — Object.assign 없이 { phone, email }만 보내면 서버가 5개 필드를 비운다
  assert.match(
    builderJs,
    /Object\.assign\(\s*businessInfoRawFromPreview\(currentPreview\),\s*\{ phone, email \}\)/);
});

test('given local이 빈 경우 when 적용하면 then 도메인 선택과 무관하게 이메일을 지운다', () => {
  // given / when / then — 그러지 않으면 `@naver.com`이 저장된다
  assert.match(builderJs, /localPart \? BUSINESS_INFO\.joinEmail\(localPart, consultSelectedDomain\(\)\) : ''/);
});

test('given 발행 전/후 when 적용하면 then footer와 같은 저장 경로를 태운다', () => {
  // given / when / then — 발행 전은 pending, 발행 후는 page-settings PUT
  assert.match(builderJs, /currentPreview = persistPendingBusinessInfo\(businessInfo\);/);
  assert.match(
    builderJs,
    /pageSettingsWriteBody\(currentPreview\.pageSettings, \{ businessInfo: businessInfo \}\)/);
  // 저장 후 footer 렌더를 갱신해야 편집 결과가 footer에 반영된다
  assert.match(builderJs, /syncPreviewFromAcademy\(academy\);\s*replaceBusinessInfoFooter\(\);/);
});

test('given footer 인라인 편집 중 when 상담 신청을 누르면 then 모달을 열지 않는다', () => {
  // given / when / then — draft/저장 중 값을 모달 prefill이 덮어쓰는 것을 막는다
  assert.match(builderJs, /if \(businessInfoEditor\.phase !== 'idle'\) return;/);
});

test('given footer 전화번호 편집 when 타이핑하면 then 사업자등록번호와 같이 자동 포맷한다', () => {
  // given / when / then
  assert.match(builderJs, /fieldKey === 'businessRegistrationNumber' \|\| fieldKey === 'phone'/);
  assert.match(builderJs, /fieldKey === 'phone'\s*\?\s*BUSINESS_INFO\.formatPhoneNumber\(value\)/);
});

test('given footer 저장 검증 when 전화·이메일을 확인하면 then 모달과 같은 규칙을 공유한다', () => {
  // given / when / then — 검증 규칙이 두 곳에 복제되면 한쪽만 고쳐지는 사고가 난다
  assert.match(builderJs, /function validateContactPair\(phone, email\)/);
  assert.match(builderJs, /return validateContactPair\(raw\.phone, raw\.email\);/);
  assert.match(builderJs, /BUSINESS_INFO\.isValidPhone\(phone\)/);
  assert.match(builderJs, /BUSINESS_INFO\.isValidEmail\(email\)/);
  // 이메일 정규식이 builder에 다시 인라인되어 있으면 규칙이 갈라진다
  assert.doesNotMatch(builderJs, /\[\^\\s@\]\+@\[\^\\s@\]\+/);
});

test('given 빌더 JS when 캐시버스팅을 확인 then template이 갱신된 버전을 로드한다', () => {
  // given / when / then
  assert.match(builderTemplate, /academy_builder\.js\?v=20260905-2/);
  // 섹션 정본 모듈은 소비자보다 먼저 로드되어야 한다(src 속성 기준 — 주석 문구에 걸리지 않게 /js/를 붙인다).
  assert.ok(
    builderTemplate.indexOf('/js/academy_sections.js') < builderTemplate.indexOf('/js/academy_builder.js'),
    'academy_sections.js가 academy_builder.js보다 먼저 로드되지 않는다');
});
