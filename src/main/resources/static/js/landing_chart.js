(function(){
  const root=document.getElementById('landingChartTop5');if(!root)return;
  const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  fetch('/api/chart/publication/current').then(r=>r.ok?r.json():Promise.reject()).then(data=>{
    const dateEl=document.getElementById('landingChartDate');
    if(dateEl){
      const label=data.updatedLabel||(data.updatedAt?('최근 업데이트 '+String(data.updatedAt).slice(0,10).replace(/-/g,'.')):'지속 업데이트');
      dateEl.textContent=label;
    }
    const rows=(data.rankedTop100||data.entries||[]).slice(0,5);
    root.innerHTML=rows.length?rows.map(e=>`<div class="chart-row"><div class="chart-rank">${esc(e.rank)}</div><div class="chart-avatar"></div><div><div class="chart-name">${esc(e.name)} <span>${esc(e.role)}</span></div><div class="chart-meta">${esc(e.identitySummary||e.academyName||e.activityRegion)}</div></div><div class="chart-new">${e.previousRank==null?'NEW':(e.previousRank>e.rank?'▲':(e.previousRank<e.rank?'▼':''))}</div></div>`).join(''):'<div class="chart-row"><div class="chart-meta">전국 조사에서 선정된 교육자가 곧 공개됩니다.</div></div>';
  }).catch(()=>{root.innerHTML='<div class="chart-row"><div class="chart-meta">Chart를 불러오지 못했습니다.</div></div>';});
})();
