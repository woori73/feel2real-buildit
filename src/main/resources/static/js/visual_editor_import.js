/*
 * BuildIt 시각 편집기 — 기존 홈페이지(P0) 불러오기.
 *
 * P0 공개 페이지(/academies/{slug})는 JS가 DOM을 만들고 애니메이션·스크롤 효과·전역 CSS로 꾸미는
 * 구조라 HTML만 복사해서는 모양이 사라진다. 그래서 "보이는 결과"를 스냅샷한다:
 *   1) 숨은 iframe에서 P0를 PC 폭(1440)과 모바일 폭(390)으로 렌더한다 (?ve-source=p0 로 P0 강제).
 *   2) 요소마다 계산된 스타일을 읽어, 기본값(상속/UA 기본)과 다른 것만 #id 규칙으로 만든다.
 *   3) PC 값과 모바일 값이 다른 요소는 @media (max-width: 768px) 규칙을 추가한다.
 *      (GrapesJS Mobile 디바이스의 widthMedia와 같은 768px 기준)
 *
 * 제한(사용자에게 안내): 애니메이션·스크롤 효과·영상·SVG·폼 컨트롤·아이콘 폰트는 가져오지 못한다.
 * 가져온 결과는 일반 편집 가능한 HTML/CSS이며 편집기에서 자유롭게 수정할 수 있다.
 * 가져오기는 편집기 안에서만 반영되고, 사용자가 저장해야 초안이 된다.
 */
