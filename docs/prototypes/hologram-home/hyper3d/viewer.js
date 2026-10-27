import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
const MAX_BYTES=8*1024*1024;
// This PoC accepts self-contained GLBs only: no remote textures, scripts or decoders.
export function inspectGLB(bytes){
 if(bytes.byteLength>MAX_BYTES)throw Error('GLB exceeds 8 MiB review budget');
 const view=new DataView(bytes);
 if(bytes.byteLength<20||view.getUint32(0,true)!==0x46546c67||view.getUint32(4,true)!==2||view.getUint32(8,true)!==bytes.byteLength||view.getUint32(16,true)!==0x4e4f534a)throw Error('Invalid GLB 2.0');
 const size=view.getUint32(12,true);if(20+size>bytes.byteLength)throw Error('Invalid GLB JSON length');
 const doc=JSON.parse(new TextDecoder().decode(new Uint8Array(bytes,20,size)));
 if([...doc.buffers||[],...doc.images||[]].some(x=>x.uri))throw Error('Only embedded GLB resources are allowed');
 if((doc.textures||[]).length>8)throw Error('Texture count exceeds review budget');
 if((doc.extensionsRequired||[]).length)throw Error('Required GLB extensions need separate validation');
 return doc;
}
export async function createViewer(host,entry,{signal,onFailure,onMetric}){
 const started=performance.now(),url=new URL(entry.url,location.href);
 if(url.origin!==location.origin)throw Error('Only local reviewed models are allowed');
 const response=await fetch(url,{signal});if(!response.ok)throw Error('GLB HTTP '+response.status);
 const reader=response.body.getReader(),chunks=[];let total=0;
 while(true){const {done,value}=await reader.read();if(done)break;total+=value.length;if(total>MAX_BYTES){await reader.cancel();throw Error('GLB exceeds 8 MiB review budget');}chunks.push(value);}
 const all=new Uint8Array(total);let offset=0;for(const c of chunks){all.set(c,offset);offset+=c.length;}
 const hash=[...new Uint8Array(await crypto.subtle.digest('SHA-256',all))].map(v=>v.toString(16).padStart(2,'0')).join('');
 if(hash!==entry.sha256)throw Error('Model SHA-256 does not match review record');
 inspectGLB(all.buffer);signal.throwIfAborted();
 const gltf=await new GLTFLoader().parseAsync(all.buffer,'');
 let renderer,mixer,raf,action,disposed=false,visible=true,last=0,previous=0,frames=0,intervals=[];
 const scene=new THREE.Scene(),root=gltf.scene;
 const camera=new THREE.PerspectiveCamera(38,1,.01,100);camera.position.set(0,.15,4.5);camera.lookAt(0,0,0);
 const resources=new Set();root.traverse(o=>{if(o.geometry)resources.add(o.geometry);if(o.skeleton)resources.add(o.skeleton);for(const m of Array.isArray(o.material)?o.material:[o.material])if(m){resources.add(m);for(const v of Object.values(m))if(v?.isTexture)resources.add(v);}});
 const dispose=()=>{if(disposed)return;disposed=true;cancelAnimationFrame(raf);resize?.disconnect();intersection?.disconnect();document.removeEventListener('visibilitychange',wake);motion.removeEventListener('change',wake);mixer?.stopAllAction();mixer?.uncacheRoot(root);const bitmaps=new Set();for(const r of resources){if(r.isTexture&&typeof r.image?.close==='function')bitmaps.add(r.image);r.dispose();}for(const bitmap of bitmaps)bitmap.close();renderer?.dispose();renderer?.forceContextLoss();renderer?.domElement.remove();};
 const motion=matchMedia('(prefers-reduced-motion: reduce)');let resize,intersection;
 function wake(){if(disposed)return;cancelAnimationFrame(raf);last=previous=0;if(!document.hidden&&visible)raf=requestAnimationFrame(tick);}
 function tick(now){if(disposed||document.hidden||!visible)return;raf=undefined;const cap=innerWidth<768?30:60;
  if(!last||now-last>=1000/cap-1){const delta=last?Math.min((now-last)/1000,.1):0;if(previous&&intervals.length<300)intervals.push(now-previous);previous=last=now;if(!motion.matches)mixer.update(delta);renderer.render(scene,camera);frames++;host.dataset.frames=String(frames);}
  if(!motion.matches)raf=requestAnimationFrame(tick);
 }
 let triangles=0;
 try{
  signal.throwIfAborted();
  root.traverse(o=>{if(o.isMesh)triangles+=(o.geometry.index?.count||o.geometry.attributes.position?.count||0)/3;});
  if(triangles>100000)throw Error('Mesh exceeds 100k triangle review budget');
  for(const r of resources)if(r.isTexture&&Math.max(r.image?.width||0,r.image?.height||0)>4096)throw Error('Texture exceeds 4096px review budget');
  for(const state of ['idle','greeting','thinking','responding'])if(!gltf.animations.some(c=>c.name===entry.clips?.[state]))throw Error('Missing reviewed animation: '+state);
  renderer=new THREE.WebGLRenderer({alpha:true,antialias:innerWidth>=768,powerPreference:'low-power'});
  renderer.setPixelRatio(Math.min(devicePixelRatio,innerWidth<768?1.5:2));renderer.setClearColor(0x000000,0);
  renderer.domElement.setAttribute('aria-label','3D 기술 검증 모형');renderer.domElement.setAttribute('role','img');
  renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();if(!disposed)onFailure('WebGL context lost');});
  host.append(renderer.domElement);
  // Framing only: preserve mesh, UVs, textures, facial geometry and costume materials.
  const box=new THREE.Box3().setFromObject(root),size=box.getSize(new THREE.Vector3()),center=box.getCenter(new THREE.Vector3());
  const span=Math.max(size.x,size.y,size.z);if(!Number.isFinite(span)||span<=0)throw Error('Empty model');
  root.position.sub(center);const frame=new THREE.Group();frame.scale.setScalar(2.5/span);frame.add(root);scene.add(frame);
  scene.add(new THREE.HemisphereLight(0xffffff,0x323232,2));const light=new THREE.DirectionalLight(0xffffff,2.5);light.position.set(3,4,5);scene.add(light);
  mixer=new THREE.AnimationMixer(root);
  resize=new ResizeObserver(()=>{if(disposed)return;const w=host.clientWidth,h=host.clientHeight;if(!w||!h)return;renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();wake();});resize.observe(host);
  intersection=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;wake();});intersection.observe(host);
  document.addEventListener('visibilitychange',wake);motion.addEventListener('change',wake);
  renderer.setSize(host.clientWidth,host.clientHeight,false);camera.aspect=host.clientWidth/host.clientHeight;camera.updateProjectionMatrix();
  renderer.render(scene,camera);
  onMetric({bytes:total,sha256:hash,triangles,drawCalls:renderer.info.render.calls,firstFrameMs:Math.round(performance.now()-started),pixelRatio:renderer.getPixelRatio(),renderer:renderer.getContext().getParameter(renderer.getContext().RENDERER)});
  wake();
  return {dispose,setState(state){const name=entry.clips[state]||entry.clips.idle;const clip=gltf.animations.find(c=>c.name===name);const next=mixer.clipAction(clip);if(next!==action){action?.stop();action=next;action.reset().play();}host.dataset.clip=name;wake();},stats(){return {frames,intervals:intervals.slice(),renderCalls:renderer.info.render.calls};}};
 }catch(error){dispose();throw error;}
}
