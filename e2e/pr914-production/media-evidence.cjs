// Public resource evidence only: never serialize cookies, auth headers, or user input.
const {createHash}=require('node:crypto');
const allowedHeaders=['content-type','content-length','cache-control','etag','last-modified','age','content-encoding','x-cache'];
function collectMediaEvidence(page, origin) {
  const events=[], pending=[];
  const watched=url=>{const u=new URL(url);return u.origin===origin && (u.pathname==='/api/agent-media/public' || u.pathname.startsWith('/agent-media/public/') || /^\/(js|css)\/f2r_(home|agent_clip)/.test(u.pathname));};
  page.on('pageerror', e=>events.push({kind:'pageerror',message:e.message,time:Date.now()}));
  page.on('requestfailed', r=>{if(watched(r.url()))events.push({kind:'failed',url:r.url(),reason:r.failure()?.errorText,time:Date.now()});});
  page.on('response', r=>{
    if(!watched(r.url()))return;
    const h=r.headers(),item={kind:'response',url:r.url(),status:r.status(),time:Date.now(),headers:Object.fromEntries(allowedHeaders.filter(k=>h[k]).map(k=>[k,h[k]]))};
    events.push(item);
    pending.push((async()=>{try{
      // MP4 bodies may be streamed; only collect timings and headers for those.
      if(!new URL(r.url()).pathname.endsWith('.mp4')){
        const body=await r.body();item.bytes=body.length;item.sha256=createHash('sha256').update(body).digest('hex');
        if(new URL(r.url()).pathname==='/api/agent-media/public')item.manifest=JSON.parse(body.toString());
      }
      item.timing=r.request().timing();
    }catch(e){item.bodyError=e.message;}})());
  });
  return async()=>{
    const dom=await page.evaluate(()=>({
      images:[...document.querySelectorAll('[data-home-agent] img,#stagePoster')].map(e=>({html:e.outerHTML,src:e.getAttribute('src'),currentSrc:e.currentSrc,complete:e.complete,naturalWidth:e.naturalWidth,naturalHeight:e.naturalHeight,visibility:getComputedStyle(e).visibility,display:getComputedStyle(e).display})),
      videos:[...document.querySelectorAll('#homeStage video')].map(v=>({src:v.getAttribute('src'),currentSrc:v.currentSrc,poster:v.poster,readyState:v.readyState,networkState:v.networkState,error:v.error?.code,paused:v.paused,currentTime:v.currentTime})),
      stage:document.getElementById('homeStage')?.outerHTML,
      scripts:[...document.scripts].map(s=>s.src).filter(s=>/\/js\/f2r_/.test(s)),
      timing:performance.getEntriesByType('resource').filter(e=>/agent-media|f2r_home/.test(e.name)).map(e=>e.toJSON())
    })).catch(e=>({error:e.message}));
    await Promise.race([Promise.allSettled(pending),new Promise(r=>setTimeout(r,1000))]);
    return {events,dom};
  };
}
module.exports={collectMediaEvidence};
