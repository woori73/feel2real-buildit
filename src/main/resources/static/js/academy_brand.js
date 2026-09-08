(function (global) {
  'use strict';

  var FONT_REGISTRY = Object.freeze({
    PRETENDARD: { label: 'Pretendard', family: "'Pretendard', 'Noto Sans KR', sans-serif", href: 'https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard-dynamic-subset.css' },
    SUIT: { label: 'SUIT', family: "'SUIT', 'Noto Sans KR', sans-serif", href: 'https://cdn.jsdelivr.net/gh/sunn-us/SUIT@v2.0.0/fonts/static/woff2/SUIT.css' },
    NOTO_SANS_KR: { label: 'Noto Sans KR', family: "'Noto Sans KR', sans-serif", href: 'https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700;800&display=swap' },
    NOTO_SERIF_KR: { label: 'Noto Serif KR', family: "'Noto Serif KR', serif", href: 'https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@500;600;700;800&display=swap' }
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

  var api = Object.freeze({ FONT_REGISTRY:FONT_REGISTRY, PRESETS:PRESETS, inferPreset:inferPreset, recommendation:recommendation, resolve:resolve, apply:apply, presetDraft:presetDraft, clamp:clamp });
  global.F2RAcademyBrand = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : globalThis);
