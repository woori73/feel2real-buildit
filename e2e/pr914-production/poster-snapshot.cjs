// Serialized into the test browser. Read-only, bounded, no network or page-content capture.
async function collectPosterSnapshot({origin, selector}) {
  const elements = [...document.querySelectorAll(selector)].slice(0,10);
  const paint = new Map();
  // IO v2 conservatively accounts for paint occlusion, including pointer-events:none overlays.
  // Missing support, no callback, partial coverage and visual effects never imply success.
  if (typeof IntersectionObserver !== 'undefined' && 'isVisible' in IntersectionObserverEntry.prototype && elements.length) {
    await new Promise(resolve => {
      let observer, timer;
      const done = () => { clearTimeout(timer); observer?.disconnect(); resolve(); };
      try {
        observer = new IntersectionObserver(entries => {
          for (const entry of entries) paint.set(entry.target, {visible:entry.isVisible, ratio:entry.intersectionRatio});
          if (paint.size === elements.length) done();
        }, {trackVisibility:true, delay:100});
        timer = setTimeout(done,300);
        elements.forEach(e => observer.observe(e));
      } catch { done(); }
    });
  }
  const safePath = url => { try { const u = new URL(url,location.href); return u.origin===origin && /^\/agent-media\/public\/(seven|suni)\/(poster\.jpg|front\.webp)$/.test(u.pathname) ? u.pathname : null; } catch { return null; } };
  const visualState = e => {
    let opacity=1, hidden=false, effects=false, animated=false;
    for (let node=e; node; node=node.parentElement) {
      const s=getComputedStyle(node); opacity*=Number(s.opacity);
      hidden ||= s.display==='none' || s.visibility!=='visible' || s.contentVisibility==='hidden';
      effects ||= s.filter!=='none' || s.transform!=='none' || s.clipPath!=='none' || s.maskImage!=='none' || s.mixBlendMode!=='normal';
      animated ||= node.getAnimations().some(a=>['running','pending'].includes(a.playState) || a.pending);
    }
    return {opacity,hidden,effects,animated};
  };
  const images=elements.map(e=>{
    const assigned=!!e.getAttribute('src'), r=e.getBoundingClientRect(), style=getComputedStyle(e), visual=visualState(e);
    const loaded=assigned && e.complete && e.naturalWidth>0;
    const state=!assigned?'not-requested':!e.complete?'loading':loaded?'loaded':'not-decoded';
    const left=Math.max(0,r.left), top=Math.max(0,r.top), right=Math.min(innerWidth,r.right), bottom=Math.min(innerHeight,r.bottom);
    const area=Math.max(0,right-left)*Math.max(0,bottom-top), ratio=r.width*r.height>0?area/(r.width*r.height):0;
    const viewportIntersection={intersects:area>0,fullyInside:area>0&&ratio>=0.999999,ratio};
    const cssVisibility={display:style.display,visibility:style.visibility,opacity:Number(style.opacity),effectiveOpacity:visual.opacity,
      visible:!visual.hidden&&visual.opacity>0&&!!(r.width&&r.height)};
    const points=[];
    if(area>0)for(const x of [.1,.5,.9])for(const y of [.1,.5,.9])points.push(document.elementFromPoint(left+(right-left)*x,top+(bottom-top)*y));
    const exposed=points.filter(n=>n===e).length, blockers=points.filter(n=>n&&n!==e);
    const p=paint.get(e);
    const opaqueCover=blockers.length===9 && blockers.every(n=>n===blockers[0]) && (()=>{
      const n=blockers[0], b=n.getBoundingClientRect(), s=getComputedStyle(n), v=visualState(n);
      const color=s.backgroundColor.match(/^rgba?\(([^)]+)\)$/), parts=color?.[1].split(',').map(Number);
      const opaque=parts && (parts.length===3 || parts[3]===1);
      return !n.contains(e) && opaque && v.opacity===1 && !v.hidden && !v.effects && !v.animated && s.borderRadius==='0px' &&
        b.left<=left && b.top<=top && b.right>=right && b.bottom>=bottom;
    })();
    const occlusion={state:!area?'not-tested':opaqueCover?'full':exposed>0&&exposed<9?'partial':
      p?.visible===true&&exposed===9?'clear':'unknown',sampleCount:points.length,exposedSamples:exposed,
      paintVisible:p?.visible??null,paintIntersectionRatio:p?.ratio??null};
    let presentationState;
    if(!loaded)presentationState='not-loaded';
    else if(!cssVisibility.visible)presentationState='css-hidden';
    else if(!area)presentationState='offscreen';
    else if(opaqueCover)presentationState='occluded';
    else if(!viewportIntersection.fullyInside || visual.opacity!==1 || visual.effects || visual.animated ||
      occlusion.state!=='clear' || p?.ratio!==1)presentationState='uncertain';
    else presentationState='visible';
    const agent=e.closest('[data-home-agent]')?.dataset.homeAgent;
    return {role:e.id==='stagePoster'?'stage-poster':'choice',agent:['seven','suni'].includes(agent)?agent:null,
      path:assigned?safePath(e.currentSrc||e.src):null,assigned,complete:e.complete,naturalWidth:e.naturalWidth,naturalHeight:e.naturalHeight,state,
      visible:presentationState==='visible',width:r.width,height:r.height,
      visibilityVersion:2,cssVisibility,viewportIntersection,occlusion,animated:visual.animated,visualEffects:visual.effects,presentationState};
  });
  const events=window.__f2rPosterObservationV1;
  return {timeOrigin:performance.timeOrigin,atMs:performance.now(),images,imageEvents:events?.events||[],imageEventsDropped:events?.dropped||0,
    resourceTiming:performance.getEntriesByType('resource').filter(r=>safePath(r.name)).slice(-200).map(r=>({path:safePath(r.name),durationMs:r.duration,
      transferSize:r.transferSize,encodedBodySize:r.encodedBodySize,decodedBodySize:r.decodedBodySize,workerStart:r.workerStart,
      deliveryType:['cache','navigational-prefetch'].includes(r.deliveryType)?r.deliveryType:'unknown'}))};
}
module.exports={collectPosterSnapshot};