(function (global) {
  'use strict';

  const DESKTOP_WIDTH = 1440;
  const MOBILE_WIDTH = 390;
  const MOBILE_MEDIA = '(max-width: 768px)';

  // 서버 정제기/Jsoup 허용 태그. 그 밖의 태그는 아래 TAG_MAP/레이아웃 기준으로 바꾼다.
  const ALLOWED_TAGS = new Set(('a b blockquote br caption cite code col colgroup dd div dl dt em '
    + 'h1 h2 h3 h4 h5 h6 i img li ol p pre q small span strike strong sub sup table tbody td tfoot th '
    + 'thead tr u ul section header footer nav main article aside figure figcaption time mark hr').split(' '));
  const SKIP_TAGS = new Set(['script', 'style', 'noscript', 'template', 'link', 'meta', 'canvas', 'svg',
    'video', 'audio', 'iframe', 'object', 'embed', 'source', 'track', 'input', 'select', 'textarea',
    'form', 'option', 'optgroup', 'datalist', 'dialog', 'map', 'area']);
  const TAG_MAP = { button: 'a', label: 'span', summary: 'div', details: 'div', picture: 'div' };

  const INHERITED = ['color', 'font-family', 'font-size', 'font-weight', 'font-style', 'line-height',
    'letter-spacing', 'text-align', 'text-transform', 'white-space', 'word-break', 'list-style-type'];
  const PLAIN = ['display', 'flex-direction', 'flex-wrap', 'flex-grow', 'flex-shrink', 'flex-basis',
    'justify-content', 'align-items', 'align-self', 'align-content', 'row-gap', 'column-gap',
    'grid-template-columns', 'grid-template-rows', 'order', 'background-color', 'background-image',
    'background-size', 'background-position', 'background-repeat', 'box-shadow', 'overflow-x',
    'overflow-y', 'max-width', 'min-height', 'object-fit', 'aspect-ratio', 'text-decoration-line',
    'text-decoration-color', 'position'];
  const POSITIONED = ['top', 'right', 'bottom', 'left'];
  const SIDES = ['top', 'right', 'bottom', 'left'];

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  async function waitFor(fn, timeoutMs, label) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      try { const v = fn(); if (v) return v; } catch (_) { /* iframe 로딩 중 */ }
      await sleep(150);
    }
    throw new Error(label + ' 시간 초과');
  }

  async function openFrame(url, width) {
    const iframe = document.createElement('iframe');
    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.cssText = 'position:fixed;left:-100000px;top:0;border:0;height:900px;width:' + width + 'px;';
    iframe.src = url;
    document.body.appendChild(iframe);
    await new Promise((resolve, reject) => {
      iframe.onload = resolve;
      iframe.onerror = () => reject(new Error('홈페이지를 불러오지 못했습니다.'));
      setTimeout(() => reject(new Error('홈페이지 로딩 시간 초과')), 30000);
    });
    const win = iframe.contentWindow;
    const doc = iframe.contentDocument;
    await waitFor(() => doc.querySelector('#publicView nav') || (doc.getElementById('errorMsg') && doc.getElementById('errorMsg').textContent.trim()), 20000, '홈페이지 렌더');
    // 성공 여부는 "실제로 렌더된 콘텐츠"로 판단한다. P0 페이지의 부가 스크립트(애니메이션 등)가
    // 렌더 뒤에 예외를 내 #errorMsg를 채우더라도 화면이 그려졌다면 스냅샷은 가능하다.
    const rendered = doc.querySelector('#publicView nav') && doc.querySelector('#publicView').children.length > 2;
    const err = doc.getElementById('errorMsg');
    if (!rendered) throw new Error((err && err.textContent.trim()) || '홈페이지를 불러오지 못했습니다.');
    await sleep(500);
    // 스크롤 기반 reveal 효과가 켜지도록 끝까지 내려갔다 올라온다.
    const total = Math.max(doc.documentElement.scrollHeight, 1);
    for (let y = 0; y < total; y += 500) { win.scrollTo(0, y); await sleep(70); }
    win.scrollTo(0, 0);
    await sleep(500);
    return iframe;
  }

  /** 스타일 기준용 빈 문서(페이지 CSS 없음)에서 태그별 기본 계산값을 얻는다. */
  async function makeDefaults() {
    const iframe = document.createElement('iframe');
    iframe.style.cssText = 'position:fixed;left:-100000px;top:0;width:' + DESKTOP_WIDTH + 'px;height:200px;border:0;';
    iframe.srcdoc = '<!DOCTYPE html><html><body></body></html>';
    document.body.appendChild(iframe);
    await new Promise((r) => { iframe.onload = r; setTimeout(r, 3000); });
    const doc = iframe.contentDocument;
    const cache = {};
    return {
      get(tag) {
        if (!cache[tag]) {
          const el = doc.createElement(tag);
          if (tag === 'a') el.setAttribute('href', '#');   // href가 있어야 UA 링크 스타일(파랑·밑줄)이 적용된다
          doc.body.appendChild(el);
          cache[tag] = snapshotComputed(iframe.contentWindow.getComputedStyle(el));
          doc.body.removeChild(el);
        }
        return cache[tag];
      },
      dispose() { iframe.remove(); }
    };
  }

  function snapshotComputed(cs) {
    const o = {};
    INHERITED.concat(PLAIN, POSITIONED).forEach((p) => { o[p] = cs.getPropertyValue(p); });
    SIDES.forEach((s) => {
      o['margin-' + s] = cs.getPropertyValue('margin-' + s);
      o['padding-' + s] = cs.getPropertyValue('padding-' + s);
      o['border-' + s + '-width'] = cs.getPropertyValue('border-' + s + '-width');
      o['border-' + s + '-style'] = cs.getPropertyValue('border-' + s + '-style');
      o['border-' + s + '-color'] = cs.getPropertyValue('border-' + s + '-color');
    });
    ['top-left', 'top-right', 'bottom-right', 'bottom-left'].forEach((c) => {
      o['border-' + c + '-radius'] = cs.getPropertyValue('border-' + c + '-radius');
    });
    return o;
  }

  function stripOrigin(value) {
    return String(value).split(global.location.origin).join('');
  }

  /** 편집기 기본(.ve-root/body) 상속값. 최상위 요소는 이 값과 비교한다. */
  function editorBaseline() {
    return {
      color: 'rgb(17, 24, 39)', 'font-size': '16px', 'font-weight': '400', 'font-style': 'normal',
      'line-height': '27.2px', 'letter-spacing': 'normal', 'text-align': 'start',
      'text-transform': 'none', 'white-space': 'normal', 'word-break': 'keep-all',
      'list-style-type': 'disc', 'font-family': null /* 글꼴은 최상위에서 항상 base 스택과 비교 불가 → 아래 */
    };
  }

  const BASE_FONT_HINT = /Pretendard Variable|Pretendard/i;

  const FLEXISH = /flex|grid/;

  /**
   * 요소의 "기본값과 다른" 스타일만 골라낸다.
   *  - 상속 속성: 부모(최상위는 편집기 기본)와 다르거나, 그 태그의 UA 기본이 일반 div와 달라
   *    (a의 파란 글씨·밑줄, h1의 굵기·크기 등) 상속만으로는 같은 모양이 안 나오는 경우
   *  - 일반 속성: 그 태그의 UA 기본값과 다를 때
   *  - 레이아웃 속성은 해당 레이아웃일 때만(flex 속성은 flex 컨테이너/아이템일 때 등)
   * 결과 값은 GrapesJS 규칙 API(setIdRule)에 그대로 넣는다. CSS 문자열로 setStyle하면 브라우저 파서가
   * margin/border 같은 축약형을 개별 속성으로 펼쳐(border 하나가 17개) 저장 한도(64KB)를 빨리 넘기기 때문이다.
   */
  function declsFor(cur, parent, defaults, divDefaults, isTop, extra) {
    const d = {};
    const base = isTop ? editorBaseline() : parent;
    INHERITED.forEach((p) => {
      const v = cur[p];
      const uaSpecial = defaults[p] !== divDefaults[p];
      if (p === 'font-family') {
        if (isTop ? !BASE_FONT_HINT.test(v) : v !== parent[p]) d[p] = v;
        return;
      }
      if (v !== base[p] || uaSpecial) d[p] = v;
    });

    const display = cur.display;
    const parentDisplay = parent ? parent.display : '';
    const isFlexContainer = FLEXISH.test(display);
    PLAIN.forEach((p) => {
      let v = cur[p];
      if (p === 'background-image') v = stripOrigin(v);
      if (v === defaults[p]) return;
      // 레이아웃 종속 속성은 그 레이아웃일 때만 의미가 있다.
      if (['flex-direction', 'flex-wrap', 'justify-content', 'align-items', 'align-content',
        'row-gap', 'column-gap'].includes(p) && !isFlexContainer) return;
      if (['grid-template-columns', 'grid-template-rows'].includes(p) && !/grid/.test(display)) return;
      if (['flex-grow', 'flex-shrink', 'flex-basis', 'align-self', 'order'].includes(p) && !FLEXISH.test(parentDisplay)) return;
      if (['background-size', 'background-position', 'background-repeat'].includes(p) && cur['background-image'] === 'none') return;
      if (p === 'text-decoration-color') return;   // 아래에서 line이 있을 때만 별도로 처리
      d[p] = v;
    });
    if (cur['text-decoration-line'] !== 'none' && cur['text-decoration-color'] !== cur.color) {
      d['text-decoration-color'] = cur['text-decoration-color'];
    }
    // 같은 값의 개별 속성은 축약형으로 합쳐 용량을 줄인다.
    if (d['row-gap'] !== undefined && d['row-gap'] === d['column-gap']) { d.gap = d['row-gap']; delete d['row-gap']; delete d['column-gap']; }
    if (d['overflow-x'] !== undefined && d['overflow-x'] === d['overflow-y']) { d.overflow = d['overflow-x']; delete d['overflow-x']; delete d['overflow-y']; }
    if (cur.position && cur.position !== 'static') POSITIONED.forEach((p) => { d[p] = cur[p]; });

    ['margin', 'padding'].forEach((k) => {
      const vals = SIDES.map((sd) => cur[k + '-' + sd]);
      const defs = SIDES.map((sd) => defaults[k + '-' + sd]);
      if (vals.every((v, i) => v === defs[i])) return;
      d[k] = vals.every((v) => v === vals[0]) ? vals[0] : vals.join(' ');
    });
    const bw = SIDES.map((sd) => cur['border-' + sd + '-width']);
    const bs = SIDES.map((sd) => cur['border-' + sd + '-style']);
    const bc = SIDES.map((sd) => cur['border-' + sd + '-color']);
    if (bs.some((v) => v !== 'none') && bw.some((v) => v !== '0px')) {
      if (bw.every((v) => v === bw[0]) && bs.every((v) => v === bs[0]) && bc.every((v) => v === bc[0])) {
        d.border = bw[0] + ' ' + bs[0] + ' ' + bc[0];
      } else {
        SIDES.forEach((sd, i) => { if (bs[i] !== 'none' && bw[i] !== '0px') d['border-' + sd] = bw[i] + ' ' + bs[i] + ' ' + bc[i]; });
      }
    }
    const rad = ['top-left', 'top-right', 'bottom-right', 'bottom-left'].map((c) => cur['border-' + c + '-radius']);
    if (rad.some((v) => v !== '0px')) d['border-radius'] = rad.every((v) => v === rad[0]) ? rad[0] : rad.join(' ');
    if (extra) Object.assign(d, extra);
    return d;
  }

  const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const escAttr = (s) => esc(s).replace(/"/g, '&quot;');

  function pickTag(el, cs) {
    const tag = el.tagName.toLowerCase();
    if (SKIP_TAGS.has(tag)) return null;
    if (TAG_MAP[tag]) return TAG_MAP[tag];
    if (ALLOWED_TAGS.has(tag)) return tag;
    return /^inline/.test(cs.getPropertyValue('display')) ? 'span' : 'div';
  }

  /** DOM 순서대로 텍스트/자식 요소를 섞어 직렬화하려고 원본 childNodes를 다시 훑는다. */
  function buildTree(el, win, stats) {
    const cs = win.getComputedStyle(el);
    const tag = pickTag(el, cs);
    if (!tag) { const k = el.tagName.toLowerCase(); stats.dropped[k] = (stats.dropped[k] || 0) + 1; return null; }
    if (el.tagName.toLowerCase() === 'button') stats.converted.button = (stats.converted.button || 0) + 1;
    const node = { el, tag, cs: snapshotComputed(cs), rect: el.getBoundingClientRect(), kids: [] };
    el.childNodes.forEach((c) => {
      if (c.nodeType === 1) { const k = buildTree(c, win, stats); if (k) node.kids.push(k); }
      else if (c.nodeType === 3) node.kids.push({ text: c.textContent });
    });
    return node;
  }

  function attrsFor(node, id) {
    const el = node.el;
    const tag = node.tag;
    let a = ' id="' + id + '"';
    if (tag === 'img') {
      const src = el.getAttribute('src') || '';
      if (src) a += ' src="' + escAttr(stripOrigin(src)) + '"';
      a += ' alt="' + escAttr(el.getAttribute('alt') || '') + '"';
    } else if (tag === 'a') {
      let href = el.getAttribute('href') || '#';
      if (/^\s*javascript:/i.test(href)) href = '#';
      a += ' href="' + escAttr(stripOrigin(href)) + '"';
    }
    return a;
  }

  function imageExtra(node, parentNode) {
    // 이미지는 폭/높이가 계산 px로만 남으므로 부모 폭 대비 비율로 바꿔 반응형을 유지한다.
    if (node.tag !== 'img' || !node.rect.width) return null;
    const pw = parentNode && parentNode.rect.width ? parentNode.rect.width : 0;
    if (pw && node.rect.width >= pw * 0.97) return { width: '100%', height: 'auto' };
    return { width: Math.round(node.rect.width) + 'px', height: 'auto' };
  }

  function cssText(map) {
    return Object.keys(map).map((k) => k + ':' + map[k] + ';').join('');
  }

  /**
   * P0 홈페이지를 편집기용 HTML/CSS로 스냅샷한다.
   * @param {{slug:string, onProgress?:(msg:string)=>void}} opts
   * @returns {Promise<{html:string, css:string, rules:Array, mobileRules:Array, mobileMedia:string, stats:object}>}
   */
  async function snapshot(opts) {
    const slug = opts.slug;
    const progress = opts.onProgress || (() => {});
    if (!slug) throw new Error('학원 정보를 찾을 수 없습니다.');
    const url = '/academies/' + encodeURIComponent(slug) + '?ve-source=p0';
    const frames = [];
    let defaults = null;
    try {
      progress('기존 홈페이지(PC)를 읽는 중...');
      const dFrame = await openFrame(url, DESKTOP_WIDTH); frames.push(dFrame);
      progress('기존 홈페이지(모바일)를 읽는 중...');
      const mFrame = await openFrame(url, MOBILE_WIDTH); frames.push(mFrame);
      defaults = await makeDefaults();

      const dRoot = dFrame.contentDocument.getElementById('publicView');
      const mRoot = mFrame.contentDocument.getElementById('publicView');
      const pick = (r) => Array.from(r.children).filter((c) => !['NAV', 'FOOTER', 'SCRIPT', 'STYLE'].includes(c.tagName));
      const dTop = pick(dRoot); const mTop = pick(mRoot);

      const stats = { sections: dTop.length, elements: 0, dropped: {}, converted: {}, mobileMatched: dTop.length === mTop.length };
      const mStats = { dropped: {}, converted: {} };
      const dTrees = dTop.map((el) => buildTree(el, dFrame.contentWindow, stats)).filter(Boolean);
      const mTrees = mTop.map((el) => buildTree(el, mFrame.contentWindow, mStats)).filter(Boolean);

      let n = 0;
      const rules = []; const mobileRules = []; const divDefaults = defaults.get('div');
      function emit(node, mNode, parent, mParent, isTop) {
        const id = 've' + (++n);
        const dd = defaults.get(node.tag);
        const dDecl = declsFor(node.cs, parent ? parent.cs : null, dd, divDefaults, isTop, imageExtra(node, parent));
        if (Object.keys(dDecl).length) rules.push({ id: id, style: dDecl });
        if (mNode && mNode.tag === node.tag) {
          const mDecl = declsFor(mNode.cs, mParent ? mParent.cs : null, dd, divDefaults, isTop, imageExtra(mNode, mParent));
          const diff = {};
          Object.keys(mDecl).forEach((p) => { if (mDecl[p] !== dDecl[p]) diff[p] = mDecl[p]; });
          Object.keys(dDecl).forEach((p) => {
            if (!(p in mDecl)) diff[p] = mNode.cs[p] !== undefined ? mNode.cs[p] : 'initial';
          });
          if (Object.keys(diff).length) mobileRules.push({ id: id, style: diff });
        }
        stats.elements++;
        let inner = '';
        // PC/모바일 DOM의 자식 수가 다르면(반응형으로 DOM이 달라진 경우) 이 하위 트리는
        // 모바일 규칙을 만들지 않는다. 잘못된 짝으로 엉뚱한 요소에 모바일 스타일이 붙는 것을 막는다.
        const dKidCount = node.kids.filter((k) => k.tag !== undefined).length;
        const mKids = mNode && mNode.tag === node.tag ? mNode.kids.filter((k) => k.tag !== undefined) : [];
        const sameShape = mKids.length === dKidCount;
        let ki = 0;
        node.kids.forEach((k) => {
          if (k.tag === undefined) { inner += esc(k.text); return; }
          inner += emit(k, sameShape ? mKids[ki] : null, node, sameShape ? mNode : null, false); ki++;
        });
        if (node.tag === 'img' || node.tag === 'hr' || node.tag === 'br') return '<' + node.tag + attrsFor(node, id) + '>';
        return '<' + node.tag + attrsFor(node, id) + '>' + inner + '</' + node.tag + '>';
      }
      const html = dTrees.map((t, i) => emit(t, mTrees[i], null, null, true)).join('\n');
      // 용량 추정/디버깅용 CSS 문자열(편집기는 아래 rules/mobileRules를 API로 적용한다)
      const ruleText = (r) => '#' + r.id + '{' + cssText(r.style) + '}';
      let css = rules.map(ruleText).join('\n');
      if (mobileRules.length) css += '\n@media ' + MOBILE_MEDIA + '{\n' + mobileRules.map(ruleText).join('\n') + '\n}';
      stats.cssBytes = new TextEncoder().encode(css).length;
      stats.mobileRules = mobileRules.length;
      return { html: html, css: css, rules: rules, mobileRules: mobileRules, mobileMedia: MOBILE_MEDIA, stats: stats };
    } finally {
      frames.forEach((f) => f.remove());
      if (defaults) defaults.dispose();
    }
  }

  global.F2RVisualEditorImport = { snapshot: snapshot, MOBILE_MEDIA: MOBILE_MEDIA };
})(window);
