const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../../main/resources/static/js');
class Element {
  constructor(dataset = {}) { this.dataset = dataset; this.handlers = {}; this.attrs = {}; this.value = ''; this.hidden = false; this.isConnected = true; this.innerHTML = ''; this.classList = { toggle: key => { const next = !this.attrs[key]; this.attrs[key] = next; return next; }, remove: key => delete this.attrs[key], contains: key => !!this.attrs[key] }; }
  addEventListener(name, cb) { (this.handlers[name] ||= []).push(cb); }
  async fire(name, event = {}) { for (const fn of this.handlers[name] || []) await fn(event); }
  setAttribute(k, v) { this.attrs[k] = v; }
  getAttribute(k) { return this.attrs[k]; }
  removeAttribute(k) { delete this.attrs[k]; }
  focus() { this.focused = true; }
  scrollIntoView() {}
}
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup({ hash = '#review', admin = true, items = [], download } = {}) {
  const ids = Object.fromEntries(['wikiSidebar','wikiMenuToggle','wikiPaneTitle','wikiPaneDescription','reviewList','reviewSearch','reviewReader','reviewFrame','reviewReaderClose','reviewReaderTitle','gradeTabs'].map(id => [id, new Element()]));
  const keys = ['home','review','guides','originals','analysis','learning','threads','community','live'];
  const panels = keys.map(key => new Element({ wikiPanel: key }));
  const navs = keys.map(key => new Element({ wikiNav: key }));
  ids.wikiSidebar.querySelectorAll = () => navs;
  const window = new Element();
  window.wikiHub = { currentGrade: () => 1, reloadAnalyses: () => {} };
  window.WikiAdminManage = { isAdmin: async () => admin, buttonsHtml: () => '<button>수정</button><button>삭제</button>', bind: () => {} };
  const document = new Element();
  document.getElementById = id => ids[id];
  document.querySelectorAll = () => panels;
  const context = { document, window, location: { hash }, URL: { createObjectURL: () => 'blob:test', revokeObjectURL: () => {} }, Blob, alert: () => {}, fetch: async url => url.endsWith('/download') ? download() : { ok: true, json: async () => items } };
  vm.runInNewContext(fs.readFileSync(path.join(root, 'wiki_workspace.js'), 'utf8'), context);
  return { ids, panels, navs, window, context, document };
}
test('a sidebar selection exposes only its matching pane and names it', async () => {
  const app = setup(); await tick();
  assert.deepEqual(app.panels.filter(p => !p.hidden).map(p => p.dataset.wikiPanel), ['review']);
  assert.equal(app.ids.wikiPaneTitle.textContent, '복습 과제');
  app.context.location.hash = '#originals'; await app.window.fire('hashchange');
  assert.deepEqual(app.panels.filter(p => !p.hidden).map(p => p.dataset.wikiPanel), ['originals']);
  assert.equal(app.ids.wikiPaneTitle.focused, true);
  assert.equal(app.navs.filter(p => p.attrs['aria-current'] === 'page').length, 1);
});
test('unrecognized hash falls back to home without leaving a blank workspace', () => {
  const app = setup({ hash: '#unknown' });
  assert.equal(app.ids.wikiPaneTitle.textContent, '위키 홈');
  assert.equal(app.panels[0].hidden, false);
});
test('mobile menu closes on Escape and returns focus to its toggle', async () => {
  const app = setup(); await app.ids.wikiMenuToggle.fire('click');
  assert.equal(app.ids.wikiMenuToggle.attrs['aria-expanded'], 'true');
  await app.document.fire('keydown', { key: 'Escape' });
  assert.equal(app.ids.wikiMenuToggle.attrs['aria-expanded'], 'false');
  assert.equal(app.ids.wikiMenuToggle.focused, true);
});
test('review search escapes titles and keeps admin controls to two actions', async () => {
  const app = setup({ items: [{ wikiAnalysisMaterialId: 1, grade: 1, title: '10월 20번 복습 <img>', originalFileName: 'a.html' }, { wikiAnalysisMaterialId: 2, title: '9월 구문독해' }] });
  await tick();
  assert.match(app.ids.reviewList.innerHTML, /&lt;img&gt;/);
  assert.equal((app.ids.reviewList.innerHTML.match(/<button/g) || []).length, 3); // open + edit + delete
  assert.doesNotMatch(app.ids.reviewList.innerHTML, /9월 구문독해/);
  app.ids.reviewSearch.value = '31번'; await app.ids.reviewSearch.fire('input');
  assert.match(app.ids.reviewList.innerHTML, /검색어에 맞는/);
});
test('student cards contain no administrator controls', async () => {
  const app = setup({ admin: false, items: [{ wikiAnalysisMaterialId: 1, grade: 1, title: '10월 복습' }] });
  await tick(); assert.doesNotMatch(app.ids.reviewList.innerHTML, /수정|삭제/);
});
test('closing a reader cancels a pending response so stale content cannot reopen', async () => {
  let finish;
  const app = setup({ items: [{ wikiAnalysisMaterialId: 1, grade: 1, title: '복습', originalFileName: 'a.html' }], download: () => new Promise(resolve => { finish = resolve; }) });
  await tick();
  const button = new Element({ reviewOpen: '1' });
  const opening = app.ids.reviewList.fire('click', { target: { closest: () => button } });
  await app.ids.reviewReaderClose.fire('click');
  finish({ ok: true, status: 200, text: async () => '<html>question</html>' });
  await opening;
  assert.equal(app.ids.reviewReader.hidden, true);
  assert.equal(app.ids.reviewFrame.src, undefined);
});
test('401 does not place downloaded content in the frame', async () => {
  const app = setup({ items: [{ wikiAnalysisMaterialId: 1, grade: 1, title: '복습', originalFileName: 'a.html' }], download: async () => ({ status: 401, ok: false }) });
  await tick();
  await app.ids.reviewList.fire('click', { target: { closest: () => new Element({ reviewOpen: '1' }) } });
  assert.equal(app.ids.reviewReader.hidden, true);
  assert.equal(app.ids.reviewFrame.src, undefined);
});
test('management renders exactly edit and recoverable delete buttons', () => {
  const context = { window: {} };
  vm.runInNewContext(fs.readFileSync(path.join(root, 'wiki_admin_manage.js'), 'utf8'), context);
  const html = context.window.WikiAdminManage.buttonsHtml('analysis', 1);
  assert.equal((html.match(/<button/g) || []).length, 2);
  assert.match(html, />수정<\/button>/); assert.match(html, />삭제<\/button>/);
  assert.doesNotMatch(html, /data-manage-hard/);
});
