const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const publicCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_public.css'),
  'utf8'
);
const lessonCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/lesson_product_showcase.css'),
  'utf8'
);
const footerCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/academy_business_footer.css'),
  'utf8'
);
const builderTemplate = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/tutor/academy_builder.html'),
  'utf8'
);
const publicTemplate = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/public/academy_public.html'),
  'utf8'
);

function compact(value) {
  // 주석을 남기면 셀렉터 앞 경계가 `*/`가 되어 선언 블록을 못 찾고,
  // 주석 안의 중괄호가 atRuleBody의 깊이 계산까지 흔든다.
  return value.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\s+/g, '');
}

function atRuleBody(css, header) {
  const headerIndex = compact(css).indexOf(compact(header));
  assert.notEqual(headerIndex, -1, `${header} must exist`);

  const compacted = compact(css);
  const openIndex = compacted.indexOf('{', headerIndex);
  let depth = 0;
  for (let index = openIndex; index < compacted.length; index += 1) {
    if (compacted[index] === '{') depth += 1;
    if (compacted[index] === '}') depth -= 1;
    if (depth === 0) return compacted.slice(openIndex + 1, index);
  }
  assert.fail(`${header} must have a balanced block`);
}

// 선언문 전체를 문자열로 박아두면 같은 규칙에 속성 하나만 더해도 테스트가 깨진다.
// 셀렉터의 선언 블록만 뽑아 필요한 속성의 값을 확인한다.
/** compact()된 CSS에서 찾기 — 기대 문자열도 같은 규칙으로 압축해 공백 차이로 깨지지 않게 한다. */
function includesDecl(haystack, needle) {
  return haystack.includes(compact(needle));
}

function declarationsFor(atRuleBodyText, selector) {
  const compactSelector = compact(selector);
  const pattern = new RegExp(
    `(?:^|[;}])${compactSelector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\{([^}]*)\\}`
  );
  const match = atRuleBodyText.match(pattern);
  assert.ok(match, `${selector} must be declared in this at-rule`);
  return match[1];
}

test('given published academy page when opened on portrait phone then core sections stack without losing hero media', () => {
  // given
  const tabletRules = atRuleBody(publicCss, '@media(max-width:768px)');

  // when / then
  assert.match(publicTemplate, /name="viewport"\s+content="width=device-width,\s*initial-scale=1\.0"/);
  assert.match(declarationsFor(tabletRules, '.principal-grid,.social-grid'), /grid-template-columns:1fr;/);
  assert.ok(!compact(publicCss).includes('.cv-hero-media{display:none;}'));
  assert.ok(!compact(publicCss).includes('overflow-x:clip'));
});

/**
 * V8 히어로·소개는 시안이 980/760이라는 자체 브레이크포인트를 쓴다.
 * 페이지 공용 768/480에 얹으면 시안과 형태가 어긋나므로 경계째로 옮겨왔다.
 */
test('given V8 hero when the viewport narrows then it collapses at the 시안 breakpoint, not the page default', () => {
  // given
  const wideRules = atRuleBody(publicCss, '@media (max-width:980px)');
  const narrowRules = atRuleBody(publicCss, '@media (max-width:760px)');

  // when / then
  assert.ok(includesDecl(declarationsFor(wideRules, '.hero-grid'), 'grid-template-columns:1fr;'));
  // 시안은 좁아지면 원장 사진을 텍스트 위로 올린다(order:-1). 이게 빠지면 순서가 시안과 달라진다.
  assert.match(declarationsFor(wideRules, '.hero .tablet'), /order:-1;/);
  assert.ok(includesDecl(declarationsFor(narrowRules, '.hero'), 'padding-top:110px;'));
});

test('given notes intro section when the viewport narrows then it remains a horizontal snap carousel', () => {
  const compactCss = compact(publicCss);
  const wideRules = atRuleBody(publicCss, '@media(max-width:980px)');
  const narrowRules = atRuleBody(publicCss, '@media(max-width:760px)');
  const canvasWide = atRuleBody(publicCss, '@container (max-width:980px)');
  const canvasNarrow = atRuleBody(publicCss, '@container (max-width:760px)');

  // notes-track은 시안처럼 가로 캐러셀이다. 이전 intro grid 3→2→1 접힘 규칙이 남으면 깨진다.
  assert.match(compactCss, /\.notes-track\.intro-feature-list\{[^}]*display:flex;/);
  assert.match(compactCss, /\.note-card\.intro-feature-item\{[^}]*flex:00auto;/);
  assert.doesNotMatch(wideRules, /\.intro-feature-list\{[^}]*grid-template-columns/);
  assert.doesNotMatch(narrowRules, /\.intro-feature-list\{[^}]*grid-template-columns/);
  assert.doesNotMatch(canvasWide, /\.builder-public-preview \.intro-feature-list\{[^}]*grid-template-columns/);
  assert.doesNotMatch(canvasNarrow, /\.builder-public-preview \.intro-feature-list\{[^}]*grid-template-columns/);
});

