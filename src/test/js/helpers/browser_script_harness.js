const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SECTIONS_PATH = path.resolve(__dirname, '../../../main/resources/static/js/academy_sections.js');
const AI_GUIDE_PATH = path.resolve(__dirname, '../../../main/resources/static/js/academy_builder_ai_guide.js');
const WORLD_PATH = path.resolve(__dirname, '../../../main/resources/static/js/academy_world.js');
let sectionsModule = null;
let aiGuideModule = null;
let worldModule = null;

/**
 * 섹션 마크업 정본(academy_sections.js)을 실제로 실행해 돌려준다.
 * academy_public.js / academy_builder.js가 이 모듈 없이는 부팅하지 않으므로 스텁이 아니라
 * 진짜를 넣는다. DOM 의존이 없어 별도 컨텍스트에서 한 번만 평가하면 된다.
 */
function loadSectionsModule() {
  if (sectionsModule) return sectionsModule;
  // URL은 JS 내장이 아니라 호스트가 주는 전역이다. 빠뜨리면 youtubeVideoId의 new URL()이
  // 던지고 catch가 null을 반환해, YouTube 링크가 조용히 placeholder로 떨어진다.
  const context = vm.createContext({ console, URL });
  vm.runInContext(fs.readFileSync(SECTIONS_PATH, 'utf8'), context, { filename: SECTIONS_PATH });
  sectionsModule = context.F2RAcademySections;
  if (!sectionsModule) throw new Error('academy_sections.js가 F2RAcademySections를 노출하지 않았다');
  return sectionsModule;
}

/**
 * AI Guide 순수 헬퍼(academy_builder_ai_guide.js)의 실제 모듈. academy_builder.js가 이
 * 모듈을 window.F2RAcademyAiGuide로 읽으므로 스텁이 아니라 진짜를 넣는다(DOM 의존 없음).
 */
function loadAiGuideModule() {
  if (aiGuideModule) return aiGuideModule;
  aiGuideModule = require(AI_GUIDE_PATH);
  return aiGuideModule;
}

/**
 * Academy World Engine(academy_world.js) 실제 모듈. academy_builder.js가 window.F2RAcademyWorld로
 * 읽는다(Bildi Sidekick). 순수 로직 + DOM 헬퍼라 UMD require로 로드 가능.
 */
function loadWorldModule() {
  if (worldModule) return worldModule;
  worldModule = require(WORLD_PATH);
  return worldModule;
}

function memoryStorage(seed = {}) {
  const values = new Map(Object.entries(seed).map(([key, value]) => [
    key,
    typeof value === 'string' ? value : JSON.stringify(value)
  ]));
  const writes = [];
  const removes = [];
  return {
    writes,
    removes,
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      const text = String(value);
      values.set(key, text);
      writes.push({ key, value: text });
    },
    removeItem(key) {
      values.delete(key);
      removes.push(key);
    },
    json(key) {
      const value = this.getItem(key);
      return value == null ? null : JSON.parse(value);
    }
  };
}

function loadBrowserScript(path, options = {}) {
  const readyCallbacks = [];
  const document = options.document || {
    addEventListener(type, callback) {
      if (type === 'DOMContentLoaded') readyCallbacks.push(callback);
    },
    getElementById() { return null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    createElement() {
      return {
        dataset: {},
        style: {},
        classList: { add() {}, remove() {}, toggle() {} },
        setAttribute() {},
        appendChild() {}
      };
    },
    head: { appendChild() {} }
  };
  const storage = options.sessionStorage || memoryStorage();
  const fetchCalls = [];
  const fetchImpl = options.fetch || (async (url, init = {}) => {
    fetchCalls.push({ url, init });
    throw new Error(`unexpected fetch: ${url}`);
  });
  const window = {
    F2RBuilderTextUtils: {
      extractEditableText(element) { return element.textContent || ''; },
      normalizeSingleLineText(value) { return String(value || '').replace(/\s+/g, ' ').trim(); },
      normalizeMultilineText(value) { return String(value || '').trim(); },
      isSingleLineTextTarget(element) { return !element.classList.contains('builder-multiline-text'); },
      ...(options.textUtils || {})
    },
    F2RAcademyBusinessInfo: {
      EDITABLE_KEYS: [],
      normalize(value) { return value || {}; },
      normalizeRaw(value) { return value || {}; },
      rows() { return []; },
      renderRows() { return ''; },
      ...(options.businessInfo || {})
    },
    F2RAcademyBusinessInfoEditor: {
      idle() { return { phase: 'idle' }; },
      ...(options.businessInfoEditor || {})
    },
    F2RBuilderEditableGuide: {
      create() { return { active: false }; },
      shouldShowCoachmark() { return false; },
      markCoachmarkSeen() {},
      ...(options.editableGuide || {})
    },
    F2RLessonProductShowcase: {
      render() { return ''; },
      ...(options.lessonProductShowcase || {})
    },
    // 스텁이 아니라 실제 모듈이다 — 섹션 마크업 정본이라 대체할 수 없다.
    F2RAcademySections: loadSectionsModule(),
    // 스텁이 아니라 실제 모듈이다 — AI Guide 스텝 정의/세션 로직 정본.
    F2RAcademyAiGuide: loadAiGuideModule(),
    F2RAcademyWorld: loadWorldModule(),
    CSS: { escape: (value) => String(value) },
    addEventListener() {},
    showToast() {},
    ...(options.window || {})
  };
  const context = vm.createContext({
    alert() {},
    atob(value) { return Buffer.from(value, 'base64').toString('binary'); },
    Blob,
    console,
    document,
    fetch: fetchImpl,
    fetchAuth: options.fetchAuth || fetchImpl,
    FormData: options.FormData || FormData,
    location: options.location || { href: '', origin: 'http://localhost', pathname: '/builder' },
    requestAnimationFrame(callback) { callback(); },
    sessionStorage: storage,
    setTimeout: options.setTimeout || setTimeout,
    clearTimeout,
    URL,
    Uint8Array,
    window
  });
  vm.runInContext(fs.readFileSync(path, 'utf8'), context, { filename: path });
  return { context, document, fetchCalls, readyCallbacks, storage, window };
}

module.exports = { loadBrowserScript, memoryStorage, loadSectionsModule, loadAiGuideModule, loadWorldModule };
