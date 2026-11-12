// 시안 렌더·가로넘침·axe 검증 스크립트 (참조용).
// 사용: source/ 폴더에서 `python3 -m http.server 8765` 실행 후 `npm i playwright-core axe-core` → `node verify.playwright.js`
// 결과 이미지는 source/out/ 에 저장된다(커밋 대상 아님 — .gitignore 하지 않았으므로 직접 제외할 것).
// 주의: 이 파일은 시안(정적 HTML) 검증용이며 운영 코드가 아니다. 실제 구현 검증은 HANDOFF §9, §14 참고.
const {chromium}=require('playwright-core');
const fs=require('fs');const D=__dirname;const axe=fs.readFileSync(require.resolve('axe-core/axe.min.js'),'utf8');
(async()=>{
 const b=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox']});
 for(const [name,w,h] of [['mobile',390,844],['pc',1440,900]] /* 확정: 360·430·768·1024도 추가 검증 필요 (HANDOFF §14) */){
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:name==='mobile'?2:1});
  const p=await c.newPage();const errs=[];
  p.on('pageerror',e=>errs.push(String(e)));p.on('console',m=>m.type()==='error'&&errs.push(m.text()));
  await p.goto('http://localhost:8765/index.html',{waitUntil:'load'}).catch(e=>errs.push(String(e)));
  await p.waitForTimeout(5500);
  await p.screenshot({path:`${D}/out/${name}-hero.png`});
  const H=await p.evaluate(()=>document.documentElement.scrollHeight);
  for(let y=0;y<H;y+=h/2){await p.evaluate(y=>scrollTo(0,y),y);await p.waitForTimeout(250)}
  await p.waitForTimeout(1500);await p.evaluate(()=>scrollTo(0,0));await p.waitForTimeout(300);
  await p.screenshot({path:`${D}/out/${name}-full.png`,fullPage:true});
  const m=await p.evaluate(()=>({over:[...document.querySelectorAll('body *')].filter(e=>{const r=e.getBoundingClientRect();return r.width&&(r.right>innerWidth+1||r.left<-1)&&!e.closest('.svc-tabs')&&!e.closest('.hero::before')}).slice(0,8).map(e=>e.tagName+'.'+e.className),scrollW:document.documentElement.scrollWidth,innerW:innerWidth,H:document.documentElement.scrollHeight,tiny:[...document.querySelectorAll('a,button')].filter(e=>{const r=e.getBoundingClientRect();return r.width&&r.height<40&&getComputedStyle(e).display!=='none'}).map(e=>e.textContent.trim().slice(0,20))}));
  await p.addScriptTag({content:axe});
  const a=await p.evaluate(()=>axe.run().then(r=>r.violations.map(v=>({id:v.id,impact:v.impact,n:v.nodes.length,t:v.nodes[0].target[0]}))));
  console.log(name,JSON.stringify({...m,errs,axe:a}));
  await c.close();
 }
 await b.close();
})();
(async()=>{
 const b=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox']});
 for(const [name,w,h] of [['mobile',390,844],['pc',1440,900]] /* 확정: 360·430·768·1024도 추가 검증 필요 (HANDOFF §14) */){
  const c=await b.newContext({viewport:{width:w,height:h},deviceScaleFactor:name==='mobile'?2:1});const p=await c.newPage();
  await p.goto('http://localhost:8765/index.html');await p.waitForTimeout(800);
  for(const id of ['journey','services','chart','tools','faq']){await p.evaluate(i=>document.getElementById(i).scrollIntoView(),id);await p.waitForTimeout(1600);
   await p.locator('#'+id).screenshot({path:`${D}/out/${name}-${id}.png`});}
  await c.close();}
 await b.close();})();
