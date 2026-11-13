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
  /** 수능최저 판정 결과를 쉬운 말로. 합격 여부가 아니라 등급 기준 충족 여부만 말한다. */
  function minimumLabel(outcome){return ({MET:'충족',NOT_MET:'미충족',NO_MINIMUM:'수능최저 없음',NEEDS_INPUT:'등급 입력 필요',WITHHELD_NEEDS_REVIEW:'원문 확인 필요',ERROR:'확인할 수 없음'})[outcome]||'확인할 수 없음';}
  /**
   * 모집인원표(quotaInfo = 모집요강 전형별 인원 JSON)에서 정시 전형을 군별로 나눈다.
   * 전형명에 적힌 '(나군)' 표기만 쓰고 추측하지 않는다. '(가·다군)'처럼 군이 둘 이상 적힌 전형과
   * 군 표기가 없는 정시 전형(재직자 전형 등)은 unclear로 둔다. 인원 0·합계 행은 뺀다.
   */
  function jeongsiGroups(quotaInfo){
    var out={'가':[],'나':[],'다':[],unclear:[],readable:true};
    var counts;try{counts=JSON.parse(quotaInfo||'');}catch(e){counts=null;}
    if(!counts||typeof counts!=='object'||Array.isArray(counts)){out.readable=false;return out;}
    Object.keys(counts).forEach(function(name){
      var n=counts[name];
      if(typeof n!=='number'||!(n>0)||name.indexOf('정시')<0||/합계|소계/.test(name))return;
      var row={type:name,count:n},marks=name.match(/[가나다](?:\s*[·,]\s*[가나다])*\s*군/g)||[];
      if(marks.length!==1||/[·,]/.test(marks[0])){out.unclear.push(row);return;}
      out[marks[0].charAt(0)].push(row);
    });
    return out;
  }
  /** 입시결과 구분 표시. OTHER = 원문에 수시·정시 구분이 없는 학교(예: 한국예술종합학교). */
  function roundLabel(roundType){return ({SUSI:'수시',JEONGSI:'정시',OTHER:'수시·정시 구분 없음'})[roundType]||'구분 미상';}
  /** 오늘 날짜(한국 시간) YYYY-MM-DD. */
  function todayKst(now){return new Date((now==null?Date.now():now)+9*3600*1000).toISOString().slice(0,10);}
  /**
   * 후보 대학들의 전형 일정을 날짜순으로 합친다. 같은 대학 후보가 둘이면 일정은 한 번만 넣는다.
   * past: 끝난 날(없으면 시작일)이 오늘보다 앞이면 true. 날짜가 없는 일정은 맨 뒤.
   */
  function mergeSchedules(entries,today){
    var seen={},rows=[];
    (entries||[]).forEach(function(e){(e.schedules||[]).forEach(function(s){
      var key=e.universityName+'|'+s.title+'|'+s.startDate+'|'+s.endDate;if(seen[key])return;seen[key]=true;
      var last=s.endDate||s.startDate;
      rows.push({universityName:e.universityName,title:s.title,startDate:s.startDate||null,endDate:s.endDate||null,note:s.note||null,sourceUrl:s.sourceUrl,sourceTitle:s.sourceTitle,past:!!(last&&today&&last<today)});
    });});
    return rows.sort(function(a,b){if(!a.startDate!==!b.startDate)return a.startDate?-1:1;return (a.startDate||'').localeCompare(b.startDate||'')||a.universityName.localeCompare(b.universityName);});
  }
  var model={text:text,norm:norm,metric:metric,cleanCandidates:cleanCandidates,minimumLabel:minimumLabel,jeongsiGroups:jeongsiGroups,roundLabel:roundLabel,todayKst:todayKst,mergeSchedules:mergeSchedules};
  if(typeof module!=='undefined'&&module.exports) module.exports=model;
  if(!root.document || !root.document.getElementById('ae-workspace'))return;
  var doc=root.document, byId=function(id){return doc.getElementById(id);};
  var member=doc.body.dataset.member==='true', base=member?'/api/dashboard/admissions/explore':'/api/admissions/explore';
  var universities=[], candidates=[], generation=0, detailGeneration=0, compareGeneration=0, showPastSchedules=false;
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
    byId('ae-my-candidates').textContent=candidates.length?candidates.map(function(c){return c.universityName+' '+c.name;}).join(' · '):'대학 찾기에서 관심 모집단위를 담아보세요.';
    byId('ae-compare-ask').disabled=!candidates.length;
  }
  function tab(name){
    if(['home','reform','prepare','my','search','compare','scores','chat'].indexOf(name)<0)return;
    byId('ae-workspace').hidden=name==='chat';
    ['home','reform','prepare','my','search','compare','scores'].forEach(function(p){byId('ae-'+p+'-panel').hidden=p!==name;});
    if(name==='reform')cohort();
    if(name!=='chat'){byId('ag-welcome').hidden=true;byId('ag-chat').hidden=true;}
    else {var hasConversation=byId('ag-messages').children.length>0;byId('ag-chat').hidden=!hasConversation;byId('ag-welcome').hidden=hasConversation;doc.dispatchEvent(new CustomEvent('admissions:show-chat'));}
    doc.querySelectorAll('[data-ae-tab]').forEach(function(b){b.setAttribute('aria-pressed',String(b.dataset.aeTab===name));});
    if(name==='scores'){byId('ae-score-form').hidden=year()===2028;byId('ae-score-year-note').hidden=year()!==2028;}
    if(name==='compare')compare();
    doc.body.dataset.admissionsView=name;
  }
  // 학년별 대입 학년도와 대학별 계획 발표 시기(입학 연도 기준 1년 10개월 전, 4월 말). 2028학년도부터 같은 제도다.
  var COHORTS={
    m2:{year:2031,entry:'2028년 3월 고교 입학',plan:'2029년 4월',system:'2028학년도와 같은 내신 5등급제·통합형 수능(현재 발표 기준)'},
    m3:{year:2030,entry:'2027년 3월 고교 입학',plan:'2028년 4월',system:'2028학년도와 같은 내신 5등급제·통합형 수능(현재 발표 기준)'},
    h1:{year:2029,entry:'지금 고1',plan:'2027년 4월',system:'2028학년도와 같은 내신 5등급제·통합형 수능(현재 발표 기준)'},
    h2:{year:2028,entry:'지금 고2',plan:'2026년 4월 발표됨',system:'바뀐 제도를 처음 적용받는 입시'},
    h3:{year:2027,entry:'지금 고3',plan:'모집요강까지 발표됨',system:'지금 제도(9등급제·선택형 수능)의 마지막 입시'}
  };
  function cohort(){
    var c=COHORTS[byId('ae-cohort-grade').value]||COHORTS.m3,box=byId('ae-cohort-result');box.replaceChildren();
    var h=el('p',c.year+'학년도 대입','ae-cohort-year');box.appendChild(h);
    var ul=el('ul');[c.entry,c.system,'대학별 계획 발표: '+c.plan].forEach(function(t){ul.appendChild(el('li',t));});box.appendChild(ul);
    if(c.year===2027)box.appendChild(button('2027학년도 대학 자료 보기',function(){byId('ae-year').value='2027';byId('ae-year').dispatchEvent(new Event('change'));tab('search');}));
  }
  function askReform(q){
    byId('ag-year-select').value='2028';byId('ag-year-select').dispatchEvent(new Event('change'));
    var grade=byId('ae-cohort-grade').selectedOptions[0];if(grade&&!byId('ag-grade').value)byId('ag-grade').value=grade.textContent;
    tab('chat');doc.dispatchEvent(new CustomEvent('admissions:question',{detail:{question:q}}));
  }
  function scoreMode(){
    var onlyGrades=byId('ae-score-kind').value==='내신';
    doc.querySelectorAll('[data-ae-csat]').forEach(function(area){area.hidden=onlyGrades;area.querySelectorAll('input,select').forEach(function(input){input.disabled=onlyGrades;});});
  }
  function prepare(){
    var nextYear=byId('ae-prepare-year').value;
    if(byId('ae-year').value!==nextYear){byId('ae-year').value=nextYear;byId('ae-year').dispatchEvent(new Event('change'));}
    byId('ag-grade').value=byId('ae-prepare-grade').value;
    byId('ag-admission-track').value=byId('ae-prepare-track').value;
    byId('ag-target-majors').value=byId('ae-prepare-major').value.trim();
    byId('ae-major').value=byId('ae-prepare-major').value.trim();
    var kind=byId('ae-prepare-kind').value;
    if(kind==='none'){resetScores();tab('search');return;}
    if(year()===2028){tab('chat');return;}
    if(byId('ae-score-kind').value!==kind){byId('ae-score-kind').value=kind;byId('ae-score-kind').dispatchEvent(new Event('change'));}
    scoreMode();tab('scores');
  }
  function askCandidates(){
    if(!candidates.length)return;
    var selectedYear=candidates[0].year;
    if(candidates.some(function(c){return c.year!==selectedYear;})){byId('ae-compare-status').textContent='같은 학년도의 후보만 남긴 뒤 비교 상담을 시작해 주세요.';return;}
    byId('ag-year-select').value=String(selectedYear);byId('ag-year-select').dispatchEvent(new Event('change'));
    byId('ag-target-universities').value=Array.from(new Set(candidates.map(function(c){return c.universityName;}))).join(', ').slice(0,200);
    byId('ag-target-majors').value=Array.from(new Set(candidates.map(function(c){return c.name;}))).join(', ').slice(0,200);
    tab('chat');
    doc.dispatchEvent(new CustomEvent('admissions:question',{detail:{question:selectedYear+'학년도 지원 후보 '+candidates.map(function(c){return c.universityName+' '+c.name;}).join(' / ')+'를 비교해 줘. 공식 모집요강과 '+(selectedYear-1)+'학년도 입결의 전형·지표·출처를 구분하고, 내 성적 요약이 있으면 참고해 줘. 비교할 근거가 없으면 미확보라고 말하고 합격 가능성을 단정하지 마.'}}));
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
    rows.forEach(function(row){var tr=el('tr');var c1=el('td',row.resultYear+'학년도(지난해) · '+roundLabel(row.roundType)+' '+(row.groupName||'')+' '+row.typeName);var c2=el('td',row.metricLabel||'모집·경쟁·충원');var c3=el('td');
      if(row.metricLabel)c3.textContent=metric(row)+(row.metricUnit?' ['+row.metricUnit+']':'');
      else c3.textContent='모집 '+text(row.recruitCount)+' / 지원 '+text(row.applicantCount)+' / 경쟁률 '+text(row.competitionRate)+' / 충원 '+text(row.extraAdmittedCount);
      source(c2,row.sourceUrl||row.documentUrl,row.sourceTitle||'공식 입시결과');if(row.evidencePages)c2.appendChild(el('small','근거 쪽: '+row.evidencePages));
      tr.append(c1,c2,c3);body.appendChild(tr);
    });table.appendChild(body);var wrap=el('div',null,'ae-table-scroll');wrap.appendChild(table);parent.appendChild(wrap);
  }
  function renderDetail(parent,data,compact){
    var row=data.department;parent.replaceChildren();paragraph(parent,row.universityName+' · '+data.academicYear+'학년도','ae-eyebrow');parent.appendChild(el('h2',row.name));
    if(data.academicYear===2028)paragraph(parent,'2028학년도 계획 자료 · 변경될 수 있습니다.','ae-note');
    var actions=el('div',null,'ae-actions');if(!compact)actions.appendChild(button('비교에 담기',function(){keep(row);}));if(data.academicYear===2027){actions.appendChild(button('수능최저 확인',function(){minimum(parent,data);}));actions.appendChild(button('환산점수 계산',function(){calculate(parent,data);}));}actions.appendChild(button('이 대학 AI 상담',function(){ask({id:Number(row.id),year:data.academicYear,name:row.name,universityName:row.universityName});},'ae-primary'));parent.appendChild(actions);
    source(parent,row.sourceUrl,row.sourceTitle||'공식 모집요강');
    if(row.quotaInfo){var d=el('details');d.append(el('summary','모집인원 원문'),el('pre',row.quotaInfo));parent.appendChild(d);}
    parent.appendChild(el('h3','지난해 공식 입시결과'));renderPrior(parent,data.priorResults);
    if(data.priorResultsTruncated)paragraph(parent,'표시 한도를 넘었습니다. 전체 입시결과는 연결된 공식 원문에서 확인해 주세요.','ae-note');
    paragraph(parent,'환산점수는 내 성적의 확정 수능 성적으로 계산합니다. 전형별 적용 범위·지원자격을 원문에서 확인해 주세요. 합격 판정은 제공하지 않습니다.','ae-note');
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
    var token=++compareGeneration,area=byId('ae-comparison'),round=byId('ae-compare-round').value;area.replaceChildren();byId('ae-compare-status').textContent='';
    byId('ae-compare-groups').replaceChildren();byId('ae-compare-schedule').replaceChildren();
    if(!candidates.length){paragraph(area,'아직 비교 후보가 없습니다. 대학 찾기에서 관심 있는 모집단위를 담아보세요.');return;}
    byId('ae-compare-status').textContent='최신 승인 자료를 조회하고 있습니다…';
    var snapshot=candidates.slice(),loaded=snapshot.map(function(){return null;});await Promise.all(snapshot.map(async function(c,i){
      var card=el('article',null,'ae-compare-card');area.appendChild(card);var content=el('div');card.appendChild(content);var remove=button('후보 삭제',function(){candidates=candidates.filter(function(r){return !(r.id===c.id&&r.year===c.year);});save();compare();});card.appendChild(remove);
      try{var data=await api('/departments/'+c.id+'?academicYear='+c.year+'&round='+round);if(token!==compareGeneration)return;loaded[i]=data;renderDetail(content,data,true);}
      catch(e){if(token===compareGeneration){paragraph(content,c.universityName+' '+c.name);paragraph(content,e.message);}}
    }));if(token!==compareGeneration)return;
    var ok=loaded.filter(Boolean);
    if(round!=='SUSI')renderGroups(byId('ae-compare-groups'),ok);
    renderSchedules(byId('ae-compare-schedule'),ok);
    byId('ae-compare-status').textContent='모집요강·지난해 입결을 원문 지표명 그대로 표시했습니다.';
  }
  /** 후보를 정시 군별로 정리한다. 모집요강 모집인원표의 전형명 표기만 쓴다. */
  function renderGroups(parent,list){
    parent.appendChild(el('h2','정시 군별 정리'));
    paragraph(parent,'모집요강 모집인원표에 적힌 정시 전형의 군 표기로 나눴습니다. 인원은 원문 값입니다.','ae-muted');
    var grid=el('div',null,'ae-group-grid'),byGroup={'가':[],'나':[],'다':[]},unclear=[],none=[];
    list.forEach(function(data){
      var d=data.department,label=d.universityName+' '+d.name,g=jeongsiGroups(d.quotaInfo);
      if(!g.readable){unclear.push({label:label,text:'모집인원표를 읽을 수 없음 — 공식 원문 확인'});return;}
      var any=false;['가','나','다'].forEach(function(k){if(g[k].length){any=true;byGroup[k].push({label:label,rows:g[k],universityName:d.universityName});}});
      g.unclear.forEach(function(r){any=true;unclear.push({label:label,text:r.type+' '+r.count+'명 — 군을 하나로 정할 수 없음, 원문 확인'});});
      if(!any)none.push(label);
    });
    ['가','나','다'].forEach(function(k){
      var col=el('section',null,'ae-group');col.appendChild(el('h3',k+'군'));
      if(!byGroup[k].length)paragraph(col,'후보 없음','ae-muted');
      byGroup[k].forEach(function(item){var p=el('p');p.appendChild(el('strong',item.label));item.rows.forEach(function(r){p.appendChild(el('small',r.type+' '+r.count+'명'));});col.appendChild(p);});
      grid.appendChild(col);
    });
    parent.appendChild(grid);
    unclear.forEach(function(u){paragraph(parent,u.label+': '+u.text,'ae-muted');});
    if(none.length)paragraph(parent,'정시 모집인원이 없는 후보: '+none.join(', '),'ae-muted');
    var crowded=['가','나','다'].filter(function(k){return byGroup[k].length>1;});
    if(crowded.length)paragraph(parent,crowded.map(function(k){return k+'군에 후보가 '+byGroup[k].length+'개('+byGroup[k].map(function(i){return i.label;}).join(', ')+')';}).join(' / ')+'입니다. 정시모집은 모집 군이 같은 대학에 복수지원할 수 없습니다(교육대학 포함, 산업대학·전문대학 제외). 같은 대학 안에서 같은 군의 여러 모집단위에 지원할 수 있는지는 각 대학 정시 모집요강의 복수지원 항목을 확인해 주세요.','ae-note');
  }
  /** 후보 대학들의 전형 일정을 한 줄로 모은다. 대학 전체 일정이라 모집단위·전형 해당 여부는 원문으로 확인. */
  function renderSchedules(parent,list){
    var rows=mergeSchedules(list.map(function(data){return {universityName:data.department.universityName,schedules:data.universitySchedules};}),todayKst());
    parent.appendChild(el('h2','후보 대학 전형 일정'));
    paragraph(parent,'후보 대학의 전체 일정을 날짜순으로 모았습니다. 일정 이름에 적힌 전형이 내가 지원할 전형인지 원문에서 확인해 주세요.','ae-muted');
    var past=rows.filter(function(r){return r.past;}).length,showPast=showPastSchedules;
    var toggle=el('label',null,'ae-check'),box=el('input');box.type='checkbox';box.checked=showPast;box.addEventListener('change',function(){showPastSchedules=box.checked;parent.replaceChildren();renderSchedules(parent,list);});toggle.append(box,doc.createTextNode(' 지난 일정도 보기 ('+past+'건)'));parent.appendChild(toggle);
    var shown=rows.filter(function(r){return showPast||!r.past;});
    if(!shown.length){paragraph(parent,rows.length?'남은 일정이 없습니다.':'승인된 일정이 없습니다.','ae-muted');return;}
    var table=el('table'),head=el('thead'),hr=el('tr');['날짜','대학','일정'].forEach(function(t){var th=el('th',t);th.scope='col';hr.appendChild(th);});head.appendChild(hr);table.appendChild(head);var body=el('tbody');
    shown.forEach(function(r){var tr=el('tr');if(r.past)tr.className='ae-past';var date=r.startDate?(r.endDate&&r.endDate!==r.startDate?r.startDate+' ~ '+r.endDate:r.startDate):'날짜 미공개';var c3=el('td',r.title);if(r.note)c3.appendChild(el('small',r.note));source(c3,r.sourceUrl,r.sourceTitle);tr.append(el('td',date),el('td',r.universityName),c3);body.appendChild(tr);});
    table.appendChild(body);var wrap=el('div',null,'ae-table-scroll');wrap.appendChild(table);parent.appendChild(wrap);
  }
  // 선택 표시는 학생이 고른 묶음만 보낸다. 고르지 않은 묶음을 0으로 보내면 서버가 '해당 없음'으로 판정해
  // 수능최저를 충족한 학생도 미충족으로 나올 수 있다(서버는 묶음이 없으면 입력을 요청한다).
  function selectionFlags(out,kor,math){
    if(kor){out.KOR_SEL_HW=Number(kor==='화법과 작문');out.KOR_SEL_EM=Number(kor==='언어와 매체');}
    if(math){out.MATH_SEL_PROB=Number(math==='확률과 통계');out.MATH_SEL_CALC=Number(math==='미적분');out.MATH_SEL_GEOM=Number(math==='기하');}
  }
  function trackFlags(out,prefix,group){if(!group)return;['SOC','SCI','VOC','SCI2'].forEach(function(flag){out[prefix+'_'+flag]=Number(group===flag||(flag==='SCI'&&group==='SCI2'));});}
  function scoreInputs(){
    var out={}, prefixes={korean:'KOR',math:'MATH',inquiry1:'INQ1',inquiry2:'INQ2'},hasAny=false;
    Object.keys(prefixes).forEach(function(key){
      var hasScore=false;
      ['standard','percentile','grade'].forEach(function(field){var value=byId('ae-'+key+'-'+field).value;if(value!==''){out[prefixes[key]+'_'+{standard:'STD',percentile:'PCT',grade:'GRD'}[field]]=Number(value);hasAny=true;hasScore=true;}});
      if(key.indexOf('inquiry')===0){var group=byId('ae-'+key+'-kind').value;if(hasScore&&(!group||!byId('ae-'+key+'-subject').value.trim()))throw new Error('탐구 성적의 과목명과 계열(사회/과학I/과학II/직업)을 선택해 주세요.');
        trackFlags(out,prefixes[key],group);}
    });
    var kor=byId('ae-korean-subject').value,math=byId('ae-math-subject').value;
    selectionFlags(out,kor,math);
    ['english','history','foreign'].forEach(function(key){var v=byId('ae-'+key+'-grade').value;if(v!==''){out[{english:'ENG_GRD',history:'HIST_GRD',foreign:'FL_GRD'}[key]]=Number(v);hasAny=true;}});out.FL_TAKEN=Number(byId('ae-foreign-grade').value!=='');
    if(!hasAny)throw new Error('내 성적에서 확정 수능 성적을 입력해 주세요.');return out;
  }
  function inputLabel(key){var labels={KOR:'국어',MATH:'수학',INQ1:'탐구1',INQ2:'탐구2',ENG:'영어',HIST:'한국사',FL:'제2외국어·한문'};var parts=key.split('_'),subject=labels[parts.shift()]||key,field=parts.join('_');return subject+' '+({STD:'표준점수',PCT:'백분위',GRD:'등급',STD_MAX:'전국 최고 표준점수',CONV:'대학 변환표준점수',CONV_MAX:'대학 변환표준점수 최고점',SEL:'선택과목',TRACK:'계열(사회/과학/직업)',TAKEN:'응시 여부',SEL_HW:'화법과 작문 선택',SEL_EM:'언어와 매체 선택',SEL_PROB:'확률과 통계 선택',SEL_CALC:'미적분 선택',SEL_GEOM:'기하 선택',SOC:'사회탐구',SCI:'과학탐구',VOC:'직업탐구',SCI2:'과학탐구Ⅱ'}[field]||field);}
  async function calculate(parent,data){
    if(byId('ae-score-kind').value!=='수능 확정 성적'){tab('scores');byId('ae-score-status').textContent='환산 계산에는 확정 수능 성적이 필요합니다. 성적 종류와 점수를 확인한 뒤 모집단위에서 계산해 주세요.';return;}
    if(!byId('ae-score-form').reportValidity()){tab('scores');return;}
    var inputs;try{inputs=scoreInputs();}catch(e){tab('scores');byId('ae-score-status').textContent=e.message;return;}
    var old=parent.querySelector('.ae-calculation');if(old)old.remove();var panel=el('section',null,'ae-calculation');panel.setAttribute('aria-live','polite');parent.appendChild(panel);panel.textContent='공식 계산식을 조회하고 있습니다…';
    try{
      var endpoint=member?base+'/score':'/api/admissions/jeongsi/score';
      var res=await(member?root.fetchAuth:root.fetch)(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({university:data.department.universityName,academicYear:2027,departmentName:data.department.name,scores:inputs})});
      var result=await res.json().catch(function(){return null;});if(!panel.isConnected)return;
      if(!res.ok)throw new Error(res.status===503?'현재 환산 계산을 이용할 수 없습니다.':res.status===429?'요청이 많습니다. 잠시 후 다시 계산해 주세요.':res.status===400?'입력 성적과 선택한 대학을 확인해 주세요.':'환산 계산에 실패했습니다.');
      panel.replaceChildren(el('h3','대학별 수능 환산점수'));paragraph(panel,result.notice||'공식 계산 결과');
      if(!result.formulas||!result.formulas.length){paragraph(panel,'이 모집단위에 승인된 계산식이 아직 없습니다. 공식 모집요강과 AI 상담을 이용해 주세요.');return;}
      result.formulas.forEach(function(f){var card=el('article',null,'ae-formula');card.appendChild(el('h4',f.label||f.formulaKey));paragraph(card,'전형: '+(f.typeNames||[]).join(' / '));if(f.unitScopeText)paragraph(card,'적용 범위: '+f.unitScopeText);
        if(f.outcome==='CALCULATED')paragraph(card,text(f.score)+' / '+text(f.csatPoints)+'점','ae-score-result');
        else if(f.outcome==='WITHHELD_NEEDS_REVIEW')paragraph(card,'계산 보류: '+text(f.needsReviewReason),'ae-note');
        else if(f.outcome==='NEEDS_INPUT'){if((f.missingInputs||[]).length)paragraph(card,'학생 입력 필요: '+f.missingInputs.map(inputLabel).join(', '));if((f.pendingInputs||[]).length)paragraph(card,'공식 자료 연결 필요: '+f.pendingInputs.map(inputLabel).join(', '));}
        else paragraph(card,'계산식을 확인하고 있습니다. 점수를 안내할 수 없습니다.');
        if(f.eligibilityText)paragraph(card,'지원자격: '+f.eligibilityText);if(f.notInCsatScoreText)paragraph(card,'수능 점수 외 반영요소: '+f.notInCsatScoreText);if(f.roundingText)paragraph(card,'점수 처리: '+f.roundingText);
        if((f.defaultedInputs||[]).length)paragraph(card,'미입력 선택 표시를 0으로 처리한 항목: '+f.defaultedInputs.map(inputLabel).join(', '));
        if((f.uncertain||[]).length)paragraph(card,'원문 확인 사항: '+f.uncertain.join(' / '));source(card,f.sourceUrl,f.sourceTitle||'공식 계산식 출처');if(f.evidencePages)paragraph(card,'근거 쪽: '+f.evidencePages);panel.appendChild(card);
      });paragraph(panel,'입력값을 바꾸면 다시 계산해 주세요. 지난해 입결과 올해 환산점수는 점수 체계가 다를 수 있습니다.','ae-note');
    }catch(e){if(panel.isConnected)panel.textContent=e.message;}
  }
  function gradeInputs(){
    var out={},has=false,prefixes={korean:'KOR',math:'MATH',inquiry1:'INQ1',inquiry2:'INQ2'};
    Object.keys(prefixes).forEach(function(key){var v=byId('ae-'+key+'-grade').value;if(v!==''){out[prefixes[key]+'_GRD']=Number(v);has=true;}
      if(key.indexOf('inquiry')===0){var group=byId('ae-'+key+'-kind').value;if(v!==''&&!group)throw new Error('탐구 등급을 입력했으면 계열(사회/과학I/과학II/직업)도 선택해 주세요.');trackFlags(out,prefixes[key],group);}});
    var kor=byId('ae-korean-subject').value,math=byId('ae-math-subject').value;
    selectionFlags(out,kor,math);
    ['english','history','foreign'].forEach(function(key){var v=byId('ae-'+key+'-grade').value;if(v!==''){out[{english:'ENG_GRD',history:'HIST_GRD',foreign:'FL_GRD'}[key]]=Number(v);has=true;}});out.FL_TAKEN=Number(byId('ae-foreign-grade').value!=='');
    if(!has)throw new Error('내 성적에서 영역별 등급을 입력해 주세요(가채점 등급도 됩니다).');return out;
  }
  async function minimum(parent,data){
    var grades;try{grades=gradeInputs();}catch(e){tab('scores');byId('ae-score-status').textContent=e.message;return;}
    var kind=byId('ae-score-kind').value;
    var old=parent.querySelector('.ae-minimum');if(old)old.remove();var panel=el('section',null,'ae-minimum ae-calculation');panel.setAttribute('aria-live','polite');parent.appendChild(panel);panel.textContent='수능최저 기준을 확인하고 있습니다…';
    try{
      var res=await root.fetch('/api/admissions/susi/minimum-check',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({university:data.department.universityName,academicYear:2027,departmentName:data.department.name,grades:grades})});
      var result=await res.json().catch(function(){return null;});if(!panel.isConnected)return;
      if(!res.ok)throw new Error(res.status===503?'현재 수능최저 확인을 이용할 수 없습니다.':res.status===429?'요청이 많습니다. 잠시 후 다시 확인해 주세요.':res.status===400?'입력 등급과 선택한 대학을 확인해 주세요.':'수능최저 확인에 실패했습니다.');
      panel.replaceChildren(el('h3','수시 수능최저학력기준 확인'));paragraph(panel,kind+' 등급 기준 · '+(result.notice||''));
      if(!result.rules||!result.rules.length){paragraph(panel,'이 대학의 수능최저 자료가 아직 연결되지 않았습니다. 공식 모집요강을 확인해 주세요.');return;}
      result.rules.forEach(function(r){var card=el('article',null,'ae-formula');card.appendChild(el('h4',(r.typeNames||[]).join(' / ')+(r.label&&r.outcome!=='NO_MINIMUM'?' · '+r.label:'')));
        paragraph(card,minimumLabel(r.outcome),'ae-score-result');
        if(r.outcome==='NEEDS_INPUT'&&(r.missingInputs||[]).length)paragraph(card,'필요한 입력: '+r.missingInputs.map(inputLabel).join(', ')+' (내 성적에서 선택과목·탐구 계열도 골라 주세요)');
        if(r.outcome==='WITHHELD_NEEDS_REVIEW')paragraph(card,'확인 필요 사유: '+text(r.needsReviewReason),'ae-note');
        if(r.rawText)paragraph(card,(r.outcome==='NO_MINIMUM'?'원문: ':'원문 기준: ')+r.rawText);
        if(r.requiredAreasText)paragraph(card,'응시 필수 영역: '+r.requiredAreasText);
        if(r.unitScopeText)paragraph(card,'적용 모집단위: '+r.unitScopeText);
        if((r.defaultedInputs||[]).length&&r.outcome!=='NO_MINIMUM')paragraph(card,'선택하지 않은 과목 표시는 해당 없음으로 보았습니다: '+r.defaultedInputs.map(inputLabel).join(', '));
        if((r.uncertain||[]).length)paragraph(card,'원문 확인 사항: '+r.uncertain.join(' / '));
        source(card,r.sourceUrl,r.sourceTitle||'공식 수시 모집요강');if(r.evidencePages)paragraph(card,'근거 쪽: '+r.evidencePages);panel.appendChild(card);});
      paragraph(panel,'수능최저 충족은 합격을 뜻하지 않습니다. 지원한 전형과 모집단위가 맞는지 원문에서 꼭 확인해 주세요.','ae-note');
    }catch(e){if(panel.isConnected)panel.textContent=e.message;}
  }
  function scores(){
    var labels={korean:'국어',math:'수학',inquiry1:'탐구1',inquiry2:'탐구2'},parts=[],kind=byId('ae-score-kind').value;
    var avg=byId('ae-grade-average').value;if(avg)parts.push('내신 평균 '+avg+'등급');
    if(kind==='내신'){if(!avg)throw new Error('내신 평균등급을 입력해 주세요.');return kind+': '+parts.join(' / ');}
    Object.keys(labels).forEach(function(key){var values=['standard','percentile','grade'].map(function(k){var v=byId('ae-'+key+'-'+k).value;return v?{standard:'표준점수 ',percentile:'백분위 ',grade:'등급 '}[k]+v:'';}).filter(Boolean);if(values.length){var subject=byId('ae-'+key+'-subject').value;if(key.indexOf('inquiry')===0&&!subject.trim())throw new Error('점수를 입력한 탐구 과목의 이름도 적어 주세요.');parts.push(labels[key]+'('+subject+') '+values.join(', '));}});
    ['english','history','foreign'].forEach(function(key){var v=byId('ae-'+key+'-grade').value;if(v)parts.push(({english:'영어',history:'한국사',foreign:'제2외국어·한문'}[key])+' '+v+'등급');});
    if(!parts.length)throw new Error('상담에 적용할 성적을 하나 이상 입력해 주세요.');
    return kind+': '+parts.join(' / ');
  }
  doc.querySelectorAll('[data-ae-tab]').forEach(function(b){b.addEventListener('click',function(){tab(b.dataset.aeTab);});});
  byId('ae-cohort-grade').addEventListener('change',cohort);
  doc.querySelectorAll('.ae-reform-ask').forEach(function(b){b.addEventListener('click',function(){askReform(b.dataset.q);});});
  doc.addEventListener('admissions:navigate',function(e){if(e.detail)tab(e.detail.tab);});
  byId('ae-prepare-form').addEventListener('submit',function(e){e.preventDefault();prepare();});
  byId('ae-score-kind').addEventListener('change',function(){scoreMode();byId('ae-score-next').hidden=true;byId('ag-academic-summary').value='';byId('ae-score-status').textContent='성적 종류가 바뀌었습니다. 입력값을 확인하고 다시 적용해 주세요.';});
  function resetScores(){byId('ae-score-form').reset();byId('ag-academic-summary').value='';byId('ae-score-next').hidden=true;byId('ae-score-status').textContent='';scoreMode();}
  byId('ae-year').addEventListener('change',function(){byId('ag-year-select').value=String(year());byId('ag-year-select').dispatchEvent(new Event('change'));resetScores();loadUniversities();});
  byId('ag-year-select').addEventListener('change',function(){byId('ae-prepare-year').value=this.value;if(byId('ae-year').value!==this.value){byId('ae-year').value=this.value;resetScores();loadUniversities();}});
  byId('ae-univ-filter').addEventListener('input',renderUniversities);
  byId('ae-university').addEventListener('change',function(){++generation;++detailGeneration;byId('ae-results').replaceChildren();byId('ae-detail').hidden=true;});
  byId('ae-search-form').addEventListener('submit',function(e){e.preventDefault();search();});
  byId('ae-compare-refresh').addEventListener('click',compare);
  byId('ae-compare-round').addEventListener('change',compare);
  byId('ae-compare-ask').addEventListener('click',askCandidates);
  byId('ae-clear').addEventListener('click',function(){candidates=[];save();compare();});
  byId('ae-score-form').addEventListener('submit',function(e){e.preventDefault();try{var value=scores();byId('ag-academic-summary').value=value;byId('ae-score-next').hidden=false;byId('ae-score-status').textContent='상담 성적 요약에 적용했습니다. AI 상담에서 질문을 전송하면 함께 전달됩니다.';}catch(error){byId('ae-score-next').hidden=true;byId('ae-score-status').textContent=error.message;}});
  byId('ae-score-clear').addEventListener('click',function(){resetScores();byId('ae-score-status').textContent='성적 입력과 상담용 성적 요약을 지웠습니다.';});
  byId('ae-score-form').addEventListener('input',function(){byId('ae-score-next').hidden=true;byId('ag-academic-summary').value='';});
  save();scoreMode();tab('home');loadUniversities();
})(typeof window!=='undefined'?window:globalThis);
