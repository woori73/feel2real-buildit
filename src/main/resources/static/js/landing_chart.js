(function(){
  const root=document.getElementById('landingChartTop5');if(!root)return;
  const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  fetch('/api/chart/publication/current').then(r=>r.ok?r.json():Promise.reject()).then(data=>{
    if(data.weeklyUpdateDate)document.getElementById('landingChartDate').textContent=`이번 주 업데이트 ${data.weeklyUpdateDate}`;
    const rows=(data.entries||[]).slice(0,5);
    root.innerHTML=rows.length?rows.map(e=>`<div class="chart-row"><div class="chart-rank">${esc(e.rank)}</div><div class="chart-avatar"></div><div><div class="chart-name">${esc(e.name)} <span>${esc(e.role)}</span></div><div class="chart-meta">${esc(e.identitySummary||e.activityRegion)}</div></div><div class="chart-new">${e.previousRank==null?'NEW':''}</div></div>`).join(''):'<div class="chart-row"><div class="chart-meta">관리자가 승인한 주간 발행본이 준비 중입니다.</div></div>';
  }).catch(()=>{root.innerHTML='<div class="chart-row"><div class="chart-meta">Chart를 불러오지 못했습니다.</div></div>';});
})();
