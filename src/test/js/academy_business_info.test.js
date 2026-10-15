const assert = require('node:assert/strict');
const test = require('node:test');

const businessInfo = require('../../main/resources/static/js/academy_business_info.js');

test('given business metadata when listing editable keys then only seven persisted fields are exposed', () => {
  assert.deepEqual(businessInfo.EDITABLE_KEYS, [
    'businessName',
    'representative',
    'address',
    'businessRegistrationNumber',
    'mailOrderRegistrationNumber',
    'phone',
    'email'
  ]);
  assert.equal(businessInfo.EDITABLE_KEYS.includes('businessInfoUrl'), false);
});

test('given partial raw metadata when normalizing then values are trimmed and blanks remain blank', () => {
  const normalized = businessInfo.normalizeRaw({ businessName: '  샘플 학원  ', phone: '   ' });

  assert.deepEqual(normalized, {
    businessName: '샘플 학원',
    representative: '',
    address: '',
    businessRegistrationNumber: '',
    mailOrderRegistrationNumber: '',
    phone: '',
    email: ''
  });
});

test('given blank raw metadata when resolving display then every field uses a masked fallback without mutating raw', () => {
  // 상호는 학원명으로 폴백하지 않는다 — 법인명과 학원 브랜드명이 다를 수 있어
  // 학원명을 법적 상호로 표기하게 된다.
  const raw = businessInfo.normalizeRaw({ businessName: '', phone: '' });
  const display = businessInfo.resolveDisplay(raw);

  assert.deepEqual(display, {
    businessName: '학원명',
    representative: '대표자명',
    address: '00시 00구 00로 00',
    businessRegistrationNumber: '000-00-00000',
    mailOrderRegistrationNumber: '0000-00-0000-0000',
    phone: '000-0000-0000',
    email: 'academy@example.com'
  });
  assert.equal(raw.businessName, '');
  assert.equal(raw.phone, '');
});

test('given formatted registration when deriving FTC URL then only ten digits become wrkr_no', () => {
  assert.equal(businessInfo.registrationDigits('000-00-00000'), '0000000000');
  assert.equal(
    businessInfo.deriveFtcUrl('000-00-00000'),
    'https://www.ftc.go.kr/bizCommPop.do?wrkr_no=0000000000'
  );
  assert.equal(businessInfo.deriveFtcUrl('000-00-0000'), '');
});

test('given registration digits when typing then hyphens are inserted as 000-00-00000 and excess input is ignored', () => {
  assert.equal(businessInfo.formatRegistrationNumber('1'), '1');
  assert.equal(businessInfo.formatRegistrationNumber('1234'), '123-4');
  assert.equal(businessInfo.formatRegistrationNumber('123456'), '123-45-6');
  assert.equal(businessInfo.formatRegistrationNumber('123-45-67890'), '123-45-67890');
  assert.equal(businessInfo.formatRegistrationNumber('123a4567890123'), '123-45-67890');
});

test('given arbitrary legacy URL when rendering then registration remains the only link source', () => {
  const html = businessInfo.renderRows({
    businessRegistrationNumber: '000-00-00000',
    businessInfoUrl: 'https://example.invalid/ignored'
  });

  assert.match(html, /wrkr_no=0000000000/);
  assert.doesNotMatch(html, /example\.invalid/);
});

test('given invalid legacy registration when rendering then stored number remains visible and FTC navigation is disabled', () => {
  const html = businessInfo.renderRows({ businessRegistrationNumber: 'legacy-invalid' });

  assert.match(html, /legacy-invalid/);
  assert.match(html, /aria-disabled="true"/);
  assert.doesNotMatch(html, /bizCommPop/);
});

test('given blank registration when rendering then masked number and masked FTC link are display-only', () => {
  const raw = businessInfo.normalizeRaw({ businessRegistrationNumber: '' });
  const html = businessInfo.renderRows(raw);

  assert.equal(raw.businessRegistrationNumber, '');
  assert.match(html, /000-00-00000/);
  assert.match(html, /wrkr_no=0000000000/);
});

