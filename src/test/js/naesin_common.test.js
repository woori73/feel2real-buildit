'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const JS = path.join(__dirname, '../../main/resources/static/js/');

function load() {
  const ctx = { console };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(JS + 'naesin_common.js', 'utf8'), ctx);
  return ctx.NT;
}
const NT = load();

test('given 내신 문항 그림 표기, when imgPath하면, then /naesin 폴더의 webp 경로만 돌려준다', () => {
  // given
  const ok = '[[img:/naesin/apgujeong-m2-2026-2/r01/q01.webp]]';
  // when / then
  assert.strictEqual(NT.imgPath(ok), '/naesin/apgujeong-m2-2026-2/r01/q01.webp');
  assert.strictEqual(NT.imgPath('[[img:https://evil.example/x.webp]]'), null);
  assert.strictEqual(NT.imgPath('[[img:/naesin/../secret.webp]]'), null);
  assert.strictEqual(NT.imgPath('[[img:/naesin/a.png]]'), null);
  assert.strictEqual(NT.imgPath(null), null);
});

test('given 로그인 후 돌아갈 주소, when safeReturn하면, then /naesintest 안쪽 주소만 허용한다', () => {
  assert.strictEqual(NT.safeReturn('/naesintest/run?attempt=3'), '/naesintest/run?attempt=3');
  assert.strictEqual(NT.safeReturn('/naesintest'), '/naesintest');
  assert.strictEqual(NT.safeReturn('//evil.example'), null);
  assert.strictEqual(NT.safeReturn('/naesintestx'), null);
  assert.strictEqual(NT.safeReturn('/naesintest/../admin'), null);
});

test('given 남은 초, when fmtTime하면, then 분:초 두 자리로 나오고 음수는 00:00이다', () => {
  assert.strictEqual(NT.fmtTime(1200), '20:00');
  assert.strictEqual(NT.fmtTime(65), '01:05');
  assert.strictEqual(NT.fmtTime(-3), '00:00');
});

test('given 답 글자, when circ하면, then 동그라미 번호가 나온다', () => {
  assert.strictEqual(NT.circ('A'), '①');
  assert.strictEqual(NT.circ('E'), '⑤');
  assert.strictEqual(NT.circ(null), '');
});

test('given 해설 문자열, when expHtml하면, then 첫 줄·근거 줄을 구분하고 HTML은 이스케이프한다', () => {
  // given
  const text = '정답 ② · 어법 (4점)\n<b>주격</b> 관계대명사다.\n근거: 필기본 05쪽\n어법 포인트: 관계대명사 that';
  // when
  const html = NT.expHtml(text);
  // then
  assert.ok(html.startsWith('<p class="nt-exp-head">정답 ②'));
  assert.ok(html.includes('&lt;b&gt;주격'));
  assert.strictEqual((html.match(/nt-exp-tag/g) || []).length, 2);
});

test('given 순서대로 실행할 비동기 작업, when serialize로 감싸 연달아 부르면, then 부른 순서대로 끝난다', async () => {
  // given
  const done = [];
  const run = NT.serialize((n, ms) => new Promise(r => setTimeout(() => { done.push(n); r(); }, ms)));
  // when
  await Promise.all([run(1, 30), run(2, 0)]);
  // then
  assert.deepStrictEqual(done, [1, 2]);
});

test('given 시험 코드, when schoolUrl하면, then 그 학교 페이지 주소가 나오고 모르는 코드는 학교 고르기로 간다', () => {
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-APM2-2602-R01'), '/naesintest/school/apgujeong-m2');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-SGM2-2602-R03'), '/naesintest/school/singu-m2');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-APM1-2602-R01'), '/naesintest/school/apgujeong-m1');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-DMM2-2602-R01'), '/naesintest/school/deokmyeong-m2');
  assert.strictEqual(NT.schoolUrl('TP-SAT-MINI-01'), '/naesintest');
  assert.strictEqual(NT.schoolUrl(null), '/naesintest');
});

