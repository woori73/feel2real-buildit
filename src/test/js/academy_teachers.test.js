const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

// 강사진(sec-teachers): 회원이 입력한 강사만 보여 주고, 데이터가 없으면 섹션을 내지 않는다.

const CSS = path.resolve(__dirname, '../../main/resources/static/css/academy_teachers.css');

function academy(builderDraft, sectionVisibility = {}) {
  return academyFixture({ name: '테스트영어교실', pageSettings: { sectionVisibility, builderDraft } });
}
const json = (list) => JSON.stringify(list);

test('given 강사 데이터 없음 when 렌더 then 공개·빌더 모두 섹션을 내지 않는다(플레이스홀더·가짜 강사 없음)', () => {
  for (const [kind, hooks] of bothRenderers()) {
    for (const draft of [{}, { teachersJson: '' }, { teachersJson: '[]' }, { teachersJson: '깨진 JSON' }, { teachersJson: '{"n":"객체"}' }, { teachersJson: json([{ r: '이름 없음' }]) }]) {
      const a = academy(draft);
      primeRenderer(hooks, a);
      assert.equal(hooks.renderTeachers(a), '', `${kind}: ${JSON.stringify(draft)}`);
    }
  }
});

test('given 유효한 강사 when 렌더 then 입력한 값만 표시하고 섹션 id·격리 클래스를 갖는다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const a = academy({
      teachersJson: json([{ n: '김서연', r: '수학 · 입시', c: ['서울대 수학과 졸업', '대입 지도 15년'] }]),
      teachersSectionTitle: '우리 선생님들'
    });
    primeRenderer(hooks, a);
    const html = hooks.renderTeachers(a);
    assert.match(html, /<section id="sec-teachers"[^>]*class="[^"]*bi-teachers/, `${kind}: section`);
    assert.match(html, /김서연/, `${kind}: name`);
    assert.match(html, /수학 · 입시/, `${kind}: role`);
    assert.match(html, /서울대 수학과 졸업/, `${kind}: career`);
    assert.match(html, /우리 선생님들/, `${kind}: custom title`);
    assert.doesNotMatch(html, /한빛|박지훈|02-1234-5678/, `${kind}: 데모 가짜 정보가 섞이지 않는다`);
  }
});

test('given 제목 미입력 when 렌더 then 기본 제목 "강사진"', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const a = academy({ teachersJson: json([{ n: '이도윤' }]) });
    primeRenderer(hooks, a);
    assert.match(hooks.renderTeachers(a), /<h2 class="section-title">강사진<\/h2>/, kind);
  }
});

test('given 사진 없음 when 렌더 then 실제 이름의 첫 글자 아바타(가짜 사진 없음)', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const a = academy({ teachersJson: json([{ n: '박지훈' }]) });
    primeRenderer(hooks, a);
    const html = hooks.renderTeachers(a);
    assert.match(html, /bi-teachers__initial[^>]*>박</, `${kind}: initial`);
    assert.doesNotMatch(html, /<img/, `${kind}: no img`);
    assert.doesNotMatch(html, /bi-teachers__role|bi-teachers__careers/, `${kind}: 입력 없는 역할·경력 요소는 내지 않는다`);
  }
});

test('given HTML/스크립트가 든 값 when 렌더 then 모두 이스케이프된다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const evil = '<script>alert(1)</script><img src=x onerror=alert(2)>';
    const a = academy({
      teachersJson: json([{ n: evil, r: evil, c: [evil], p: 'https://ex.com/a.jpg?x="onerror="alert(3)' }]),
      teachersSectionTitle: evil
    });
    primeRenderer(hooks, a);
    const html = hooks.renderTeachers(a);
    assert.doesNotMatch(html, /<script/i, `${kind}: script`);
    assert.doesNotMatch(html, /<img src=x/i, `${kind}: injected img`);
    assert.doesNotMatch(html, /<[^>]*\sonerror=/i, `${kind}: handler attr`);
    assert.match(html, /&lt;script&gt;/, `${kind}: escaped`);
  }
});

test('given 사진 URL when 렌더 then https만 허용하고 javascript:/data:/http:는 아바타로 대체', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const ok = academy({ teachersJson: json([{ n: '가', p: 'https://cdn.example.com/t.jpg' }]) });
    primeRenderer(hooks, ok);
    assert.match(hooks.renderTeachers(ok), /<img class="bi-teachers__img" src="https:\/\/cdn\.example\.com\/t\.jpg"[^>]*loading="lazy"/, `${kind}: https img`);
    for (const bad of ['javascript:alert(1)', 'data:image/png;base64,AAAA', 'http://ex.com/a.jpg', '//ex.com/a.jpg', 'https://ex.com/a b.jpg']) {
      const a = academy({ teachersJson: json([{ n: '나', p: bad }]) });
      primeRenderer(hooks, a);
      const html = hooks.renderTeachers(a);
      assert.doesNotMatch(html, /<img/, `${kind}: ${bad}`);
      assert.match(html, /bi-teachers__initial/, `${kind}: avatar for ${bad}`);
    }
  }
});