test('given both phone and email when collecting consult channels then phone comes first with dialable tel href', () => {
  // given
  const raw = { phone: '010-1234-5678', email: 'hello@academy.co.kr' };

  // when
  const channels = businessInfo.contactChannels(raw);

  // then
  assert.deepEqual(channels, [
    { key: 'phone', label: '전화하기', icon: '📞', value: '010-1234-5678', href: 'tel:01012345678' },
    { key: 'email', label: '이메일 문의', icon: '✉️', value: 'hello@academy.co.kr', href: 'mailto:hello@academy.co.kr' }
  ]);
});

test('given only one contact when collecting consult channels then the missing channel is omitted', () => {
  // given / when
  const phoneOnly = businessInfo.contactChannels({ phone: ' 02 555 1234 ', email: '   ' });
  const emailOnly = businessInfo.contactChannels({ email: 'ask@academy.co.kr' });

  // then
  assert.deepEqual(phoneOnly.map((channel) => channel.key), ['phone']);
  assert.equal(phoneOnly[0].href, 'tel:025551234');
  assert.equal(phoneOnly[0].value, '02 555 1234');
  assert.deepEqual(emailOnly.map((channel) => channel.key), ['email']);
});

test('given blank contacts when collecting consult channels then masked footer dummies never become links', () => {
  // given — footer는 000-0000-0000/academy@example.com으로 마스킹하지만 상담 링크로는 쓸 수 없다
  const raw = { phone: '', email: '' };

  // when
  const channels = businessInfo.contactChannels(raw);

  // then
  assert.deepEqual(channels, []);
  assert.equal(businessInfo.resolveDisplay(raw, {}).phone, businessInfo.MASKED_DEFAULTS.phone);
});

test('given phone without any digit when collecting consult channels then no tel link is produced', () => {
  // given / when
  const channels = businessInfo.contactChannels({ phone: '전화 문의 환영' });

  // then
  assert.deepEqual(channels, []);
});

test('given 자리수별 전화번호 when 포맷하면 then 10자리는 3-3-4, 11자리는 3-4-4가 된다', () => {
  // given / when / then
  assert.equal(businessInfo.formatPhoneNumber('01012345678'), '010-1234-5678');
  assert.equal(businessInfo.formatPhoneNumber('0311234567'), '031-123-4567');
});

test('given 서울 02 번호 when 포맷하면 then 국번이 한 자리 짧은 2-3-4 / 2-4-4가 된다', () => {
  // given / when / then — 3-3-4를 그대로 적용하면 021-234-5678로 잘못 잡힌다
  assert.equal(businessInfo.formatPhoneNumber('0212345678'), '02-1234-5678');
  assert.equal(businessInfo.formatPhoneNumber('021234567'), '02-123-4567');
});

test('given 이미 하이픈이 있거나 초과 입력된 번호 when 포맷하면 then 숫자만 남기고 11자리로 자른다', () => {
  // given / when / then
  assert.equal(businessInfo.formatPhoneNumber('010-1234-5678'), '010-1234-5678');
  assert.equal(businessInfo.formatPhoneNumber('010 1234 5678'), '010-1234-5678');
  assert.equal(businessInfo.formatPhoneNumber('010123456789999'), '010-1234-5678');
  assert.equal(businessInfo.formatPhoneNumber(''), '');
});

test('given 입력 중인 부분 번호 when 포맷하면 then 국번 뒤에만 하이픈을 붙여 진행한다', () => {
  // given / when / then
  assert.equal(businessInfo.formatPhoneNumber('010'), '010');
  assert.equal(businessInfo.formatPhoneNumber('0101'), '010-1');
  assert.equal(businessInfo.formatPhoneNumber('02'), '02');
});

