/* Unified workspace: previews are server data; only a clicked approval executes writes. */
(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const API = '/api/dashboard/assistant/v1';
  const state = { user: null, context: null, academy: null, progress: null, business: null, generation: 0, draftRevision: 0, busy: false, ready: false, tour: false };
  const labels = { occurredOn:'거래일', direction:'수입·지출', amount:'금액 (원)', counterparty:'거래처', payMethod:'결제수단', externalAccount:'기존 원본 계정명', evidenceType:'증빙 유형', name:'이름 / 반 이름', grade:'학년', schoolName:'학교', parentName:'학부모 이름', parentPhone:'학부모 연락처', studentId:'학생 ID', classId:'반 ID', teacherId:'담당 강사 ID', description:'설명', eventType:'출결 구분', occurredAt:'기록 시각 (한국 시간)', note:'메모', title:'상담 제목', content:'상담 내용', consultId:'상담 ID', batchId:'가져오기 배치 ID', rowId:'거래 행 ID', accountId:'계정과목 ID' };
  const operations = {
    STUDENT_CREATE:['name','grade','schoolName','parentName','parentPhone'], STUDENT_UPDATE:['studentId','name','grade','schoolName','parentName','parentPhone'],
    CLASS_CREATE:['name','teacherId','description'], CLASS_UPDATE:['classId','name','teacherId','description'], CLASS_ASSIGN:['classId','studentId'],
    ATTENDANCE_RECORD:['studentId','eventType','occurredAt','note'], CONSULT_CREATE:['studentId','title','content'], CONSULT_MESSAGE:['consultId','content'],
    LEDGER_ENTRY:['occurredOn','direction','amount','counterparty','description','payMethod','externalAccount','evidenceType'], LEDGER_IMPORT:[], LEDGER_ACCOUNT:['batchId','rowId','accountId']
  };
  const tours = [
    ['반가워요. 첫 업무를 함께 시작해요','학원 운영과 장부 현황을 한 화면에서 확인하고, 아래 AI 업무 비서에게 평소 쓰는 말로 업무를 알려주세요.','uaMenus'],
    ['학생과 반을 연결하세요','학생 메뉴에서 정보를 확인하고 반·수업 메뉴에서 학생을 배정합니다. 목록에서 정확한 학생·반 ID를 확인한 뒤 비서에게 알려주세요.','uaStudents'],
    ['오늘의 출결을 기록하세요','출결 메뉴에서 반별 출결부를 확인하세요. 비서에게 “학생 ID 1 결석 기록해줘”라고 요청하면 기록할 내용과 한국 시간 기준 시각을 먼저 보여드립니다.','uaAttendance'],
    ['상담 기록을 이어가세요','상담을 작성하거나 열린 상담에 후속 메시지를 남길 수 있습니다. 학부모 알림이 전달될 수 있으니 대상과 내용을 확인해 주세요.','uaConsult'],
    ['사업체별 거래와 증빙을 확인하세요','상단에서 사업체를 선택한 뒤 거래·증빙 CSV를 올리세요. 원본과 증빙 유형을 보관하고, 장부에 저장한 수·중복·검토 대기를 구분해 보여드립니다.','uaLedger'],
    ['초안 확인 → 승인 → 결과 확인','대화창의 초안에서 대상과 내용을 검토한 뒤 승인하세요. 실제 API 실행 후 저장 결과를 다시 조회합니다. 연결 오류로 결과가 불명확하면 기존 화면에서 확인하세요.','uaReview']
  ];
  function error(e) { $('uaError').hidden=false; $('uaError').textContent=e.message || '요청을 처리할 수 없습니다.'; }
  async function json(path, options={}) {
    const res=await fetch(API+path,{credentials:'same-origin',cache:'no-store',...options});
    const body=await res.json().catch(()=>({}));
    if(!res.ok) throw new Error(body.message || body.detail || body.error || '요청 실패 ('+res.status+'). 로그인·권한·연결 상태를 확인하세요.');
    return body;
  }
  const post = (path,body) => json(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  function el(tag,text,cls) { const e=document.createElement(tag); if(text!=null)e.textContent=text; if(cls)e.className=cls; return e; }
  function name(op) { return [...$('uaOperation').options].find(x=>x.value===op)?.textContent || op; }
  function money(n) { return Number(n).toLocaleString('ko-KR')+'원'; }
  function isLedger() { return $('uaOperation').value.startsWith('LEDGER_'); }
  function scopeText() { return isLedger() ? '사업체: '+($('bizSelect').selectedOptions[0]?.textContent || '선택 필요') : '학원 ID: '+(state.context?.academyId || '연결 없음')+' · '+(state.context?.role==='ACADEMY_OWNER'?'원장':'강사 / 조회만 가능'); }
  function fields(values={}) {
    const op=$('uaOperation').value;
    $('uaFields').replaceChildren(); $('uaCreateDraft').hidden=op==='LEDGER_IMPORT';
    for(const key of operations[op]||[]) {
      const wrap=el('div',null,key==='content'?'ua-field-wide':''); const label=el('label',key==='name'?(op.startsWith('STUDENT')?'학생 이름':'반 이름'):labels[key]); label.htmlFor='uaField_'+key;
      let input;
      if(key==='direction'||key==='evidenceType') { input=el('select');const options=key==='direction'?[['','선택하세요'],['INCOME','수입'],['EXPENSE','지출']]:[['','미확인'],['TAX_INVOICE','세금계산서'],['CREDIT_CARD','신용카드'],['CASH_RECEIPT','현금영수증'],['INVOICE','계산서'],['NONE','없음'],['OTHER','기타']];for(const [v,t] of options){const o=el('option',t);o.value=v;input.append(o);} }
      else if(key==='eventType') { input=el('select'); for(const [v,t] of [['CHECK_IN','등원'],['CHECK_OUT','하원'],['ABSENT','결석'],['LATE','지각'],['EARLY_LEAVE','조퇴']]) { const o=el('option',t);o.value=v; input.append(o); } }
      else { input=el(key==='content'?'textarea':'input'); if(key!=='content')input.type=key.endsWith('Id')||key==='amount'?'number':key==='occurredAt'?'datetime-local':key==='occurredOn'?'date':'text'; if(key.endsWith('Id')) {input.min='1';input.step='1';} input.maxLength=2000; }
      input.id='uaField_'+key; input.name=key; input.value=values[key]??'';
      input.required=['name','studentId','classId','consultId','batchId','rowId','accountId','title','content','eventType','occurredOn','amount','direction'].includes(key) || (key==='teacherId'&&op==='CLASS_CREATE');
      wrap.append(label,input); $('uaFields').append(wrap);
    }
    if($('uaField_eventType')&&!values.eventType)$('uaField_eventType').value='CHECK_IN';
    $('uaScope').textContent=scopeText();
  }
  function highlight() {
    document.querySelectorAll('.ua-tour-target').forEach(x=>{x.classList.remove('ua-tour-target');x.removeAttribute('aria-current');});
    if(!state.tour||!state.progress)return;
    const i=state.progress.step,t=tours[i]; $(t[2]).classList.add('ua-tour-target');$(t[2]).setAttribute('aria-current','step');
    if(i===1){$('uaClasses').classList.add('ua-tour-target');$('uaClasses').setAttribute('aria-current','step');}
    $('uaStep').textContent=(i+1)+' / 6 · 사용법 안내'; $('uaTourTitle').textContent=t[0];
    $('uaTourText').textContent=t[1]+(i===0?(state.user?.role==='ADMIN'?' 관리자는 허용된 사업체를 선택해 현황을 볼 수 있습니다.':' 강사는 연결된 학원·사업체의 업무만 볼 수 있습니다. AI 쓰기는 원장 연결 계정에서 승인할 수 있습니다.'):'');
    $('uaTourProgress').value=i+1; $('uaBack').disabled=i===0; $('uaNext').textContent=i===5?'안내 완료':'다음';
  }
  async function saveProgress(step,completed) {
    const p=await json('/progress',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({step,completed,version:state.progress.version})}); state.progress=p;
  }
  function showTour(open) {state.tour=open;$('uaTour').hidden=!open;highlight();}
  async function advance(delta) {
    $('uaNext').disabled=$('uaBack').disabled=true;
    try { const i=state.progress.step; await saveProgress(Math.max(0,Math.min(5,i+delta)),i===5&&delta===1); if(state.progress.completed){showTour(false);$('uaMessage').textContent='안내를 마쳤습니다. 언제든 사용법 안내에서 다시 볼 수 있습니다.';}else highlight(); }
    finally {$('uaNext').disabled=false;if(state.progress)$('uaBack').disabled=state.progress.step===0;}
  }
  async function overview() {
    const g=state.generation,b=state.business;
    const query=new URLSearchParams();if(b)query.set('businessId',b);if(state.academy)query.set('academyId',state.academy);const data=await json('/overview?'+query); if(g!==state.generation)return;
    state.context=data.academyContext||null;
    if(state.context) {
      state.academy=state.context.academyId;
      const select=$('uaAcademy');select.replaceChildren();for(const option of state.context.academyOptions||[]){const node=el('option',option.name);node.value=option.id;select.append(node);}select.value=String(state.academy);
    } else {$('uaAcademy').replaceChildren(el('option','학원 연결 확인 필요'));}

    $('uaRole').textContent=(state.user.role==='ADMIN'?'관리자':'강사')+' · '+(state.context?(state.context.role==='ACADEMY_OWNER'?'원장 연결 · 학원 업무 승인 가능':'강사 연결 · 학원 조회 가능'):'학원 연결 확인 필요')+' · 사업체별 접근 권한 적용';
    const box=$('uaSummary');box.replaceChildren();box.append(el('small','학원: '+($('uaAcademy').selectedOptions[0]?.textContent||'연결 없음')+' · 장부 사업체: '+(data.finance?.businessName||'선택 필요')));
    if(data.academy) {for(const [label,value] of [['학생',data.academy.studentCount],['운영 중인 반',data.academy.classCount],['오늘 등원',data.academy.todayAttendance?.checkInCount],['오늘 결석',data.academy.todayAttendance?.absentCount]]) {const e=el('span',label);e.append(el('strong',value==null?'확인 필요':value+(label.includes('반')?'개':'명')));box.append(e);}}
    else box.append(el('span','학원 현황: 연결 또는 조회 권한을 확인하세요.'));
    if(data.finance) for(const [label,value] of [['이번 달 수입',data.finance.income],['지출',data.finance.expense],['미수금',data.finance.receivable]]) { const e=el('span',label);e.append(el('strong',money(value)));box.append(e); }
    else box.append(el('span','재무 현황: 연결된 사업체를 선택하세요.'));
    box.append(el('small','학원 집계일 '+(data.academy?.todayAttendance?.date||'확인 불가')+' · 재무 기준월 '+(data.finance?.month||'확인 불가')+' · 조회 '+new Date(data.asOf).toLocaleString('ko-KR')));
    $('uaScope').textContent=scopeText();
  }
  function renderDraft(d,prepend=true) {
    if(prepend)state.draftRevision++;
    const existing=$('uaDrafts').querySelector('[data-ua-draft="'+d.id+'"]');
    const card=el('article',null,'ua-draft');card.dataset.uaDraft=d.id;
    const header=el('header');header.append(el('strong',name(d.operation)),el('span',({PENDING:'승인 대기',EXECUTING:'실행 중 · 재실행 금지',VERIFIED:'저장 검증 완료',REVIEW_REQUIRED:'검토 필요',CANCELLED:'취소',EXPIRED:'만료',INVALIDATED:'대상 변경',UNKNOWN:'결과 확인 필요',FAILED:'저장 실패'})[d.status]||d.status,'ua-status'));card.append(header);
    card.append(el('p',(d.academyId?'학원 ID '+d.academyId:'사업체 ID '+d.businessId),'ua-hint'));
    const dl=el('dl');
    const preview=d.preview;
    if(d.operation==='LEDGER_IMPORT') {
      for(const [k,v] of [['파일',preview.sourceName],['수집 행',preview.totalRows],['승인 시 장부 반영 대상',preview.rows.filter(x=>x.status==='READY').length],['검토 대기',preview.rows.filter(x=>x.status!=='READY').length]]) dl.append(el('dt',k),el('dd',String(v)));
      card.append(dl);const details=el('details'),summary=el('summary','거래·금액·증빙 미리보기 ('+preview.totalRows+'행)');details.append(summary);
      for(const row of preview.rows) details.append(el('p',[row.occurredOn,row.direction,money(row.amount),row.counterparty,row.description,row.evidenceType||'증빙 미확인',row.matchedAccountName||'미분류',row.status].filter(Boolean).join(' · '),'ua-hint'));
      card.append(details);
    } else { for(const [k,v] of Object.entries(preview))dl.append(el('dt',labels[k]||k),el('dd',k==='eventType'?({CHECK_IN:'등원',CHECK_OUT:'하원',ABSENT:'결석',LATE:'지각',EARLY_LEAVE:'조퇴'})[v]||v:String(v)));card.append(dl); }
    card.append(el('time','유효 시각 '+new Date(d.expiresAt).toLocaleString('ko-KR')));
    if(d.status==='PENDING') {
      const actions=el('div',null,'ua-actions');
      for(const [action,label] of [['approve','승인하고 실행'],['cancel','초안 취소']]) { const button=el('button',label,action==='approve'?'ua-primary':'');button.type='button';
        button.addEventListener('click',async()=>{
          const generation=state.generation;state.draftRevision++;actions.querySelectorAll('button').forEach(x=>x.disabled=true);
          try {const result=await post('/drafts/'+d.id+'/'+action,{planHash:d.planHash});if(generation!==state.generation)return;renderDraft(result);if(d.businessId)window.dispatchEvent(new Event('dashboard:refresh-widgets'));await Promise.all([overview(),loadAudit()]);}
          catch(e) {error(e); const note=el('p','승인 응답을 받지 못했다면 기록 불러오기로 상태를 먼저 확인하세요. 자동 재실행하지 않습니다.','ua-hint');card.append(note);}
        }); actions.append(button); }
      card.append(actions);
    }
    if(d.result) { const result=el('div',null,'ua-result'); result.append(el('p',d.result.message||'결과를 확인하세요.'));
      if(d.result.collected!=null)result.append(el('p','수집 '+d.result.collected+' · 장부 저장 '+d.result.saved+' · 기존 중복 '+d.result.duplicates+' · 미분류 '+d.result.unclassified+' · 분류 완료 '+d.result.classified+' · 검토 '+d.result.review+' · 실패 '+d.result.failed));
      if(d.result.recordId)result.append(el('p','저장 레코드 ID '+d.result.recordId));card.append(result); }
    if(existing)existing.replaceWith(card);else $('uaDrafts').append(card);if(prepend)scrollChat();
  }
  function scrollChat() { const log=$('uaDrafts');log.scrollTop=log.scrollHeight; }
  function bubble(role,text) {const node=el('div',null,'ua-bubble ua-'+role);node.append(el('strong',role==='user'?'나':'업무 비서'),el('p',text));$('uaDrafts').append(node);scrollChat();return node;}
  async function acceptTurn(t) {
    bubble('assistant',t.message+' ('+t.analysis+')');
    for(const issue of t.issues||[])bubble('assistant','확인 필요: '+issue);
    if(t.suggestion?.operation&&operations[t.suggestion.operation]) { $('uaOperation').value=t.suggestion.operation;fields(t.suggestion.input);if(!t.drafts.length)$('uaEdit').open=true; }
    for(const d of t.drafts||[])renderDraft(d);
    if(t.overview) {const o=t.overview;let text='현재 현황: ';if(o.academy)text+='학생 '+o.academy.studentCount+'명 · 반 '+o.academy.classCount+'개. ';if(o.finance)text+=o.finance.businessName+' · 수입 '+money(o.finance.income)+' · 지출 '+money(o.finance.expense)+' · 미수금 '+money(o.finance.receivable);bubble('assistant',text);await overview();}
    $('uaMessage').textContent=t.message;
  }
  async function loadAudit() {const data=await json('/audit');$('uaAudit').replaceChildren(...data.map(row=>el('div',new Date(row.createdAt).toLocaleString('ko-KR')+' · '+row.event+' · '+(row.draftId||''))));}
  async function history() {const g=state.generation,revision=state.draftRevision,data=await json('/drafts');if(g!==state.generation||revision!==state.draftRevision)return;$('uaDrafts').replaceChildren();for(const d of data.reverse().filter(x=>(!x.businessId||String(x.businessId)===String(state.business))&&(!x.academyId||Number(x.academyId)===Number(state.academy))))renderDraft(d,false);if(!$('uaDrafts').children.length)bubble('assistant','반가워요. 학생·반·출결·상담과 이지장부 거래를 이 대화에서 함께 처리합니다. 업무 지시 또는 자료를 보내주세요.');scrollChat();await loadAudit();}
  async function submitDraft(e) {e.preventDefault();if(state.busy)return;setBusy(true);const g=state.generation;
    try {const data={};for(const [key,value] of new FormData($('uaDraftForm')))if(value)data[key]=key.endsWith('Id')||key==='amount'?Number(value):value;
      const d=await post('/drafts',{operation:$('uaOperation').value,academyId:state.context?.academyId,businessId:state.business?Number(state.business):null,input:data});if(g===state.generation){bubble('user','직접 입력한 '+name($('uaOperation').value)+' 초안을 요청했습니다.');renderDraft(d);} }
    catch(e){error(e);}finally{setBusy(false);}}
  function setBusy(b) {state.busy=b;document.querySelectorAll('#uaDraftForm button,#uaUploadForm button,#uaCommandForm button').forEach(x=>x.disabled=b||!state.ready);if(b)$('uaError').hidden=true;}
  async function refreshContext() {const g=state.generation;state.ready=false;setBusy(true);try{await overview();await history();if(g===state.generation)state.ready=true;}finally{if(g===state.generation)setBusy(false);}}
  function bind() {
    $('uaAcademy').addEventListener('change',()=>{state.academy=Number($('uaAcademy').value);state.context=null;state.generation++;$('uaCatalog').replaceChildren();$('uaDrafts').replaceChildren();$('uaEdit').open=false;fields();refreshContext().catch(error);});
    $('uaOperation').addEventListener('change',()=>fields());$('uaDraftForm').addEventListener('submit',submitDraft);
    $('uaCommandForm').addEventListener('submit',async e=>{e.preventDefault();if(state.busy)return;const message=$('uaCommand').value,g=state.generation;setBusy(true);bubble('user',message);try{const turn=await post('/conversation',{academyId:state.academy,businessId:state.business?Number(state.business):null,message});if(g!==state.generation)return;await acceptTurn(turn);$('uaCommand').value='';}catch(e){if(g===state.generation){bubble('assistant','요청 실패: '+e.message);error(e);}}finally{setBusy(false);}});
    document.querySelectorAll('[data-ua-example]').forEach(button=>button.addEventListener('click',()=>{$('uaCommand').value=button.dataset.uaExample;$('uaCommandForm').requestSubmit();}));
    document.querySelectorAll('[data-ua-list]').forEach(button=>button.addEventListener('click',async()=>{const g=state.generation;try{const data=await json('/catalog/'+button.dataset.uaList+(state.academy?'?academyId='+state.academy:''));if(g!==state.generation)return;const table=el('table');for(const row of data){const tr=el('tr');for(const value of [row.id,row.name||row.title,row.schoolName||row.teacherName||row.role||row.status||''])tr.append(el('td',String(value??'')));table.append(tr);}$('uaCatalog').replaceChildren(el('p','ID · 이름/제목 · 학교/담당/상태'),table);}catch(e){error(e);}}));
    $('uaUploadForm').addEventListener('submit',async e=>{e.preventDefault();const file=$('uaFile').files[0];if(!file||state.busy)return;if(file.size>262144){error(new Error('자료는 256KB 이하로 올려주세요.'));return;}setBusy(true);const g=state.generation;bubble('user','자료 첨부: '+file.name);try{const form=new FormData();form.append('file',file);const query=new URLSearchParams();if(state.academy)query.set('academyId',state.academy);if(state.business)query.set('businessId',state.business);const turn=await json('/materials?'+query,{method:'POST',body:form});if(g===state.generation){await acceptTurn(turn);$('uaFile').value='';}}catch(e){if(g===state.generation){bubble('assistant','자료 분석 실패: '+e.message);error(e);}}finally{setBusy(false);}});
    $('uaNext').addEventListener('click',()=>advance(1).catch(error));$('uaBack').addEventListener('click',()=>advance(-1).catch(error));
    $('uaPause').addEventListener('click',()=>showTour(false));$('uaResume').addEventListener('click',async()=>{try{if(state.progress.completed)await saveProgress(0,false);showTour(true);$('uaTour').scrollIntoView({block:'nearest'});}catch(e){error(e);}});
    $('uaExplain').addEventListener('click',async()=>{
      if(!state.business){error(new Error('해설할 사업체를 먼저 선택하세요.'));return;}
      const g=state.generation,b=state.business;$('uaExplain').disabled=true;
      try{const res=await fetch('/api/dashboard/assistant',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify({businessId:Number(b),message:'선택한 사업체의 이번 달 재무 현황을 요약하고 확인할 거래·미수금 업무를 알려주세요.'})});const body=await res.json();if(g!==state.generation)return;if(!res.ok)throw new Error(body.error||body.message||'AI 연결을 확인하세요.');bubble('assistant','AI 재무 해설 (조회 전용): '+body.answer);}
      catch(e){error(e);}finally{$('uaExplain').disabled=false;}
    });
    $('uaRefresh').addEventListener('click',()=>{$('refreshBtn').click();});$('refreshBtn').addEventListener('click',()=>overview().catch(error));$('uaHistoryRefresh').addEventListener('click',()=>history().catch(error));
    $('aiFab').addEventListener('click',()=>{$('unifiedAssistant').scrollIntoView({block:'start'});$('uaCommand').focus({preventScroll:true});});
    const change=()=>{const business=$('bizSelect').value||null;if(state.business===business)return;state.business=business;state.generation++;$('uaDrafts').replaceChildren();$('uaCatalog').replaceChildren();$('uaFile').value='';$('uaCommand').value='';$('uaEdit').open=false;fields();$('uaScope').textContent=scopeText();refreshContext().catch(error);};
    $('bizSelect').addEventListener('change',change);new MutationObserver(change).observe($('bizSelect'),{childList:true});change();
  }
  async function init() {if(!$('unifiedAssistant'))return;try{state.user=await ensureCurrentUser();if(!state.user||!['ADMIN','TUTOR'].includes(state.user.role))return;
    try { const res=await fetch('/api/dashboard/businesses',{credentials:'same-origin',cache:'no-store'});if(res.ok){const list=await res.json(),saved=localStorage.getItem('dashboard.businessId');state.business=String(list.find(x=>String(x.businessId)===saved)?.businessId||list[0]?.businessId||'')||null;} } catch(_) {}
    fields();setBusy(true);bind();state.progress=await json('/progress');showTour(!state.progress.completed);await refreshContext();}catch(e){error(e);}}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
