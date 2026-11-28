const assert = require('node:assert/strict');
const test = require('node:test');
const { requestBody, sourceUrl, failureMessage, authDestination } = require('../../main/resources/static/js/codi_chat.js');

test('첫 자유대화는 업무 식별자와 이력 없이 일반 질문만 전송한다', () => {
  assert.deepEqual(requestBody('질문입니다.'), { mode: 'general', message: '질문입니다.', webSearch: false });
});

test('후속 질문에는 서버가 돌려준 대화 식별자만 추가한다', () => {
  assert.deepEqual(requestBody('이어서 설명해 주세요.', 'thread-2'), {
    mode: 'general', message: '이어서 설명해 주세요.', webSearch: false, conversationId: 'thread-2'
  });
});

test('출처는 HTTP(S) 주소만 링크로 표시하며 자격 정보가 든 URL은 제외한다', () => {
  for (const bad of [null, '/private', 'data:text/html,test', 'javascript:alert(1)', 'https://abc:123@example.org']) {
    assert.equal(sourceUrl(bad), null);
  }
  assert.equal(sourceUrl('https://example.org/source').pathname, '/source');
});

test('만료, 한도, 공급자 실패를 사용자가 구분할 수 있다', () => {
  assert.match(failureMessage(410), /새 대화/);
  assert.match(failureMessage(429), /사용 한도/);
  assert.match(failureMessage(503), /답변 서비스/);
});

test('세션 만료와 임시 비밀번호는 전용 경로로 보내고 일반 권한 오류는 화면에 남긴다', () => {
  const normal = new Headers();
  const temporary = new Headers({ 'X-Password-Change-Required': 'true' });
  assert.equal(authDestination({ status: 401, headers: normal }), '/?login=required&returnUrl=%2Fcodi');
  assert.equal(authDestination({ status: 403, headers: temporary }), '/account/password-change');
  assert.equal(authDestination({ status: 403, headers: normal }), null);
});
