const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const brand = require('../../main/resources/static/js/academy_brand.js');
const { bothRenderers, primeRenderer, academyFixture } = require('./helpers/academy_render_hooks');
const { loadBrowserScript } = require('./helpers/browser_script_harness');

const PUBLIC_JS = path.resolve(__dirname, '../../main/resources/static/js/academy_public.js');
const BUILDER_JS = path.resolve(__dirname, '../../main/resources/static/js/academy_builder.js');
const PUBLIC_CSS = path.resolve(__dirname, '../../main/resources/static/css/academy_public.css');

function builderTestHooks() {
  const { window } = loadBrowserScript(BUILDER_JS, {
    window: { F2R_ACADEMY_SECTION_ORDER_TEST: true }
  });
  return window.F2RAcademyBuilderTestHooks;
}

// ═══════════════ P0-1: 5종 디자인 프리셋 ═══════════════

test('given design presets when inspecting then five presets exist with full definitions', () => {
  // given / when / then
  const ids = Object.keys(brand.DESIGN_PRESETS).sort();
  assert.deepEqual(ids, ['ARTS', 'ENGLISH', 'EXAM', 'ONLINE', 'STUDY']);
  for (const id of ids) {
    const preset = brand.DESIGN_PRESETS[id];
    assert.ok(preset.label, `${id}: label`);
    assert.ok(preset.description, `${id}: description`);
    // 기존 타이포 프리셋 5종을 재사용한다(중복 구현 금지)
    assert.ok(brand.PRESETS[preset.typography], `${id}: typography ${preset.typography}는 등록된 타이포 프리셋이어야 한다`);
    assert.deepEqual(Object.keys(preset.colors).sort(), [...brand.BRAND_COLOR_KEYS].sort());
    assert.ok(preset.heroLayout, `${id}: heroLayout`);
    assert.ok(preset.cardStyle, `${id}: cardStyle`);
  }
});

test('given design preset when creating draft then custom values are never overwritten (fill-in)', () => {
  // given — 사용자가 직접 고른 주색 + 타이포
  const current = { colorPrimary: '#ff0000', typographyPreset: 'PREMIUM' };
  // when
  const { draft } = brand.designPresetDraft('ARTS', current, {});
  // then
  assert.equal(draft.colorPrimary, '#ff0000');
  assert.equal(draft.typographyPreset, 'PREMIUM');
  assert.equal(draft.designPreset, 'ARTS');
  assert.equal(draft.colorHeroBg, brand.DESIGN_PRESETS.ARTS.colors.heroBg);
});

test('given design preset switch when previous preset filled values then they are replaced but customs stay', () => {
  // given — ARTS 적용 상태(주색만 직접 바꿈)
  const first = brand.designPresetDraft('ARTS', { colorPrimary: '#ff0000' }, {}).draft;
  // when — ONLINE으로 전환
  const { draft } = brand.designPresetDraft('ONLINE', first, {});
  // then
  assert.equal(draft.designPreset, 'ONLINE');
  assert.equal(draft.colorPrimary, '#ff0000', '직접 바꾼 주색은 유지');
  assert.equal(draft.colorHeroBg, brand.DESIGN_PRESETS.ONLINE.colors.heroBg, '프리셋이 채운 값은 새 프리셋 값으로 교체');
});

test('given unknown design preset id when creating draft then falls back to a registered preset', () => {
  // given / when
  const { draft } = brand.designPresetDraft('NOPE', {}, {});
  // then
  assert.ok(brand.DESIGN_PRESETS[draft.designPreset]);
});

