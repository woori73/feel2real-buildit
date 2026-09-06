const assert = require('node:assert/strict');
const test = require('node:test');
const { loadRenderHooks, academyFixture } = require('./helpers/academy_render_hooks');

/**
 * Phase 5B.1 — Bildi(빌디) Sidekick 배선(실제 academy_builder.js + academy_world.js 실행).
 *
 * 검증:
 *   1) #builderBildiStage + F2RAcademyWorld 있으면 wireAiGuide가 하늘/캐릭터/말풍선을 mount 한다.
 *   2) 켜면 body.bildi-active, 정적 패널은 접힌 채(§3: 캐릭터가 주 UI).
 *   3) 스텝 이동 → Bildi 말풍선 갱신 + step별 하늘 scene 전환(§6).
 *   4) 후보 적용은 여전히 PUT/Publish 없이 builderDraft만(§10 안전장치 유지).
 *   5) F2RAcademyWorld 미로드 → 기존 패널 폴백(회귀 없음).
 */

const flush = () => new Promise((resolve) => setImmediate(resolve));

function fakeEl(tag) {
  const el = {
    tagName: (tag || 'div').toUpperCase(),
    children: [], parentNode: null, hidden: false, innerHTML: '', value: '', disabled: false,
    dataset: {}, _attrs: {}, _listeners: {},
    style: { _p: {}, setProperty(k, v) { this._p[k] = v; } },
    classList: {
      _s: new Set(),
      add(n) { this._s.add(n); }, remove(n) { this._s.delete(n); },
      toggle(n, f) { const v = f === undefined ? !this._s.has(n) : f; if (v) this._s.add(n); else this._s.delete(n); return v; },
      contains(n) { return this._s.has(n); }
    },
    setAttribute(k, v) { this._attrs[k] = String(v); },
    getAttribute(k) { return this._attrs[k] == null ? null : this._attrs[k]; },
    removeAttribute(k) { delete this._attrs[k]; },
    appendChild(c) { c.parentNode = el; el.children.push(c); return c; },
    insertBefore(c) { c.parentNode = el; el.children.unshift(c); return c; },
    removeChild(c) { el.children = el.children.filter((x) => x !== c); return c; },
    addEventListener(t, fn) { (el._listeners[t] = el._listeners[t] || []).push(fn); },
    removeEventListener(t, fn) { el._listeners[t] = (el._listeners[t] || []).filter((f) => f !== fn); },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    getBoundingClientRect() { return el._rect || { left: 0, top: 0, width: 400, height: 300, right: 400, bottom: 300 }; },
    focus() {}, scrollTo() {},
    get firstChild() { return el.children[0] || null; }
  };
  return el;
}

const IDS = [
  'builderAiGuidePanel', 'builderAiGuideToggle', 'builderAiGuideClose', 'builderAiGuideRestart',
  'builderAiGuidePrev', 'builderAiGuideNext', 'builderAiGuideSkip', 'builderAiGuideSteps',
  'builderAiGuideBody', 'builderAiGuideProgressFill', 'builderComposeTrigger', 'pageContent',
  'builderBildiStage'
];

function buildDoc(opts = {}) {
  const elements = new Map(IDS.filter((id) => !(opts.without || []).includes(id)).map((id) => [id, fakeEl('div')]));
  const body = fakeEl('body');
  const doc = {
    body, head: fakeEl('head'), title: '',
    addEventListener() {},
    getElementById(id) { return elements.has(id) ? elements.get(id) : null; },
    querySelector(sel) { return typeof sel === 'string' && sel[0] === '#' ? this.getElementById(sel.slice(1)) : null; },
    querySelectorAll() { return []; },
    createElement(t) { const e = fakeEl(t); e.ownerDocument = doc; return e; },
    createRange() { return { selectNodeContents() {}, collapse() {}, setStart() {} }; },
    elements
  };
  elements.forEach((el) => { el.ownerDocument = doc; });
  body.ownerDocument = doc;
  // pageContent에 섹션 노드 몇 개를 붙여 story sections()가 비어있지 않게 한다.
  const pc = elements.get('pageContent');
  if (pc) {
    pc.querySelectorAll = (sel) => {
      if (String(sel).indexOf('data-builder-section-key') >= 0) {
        return [fakeEl('section'), fakeEl('section')];
      }
      return [];
    };
    pc.scrollHeight = 3000; pc.clientHeight = 1000; pc.scrollTop = 0;
  }
  return doc;
}

