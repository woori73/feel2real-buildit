import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import vm from 'node:vm';

const reordered = ['sec-hero','sec-intro','sec-lessons','sec-principal','sec-videos','sec-reviews','sec-curriculum','sec-effect','sec-digitaltwin','sec-market','sec-payment','sec-social'];

function loadBrowserScript(path, extras = {}) {
  const document = extras.document || {
    addEventListener() {},
    getElementById() { return null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    createElement() { return { style: {}, setAttribute() {}, appendChild() {} }; },
    head: { appendChild() {} }
  };
  const sessionStorage = {
    getItem() { return null; },
    setItem() {},
    removeItem() {}
  };
  const window = {
    F2RBuilderTextUtils: {},
    F2RAcademyBusinessInfo: {
      EDITABLE_KEYS: [],
      normalize(value) { return value || {}; },
      normalizeRaw(value) { return value || {}; },
      renderRows() { return ''; }
    },
    F2RAcademyBusinessInfoEditor: {
      idle() { return { phase: 'idle' }; }
    },
    F2RBuilderEditableGuide: {
      shouldShowCoachmark() { return false; },
      markCoachmarkSeen() {},
      closestEditableTarget() { return null; }
    },
    F2R_ACADEMY_SECTION_ORDER_TEST: true,
    addEventListener() {},
    CSS: { escape: (value) => String(value) },
    showToast() {},
    ...extras.window
  };
  const context = vm.createContext({
    window,
    document,
    sessionStorage,
    location: extras.location || { origin: 'http://localhost', pathname: '/academies/sample', href: '' },
    console,
    URL,
    Blob,
    FormData: class {},
    setTimeout() {},
    requestAnimationFrame(callback) { callback(); },
    alert(message) { throw new Error(`unexpected alert: ${message}`); },
    fetch() { throw new Error(`unexpected fetch while loading ${path}`); },
    fetchAuth() { throw new Error(`unexpected fetchAuth while loading ${path}`); },
    currentUser() { return null; }
  });
  vm.runInContext(readFileSync(path, 'utf8'), context, { filename: path });
  return context.window.F2RAcademySectionOrderTestHooks;
}

const builderHooks = loadBrowserScript('src/main/resources/static/js/academy_builder.js');
assert(builderHooks, 'builder section order test hooks are exposed');
const publicHooks = loadBrowserScript('src/main/resources/static/js/academy_public.js', {
  document: {
    addEventListener() {},
    getElementById(id) {
      if (id === 'publicView') return { dataset: { slug: 'sample' }, innerHTML: '', addEventListener() {} };
      if (id === 'errorMsg') return { textContent: '' };
      return null;
    },
    querySelectorAll() { return []; }
  }
});
assert(publicHooks, 'public section order test hooks are exposed');

const academy = { pageSettings: { sectionOrder: reordered, sectionVisibility: {} } };
assert.deepEqual(builderHooks.orderedSectionIds(academy), reordered, 'actual builder helper keeps reordered full order with sec-hero first');
assert.deepEqual(publicHooks.orderedSectionIds(academy), reordered, 'actual public helper keeps reordered full order with sec-hero first');

const hiddenAcademy = { pageSettings: { sectionOrder: reordered, sectionVisibility: { 'sec-lessons': false } } };
assert.deepEqual(builderHooks.visibleSectionIds(hiddenAcademy), ['sec-hero','sec-intro','sec-principal','sec-videos','sec-social'], 'actual builder visible order skips hidden and future sections');
assert.deepEqual(publicHooks.visibleSectionIds(hiddenAcademy), ['sec-hero','sec-intro','sec-principal','sec-videos','sec-social'], 'actual public visible order skips hidden and future sections');
assert.deepEqual(builderHooks.orderedSectionIds(hiddenAcademy), reordered, 'hidden section does not mutate builder full order');
assert.deepEqual(builderHooks.layoutSectionIds(hiddenAcademy), ['sec-hero','sec-intro','sec-principal','sec-videos','sec-social','sec-footer'], 'layout tab lists only supported ON sections plus locked hero and footer');
assert.equal(builderHooks.visibleSectionIds(academy).indexOf('sec-lessons'), 2, 'section reappears in the same saved slot when visible again');
assert.equal(builderHooks.displayUrlForPreview({ name: '테스트학원', slug: '한글슬러그', handle: 'apgujeong-english' }), 'apgujeong-english.feel2real.co.kr', 'builder browser URL uses actual handle before title-derived fallback');
assert.equal(builderHooks.displayUrlForPreview({ name: '테스트학원', slug: 'academy-slug' }), 'academy-slug.feel2real.co.kr', 'builder browser URL falls back to actual slug');

assert.equal(builderHooks.nextSectionOrder(reordered, 'sec-intro', 'up'), null, 'actual builder move boundary refuses moving a section above sec-hero');
assert.equal(builderHooks.nextSectionOrder(reordered, 'sec-hero', 'down'), null, 'actual builder move boundary refuses moving sec-hero');
assert.deepEqual(builderHooks.nextSectionOrder(reordered, 'sec-lessons', 'up'), ['sec-hero','sec-lessons','sec-intro','sec-principal','sec-videos','sec-reviews','sec-curriculum','sec-effect','sec-digitaltwin','sec-market','sec-payment','sec-social'], 'actual builder move helper moves non-hero section within allowed range');
assert.deepEqual(builderHooks.nextSectionOrder(reordered, 'sec-principal', 'up', { 'sec-lessons': false }), ['sec-hero','sec-principal','sec-lessons','sec-intro','sec-videos','sec-reviews','sec-curriculum','sec-effect','sec-digitaltwin','sec-market','sec-payment','sec-social'], 'layout move swaps with previous ON section and preserves hidden section in full order');

const template = readFileSync('src/main/resources/templates/tutor/academy_builder.html', 'utf8');
assert(template.includes('data-builder-panel-tab="layout"'), 'template has layout tab');
assert(template.includes('data-section-order-list'), 'template has layout section order list');
assert(template.includes('builderPublicHandleModal'), 'template has public handle modal');
assert(template.includes('builderPublicHandleStatus'), 'template has public handle availability status');
assert(template.includes('builderSocialUrlModal'), 'template has external URL modal');

const builderJs = readFileSync('src/main/resources/static/js/academy_builder.js', 'utf8');
assert(builderJs.includes('data-builder-draft="monthlyTuition"'), 'builder hero monthly tuition stat is editable draft text');
assert(builderJs.includes('data-builder-draft="scoreImprovement"'), 'builder hero score improvement stat is editable draft text');
assert(builderJs.includes('data-builder-draft="operationYears"'), 'builder hero operation years stat is editable draft text');
assert(builderJs.includes('starterDraftForDirectBuilder'), 'builder seeds a direct-entry draft when /builder has no landing pending data');
assert(template.includes('builderPublicHandleCheckBtn'), 'template has public handle explicit availability check button');
assert(builderJs.includes('checkPublicHandleAvailabilityFromButton'), 'builder public handle modal checks availability from an explicit button');
assert(builderJs.includes('social-card-header'), 'builder social card renders the banner header');
assert(!/social-card-body|social-card-actions|PreviewThumbnail/.test(builderJs), 'builder social card is a banner only — thumbnail preview and duplicate action buttons are gone');
assert(builderJs.includes('data-social-url-edit'), 'builder social card click opens URL modal instead of external navigation');

console.log('academy section order smoke passed via actual JS hooks');