test('given academy without design preset when applying then no preset attrs and no brand vars (zero regression)', () => {
  // given — 에듀홀릭 같은 기존 학원: designPreset 없음
  const calls = {};
  const root = {
    dataset: {},
    style: {
      setProperty(k, v) { calls[k] = v; },
      removeProperty(k) { delete calls[k]; }
    },
    ownerDocument: null
  };
  // when
  brand.apply(root, { pageSettings: { builderDraft: {} } });
  // then — data-design-preset이 없고 --brand-* 인라인 변수가 하나도 없어
  // 스타일시트 기본값(기존 색상 1:1)이 그대로 적용된다
  assert.equal(root.dataset.designPreset, undefined);
  assert.deepEqual(Object.keys(calls).filter((k) => k.startsWith('--brand-')), []);
});

test('given design preset section hints when visibility already chosen then no change is suggested for chosen ones', () => {
  // given — ARTS는 sec-videos·sec-minilectures ON을 권장하지만 사용자가 이미 선택했다
  // when
  const { sectionChanges } = brand.designPresetDraft('ARTS', {}, { 'sec-videos': false, 'sec-minilectures': true });
  // then
  assert.deepEqual(sectionChanges, []);
});

test('given design preset section hints when visibility partially chosen then only unchosen are suggested', () => {
  // given — ARTS 권장 중 sec-videos만 사용자가 이미 OFF로 골랐다
  // when
  const { sectionChanges } = brand.designPresetDraft('ARTS', {}, { 'sec-videos': false });
  // then — sec-minilectures만 제안된다
  assert.deepEqual(sectionChanges, [{ sectionId: 'sec-minilectures', visible: true }]);
});

// ═══════════════ P0-2: 색상 테마 변수화 ═══════════════

test('given public css when inspecting then ten brand variables default to existing colors', () => {
  // given / when
  const css = fs.readFileSync(PUBLIC_CSS, 'utf8');
  // then — 기본값은 기존 색상과 1:1
  for (const [variable, value] of [
    ['--brand-primary', '#2b3a9e'], ['--brand-secondary', '#c9a13b'],
    ['--brand-accent', '#2563eb'], ['--brand-bg', '#ffffff'],
    ['--brand-bg-alt', '#f6f7f9'], ['--brand-text', '#0a0a0c'],
    ['--brand-text-light', '#67707c'], ['--brand-border', '#e8eaee'],
    ['--brand-hero-bg', '#1a2b4a'], ['--brand-hero-text', '#ffffff']
  ]) {
    assert.match(css, new RegExp(`${variable.replace(/-/g, '\\-')}\\s*:\\s*${value}`), `${variable} 기본값 ${value}`);
  }
});

test('given public css when inspecting then five presets remap legacy tokens and define layouts', () => {
  // given / when
  const css = fs.readFileSync(PUBLIC_CSS, 'utf8');
  // then
  for (const id of ['EXAM', 'ENGLISH', 'ARTS', 'STUDY', 'ONLINE']) {
    assert.match(css, new RegExp(`\\[data-design-preset="${id}"\\]`), `${id} 프리셋 규칙`);
  }
  for (const layout of ['center', 'visual', 'gradient']) {
    assert.match(css, new RegExp(`\\[data-hero-layout="${layout}"\\]`), `히어로 레이아웃 ${layout}`);
  }
  for (const style of ['bordered', 'rounded', 'media', 'minimal']) {
    assert.match(css, new RegExp(`\\[data-card-style="${style}"\\]`), `카드 스타일 ${style}`);
  }
  assert.match(css, /\.cta-section/);
});

// ═══════════════ P0-3: 상담 신청 CTA ═══════════════

test('given sec-cta when visibility is absent then both renderers output nothing (default OFF)', () => {
  // given — 기존 테넌트: visibility 키 없음
  for (const [name, hooks] of bothRenderers()) {
    const academy = academyFixture();
    // when
    const html = primeRenderer(hooks, academy).renderCta(academy);
    // then
    assert.equal(html, '', `${name}: 기본 OFF`);
  }
});

