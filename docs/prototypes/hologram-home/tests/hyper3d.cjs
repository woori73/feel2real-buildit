// Given/when/then browser integration. All media intercepted locally; no paid/API/DB calls.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),out=path.join(root,'evidence/hyper3d');fs.mkdirSync(out+'/captures',{recursive:true});
const results=[];const save=()=>fs.writeFileSync(out+'/results.json',JSON.stringify({environment:'Local Chromium / SwiftShader; mobile viewport emulation, not phone hardware. Diagnostic GLB and intercepted synthetic media only.',results},null,2));
const fixture=JSON.parse(fs.readFileSync(root+'/hyper3d/fixture.json'));let browser;
const record=(row,name)=>{row.checks.push({name,status:'PASS'});save();};
async function setup(width=390,{mode='normal',reduced=false}={}){
 const ctx=await browser.newContext({viewport:{width,height:width===1440?1050:width===360?800:width===430?932:844},isMobile:width<768,hasTouch:width<768,deviceScaleFactor:width<768?3:1,locale:'ko-KR',reducedMotion:reduced?'reduce':'no-preference'});
 const requests=[],errors=[];
 if(mode==='no-webgl')await ctx.addInitScript(()=>{const original=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(type,...args){return type.startsWith('webgl')?null:original.call(this,type,...args);};});
 await ctx.route('**/*',async route=>{const url=new URL(route.request().url());requests.push({method:route.request().method(),path:url.pathname});if(url.hostname!=='127.0.0.1')return route.abort();
  if(url.pathname==='/api/agent-media/public')return route.fulfill({json:{agents:{seven:{files:['front.webp','poster.jpg','idle.mp4','think.mp4','speak.mp4']},suni:{files:['front.webp','poster.jpg','idle.mp4','think.mp4','speak.mp4']}}}});
  if(url.pathname.startsWith('/agent-media/public/')){const file=url.pathname.endsWith('.mp4')?'test-only.mp4':url.pathname.split('/').pop();return route.fulfill({path:path.join(__dirname,'fixtures',file),contentType:file.endsWith('.mp4')?'video/mp4':file.endsWith('.webp')?'image/webp':'image/jpeg'});}
  if(url.pathname.endsWith('/fixture.json')){
   if(mode==='bad-hash')return route.fulfill({json:{...fixture,sha256:'0'.repeat(64)}});
   if(mode==='missing-clip')return route.fulfill({json:{...fixture,clips:{...fixture.clips,greeting:'absent'}}});
   if(mode==='malformed')return route.fulfill({json:{...fixture,sha256:crypto.createHash('sha256').update('invalid').digest('hex')}});
  }
  if(url.pathname.endsWith('/diagnostic.glb')){
   if(mode==='http404')return route.fulfill({status:404,body:'missing'});
   if(mode==='malformed')return route.fulfill({body:'invalid'});
   if(mode==='timeout'){await new Promise(r=>setTimeout(r,9500));return route.fulfill({status:504,body:'timeout'}).catch(()=>{});}
   if(mode==='slow')await new Promise(r=>setTimeout(r,600));
  }
  return route.continue();
 });
 const page=await ctx.newPage();page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:8765/?preview3d=1');await page.evaluate(()=>document.fonts.ready);
 await page.locator('.review-note').evaluate(e=>e.textContent='LOCAL TEST · 기하학 GLB / 합성 MP4 · 운영 캐릭터 아님');
 await page.waitForFunction(()=>[...document.querySelectorAll('video')].some(v=>!v.paused&&v.readyState>=2));
 return {ctx,page,requests,errors};
}
async function ready(page){await page.waitForFunction(()=>document.querySelector('#modelViewport').dataset.result==='ready');}
async function enable(page){await page.locator('[data-model-mode=diagnostic]').click();await ready(page);await page.locator('#homeStage').scrollIntoViewIfNeeded();}
async function shot(page,name){await page.screenshot({path:out+'/captures/'+name+'.png',fullPage:true});}
(async()=>{browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--enable-unsafe-swiftshader','--use-angle=swiftshader','--disable-dev-shm-usage']});
try{
 for(const width of [360,390,430,1440]){
  const row={width,scenario:'functional + synthetic performance',checks:[]};results.push(row);let current;
  try{current=await setup(width);const {page,requests,errors}=current;
   assert.equal(await page.locator('canvas').count(),0);assert.ok(!requests.some(r=>r.path.endsWith('viewer.bundle.js')));record(row,'default video mode has no WebGL bundle or canvas');
   await page.locator('[data-model-mode=approved]').click();await page.waitForFunction(()=>document.querySelector('#modelViewport').dataset.result==='fallback');assert.match(await page.locator('#modelStatus').innerText(),/승인된 세븐 모델 없음/);assert.equal(await page.locator('canvas').count(),0);record(row,'unapproved/missing model keeps video and never substitutes character');
   await enable(page);assert.equal(await page.locator('#modelViewport canvas').count(),1);assert.match(await page.locator('.model-badge').innerText(),/얼굴·의상 없음/);record(row,'actual hashed GLB decoded and rendered; diagnostic label visible');
   await page.waitForTimeout(2200);row.performance=await page.evaluate(()=>f2r3dDiagnostics());assert.ok(row.performance.stats.frames>=2);assert.ok(row.performance.metrics.triangles>0);assert.ok(row.performance.metrics.pixelRatio<=(width<768?1.5:2));record(row,'animated render frames advance and DPR cap enforced');
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth),0);record(row,'no horizontal overflow');
   await shot(page,width+'-3d-seven');
   // Given main's character selector, when switched, then both main and 3D show Suni greeting.
   await page.locator('[data-home-agent=suni]').click();await ready(page);await page.waitForFunction(()=>document.querySelector('#modelViewport').dataset.clip==='greeting');assert.equal(await page.locator('#modelViewport').getAttribute('data-agent'),'suni');assert.equal(await page.locator('#assistant-name').textContent(),'서수니');record(row,'character selection and greeting animation follow unchanged main');
   await shot(page,width+'-3d-suni-greeting');
   await page.waitForFunction(()=>document.querySelector('#modelViewport').dataset.clip==='idle');record(row,'greeting completion returns to idle');
   await page.evaluate(()=>{window.observed3D=[];new MutationObserver(()=>observed3D.push(document.querySelector('#modelViewport').dataset.clip)).observe(document.querySelector('#modelViewport'),{attributes:true,attributeFilter:['data-clip']});});
   await page.locator('#homeInput').fill('알 수 없는 질문 zzz');await page.locator('.send').click();await page.waitForFunction(()=>window.observed3D.includes('thinking')&&window.observed3D.includes('responding'));
   assert.equal(await page.locator('#homeChat .is-agent').last().locator('button').count(),3);record(row,'actual input produces thinking/responding and three unknown-question options');
   for(const button of await page.locator('.si-holo-tile').all()){await button.click();await page.locator('#homeChat .is-agent').last().filter({hasText:'240문항'}).waitFor();}
   assert.equal(await page.locator('.si-holo-tile').count(),6);assert.equal(await page.locator('#homeChat .is-agent').last().locator('a').first().getAttribute('href'),'https://open.kakao.com/o/sMf4ifRi');record(row,'all six application menus retain real main guidance and Kakao link');
   await page.locator('#homeWelcomeAgain').click();assert.match(await page.locator('#homeChat .is-agent').last().innerText(),/안녕하세요/);record(row,'replay welcome works with 3D active');
   await page.locator('[data-panel=naesin]').click();assert.equal(await page.locator('#naesin-panel a').last().getAttribute('href'),'/naesin-production-studio?division=HIGH');assert.equal(await page.locator('.prompt-row a').getAttribute('href'),'/dashboard');record(row,'studio and dashboard destinations preserved (destination backends untested)');
   await page.locator('[data-home-intent=studio]').first().click();await page.locator('#homeChat .is-agent').last().filter({hasText:'AI AGENT'}).waitFor();assert.ok((await page.locator('#homeChat').innerHTML()).includes('/ai-agent-studio'));record(row,'AI AGENT Studio guidance and destination preserved');
   await page.locator('.menu-trigger').click();assert.equal(await page.locator('dialog').evaluate(e=>e.matches(':modal')),true);await shot(page,width+'-3d-menu');await page.keyboard.press('Escape');record(row,'hologram service menu opens and Escape closes');
   await page.locator('[data-model-mode=video]').click();assert.equal(await page.locator('canvas').count(),0);assert.equal(await page.locator('#modelViewport').getAttribute('data-result'),'video');await page.waitForFunction(()=>[...document.querySelectorAll('video')].some(v=>v.classList.contains('is-on')&&!v.paused&&v.readyState>=2));await shot(page,width+'-video-return');record(row,'explicit video return disposes canvas and existing MP4 is playing');
   assert.deepEqual(errors,[]);assert.ok(requests.every(r=>r.method==='GET'));record(row,'no JavaScript errors or write requests');row.status='PASS';
  }catch(e){row.status='FAIL';row.failure=String(e);if(current)await shot(current.page,width+'-failure').catch(()=>{});}finally{row.requests=current?.requests;save();await current?.ctx.close();}
 }
 for(const mode of ['http404','bad-hash','malformed','missing-clip','no-webgl','timeout','context-loss','rapid-switch','reduced-motion','offscreen']){
  const row={scenario:mode,checks:[]};results.push(row);let current;
  try{current=await setup(390,{mode:mode==='rapid-switch'?'slow':mode,reduced:mode==='reduced-motion'});const {page,errors}=current;
   if(['http404','bad-hash','malformed','missing-clip','no-webgl','timeout'].includes(mode)){
    await page.locator('[data-model-mode=diagnostic]').click();await page.waitForFunction(()=>document.querySelector('#modelViewport').dataset.result==='fallback',null,{timeout:12000});assert.equal(await page.locator('canvas').count(),0);assert.equal(await page.locator('#modelViewport').evaluate(e=>getComputedStyle(e).opacity),'0');row.message=await page.locator('#modelStatus').innerText();record(row,'loading failure releases 3D and exposes existing video');
   }else if(mode==='context-loss'){
    await enable(page);await page.locator('canvas').evaluate(c=>c.getContext('webgl2').getExtension('WEBGL_lose_context').loseContext());await page.waitForFunction(()=>document.querySelector('#modelViewport').dataset.result==='fallback');assert.equal(await page.locator('canvas').count(),0);record(row,'real WebGL context loss returns to video');
   }else if(mode==='rapid-switch'){
    await page.locator('[data-model-mode=diagnostic]').click();await page.locator('[data-home-agent=suni]').click();await page.locator('[data-home-agent=seven]').click();await ready(page);await page.waitForTimeout(1000);assert.equal(await page.locator('canvas').count(),1);assert.equal(await page.locator('#modelViewport').getAttribute('data-agent'),'seven');record(row,'stale model loads cannot overwrite latest character');
   }else if(mode==='reduced-motion'){
    await enable(page);await page.waitForTimeout(300);const n=await page.evaluate(()=>f2r3dDiagnostics().stats.frames);await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>f2r3dDiagnostics().stats.frames),n);record(row,'reduced motion renders static pose without continuous frames');
   }else{
    await enable(page);await page.locator('footer').scrollIntoViewIfNeeded();await page.waitForTimeout(250);const n=await page.evaluate(()=>f2r3dDiagnostics().stats.frames);await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>f2r3dDiagnostics().stats.frames),n);await page.locator('#homeStage').scrollIntoViewIfNeeded();await page.waitForFunction(n=>f2r3dDiagnostics().stats.frames>n,n);record(row,'offscreen rendering pauses and resumes');
   }
   await shot(page,'edge-'+mode);assert.deepEqual(errors,[]);record(row,'no uncaught page errors');row.status='PASS';
  }catch(e){row.status='FAIL';row.failure=String(e);if(current)await shot(current.page,'edge-'+mode+'-failure').catch(()=>{});}finally{save();await current?.ctx.close();}
 }
}finally{await browser.close();save();}console.log(JSON.stringify(results.map(r=>({width:r.width,scenario:r.scenario,status:r.status,checks:r.checks.length,failure:r.failure})),null,2));if(results.some(r=>r.status!=='PASS'))process.exitCode=1;})();
