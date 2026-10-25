import * as T from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {SVGRenderer} from 'three/addons/renderers/SVGRenderer.js';

export async function mount(stage,{onFailure}={}) {
  let renderer,compat=false;
  try{renderer=new T.WebGLRenderer({alpha:false,antialias:false,powerPreference:'low-power'});}
  catch(_){renderer=new SVGRenderer();renderer.setQuality('low');compat=true;}
  renderer.setPixelRatio?.(Math.min(devicePixelRatio,innerWidth<600?1:1.25));
  renderer.setClearColor(0x050d17);renderer.outputColorSpace=T.SRGBColorSpace;
  const scene=new T.Scene();scene.fog=new T.Fog(0x050d17,9,24);
  const camera=new T.PerspectiveCamera(38,1,.1,45);
  const ambient=new T.HemisphereLight(0xb9e7ff,0x121630,2);scene.add(ambient);
  const key=new T.PointLight(0x5de1ff,42,16,2);key.position.set(0,3.8,3.2);scene.add(key);
  const fill=new T.PointLight(0x9d80ff,24,14,2);fill.position.set(-4,2,-1);scene.add(fill);
  // SVG uses a non-physical light model. Keep its compatibility exposure restrained.
  if(compat){ambient.intensity=.45;key.intensity=.35;fill.intensity=.15;scene.add(new T.AmbientLight(0xb7d1e0,.35));renderer.setPrecision(2);}
  let gltf;
  try {gltf=await new GLTFLoader().loadAsync('/models/f2r-space/f2r-environment.glb');}
  catch(e) {renderer.dispose?.();throw e;}
  const env=gltf.scene;scene.add(env);
  let disposed=false,visible=true,raf=0,last=0,frames=0,activeMs=0,renderMs=0;
  const host=document.createElement('div');host.className='f2r-space-canvas';host.setAttribute('aria-hidden','true');
  host.append(renderer.domElement);stage.prepend(host);
  if(compat)stage.classList.add('f2r-space-compat');
  stage.dataset.spaceRenderer=compat?'svg-compatibility':'webgl2';
  const originalVideoStyles=new Map([...stage.querySelectorAll('video')].map(v=>[v,v.getAttribute('style')]));
  const labels=document.createElement('div');labels.className='f2r-space-labels';stage.append(labels);
  const buttons=[...stage.querySelectorAll('[data-home-intent],[data-flow]')];
  const homes=buttons.map(button=>({button,parent:button.parentNode,next:button.nextSibling}));
  buttons.forEach((button,i)=>{button.dataset.spatialPanel=String(i);labels.append(button);});
  const rings=[],panels=[],lights=[],resources=new Set();
  env.traverse(o=>{
    if(o.isMesh){
      resources.add(o.geometry);resources.add(o.material);
      if(o.material?.isMeshBasicMaterial)lights.push(o.material);
      else if(compat){
        // Flat shading avoids thousands of per-face SVG style changes; WebGL keeps PBR lighting.
        o.material=new T.MeshBasicMaterial({color:o.material.color,transparent:o.material.transparent,opacity:o.material.opacity,side:o.material.side});resources.add(o.material);
      }
    }
    if(/^orbit_/.test(o.name))rings.push(o);
    if(/^service_\d$/.test(o.name))panels[Number(o.name.slice(-1))]=o;
  });
  const videoMaterials=new Map(),geo=new T.PlaneGeometry(7.35,4.2),emptyMaterial=new T.MeshBasicMaterial({visible:false}),screen=new T.Mesh(geo,emptyMaterial);
  screen.position.set(0,2.25,.15);screen.renderOrder=2;scene.add(screen);
  const posterMaterial=new T.MeshBasicMaterial({transparent:true,toneMapped:false});
  let posterURL='',posterTexture=null,posterGeneration=0;
  function prepareMaterial(mat) {
    // Feather only the backdrop edges. Approved RGB pixels in the face stay unchanged.
    mat.onBeforeCompile=s=>{s.fragmentShader=s.fragmentShader.replace('#include <alphamap_fragment>',`#include <alphamap_fragment>
      float side=smoothstep(0.10,0.31,vMapUv.x)*smoothstep(0.10,0.31,1.0-vMapUv.x);
      float bottom=smoothstep(0.0,0.15,vMapUv.y);
      diffuseColor.a*=side*bottom;`);};
    return mat;
  }
  prepareMaterial(posterMaterial);
  function media() {
    const videos=[...stage.querySelectorAll('video')];
    const v=videos.find(x=>x.classList.contains('is-on')) || videos[0];
    if(stage.dataset.media!=='ready'||!v?.getAttribute('src')){screen.visible=false;stage.dataset.spaceVideo='missing';return;}
    if(compat){screen.visible=false;stage.dataset.spaceVideo=v.paused?'paused':'playing';return;}
    if(v.readyState>=2) {
      if(!videoMaterials.has(v)) {
        const tex=new T.VideoTexture(v);tex.colorSpace=T.SRGBColorSpace;tex.minFilter=T.LinearFilter;tex.magFilter=T.LinearFilter;tex.generateMipmaps=false;
        videoMaterials.set(v,prepareMaterial(new T.MeshBasicMaterial({map:tex,transparent:true,toneMapped:false})));
      }
      screen.material=videoMaterials.get(v);screen.visible=true;stage.dataset.spaceVideo=v.paused?'paused':'playing';
    } else if(v.poster) {
      if(v.poster!==posterURL){
        posterURL=v.poster;const current=++posterGeneration;
        new T.TextureLoader().load(v.poster,t=>{if(disposed||current!==posterGeneration){t.dispose();return;}posterTexture?.dispose();posterTexture=t;t.colorSpace=T.SRGBColorSpace;posterMaterial.map=t;posterMaterial.needsUpdate=true;});
      }
      screen.material=posterMaterial;screen.visible=!!posterTexture;
    }else screen.visible=false;
  }
  const pointer=new T.Vector2(),smooth=new T.Vector2(),raycaster=new T.Raycaster(),projection=new T.Vector3();
  let selected=-1,drag=null,tap=null,targetZoom=0,zoom=0;
  function dimensions() {
    const w=stage.clientWidth,h=stage.clientHeight;if(w<1||h<1)return;
    renderer.setPixelRatio?.(Math.min(devicePixelRatio,w<600?1:1.25));
    camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h,false);
  }
  const resize=new ResizeObserver(dimensions);resize.observe(stage);dimensions();
  function cameraUpdate(dt) {
    smooth.lerp(pointer,1-Math.exp(-dt*5));zoom+=(targetZoom-zoom)*(1-Math.exp(-dt*5));
    // Keep all six panels in frame, including narrow 390px viewports.
    const mobile=stage.clientWidth<600;
    const distance=mobile?Math.max(11.2,11/camera.aspect):Math.max(9.6,15/camera.aspect);
    camera.position.set(smooth.x*.5,3.25+smooth.y*.26,distance-zoom*.4);
    camera.lookAt(smooth.x*.13,1.85+smooth.y*.06,0);camera.updateMatrixWorld();
  }
  function project() {
    const w=stage.clientWidth,h=stage.clientHeight;
    panels.forEach((p,i)=>{
      const button=buttons[i];if(!button)return;
      p.getWorldPosition(projection);projection.project(camera);
      button.style.left=((projection.x*.5+.5)*w)+'px';button.style.top=((-projection.y*.5+.5)*h)+'px';
      button.style.width=(innerWidth<600?Math.min(102,w*.27):Math.min(148,w*.21))+'px';
      button.style.transform='translate(-50%,-50%)';
    });
    if(compat){
      const top=new T.Vector3(-3.675,4.35,.15).project(camera),bottom=new T.Vector3(3.675,.15,.15).project(camera);
      for(const v of stage.querySelectorAll('video')){
        v.style.left=((top.x*.5+.5)*w)+'px';v.style.top=((-top.y*.5+.5)*h)+'px';
        v.style.width=((bottom.x-top.x)*.5*w)+'px';v.style.height=((top.y-bottom.y)*.5*h)+'px';
      }
    }
  }
  function pick(event) {
    const rect=stage.getBoundingClientRect();
    raycaster.setFromCamera(new T.Vector2((event.clientX-rect.left)/rect.width*2-1,-(event.clientY-rect.top)/rect.height*2+1),camera);
    const hit=raycaster.intersectObjects(panels,true)[0];if(!hit)return -1;
    let o=hit.object;while(o&&!/^service_\d$/.test(o.name))o=o.parent;return o?Number(o.name.slice(-1)):-1;
  }
  function pointerDown(e){if(e.target.closest('button'))return;drag={x:e.clientX,y:e.clientY,px:pointer.x,py:pointer.y};tap={x:e.clientX,y:e.clientY};}
  function pointerMove(e){
    if(drag){pointer.set(T.MathUtils.clamp(drag.px+(e.clientX-drag.x)/120,-1,1),T.MathUtils.clamp(drag.py+(e.clientY-drag.y)/180,-1,1));}
    else if(e.pointerType==='mouse'){
      const r=stage.getBoundingClientRect();pointer.set((e.clientX-r.left)/r.width*2-1,(e.clientY-r.top)/r.height*2-1);
    }
  }
  function pointerUp(e){if(tap&&Math.hypot(e.clientX-tap.x,e.clientY-tap.y)<8){const i=pick(e);if(i>=0)buttons[i]?.click();}drag=null;tap=null;}
  function pointerCancel(){drag=null;tap=null;}
  function pointerLeave(){if(!drag)pointer.set(0,0);}
  function focusPanel(e){const b=e.target.closest('[data-spatial-panel]');if(!b)return;selected=Number(b.dataset.spatialPanel);targetZoom=.6;}
  function blurPanel(){selected=-1;targetZoom=0;}
  stage.addEventListener('pointerdown',pointerDown);stage.addEventListener('pointermove',pointerMove);
  window.addEventListener('pointerup',pointerUp);stage.addEventListener('pointercancel',pointerCancel);stage.addEventListener('pointerleave',pointerLeave);
  labels.addEventListener('pointerover',focusPanel);labels.addEventListener('focusin',focusPanel);labels.addEventListener('pointerleave',blurPanel);labels.addEventListener('focusout',blurPanel);
  const color=new T.Color(),stateColors={idle:0x5de1ff,listening:0xbb8aff,thinking:0xffca77,speaking:0xbb8aff,greeting:0x5de1ff,done:0x7df0b2,error:0xff9999};
  function publishMetrics(ms){
    stage.dataset.spaceFrames=String(frames);stage.dataset.spaceFps=(frames*1000/Math.max(1,activeMs)).toFixed(1);
    stage.dataset.spaceDrawCalls=String(renderer.info?.render?.calls||0);stage.dataset.spaceTriangles=String(renderer.info?.render?.triangles||renderer.info?.render?.faces||0);
    stage.dataset.spaceRenderMs=(renderMs/Math.max(frames,1)).toFixed(2);
    stage.dataset.spaceCamera=camera.position.toArray().map(x=>x.toFixed(2)).join(',');
  }
  function frame(ms) {
    raf=0;if(disposed||!visible||document.hidden)return;
    raf=requestAnimationFrame(frame);if(ms-last<1000/(compat?8:30))return;
    const dt=Math.min(.08,(ms-last)/1000||.033);activeMs+=ms-last;last=ms;
    const state=stage.dataset.state||'idle';color.setHex(stateColors[state]||stateColors.idle);key.color.lerp(color,.12);
    for(const m of lights)m.color.lerp(color,.07);
    rings.forEach((r,i)=>{r.rotation.z+=dt*(i%2?-1:1)*(state==='thinking'?.55:.12)*(i+1);r.rotation.y=Math.sin(ms*.00017+i)*.13;});
    panels.forEach((p,i)=>{
      const mobile=stage.clientWidth<600;p.position.x=(i<3?-1:1)*(mobile?2.75:3.45);
      p.position.y=mobile?3.55-(i%3)*1.3:3.1-(i%3)*.93;p.scale.x=mobile?.8:1;
      p.position.z=T.MathUtils.lerp(p.position.z,i===selected?.7:.45,.12);
    });
    media();cameraUpdate(dt);project();const then=performance.now();renderer.render(scene,camera);renderMs+=performance.now()-then;frames++;
    if(frames%15===0)publishMetrics(ms);
  }
  const pausedMedia=new Map();
  function run(){if(!disposed&&visible&&!document.hidden&&!raf){
    for(const [v,src] of pausedMedia)if(v.getAttribute('src')===src&&!v.ended)v.play()?.catch(()=>{});pausedMedia.clear();
    stage.dataset.spacePaused='false';last=performance.now();raf=requestAnimationFrame(frame);
  }}
  function pause(){cancelAnimationFrame(raf);raf=0;stage.dataset.spacePaused='true';
    for(const v of stage.querySelectorAll('video'))if(!v.paused){pausedMedia.set(v,v.getAttribute('src'));v.pause();}
  }
  const intersection=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;if(visible)run();else pause();},{threshold:0});intersection.observe(stage);
  function visibility(){document.hidden?pause():run();}document.addEventListener('visibilitychange',visibility);
  function contextLost(e){e.preventDefault();onFailure?.();}renderer.domElement.addEventListener('webglcontextlost',contextLost);
  stage.classList.add('f2r-space-ready');stage.dataset.space='3d';stage.dataset.spaceReason='';run();
  return {reset(){pointer.set(0,0);targetZoom=0;},dispose(){
    if(disposed)return;disposed=true;++posterGeneration;pause();resize.disconnect();intersection.disconnect();
    if(!document.hidden)for(const [v,src] of pausedMedia)if(v.getAttribute('src')===src&&!v.ended)v.play()?.catch(()=>{});pausedMedia.clear();
    document.removeEventListener('visibilitychange',visibility);stage.removeEventListener('pointerdown',pointerDown);stage.removeEventListener('pointermove',pointerMove);
    window.removeEventListener('pointerup',pointerUp);stage.removeEventListener('pointercancel',pointerCancel);stage.removeEventListener('pointerleave',pointerLeave);
    labels.removeEventListener('pointerover',focusPanel);labels.removeEventListener('focusin',focusPanel);labels.removeEventListener('pointerleave',blurPanel);labels.removeEventListener('focusout',blurPanel);
    renderer.domElement.removeEventListener('webglcontextlost',contextLost);
    // Reverse restores original order even though nextSibling was moved too.
    [...homes].reverse().forEach(({button,parent,next})=>{parent.insertBefore(button,next?.parentNode===parent?next:null);button.removeAttribute('style');delete button.dataset.spatialPanel;});
    host.remove();labels.remove();stage.classList.remove('f2r-space-ready','f2r-space-compat');
    for(const [v,style] of originalVideoStyles)style===null?v.removeAttribute('style'):v.setAttribute('style',style);
    for(const m of videoMaterials.values()){m.map.dispose();m.dispose();}posterTexture?.dispose();posterMaterial.dispose();geo.dispose();
    emptyMaterial.dispose();for(const r of resources)r.dispose();renderer.dispose?.();
  }};
}
