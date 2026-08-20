const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const {
  bothRenderers, loadRenderHooks, primeRenderer, academyFixture
} = require('./helpers/academy_render_hooks');

/**
 * 개설 시간표(`.lesson-schedule-inner`) 토글 안의 달력 이미지 업로드(V948 schedule 슬롯).
 *
 * 수업상품에서 파생하던 행 목록(`.schedule-list`)은 학원이 실제로 쓰는 달력형 시간표로 보이지
 * 않아 걷어냈다. 시간표 본문은 원장이 올린 달력 이미지 하나뿐이며, 그래서 이 섹션의 존재 여부는
 * lessons가 아니라 schedule 슬롯이 좌우한다.
 *
 * 공개 렌더와 빌더 렌더는 같은 화면을 각각 따로 그리므로 한쪽만 고치면 발행 결과와 미리보기가
 * 어긋난다. 슬롯 문자열은 서버 enum(HeroMediaSlot.SCHEDULE)과 글자까지 같아야 업로드 URL이 맞는다.
 */

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const builderJs = read('../../main/resources/static/js/academy_builder.js');
const publicCss = read('../../main/resources/static/css/academy_public.css');
const heroMediaSlotJava = read(
  '../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/entity/HeroMediaSlot.java');

const LESSONS = [
  { lessonId: 1, name: '중2 내신 심화반', targetGroup: 'MIDDLE', scheduleSummary: '화·목 19:00–21:00' }
];

test('given 달력 이미지가 없는 공개 페이지 when 시간표를 렌더 then 섹션을 통째로 뺀다', () => {
  // given: 수업상품은 있지만 달력을 아직 올리지 않은 발행 학원
  const academy = academyFixture({ lessons: LESSONS, heroMediaSlots: [] });

  // when
  const html = primeRenderer(loadRenderHooks('public'), academy).renderLessons(academy);

  // then: 본문이 달력뿐이라 머리말만 남기면 빈 껍데기가 된다. 수업이 있어도 살리지 않는다
  assert.doesNotMatch(html, /lesson-schedule-wrap/, '본문 없는 시간표 머리말이 남음');
  assert.doesNotMatch(html, /schedule-calendar/, '공개 페이지에 빈 달력 자리가 생김');
  assert.doesNotMatch(html, /data-schedule-toggle/, '내용 없는 토글만 남음');
  // 커리큘럼(수업상품 탭)은 시간표와 별개라 그대로 있어야 한다
  assert.match(html, /테스트학원 커리큘럼/, '커리큘럼 섹션까지 사라짐');
});

test('given 걷어낸 자동 시간표 when 어느 렌더러든 then .schedule-list를 다시 그리지 않는다', () => {
  // given: 수업상품 데이터가 충분히 있어 과거 구현이라면 행이 그려졌을 상황
  const academy = academyFixture({ lessons: LESSONS, heroMediaSlots: ['schedule'] });

  for (const [name, hooks] of bothRenderers()) {
    // when
    const html = primeRenderer(hooks, academy).renderLessons(academy);

    // then: 달력 이미지만 본문에 남는다
    assert.doesNotMatch(html, /schedule-list|sch-row/, `${name}: 자동 시간표 목록이 되살아남`);
    assert.doesNotMatch(html, /모집중|마감임박/, `${name}: 모집 상태 pill이 남음`);
    assert.doesNotMatch(html, /화·목 19:00–21:00/, `${name}: 수업 일정 텍스트가 시간표에 남음`);
    assert.match(html, /schedule-calendar-image/, `${name}: 달력 이미지가 없음`);
  }
});

test('given 달력 이미지를 등록한 공개 페이지 when 시간표를 렌더 then 토글 안에 이미지가 보인다', () => {
  // given: schedule 슬롯이 채워진 발행 학원
  const academy = academyFixture({ lessons: LESSONS, heroMediaSlots: ['schedule'] });

  // when
  const html = primeRenderer(loadRenderHooks('public'), academy).renderLessons(academy);

  // then: 공개 서빙 경로의 이미지가 토글 영역(.lesson-schedule-inner) 안에 들어간다
  assert.match(html, /class="schedule-calendar"/, '달력 블록이 없음');
  assert.match(html, /schedule-calendar-image/, '달력 이미지 태그가 없음');
  assert.match(html, /\/hero-media\/schedule/, '공개 달력 서빙 경로가 아님');
  assert.doesNotMatch(html, /data-hero-media-edit/, '공개 페이지에 편집 통로가 노출됨');

  // 달력은 머리말 다음, 시간표 영역(.lesson-schedule-inner) 안에 들어가야 한다
  const innerAt = html.indexOf('lesson-schedule-inner');
  const headAt = html.indexOf('schedule-head');
  const calendarAt = html.indexOf('schedule-calendar');
  assert.ok(innerAt !== -1 && innerAt < calendarAt, '달력이 시간표 영역 밖에 있음');
  assert.ok(headAt !== -1 && headAt < calendarAt, '달력이 머리말보다 앞에 있음');
});