function loadBildi(opts = {}) {
  const doc = buildDoc(opts);
  const win = {
    getComputedStyle: () => ({}),
    getSelection: () => ({ removeAllRanges() {}, addRange() {} }),
    matchMedia: () => ({ matches: false }),
    requestAnimationFrame: (fn) => { fn(); return 1; },
    IntersectionObserver: function () { return { observe() {}, disconnect() {}, unobserve() {} }; },
    performance: { now: () => Date.now() }
  };
  if (opts.noWorld) win.F2RAcademyWorld = undefined;
  const fetchCalls = [];
  const hooks = loadRenderHooks('builder', {
    document: doc,
    window: opts.noWorld ? Object.assign({}, win, { F2RAcademyWorld: null }) : win,
    fetch: async (url, init) => {
      fetchCalls.push({ url, init });
      if (opts.fetch) return opts.fetch(url, init);
      return new Promise(() => {});
    }
  });
  return { hooks, doc, fetchCalls };
}

test('wireAiGuide + #builderBildiStage → 하늘/캐릭터/말풍선이 mount 된다', () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);

  const stage = doc.getElementById('builderBildiStage');
  const classes = stage.children.map((c) => (c.className || ''));
  assert.ok(classes.some((c) => c.indexOf('world-sky') >= 0), '하늘 레이어 mount');
  assert.ok(classes.some((c) => c.indexOf('bildi') >= 0 && c.indexOf('bubble') < 0), 'Bildi 캐릭터 mount');
  assert.ok(classes.some((c) => c.indexOf('bildi-bubble') >= 0), '말풍선 mount');
});

test('켜면 body.bildi-active, 정적 패널은 접힌 채(캐릭터가 주 UI)', () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);

  assert.equal(doc.body.classList.contains('bildi-active'), true);
  assert.equal(doc.getElementById('builderAiGuidePanel').classList.contains('is-open'), false,
    'Bildi 모드에서 정적 8-step 패널은 기본으로 열지 않는다');
  // "단계 목록" 요청 시에만 패널을 편다
  hooks.setAiGuideOpen(true, { panel: true });
  assert.equal(doc.getElementById('builderAiGuidePanel').classList.contains('is-open'), true);
});

test('스텝 이동 → Bildi 말풍선 갱신 + step별 하늘 scene 전환', () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);

  const stage = doc.getElementById('builderBildiStage');
  const sky = stage.children.find((c) => (c.className || '').indexOf('world-sky') >= 0);
  const bubble = stage.children.find((c) => (c.className || '').indexOf('bildi-bubble') >= 0);

  hooks.goAiGuideStep('philosophy'); // sceneForStep('philosophy') === 'clouds'
  assert.equal(sky.getAttribute('data-scene'), 'clouds');
  assert.match(bubble.children[0].innerHTML, /교육철학|이 학원이 가르치는/);

  hooks.goAiGuideStep('review'); // 'stars'
  assert.equal(sky.getAttribute('data-scene'), 'stars');
});

