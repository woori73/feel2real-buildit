/* 회원 전용 이력과 기존 관리자 검수 API 연결. 저장소/운영 시드 자동 적재 없음. */
(function () {
  'use strict';
  if (document.body.dataset.member !== 'true') return;
  function el(tag, text) { var n=document.createElement(tag); if(text!=null)n.textContent=text; return n; }
  function byId(id) { return document.getElementById(id); }
  function source(parent, url, title) {
    if (!url || !/^https?:\/\//i.test(url)) return;
    var a=el('a',title||'공식 원문 열기');a.href=url;a.target='_blank';a.rel='noopener noreferrer';parent.appendChild(a);
  }
  async function api(path, options) {
    var res=await fetchAuth(path,options), data=await res.json().catch(function(){return null;});
    if(!res.ok) throw new Error(res.status===403?'접근 권한 또는 학원 연결을 확인해 주세요.':res.status===401?'다시 로그인해 주세요.':'자료를 불러오지 못했습니다.');
    return data;
  }
  byId('ag-history-toggle').addEventListener('click', async function(){
    var panel=byId('ag-history');panel.hidden=!panel.hidden;if(panel.hidden)return;
    var list=byId('ag-history-list');list.textContent='불러오는 중…';
    try {
      var rows=await api('/api/dashboard/admissions/history');list.replaceChildren();
      if(!rows.length)list.textContent='아직 저장된 상담이 없습니다.';
      rows.forEach(function(row){
        var article=el('article');article.appendChild(el('small',(row.academicYear||'미지정')+'학년도 · '+(row.createdAt||'')));
        article.appendChild(el('h3',row.question));article.appendChild(el('pre',row.answer));
        try {JSON.parse(row.evidenceSummary||'[]').forEach(function(s){source(article,s.url,s.title);});}
        catch(e){if(row.evidenceSummary)article.appendChild(el('small','근거 ID: '+row.evidenceSummary));}
        list.appendChild(article);
      });
    } catch(e){list.textContent=e.message;}
  });
  var admin=byId('ag-admin-toggle');if(!admin)return;
  var page=0;
  admin.addEventListener('click',function(){byId('ag-admin').hidden=!byId('ag-admin').hidden;if(!byId('ag-admin').hidden)load();});
  ['ag-review-kind','ag-review-year','ag-review-status'].forEach(function(id){byId(id).addEventListener('change',function(){page=0;load();});});
  byId('ag-review-refresh').addEventListener('click',load);
  byId('ag-review-prev').addEventListener('click',function(){if(page>0){page--;load();}});
  byId('ag-review-next').addEventListener('click',function(){page++;load();});
  async function load(){
    var kind=byId('ag-review-kind').value, list=byId('ag-review-list');list.textContent='불러오는 중…';
    var base='/api/admin/admissions/'+kind;
    try {
      var data=await api(base+'?academicYear='+byId('ag-review-year').value+'&status='+byId('ag-review-status').value+'&page='+page);
      list.replaceChildren();byId('ag-review-prev').disabled=page===0;byId('ag-review-next').disabled=data.last;
      if(!data.content.length)list.textContent='해당 조건의 자료가 없습니다. 자료를 자동 승인하거나 테스트 자료를 적재하지 않습니다.';
      data.content.forEach(function(row){
        var card=el('article');card.appendChild(el('h3',row.title));
        card.appendChild(el('p',row.academicYear+'학년도 · '+(row.status==='ACTIVE'?'APPROVED(승인)':row.status)));
        source(card,row.documentUrl||row.sourceUrl);
        if(row.content){var details=el('details');details.appendChild(el('summary','근거 내용 확인'));details.appendChild(el('pre',row.content));card.appendChild(details);}
        var actions=row.status==='DRAFT'?[['review','검수 시작']]:row.status==='REVIEW'?[['approve','검수 완료 · 승인'],['reject','반려']]:row.status==='ACTIVE'?[['archive','보관']]:[['reject','DRAFT로 되돌리기']];
        actions.forEach(function(pair){var b=el('button',pair[1]);b.type='button';b.addEventListener('click',async function(){
          if(!window.confirm(row.title+' — '+pair[1]+' 처리할까요?'))return;
          b.disabled=true;
          try {await api(base+'/'+row.id+'/review',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:pair[0]})});await load();}
          catch(e){list.textContent=e.message;}
        });card.appendChild(b);});
        list.appendChild(card);
      });
    } catch(e){list.textContent=e.message;}
  }
})();