test('given 전화번호 검증 when 자리수를 확인하면 then 0으로 시작하는 9~11자리만 통과한다', () => {
  // given / when / then — 하이픈 위치는 자동 포맷이 맞추므로 자리수만 본다
  assert.equal(businessInfo.isValidPhone('010-1234-5678'), true);
  assert.equal(businessInfo.isValidPhone('02-123-4567'), true);
  assert.equal(businessInfo.isValidPhone('1234'), false);
  assert.equal(businessInfo.isValidPhone('010-1234-56789'), false);
  assert.equal(businessInfo.isValidPhone('110-1234-5678'), false);
});

test('given 이메일 문자열 when 검증하면 then 도메인에 점이 있는 형태만 통과한다', () => {
  // given / when / then
  assert.equal(businessInfo.isValidEmail('hello@academy.co.kr'), true);
  assert.equal(businessInfo.isValidEmail('hello@academy'), false);
  assert.equal(businessInfo.isValidEmail('hello'), false);
  assert.equal(businessInfo.isValidEmail(''), false);
});

test('given 저장된 이메일 when 모달용으로 쪼개면 then local과 domain으로 나뉜다', () => {
  // given / when / then
  assert.deepEqual(businessInfo.splitEmail('hello@academy.co.kr'), { local: 'hello', domain: 'academy.co.kr' });
  assert.deepEqual(businessInfo.splitEmail('hello'), { local: 'hello', domain: '' });
  assert.deepEqual(businessInfo.splitEmail(''), { local: '', domain: '' });
});

test('given local과 domain when 합치면 then 둘 다 비었을 때만 빈 문자열이 된다', () => {
  // given / when / then — 빈 문자열은 '연락처 지움'이라 저장 payload에서 blank로 남아야 한다
  assert.equal(businessInfo.joinEmail('hello', 'naver.com'), 'hello@naver.com');
  assert.equal(businessInfo.joinEmail('', ''), '');
  assert.equal(businessInfo.joinEmail('hello', ''), 'hello@');
});

test('given 도메인 선택 목록 when 모달을 그리면 then preset과 직접입력 sentinel이 분리되어 있다', () => {
  // given / when / then
  assert.ok(businessInfo.EMAIL_DOMAIN_PRESETS.includes('naver.com'));
  assert.ok(businessInfo.EMAIL_DOMAIN_PRESETS.includes('gmail.com'));
  assert.ok(!businessInfo.EMAIL_DOMAIN_PRESETS.includes(businessInfo.EMAIL_DOMAIN_CUSTOM));
});

test('given unsafe characters when rendering then values and field attributes are escaped', () => {
  const html = businessInfo.renderRows({ businessName: '<script>alert(1)</script>' });

  assert.doesNotMatch(html, /<script>/);
  assert.match(html, /&lt;script&gt;/);
  assert.match(html, /data-business-info-field="businessName"/);
});

test('given blank business name when rendering public rows then the 상호명 item is omitted', () => {
  // 마스킹 값('학원명')이라도 사업자 상호 자리에 뜨면 법적 표기로 읽힌다. 미입력이면 항목을 뺀다.
  const html = businessInfo.renderRows({ businessName: '', representative: '홍길동' });

  assert.doesNotMatch(html, /data-business-info-field="businessName"/);
  assert.doesNotMatch(html, /상호명/);
  assert.match(html, /홍길동/, '나머지 사업자 정보는 그대로 나와야 한다');
});

test('given blank business name when building editable rows then the 상호명 slot survives', () => {
  // 편집 화면은 rows()를 직접 부른다. 여기서까지 항목을 빼면 원장이 상호를 입력할 자리가 사라진다.
  const items = businessInfo.rows({ businessName: '' }).flat();
  const businessNameItem = items.find((item) => item.key === 'businessName');

  assert.ok(businessNameItem, '편집 경로에는 상호명 항목이 남아야 한다');
  assert.equal(businessNameItem.value, '학원명');
});
