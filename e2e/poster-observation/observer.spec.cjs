const {test,expect}=require('@playwright/test');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const {observePosters}=require('../pr914-production/poster-observer.cjs');
const PNG=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0WQAAAAASUVORK5CYII=','base64');
let server,origin,requests;
test.beforeAll(async()=>{
  requests=[];
  server=http.createServer((req,res)=>{
    const url=new URL(req.url,'http://localhost');requests.push(url.pathname+url.search);
    if(url.pathname==='/'){
      const mode=url.searchParams.get('mode')||'healthy';
      const image=mode==='no-src'?'':`src="/agent-media/public/seven/poster.jpg?mode=${mode}&token=private-url-canary"`;
      res.writeHead(200,{'Content-Type':'text/html'});
      return res.end(`<style>img{width:96px;height:96px;background:#28aabb}body{font-family:sans-serif}</style><h1>LOCAL SYNTHETIC IMAGE TEST</h1><button data-home-agent="seven"><img ${image}></button>`);
    }
    if(url.pathname==='/api/agent-media/public'){res.writeHead(503,{'Content-Type':'application/json'});return res.end('{"error":"synthetic"}');}
    const mode=url.searchParams.get('mode')||'healthy';
    if(mode==='redirect'){res.writeHead(302,{Location:'/agent-media/public/seven/poster.jpg?mode=healthy'});return res.end();}
    if(mode==='network'){return req.socket.destroy();}
    if(mode==='404'){res.writeHead(404,{'Content-Type':'text/html'});return res.end('missing');}
    if(mode==='corrupt'){res.writeHead(200,{'Content-Type':'image/jpeg'});return res.end('invalid JPEG');}
    if(mode==='revalidate'&&req.headers['if-none-match']){res.writeHead(304,{'Cache-Control':'public, max-age=0, must-revalidate',ETag:'\"poster\"'});return res.end();}
    res.writeHead(200,{'Content-Type':mode==='mime'?'text/plain':'image/png','Content-Length':PNG.length,
      'Cache-Control':mode==='revalidate'?'public, max-age=0, must-revalidate':'public, max-age=3600',ETag:'\"poster\"','Set-Cookie':'secret-cookie=do-not-record'});res.flushHeaders();
    const delay=mode==='slow'?900:mode==='late'?5600:0;
    const timer=setTimeout(()=>res.end(PNG),delay);res.on('close',()=>clearTimeout(timer));
  });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
});
test.afterAll(async()=>{server.closeAllConnections();await new Promise(r=>server.close(r));});
async function setup(page,mode,options={}){
  const observation=await observePosters(page,{origin,...options});
  await page.goto(origin+'/?mode='+mode,{waitUntil:'domcontentloaded'});
  return observation;
}
const image=page=>page.locator('[data-home-agent="seven"] img');
const last=o=>o.report.snapshots.at(-1).images[0];
async function saved(o,info){await o.save(info.outputPath('observation.json'));o.dispose();return o.report;}
test('healthy image: HTTP, MIME, timing and actual visible decoding; no extra requests',async({page},info)=>{
  const start=requests.length,o=await setup(page,'healthy');
  await expect.poll(()=>image(page).evaluate(e=>e.complete&&e.naturalWidth>0)).toBe(true);
  await o.checkpoint('gate-pass');const img=last(o);expect(img.state).toBe('loaded');expect(img.visible).toBe(true);
  await expect.poll(()=>o.report.requests[0].finished).toBe(true);
  expect(o.report.requests[0]).toMatchObject({status:200,contentType:'image/png',cache:{policy:'public, max-age=3600'},transportError:null});
  expect(o.report.requests[0].timing.fetchToCompleteMs).toBeGreaterThanOrEqual(0);
  expect(requests.slice(start).filter(p=>p.startsWith('/agent-media/public/')).length).toBe(1);
  await page.screenshot({path:info.outputPath('healthy.png')});await saved(o,info);
});
test('ordinary latency remains loading then loaded; no incident decision',async({page},info)=>{
  const o=await setup(page,'slow');await o.checkpoint('pending');expect(last(o).state).toBe('loading');
  expect(o.report.requests[0].status).toBe(200);expect(o.report.requests[0].finished).toBe(false);
  await expect.poll(()=>image(page).evaluate(e=>e.complete&&e.naturalWidth>0)).toBe(true);
  await o.checkpoint('completed');expect(last(o)).toMatchObject({state:'loaded',loadedAfterObservedWait:true});
  expect(o.report.automaticIncidentDecision).toBe(false);await saved(o,info);
});
test('real 5-second failure stays failed even when image subsequently loads',async({page},info)=>{
  const o=await setup(page,'late');let originalGate='passed';const start=Date.now();
  try{await expect.poll(()=>image(page).evaluate(e=>e.complete&&e.naturalWidth>0),{timeout:5000}).toBe(true);}catch{originalGate='failed';}
  expect(originalGate).toBe('failed');expect(Date.now()-start).toBeGreaterThanOrEqual(4900);
  await o.checkpoint('five-second-gate-failed');expect(last(o).state).toBe('loading');
  // A separate observation of recovery never changes the original test verdict.
  await page.waitForFunction(()=>document.querySelector('img').naturalWidth>0);
  await o.checkpoint('late-recovery');expect(last(o)).toMatchObject({state:'loaded',loadedAfterObservedWait:true});
  expect(originalGate).toBe('failed');expect(o.report.requests[0].status).toBe(200);
  await page.screenshot({path:info.outputPath('late-recovery.png')});await saved(o,info);
});
for(const mode of ['404','corrupt','network'])test(`${mode}: distinguish HTTP status, transport and decode evidence`,async({page},info)=>{
  const o=await setup(page,mode);await expect.poll(()=>image(page).evaluate(e=>e.complete)).toBe(true);
  await o.checkpoint('image-error');expect(last(o).state).toBe('not-decoded');
  expect(o.report.snapshots.at(-1).imageEvents.some(e=>e.type==='error')).toBe(true);
  if(mode==='404')expect(o.report.requests[0]).toMatchObject({status:404,contentType:'text/html'});
  if(mode==='corrupt')expect(o.report.requests[0]).toMatchObject({status:200,contentType:'image/jpeg',transportError:null});
  if(mode==='network')expect(o.report.requests.some(r=>r.transportError!==null)).toBe(true);
  await saved(o,info);
});
test('MIME mismatch is evidence, not a false failure when the browser decodes it',async({page},info)=>{
  const o=await setup(page,'mime');await expect.poll(()=>image(page).evaluate(e=>e.naturalWidth>0)).toBe(true);
  await o.checkpoint('decoded');expect(last(o).state).toBe('loaded');expect(o.report.requests[0].contentType).toBe('text/plain');await saved(o,info);
});
test('redirect is recorded separately and a successful final image remains loaded',async({page},info)=>{
  const o=await setup(page,'redirect');await expect.poll(()=>image(page).evaluate(e=>e.naturalWidth>0)).toBe(true);
  await o.checkpoint('redirect-completed');expect(last(o).state).toBe('loaded');expect(o.report.requests.map(r=>r.status)).toEqual([302,200]);await saved(o,info);
});
test('cached/already loaded image does not require a new request to count as loaded',async({page},info)=>{
  await page.goto(origin+'/?mode=healthy');const o=await observePosters(page,{origin});await o.checkpoint('already-loaded');
  expect(last(o).state).toBe('loaded');expect(o.report.requests).toEqual([]);await saved(o,info);
});
test('hidden decoded image and unassigned src are not reported as visible success',async({page},info)=>{
  const o=await setup(page,'no-src');await o.checkpoint('not-assigned');expect(last(o).state).toBe('not-requested');
  await image(page).evaluate((e,url)=>{e.src=url;e.style.display='none';},origin+'/agent-media/public/seven/poster.jpg');
  await expect.poll(()=>image(page).evaluate(e=>e.naturalWidth>0)).toBe(true);await o.checkpoint('hidden');
  expect(last(o)).toMatchObject({state:'loaded',visible:false});await saved(o,info);
});
test('manifest error, console error, warning and uncaught exception are separate and private',async({page},info)=>{
  const o=await setup(page,'healthy');
  await page.evaluate(async()=>{await fetch('/api/agent-media/public');console.error('password=private-log-canary');console.warn('warning-canary');setTimeout(()=>{throw new TypeError('token=private-error-canary')},0);});
  await expect.poll(()=>o.report.browserErrors.some(e=>e.kind==='uncaught-exception')).toBe(true);
  expect(o.report.browserErrors.map(e=>e.kind)).toEqual(expect.arrayContaining(['console-error','console-warning','uncaught-exception']));
  expect(o.report.requests.find(r=>r.path==='/api/agent-media/public').status).toBe(503);
  const data=await saved(o,info),serialized=JSON.stringify(data);
  for(const secret of ['private-log-canary','private-error-canary','private-url-canary','secret-cookie','warning-canary'])expect(serialized).not.toContain(secret);
});
test('bounded observations preserve truncation counters and survive page closure',async({page},info)=>{
  const o=await setup(page,'healthy',{limit:2});
  await page.evaluate(async()=>{for(let i=0;i<8;i++){console.error('bounded '+i);await fetch('/agent-media/public/seven/poster.jpg?different='+i);}});
  await o.checkpoint('one');await o.checkpoint('two');await o.checkpoint('three');
  expect(o.report.requests).toHaveLength(2);expect(o.report.browserErrors).toHaveLength(2);
  expect(o.report.dropped.requests).toBeGreaterThan(0);expect(o.report.dropped.browserErrors).toBeGreaterThan(0);expect(o.report.dropped.snapshots).toBe(1);
  await page.close();const data=await saved(o,info);expect(data.collectionErrors).toContain('DOM_SNAPSHOT_UNAVAILABLE');
  expect(JSON.parse(fs.readFileSync(info.outputPath('observation.json'))).requests).toHaveLength(2);
});
test('unresponsive DOM snapshot cannot discard collected HTTP evidence',async({page},info)=>{
  const o=await setup(page,'healthy');await expect.poll(()=>image(page).evaluate(e=>e.naturalWidth>0)).toBe(true);
  const evaluate=page.evaluate.bind(page);page.evaluate=()=>new Promise(()=>{});
  try{await o.save(info.outputPath('observation.json'));}finally{page.evaluate=evaluate;o.dispose();}
  const data=JSON.parse(fs.readFileSync(info.outputPath('observation.json')));
  expect(data.collectionErrors).toContain('DOM_SNAPSHOT_UNAVAILABLE');expect(data.requests[0].status).toBe(200);
});

