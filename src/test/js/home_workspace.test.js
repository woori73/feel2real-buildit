const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {init} = require('../../main/resources/static/js/home_workspace.js');

const HTML = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/landing.html'), 'utf8');

function node(extra={}) {
  const classes=new Set(), handlers={};
  return Object.assign({hidden:false,attrs:{},handlers,offsetParent:{},
    classList:{add:k=>classes.add(k),remove:k=>classes.delete(k),contains:k=>classes.has(k),toggle(k){if(classes.has(k)){classes.delete(k);return false;}classes.add(k);return true;}},
    addEventListener(k,fn){handlers[k]=fn;},setAttribute(k,v){this.attrs[k]=v;},getAttribute(k){return this.attrs[k];},
    focus(){doc.activeElement=this;},closest(){return null;},querySelectorAll(){return [];}
  },extra);
}
let doc;
function setup(hash='') {
  const root=node(),toggle=node(),closeBtn=node(),firstLink=node(),lastLink=node(),ticker=node(),pause=node();
  const details={tagName:'DETAILS',open:false};
  const panel=node({hidden:true,closest:s=>s==='.home-light'?root:null,querySelectorAll:()=>[closeBtn,firstLink,lastLink]});
  [closeBtn,firstLink,lastLink].forEach(n=>{n.closest=()=>null;});
  const ids={homeMenuPanel:panel,enBurger:toggle,homeMenuClose:closeBtn,homeNoticeTrack:ticker,homeNoticePause:pause,hitCompare:details};
  doc={body:{},activeElement:null,handlers:{},getElementById:id=>ids[id]||null,addEventListener(k,fn){this.handlers[k]=fn;}};
  const win={location:{hash},handlers:{},addEventListener(k,fn){this.handlers[k]=fn;}};
  const app=init(doc,win);
  const key=(k,shiftKey=false)=>{let prevented=false;doc.handlers.keydown({key:k,shiftKey,preventDefault(){prevented=true;}});return prevented;};
  const clickIn=target=>panel.handlers.click({target});
  return {app,doc,win,root,panel,toggle,closeBtn,firstLink,lastLink,ticker,pause,details,key,clickIn};
}

test('given the page loads then the full menu starts closed, the shell is marked ready and nothing else is hidden', () => {
  // given / when
  const s=setup();
  // then
  assert.equal(s.panel.hidden,true);
  assert.equal(s.root.classList.contains('home-ready'),true);
  assert.equal(s.app.isOpen(),false);
});

test('given the menu button when pressed then the panel opens, the button reports expanded and focus moves to 닫기', () => {
  // given
  const s=setup();s.toggle.focus();
  // when
  s.toggle.handlers.click();
  // then
  assert.equal(s.panel.hidden,false);
  assert.equal(s.toggle.attrs['aria-expanded'],'true');
  assert.equal(s.root.classList.contains('menu-open'),true);
  assert.equal(s.doc.activeElement,s.closeBtn);
});

test('given an open menu when Escape is pressed then it closes and focus returns to the menu button', () => {
  // given
  const s=setup();s.toggle.focus();s.toggle.handlers.click();
  // when
  const prevented=s.key('Escape');
  // then
  assert.equal(prevented,true);
  assert.equal(s.panel.hidden,true);
  assert.equal(s.toggle.attrs['aria-expanded'],'false');
  assert.equal(s.root.classList.contains('menu-open'),false);
  assert.equal(s.doc.activeElement,s.toggle);
});

test('given an open menu when 닫기 or the dimmed backdrop is pressed then it closes and focus returns', () => {
  // given
  const s=setup();s.toggle.focus();s.toggle.handlers.click();
  // when
  s.clickIn({closest:sel=>sel==='[data-menu-close]'?{}:null});
  // then
  assert.equal(s.panel.hidden,true);
  assert.equal(s.doc.activeElement,s.toggle);
});

test('given an open menu when a destination link is chosen then the menu closes and the browser follows the link', () => {
  // given
  const s=setup();s.toggle.handlers.click();
  // when
  s.clickIn({closest:sel=>sel==='a[href], [data-auth-login]'?{}:null});
  // then
  assert.equal(s.panel.hidden,true);
});

test('given an open menu when 로그인 inside it is pressed then the menu closes so the login dialog gets the keyboard', () => {
  // given
  const s=setup();s.toggle.handlers.click();
  const login={closest:sel=>sel.split(', ').includes('[data-auth-login]')?{}:null};
  // when
  s.clickIn(login);
  // then
  assert.equal(s.panel.hidden,true);
  assert.equal(s.key('Tab'),false);
});

test('given an open menu when Tab passes the last or first item then focus wraps inside the panel', () => {
  // given
  const s=setup();s.toggle.handlers.click();
  // when / then
  s.lastLink.focus();assert.equal(s.key('Tab'),true);assert.equal(s.doc.activeElement,s.closeBtn);
  assert.equal(s.key('Tab',true),true);assert.equal(s.doc.activeElement,s.lastLink);
  s.firstLink.focus();assert.equal(s.key('Tab'),false);
});

test('given a closed menu when keys are pressed then the page keeps its normal keyboard behaviour', () => {
  // given
  const s=setup();
  // when / then
  assert.equal(s.key('Escape'),false);
  assert.equal(s.key('Tab'),false);
  assert.equal(s.panel.hidden,true);
});

test('given a link to a closed 적중 사례 item when the hash points at it then the item opens', () => {
  // given
  const s=setup('#hitCompare');
  // then (initial load)
  assert.equal(s.details.open,true);
  // and on later navigation
  s.details.open=false;s.win.location.hash='#hitCompare';s.win.handlers.hashchange();
  assert.equal(s.details.open,true);
  s.win.location.hash='#%E0%A4%A';assert.doesNotThrow(()=>s.win.handlers.hashchange());
});

test('given moving notices when pause is toggled then control and animation state match', () => {
  // given
  const s=setup();
  // when / then
  s.pause.handlers.click();assert.equal(s.ticker.classList.contains('is-paused'),true);assert.equal(s.pause.attrs['aria-pressed'],'true');
  s.pause.handlers.click();assert.equal(s.ticker.classList.contains('is-paused'),false);assert.equal(s.pause.attrs['aria-label'],'공지 흐름 일시정지');
});

test('given the home template then the full menu is a closed dialog wired to the menu button and every approved destination stays reachable', () => {
  // given / when
  const panel=HTML.slice(HTML.indexOf('id="homeMenuPanel"'),HTML.indexOf('<main'));
  const keys=[...HTML.matchAll(/data-home-nav="([a-z]+)"/g)].map(m=>m[1]);
  // then
  assert.match(HTML,/id="enBurger" aria-label="전체 메뉴 열기" aria-expanded="false" aria-controls="homeMenuPanel"/);
  assert.match(HTML,/<div class="hl-menu" id="homeMenuPanel" hidden>/);
  assert.match(panel,/role="dialog" aria-modal="true" aria-labelledby="homeMenuTitle"/);
  assert.match(panel,/id="homeMenuClose" aria-label="메뉴 닫기" data-menu-close/);
  assert.deepEqual(keys,['home','request','wiki','game','agent','dashboard','chart','guide']);
  assert.doesNotMatch(HTML,/data-home-panel|id="homeSidebar"/);
});
