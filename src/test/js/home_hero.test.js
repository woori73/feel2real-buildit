const test = require('node:test');
const assert = require('node:assert/strict');
const {init} = require('../../main/resources/static/js/home_hero.js');
function setup(reduce=false){
 function el(){return {hidden:false,attrs:{},events:{},setAttribute(k,v){this.attrs[k]=v},getAttribute(k){return this.attrs[k]},addEventListener(k,f){this.events[k]=f}}}
 const slides=Array.from({length:4},(_,i)=>{const x=el();x.attrs['aria-label']=`${i+1} / 4`;return x});
 const dots=slides.map(el),ids={};['heroPause','heroNext','heroPrev','heroCount','heroStatus'].forEach(k=>ids[k]=el());
 const panel=el(),controls=el(),root=el();ids.homeHero=root;
 root.querySelectorAll=q=>q==='[data-hero-slide]'?slides:dots;root.querySelector=()=>controls;root.closest=()=>panel;root.contains=x=>dots.includes(x);
 const doc=el();doc.getElementById=k=>ids[k];const media=el();media.matches=reduce;
 const win=el();let callback=null;win.matchMedia=()=>media;win.clearTimeout=()=>callback=null;win.setTimeout=f=>{callback=f;return 1};
 const app=init(doc,win);return {app,root,slides,dots,ids,doc,win,panel,media,tick(){const f=callback;callback=null;if(f)f()},scheduled:()=>!!callback};
}
test('given default carousel when timer advances then one slide and its selector are active',()=>{const s=setup();assert.equal(s.scheduled(),true);s.tick();assert.equal(s.app.current(),1);assert.equal(s.slides.filter(x=>!x.hidden).length,1);assert.equal(s.dots[1].attrs['aria-pressed'],'true')});
test('given manual controls when wrapping then counter and announcement follow the slide',()=>{const s=setup();s.ids.heroPrev.events.click();assert.equal(s.app.current(),3);assert.equal(s.ids.heroCount.textContent,'4 / 4');s.ids.heroNext.events.click();assert.equal(s.app.current(),0);s.dots[2].events.click();assert.equal(s.ids.heroStatus.textContent,'3 / 4')});
test('given pause, focus, hover or hidden page then automatic navigation waits',()=>{const s=setup();s.root.events.mouseenter();assert.equal(s.scheduled(),false);s.root.events.mouseleave();assert.equal(s.scheduled(),true);s.root.events.focusin();assert.equal(s.scheduled(),false);s.root.events.focusout({relatedTarget:null});s.ids.heroPause.events.click();assert.equal(s.scheduled(),false);s.ids.heroPause.events.click();s.doc.hidden=true;s.doc.events.visibilitychange();assert.equal(s.scheduled(),false);s.doc.hidden=false;s.panel.hidden=true;s.win.events.hashchange();assert.equal(s.scheduled(),false)});
test('given reduced motion then rotation starts paused but manual selection remains usable',()=>{const s=setup(true);assert.equal(s.scheduled(),false);assert.equal(s.ids.heroPause.attrs['aria-pressed'],'true');s.ids.heroNext.events.click();assert.equal(s.app.current(),1);assert.equal(s.scheduled(),false)});
