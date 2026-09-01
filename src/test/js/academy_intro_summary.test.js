const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { bothRenderers, loadRenderHooks, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * sec-intro 좌측 컬럼(과목 + 학원 특징)과 GNB 지역 텍스트 제거, 수업상품 탭 안내 문구의
 * 교차 파일 정합성.
 *
 * 공개 렌더(academy_public.js)와 빌더 렌더(academy_builder.js)는 같은 화면을 각각 따로
 * 그리므로 한쪽만 고치면 발행 결과와 미리보기가 어긋난다. builderDraft.introFeatures는
 * 서버 허용 key(AcademyService.BUILDER_DRAFT_KEYS)에 없으면 저장 시 400이 된다.
 */

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const publicJs = read('../../main/resources/static/js/academy_public.js');
const builderJs = read('../../main/resources/static/js/academy_builder.js');
const showcaseJs = read('../../main/resources/static/js/lesson_product_showcase.js');
const publicCss = read('../../main/resources/static/css/academy_public.css');
const showcaseCss = read('../../main/resources/static/css/lesson_product_showcase.css');
const academyServiceJava = read('../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java');
const editableGuideJs = read('../../main/resources/static/js/academy_builder_editable_guide.js');
const builderTemplate = read('../../main/resources/templates/tutor/academy_builder.html');

test('given GNB when 공개·빌더가 렌더 then 로고 옆 지역 보조 텍스트를 넣지 않는다', () => {
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture();
    primeRenderer(hooks, academy);

    // when
    const gnb = hooks.renderGnb(academy, '');
    const hero = hooks.renderHero(academy, '영어', '강남구');

    // then
    assert.doesNotMatch(gnb, /eh-logo-sub/, `${name}: GNB에 지역 보조 텍스트가 남아 있음`);
    assert.doesNotMatch(hero, /cv-eyebrow/, `${name}: 히어로에 지역 eyebrow가 남아 있음`);
  }
});

test('given sec-intro when 공개·빌더가 렌더 then BuildIt notes snap 4요소만 남는다', () => {
  // BuildIt_3 시안의 notes snap으로 전면 교체됐다. eyebrow·제목·인용문·노트 카드가 전부다.
  // 시안 문구 3개는 기존 draft 키에 매핑한다(신규 키 없음 = 서버 화이트리스트 무수정).
  const draft = {
    introHeading: '키워드값', introMotto: '큰제목값', introBodySecondary: '인용문값'
  };

  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture({ pageSettings: { builderDraft: draft } });

    // when
    const intro = primeRenderer(hooks, academy).renderIntro(academy);

    // then — 시안의 4요소 골격을 갖는다
    assert.match(intro, /notes-keyword/, `${name}: 키워드 없음`);
    assert.match(intro, /notes-title/, `${name}: 큰제목 없음`);
    assert.match(intro, /notes-quote/, `${name}: 인용문 없음`);
    assert.match(intro, /intro-feature-list/, `${name}: 특징 목록 없음`);

    // then — 세 draft 키가 실제로 각 자리에 렌더된다
    assert.match(intro, /notes-keyword[^>]*>[^<]*키워드값/, `${name}: introHeading이 키워드 자리에 없음`);
    assert.match(intro, /notes-title[^>]*>[^<]*큰제목값/, `${name}: introMotto가 큰제목 자리에 없음`);
    assert.ok(intro.includes('인용문값'), `${name}: introBodySecondary가 렌더되지 않음`);

    // then — 중복 로고/학원명/영문명은 여전히 없다
    assert.doesNotMatch(intro, /intro-logo|intro-brand|introEnglishName/, `${name}: 중복 브랜드 요소가 남아 있음`);

    // then — 구 레이아웃(요약 2열 + 교재 카드)은 사라졌다. 교재는 히어로 textbook-card로 옮겼다.
    assert.doesNotMatch(intro, /intro-summary-area|intro-summary-block|intro-summary-label/, `${name}: 구 요약 레이아웃이 남아 있음`);
    assert.doesNotMatch(intro, /intro-textbook-card|intro-subject-badges|intro-divider/, `${name}: 구 교재·과목 요소가 남아 있음`);
  }
});