test('후보 적용은 Bildi 경로에서도 PUT/Publish 없이 builderDraft만 바꾼다', async () => {
  const putCalls = [];
  const { hooks } = loadBildi({
    fetch: async (url, init) => {
      if (init && init.method === 'PUT') { putCalls.push(url); return { ok: true, json: async () => ({}) }; }
      return { ok: true, json: async () => ({ target: 'principalDesc', candidates: ['20년간 지역 학생을 지도한 원장이 직접 가르칩니다.'] }) };
    }
  });
  const academy = academyFixture({
    publicPathUrl: '/academies/x',
    pageSettings: { builderDraft: { principalDesc: '' } }
  });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  hooks.goAiGuideStep('principal');
  hooks.requestAiGuideRecommendations();
  await flush();
  hooks.applyAiGuideCandidate(0);

  assert.equal(academy.pageSettings.builderDraft.principalDesc, '20년간 지역 학생을 지도한 원장이 직접 가르칩니다.');
  assert.equal(putCalls.length, 0, 'Bildi 적용이 발행 PUT을 자동 실행하면 안 된다');
});

test('F2RAcademyWorld 미로드 → 기존 정적 패널로 폴백(회귀 없음)', () => {
  const { hooks, doc } = loadBildi({ noWorld: true });
  const academy = academyFixture({ publicPathUrl: '/academies/x', handle: 'apgujeong-eduholic' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);

  const stage = doc.getElementById('builderBildiStage');
  assert.equal(stage.children.length, 0, 'World 없으면 Bildi를 mount하지 않는다');
  // 폴백: 패널이 열리고 body.ai-guide-open
  assert.equal(doc.getElementById('builderAiGuidePanel').classList.contains('is-open'), true);
  assert.equal(doc.body.classList.contains('bildi-active'), false);
  // guide 본문은 여전히 렌더된다(이름/주소 확정된 Academy → 스타일 단계)
  assert.match(doc.getElementById('builderAiGuideBody').innerHTML, /홈페이지 스타일|어떤 느낌/);
});

test('#builderBildiStage 자체가 없으면(구버전 템플릿) 조용히 패널 폴백', () => {
  const { hooks, doc } = loadBildi({ without: ['builderBildiStage'] });
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  assert.equal(doc.getElementById('builderAiGuidePanel').classList.contains('is-open'), true);
  assert.match(doc.getElementById('builderAiGuideBody').innerHTML, /STEP 1/);
});

// ── Phase 5B.1b: Bildi 위치가 스크롤 progress 에 따라 실제로 이동한다 ──────────
function fakeSection(key, rect) {
  const s = fakeEl('section');
  s.setAttribute('data-builder-section-key', key);
  s.getAttribute = (k) => (k === 'data-builder-section-key' ? key : (s._attrs[k] == null ? null : s._attrs[k]));
  s._rect = rect;
  return s;
}

function primeBildiGeometry(doc, sectionKeys) {
  const stage = doc.getElementById('builderBildiStage');
  const pc = doc.getElementById('pageContent');
  // 실제 Builder 좌표계 근사: stage 가 canvas-frame 전체(0..680×520), pageContent 는 브라우저바
  // 아래(y 44..520). Bildi safe area = pageContent inset.
  stage._rect = { left: 0, top: 0, width: 680, height: 520, right: 680, bottom: 520 };
  pc._rect = { left: 0, top: 44, width: 680, height: 476, right: 680, bottom: 520 };
  pc.scrollTop = 0; pc.scrollHeight = 4000; pc.clientHeight = 476;
  const sections = sectionKeys.map((k, i) =>
    fakeSection(k, { left: 0, top: 44 + i * 400, width: 680, height: 400, right: 680, bottom: 44 + (i + 1) * 400 }));
  pc.querySelectorAll = (sel) => (String(sel).indexOf('data-builder-section-key') >= 0 ? sections : []);
  pc.querySelector = (sel) => (String(sel).indexOf('ribbon') >= 0 ? null : null);
  return { stage, pc, sections };
}

test('updateBildiFlight: 스크롤 progress 가 달라지면 Bildi 화면 좌표가 실제로 달라진다 (고정 금지)', () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  primeBildiGeometry(doc, ['sec-hero', 'sec-principal', 'sec-philosophy', 'sec-videos', 'sec-lessons']);

  const bildiEl = hooks.getBildi().el;
  const seen = [];
  [0, 0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1].forEach((p) => {
    hooks.updateBildiFlight({ progress: p, direction: 'down', speed: 'normal', activeSection: null });
    seen.push(bildiEl.style.transform);
  });

  // 같은 transform 이 계속 반복되면 실패 (viewport 고정 chatbot)
  const unique = new Set(seen);
  assert.ok(unique.size >= 6, `progress 별로 위치가 바뀌어야 한다: ${JSON.stringify(seen)}`);
  assert.notEqual(seen[0], seen[seen.length - 1], '시작과 끝 좌표가 다르다');

  // 파싱해서 X 가 좌우로 크게 움직이는지
  const xs = seen.map((t) => Number((t.match(/translate3d\((-?\d+)px/) || [])[1]));
  assert.ok(Math.max(...xs) - Math.min(...xs) > 150, `X 이동폭이 커야 한다 (${xs})`);
});

test('updateBildiFlight: 위로 스크롤하면 Bildi 가 반대 방향으로 이동하고 data-facing=up', () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  primeBildiGeometry(doc, ['sec-hero', 'sec-principal', 'sec-philosophy']);
  const bildiEl = hooks.getBildi().el;

  hooks.updateBildiFlight({ progress: 0.8, direction: 'down', speed: 'normal' });
  const down = bildiEl.style.transform;
  hooks.updateBildiFlight({ progress: 0.2, direction: 'up', speed: 'normal' });
  const up = bildiEl.style.transform;

  assert.notEqual(down, up, '스크롤 방향에 따라 위치가 되돌아간다');
  assert.equal(bildiEl.getAttribute('data-facing'), 'up');
});

