// Anonymous GET-only diagnosis. Never deploys, authenticates, or submits forms.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs');
const crypto = require('node:crypto');
const origin = 'https://feel2real.co.kr';
const out = 'image-evidence';
fs.mkdirSync(out, {recursive:true});
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
function section(html) {
  const start = html.search(/<section\b[^>]*\bid="aiHome"/);
  if (start < 0) throw new Error('Missing aiHome');
  const tags = /<\/?section\b[^>]*>/g; tags.lastIndex = start;
  let depth=0, tag;
  while ((tag=tags.exec(html))) { depth += tag[0].startsWith('</') ? -1 : 1; if (!depth) return [start,tags.lastIndex]; }
  throw new Error('Unclosed aiHome');
}
(async () => {
  let failed=false;
  const browser=await chromium.launch({headless:true, ...(process.env.CHROMIUM_PATH ? {executablePath:process.env.CHROMIUM_PATH} : {})});
  const template=fs.readFileSync('src/main/resources/templates/public/landing.html','utf8');
  const [a,b]=section(template);
  for (const mode of ['baseline','candidate']) for (const width of [1440,360,390,430]) {
    const context=await browser.newContext({viewport:{width,height:900},isMobile:width<500,hasTouch:width<500,locale:'ko-KR'});
    const page=await context.newPage(), events=[], pending=[], snapshots=[];
    const stamp=()=>Date.now();
    page.on('pageerror', e=>events.push({kind:'pageerror',message:e.message,time:stamp()}));
    page.on('requestfailed', r=>events.push({kind:'failed',path:new URL(r.url()).pathname,error:r.failure()?.errorText,time:stamp()}));
    page.on('response', r=>{
      const u=new URL(r.url());
      if (u.origin!==origin || !(/agent-media|f2r_home|f2r_agent_clip/.test(u.pathname) || u.pathname==='/')) return;
      pending.push((async()=>{
        const headers=r.headers();
        const item={kind:'response',path:u.pathname+u.search,status:r.status(),time:stamp(),headers:Object.fromEntries(['content-type','content-length','cache-control','age','etag','last-modified','content-encoding','x-cache'].filter(k=>headers[k]).map(k=>[k,headers[k]])),timing:r.request().timing()};
        try { const body=await r.body(); item.sha256=hash(body);item.bytes=body.length;
          if (/^\/(js\/f2r_|css\/f2r_)/.test(u.pathname)) fs.writeFileSync(`${out}/${mode}-${width}-${u.pathname.split('/').pop()}`,body);
          if(u.pathname==='/api/agent-media/public')item.manifest=JSON.parse(body.toString());
        } catch(e){item.bodyError=e.message;}
        events.push(item);
      })());
    });
    await context.route('**/*', async route=>{
      const req=route.request(),u=new URL(req.url());
      if(req.method()!=='GET') return route.abort();
      if(u.origin!==origin) return route.continue();
      if(mode==='candidate' && u.pathname==='/') {
        const response=await route.fetch();let html=await response.text();
        const [s,e]=section(html);html=html.slice(0,s)+template.slice(a,b)+html.slice(e);
        html=html.replace(/(?=<script src="\/js\/site_events\.js)/, '<link rel="stylesheet" href="/css/f2r_home_compact.css?v=20261028-1"><script src="/js/f2r_home_presentation.js?v=20261028-1" defer></script>');
        return route.fulfill({response,body:html});
      }
      if(mode==='candidate' && ['/css/f2r_home_compact.css','/js/f2r_home_presentation.js'].includes(u.pathname)) return route.fulfill({path:'src/main/resources/static'+u.pathname});
      return route.continue();
    });
    const snapshot=async label=>snapshots.push({label,time:stamp(),...(await page.evaluate(()=>({
      images:[...document.querySelectorAll('#homeStage img,[data-home-agent] img')].map(e=>({outer:e.outerHTML,currentSrc:e.currentSrc,complete:e.complete,naturalWidth:e.naturalWidth,visibility:getComputedStyle(e).visibility})),
      stage:document.getElementById('homeStage')?.outerHTML,
      scripts:[...document.scripts].map(s=>s.src).filter(Boolean),
      resourceTiming:performance.getEntriesByType('resource').filter(e=>/agent-media|f2r_home/.test(e.name)).map(e=>e.toJSON())
    })))});
    try {
      await page.goto(origin,{waitUntil:'domcontentloaded'});
      await page.locator('#homeChat .is-agent').first().waitFor();
      await snapshot('initial');
      for (const agent of ['seven','suni']) {
        await page.locator(`[data-home-agent="${agent}"]`).click();
        const start=Date.now();
        try {
          await expect.poll(()=>page.locator(`[data-home-agent="${agent}"] img`).evaluate(e=>e.complete&&e.naturalWidth>0),{timeout:5000}).toBe(true);
          events.push({kind:'portrait-check',agent,status:'PASS',elapsedMs:Date.now()-start});
        } catch(e) { events.push({kind:'portrait-check',agent,status:'FAIL',elapsedMs:Date.now()-start,message:e.message}); }
        await snapshot(agent);
        await page.waitForTimeout(1000);
        await page.evaluate(()=>window.scrollTo(0,0));
        await page.screenshot({path:`${out}/${mode}-${width}-${agent}.png`,fullPage:true});
      }
    } catch(e) { events.push({kind:'diagnostic-error',message:e.message}); }
    finally {
      await Promise.allSettled(pending);
      if(events.some(e=>e.kind==='diagnostic-error'||e.kind==='pageerror'||e.status==='FAIL'))failed=true;
      fs.writeFileSync(`${out}/${mode}-${width}.json`,JSON.stringify({mode,width,browser:browser.version(),productionModified:false,events,snapshots},null,2));
      await context.close();
    }
  }
  await browser.close();
  if(failed)process.exitCode=1;
})();