test('given 8명 초과·경력 5개 초과 when 렌더 then 한도만큼만 표시', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const many = Array.from({ length: 12 }, (_, i) => ({ n: `강사${i + 1}`, c: ['1', '2', '3', '4', '5', '6', '7'] }));
    const a = academy({ teachersJson: json(many) });
    primeRenderer(hooks, a);
    const html = hooks.renderTeachers(a);
    assert.equal((html.match(/class="bi-teachers__card"/g) || []).length, 8, `${kind}: cards`);
    assert.equal((html.match(/<li>\d<\/li>/g) || []).length, 8 * 5, `${kind}: careers`);
  }
});

test('given sectionVisibility[sec-teachers]=false when 렌더 then 데이터가 있어도 숨긴다, 키 없으면 데이터만으로 표시(기존 학원 불변)', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const draft = { teachersJson: json([{ n: '최민아' }]) };
    const hidden = academy(draft, { 'sec-teachers': false });
    primeRenderer(hooks, hidden);
    assert.equal(hooks.renderTeachers(hidden), '', `${kind}: explicit false`);
    const shown = academy(draft, {});
    primeRenderer(hooks, shown);
    assert.match(hooks.renderTeachers(shown), /최민아/, `${kind}: default shown with data`);
  }
});

test('given 기존 학원(teachersJson 없음) when 전체 섹션을 렌더 then 강사진 마크업이 한 줄도 없다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const a = academy({});
    primeRenderer(hooks, a);
    assert.equal(hooks.renderTeachers(a).length, 0, kind);
  }
});

test('given academy_teachers.css when 검사 then bi-teachers 아래로 격리되고 전역 리셋·하드코딩 데모 값이 없다', () => {
  const css = fs.readFileSync(CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const selectors = css.split('{').slice(0, -1).map((chunk) => chunk.split('}').pop().trim()).filter((s) => s && !s.startsWith('@media'));
  assert.ok(selectors.length > 5);
  for (const sel of selectors) {
    for (const part of sel.split(',')) assert.match(part.trim(), /^(#publicView |\.builder-public-preview )?\.bi-teachers\b/, `격리 위반: ${part.trim()}`);
  }
  assert.doesNotMatch(css, /(^|[\s,{])(\*|html|body)\s*[,{]/, '전역 리셋 금지');
  for (const bp of ['960px', '560px']) assert.ok(css.includes(bp), `반응형 ${bp}`);
});

test('given sec-teachers when 섹션 목록 정의를 비교 then 서버·공개·빌더·AI 채팅 모두 같은 순서이고 sec-principal 바로 뒤', () => {
  const read = (rel) => fs.readFileSync(path.resolve(__dirname, '../../main', rel), 'utf8');
  const idsFrom = (source, declaration) => {
    const start = source.indexOf(declaration);
    assert.notEqual(start, -1, declaration);
    const body = source.slice(start, source.indexOf(';', start) + 1);
    return [...body.matchAll(/['"](sec-[a-z0-9-]+)['"]/g)].map((m) => m[1]);
  };
  const server = idsFrom(read('java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java'), 'static final List<String> DEFAULT_SECTION_ORDER');
  const pub = idsFrom(read('resources/static/js/academy_public.js'), 'const DEFAULT_SECTION_ORDER');
  const bld = idsFrom(read('resources/static/js/academy_builder.js'), 'const DEFAULT_SECTION_ORDER');
  const managed = idsFrom(read('resources/static/js/academy_builder.js'), 'const MANAGED_SECTION_IDS');
  const aiChat = idsFrom(read('resources/static/js/academy_builder_ai_chat.js'), 'var DEFAULT_SECTION_ORDER');
  assert.deepEqual(pub, server);
  assert.deepEqual(bld, server);
  assert.deepEqual(aiChat, server);
  assert.deepEqual(managed, server.filter((id) => id !== 'sec-hero'));
  assert.equal(server[server.indexOf('sec-principal') + 1], 'sec-teachers');
});

test('given 템플릿 when 읽기 then 공개·빌더 페이지가 academy_teachers.css를 로드한다(미리보기 일치)', () => {
  for (const rel of ['resources/templates/public/academy_public.html', 'resources/templates/tutor/academy_builder.html']) {
    const html = fs.readFileSync(path.resolve(__dirname, '../../main', rel), 'utf8');
    assert.match(html, /\/css\/academy_teachers\.css\?v=/, rel);
  }
});