test('given sec-cta on when phone and kakao are set then public renders tel and kakao links', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = academyFixture({
    pageSettings: {
      sectionVisibility: { 'sec-cta': true },
      builderDraft: { ctaKakaoUrl: 'https://pf.kakao.com/_abc' },
      businessInfo: { phone: '02-6324-1453' }
    }
  });
  // when
  const html = hooks.renderCta(academy);
  // then
  assert.match(html, /id="sec-cta"/);
  assert.match(html, /href="tel:0263241453"/);
  assert.match(html, /href="https:\/\/pf\.kakao\.com\/_abc"/);
  assert.match(html, /target="_blank"/);
  assert.match(html, /지금 상담 신청하세요/);
});

test('given sec-cta on when no channels then public hides buttons but builder keeps edit entry', () => {
  // given — 전화·카카오 둘 다 없음
  const academy = academyFixture({
    pageSettings: {
      sectionVisibility: { 'sec-cta': true },
      builderDraft: {},
      businessInfo: {}
    }
  });
  // when
  const [pubName, pubHooks] = bothRenderers().find(([name]) => name === 'public');
  const [bldName, bldHooks] = bothRenderers().find(([name]) => name === 'builder');
  const pubHtml = pubHooks.renderCta(academy);
  const bldHtml = primeRenderer(bldHooks, academy).renderCta(academy);
  // then
  assert.doesNotMatch(pubHtml, /cta-btn/, `${pubName}: 버튼 없음`);
  assert.match(bldHtml, /data-cta-edit/, `${bldName}: 편집 진입점 유지`);
});

test('given sec-cta on when draft overrides phone then draft phone wins over business info', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = academyFixture({
    pageSettings: {
      sectionVisibility: { 'sec-cta': true },
      builderDraft: { ctaPhone: '010-1234-5678' },
      businessInfo: { phone: '02-6324-1453' }
    }
  });
  // when
  const html = hooks.renderCta(academy);
  // then
  assert.match(html, /href="tel:01012345678"/);
});

// ═══════════════ P0-4: 발행 전 검증 ═══════════════

test('given publish request when name or phone is missing then validation blocks with message', () => {
  // given
  const hooks = builderTestHooks();
  // when / then — 학원명 없음
  let result = hooks.validateBeforePublish({ name: '', pageSettings: { businessInfo: { phone: '02-1234-5678' } } });
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.includes('학원명')));
  // when / then — 전화번호 없음
  result = hooks.validateBeforePublish({ name: '테스트학원', pageSettings: { businessInfo: {} } });
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.includes('전화번호')));
  // when / then — 마스킹 dummy는 전화번호가 아니다
  result = hooks.validateBeforePublish({ name: '테스트학원', pageSettings: { businessInfo: { phone: '000-0000-0000' } } });
  assert.equal(result.ok, false);
});

test('given publish request when CTA is on then phone and kakao url are both required', () => {
  // given
  const hooks = builderTestHooks();
  const base = (draft) => ({
    name: '테스트학원',
    pageSettings: {
      businessInfo: { phone: '02-1234-5678' },
      builderDraft: draft,
      sectionVisibility: { 'sec-cta': true }
    }
  });
  // when / then — 카카오 URL 없음
  let result = hooks.validateBeforePublish(base({}));
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.includes('카카오톡')));
  // when / then — 둘 다 있음
  result = hooks.validateBeforePublish(base({ ctaKakaoUrl: 'https://pf.kakao.com/_abc' }));
  assert.equal(result.ok, true);
  assert.equal(result.errors.length, 0);
});

test('given publish request when everything is valid then validation passes', () => {
  // given
  const hooks = builderTestHooks();
  // when
  const result = hooks.validateBeforePublish({
    name: '테스트학원',
    pageSettings: { businessInfo: { phone: '02-1234-5678' }, builderDraft: {} }
  });
  // then
  assert.equal(result.ok, true);
});

// ═══════════════ P0 보완: 프리셋별 히어로 DOM 차별화 ═══════════════

function heroFixture(presetId, overrides = {}) {
  const { pageSettings, ...rest } = overrides;
  return academyFixture({
    ...rest,
    pageSettings: {
      builderDraft: { designPreset: presetId },
      ...(pageSettings || {})
    }
  });
}