test('updateBildiFlight: Bildi 좌표가 safe area(브라우저바 아래) 안에 머문다 — 상단 UI overlap 없음', () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  primeBildiGeometry(doc, ['sec-hero', 'sec-principal', 'sec-philosophy', 'sec-videos']);
  const bildiEl = hooks.getBildi().el;

  for (let p = 0; p <= 1.0001; p += 0.05) {
    hooks.updateBildiFlight({ progress: p, direction: 'down', speed: 'fast' });
    const m = bildiEl.style.transform.match(/translate3d\((-?\d+)px,(-?\d+)px/);
    const x = Number(m[1]); const y = Number(m[2]);
    // safe area: pageContent inset (top 44 + PAD 14 = 58 이상), 아래로 476-14-84
    assert.ok(y >= 44 + 14 - 1, `y(${y}) 가 브라우저바+리본 아래여야 한다 @p=${p.toFixed(2)}`);
    assert.ok(y <= 520 - 14 - 84 + 1, `y(${y}) 가 하단 safe area 안이어야 한다 @p=${p.toFixed(2)}`);
    assert.ok(x >= 14 - 1 && x <= 680 - 14 - 84 + 1, `x(${x}) 가 좌우 safe area 안 @p=${p.toFixed(2)}`);
  }
});

test('updateBildiFlight: 스크롤 중 fly 상태, 멈추면 land→idle/point (상태머신 유지)', async () => {
  const { hooks, doc } = loadBildi();
  const academy = academyFixture({ publicPathUrl: '/academies/x' });
  hooks.setPreview(academy);
  hooks.wireAiGuide(academy);
  primeBildiGeometry(doc, ['sec-hero', 'sec-principal', 'sec-philosophy']);
  const b = hooks.getBildi();

  hooks.updateBildiFlight({ progress: 0.4, direction: 'down', speed: 'normal' });
  assert.ok(['fly', 'wings_open'].includes(b.getState()), '스크롤 중엔 비행: ' + b.getState());
  // 멈춤 디바운스(180ms) + settle(320ms) 대기
  await new Promise((r) => setTimeout(r, 600));
  assert.ok(['land', 'idle', 'point', 'talk'].includes(b.getState()), '멈추면 착지/대기: ' + b.getState());
});
