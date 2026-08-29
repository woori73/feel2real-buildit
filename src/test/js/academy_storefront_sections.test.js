const assert = require('node:assert/strict');
const test = require('node:test');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');

// Education Storefront 신규 섹션(sec-featured, sec-digitaltwin=AI Native 교육 과정)은
// 재사용 가능한 generic 섹션이고, 기존 사이트에 자동으로 나타나면 안 된다(opt-in, 기본 OFF).

function fixture(pageSettings) {
  return academyFixture({ name: '테스트영어교실', pageSettings });
}

test('opt-in 섹션은 visibility 키가 없으면 공개/빌더 모두 렌더되지 않는다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    primeRenderer(hooks, fixture({ sectionVisibility: {}, builderDraft: {} }));
    assert.equal(hooks.renderFeatured(fixture({})), '', `${kind}: featured`);
    assert.equal(hooks.renderAiProcess(fixture({})), '', `${kind}: ai-process`);
  }
});

test('opt-in 섹션은 sectionVisibility[id] === true 일 때만 렌더된다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const on = fixture({ sectionVisibility: { 'sec-featured': true, 'sec-digitaltwin': true }, builderDraft: {} });
    primeRenderer(hooks, on);
    const featured = hooks.renderFeatured(on);
    const ai = hooks.renderAiProcess(on);
    assert.match(featured, /<section id="sec-featured"/, `${kind}: featured section`);
    assert.match(ai, /<section id="sec-digitaltwin"/, `${kind}: ai section id stays sec-digitaltwin`);

    const off = fixture({ sectionVisibility: { 'sec-featured': false }, builderDraft: {} });
    primeRenderer(hooks, off);
    assert.equal(hooks.renderFeatured(off), '', `${kind}: explicit false hides featured`);
  }
});

test('기존 opt-out 섹션은 visibility 키가 없어도 그대로 렌더된다(회귀 방지)', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const academy = fixture({ sectionVisibility: {}, builderDraft: {} });
    primeRenderer(hooks, academy);
    assert.match(hooks.renderIntro(academy), /<section id="sec-intro"/, `${kind}: intro still renders`);
    assert.match(hooks.renderPhilosophy(academy), /<section id="sec-philosophy"/, `${kind}: philosophy still renders`);
  }
});

test('featured 섹션은 builderDraft 값을 쓰고 없으면 generic 기본값으로 떨어진다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const filled = fixture({
      sectionVisibility: { 'sec-featured': true },
      builderDraft: {
        featuredEyebrow: '2026 FALL',
        featuredTitle: '우리 학교 2학기 내신',
        featuredItem1: '압구정중',
        featuredItem2: '신구중',
        featuredCtaLabel: '상담 신청하기'
      }
    });
    primeRenderer(hooks, filled);
    const html = hooks.renderFeatured(filled);
    assert.match(html, /2026 FALL/, `${kind}: eyebrow`);
    assert.match(html, /우리 학교 2학기 내신/, `${kind}: title`);
    assert.match(html, /압구정중/, `${kind}: item1`);
    assert.match(html, /신구중/, `${kind}: item2`);

    const empty = fixture({ sectionVisibility: { 'sec-featured': true }, builderDraft: {} });
    primeRenderer(hooks, empty);
    const fallback = hooks.renderFeatured(empty);
    assert.match(fallback, /FEATURED/, `${kind}: eyebrow fallback`);
    assert.doesNotMatch(fallback, /압구정중|신구중|2026/, `${kind}: no hardcoded school/year`);
  }
});

test('AI Native 교육 과정은 번호가 매겨진 단계 흐름으로 렌더된다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    const academy = fixture({
      sectionVisibility: { 'sec-digitaltwin': true },
      builderDraft: {
        aiProcessStep1: '학교 자료',
        aiProcessStep2: '출제자 분석',
        aiProcessStep3: '수업 인사이트',
        aiProcessStep4: '학교별 문제 제작',
        aiProcessStep5: 'FINAL'
      }
    });
    primeRenderer(hooks, academy);
    const html = hooks.renderAiProcess(academy);
    assert.match(html, /<ol class="ai-process-steps">/, `${kind}: ordered step list`);
    ['학교 자료', '출제자 분석', '수업 인사이트', '학교별 문제 제작', 'FINAL'].forEach((label) => {
      assert.match(html, new RegExp(label), `${kind}: step ${label}`);
    });
    // 공개 화면에는 개발 용어를 쓰지 않는다.
    assert.doesNotMatch(html, /RAG|LLM|Embedding|Vector DB|MCP/, `${kind}: no dev jargon`);
  }
});

test('빌더 렌더는 편집 앵커를 얹고 공개 렌더는 얹지 않는다', () => {
  const map = Object.fromEntries(bothRenderers());
  const academy = fixture({
    sectionVisibility: { 'sec-featured': true, 'sec-digitaltwin': true },
    builderDraft: { featuredItem1: '압구정중', aiProcessStep1: '학교 자료' }
  });

  primeRenderer(map.builder, academy);
  const builderFeatured = map.builder.renderFeatured(academy);
  assert.match(builderFeatured, /data-builder-draft="featuredEyebrow"/);
  assert.match(builderFeatured, /data-builder-draft="featuredItem1"/);
  assert.match(map.builder.renderAiProcess(academy), /data-builder-draft="aiProcessStep1"/);

  primeRenderer(map.public, academy);
  const publicFeatured = map.public.renderFeatured(academy);
  assert.doesNotMatch(publicFeatured, /data-builder-draft/);
  assert.match(publicFeatured, /onclick="requestConsult\(\)"/);
});
