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
  scrollIntoView() { this.scrolled = true; }
}
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup({ hash = '#review', search = '', admin = true, items = [], download, listFailure = false } = {}) {
  const ids = Object.fromEntries(['wikiSidebar','wikiMenuToggle','wikiPaneTitle','wikiPaneDescription','reviewList','reviewMonthTitle','reviewMonthSwitch','reviewStatus','reviewRetry','reviewReader','reviewFrame','reviewReaderClose','reviewReaderTitle','gradeTabs'].map(id => [id, new Element()]));
  const keys = ['home','review','guides','originals','analysis','learning','threads','live'];
  const panels = keys.map(key => new Element({ wikiPanel: key }));
  const navs = keys.map(key => new Element({ wikiNav: key }));
  ids.wikiSidebar.querySelectorAll = () => navs;
  const window = new Element();
  window.wikiHub = { currentGrade: () => 1, reloadAnalyses: () => {} };
  window.WikiAdminManage = { isAdmin: async () => admin, buttonsHtml: () => '<button>수정</button><button>삭제</button>', bind: () => {} };
  const document = new Element();
  document.getElementById = id => ids[id];
  document.querySelectorAll = () => panels;
  const context = { document, window, URLSearchParams, location: { hash, search }, URL: { createObjectURL: () => 'blob:test', revokeObjectURL: () => {} }, Blob, alert: () => {}, fetch: async url => { if (listFailure && !url.endsWith('/download')) throw new Error('temporary'); return url.endsWith('/download') ? download(url) : { ok: true, json: async () => items }; } };
  vm.runInNewContext(fs.readFileSync(path.join(root, 'wiki_workspace.js'), 'utf8'), context);
  return { ids, panels, navs, window, context, document };
}
test('a sidebar selection exposes only its matching pane and names it', async () => {
  const app = setup(); await tick();
  assert.deepEqual(app.panels.filter(p => !p.hidden).map(p => p.dataset.wikiPanel), ['review']);
  assert.equal(app.ids.wikiPaneTitle.textContent, '지문 복습 문제');
  app.context.location.hash = '#originals'; await app.window.fire('hashchange');
  assert.deepEqual(app.panels.filter(p => !p.hidden).map(p => p.dataset.wikiPanel), ['originals']);
  assert.equal(app.ids.wikiPaneTitle.focused, true);
  assert.equal(app.ids.wikiPaneTitle.scrolled, true);
  assert.equal(app.navs.filter(p => p.attrs['aria-current'] === 'page').length, 1);
});
test('unrecognized hash falls back to home without leaving a blank workspace', () => {
  const app = setup({ hash: '#unknown' });
  assert.equal(app.ids.wikiPaneTitle.textContent, '위키홈');
  assert.equal(app.panels[0].hidden, false);
});
test('mobile menu closes on Escape and returns focus to its toggle', async () => {
  const app = setup(); await app.ids.wikiMenuToggle.fire('click');
  assert.equal(app.ids.wikiMenuToggle.attrs['aria-expanded'], 'true');
  await app.document.fire('keydown', { key: 'Escape' });
  assert.equal(app.ids.wikiMenuToggle.attrs['aria-expanded'], 'false');
  assert.equal(app.ids.wikiMenuToggle.focused, true);
});
test('October shows ascending question buttons without search or download links', async () => {
  const app = setup({items:[
    {wikiAnalysisMaterialId:2,title:'2026 고1 10월 31번 복습 과제',originalFileName:'31.html'},
    {wikiAnalysisMaterialId:1,title:'2026 고1 10월 20번 복습 과제',originalFileName:'20.html'},
    {wikiAnalysisMaterialId:3,title:'고1 9월 자습용 상세해설',originalFileName:'9.html'},
    {wikiAnalysisMaterialId:4,title:'이전 편집 시안 검토본',originalFileName:'old.html'}
  ]});await tick();
  const html=app.ids.reviewList.innerHTML;
  assert.ok(html.indexOf('20번') < html.indexOf('31번'));
  assert.equal((html.match(/data-review-open=/g)||[]).length,2);
  assert.doesNotMatch(html,/href=|download|검토본|전체 과제/);
  assert.equal((html.match(/<button/g)||[]).length,6);
  assert.equal(app.ids.reviewMonthTitle.textContent,'2026년 10모 복습과제');
});
test('September arrow shows only September material and returns to October',async()=>{
 const app=setup({items:[{wikiAnalysisMaterialId:1,title:'10월 20번 복습 과제'},{wikiAnalysisMaterialId:2,title:'9월 자습용 상세해설',originalFileName:'9.html'}]});await tick();
 assert.equal(app.ids.reviewMonthSwitch.textContent,'← 2026년 9모 자료');
 await app.ids.reviewMonthSwitch.fire('click');await tick();
 assert.equal(app.ids.reviewMonthTitle.textContent,'2026년 9모 복습과제');
 assert.match(app.ids.reviewList.innerHTML,/전체 과제/);assert.doesNotMatch(app.ids.reviewList.innerHTML,/20번/);
 await app.ids.reviewMonthSwitch.fire('click');await tick();assert.match(app.ids.reviewList.innerHTML,/20번/);
});
test('failed loading keeps question numbers and offers an explicit retry',async()=>{
 const app=setup({listFailure:true});await tick();assert.match(app.ids.reviewList.innerHTML,/20번/);assert.match(app.ids.reviewList.innerHTML,/41~42번/);assert.equal(app.ids.reviewRetry.hidden,false);
 app.context.fetch=async()=>({ok:true,json:async()=>[{wikiAnalysisMaterialId:1,title:'10월 20번 복습 과제'}]});
 await app.ids.reviewRetry.fire('click');await tick();assert.equal(app.ids.reviewRetry.hidden,true);assert.match(app.ids.reviewList.innerHTML,/data-review-open="1"/);
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

test('unused community navigation and board previews are absent from the hub', () => {
  const html = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/public/wiki.html'), 'utf8');
  assert.doesNotMatch(html, /data-wiki-nav="community"|data-wiki-panel="community"|class="board-col"/);
});


test('review template has month navigation and no search field',()=>{const html=fs.readFileSync(path.join(__dirname,'../../main/resources/templates/public/wiki.html'),'utf8');assert.match(html,/id="reviewMonthSwitch"/);assert.doesNotMatch(html,/reviewSearch|복습 과제 검색/);});

test('September uses numbered HTML variants, prefers them to workbooks, and excludes PDF',async()=>{
 const app=setup({items:[
 {wikiAnalysisMaterialId:2,title:'2026 고1 9월 20번 WORKBOOK',originalFileName:'20-workbook.html'},
 {wikiAnalysisMaterialId:3,title:'2026 고1 9월 20번 변형문제',originalFileName:'20.html'},
 {wikiAnalysisMaterialId:4,title:'2026 고1 9월 21번 변형문제',originalFileName:'21.html'},
 {wikiAnalysisMaterialId:5,title:'2026 고1 9월 자습용 통합본',originalFileName:'all.pdf'}
 ]});await tick();await app.ids.reviewMonthSwitch.fire('click');await tick();
 const html=app.ids.reviewList.innerHTML;
 assert.equal((html.match(/data-review-open=/g)||[]).length,2);
 assert.match(html,/data-review-open="3"/);assert.match(html,/data-review-open="4"/);
 assert.doesNotMatch(html,/data-review-open="[25]"|전체 과제/);
});

test('material links open the exact item even when question numbers repeat',async()=>{
 let opened='';
 const app=setup({search:'?question=20&material=2',items:[
 {wikiAnalysisMaterialId:1,title:'10월 20번 복습 과제',originalFileName:'old.html'},
 {wikiAnalysisMaterialId:2,title:'10월 20번 복습 과제',originalFileName:'new.html'}
 ],download:async url=>{opened=url;return {ok:true,text:async()=>'<html>exact material</html>'};}});
 await tick();await tick();assert.equal(opened,'/api/wiki/analysis-materials/2/download');
});
test('a missing material ID never falls back to a different same-number item',async()=>{
 let opened=false;setup({search:'?question=20&material=404',items:[
 {wikiAnalysisMaterialId:1,title:'10월 20번 복습 과제',originalFileName:'old.html'}
 ],download:async()=>{opened=true;return {ok:true,text:async()=>'<html>wrong</html>'};}});
 await tick();await tick();assert.equal(opened,false);
});
