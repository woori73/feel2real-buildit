(function (global) {
  'use strict';

  var FONT_REGISTRY = Object.freeze({
    PRETENDARD: { label: 'Pretendard', family: "'Pretendard', 'Noto Sans KR', sans-serif", href: 'https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard-dynamic-subset.css' },
    SUIT: { label: 'SUIT', family: "'SUIT', 'Noto Sans KR', sans-serif", href: 'https://cdn.jsdelivr.net/gh/sunn-us/SUIT@v2.0.0/fonts/static/woff2/SUIT.css' },
    NOTO_SANS_KR: { label: 'Noto Sans KR', family: "'Noto Sans KR', sans-serif", href: 'https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700;800&display=swap' },
    NOTO_SERIF_KR: { label: 'Noto Serif KR', family: "'Noto Serif KR', serif", href: 'https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@500;600;700;800&display=swap' }
  });

  // ── 디자인 프리셋 (P0-1) ────────────────────────────────────────────────
  // 색상 팔레트 + 기존 타이포 프리셋 참조 + 히어로/카드 레이아웃.
  // 적용은 fill-in: draft에 이미 값이 있으면 덮어쓰지 않는다. 단, 이전 디자인 프리셋이
  // 채워 넣은 값(현재 값이 이전 프리셋 값과 동일)은 새 프리셋 값으로 교체한다 — 그래야
  // 프리셋 간 전환이 동작하고, 사용자가 직접 고른 값만 유지된다.
  var BRAND_COLOR_KEYS = ['primary', 'secondary', 'accent', 'bg', 'bgAlt', 'text', 'textLight', 'border', 'heroBg', 'heroText'];
  var COLOR_DRAFT_KEYS = {
    primary: 'colorPrimary', secondary: 'colorSecondary', accent: 'colorAccent',
    bg: 'colorBg', bgAlt: 'colorBgAlt', text: 'colorText',
    textLight: 'colorTextLight', border: 'colorBorder',
    heroBg: 'colorHeroBg', heroText: 'colorHeroText'
  };
  var DESIGN_PRESETS = Object.freeze({
    EXAM: {
      label: '입시학원형', description: '신뢰와 실적을 강조하는 입시 전문 학원',
      typography: 'ACADEMIC', heroLayout: 'center', cardStyle: 'bordered',
      colors: { primary: '#1a2b4a', secondary: '#c9a86a', accent: '#d4a017', bg: '#ffffff', bgAlt: '#f6f7f9', text: '#141c2e', textLight: '#5b6577', border: '#e2e6ee', heroBg: '#1a2b4a', heroText: '#ffffff' },
      // 실적(featured)·커리큘럼(lessons) 중심
      sections: { 'sec-featured': true }
    },
    ENGLISH: {
      label: '영어회화형', description: '밝고 친근한 회화·어학 학원',
      typography: 'FRIENDLY', heroLayout: 'split', cardStyle: 'rounded',
      colors: { primary: '#4a90d9', secondary: '#f5a623', accent: '#f5a623', bg: '#ffffff', bgAlt: '#f2f8ff', text: '#22303f', textLight: '#5f7285', border: '#dce9f7', heroBg: '#2f6fb8', heroText: '#ffffff' },
      // 과정(lessons)·후기 중심
      sections: { 'sec-lessons': true }
    },
    ARTS: {
      label: '예체능형', description: '다크 갤러리 무드의 예체능 학원',
      typography: 'MODERN', heroLayout: 'visual', cardStyle: 'media',
      // 차콜(#2d2d2d 계열)은 bgAlt·border·heroBg로, 인터랙티브 주색은 바이올렛으로 둔다.
      colors: { primary: '#8b5cf6', secondary: '#a78bfa', accent: '#c4b5fd', bg: '#16161a', bgAlt: '#232329', text: '#f4f4f6', textLight: '#a9a9b6', border: '#34343e', heroBg: '#101014', heroText: '#ffffff' },
      // 갤러리(videos)·미니특강(minilectures) 중심
      sections: { 'sec-videos': true, 'sec-minilectures': true }
    },
    STUDY: {
      label: '공부방형', description: '따뜻하고 소규모인 공부방·교습소',
      typography: 'CLEAN', heroLayout: 'visual', cardStyle: 'bordered',
      colors: { primary: '#8b6f47', secondary: '#c2703d', accent: '#c2703d', bg: '#fffdf8', bgAlt: '#f5f0e8', text: '#3d3225', textLight: '#7a6c58', border: '#e8ddc9', heroBg: '#6f5836', heroText: '#ffffff' },
      // 원장·소개 중심: 영상·특강·디지털트윈은 소규모 공부방과 거리가 멀어 기본 OFF 제안
      sections: { 'sec-videos': false, 'sec-minilectures': false, 'sec-digitaltwin': false }
    },
    ONLINE: {
      label: '온라인학원형', description: '모던하고 테크 무드의 온라인 학원',
      typography: 'MODERN', heroLayout: 'gradient', cardStyle: 'minimal',
      colors: { primary: '#0f172a', secondary: '#06b6d4', accent: '#22d3ee', bg: '#ffffff', bgAlt: '#f1f5f9', text: '#0f172a', textLight: '#64748b', border: '#e2e8f0', heroBg: '#0f172a', heroText: '#ffffff' },
      // 강의(digitaltwin)·미니특강 중심
      sections: { 'sec-digitaltwin': true, 'sec-minilectures': true }
    }
  });

  var PRESETS = Object.freeze({
    MODERN: { label:'모던', description:'젊고 현대적인 학원', headingFont:'PRETENDARD', bodyFont:'PRETENDARD', headingWeight:800, bodyWeight:500, headingLineHeight:1.22, bodyLineHeight:1.7, headingLetterSpacing:'-.035em', bodyLetterSpacing:'-.012em' },
    PREMIUM: { label:'프리미엄', description:'고급스럽고 신뢰감 있는 전문 학원', headingFont:'NOTO_SERIF_KR', bodyFont:'PRETENDARD', headingWeight:700, bodyWeight:500, headingLineHeight:1.32, bodyLineHeight:1.72, headingLetterSpacing:'-.035em', bodyLetterSpacing:'-.012em' },
    CLEAN: { label:'클린', description:'정돈된 전문 과목 학원', headingFont:'SUIT', bodyFont:'SUIT', headingWeight:800, bodyWeight:500, headingLineHeight:1.25, bodyLineHeight:1.68, headingLetterSpacing:'-.04em', bodyLetterSpacing:'-.015em' },
    FRIENDLY: { label:'프렌들리', description:'초등·저학년에게 편안한 학원', headingFont:'SUIT', bodyFont:'PRETENDARD', headingWeight:700, bodyWeight:500, headingLineHeight:1.34, bodyLineHeight:1.75, headingLetterSpacing:'-.025em', bodyLetterSpacing:'-.008em' },
    ACADEMIC: { label:'아카데믹', description:'학구적이고 진지한 입시 전문 학원', headingFont:'NOTO_SERIF_KR', bodyFont:'NOTO_SANS_KR', headingWeight:700, bodyWeight:500, headingLineHeight:1.34, bodyLineHeight:1.72, headingLetterSpacing:'-.03em', bodyLetterSpacing:'-.01em' }
  });

  function normalizedText(value) { return String(value == null ? '' : value).trim().toLowerCase(); }
  function inferPreset(value) {
    var text = normalizedText(value);
    if (!text) return 'MODERN';
    var scores = { MODERN:0, PREMIUM:0, CLEAN:0, FRIENDLY:0, ACADEMIC:0 };
    function add(key, words, score) { words.forEach(function (word) { if (text.indexOf(word) >= 0) scores[key] += score; }); }
    add('PREMIUM', ['고급','압구정','명문','경력','품격','신뢰'], 3);
    add('ACADEMIC', ['입시','학구','진지','논술','고등','전문'], 3);
    add('CLEAN', ['깔끔','미니멀','정돈','대치동','수학','과학'], 3);
    add('FRIENDLY', ['초등','아이','친근','편안','부드럽','딱딱'], 3);
    add('MODERN', ['젊','세련','현대','최신','ai','스마트'], 3);
    if (text.indexOf('제목만') >= 0 && (text.indexOf('고급') >= 0 || text.indexOf('명조') >= 0)) scores.PREMIUM += 4;
    var order = ['PREMIUM','ACADEMIC','CLEAN','FRIENDLY','MODERN'];
    return order.reduce(function (best, key) { return scores[key] > scores[best] ? key : best; }, 'MODERN');
  }

  function clamp(value, min, max, fallback) {
    var n = Number(value);
    return Number.isFinite(n) ? Math.max(min, Math.min(max, Math.round(n))) : fallback;
  }
  function draftOf(academyOrDraft) {
    if (!academyOrDraft) return {};
    if (academyOrDraft.pageSettings) return academyOrDraft.pageSettings.builderDraft || {};
    return academyOrDraft;
  }
  function resolve(academyOrDraft) {
    var draft = draftOf(academyOrDraft);
    var presetId = PRESETS[draft.typographyPreset] ? draft.typographyPreset : 'MODERN';
    var preset = PRESETS[presetId];
    var headingFont = FONT_REGISTRY[draft.headingFont] ? draft.headingFont : preset.headingFont;
    var bodyFont = FONT_REGISTRY[draft.bodyFont] ? draft.bodyFont : preset.bodyFont;
    return {
      presetId:presetId, preset:preset, headingFont:headingFont, bodyFont:bodyFont,
      logoMobile:clamp(draft.logoSizeMobile, 44, 104, 68),
      logoDesktop:clamp(draft.logoSizeDesktop, 52, 160, 88),
      headingMobile:clamp(draft.headingSizeMobile, 24, 44, 34),
      bodyMobile:clamp(draft.bodySizeMobile, 15, 20, 16)
    };
  }
  function ensureFonts(doc, ids) {
    if (!doc || !doc.head) return;
    var wanted = {};
    ids.forEach(function (id) { wanted[id] = true; });
    Array.prototype.slice.call(doc.querySelectorAll('link[data-academy-brand-font]')).forEach(function (link) {
      if (!wanted[link.getAttribute('data-academy-brand-font')]) link.remove();
    });
    ids.forEach(function (id) {
      if (!FONT_REGISTRY[id] || doc.querySelector('link[data-academy-brand-font="'+id+'"]')) return;
      var link = doc.createElement('link');
      link.rel = 'stylesheet'; link.href = FONT_REGISTRY[id].href;
      link.setAttribute('data-academy-brand-font', id);
      doc.head.appendChild(link);
    });
  }
  function designOf(academyOrDraft) {
    var draft = draftOf(academyOrDraft);
    var id = DESIGN_PRESETS[draft.designPreset] ? draft.designPreset : null;
    return { id: id, preset: id ? DESIGN_PRESETS[id] : null, draft: draft };
  }

  /**
   * 디자인 프리셋을 루트에 적용한다. data-design-preset / data-hero-layout /
   * data-card-style 속성 + --brand-* CSS 변수(10종).
   * 색상은 draft 커스텀값 > 프리셋값 순이고, 둘 다 없으면 인라인 변수를 지워
   * 스타일시트 기본값(기존 색상과 1:1)이 그대로 적용되게 한다 — 시각 회귀 0.
   */
  function applyDesign(root, academyOrDraft) {
    var design = designOf(academyOrDraft), draft = design.draft, preset = design.preset;
    if (design.id) root.dataset.designPreset = design.id;
    else delete root.dataset.designPreset;
    var heroLayout = draft.heroLayout || (preset && preset.heroLayout);
    if (heroLayout) root.dataset.heroLayout = heroLayout;
    else delete root.dataset.heroLayout;
    var cardStyle = draft.cardStyle || (preset && preset.cardStyle);
    if (cardStyle) root.dataset.cardStyle = cardStyle;
    else delete root.dataset.cardStyle;
    BRAND_COLOR_KEYS.forEach(function (key) {
      var custom = draft[COLOR_DRAFT_KEYS[key]];
      var value = (custom == null || custom === '') ? (preset && preset.colors[key]) : custom;
      if (value) root.style.setProperty('--brand-' + key, value);
      else root.style.removeProperty('--brand-' + key);
    });
    return design;
  }

  /**
   * 디자인 프리셋 적용용 draft를 만든다. fill-in: 이미 값이 있는 키는 덮어쓰지 않는다.
   * 단 이전 디자인 프리셋이 채워 넣은 값(현재 값이 이전 프리셋 값과 동일)은 새 프리셋 값으로
   * 교체한다 — 그래야 프리셋 간 전환이 동작하고 사용자가 직접 고른 값만 유지된다.
   * 반환: { draft, sectionChanges } — sectionChanges는 visibility에 키가 없을 때만
   * 채울 권장 표시 상태라 호출자가 확인 다이얼로그에 그대로 쓴다.
   */
  function designPresetDraft(id, current, visibility) {
    var presetId = DESIGN_PRESETS[id] ? id : 'EXAM';
    var preset = DESIGN_PRESETS[presetId];
    var base = Object.assign({}, current || {});
    var prev = base.designPreset && DESIGN_PRESETS[base.designPreset];
    base.designPreset = presetId;
    // 타이포/레이아웃: 비어 있으면 채우고, 이전 프리셋이 채운 값이면 새 값으로 교체.
    // 사용자가 직접 고른 값(이전 프리셋 값과 다름)은 유지한다.
    [['typographyPreset', 'typography'], ['heroLayout', 'heroLayout'], ['cardStyle', 'cardStyle']].forEach(function (pair) {
      var draftKey = pair[0], presetKey = pair[1];
      var cur = base[draftKey];
      if (cur == null || cur === '') base[draftKey] = preset[presetKey];
      else if (prev && cur === prev[presetKey]) base[draftKey] = preset[presetKey];
    });
    BRAND_COLOR_KEYS.forEach(function (key) {
      var draftKey = COLOR_DRAFT_KEYS[key];
      var cur = base[draftKey];
      if (prev && cur === prev.colors[key]) delete base[draftKey];
      if (base[draftKey] == null || base[draftKey] === '') base[draftKey] = preset.colors[key];
    });
    var sectionChanges = [];
    var sections = preset.sections || {};
    Object.keys(sections).forEach(function (sectionId) {
      if (!visibility || visibility[sectionId] == null) {
        sectionChanges.push({ sectionId: sectionId, visible: !!sections[sectionId] });
      }
    });
    return { draft: base, sectionChanges: sectionChanges };
  }

  function apply(root, academyOrDraft) {
    if (!root || !root.style) return resolve(academyOrDraft);
    var value = resolve(academyOrDraft), heading = FONT_REGISTRY[value.headingFont], body = FONT_REGISTRY[value.bodyFont];
    root.dataset.typographyPreset = value.presetId;
    root.style.setProperty('--academy-heading-font', heading.family);
    root.style.setProperty('--academy-body-font', body.family);
    root.style.setProperty('--academy-heading-weight', value.preset.headingWeight);
    root.style.setProperty('--academy-body-weight', value.preset.bodyWeight);
    root.style.setProperty('--academy-heading-line-height', value.preset.headingLineHeight);
    root.style.setProperty('--academy-body-line-height', value.preset.bodyLineHeight);
    root.style.setProperty('--academy-heading-letter-spacing', value.preset.headingLetterSpacing);
    root.style.setProperty('--academy-body-letter-spacing', value.preset.bodyLetterSpacing);
    root.style.setProperty('--academy-logo-mobile', value.logoMobile + 'px');
    root.style.setProperty('--academy-logo-desktop', value.logoDesktop + 'px');
    root.style.setProperty('--academy-heading-mobile', value.headingMobile + 'px');
    root.style.setProperty('--academy-body-mobile', value.bodyMobile + 'px');
    ensureFonts(root.ownerDocument || global.document, [value.headingFont, value.bodyFont].filter(function (id, i, arr) { return arr.indexOf(id) === i; }));
    applyDesign(root, academyOrDraft);
    return value;
  }
  function presetDraft(id, current) {
    var presetId = PRESETS[id] ? id : 'MODERN';
    var base = Object.assign({}, current || {});
    base.typographyPreset = presetId;
    base.headingFont = PRESETS[presetId].headingFont;
    base.bodyFont = PRESETS[presetId].bodyFont;
    return base;
  }
  function recommendation(value) {
    var id = inferPreset(value), preset = PRESETS[id];
    return { presetId:id, label:preset.label, description:preset.description, message:preset.label+' 스타일을 제안합니다. 제목과 본문을 모바일 가독성에 맞춰 조합합니다.' };
  }

  var api = Object.freeze({ FONT_REGISTRY:FONT_REGISTRY, PRESETS:PRESETS, DESIGN_PRESETS:DESIGN_PRESETS, BRAND_COLOR_KEYS:BRAND_COLOR_KEYS, COLOR_DRAFT_KEYS:COLOR_DRAFT_KEYS, inferPreset:inferPreset, recommendation:recommendation, resolve:resolve, apply:apply, presetDraft:presetDraft, designOf:designOf, designPresetDraft:designPresetDraft, clamp:clamp });
  global.F2RAcademyBrand = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : globalThis);
