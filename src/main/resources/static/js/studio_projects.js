"use strict";
(()=>{
const $=id=>document.getElementById(id), el=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e;};
let canManage=false,renameId;
const message=text=>$("message").textContent=text;
const run=fn=>async event=>{event?.preventDefault();try{message("");await fn(event);}catch(e){message(e.message);}};
async function api(path,data){const r=await fetch('/api/studio/'+path,{credentials:'same-origin',...(data?{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)}:{})});const body=await r.json();if(!r.ok)throw Error(body.message||body.detail||'요청 실패 ('+r.status+')');return body;}
const scope=id=>'?'+new URLSearchParams({projectId:id});
async function load(){
 const [projects,invites,caps]=await Promise.all([api('projects'),api('invitations'),api('project-capabilities')]);canManage=caps.canManage;$("create-project").hidden=!canManage;
 $("active-projects").replaceChildren();$("archived-projects").replaceChildren();$("invite-list").replaceChildren();
 for(const p of projects){const c=el('article');c.className='card project-card';c.dataset.projectId=p.id;c.append(el('h3',p.title),el('p',p.archived?'보관됨 · 모든 자료 유지':'작업 중'));const open=el('a','프로젝트 열기');open.href='/ai-agent-studio'+scope(p.id);c.append(open);
  if(canManage){const rename=el('button','제목 수정');rename.onclick=()=>{renameId=p.id;$("rename-form").elements.title.value=p.title;$("rename-project").showModal();};const toggle=el('button',p.archived?'프로젝트 재개':'프로젝트 보관');toggle.onclick=run(async()=>{toggle.disabled=true;try{const state=await api('project-state'+scope(p.id),{action:p.archived?'REOPEN':'ARCHIVE'});if(state.archived===Boolean(p.archived))throw Error('처리 결과를 확인할 수 없습니다.');await load();message(state.archived?'보관했습니다. 모든 자료와 이력은 유지합니다.':'프로젝트를 재개했습니다.');}finally{toggle.disabled=false;}});c.append(rename,toggle);}
  $(p.archived?'archived-projects':'active-projects').append(c);
 }
 for(const [id,text]of [['active-projects','작업 중인 프로젝트가 없습니다.'],['archived-projects','보관된 프로젝트가 없습니다.']])if(!$(id).children.length)$(id).append(el('p',text));
 for(const invitation of invites){const c=el('article');c.className='card';c.dataset.projectId=invitation.id;c.append(el('h3',invitation.title),el('p','요청 권한 · '+invitation.permission.replace('PENDING_','')));for(const [accept,label]of [[true,'초대 수락'],[false,'초대 거절']]){const b=el('button',label);b.onclick=run(async()=>{await api('invitations/answer'+scope(invitation.id),{accept});await load();message(accept?'초대를 수락했습니다.':'초대를 거절했습니다.');});c.append(b);}$("invite-list").append(c);}
 if(!invites.length)$("invite-list").append(el('p','대기 중인 초대가 없습니다.'));
}
$("create-project").onsubmit=run(async()=>{const form=$("create-project"),created=await api('projects',{title:form.elements.title.value});form.reset();await load();if(!document.querySelector('[data-project-id="'+created.id+'"]'))throw Error('생성 결과 재조회 실패');message('프로젝트를 생성하고 저장 결과를 확인했습니다.');});
$("rename-form").onsubmit=run(async()=>{await api('project-state'+scope(renameId),{action:'TITLE',title:$("rename-form").elements.title.value});$("rename-project").close();await load();message('제목을 수정하고 다시 조회했습니다.');});$("cancel-rename").onclick=()=>$("rename-project").close();run(load)();
})();