test('given 타과목 디지털 모의고사 코드, when schoolUrl하면, then 문제 풀기(/learn)의 그 과목 탭으로 돌아간다', () => {
  // given & when & then
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-KOR-M2-2602-R01'), '/learn?tab=korean');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-MATH-M2-2602-R01'), '/learn?tab=math');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-SOC-M2-2602-R01'), '/learn?tab=social');
  assert.strictEqual(NT.schoolUrl('TP-NAESIN-SCI-M2-2602-R01'), '/learn?tab=science');
});

test('given 예비고1 채점 결과, when 해설을 열면, then 정답과 핵심 풀이 단서를 강조한다', async () => {
  // given
  const classList = { toggle() {}, remove() {} };
  const elements = Object.fromEntries([
    'ph-test', 'ph-body', 'ph-status', 'ph-time-label', 'ph-section', 'ph-time', 'nt-timer-box',
  ].map(id => [id, { classList }]));
  elements['ph-test'].dataset = { round: '1', shared: '[false,false]' };
  let click;
  elements['ph-body'].addEventListener = (_, listener) => { click = listener; };
  const state = {
    phase: 'REVIEW', remainingMillis: 0, elapsedMillis: 1200, wordResults: [],
    readingResults: [
      { number: 1, chosen: 2, answer: 1, explanation: [
        '주제: 생각과 행동',
        '해설: 두 대상의 관계가 중요하다. 이어지는 문장은 예시다.',
        '① 생각과 행동 · 정답 근거: “영향”을 보여 준다.',
        '② 다른 내용 · 오답 근거: 글의 소재와 다르다.',
        '풀이 전략: to achieve this가 가리키는 내용을 찾는다.',
        '이 문항에 적용 · <근거>를 확인한다.',
      ].join('\n') },
      { number: 2, chosen: 1, answer: 2, explanation:
        '해설: ②가 정답입니다. 원인에 이어 결과가 나온다.\n풀이해설: 전환 표현을 찾는다.' },
    ],
  };
  const context = {
    document: {
      getElementById: id => elements[id],
      addEventListener() {},
      body: { classList },
    },
    NT: { ...NT, api: async () => state },
    performance: { now: () => 0 },
    window: { setInterval() {} },
  };
  vm.runInNewContext(fs.readFileSync(JS + 'pre_high_english.js', 'utf8'), context);

  // when
  click({ target: { closest: () => ({ dataset: { action: 'start' } }) } });
  await new Promise(setImmediate);

  // then
  const html = elements['ph-body'].innerHTML;
  const key = text => '<span class="ph-review-key">' + text + '</span>';
  assert.ok(html.includes('내 답 ② · ' + key('정답 ①')));
  assert.ok(html.includes(key('① 생각과 행동 · 정답 근거') + ': ' + key('“영향”')));
  assert.ok(html.includes('<p>② 다른 내용 · 오답 근거: 글의 소재와 다르다.</p>'));
  assert.ok(html.includes('<strong>주제</strong> 생각과 행동'));
  assert.ok(html.includes('<strong>풀이 전략</strong> ' + key('to achieve this')));
  assert.ok(html.includes('<strong>이 문항에 적용</strong> ' + key('&lt;근거&gt;를 확인한다.')));
  assert.ok(html.includes(key('②가 정답입니다.') + ' ' + key('원인에 이어 결과가 나온다.')));
  assert.ok(html.includes('<strong>풀이해설</strong> ' + key('전환 표현을 찾는다.')));
  assert.ok(html.includes(key('두 대상의 관계가 중요하다.') + ' 이어지는 문장은 예시다.'));
});

test('given 글로 된 발문·지문, when sheetHtml하면, then 글을 이스케이프해 줄바꿈과 함께 보여 준다', () => {
  // given
  const markup = '다음 글을 읽고 물음에 답하시오.\n<b>가</b> & 나';
  // when
  const html = NT.sheetHtml(markup, '1번 문항', 'nt-passage');
  // then
  assert.strictEqual(html, '<div class="nt-text nt-passage">다음 글을 읽고 물음에 답하시오.<br>&lt;b&gt;가&lt;/b&gt; &amp; 나</div>');
});

