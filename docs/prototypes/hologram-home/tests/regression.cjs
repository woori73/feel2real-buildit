const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'), repo=path.resolve(root,'../../..');
const out=path.resolve(process.env.EVIDENCE_DIR || path.join(root,'evidence'));
fs.mkdirSync(path.join(out,'captures'),{recursive:true});
const results=[];
const save=()=>fs.writeFileSync(path.join(out,'restoration.json'),JSON.stringify(results,null,2));
const mainHTML=fs.readFileSync(path.join(repo,'src/main/resources/templates/public/landing.html'),'utf8');
const tileMatches=[...mainHTML.matchAll(/<button type="button" class="si-holo-tile" data-home-intent="exam"><b>(.*?)<\/b><small>(.*?)<\/small><\/button>/g)];
const tiles=tileMatches.map(m=>({title:m[1],subtitle:m[2]}));
const sourceRow={scope:'source fidelity',checks:[]};results.push(sourceRow);
function pass(row,name){row.checks.push({name,status:'PASS'});save();}
function check(row,name,fn){fn();pass(row,name);}
for(const name of ['f2r_home_agent.js','f2r_agent_clip_switch.js'])check(sourceRow,name+' byte-identical to main',()=>assert.equal(fs.readFileSync(path.join(root,'vendor',name),'utf8'),fs.readFileSync(path.join(repo,'src/main/resources/static/js',name),'utf8')));
const html=fs.readFileSync(path.join(root,'index.html'),'utf8');
check(sourceRow,'removed copy does not return',()=>assert.doesNotMatch(html,/human\s+intelligence|DesignCraft|PhotoCraft|고객센터|학습자료실/i));
check(sourceRow,'all displayed won prices exist in main',()=>{for(const price of html.match(/\d[\d,]*원/g)||[])assert.ok(mainHTML.includes(price),price);});
check(sourceRow,'six main tile labels preserved',()=>assert.equal(tiles.length,6));

