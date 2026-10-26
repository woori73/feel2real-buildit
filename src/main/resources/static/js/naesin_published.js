"use strict";
(() => {
  const $=id=>document.getElementById(id);let rows=[],offset=0;
  const el=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};
  function render(){const q=$("query").value.trim().toLowerCase();$("publications").replaceChildren();let count=0;rows.forEach(r=>{let p={};try{p=JSON.parse(r.profile);}catch{}if(![r.title,p.school,p.grade,p.examRange].join(" ").toLowerCase().includes(q))return;count++;const card=el("article","");card.className="card";card.append(el("h2",r.title),el("p",[p.school,p.grade,p.examRange].filter(Boolean).join(" · ")),el("p","승인 버전 "+r.label));const a=el("a","완성본 다운로드");a.href=`/api/naesin-wiki/publications/${r.publication_id}/download`;card.append(a,el("p","SHA-256 "+r.sha256));$("publications").append(card);});if(!count)$("publications").append(el("p","게시 승인된 완성본이 없습니다."));}
  async function load(){try{const r=await fetch("/api/naesin-wiki/publications?offset="+offset,{credentials:"same-origin"});if(!r.ok)throw Error("로그인과 내신Wiki 이용 권한을 확인하세요.");const result=await r.json();rows.push(...result);offset+=result.length;$("more").hidden=result.length<100;render();}catch(e){$("message").textContent=e.message;}}
  $("query").addEventListener("input",render);$("more").onclick=load;load();
})();
