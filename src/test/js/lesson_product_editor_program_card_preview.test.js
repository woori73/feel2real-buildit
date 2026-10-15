const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { loadSectionsModule } = require('./helpers/browser_script_harness');

// V2-7: 가로 컬렉션(Storefront V2) 학원의 Program Editor 미리보기는 공개 홈페이지와
//       똑같은 F2RAcademySections.programCardHtml 을 호출한다 — 하나의 렌더 계약.
//       이 테스트는 편집기가 쓰는 것과 같은 모양의 ctx 로 그 함수를 직접 돌려,
//       편집기 draft 필드가 실제 storefront 카드 구조로 렌더되는지 고정한다.

const EDITOR_JS = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/lesson_product_editor.js'),
  'utf8'
);

/** 편집기 lesson_product_editor.js 의 EDITOR_CARD_CTX 와 같은 슬롯 2개만 채운 ctx. */
const EDITOR_CTX = {
  programCardMedia(lesson) {
    if (lesson.mediaType === 'IMAGE' && lesson.lessonId != null) {
      return `<button type="button" class="f2r-program-card-media" data-editor-media="hero">`
        + `<img src="/api/tutor/academy/lessons/${lesson.lessonId}/hero-image" alt=""></button>`;
    }
    return '<button type="button" class="f2r-program-card-media f2r-program-card-media-empty"'
      + ' data-editor-media="hero">대표 이미지 또는 수업 영상을<br>등록해 주세요</button>';
  },
  collectionConsultCta(labelText) {
    return `<span class="f2r-program-card-cta" aria-hidden="true">${labelText || '상담 신청하기'}</span>`;
  }
};

test('편집기는 정본 programCardHtml 을 그대로 호출한다(별도 카드 마크업 없음)', () => {
  assert.match(EDITOR_JS, /SECTIONS\.programCardHtml\(lesson, EDITOR_CARD_CTX\)/);
  assert.match(EDITOR_JS, /window\.F2RAcademySections/);
  // 편집기가 카드 <article> 마크업을 자체적으로 다시 만들지 않는다.
  assert.doesNotMatch(EDITOR_JS, /f2r-program-card-body/);
  assert.doesNotMatch(EDITOR_JS, /<article class="f2r-program-card/);
});

test('편집기 draft 필드 → storefront Program Card 구조로 렌더된다', () => {
  const sections = loadSectionsModule();
  const draft = {
    lessonId: 42,
    tabShortName: '개념완성',
    name: '중등 수학 개념완성 집중반',
    kicker: '개념부터 실전까지 한 번에',
    learnerNeed: '어디서부터 풀어야 할지 막힐 때',
    highlight: '개념 이해와 문제 적용력을 함께',
    gradeLabel: '고등학교 2학년',
    mediaType: 'IMAGE'
  };
  const html = sections.programCardHtml(draft, EDITOR_CTX);

  // 공개 storefront 와 동일한 카드 골격/클래스.
  assert.match(html, /<article class="f2r-program-card has-media">/);
  assert.match(html, /f2r-program-card-eyebrow">개념완성</);
  assert.match(html, /f2r-program-card-name">중등 수학 개념완성 집중반</);
  assert.match(html, /f2r-program-card-kicker">개념부터 실전까지 한 번에</);
  assert.match(html, /f2r-program-card-need">어디서부터 풀어야 할지 막힐 때</);
  assert.match(html, /f2r-program-card-highlight">개념 이해와 문제 적용력을 함께</);
  assert.match(html, /f2r-program-card-grade">고등학교 2학년</);
  // CTA 는 고정 동작 — 편집기에서는 정적 표시(버튼 onclick 없음).
  assert.match(html, /f2r-program-card-cta" aria-hidden="true">상담 신청하기</);
  assert.doesNotMatch(html, /onclick/);
  // 편집기 미디어 슬롯은 클릭 시 모달을 여는 트리거.
  assert.match(html, /data-editor-media="hero"/);
});

test('빈 필드는 카드에서 생략된다(가짜 데이터 없음)', () => {
  const sections = loadSectionsModule();
  const html = sections.programCardHtml(
    { lessonId: 1, name: '이름만 있는 수업', mediaType: null },
    EDITOR_CTX
  );
  assert.doesNotMatch(html, /f2r-program-card-kicker/);
  assert.doesNotMatch(html, /f2r-program-card-need/);
  assert.doesNotMatch(html, /f2r-program-card-highlight/);
  assert.doesNotMatch(html, /f2r-program-card-grade/);
  assert.match(html, /f2r-program-card-media-empty/);
});

test('gradeLabel 이 없으면 tabGuidanceLabel 로 대상을 채운다(기존 계약 유지)', () => {
  const sections = loadSectionsModule();
  const html = sections.programCardHtml(
    { lessonId: 1, name: '수업', tabGuidanceLabel: '중등 1학년 - 3학년', mediaType: null },
    EDITOR_CTX
  );
  assert.match(html, /f2r-program-card-grade">중등 1학년 - 3학년</);
});