(async()=>{
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
async function scenario(width,height,mode='valid',staff=false){
 const context=await browser.newContext({viewport:{width,height},isMobile:width<768,hasTouch:width<768,deviceScaleFactor:1,locale:'ko-KR',reducedMotion:'reduce'});
 const requests=[],errors=[];
 if(staff)await context.addInitScript(()=>sessionStorage.setItem('currentUser',JSON.stringify({role:'ADMIN'})));
 await context.route('**/*',route=>{
  const u=new URL(route.request().url());
  if(u.hostname!=='127.0.0.1')return route.abort();
  if(u.pathname==='/api/agent-media/public'||u.pathname==='/api/dashboard/agent-media'){
   requests.push({path:u.pathname,status:mode==='manifest-error'?503:200,fixture:true});
   if(mode==='manifest-error')return route.fulfill({status:503,body:'fixture unavailable'});
   const files=mode==='poster-only'?['poster.jpg']:['front.webp','poster.jpg','idle.mp4','think.mp4','speak.mp4'];
   return route.fulfill({json:{agents:{seven:{files},suni:{files}}}});
  }
  if(/^\/(agent-media\/public|dashboard\/agent-media)\//.test(u.pathname)){
   const name=u.pathname.split('/').pop();
   const status=mode==='broken-video'&&name.endsWith('.mp4')?404:200;
   requests.push({path:u.pathname,status,fixture:true});
   if(status===404)return route.fulfill({status:404,body:'fixture video absent'});
   return route.fulfill({path:path.join(__dirname,'fixtures',name.endsWith('.mp4')?'test-only.mp4':name),contentType:name.endsWith('.mp4')?'video/mp4':name.endsWith('.webp')?'image/webp':'image/jpeg'});
  }
  return route.continue();
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:8765/');await page.evaluate(()=>document.fonts.ready);
 await page.locator('#homeChat .is-agent').first().waitFor();
 // Mark every fixture screenshot prominently; these are never approved-character evidence.
 await page.locator('.review-note').evaluate(e=>e.textContent='TEST FIXTURE · 운영 캐릭터 아님 · 로컬 기능 검증');
 return {context,page,requests,errors};
}
async function shownClip(page,agent,clip){await page.waitForFunction(([agent,clip])=>[...document.querySelectorAll('video')].some(v=>v.classList.contains('is-on')&&v.getAttribute('src')?.endsWith('/'+agent+'/'+clip+'.mp4')&&v.readyState>=2&&!v.paused),[agent,clip]);}
async function shot(page,width,name){await page.screenshot({path:path.join(out,'captures',width+'-'+name+'.png'),fullPage:true});}
try{
 for(const [width,height] of [[360,800],[390,844],[430,932],[768,1024],[1440,1050]]){
  const row={width,height,scope:'local intercepted test media; NOT production media',checks:[],requests:[]};results.push(row);
  let current;
  try{
   current=await scenario(width,height);const {page}=current;
   await shownClip(page,'seven','idle');

   assert.match(await page.locator('#homeChat .is-agent').first().innerText(),/안녕하세요. 저는 세븐입니다/);
   assert.equal(await page.locator('#homeChat .is-agent').first().locator('button').count(),4);
   pass(row,'initial welcome and four actions');
   const t=await page.locator('video.is-on').last().evaluate(v=>v.currentTime);
   await page.waitForFunction(t=>[...document.querySelectorAll('video.is-on')].some(v=>v.currentTime>t+.15),t);
   pass(row,'actual fixture MP4 time advances');
   const portraits=await page.locator('[data-home-agent] img').evaluateAll(a=>a.map(i=>({src:i.getAttribute('src'),loaded:i.naturalWidth>0,opacity:getComputedStyle(i).opacity})));
   check(row,'main front.webp priority and unfiltered portrait opacity',()=>assert.ok(portraits.every(p=>p.src.endsWith('/front.webp')&&p.loaded&&p.opacity==='1')));
   const actual=await page.locator('.si-holo-tile').evaluateAll(a=>a.map(b=>({title:b.querySelector('b').textContent,subtitle:b.querySelector('small').textContent})));
   check(row,'all six menu titles and subtitles match main',()=>assert.deepEqual(actual,tiles));
   for(const tile of tiles){
    const before=await page.locator('#homeChat .is-agent').count();
    await page.locator('.si-holo-tile').filter({hasText:tile.title}).click();
    await page.waitForFunction(n=>document.querySelectorAll('#homeChat .is-agent').length>n,before);
    const reply=page.locator('#homeChat .is-agent').last();
    const text=await reply.innerText(),links=await reply.locator('a').evaluateAll(a=>a.map(x=>x.getAttribute('href')));
    check(row,'six-menu action: '+tile.title,()=>{assert.match(text,/20,000원/);assert.match(text,/240문항/);assert.match(text,/학교·학년·시험 범위·최근 기출·선생님 힌트/);assert.ok(links.includes('https://open.kakao.com/o/sMf4ifRi'));assert.ok(links.includes('mailto:woorinaesin@naver.com'));assert.ok(links.includes('#exam-hit'));});
   }
   await shot(page,width,'six-intake-actions');
   await page.locator('#homeInput').fill('오늘 날씨');await page.locator('#homeForm button').click();
   await page.locator('#homeChat .is-agent').last().filter({hasText:'아래에서 골라 주세요'}).waitFor();
   const choices=page.locator('#homeChat .is-agent').last();

   assert.equal(await choices.locator('button').count(),3);
   pass(row,'unknown question exposes main choices');
   await shot(page,width,'unknown-choices');
   await choices.getByRole('button',{name:'기말고사 이용권 신청',exact:true}).click();
   await page.locator('#homeChat .is-agent').last().filter({hasText:'학교·학년·시험 범위'}).waitFor();
   pass(row,'unknown choice performs intake guidance');
   await page.locator('[data-home-agent="suni"]').click();
   await shownClip(page,'suni','speak');
   assert.match(await page.locator('#homeChat .is-agent').last().innerText(),/서수니입니다. Feel to Real/);
   const speaking=await page.locator('video.is-on').last().evaluate(v=>({muted:v.muted,loop:v.loop,src:v.getAttribute('src')}));
   check(row,'Suni selection starts unmuted nonloop greeting clip',()=>{assert.equal(speaking.muted,false);assert.equal(speaking.loop,false);assert.match(speaking.src,/suni\/speak.mp4/);});
   await shot(page,width,'suni-fixture-video');
   await shownClip(page,'suni','idle');
   pass(row,'greeting end returns to idle');
   await page.locator('[data-home-agent="seven"]').click();await shownClip(page,'seven','speak');
   assert.match(await page.locator('#homeChat .is-agent').last().innerText(),/세븐입니다. Feel to Real/);

   assert.match(await page.locator('#homeChat').innerText(),/오늘 날씨/);
   pass(row,'Seven greeting and conversation retention');
   await page.locator('#homeWelcomeAgain').click();
   assert.equal(await page.locator('#homeChat .is-agent').last().locator('button').count(),4);
   await page.locator('#homeChat .is-agent').last().getByRole('button',{name:'나중에',exact:true}).click();
   await page.reload();await page.locator('#homeChat .is-agent').first().filter({hasText:'다시 오셨네요'}).waitFor();
   await page.locator('#homeWelcomeAgain').click();assert.equal(await page.locator('#homeChat .is-agent').last().locator('button').count(),4);
   pass(row,'later persists and replay restores initial choices');
   await page.locator('#homeChat .is-agent').last().getByRole('button',{name:'학원 운영 AI 비서'}).click();
   await page.locator('#homeChat .is-agent').last().getByRole('link',{name:'로그인하고 시작하기'}).waitFor();

   assert.equal(await page.locator('#homeChat .is-agent').last().locator('a').getAttribute('href'),'/?login=required&returnUrl=%2Fdashboard');
   pass(row,'anonymous work handoff preserves login returnUrl');
   await page.locator('.prompt-row [data-home-intent="studio"]').click();
   await page.locator('#homeChat .is-agent').last().getByRole('link',{name:'AI AGENT 제작협업실 열기'}).waitFor();
   assert.equal(await page.locator('#homeChat .is-agent').last().locator('a').first().getAttribute('href'),'/ai-agent-studio');
   pass(row,'Studio main guidance and URL retained');
   check(row,'no script errors in playable fixture flow',()=>assert.deepEqual(current.errors,[]));
   row.requests.push(...current.requests);await current.context.close();
   current=await scenario(width,height,'broken-video');
   await current.page.waitForFunction(()=>{const p=document.getElementById('stagePoster');return !p.hidden&&p.naturalWidth>0;});
   assert.match(await current.page.locator('#stagePoster').getAttribute('src'),/seven\/poster.jpg$/);
   assert.equal(await current.page.locator('#homeStage').getAttribute('data-media'),'missing');
   pass(row,'failed MP4 displays Seven poster');
   await current.page.locator('[data-home-agent="suni"]').click();
   await current.page.waitForFunction(()=>{const p=document.getElementById('stagePoster');return !p.hidden&&p.naturalWidth>0&&p.src.includes('/suni/poster.jpg');});
   assert.equal(await current.page.locator('#homeName').textContent(),'서수니');
   await shot(current.page,width,'failed-video-poster');
   pass(row,'failed Suni clip never retains Seven poster');
   check(row,'no script errors in failure flow',()=>assert.deepEqual(current.errors,[]));
   row.requests.push(...current.requests);row.status='PASS';
  }catch(error){row.status='FAIL';row.error=String(error);if(current)await shot(current.page,width,'failure').catch(()=>{});}
  finally{if(current)await current.context.close();save();}
 }
 // Edge cases use the real unchanged main modules, only the transport is intercepted.
 for(const [mode,staff] of [['poster-only',false],['manifest-error',false],['valid',true]]){
  const row={scope:'edge '+mode+(staff?' simulated ADMIN':''),checks:[]};results.push(row);let s;
  try{s=await scenario(390,844,mode,staff);
   if(mode==='poster-only'){
    await s.page.waitForFunction(()=>!document.getElementById('stagePoster').hidden);
    assert.match(await s.page.locator('#stagePoster').getAttribute('src'),/seven\/poster.jpg$/);
    await s.page.locator('[data-home-agent="suni"]').click();await s.page.locator('#homeChat .is-agent').last().filter({hasText:'서수니입니다. 이어서 도와드릴게요.'}).waitFor();
    pass(row,'poster-only manifest and text-only greeting');
   }else if(mode==='manifest-error'){
    await s.page.locator('#mediaStatus').waitFor();assert.equal(await s.page.locator('#stagePoster').getAttribute('src'),null);
    pass(row,'manifest failure leaves honest missing-media state');
   }else{
    await shownClip(s.page,'seven','idle');assert.ok(s.requests.some(x=>x.path==='/api/dashboard/agent-media'));
    assert.ok(s.requests.some(x=>x.path==='/dashboard/agent-media/seven/idle.mp4'));
    await s.page.locator('#homeInput').fill('학생 출결 관리');await s.page.locator('#homeForm button').click();
    await s.page.locator('#homeChat .is-agent').last().getByRole('button',{name:'AI 비서 화면으로 이어 가기'}).waitFor();
    await s.context.route('**/dashboard',route=>route.fulfill({body:'TEST ONLY handoff destination',contentType:'text/html'}));
    await s.page.locator('#homeChat .is-agent').last().getByRole('button',{name:'AI 비서 화면으로 이어 가기'}).click();
    await s.page.waitForURL('**/dashboard');assert.equal(await s.page.evaluate(()=>sessionStorage.getItem('f2r.pendingAsk')),'학생 출결 관리');
    pass(row,'private media branch and pendingAsk handoff without DB');
   }
   row.requests=s.requests;row.status='PASS';
  }catch(e){row.status='FAIL';row.error=String(e);}finally{if(s)await s.context.close();save();}
 }
}finally{await browser.close();save();}
console.log(JSON.stringify(results.map(r=>({scope:r.scope,width:r.width,status:r.status||'PASS',checks:r.checks.length,error:r.error}))));
if(results.some(r=>r.status==='FAIL'))process.exitCode=1;
})();