test('given 빌더 캔버스 when 경계를 확인 then 발행 화면과 같은 폭에서 접힌다', () => {
  // given — 경계가 다르면 같은 폭에서 캔버스와 발행 화면이 서로 다른 레이아웃을 보여준다.
  const canvasWide = atRuleBody(publicCss, '@container (max-width:980px)');
  const canvasNarrow = atRuleBody(publicCss, '@container (max-width:760px)');

  // when / then
  assert.ok(includesDecl(
    declarationsFor(canvasWide, '.builder-public-preview .hero-grid'),
    'grid-template-columns:1fr;'
  ));
  assert.doesNotMatch(canvasWide, /\.builder-public-preview \.intro-feature-list\{[^}]*grid-template-columns/);
  assert.doesNotMatch(canvasNarrow, /\.builder-public-preview \.intro-feature-list\{[^}]*grid-template-columns/);
  // 옛 경계(720/900)가 되살아나면 발행 화면과 다시 갈린다.
  assert.doesNotMatch(publicCss, /@container \(max-width:720px\)/);
  assert.doesNotMatch(publicCss, /@container \(min-width:721px\)/);
});

/**
 * 이 리셋은 specificity가 (1,1,1)이라 클래스 규칙을 전부 이긴다.
 * V8 제목(h1/h2)·인용문(p), Storefront 설명/주석이 여기 걸리면 시안의 세로 리듬과 가운데 정렬이
 * 통째로 사라진다(margin:X auto 0의 auto가 0으로, margin-top이 0으로 덮여 카드와 겹친다).
 */
test('given the publicView margin reset then hero and notes typography keeps its own spacing', () => {
  const css = compact(publicCss);

  // 리셋이 notes 요소와 Storefront 설명/주석을 건드리지 않아야 한다
  assert.match(css, /#publicView:is\(h1,h2,h3,h4,p\):not\(\.notes-title,\.notes-quote,\.featured-desc,\.ai-process-desc,\.ai-process-note\)\{margin:0;\}/);
  // 새 히어로는 h1과 quote가 자기 여백을 직접 가진다.
  assert.ok(includesDecl(css, 'margin:0;'));
  assert.ok(includesDecl(css, 'margin:18px 0 0;'));
  // .notes-quote는 auto가 빠지면 블록이 왼쪽으로 붙어 가운데 정렬을 잃는다
  assert.ok(includesDecl(css, 'margin:16px auto 0;'));
  // Storefront 설명/주석도 auto 마진과 margin-top이 리셋에 덮이지 않아야 한다(카드 겹침 방지)
  assert.ok(includesDecl(css, 'margin:14px auto 0;'), 'ai-process-desc 중앙 정렬 마진');
  assert.ok(includesDecl(css, 'margin:32px auto 0;'), 'ai-process-note margin-top 확보 + 중앙 정렬');
});

test('given narrow portrait phone when rendering published academy then header actions cards and modal stay inside viewport', () => {
  // given
  const phoneRules = atRuleBody(publicCss, '@media(max-width:480px)');

  // when / then
  assert.ok(phoneRules.includes('.cv-gnb{grid-template-columns:minmax(0,1fr)auto;'));
  assert.ok(phoneRules.includes('.subject-grid,.blog-grid{grid-template-columns:1fr;}'));
  assert.ok(compact(publicCss).includes('#applyModal>div{box-sizing:border-box;'));
  assert.ok(compact(publicCss).includes('max-height:calc(100dvh-32px);overflow-y:auto;'));
  assert.ok(atRuleBody(lessonCss, '@media(max-width:480px)').includes('.lesson-product-showcase.selected-hero{padding:24px18px;}'));
});

test('given builder mobile preview when frame narrows then shared public layout uses container responsive rules', () => {
  // given
  const tabletPreviewRules = atRuleBody(publicCss, '@container(max-width:768px)');
  const phonePreviewRules = atRuleBody(publicCss, '@container(max-width:480px)');
  const template = compact(builderTemplate);

  // when / then
  assert.ok(template.includes('.canvas-frame{'));
  assert.ok(template.includes('container-type:inline-size;'));
  assert.ok(tabletPreviewRules.includes('.builder-public-preview.cv-gnb{display:grid;'));
  assert.ok(tabletPreviewRules.includes('.builder-public-preview.hero-grid'));
  assert.ok(tabletPreviewRules.includes('.builder-public-preview.intro-grid'));
  assert.ok(phonePreviewRules.includes('.builder-public-preview.video-playlist-item{flex-basis:116px;}'));
  assert.ok(atRuleBody(lessonCss, '@container(max-width:768px)').includes('.builder-public-preview.lesson-product-showcase.selected-hero{'));
  assert.ok(atRuleBody(footerCss, '@container(max-width:640px)').includes('.builder-public-preview.academy-business-footer__row{flex-direction:column;gap:3px;}'));
});
