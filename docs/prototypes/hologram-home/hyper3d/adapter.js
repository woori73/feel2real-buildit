// Additive preview adapter: never changes main's dialogue, selected media or application routes.
// The ordinary preview remains byte-for-byte identical except for this lazy entry point.
if(new URLSearchParams(location.search).get('preview3d')==='1')startPreview();
function startPreview(){
 const stage=document.getElementById('homeStage'),screen=stage.querySelector('.media-screen');
 const host=document.createElement('div');host.id='modelViewport';screen.append(host);
 const controls=document.createElement('section');controls.className='model-controls';controls.setAttribute('aria-label','3D 기술 검증');
 controls.innerHTML='<strong>3D 기술 검증 · 실제 캐릭터 아님</strong><div><button type="button" data-model-mode="video" aria-pressed="true">기존 영상</button><button type="button" data-model-mode="diagnostic" aria-pressed="false">테스트 GLB</button><button type="button" data-model-mode="approved" aria-pressed="false">승인 모델 확인</button></div><p id="modelStatus" role="status">기존 영상 경로 유지 · 승인 캐릭터 GLB 미확인</p>';
 stage.parentElement.append(controls);
 const badge=document.createElement('span');badge.className='model-badge';badge.textContent='TEST GLB · 얼굴·의상 없음';host.append(badge);
 const style=document.createElement('link');style.rel='stylesheet';style.href='hyper3d/viewer.css';document.head.append(style);
 const status=controls.querySelector('#modelStatus');let mode='video',key='',epoch=0,viewer,controller,timer,metrics;
 const selected=()=>document.querySelector('[data-home-agent][aria-pressed="true"]').dataset.homeAgent;
 const state=()=>({greeting:'greeting',thinking:'thinking',speaking:'responding'})[stage.dataset.state]||'idle';
 const setStatus=(result,message)=>{host.dataset.result=result;status.textContent=message;};
 function clear(){clearTimeout(timer);controller?.abort();viewer?.dispose();viewer=null;host.classList.remove('ready');delete host.dataset.clip;}
 function fallback(message){epoch++;clear();setStatus('fallback',message+' · 기존 영상/포스터 경로로 복귀');}
 async function load(){
  const agent=selected(),ticket=++epoch;clear();host.dataset.agent=agent;metrics=null;
  if(mode==='video'){setStatus('video','기존 영상 경로 유지 · 승인 캐릭터 GLB 미확인');return;}
  controller=new AbortController();const signal=controller.signal;
  timer=setTimeout(()=>{if(ticket===epoch)fallback('3D 로딩 시간 초과');},8000);
  setStatus('loading','3D 로딩 중 · 기존 영상 유지');
  try{
   const response=await fetch(mode==='diagnostic'?'hyper3d/fixture.json':'hyper3d/models.json',{signal});
   if(!response.ok)throw Error('Model manifest HTTP '+response.status);
   const manifest=await response.json(),entry=mode==='diagnostic'?manifest:manifest.agents?.[agent];
   if(mode==='approved'&&(!entry||entry.approved!==true||!entry.approvalReference||!entry.sourceSha256))throw Error('승인된 '+(agent==='seven'?'세븐':'서수니')+' 모델 없음');
   if(!entry?.url||!/^[a-f0-9]{64}$/.test(entry.sha256))throw Error('모델 무결성 기록 없음');
   const {createViewer}=await import('./viewer.bundle.js');if(ticket!==epoch)return;
   const next=await createViewer(host,entry,{signal,onFailure:reason=>{if(ticket===epoch)fallback(reason);},onMetric:value=>{if(ticket===epoch)metrics=value;}});
   if(ticket!==epoch){next.dispose();return;}
   viewer=next;clearTimeout(timer);viewer.setState(state());host.classList.add('ready');
   badge.textContent=mode==='diagnostic'?'TEST GLB · 얼굴·의상 없음':'승인 GLB · 로컬 검증';
   setStatus('ready',(mode==='diagnostic'?'기하학 테스트 GLB':'승인 GLB')+' · '+(agent==='seven'?'세븐':'서수니')+' 상태 연동');
  }catch(error){if(ticket===epoch)fallback(error.message);}
 }
 controls.addEventListener('click',e=>{const button=e.target.closest('[data-model-mode]');if(!button)return;mode=button.dataset.modelMode;controls.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));key=selected();load();});
 new MutationObserver(()=>{if(key!==selected()){key=selected();load();}else viewer?.setState(state());}).observe(stage,{attributes:true,subtree:true,attributeFilter:['data-state','aria-pressed']});
 addEventListener('pagehide',()=>{epoch++;clear();});
 // Read-only inspection for the local test harness; no production agent controls or API keys.
 window.f2r3dDiagnostics=()=>({mode,agent:selected(),state:state(),result:host.dataset.result,metrics,stats:viewer?.stats()||null});
 key=selected();load();
}
