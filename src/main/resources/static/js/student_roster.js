'use strict';
// 학생명단(시간표) 자료실: 연도 목록 → 기존 Studio PDF 미리보기(쪽 이미지) → 원본 Word 내려받기(attachment).
(function(){
const PROJECT='student-roster';
const $=id=>document.getElementById(id);
const state={entries:[],year:null,page:1,pages:1};
function say(text,error){const m=$('roster-message');m.textContent=text||'';m.classList.toggle('error',!!error);}
async function api(path,body){
  const init={credentials:'same-origin'};
  if(body!==undefined){init.method='POST';if(body instanceof FormData)init.body=body;else{init.headers={'Content-Type':'application/json'};init.body=JSON.stringify(body);}}
  const r=await fetch(path,init);const data=await r.json().catch(()=>({}));
  if(!r.ok)throw Error(data.message||('요청이 안 됐습니다 ('+r.status+'). 다시 시도해 주세요.'));
  return data;
}
const fileUrl=(id,suffix)=>'/api/studio/files/'+encodeURIComponent(id)+'/'+suffix+(suffix.includes('?')?'&':'?')+'projectId='+PROJECT;
const day=v=>v?String(v).replace('T',' ').slice(0,16):'';
function renderList(){
  const list=$('year-list');list.textContent='';
  if(!state.entries.length){const li=document.createElement('li');li.className='empty';li.textContent='아직 올린 자료가 없습니다.';list.append(li);return;}
  for(const e of state.entries){
    const li=document.createElement('li'),b=document.createElement('button'),y=document.createElement('span'),p=document.createElement('span');
    b.type='button';b.dataset.year=e.year;b.setAttribute('aria-pressed',String(e.year===state.year));
    y.textContent=e.year+'년';p.className='pages';p.textContent=(e.pages??'?')+'쪽';
    b.append(y,p);b.addEventListener('click',()=>select(e.year));li.append(b);list.append(li);
  }
}
function showPage(){
  const e=state.entries.find(x=>x.year===state.year);if(!e)return;
  const sheet=$('sheet');sheet.textContent='';
  const img=new Image();img.alt=e.year+'년 학생명단 '+state.page+'쪽';
  img.onerror=()=>{sheet.textContent='';const p=document.createElement('p');p.className='empty';p.textContent='미리보기를 열지 못했습니다. 잠시 후 다시 시도해 주세요.';sheet.append(p);};
  img.src=fileUrl(e.pdfFileId,'preview?page='+state.page);sheet.append(img);
  $('page-label').textContent=state.page+' / '+state.pages+'쪽';
  $('prev-page').disabled=state.page<=1;$('next-page').disabled=state.page>=state.pages;
}
async function select(year){
  state.year=year;state.page=1;renderList();
  const e=state.entries.find(x=>x.year===year);if(!e)return;
  state.pages=Math.max(1,e.pages||1);
  $('viewer-title').textContent=year+'년 학생명단(시간표)';
  const dl=$('word-download');dl.hidden=!e.wordFileId;if(e.wordFileId){dl.href=fileUrl(e.wordFileId,'content');dl.setAttribute('download',e.wordFilename||(year+'.docx'));}
  $('pager').hidden=false;showPage();
  try{history.replaceState(null,'','?year='+year);}catch(_){}
  try{
    const rows=await api('/api/admin/student-roster/history?year='+year);const list=$('history-list');list.textContent='';
    $('history-box').hidden=rows.length<2;
    for(const r of rows){const li=document.createElement('li');
      li.append(document.createTextNode(day(r.uploadedAt)+' · '+(r.pages??'?')+'쪽'+(r.current?' (지금 버전)':'')+' · '));
      const pdf=document.createElement('a');pdf.href=fileUrl(r.pdfFileId,'content');pdf.textContent='PDF';
      const word=document.createElement('a');word.href=fileUrl(r.wordFileId,'content');word.textContent='Word';
      li.append(pdf,document.createTextNode(' · '),word);list.append(li);}
  }catch(err){$('history-box').hidden=true;}
}
async function load(preferred){
  state.entries=await api('/api/admin/student-roster/entries');
  renderList();
  const pick=state.entries.find(e=>e.year===preferred)||state.entries[0];
  if(pick)await select(pick.year);
}
$('prev-page').addEventListener('click',()=>{if(state.page>1){state.page--;showPage();}});
$('next-page').addEventListener('click',()=>{if(state.page<state.pages){state.page++;showPage();}});
$('upload-form').addEventListener('submit',async ev=>{
  ev.preventDefault();const form=ev.target,button=form.querySelector('button[type=submit]');
  const year=form.year.value.trim();
  if(!/^\d{4}$/.test(year)){say('연도를 4자리로 적어 주세요.',true);return;}
  const pdf=form.pdf.files[0],word=form.word.files[0];
  if(!pdf||!word){say('미리보기 PDF와 원본 Word를 함께 골라 주세요.',true);return;}
  button.disabled=true;say('올리는 중입니다…');
  try{
    const up=async f=>{const d=new FormData();d.append('file',f);return (await api('/api/admin/student-roster/files',d)).file.id;};
    const pdfFileId=await up(pdf),wordFileId=await up(word);
    const saved=await api('/api/admin/student-roster/entries',{year,pdfFileId,wordFileId});
    form.reset();say('저장했습니다 ('+saved.year+'년, '+saved.pages+'쪽)');await load(saved.year);
  }catch(err){say(err.message,true);}finally{button.disabled=false;}
});
const wanted=Number(new URLSearchParams(location.search).get('year'))||null;
load(wanted).catch(err=>say(err.message,true));
})();