test('given no design preset when rendering hero then legacy DOM is unchanged (byte-identical structure)', () => {
  // given — 기존 테넌트: designPreset 없음
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = academyFixture();
  // when
  const html = hooks.renderHero(academy);
  // then — 기존 구조 그대로: .hero-grid + .tablet, 프리셋 전용 클래스 없음
  assert.match(html, /class="hero-grid"/);
  assert.match(html, /class="tablet"/);
  assert.doesNotMatch(html, /hero-exam|hero-english|hero-arts|hero-study|hero-online/);
  assert.doesNotMatch(html, /data-design-preset/);
});

test('given EXAM preset when rendering hero then center layout with prominent stats and two buttons', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = heroFixture('EXAM');
  // when
  const html = hooks.renderHero(academy);
  // then
  assert.match(html, /data-design-preset="EXAM"/);
  assert.match(html, /hero-exam-inner/);
  assert.match(html, /hero-exam-stats/);
  assert.match(html, /hero-exam-stat/);
  assert.match(html, /hero-exam-actions/);
  assert.match(html, /상담 신청하기/);
  assert.match(html, /커리큘럼 보기/);
  // 원장 사진은 각진 프레임 (태블릿 아님)
  assert.match(html, /principal-frame-angular/);
  assert.doesNotMatch(html, /class="tablet"/);
});

test('given ENGLISH preset when rendering hero then two-column layout with rounded button', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = heroFixture('ENGLISH');
  // when
  const html = hooks.renderHero(academy);
  // then — 2열 구조
  assert.match(html, /data-design-preset="ENGLISH"/);
  assert.match(html, /hero-english-grid/);
  assert.match(html, /hero-english-text/);
  assert.match(html, /hero-english-visual/);
  assert.match(html, /btn-rounded/);
  assert.match(html, /무료 레벨테스트 신청/);
  // 밝은 원형 프레임
  assert.match(html, /principal-frame-circle-bright/);
});

test('given ARTS preset when rendering hero then fullscreen bg with overlay and gallery strip', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = heroFixture('ARTS');
  // when
  const html = hooks.renderHero(academy);
  // then
  assert.match(html, /data-design-preset="ARTS"/);
  assert.match(html, /hero-arts-bg/);
  assert.match(html, /hero-arts-overlay/);
  assert.match(html, /hero-arts-gallery/);
  assert.match(html, /작품 보기/);
});

test('given STUDY preset when rendering hero then circular photo with handwritten caption', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = heroFixture('STUDY');
  // when
  const html = hooks.renderHero(academy);
  // then
  assert.match(html, /data-design-preset="STUDY"/);
  assert.match(html, /hero-study-wrap/);
  assert.match(html, /hero-study-photo/);
  assert.match(html, /hero-study-text/);
  assert.match(html, /principal-frame-circle-warm/);
  assert.match(html, /handwritten/);
  assert.match(html, /원장과 상담하기/);
});

