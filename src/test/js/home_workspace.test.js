const test = require('node:test');
const assert = require('node:assert/strict');
const {PANES, init} = require('../../main/resources/static/js/home_workspace.js');

function node(dataset={}) {
  const classes=new Set(), handlers={};
  return {dataset,hidden:false,attrs:{},handlers,
    classList:{add:k=>classes.add(k),remove:k=>classes.delete(k),contains:k=>classes.has(k),toggle(k){if(classes.has(k)){classes.delete(k);return false;}classes.add(k);return true;}},
    addEventListener(k,fn){handlers[k]=fn;},setAttribute(k,v){this.attrs[k]=v;},removeAttribute(k){delete this.attrs[k];},getAttribute(k){return this.attrs[k];},
    focus(){this.focused=true;},scrollIntoView(){this.scrolled=true;},querySelectorAll(){return [];},closest(){return null;}
  };
}
function setup(hash='') {
  const shell=node(),sidebar=node(),toggle=node(),title=node(),description=node(),ticker=node(),pause=node();
  const panels=Object.keys(PANES).map(key=>node({homePanel:key}));
  const shared=node({homePanel:'request guide'});panels.push(shared);
  const video={paused:false,pause(){this.paused=true;}};
  panels.find(p=>p.dataset.homePanel==='agent').querySelectorAll=()=>[video];
  const links=Object.keys(PANES).map(key=>node({homeNav:key}));
  sidebar.closest=()=>shell;sidebar.querySelectorAll=()=>links;
  const contact=node();contact.closest=()=>shared;
  const stage=node(),close=node();stage.querySelector=()=>({});close.click=()=>{close.clicked=true;};
  const ids={homeSidebar:sidebar,enBurger:toggle,homePaneTitle:title,homePaneDescription:description,
    homeNoticeTrack:ticker,homeNoticePause:pause,'exam-contact':contact,homeGameStage:stage,homeGameClose:close};
  const doc={title:'Feel2Real',handlers:{},getElementById:id=>ids[id]||null,
    querySelectorAll:q=>q==='[data-home-panel]'?panels:links,addEventListener(k,fn){this.handlers[k]=fn;}};
  const win={location:{hash},handlers:{},addEventListener(k,fn){this.handlers[k]=fn;}};
  const app=init(doc,win);
  return {app,win,doc,panels,links,sidebar,toggle,title,ticker,pause,video,close,contact,
    go(hash){win.location.hash=hash;win.handlers.hashchange();}};
}
test('given no hash when initialized then only home is visible and navigation order is approved',()=>{
  const s=setup();
  assert.deepEqual(Object.keys(PANES),['home','request','wiki','game','agent','dashboard','chart','guide']);
  assert.deepEqual(s.panels.filter(p=>!p.hidden).map(p=>p.dataset.homePanel),['home']);
  assert.equal(s.links[0].attrs['aria-current'],'page');
});
test('given a selection or history hash change then content, focus, title and current link agree',()=>{
  const s=setup();s.go('#ai-content');assert.equal(s.app.current(),'agent');assert.equal(s.title.textContent,'AI AGENT');
  assert.equal(s.title.focused,true);assert.equal(s.doc.title,'AI AGENT | Feel2Real');
  s.go('#home');assert.equal(s.video.paused,true);assert.equal(s.doc.title,'Feel2Real');
});
test('given legacy contact anchor then its pane is shown and the contact itself is scrolled',()=>{
  const s=setup('#exam-contact');assert.equal(s.app.current(),'request');s.go('#exam-contact');assert.equal(s.contact.scrolled,true);
  s.go('#guide');assert.equal(s.panels.at(-1).hidden,false);
});
test('given unknown or malformed hash then homepage remains available',()=>{
  for(const hash of ['#unknown','#%E0%A4%A'])assert.equal(setup(hash).app.current(),'home');
});
test('given open mobile menu when Escape or a selection occurs then it closes accessibly',()=>{
  const s=setup();s.toggle.handlers.click();assert.equal(s.toggle.attrs['aria-expanded'],'true');
  s.doc.handlers.keydown({key:'Escape'});assert.equal(s.toggle.attrs['aria-expanded'],'false');assert.equal(s.toggle.focused,true);
  s.toggle.handlers.click();s.go('#wiki');assert.equal(s.sidebar.classList.contains('menu-open'),false);
});
test('given game pane when leaving then the real game close action releases its iframe',()=>{
  const s=setup('#review-game');s.go('#dashboard');assert.equal(s.close.clicked,true);
});
test('given async media play inside hidden pane then it is paused',()=>{
  const s=setup();let paused=false;s.doc.handlers.play({target:{closest:()=>({hidden:true}),pause(){paused=true;}}});assert.equal(paused,true);
});
test('given moving notices when pause is toggled then control and animation state match',()=>{
  const s=setup();s.pause.handlers.click();assert.equal(s.ticker.classList.contains('is-paused'),true);assert.equal(s.pause.attrs['aria-pressed'],'true');
  s.pause.handlers.click();assert.equal(s.ticker.classList.contains('is-paused'),false);assert.equal(s.pause.attrs['aria-label'],'공지 흐름 일시정지');
});
