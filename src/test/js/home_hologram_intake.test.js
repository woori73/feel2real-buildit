const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const resources = path.resolve(__dirname, '../../main/resources');
const window = {};
vm.runInNewContext(fs.readFileSync(path.join(resources, 'static/js/f2r_home_agent.js'), 'utf8'), { window });

// 홈 버튼 6개는 2026-10-29 개편에서 지웠다. 같은 신청 안내는 입력창에 적은 문장으로 그대로 받는다.
for (const text of ['기말고사 이용권 신청할게요', '시험 범위 제출은 어떻게 하나요', '학부모인데 신청하고 싶어요', '160문항 주문 방법']) {
  test(`given "${text}" 입력 when 실제 intent로 답하면 then 승인된 신청·자료 안내와 카톡·이메일로 연결된다`, () => {
    const intent = window.f2rHomeAgent.detectIntent(text);
    assert.equal(intent, 'exam', text);
    const reply = window.f2rHomeAgent.replyFor(intent, {});
    assert.match(reply.text, /학교·학년·시험 범위·최근 기출·선생님 힌트/);
    assert.match(reply.text, /카톡·이메일로 접수/);
    assert.match(reply.text, /결제는 별도로 확인/);
    assert.equal(reply.links[0].href, 'https://open.kakao.com/o/sMf4ifRi');
    assert.ok(reply.links.some(link => link.href === 'mailto:woorinaesin@naver.com'));
    assert.ok(reply.links.every(link => !['/ai-agent-studio', '/builder', '/wiki'].includes(link.href)));
  });
}
