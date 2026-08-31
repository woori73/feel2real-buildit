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
    assert.match(html, /<ol class="ai-native-steps">/, `${kind}: 단계 목록`);
    assert.match(html, /class="ai-native-step" data-ai-step="0"/, `${kind}: 번호가 매겨진 단계`);
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

// ── PHASE 2.1: 섹션 제목 독립 + Storefront 중앙 정렬 ─────────────────────────

const fs = require('node:fs');
const path = require('node:path');
const PUBLIC_CSS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/academy_public.css'), 'utf8');

test('sec-videos / sec-social 제목은 학원명·고정문구가 아니라 독립 draft 키로 편집한다', () => {
  for (const [kind, hooks] of bothRenderers()) {
    // 미설정: 기존 사이트 호환 — 학원명 파생값 / 고정 문구로 fallback
    const base = academyFixture({ name: '테스트영어교실', pageSettings: { sectionVisibility: {}, builderDraft: {} } });
    primeRenderer(hooks, base, []);
    assert.match(hooks.renderVideos(base), /테스트영어교실의 수업 현장/, `${kind}: videos 기본 제목 fallback`);
    assert.match(hooks.renderExternalServices(base), /최신 소식 (&|&amp;) 방문 안내/, `${kind}: social 기본 제목 fallback`);

    // 설정: 각 섹션 제목을 서로 다른 값으로 독립 편집
    const custom = academyFixture({ name: '테스트영어교실', pageSettings: {
      sectionVisibility: {},
      builderDraft: { videosSectionTitle: '에듀홀릭의 실제 수업 현장', socialSectionTitle: '학원 소식과 오시는 길' }
    }});
    primeRenderer(hooks, custom, []);
    const v = hooks.renderVideos(custom);
    const s = hooks.renderExternalServices(custom);
    assert.match(v, /에듀홀릭의 실제 수업 현장/, `${kind}: videos 커스텀 제목 미반영`);
    assert.doesNotMatch(v, /테스트영어교실의 수업 현장/, `${kind}: videos 옛 파생 제목 잔존`);
    assert.match(s, /학원 소식과 오시는 길/, `${kind}: social 커스텀 제목 미반영`);
    if (kind === 'builder') {
      assert.match(v, /data-builder-draft="videosSectionTitle"/, 'builder: videos 제목 편집 앵커 없음');
      assert.match(s, /data-builder-draft="socialSectionTitle"/, 'builder: social 제목 편집 앵커 없음');
    } else {
      assert.doesNotMatch(v + s, /data-builder-draft/, 'public: 편집 앵커 누수');
    }
  }
});

test('공개 CSS 리셋이 Storefront 설명/주석의 auto 마진과 margin-top을 덮지 않는다', () => {
  // #publicView :is(h1..p) 리셋은 specificity가 높아 클래스 규칙을 이긴다 →
  // featured-desc/ai-process-desc/ai-process-note가 예외 목록에 있어야 중앙 정렬 + 카드 겹침 방지가 유지된다.
  assert.match(PUBLIC_CSS, /:not\([^)]*\.featured-desc[^)]*\.ai-native-desc[^)]*\.ai-native-note[^)]*\)\{margin:0;\}/);
  assert.match(PUBLIC_CSS, /\.featured-desc\{margin:16px auto 0;[^}]*max-width:5[0-9]0px/);
  assert.match(PUBLIC_CSS, /\.ai-native-desc\{margin:14px auto 0;[^}]*max-width:620px/);
  assert.match(PUBLIC_CSS, /\.ai-native-note\{margin:32px auto 0;max-width:600px;text-align:center/);
});
