(function(){
  const topList=document.getElementById('topChartList'),restList=document.getElementById('restChartList'),restSection=document.getElementById('restSection');
  const discList=document.getElementById('discoveredList'),discSection=document.getElementById('discoveredSection'),modalRoot=document.getElementById('profileModalRoot');
  let people=[];
  const esc=(v)=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const avatar=(url)=>url?`<img class="row-avatar" src="${esc(url)}" alt="" loading="lazy" referrerpolicy="no-referrer">`:'<svg class="avatar-default" viewBox="0 0 56 56" aria-hidden="true"><circle cx="28" cy="28" r="28" fill="var(--bg-soft)"/><circle cx="28" cy="22" r="9" fill="var(--ink-faint)"/><path d="M10 48c2-9.5 9.8-15 18-15s16 5.5 18 15" fill="var(--ink-faint)"/></svg>';
  function trend(e){if(e.previousRank==null)return{cls:'new',label:'NEW'};const d=e.previousRank-e.rank;return d>0?{cls:'up',label:`▲ ${d}`}:d<0?{cls:'down',label:`▼ ${Math.abs(d)}`}:{cls:'flat',label:'—'};}
  function region(e){return esc(e.activityRegion||[e.activityProvince,e.activityCityDistrict].filter(Boolean).join(' '));}
  function row(e,discovered){const t=trend(e);return `<article class="chart-row ${!discovered&&e.rank<=10?'top10':''}" data-id="${esc(e.educatorId)}" role="button" tabindex="0">`
    +`<div class="row-rank"><span class="rank-num">${discovered?'★':esc(e.rank)}</span></div>`
    +`<div class="row-photo">${avatar(e.profileImageUrl)}</div>`
    +`<div class="row-primary"><div class="row-name-line"><strong class="row-name">${esc(e.name)}</strong><span class="row-role">${esc(e.role)}</span></div>`
    +`<div class="row-academy">${esc(e.academyName||'소속 확인 중')}</div></div>`
    +`<div class="row-secondary"><span class="row-region">${region(e)}</span></div>`
    +`<div class="row-stats">${discovered?'':`<span class="row-trend ${t.cls}">${esc(t.label)}</span>`}<span class="score-badge">${esc(e.buildItScore??'–')}점</span></div>`
    +`<div class="row-action"><button class="btn outline small" type="button">기록 보기</button></div></article>`;}
  function link(label,url){return url?`<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${esc(label)} →</a>`:'';}
  function para(text){return `<p>${esc(text)}</p>`;}
  function listOr(items,fallback){return (items&&items.length)?`<ul>${items.map(x=>`<li>${esc(x)}</li>`).join('')}</ul>`:`<p class="muted-note">${esc(fallback)}</p>`;}
  function activity(a){return `<article class="content-card"><p class="content-meta">${esc(a.evidenceKind||a.contentType||'공개 활동')}</p><h4>${esc(a.contentSummary||'확인된 교육 활동')}</h4><footer><span>${esc(a.activityDate||'')}</span>${link('근거 보기',a.sourceUrl)}</footer></article>`;}
  function sources(e){const s=[link('홈페이지',e.officialHomepage),link('블로그',e.officialBlog),link('YouTube',e.officialYoutube),link('SNS',e.officialSocial)].filter(Boolean);
    const acts=(e.activities||[]).map(a=>a.sourceUrl).filter(Boolean).slice(0,6).map((u,i)=>link('공개 근거 '+(i+1),u));
    const all=s.concat(acts);return all.length?all.join(' · '):'<span class="muted-note">공개 근거를 정리 중입니다.</span>';}
  function openModal(id){const e=people.find(x=>String(x.educatorId)===String(id));if(!e)return;const acts=e.activities||[];
    const philosophy=e.educationPhilosophy, curriculum=[e.curriculum,e.distinctiveTeaching].filter(Boolean).join(' · ');
    modalRoot.innerHTML=`<div class="modal-backdrop" data-close="1"><article class="profile-sheet" data-sheet="1">`
    +`<header class="modal-head"><button type="button" data-close="1">← 차트</button><span>EDUCATOR LIVING ARCHIVE</span><button class="close" type="button" data-close="1">×</button></header>`
    +`<section class="profile-hero">${avatar(e.profileImageUrl)}<div><h2>${esc(e.name)} <small>${esc(e.role)}</small></h2>`
    +`<p class="academy">${region(e)}${e.academyName?' · '+esc(e.academyName):''}</p>`
    +`<p class="specialty">BuildIt Score ${esc(e.buildItScore??'–')}${e.rank?` · 현재 ${esc(e.rank)}위`:''}${e.previousRank!=null?` (이전 ${esc(e.previousRank)}위)`:(e.rank?' (NEW)':'')}</p></div></section>`
    +`<section class="intro"><h3>교육철학</h3>${philosophy?para(philosophy):'<p class="muted-note">확인된 공개 기록이 아직 없습니다.</p>'}`
    +`<h3>커리큘럼·수업 방식</h3>${curriculum?para(curriculum):'<p class="muted-note">확인된 공개 기록이 아직 없습니다.</p>'}`
    +`${e.discoveryReason?`<h3>BuildIt이 발견한 이유</h3>${para(e.discoveryReason)}`:''}</section>`
    +`<section class="why"><h3>BuildIt이 발견한 강점</h3>${listOr(e.strengths,'공개 근거에서 확인된 강점을 정리 중입니다.')}</section>`
    +`<section class="activity"><h3>최근 활동</h3>${e.recentActivity?para(e.recentActivity):''}<div class="content-grid">${acts.length?acts.map(activity).join(''):'<p class="muted-note">확인된 최근 활동 기록이 아직 없습니다.</p>'}</div></section>`
    +`<section class="why"><h3>이런 점을 개선하면 어떨까요?</h3>${listOr(e.improvementSuggestions,'현재 가진 교육적 강점이 더 잘 발견되도록 공개 기록을 꾸준히 남겨보세요.')}</section>`
    +`<section class="register"><strong>공개 조사 근거</strong><span>${sources(e)}</span></section>`
    +`<section class="final-cta"><p>이 기록을 바탕으로 교육자 자신의 디지털 공간을 만들어 보세요.</p><a class="btn" href="/#cta">BuildIt Web 알아보기 →</a></section>`
    +`</article></div>`;}
  function render(data){
    const ranked=data.rankedTop100||data.entries||[];const discovered=data.discovered||[];
    people=[...ranked,...discovered];
    const label=data.updatedLabel||(data.updatedAt?('최근 업데이트 '+String(data.updatedAt).slice(0,10).replace(/-/g,'.')):'지속 업데이트');
    document.getElementById('weeklyUpdate').textContent=label;
    document.getElementById('chartDisclaimer').textContent=data.disclaimer||'';
    document.getElementById('topCount').textContent=`현재 선정 교육자 ${ranked.length}명`;
    topList.innerHTML=ranked.length?ranked.slice(0,10).map(e=>row(e,false)).join(''):'<div class="empty">전국 조사에서 선정된 교육자가 곧 공개됩니다.</div>';
    const rest=ranked.slice(10);restSection.hidden=!rest.length;restList.innerHTML=rest.map(e=>row(e,false)).join('');
    discSection.hidden=!discovered.length;discList.innerHTML=discovered.map(e=>row(e,true)).join('');
  }
  document.addEventListener('click',ev=>{const trigger=ev.target.closest('.chart-row');if(trigger){openModal(trigger.dataset.id);return;}if(ev.target.matches('[data-close]'))modalRoot.innerHTML='';});
  document.addEventListener('keydown',ev=>{if(ev.key==='Escape')modalRoot.innerHTML='';const rowEl=ev.target.closest&&ev.target.closest('.chart-row');if(rowEl&&(ev.key==='Enter'||ev.key===' ')){ev.preventDefault();openModal(rowEl.dataset.id);}});
  fetch('/api/chart/publication/current').then(r=>r.ok?r.json():Promise.reject()).then(render).catch(()=>{topList.innerHTML='<p class="state error">차트를 불러오지 못했습니다.</p>';});
})();
