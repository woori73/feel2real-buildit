'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const HTML_PATH = path.resolve(__dirname, '../../main/resources/templates/newstart/fss-helper.html');
// git checkout에 CRLF가 들어와도 정규식 계약이 깨지지 않도록 개행만 정규화한다.
const html = fs.readFileSync(HTML_PATH, 'utf8').replace(/\r\n/g, '\n');

const DENIAL = '감사회원 전용 기능입니다. 감사회원 승인을 운영자에게 요청해 주세요.';
const GENERIC = '연결에 실패했습니다. 잠시 뒤 다시 눌러 주세요.';

/**
 * Guided Agent의 403은 "권한이 없다"는 서버 안내 문구로 보여야 한다. agentFetch가 !r.ok를
 * 무조건 네트워크 오류 문구로 뭉개면 버튼이 안 눌리는 이유가 접속 문제로 위장된다 —
 * §7의 "UI 숨김으로만 처리하지 않는다"를 프론트 측에서 지키는 회귀 테스트.
 */
function extractAgentFetch() {
  const match = html.match(/function agentFetch\(path, method\) \{[\s\S]*?\n    \}\n/);
  assert.ok(match, 'agentFetch 함수를 템플릿에서 찾지 못했다');
  return match[0];
}

function runAgentFetch(responses) {
  const state = { loginBannerHidden: true };
  const queue = responses.slice();
  const context = vm.createContext({
    fetch: () => Promise.resolve(queue.shift()),
    $: () => ({
      get hidden() { return state.loginBannerHidden; },
      set hidden(v) { state.loginBannerHidden = v; }
    }),
    Error
  });
  vm.runInContext(extractAgentFetch() + '\nthis.__agentFetch = agentFetch;', context);
  return { call: (p, m) => context.__agentFetch(p, m), state };
}

function response(status, ok, body, brokenJson) {
  return {
    status,
    ok,
    json: () => (brokenJson ? Promise.reject(new Error('not json')) : Promise.resolve(body))
  };
}

test('given 403 + 서버 안내 문구 when agentFetch then then message는 서버 문구(네트워크 오류로 위장하지 않는다)', async () => {
  const { call } = runAgentFetch([response(403, false, { status: 'error', errorMessage: DENIAL })]);
  await assert.rejects(call('/api/newstart/fss/agent/session'), (err) => {
    assert.equal(err.message, DENIAL);
    return true;
  });
});

test('given 401(비로그인) when agentFetch then 로그인 배너를 보이고 resolve null — 403 권한 안내와 구분', async () => {
  const { call, state } = runAgentFetch([response(401, false, {})]);
  assert.equal(await call('/api/newstart/fss/agent/advance', 'POST'), null);
  assert.equal(state.loginBannerHidden, false, 'ns-fss-auth 배너가 로그인 회원과 비로그인을 구분해 드러나야 한다');
});

test('given 500 + JSON body 없음 when agentFetch then 연결 실패 안내로 안전하게 fallback', async () => {
  const { call } = runAgentFetch([response(500, false, null, true)]);
  await assert.rejects(call('/api/newstart/fss/agent/restart', 'POST'), (err) => {
    assert.equal(err.message, GENERIC);
    return true;
  });
});

test('given 200 when agentFetch then body JSON을 그대로 통과', async () => {
  const { call } = runAgentFetch([response(200, true, { status: 'ok', currentStep: 3, finished: false })]);
  assert.deepEqual(await call('/api/newstart/fss/agent/session'),
    { status: 'ok', currentStep: 3, finished: false });
});

test('given 세션(실행)이 거절될 때 when agentOpen then 14단계 안내 렌더는 진행 + 문구만 교체(설명은 공개)', () => {
  assert.match(html, /agentFetch\('\/api\/newstart\/fss\/agent\/session'\)\n\s*\.then\(agentApplySession\)/);
  assert.match(html, /\.catch\(function \(err\) \{\n\s*agentRenderBody\(\);\n\s*agentFail\(err, '세션 정보를 불러오지 못했습니다/);
});
