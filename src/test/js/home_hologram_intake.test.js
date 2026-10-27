const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const resources = path.resolve(__dirname, '../../main/resources');
const html = fs.readFileSync(path.join(resources, 'templates/public/landing.html'), 'utf8');
const window = {};
vm.runInNewContext(fs.readFileSync(path.join(resources, 'static/js/f2r_home_agent.js'), 'utf8'), { window });

for (const title of ['기말고사 이용권', '시험 범위 제출', '학부모·학생 신청', '접수 방법']) {
  test(`given ${title} 타일 when 실제 intent로 답하면 then 승인된 신청·자료 안내와 카톡·이메일로 연결된다`, () => {
    const tile = [...html.matchAll(/<button[^>]*class="si-holo-tile"[^>]*data-home-intent="([^"]+)"[^>]*><b>([^<]+)<\/b>/g)]
      .find(match => match[2] === title);
    assert.ok(tile, title);
    const reply = window.f2rHomeAgent.replyFor(tile[1], {});
    assert.match(reply.text, /학교·학년·시험 범위·최근 기출·선생님 힌트/);
    assert.match(reply.text, /카톡·이메일로 접수/);
    assert.match(reply.text, /결제는 별도로 확인/);
    assert.equal(reply.links[0].href, 'https://open.kakao.com/o/sMf4ifRi');
    assert.ok(reply.links.some(link => link.href === 'mailto:woorinaesin@naver.com'));
    assert.ok(reply.links.every(link => !['/ai-agent-studio', '/builder', '/wiki'].includes(link.href)));
  });
}
