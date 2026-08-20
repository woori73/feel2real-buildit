const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadBrowserScript } = require('./helpers/browser_script_harness');
const {
  bothRenderers, loadRenderHooks, primeRenderer, markerOrder, isAscending, academyFixture
} = require('./helpers/academy_render_hooks');

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const builderJs = read('../../main/resources/static/js/academy_builder.js');
const publicCss = read('../../main/resources/static/css/academy_public.css');
const builderTemplate = read('../../main/resources/templates/tutor/academy_builder.html');
const academyServiceJava = read('../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java');
const builderPath = path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js');

// 대표 수업 4개 중 2개만 선택한 학원. 선택 id만 렌더되는지 보기 위한 픽스처.
function heroAcademy() {
  return academyFixture({
    lessons: [1, 2, 3, 4, 5].map((lessonId) => ({ lessonId, name: `수업 ${lessonId}` })),
    pageSettings: { builderDraft: { featuredLessonIds: '3,1' } }
  });
}

test('given 히어로 when 공개·빌더를 렌더 then 지역과 과목 전문 문구를 표시하지 않는다', () => {
  for (const [name, hooks] of bothRenderers()) {
    const academy = heroAcademy();
    const html = primeRenderer(hooks, academy).renderHero(academy, '영어', '강남구');
    assert.doesNotMatch(html, /cv-eyebrow/, `${name}: 지역 eyebrow가 남아 있음`);
    assert.doesNotMatch(html, /class="accent"/, `${name}: 과목 전문 accent가 남아 있음`);
  }
});

test('given 히어로 좌측 when 요소 순서를 확인 then 시안 순서(eyebrow-제목-인용-버튼-통계)다', () => {
  // 시안(에듀홀릭_BuildIt_3.html .hero) 구조를 그대로 따른다.
  for (const [name, hooks] of bothRenderers()) {
    const academy = academyFixture({ pageSettings: { builderDraft: { principalName: '박정민' } } });
    const html = primeRenderer(hooks, academy).renderHero(academy);
    const order = markerOrder(html, [
      'class="eyebrow"',
      '<h1>',
      'class="hero-quote',
      'class="hero-actions"',
      'class="hero-stats"'
    ]);
    assert.ok(isAscending(order), `${name}: 히어로 요소 순서가 시안과 다름 (${order})`);
  }
});

test('given 히어로 when 시안과 대조 then 대표수업 패널·교재 카드·영상·배지를 히어로에 두지 않는다', () => {
  // 시안 히어로는 좌(문구+통계) / 우(태블릿 원장사진)뿐이다.
  // 대표수업·교재는 sec-lessons로, 영상은 원장 소개 섹션으로 옮긴다.
  const academy = academyFixture({
    lessons: [{ lessonId: 1, name: '수업 1' }],
    heroMediaSlots: ['textbook1'],
    pageSettings: { builderDraft: { featuredLessonIds: '1', heroVideoId: '9' } }
  });

  const publicHtml = primeRenderer(loadRenderHooks('public'), academy).renderHero(academy);
  assert.doesNotMatch(publicHtml, /collection-panel/, '대표 수업 패널이 히어로에 남아 있음');
  assert.doesNotMatch(publicHtml, /hero-textbook-card/, '교재 카드가 히어로에 남아 있음');
  assert.doesNotMatch(publicHtml, /cv-hero-media/, '히어로 영상이 남아 있음');
  assert.doesNotMatch(publicHtml, /cv-hero-badges/, '공개 히어로에 배지가 남아 있음');

  // 배지는 대상·과목을 고칠 유일한 통로라 빌더 캔버스에만 남긴다.
  const builderHtml = primeRenderer(loadRenderHooks('builder'), academy).renderHero(academy);
  assert.match(builderHtml, /builder-only-badges/, '빌더에서 대상·과목 편집 통로가 사라짐');
  assert.match(builderHtml, /data-builder-basic="targetGroups"/);
  assert.match(builderHtml, /data-builder-basic="subjects"/);
});

test('given 히어로 우측 when 렌더 then 시안 iPad 프레임에 원장 사진이 들어간다', () => {
  for (const [name, hooks] of bothRenderers()) {
    // given — 원장 사진 등록됨
    const filled = academyFixture({
      heroMediaSlots: ['principal'],
      pageSettings: { builderDraft: { principalName: '박정민' } }
    });
    const html = primeRenderer(hooks, filled).renderHero(filled);

    assert.match(html, /class="tablet"/, `${name}: 태블릿 프레임 없음`);
    assert.match(html, /class="tablet-screen"/);
    assert.match(html, /<img src="[^"]*principal[^"]*"/, `${name}: 원장 사진이 렌더되지 않음`);
    assert.match(html, /class="tablet-cap"><b>박정민<\/b>/, `${name}: 캡션에 원장명이 없음`);

    // given — 미등록이면 프레임 톤에 맞춘 placeholder
    const empty = academyFixture();
    const emptyHtml = primeRenderer(hooks, empty).renderHero(empty);
    assert.match(emptyHtml, /tablet-empty/, `${name}: 미등록 placeholder 없음`);
  }
});

test('given 대표 수업 편집 진입점 when 저장 경로를 확인 then 서버·빌더·템플릿에 배선이 있다', () => {
  assert.match(academyServiceJava, /"featuredLessonIds"/);
  assert.match(builderJs, /data-featured-lessons-edit/);
  assert.match(builderJs, /최대 4개/);
  assert.match(builderTemplate, /id="builderFeaturedLessonsModal"/);
});

test('given 저장된 대표 수업 ID when 수업 목록을 필터링 then 선택 순서·중복제거·최대 4개를 지킨다', () => {
  const { window } = loadBrowserScript(builderPath);
  const hooks = window.F2RAcademyBuilderTestHooks;
  const academy = {
    pageSettings: { builderDraft: { featuredLessonIds: '3,1,3,2,4,5,bad' } },
    lessons: [1, 2, 3, 4, 5].map((lessonId) => ({ lessonId, name: `수업 ${lessonId}` }))
  };

  assert.deepEqual(Array.from(hooks.featuredLessonIds(academy)), ['3', '1', '2', '4']);
  assert.deepEqual(Array.from(hooks.selectedFeaturedLessons(academy), (lesson) => lesson.lessonId), [3, 1, 2, 4]);
});

test('given 섹션 배경 when 공개 CSS를 확인 then Build It 라이트 표면을 쓴다', () => {
  // 고흐 명화 fixed 배경을 폐기하면서 그 위에 얹혀 있던 다크 글래스도 함께 걷어냈다.
  // 어두운 배경 전제가 남아 있으면 흰 페이지에 검은 띠가 생긴다.
  assert.match(publicCss, /\.section-wrap\{[^}]*background:#fff/);
  assert.match(publicCss, /\.intro-wrap\.notes\{[^}]*background:var\(--bg\);[^}]*color:var\(--ink\)/s, 'sec-intro notes가 흰 표면이 아님');
  assert.doesNotMatch(publicCss, /rgba\(9,16,32/, '다크 글래스 배경이 남아 있음');
  assert.doesNotMatch(publicCss, /gogh/, '고흐 배경 규칙이 남아 있음');
});