for(const [scenario,expected] of [['offscreen','offscreen'],['covered','occluded'],['partial','uncertain'],['opacity','uncertain'],
  ['transparent-cover','uncertain'],['animated','uncertain'],['pointer-none-cover','uncertain'],['viewport-clipped','uncertain'],
  ['no-io-v2','uncertain'],['css-hidden','css-hidden'],['tiny-cover','uncertain'],['pointer-none-image','uncertain']]) {
  test(`visibility separates ${scenario} from a successfully displayed image`,async({page},info)=>{
    const o=await setup(page,'healthy');await expect.poll(()=>image(page).evaluate(e=>e.naturalWidth>0)).toBe(true);
    await page.evaluate(scenario=>{
      const img=document.querySelector('img'),r=img.getBoundingClientRect();
      if(scenario==='offscreen')img.style.cssText='position:absolute;left:2000px;top:2000px';
      if(scenario==='viewport-clipped')img.style.cssText='position:fixed;left:-40px;top:200px';
      if(scenario==='opacity')img.parentElement.style.opacity='.5';
      if(scenario==='pointer-none-image')img.style.pointerEvents='none';
      if(scenario==='css-hidden')img.style.visibility='hidden';
      if(scenario==='animated')img.animate([{opacity:1},{opacity:.5}],{duration:2000,iterations:Infinity});
      if(scenario==='no-io-v2')window.IntersectionObserver=undefined;
      if(['covered','partial','transparent-cover','pointer-none-cover','tiny-cover'].includes(scenario)){
        const cover=document.createElement('div');cover.style.cssText='position:fixed;inset:0;z-index:9999;background:black';
        if(scenario==='partial')cover.style.cssText=`position:fixed;left:${r.x}px;top:${r.y}px;width:${r.width/2}px;height:${r.height}px;z-index:9999;background:black`;
        if(scenario==='tiny-cover')cover.style.cssText=`position:fixed;left:${r.x+25}px;top:${r.y+25}px;width:2px;height:2px;z-index:9999;background:black;pointer-events:none`;
        if(scenario==='transparent-cover')cover.style.opacity='.3';
        if(scenario==='pointer-none-cover')cover.style.pointerEvents='none';
        document.body.append(cover);
      }
    },scenario);
    await o.checkpoint(scenario);
    expect(last(o)).toMatchObject({state:'loaded',visible:false,presentationState:expected,visibilityVersion:2});
    expect(last(o)).toHaveProperty('cssVisibility');expect(last(o)).toHaveProperty('viewportIntersection');expect(last(o)).toHaveProperty('occlusion');
    if(scenario==='offscreen')expect(last(o).viewportIntersection.intersects).toBe(false);
    if(scenario==='covered')expect(last(o).occlusion.state).toBe('full');
    if(scenario==='partial')expect(last(o).occlusion.state).toBe('partial');
    await page.screenshot({path:info.outputPath(scenario+'.png')});await saved(o,info);
  });
}
test('visibility uncertainty never removes the legacy observation fields',async({page},info)=>{
  const o=await setup(page,'healthy');await expect.poll(()=>image(page).evaluate(e=>e.naturalWidth>0)).toBe(true);await o.checkpoint('compatibility');
  expect(last(o)).toMatchObject({visible:true,presentationState:'visible',state:'loaded',complete:true,naturalWidth:1,
    cssVisibility:{visible:true},viewportIntersection:{fullyInside:true},occlusion:{state:'clear'}});
  for(const key of ['role','agent','path','assigned','complete','naturalWidth','naturalHeight','state','visible','width','height','loadedAfterObservedWait'])expect(last(o)).toHaveProperty(key);
  expect(o.report.schemaVersion).toBe(1);await saved(o,info);
});
for(const mode of ['healthy','revalidate'])test(`real ${mode==='healthy'?'browser cache reuse':'HTTP 304 revalidation'} preserves loaded state`,async({page},info)=>{
  const start=requests.length,o=await setup(page,mode);await page.waitForLoadState('load');await o.checkpoint('first');
  await page.goto(origin+'/?mode='+mode+'&second=1');await o.checkpoint('second');
  expect(last(o).state).toBe('loaded');expect(last(o).visible).toBe(true);
  if(mode==='healthy'){
    expect(requests.slice(start).filter(p=>p.startsWith('/agent-media/public/'))).toHaveLength(1);
    expect(o.report.snapshots.at(-1).resourceTiming[0]).toMatchObject({transferSize:0,deliveryType:'cache'});
  }else{
    expect(requests.slice(start).filter(p=>p.startsWith('/agent-media/public/'))).toHaveLength(2);
    expect(o.report.requests.map(r=>r.status)).toEqual([200,304]);
  }
  await saved(o,info);
});
for(const failure of ['write','rename'])test(`optional ${failure} failure preserves a passing gate, prior artifact and cleanup`,async({browser},info)=>{
  const context=await browser.newContext(),page=await context.newPage(),o=await setup(page,'healthy');
  const file=info.outputPath('poster-observation.json');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,'prior-evidence');
  const original=fs[failure==='write'?'writeFileSync':'renameSync'],open=fs.openSync,stderr=process.stderr.write;
  let closed=false,gate=false,opened;const fallback=[];
  fs.openSync=(...args)=>{const fd=open(...args);if(String(args[0]).includes('.poster-observation-'))opened=fd;return fd;};
  process.stderr.write=function(chunk,...args){fallback.push(String(chunk));return stderr.call(this,chunk,...args);};
  const method=failure==='write'?'writeFileSync':'renameSync';
  fs[method]=(...args)=>{
    if(failure==='write'?typeof args[0]==='number':String(args[0]).includes('.poster-observation-')){const error=new Error('secret-path-token-do-not-log');error.code='ENOSPC';throw error;}
    return original(...args);
  };
  try{
    try{await expect.poll(()=>image(page).evaluate(e=>e.complete&&e.naturalWidth>0)).toBe(true);gate=true;}
    finally{await o.finish(file,async()=>{await context.close();closed=true;});}
  }finally{fs[method]=original;fs.openSync=open;process.stderr.write=stderr;await context.close();}
  expect(gate).toBe(true);expect(closed).toBe(true);expect(page.listenerCount('response')).toBe(0);
  expect(o.report).toMatchObject({saveStatus:'failed',diagnostics:[{code:'SAVE_FAILED',errorCode:'ENOSPC'}]});
  expect(JSON.stringify(o.report)).not.toContain('secret-path-token-do-not-log');
  expect(fallback.join('')).toContain('SAVE_FAILED');expect(fallback.join('')).not.toContain('secret-path-token-do-not-log');
  expect(()=>fs.fstatSync(opened)).toThrow();
  expect(fs.readFileSync(file,'utf8')).toBe('prior-evidence');
  expect(fs.readdirSync(path.dirname(file)).filter(f=>f.endsWith('.tmp'))).toEqual([]);
  fs.writeFileSync(info.outputPath('save-failure-diagnostic.json'),JSON.stringify(o.report,null,2));
});
test('real original 5-second image failure survives an independent save failure and closes resources',async({browser},info)=>{
  const context=await browser.newContext(),page=await context.newPage(),o=await setup(page,'corrupt');
  const original=fs.writeFileSync;let originalFailure,observedFailure,closed=false;
  fs.writeFileSync=(file,...args)=>{if(typeof file==='number'){const e=new Error('synthetic');e.code='EACCES';throw e;}return original(file,...args);};
  try{
    try{
      try{await expect.poll(()=>image(page).evaluate(e=>e.complete&&e.naturalWidth>0),{timeout:5000}).toBe(true);}
      catch(error){originalFailure=error;throw error;}
      finally{await o.finish(info.outputPath('poster-observation.json'),async()=>{await context.close();closed=true;});}
    }catch(error){observedFailure=error;}
  }finally{fs.writeFileSync=original;await context.close();}
  expect(originalFailure).toBeTruthy();expect(observedFailure).toBe(originalFailure);expect(closed).toBe(true);
  expect(o.report.diagnostics).toContainEqual({code:'SAVE_FAILED',errorCode:'EACCES'});
  expect(fs.readdirSync(info.outputDir).filter(f=>f.endsWith('.tmp'))).toEqual([]);
  fs.writeFileSync(info.outputPath('save-failure-diagnostic.json'),JSON.stringify(o.report,null,2));
});
test('successful atomic save removes temporary files and closes context',async({browser},info)=>{
  const context=await browser.newContext(),page=await context.newPage(),o=await setup(page,'healthy');
  const file=info.outputPath('observation.json');await o.finish(file,()=>context.close());
  expect(page.isClosed()).toBe(true);expect(page.listenerCount('response')).toBe(0);
  expect(JSON.parse(fs.readFileSync(file))).toMatchObject({schemaVersion:1,saveStatus:'saved',diagnostics:[]});
  expect(fs.readdirSync(info.outputDir).filter(f=>f.endsWith('.tmp'))).toEqual([]);
});