test('given 빌더 캔버스 when 달력이 비어 있으면 then 클릭해서 올릴 자리를 그린다', () => {
  // given: 이미지를 아직 올리지 않은 원장의 빌더 미리보기
  const academy = academyFixture({ lessons: LESSONS, heroMediaSlots: [] });

  // when
  const html = primeRenderer(loadRenderHooks('builder'), academy).renderLessons(academy);

  // then: 공개와 달리 placeholder를 그려야 원장이 클릭해 올릴 수 있다
  assert.match(html, /schedule-calendar-placeholder/, '빌더에 업로드 placeholder가 없음');
  assert.match(html, /data-hero-media-edit="schedule"/, '업로드 배선 속성이 없음');
  assert.match(html, /role="button"/, '키보드·클릭 접근성 role이 없음');
  assert.match(html, /builder-hero-media-chip/, '수정 안내 칩이 없음');
});

test('given 수업상품이 하나도 없는 빌더 when 렌더 then 달력 업로드 통로가 남는다', () => {
  // given: 수업을 아직 만들지 않은 학원. 시간표는 이제 수업 데이터와 무관하다
  const academy = academyFixture({ lessons: [], heroMediaSlots: [] });

  // when
  const builderHtml = primeRenderer(loadRenderHooks('builder'), academy).renderLessons(academy);
  const publicHtml = primeRenderer(loadRenderHooks('public'), academy).renderLessons(academy);

  // then: 빌더는 업로드 자리를 남기고, 공개는 보여줄 게 없으므로 섹션을 통째로 뺀다
  assert.match(builderHtml, /data-hero-media-edit="schedule"/, '수업이 없다고 업로드 통로까지 사라짐');
  assert.doesNotMatch(builderHtml, /class="schedule-list"/, '빈 자동 시간표 목록이 렌더됨');
  assert.doesNotMatch(publicHtml, /lesson-schedule-wrap/, '공개 페이지에 빈 시간표 섹션이 남음');
});

test('given 발행 전 보류 업로드 when 빌더가 렌더 then objectURL로 미리 보여준다', () => {
  // given: Academy row가 없어 업로드 API가 404인 발행 전 상태 (파일은 로컬 보류)
  const academy = academyFixture({ lessons: LESSONS, heroMediaSlots: [] });
  const hooks = loadRenderHooks('builder');
  primeRenderer(hooks, academy);
  hooks.setPendingHeroMedia({ schedule: { file: {}, objectUrl: 'blob:preview-schedule' } });

  // when
  const html = hooks.renderLessons(academy);

  // then: 서버 경로가 아니라 보류 파일의 objectURL을 쓴다
  assert.match(html, /blob:preview-schedule/, '보류한 달력 이미지가 미리보기에 안 뜸');
  assert.doesNotMatch(html, /schedule-calendar-placeholder/, '보류분이 있는데 placeholder가 남음');
});

test('given 시간표 공개 토글 when 끄면 then 달력이 숨는다', () => {
  // given / when / then: 시간표 본문은 달력 하나뿐이라 이 규칙이 토글의 전부다
  assert.match(publicCss, /\.lesson-schedule-wrap\.is-schedule-hidden \.schedule-calendar\{display:none;\}/);
  assert.ok(publicCss.includes('.schedule-calendar{'), '.schedule-calendar 스타일 정의 누락');
  assert.ok(publicCss.includes('.schedule-calendar-image{'), '.schedule-calendar-image 스타일 정의 누락');
  // 걷어낸 목록 스타일이 남아 있으면 죽은 CSS다
  assert.doesNotMatch(publicCss, /\.sch-row|\.schedule-list/, '자동 시간표 목록 CSS가 남아 있음');
});

test('given 달력 업로드·삭제 when 캔버스를 다시 그리면 then 히어로가 아니라 sec-lessons로 스크롤한다', () => {
  // given: 시간표 달력은 히어로가 아니라 sec-lessons 하단에 그려진다
  const start = builderJs.indexOf('function afterHeroMediaChange(');
  assert.notEqual(start, -1, 'afterHeroMediaChange를 찾지 못함');
  const body = builderJs.slice(start, builderJs.indexOf('\n  }', start));

  // when / then: 'sec-hero'로 보내면 방금 올린 이미지가 화면 밖으로 밀린다
  assert.match(body, /slot === 'schedule' \? 'sec-lessons' : 'sec-hero'/,
    '슬롯별 스크롤 대상 분기가 없음');
});

test('given 프론트 슬롯 문자열 when 서버 enum과 비교 then 같은 key를 쓴다', () => {
  // given / when / then: 다르면 업로드 URL이 400(올바르지 않은 슬롯)이 된다
  assert.match(heroMediaSlotJava, /SCHEDULE\("schedule"\)/, '서버에 schedule 슬롯이 없음');

  for (const [name, hooks] of bothRenderers()) {
    const academy = academyFixture({ lessons: LESSONS, heroMediaSlots: ['schedule'] });
    const html = primeRenderer(hooks, academy).renderLessons(academy);
    assert.match(html, /schedule-calendar-image/, `${name}: 등록한 달력 이미지가 렌더되지 않음`);
    assert.match(html, /hero-media\/schedule/, `${name}: schedule 슬롯 경로를 쓰지 않음`);
  }
});