test('given ONLINE preset when rendering hero then gradient with three lecture cards', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  const academy = heroFixture('ONLINE', {
    lessons: [
      { title: '강의 1' }, { title: '강의 2' }, { title: '강의 3' }, { title: '강의 4' }
    ]
  });
  // when
  const html = hooks.renderHero(academy);
  // then — 강의 카드 3개
  assert.match(html, /data-design-preset="ONLINE"/);
  assert.match(html, /hero-online-gradient/);
  assert.match(html, /hero-online-cards/);
  const cardCount = (html.match(/hero-online-card"/g) || []).length;
  assert.equal(cardCount, 3, '강의 카드는 3개만');
  assert.match(html, /소개 영상 보기/);
  assert.match(html, /수강 신청/);
});

test('given five presets when rendering heroes then each DOM structure is distinct', () => {
  // given
  const hooks = bothRenderers().find(([name]) => name === 'public')[1];
  // when
  const htmls = ['EXAM', 'ENGLISH', 'ARTS', 'STUDY', 'ONLINE'].map((id) =>
    hooks.renderHero(heroFixture(id)));
  // then — 5개 모두 서로 다름
  const unique = new Set(htmls);
  assert.equal(unique.size, 5, '5종 프리셋 히어로 DOM이 모두 달라야 한다');
  // 마커 교차 검증: 각 프리셋 고유 클래스가 다른 프리셋에 없음
  const markers = {
    EXAM: 'hero-exam-inner', ENGLISH: 'hero-english-grid', ARTS: 'hero-arts-gallery',
    STUDY: 'hero-study-wrap', ONLINE: 'hero-online-cards'
  };
  for (const [id, marker] of Object.entries(markers)) {
    const html = htmls[['EXAM', 'ENGLISH', 'ARTS', 'STUDY', 'ONLINE'].indexOf(id)];
    assert.match(html, new RegExp(marker), `${id} 고유 마커`);
    for (const [otherId, otherMarker] of Object.entries(markers)) {
      if (otherId === id) continue;
      assert.doesNotMatch(html, new RegExp(otherMarker), `${id}에 ${otherId} 마커가 없어야 함`);
    }
  }
});

test('given design presets when checking section emphasis then each preset has distinct config', () => {
  // given / when
  const sections = {};
  for (const id of ['EXAM', 'ENGLISH', 'ARTS', 'STUDY', 'ONLINE']) {
    sections[id] = brand.DESIGN_PRESETS[id].sections;
  }
  // then — 프리셋별 섹션 강조가 다름
  assert.deepEqual(sections.EXAM, { 'sec-featured': true });
  assert.deepEqual(sections.ENGLISH, { 'sec-lessons': true });
  assert.deepEqual(sections.ARTS, { 'sec-videos': true, 'sec-minilectures': true });
  assert.deepEqual(sections.STUDY, { 'sec-videos': false, 'sec-minilectures': false, 'sec-digitaltwin': false });
  assert.deepEqual(sections.ONLINE, { 'sec-digitaltwin': true, 'sec-minilectures': true });
});

// ═══════════════ sec-cta 순서 정합성 ═══════════════

test('given sec-cta when checking section order then it sits before sec-social in all four definitions', () => {
  // given
  const serverJava = fs.readFileSync(
    path.resolve(__dirname, '../../main/java/f2r/tutor_solution/private_tutor_solution_back/domain/academy/service/AcademyService.java'), 'utf8');
  const publicJs = fs.readFileSync(PUBLIC_JS, 'utf8');
  const builderJs = fs.readFileSync(BUILDER_JS, 'utf8');
  const sectionIdsFrom = (source, declaration) => {
    const start = source.indexOf(declaration);
    assert.notEqual(start, -1);
    const body = source.slice(start, source.indexOf(';', start) + 1);
    return [...body.matchAll(/['"](sec-[a-z0-9-]+)['"]/g)].map((m) => m[1]);
  };
  const server = sectionIdsFrom(serverJava, 'static final List<String> DEFAULT_SECTION_ORDER');
  const pub = sectionIdsFrom(publicJs, 'const DEFAULT_SECTION_ORDER');
  const bld = sectionIdsFrom(builderJs, 'const DEFAULT_SECTION_ORDER');
  const managed = sectionIdsFrom(builderJs, 'const MANAGED_SECTION_IDS');
  // when / then — 4곳 동일 + sec-cta가 sec-social 바로 앞
  assert.deepEqual(pub, server);
  assert.deepEqual(bld, server);
  assert.deepEqual(managed, server.filter((id) => id !== 'sec-hero'));
  for (const [name, order] of [['server', server], ['public', pub], ['builder', bld]]) {
    assert.equal(order[order.indexOf('sec-cta') + 1], 'sec-social', `${name}: sec-cta는 social 바로 앞`);
  }
});