test('given sec-intro when 빌더만 렌더 then 특징 편집 어피던스는 빌더에만 노출된다', () => {
  // given
  const academy = academyFixture();
  const [[, publicHooks], [, builderHooks]] = bothRenderers();

  // when
  const publicIntro = primeRenderer(publicHooks, academy).renderIntro(academy);
  const builderIntro = primeRenderer(builderHooks, academy).renderIntro(academy);
  const builderHero = builderHooks.renderHero(academy, '영어', '강남구');

  // then — 빌더에서만 특징 추가/삭제 편집 어피던스를 노출한다
  assert.match(builderIntro, /data-intro-feature-list/);
  assert.match(builderIntro, /data-intro-feature-add/);
  assert.doesNotMatch(publicIntro, /data-intro-feature|data-builder-basic/);

  // then — 과목 편집은 sec-intro에서 빠졌어도 빌더 전용 히어로 배지로 계속 가능하다
  assert.match(builderHero, /cv-hero-subject-badges" data-builder-basic="subjects"/);
});

test('given 학원 특징 when 저장 경로를 확인 then introFeatures가 서버 허용 key와 빌더 커밋 경로에 모두 있다', () => {
  // given / when / then
  assert.match(academyServiceJava, /"introFeatures"/);
  // 개행 결합 문자열이라 200자 제한이 아닌 long key여야 한다
  const longKeys = academyServiceJava.slice(
    academyServiceJava.indexOf('LONG_BUILDER_DRAFT_KEYS'),
    academyServiceJava.indexOf('TEMP_HANDLE_PREFIX')
  );
  assert.match(longKeys, /"introFeatures"/);
  assert.match(builderJs, /introFeatures: list\.join\('\\n'\)/);
  // notes 포팅 후 카드 루트는 li가 아니라 article(.intro-feature-item)이다.
  assert.match(builderJs, /removeBtn\.closest\('\.intro-feature-item'\)/);
  assert.match(builderJs, /querySelector\('\.intro-feature-item:last-child \[data-intro-feature-item\]'\)/);
  assert.match(builderJs, /querySelectorAll\('\.intro-feature-item'\)/);
  assert.match(builderJs, /return body \? `\$\{title\}\|\$\{body\}` : title;/);
  // 섹션 "적용"이 가변 리스트를 DOM에서 수집해 커밋한다
  assert.match(builderJs, /\[data-intro-feature-list\]'\)\) \{\n\s+if \(commitIntroFeatures\(section\)\)/);
  // "수정할 곳 보기" 안내 대상에도 포함된다
  assert.match(editableGuideJs, /\[data-intro-feature-item\]/);
});

test('given 학원 특징 본문 when 빌더가 렌더 then 제목과 같은 편집 대상이고 공개 렌더에는 새지 않는다', () => {
  // given — 본문이 있는 특징 1개 + 본문이 없는 특징 1개
  const academy = academyFixture({
    pageSettings: { builderDraft: { introFeatures: '제목1|본문1\n제목2' } }
  });
  const [[, publicHooks], [, builderHooks]] = bothRenderers();

  // when
  const builderIntro = primeRenderer(builderHooks, academy).renderIntro(academy);
  const publicIntro = primeRenderer(publicHooks, academy).renderIntro(academy);

  // then — 빌더는 본문에도 편집 앵커를 붙이고, 본문이 비어도 클릭할 자리를 남긴다
  assert.match(builderIntro, /<p data-intro-feature-body contenteditable="false" spellcheck="false">본문1<\/p>/);
  assert.match(builderIntro, /<p data-intro-feature-body contenteditable="false" spellcheck="false"><\/p>/);

  // then — 공개 렌더는 편집 앵커가 없고 빈 본문은 아예 만들지 않는다
  assert.doesNotMatch(publicIntro, /data-intro-feature-body/);
  assert.match(publicIntro, /<p>본문1<\/p>/);
  assert.equal((publicIntro.match(/<p>/g) || []).length, 1, '공개 렌더가 빈 본문 자리를 만들었다');

  // then — 클릭 진입 / 섹션 적용 / 값 수집 / "수정할 곳 보기" 안내가 본문도 함께 다룬다
  assert.match(builderJs, /closest\('\[data-intro-feature-item\],\[data-intro-feature-body\]'\)/);
  assert.match(builderJs, /querySelectorAll\('\[data-intro-feature-item\],\[data-intro-feature-body\]'\)/);
  assert.match(builderJs, /card\.querySelector\('\[data-intro-feature-body\]'\)/);
  assert.match(editableGuideJs, /\[data-intro-feature-body\]/);

  // then — "제목|본문" 구분자가 편집값에 섞이면 칸이 밀리므로 수집 단계에서 걷어낸다
  assert.match(builderJs, /function notePartText\(element\)[\s\S]*?replace\(\/\\\|\/g, ' '\)/);

  // then — 빈 본문은 빌더에서만 안내 문구로 클릭 면적을 유지한다
  assert.match(publicCss, /\.builder-public-preview \[data-intro-feature-body\]:empty::before\{content:'설명을 입력하세요'/);
});

test('given 특징 저장값이 없을 때 when 목록을 렌더 then 공개·빌더가 같은 안내 placeholder를 쓴다', () => {
  // given — introFeatures 저장값이 없는 학원
  const featureTexts = (hooks) => {
    const academy = academyFixture();
    const html = primeRenderer(hooks, academy).renderIntro(academy);
    return Array.from(html.matchAll(/class="intro-feature-text"[^>]*>([^<]*)</g), (m) => m[1]);
  };
  const [[, publicHooks], [, builderHooks]] = bothRenderers();

  // when
  const publicDefaults = featureTexts(publicHooks);
  const builderDefaults = featureTexts(builderHooks);

  // then
  assert.ok(publicDefaults.length > 0, '공개 렌더가 기본 특징을 표시하지 않음');
  assert.deepEqual(publicDefaults, builderDefaults, '공개·빌더의 기본 특징 문구가 다름');
});

test('given 수업상품 탭 when 렌더 then 닫힌 상태로 시작하고 수업 관리 미리보기 골격을 쓴다', () => {
  // given / when / then — 탭은 닫힌 채 시작하고(#244 기믹), 패널 골격은 lesson_product_editor의 .preview와 같다.
  assert.match(showcaseJs, /class="lesson-product-showcase"/);
  assert.match(showcaseJs, /data-active-index=""/);
  assert.doesNotMatch(showcaseJs, /lesson-product-showcase is-expanded/, '처음부터 펼쳐진 채로 렌더됨');
  assert.match(showcaseJs, /lesson-product-tab-hint/, '닫힌 탭의 클릭 안내가 없음');
  assert.match(showcaseJs, /lesson-product-collapse/, '접이식 컨테이너가 없음');
  assert.match(showcaseJs, /class="course-nav lesson-product-nav"/);
  assert.match(showcaseJs, /class="selected-hero lesson-product-selected-hero"/);
  assert.match(showcaseJs, /class="detail-section lesson-product-detail-section"/);
  assert.match(showcaseCss, /\.course-nav\.lesson-product-nav \{/);
  assert.match(showcaseCss, /\.lesson-product-showcase\.is-expanded \.lesson-product-collapse \{ grid-template-rows: 1fr; \}/);
  assert.match(showcaseCss, /\.lesson-product-showcase \.selected-hero \{/);
  assert.match(publicCss, /\.lesson-schedule-wrap\.is-schedule-hidden \.schedule-calendar\{display:none;\}/);
  assert.match(publicJs, /data-schedule-toggle/);
  assert.match(builderJs, /wireLessonScheduleToggle/);
});

test('given sec-lessons when 공개·빌더가 렌더 then 커리큘럼과 달력 시간표 시안 골격을 갖는다', () => {
  // 시간표 본문은 수업상품에서 파생하던 행 목록(.schedule-list)을 걷어내고 원장이 올린
  // 달력 이미지 하나로 바꿨다. 그래서 시간표 섹션은 lessons가 아니라 schedule 슬롯이 좌우한다.
  const academy = academyFixture({
    heroMediaSlots: ['schedule'],
    lessons: [
      { lessonId: 1, name: '중2 내신 심화반', targetGroup: 'MIDDLE', scheduleSummary: '화·목 19:00–21:00', tabShortName: 'F2R Starter' },
      { lessonId: 2, name: '고1 내신·수능 독해반', targetGroup: 'HIGH', scheduleSummary: '토 10:00–13:00', tabShortName: 'F2R Master' }
    ]
  });

  for (const [name, hooks] of bothRenderers()) {
    const html = primeRenderer(hooks, academy).renderLessons(academy);
    assert.match(html, /classes-wrap/, `${name}: 커리큘럼 섹션 래퍼 없음`);
    assert.match(html, /대표 수업/, `${name}: 대표 수업 eyebrow 없음`);
    // 제목은 학원마다 달라야 한다 — 고정 문구가 아니라 학원명 기반 기본값 + curriculumTitle draft다.
    assert.match(html, /테스트학원 커리큘럼/, `${name}: 커리큘럼 제목 기본값이 학원명에서 파생되지 않음`);
    assert.doesNotMatch(html, /F2R 커리큘럼/, `${name}: 학원 공통 고정 문구가 남아 있음`);
    assert.match(html, /lesson-schedule-wrap/, `${name}: 시간표 섹션 없음`);
    assert.match(html, /개설 시간표/, `${name}: 시간표 eyebrow 없음`);
    assert.match(html, /지금 모집 중인 반/, `${name}: 시간표 제목 없음`);
    assert.match(html, /data-schedule-toggle/, `${name}: 시간표 토글 버튼 배선 없음`);
    assert.match(html, /role="switch" aria-checked="true"/, `${name}: 시간표 토글 접근성 상태 없음`);
    assert.match(html, /schedule-calendar-image/, `${name}: 달력 이미지가 시간표 본문에 없음`);
    assert.doesNotMatch(html, /schedule-list|sch-row/, `${name}: 걷어낸 자동 시간표 목록이 남아 있음`);
  }
});

test('given curriculumTitle draft when 렌더 then 대표수업 h2가 그 값으로 바뀌고 빌더는 인라인 편집 대상이 된다', () => {
  // given: 원장이 커리큘럼 제목을 직접 고쳐 저장한 상태
  const academy = academyFixture({
    pageSettings: { builderDraft: { curriculumTitle: '우리만의 3단계 로드맵' } },
    lessons: [{ lessonId: 1, name: '중2 내신 심화반', targetGroup: 'MIDDLE' }]
  });

  for (const [name, hooks] of bothRenderers()) {
    // when
    const html = primeRenderer(hooks, academy).renderLessons(academy);

    // then: 두 렌더러 모두 draft 값을 쓴다
    assert.match(html, /우리만의 3단계 로드맵/, `${name}: curriculumTitle draft가 제목에 반영되지 않음`);
    assert.doesNotMatch(html, /테스트학원 커리큘럼/, `${name}: 기본값이 draft를 덮어씀`);
  }

  // then: 빌더만 인라인 편집 훅을 얹는다(서버 화이트리스트에 있는 key여야 저장된다)
  const builderHtml = primeRenderer(loadRenderHooks('builder'), academy).renderLessons(academy);
  assert.match(builderHtml, /<h2[^>]*data-builder-draft="curriculumTitle"/, '빌더 h2에 편집 훅이 없음');
  assert.match(academyServiceJava, /"curriculumTitle"/, '서버 draft 화이트리스트에 curriculumTitle 없음');
});

test('given 새 sec-intro 마크업 when 공개 CSS를 확인 then 신규 클래스는 정의되고 제거된 클래스는 남지 않는다', () => {
  // given / when / then
  ['.notes-keyword', '.notes-title', '.notes-quote', '.intro-feature-list']
    .forEach((selector) => assert.ok(publicCss.includes(selector + '{'), `${selector} 정의 누락`));
  assert.doesNotMatch(publicCss, /\.intro-logo|\.intro-brand/);
  // 마크업에서 사라진 클래스는 CSS도 함께 지운다(고아 규칙 방지).
  assert.doesNotMatch(publicCss, /\.intro-summary-label|\.intro-subject-badges|\.intro-divider/);
  assert.doesNotMatch(publicCss, /\.intro-summary-area|\.intro-summary-block|\.intro-textbook/);
  // 특징 추가/삭제 컨트롤은 빌더 preview에서만 보인다
  assert.match(publicCss, /\.intro-feature-remove,\.intro-feature-add\{display:none;\}/);
  assert.match(publicCss, /\.note-card\.intro-feature-item/);
  assert.match(publicCss, /\.builder-public-preview \.intro-feature-add\{\n\s+display:inline-flex;/);
});

test('given 빌더 캔버스 when 폭이 바뀔 때 then container query로 반응한다', () => {
  // given — 캔버스 폭은 뷰포트가 아니라 컨테이너라 @media 반응형이 발화하지 않는다.
  //         그렇다고 무조건 1열로 접으면 넓은 캔버스에서도 영원히 모바일 레이아웃이 된다.
  // when / then — 컨테이너로 선언하고 폭에 따라 접혀야 한다.
  // 컨테이너는 .canvas-frame 하나뿐이다. 여기에 또 걸면 중첩돼 기준 폭이 갈린다.
  assert.match(builderTemplate, /\.canvas-frame\{[^}]*container-type:inline-size;/s);
  assert.doesNotMatch(publicCss, /\.builder-public-preview\{[^}]*container-type/);
  // 데스크탑 뷰는 canvas-area 폭을 그대로 쓴다 — 고정 폭이면 넓은 화면에서도 모바일로 접힌다.
  assert.match(builderTemplate, /\.canvas-frame\{\n\s*width:100%;max-width:none;/);
  assert.match(builderTemplate, /const widths = \{ desktop: 'none', mobile: '390px' \}/);
  // 경계는 발행 화면(@media)과 같은 값이어야 한다 — 다르면 같은 폭에서 캔버스와 발행이 갈린다.
  assert.match(publicCss, /@container \(max-width:980px\)\{/);
  assert.match(publicCss, /@container \(max-width:760px\)\{/);
  // 데스크탑 캔버스에서는 2열을 유지한다(발행 화면과 같은 구조).
  assert.match(publicCss, /\.builder-public-preview \.hero-grid\{grid-template-columns:minmax\(0,1\.05fr\) minmax\(260px,\.95fr\)/);
  // 1열 접힘은 @container 안에서만 발생해야 한다.
  const beforeContainer = publicCss.slice(0, publicCss.indexOf('@container (max-width:980px)'));
  assert.doesNotMatch(beforeContainer, /\.builder-public-preview \.hero-grid\{grid-template-columns:1fr;/);
});

test('given 시안 클래스명 when 기존 CSS와 겹치는지 확인 then 히어로 교재 카드는 제거되어 있다', () => {
  // 새 시안 히어로는 좌측 문구와 우측 iPad 원장사진만 둔다.
  for (const [name, hooks] of bothRenderers()) {
    const academy = academyFixture();
    const hero = primeRenderer(hooks, academy).renderHero(academy);
    assert.doesNotMatch(hero, /hero-textbook-card|textbook-card|book-slot/, `${name}: 구 히어로 교재 카드가 남음`);
  }
  assert.doesNotMatch(publicCss, /\.hero-textbook-card|\.book-slot|\.portrait-photo img/);
});

test('given 히어로 소개영상 when 새 시안을 확인 then 구 영상 카드 CSS는 남지 않는다', () => {
  assert.doesNotMatch(publicCss, /\.cv-hero-video-frame|\.cv-play-inner|\.cv-hero-media/);
});


test('given sec-philosophy when 공개·빌더가 렌더 then BuildIt 학습 철학 독립 섹션이 나온다', () => {
  for (const [name, hooks] of bothRenderers()) {
    // given
    const academy = academyFixture();

    // when
    const philosophy = primeRenderer(hooks, academy).renderPhilosophy(academy);

    // then
    assert.match(philosophy, /id="sec-philosophy"/, `${name}: 학습 철학 독립 섹션 id 없음`);
    assert.match(philosophy, /data-builder-section-key="sec-philosophy"/, `${name}: 빌더 섹션 키가 분리되지 않음`);
    assert.match(philosophy, /class="section-wrap philosophy"/, `${name}: philosophy 섹션 클래스 없음`);
    assert.ok(philosophy.includes('학습 철학'), `${name}: eyebrow 없음`);
    assert.ok(philosophy.includes('000 원장이'), `${name}: 실명 마스킹 제목 아님`);
    assert.equal((philosophy.match(/class="p-card"/g) || []).length, 4, `${name}: 철학 카드 4개가 아님`);
    if (name === 'builder') {
      assert.match(philosophy, /data-builder-draft="philosophyKeyword"/, `${name}: eyebrow 편집 앵커 없음`);
      assert.match(philosophy, /data-builder-draft="philosophyTitle"/, `${name}: 제목 편집 앵커 없음`);
      assert.match(philosophy, /data-builder-draft="philosophyItem1"/, `${name}: 카드 편집 앵커 없음`);
      // 빌더 캔버스는 편집 UX 우선 — 정적 그리드
      assert.match(philosophy, /class="philosophy-grid"/, `${name}: 빌더는 정적 그리드`);
    } else {
      assert.doesNotMatch(philosophy, /data-builder-draft/, `${name}: 공개 화면에 편집 앵커가 샘`);
      // 공개 페이지는 대표수업과 동일한 .f2r-collection 횡스크롤(새 carousel 없음)
      assert.match(philosophy, /class="f2r-collection f2r-collection--philosophy"/, `${name}: 학습철학 횡스크롤 컬렉션 아님`);
      assert.match(philosophy, /data-collection-track/, `${name}: 컬렉션 트랙 없음`);
      assert.doesNotMatch(philosophy, /class="philosophy-grid"/, `${name}: 공개에 정적 그리드가 남음`);
    }
  }
});


test('given 학습 철학 편집값 when 공개·빌더가 렌더 then builderDraft 키로 제목과 카드 문구를 바꾼다', () => {
  const academy = academyFixture({
    pageSettings: {
      builderDraft: {
        philosophyKeyword: '교육 원칙',
        philosophyTitle: '우리 원장이\n가르치는 기준',
        philosophyItem1: '첫 번째 원칙',
        philosophyItem4: '네 번째 원칙'
      }
    }
  });
  for (const [name, hooks] of bothRenderers()) {
    const html = primeRenderer(hooks, academy).renderPhilosophy(academy);
    assert.ok(html.includes('교육 원칙'), `${name}: keyword draft 미반영`);
    assert.ok(html.includes('우리 원장이'), `${name}: title draft 미반영`);
    assert.ok(html.includes('첫 번째 원칙'), `${name}: item1 draft 미반영`);
    assert.ok(html.includes('네 번째 원칙'), `${name}: item4 draft 미반영`);
  }

  assert.match(academyServiceJava, /"philosophyTitle"/);
  assert.match(academyServiceJava, /"philosophyItem1"/);
});

test('given 학원 소개 기본 문구 when 공개·빌더를 렌더 then notes 시안 예시가 기본값이다', () => {
  // 빈 화면이나 입력 안내문 대신 완성된 시안 모습을 먼저 보여준다.
  for (const [name, hooks] of bothRenderers()) {
    // given — 저장값이 하나도 없는 학원
    const academy = academyFixture();

    // when
    const intro = primeRenderer(hooks, academy).renderIntro(academy);

    // then
    assert.ok(intro.includes('📖 COLUMN'), `${name}: 키워드 기본값 아님`);
    assert.ok(intro.includes('000 원장의'), `${name}: 제목 기본값 아님`);
    assert.ok(intro.includes('학생의 가능성을 발견하는 일입니다.'), `${name}: 인용문 기본값 아님`);
    assert.ok(intro.includes('학생의 마음을 먼저 읽어야 하는 이유'), `${name}: 노트 기본값 아님`);
    // 구 입력 안내문이 남아 있으면 시안과 다른 화면이 나온다.
    assert.doesNotMatch(intro, /학원의 강점을 입력해 주세요/, `${name}: 구 placeholder가 남아 있음`);
  }
});