test('given 통째 그림 표기와 글 속 그림 표기, when sheetHtml하면, then /naesin 그림만 img로 바꾸고 바깥 주소는 지운다', () => {
  // given
  const whole = '[[img:/naesin/a/q01.webp]]';
  const inline = '그래프를 보고 답하시오.\n[[img:/naesin/a/g.webp]][[img:https://evil.example/x.webp]]';
  // when
  const a = NT.sheetHtml(whole, '1번 문항');
  const b = NT.sheetHtml(inline, '2번 문항');
  // then
  assert.strictEqual(a, '<img class="nt-img" loading="lazy" src="/naesin/a/q01.webp" alt="1번 문항">');
  assert.ok(b.includes('<img class="nt-img" loading="lazy" src="/naesin/a/g.webp" alt="2번 문항">'));
  assert.ok(!b.includes('evil'));
  assert.strictEqual(NT.sheetHtml('  ', 'x'), '');
  assert.strictEqual(NT.sheetHtml(null, 'x'), '');
});

test('given 선택지, when choicesHtml하면, then 글 선택지는 번호와 함께 보이고 ①~⑤만 있으면 빈 글이다', () => {
  // given
  const text = { choices: ['ㄱ', 'ㄴ', 'ㄱ, ㄴ', '<script>', 'ㄱ, ㄴ, ㄷ'] };
  const image = { choices: ['①', '②', '③', '④', '⑤'] };
  // when
  const html = NT.choicesHtml(text);
  // then
  assert.ok(html.startsWith('<ol class="nt-choices"><li><span>①</span> ㄱ</li>'));
  assert.ok(html.includes('<li><span>④</span> &lt;script&gt;</li>'));
  assert.strictEqual(NT.choicesHtml(image), '');
  assert.strictEqual(NT.choicesHtml({}), '');
});

test('given 시험 코드와 제목, when subjectLabel·gradeLabel하면, then 과목과 학년을 돌려주고 모르면 영어·「-」다', () => {
  // given / when / then
  assert.strictEqual(NT.subjectLabel('TP-NAESIN-KOR-HS-2026-1'), '국어');
  assert.strictEqual(NT.subjectLabel('TP-NAESIN-MATH-X'), '수학');
  assert.strictEqual(NT.subjectLabel('TP-NAESIN-SOC-X'), '사회');
  assert.strictEqual(NT.subjectLabel('TP-NAESIN-SCI-X'), '과학');
  assert.strictEqual(NT.subjectLabel('TP-NAESIN-APGU-M2'), '영어');
  assert.strictEqual(NT.subjectLabel(null), '영어');
  assert.strictEqual(NT.gradeLabel('○○고 1학년 1학기 중간 국어'), '1학년');
  assert.strictEqual(NT.gradeLabel('국어 모의고사'), '-');
});

test('given <u> 밑줄 표시가 든 글 문항, when sheetHtml·choicesHtml하면, then 밑줄만 살리고 다른 태그는 글자로 보인다', () => {
  // given
  const prompt = '밑줄 친 ⓐ<u>are</u> 중 <b>틀린</b> 것은?';
  const q = { choices: ['ⓐ <u>who</u>', 'b', 'c', 'd', '<script>x</script>'] };
  // when
  const html = NT.sheetHtml(prompt, '1번 문항');
  const choices = NT.choicesHtml(q);
  // then
  assert.ok(html.includes('ⓐ<u>are</u>'));
  assert.ok(html.includes('&lt;b&gt;틀린&lt;/b&gt;'));
  assert.ok(choices.includes('ⓐ <u>who</u>'));
  assert.ok(choices.includes('&lt;script&gt;'));
});
