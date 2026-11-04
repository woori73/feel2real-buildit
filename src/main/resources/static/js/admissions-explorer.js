/* 공식자료 탐색·비교. 점수/확률 추정 없음. 성적은 저장하지 않는다. */
(function (root) {
  'use strict';
  function text(value) { return value == null || value === '' ? '미공개' : String(value); }
  function norm(value) { return String(value || '').replace(/국립/g, '').replace(/대학교/g, '대').replace(/\s/g, '').toLowerCase(); }
  function metric(row) { return text(row.rawValueText == null || row.rawValueText === '' ? row.metricValue : row.rawValueText); }
  function cleanCandidates(rows) {
    if (!Array.isArray(rows)) return [];
    var seen = {};
    return rows.filter(function (r) {
      if (!r || !Number.isSafeInteger(r.id) || r.id < 1 || [2027,2028].indexOf(r.year)<0 || typeof r.name!=='string' || typeof r.universityName!=='string') return false;
      var key=r.year+':'+r.id; if(seen[key])return false;seen[key]=true;return true;
    }).slice(0,3).map(function(r){return {id:r.id,year:r.year,name:r.name.slice(0,200),universityName:r.universityName.slice(0,100)};});
  }
  var model={text:text,norm:norm,metric:metric,cleanCandidates:cleanCandidates};
  if(typeof module!=='undefined'&&module.exports) module.exports=model;
  if(!root.document || !root.document.getElementById('ae-workspace'))return;
  var doc=root.document, byId=function(id){return doc.getElementById(id);};
  var member=doc.body.dataset.member==='true', base=member?'/api/dashboard/admissions/explore':'/api/admissions/explore';
  var universities=[], candidates=[], generation=0, detailGeneration=0, compareGeneration=0;
  try { candidates=cleanCandidates(JSON.parse(sessionStorage.getItem('f2r-admissions-candidates-v1')||'[]')); }catch(e){}
  function el(tag,value,cls){var n=doc.createElement(tag);if(value!=null)n.textContent=value;if(cls)n.className=cls;return n;}
  function button(label,run,cls){var b=el('button',label,cls);b.type='button';b.addEventListener('click',run);return b;}
  function paragraph(parent,value,cls){parent.appendChild(el('p',value,cls));}
  function source(parent,url,title){if(!/^https?:\/\//i.test(url||''))return;var a=el('a',title||'공식 원문');a.href=url;a.target='_blank';a.rel='noopener noreferrer';parent.appendChild(a);}
  async function api(path){
    var res=await (member?root.fetchAuth:root.fetch)(base+path),data=await res.json().catch(function(){return null;});
    if(!res.ok)throw new Error(res.status===503?'입시자료 탐색이 아직 열리지 않았습니다. AI 상담에서 자료를 확인해 주세요.':res.status===429?'조회가 많습니다. 잠시 후 다시 시도해 주세요.':res.status===401?'다시 로그인해 주세요.':res.status===404?'현재 승인된 자료가 없습니다.':'자료를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
    return data;
  }
  function year(){return Number(byId('ae-year').value);}
  function save(){
    try{sessionStorage.setItem('f2r-admissions-candidates-v1',JSON.stringify(candidates));}catch(e){}
    byId('ae-count').textContent=String(candidates.length);
  }
  function tab(name){
    byId('ae-workspace').hidden=name==='chat';
    ['search','compare','scores'].forEach(function(p){byId('ae-'+p+'-panel').hidden=p!==name;});
    if(name!=='chat'){byId('ag-welcome').hidden=true;byId('ag-chat').hidden=true;}
    else {byId('ag-chat').hidden=true;byId('ag-welcome').hidden=false;doc.dispatchEvent(new CustomEvent('admissions:show-chat'));}
    doc.querySelectorAll('[data-ae-tab]').forEach(function(b){b.setAttribute('aria-pressed',String(b.dataset.aeTab===name));});
    if(name==='scores'){byId('ae-score-form').hidden=year()===2028;byId('ae-score-year-note').hidden=year()!==2028;}
    if(name==='compare')compare();
  }
  function ask(row){
    byId('ag-year-select').value=String(row.year);byId('ag-year-select').dispatchEvent(new Event('change'));
    byId('ag-target-universities').value=row.universityName;byId('ag-target-majors').value=row.name;
    tab('chat');
    doc.dispatchEvent(new CustomEvent('admissions:question',{detail:{question:row.year+'학년도 '+row.universityName+' '+row.name+'의 모집요강과 '+(row.year-1)+'학년도(지난해) 공식 입시결과를 출처와 함께 설명해 줘. 지표가 서로 다르면 구분하고 합격 가능성은 단정하지 마.'}}));
  }
  function keep(row){
    var value={id:Number(row.id),year:year(),name:row.name,universityName:row.universityName};
    if(candidates.some(function(c){return c.id===value.id&&c.year===value.year;})){byId('ae-status').textContent='이미 비교 후보에 담았습니다.';return;}
    if(candidates.length===3){byId('ae-status').textContent='최대 3개까지 비교할 수 있습니다. 입결 비교에서 후보를 삭제해 주세요.';return;}
    candidates.push(value);save();byId('ae-status').textContent=value.name+'을 비교 후보에 담았습니다.';
  }
  function renderUniversities(){
    var filter=norm(byId('ae-univ-filter').value), select=byId('ae-university'),previous=select.value;
    select.replaceChildren(el('option','대학을 선택해 주세요'));select.firstChild.value='';
    universities.filter(function(u){return norm(u.name).includes(filter);}).forEach(function(u){var o=el('option',u.name+(u.region?' · '+u.region:''));o.value=u.id;select.appendChild(o);});
    if(Array.from(select.options).some(function(o){return o.value===previous;}))select.value=previous;
  }
  async function loadUniversities(){
    var token=++generation;++detailGeneration;byId('ae-results').replaceChildren();byId('ae-detail').hidden=true;
    byId('ae-status').textContent='승인된 공식 자료를 확인하고 있습니다…';universities=[];renderUniversities();
    try{var data=await api('/universities?academicYear='+year());if(token!==generation)return;universities=data;renderUniversities();byId('ae-status').textContent=data.length?'대학을 선택해 모집단위를 찾아보세요.':'이 학년도에 승인된 모집단위 자료가 없습니다.';}
    catch(e){if(token===generation)byId('ae-status').textContent=e.message;}
  }
  async function search(){
    var id=byId('ae-university').value;if(!id){byId('ae-status').textContent='대학을 먼저 선택해 주세요.';return;}
    var token=++generation, selectedYear=year();++detailGeneration;byId('ae-detail').hidden=true;byId('ae-results').replaceChildren();byId('ae-status').textContent='모집단위를 찾고 있습니다…';
    try{
      var data=await api('/departments?academicYear='+selectedYear+'&universityId='+encodeURIComponent(id)+'&q='+encodeURIComponent(byId('ae-major').value));if(token!==generation)return;
      byId('ae-status').textContent=data.items.length+'개 모집단위'+(data.hasMore?'를 표시했습니다. 검색어를 더 입력하면 범위를 좁힐 수 있습니다.':'를 찾았습니다.')+(selectedYear===2028?' 2028학년도는 계획 자료로 변경될 수 있습니다.':'');
      if(!data.items.length)byId('ae-status').textContent='이 조건의 승인된 자료가 없습니다. 다른 검색어로 찾아보세요.';
      data.items.forEach(function(row){var card=el('article',null,'ae-card');paragraph(card,row.universityName,'ae-eyebrow');card.appendChild(el('h2',row.name));paragraph(card,text(row.category));var a=el('div',null,'ae-actions');a.appendChild(button('공식 자료 보기',function(){detail(row);}));a.appendChild(button('비교에 담기',function(){keep(row);},'ae-primary'));card.appendChild(a);byId('ae-results').appendChild(card);});
    }catch(e){if(token===generation)byId('ae-status').textContent=e.message;}
  }
  function renderPrior(parent,rows){
    if(!rows.length){paragraph(parent,'해당 모집단위의 지난해 공식 입시결과가 아직 연결되지 않았습니다.');return;}
    var table=el('table'),head=el('thead'),hr=el('tr');['결과 학년도 / 전형','원문 지표','공개 값'].forEach(function(t){var th=el('th',t);th.scope='col';hr.appendChild(th);});head.appendChild(hr);table.appendChild(head);var body=el('tbody');
    rows.forEach(function(row){var tr=el('tr');var c1=el('td',row.resultYear+'학년도(지난해) · '+(row.roundType==='JEONGSI'?'정시':'수시')+' '+(row.groupName||'')+' '+row.typeName);var c2=el('td',row.metricLabel||'모집·경쟁·충원');var c3=el('td');
      if(row.metricLabel)c3.textContent=metric(row)+(row.metricUnit?' ['+row.metricUnit+']':'');
      else c3.textContent='모집 '+text(row.recruitCount)+' / 지원 '+text(row.applicantCount)+' / 경쟁률 '+text(row.competitionRate)+' / 충원 '+text(row.extraAdmittedCount);
      source(c2,row.sourceUrl||row.documentUrl,row.sourceTitle||'공식 입시결과');if(row.evidencePages)c2.appendChild(el('small','근거 쪽: '+row.evidencePages));
      tr.append(c1,c2,c3);body.appendChild(tr);
    });table.appendChild(body);var wrap=el('div',null,'ae-table-scroll');wrap.appendChild(table);parent.appendChild(wrap);
  }
  function renderDetail(parent,data,compact){
    var row=data.department;parent.replaceChildren();paragraph(parent,row.universityName+' · '+data.academicYear+'학년도','ae-eyebrow');parent.appendChild(el('h2',row.name));
    if(data.academicYear===2028)paragraph(parent,'2028학년도 계획 자료 · 변경될 수 있습니다.','ae-note');
    var actions=el('div',null,'ae-actions');if(!compact)actions.appendChild(button('비교에 담기',function(){keep(row);}));actions.appendChild(button('이 대학 AI 상담',function(){ask({id:Number(row.id),year:data.academicYear,name:row.name,universityName:row.universityName});},'ae-primary'));parent.appendChild(actions);
    source(parent,row.sourceUrl,row.sourceTitle||'공식 모집요강');
    if(row.quotaInfo){var d=el('details');d.append(el('summary','모집인원 원문'),el('pre',row.quotaInfo));parent.appendChild(d);}
    parent.appendChild(el('h3','지난해 공식 입시결과'));renderPrior(parent,data.priorResults);
    if(data.priorResultsTruncated)paragraph(parent,'표시 한도를 넘었습니다. 전체 입시결과는 연결된 공식 원문에서 확인해 주세요.','ae-note');
    paragraph(parent,'대학별 환산점수: 연결 준비 중 · 합격 판정은 제공하지 않습니다.','ae-note');
    if(!compact){
      var td=el('details');td.appendChild(el('summary','대학 전체 전형 안내 (이 모집단위 적용 여부는 원문 확인)'));
      data.universityTypes.forEach(function(t){td.appendChild(el('h3',t.name));td.appendChild(el('pre',t.details||'세부내용 미공개'));source(td,t.sourceUrl,t.sourceTitle);});if(!data.universityTypes.length)paragraph(td,'승인된 전형 안내가 없습니다.');if(data.typesTruncated)paragraph(td,'일부 전형만 표시됩니다. 전체는 공식 원문을 확인해 주세요.');parent.appendChild(td);
      var sd=el('details');sd.appendChild(el('summary','대학 전체 전형 일정 (모집단위별 해당 여부 확인)'));data.universitySchedules.forEach(function(s){paragraph(sd,s.title+' · '+text(s.startDate)+' ~ '+text(s.endDate));if(s.note)sd.appendChild(el('pre',s.note));source(sd,s.sourceUrl,s.sourceTitle);});if(!data.universitySchedules.length)paragraph(sd,'승인된 일정이 없습니다.');if(data.schedulesTruncated)paragraph(sd,'일부 일정만 표시됩니다. 전체는 공식 원문을 확인해 주세요.');parent.appendChild(sd);
    }
  }
  async function detail(row){
    var token=++detailGeneration, selectedYear=year(),panel=byId('ae-detail');panel.hidden=false;panel.textContent='공식자료를 불러오는 중…';
    try{var data=await api('/departments/'+row.id+'?academicYear='+selectedYear);if(token!==detailGeneration)return;renderDetail(panel,data,false);panel.scrollIntoView({behavior:'smooth',block:'start'});}catch(e){if(token===detailGeneration)panel.textContent=e.message;}
  }
  async function compare(){
    var token=++compareGeneration,area=byId('ae-comparison');area.replaceChildren();byId('ae-compare-status').textContent='';
    if(!candidates.length){paragraph(area,'아직 비교 후보가 없습니다. 대학 찾기에서 관심 있는 모집단위를 담아보세요.');return;}
    byId('ae-compare-status').textContent='최신 승인 자료를 조회하고 있습니다…';
    var snapshot=candidates.slice();await Promise.all(snapshot.map(async function(c){
      var card=el('article',null,'ae-compare-card');area.appendChild(card);var content=el('div');card.appendChild(content);var remove=button('후보 삭제',function(){candidates=candidates.filter(function(r){return !(r.id===c.id&&r.year===c.year);});save();compare();});card.appendChild(remove);
      try{var data=await api('/departments/'+c.id+'?academicYear='+c.year);if(token!==compareGeneration)return;renderDetail(content,data,true);}
      catch(e){if(token===compareGeneration){paragraph(content,c.universityName+' '+c.name);paragraph(content,e.message);}}
    }));if(token===compareGeneration)byId('ae-compare-status').textContent='모집요강·지난해 입결을 원문 지표명 그대로 표시했습니다.';
  }
  function scores(){
    var labels={korean:'국어',math:'수학',inquiry1:'탐구1',inquiry2:'탐구2'},parts=[],kind=byId('ae-score-kind').value;
    var avg=byId('ae-grade-average').value;if(avg)parts.push('내신 평균 '+avg+'등급');
    Object.keys(labels).forEach(function(key){var values=['standard','percentile','grade'].map(function(k){var v=byId('ae-'+key+'-'+k).value;return v?{standard:'표준점수 ',percentile:'백분위 ',grade:'등급 '}[k]+v:'';}).filter(Boolean);if(values.length){var subject=byId('ae-'+key+'-subject').value;if(key.indexOf('inquiry')===0&&!subject.trim())throw new Error('점수를 입력한 탐구 과목의 이름도 적어 주세요.');parts.push(labels[key]+'('+subject+') '+values.join(', '));}});
    ['english','history'].forEach(function(key){var v=byId('ae-'+key+'-grade').value;if(v)parts.push((key==='english'?'영어':'한국사')+' '+v+'등급');});
    if(!parts.length)throw new Error('상담에 적용할 성적을 하나 이상 입력해 주세요.');
    return kind+': '+parts.join(' / ');
  }
  doc.querySelectorAll('[data-ae-tab]').forEach(function(b){b.addEventListener('click',function(){tab(b.dataset.aeTab);});});
  byId('ae-year').addEventListener('change',function(){byId('ag-year-select').value=String(year());byId('ag-year-select').dispatchEvent(new Event('change'));byId('ae-score-form').reset();byId('ag-academic-summary').value='';loadUniversities();});
  byId('ag-year-select').addEventListener('change',function(){if(byId('ae-year').value!==this.value){byId('ae-year').value=this.value;byId('ae-score-form').reset();byId('ag-academic-summary').value='';loadUniversities();}});
  byId('ae-univ-filter').addEventListener('input',renderUniversities);
  byId('ae-university').addEventListener('change',function(){++generation;++detailGeneration;byId('ae-results').replaceChildren();byId('ae-detail').hidden=true;});
  byId('ae-search-form').addEventListener('submit',function(e){e.preventDefault();search();});
  byId('ae-compare-refresh').addEventListener('click',compare);
  byId('ae-clear').addEventListener('click',function(){candidates=[];save();compare();});
  byId('ae-score-form').addEventListener('submit',function(e){e.preventDefault();try{var value=scores();byId('ag-academic-summary').value=value;byId('ae-score-status').textContent='상담 성적 요약에 적용했습니다. AI 상담에서 질문을 전송하면 함께 전달됩니다.';}catch(error){byId('ae-score-status').textContent=error.message;}});
  byId('ae-score-clear').addEventListener('click',function(){byId('ae-score-form').reset();byId('ag-academic-summary').value='';byId('ae-score-status').textContent='성적 입력과 상담용 성적 요약을 지웠습니다.';});
  save();loadUniversities();
})(typeof window!=='undefined'?window:globalThis);
