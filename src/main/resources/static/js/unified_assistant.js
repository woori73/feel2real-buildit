/* Unified workspace: previews are server data; only a clicked approval executes writes. */
(function () {
  'use strict';
  // Pure tour rules (also exported for unit tests): which step a menu or purpose belongs to.
  const MENU_STEP = { uaMenus:0, uaStudents:1, uaClasses:1, uaAttendance:2, uaConsult:3, uaLedger:4 };
  const PURPOSE_STEP = { students:1, attendance:2, consult:3, ledger:4 };
  const stepAfterMenuClick = (current, menuId) => (menuId in MENU_STEP ? Math.max(current, Math.min(5, MENU_STEP[menuId] + 1)) : current);
  const stepForPurpose = purpose => (purpose in PURPOSE_STEP ? PURPOSE_STEP[purpose] : 0);
  if (typeof window !== 'undefined') window.unifiedAssistantTour = { stepAfterMenuClick, stepForPurpose };
  const $ = id => document.getElementById(id);
  const mobileChat = () => window.matchMedia('(max-width: 1023px)').matches;
  const API = '/api/dashboard/assistant/v1';
  const state = { user: null, context: null, academy: null, progress: null, business: null, generation: 0, draftRevision: 0, busy: false, ready: false, tour: false };
  let composerMode = 'work';
  let gradingPhotos;
  let generalConversationId = null;
  const modeInputs = { work: '', general: '' };
  let workPlaceholder = '메시지';
  const activeLog = () => $(composerMode === 'general' ? 'codiGeneralLog' : 'uaDrafts');
  let activitySequence = 0;
  let activityPose = 'idle';
  function beginActivity(pose, operation) {
    const sequence = ++activitySequence;
    const generation = state.generation;
    function update(nextPose) {
      if (generation !== state.generation || sequence !== activitySequence) return;
      activityPose = nextPose;
      if (!mobileChat()) return;
      document.dispatchEvent(new CustomEvent('codi:activity', { detail: { pose: nextPose, operation } }));
    }
    update(pose);
    return update;
  }
  function draftPose(draft) {
    switch (draft.status) {
      case 'VERIFIED': return 'result';
      case 'CANCELLED': return 'idle';
      case 'EXECUTING': return 'work';
      case 'PENDING':
      case 'REVIEW_REQUIRED': return 'read';
      default: return 'error';
    }
  }
  function turnPose(turn) {
    if (turn.issues?.length || turn.drafts?.length) return 'read';
    if (operations[turn.suggestion?.operation]) return 'input';
    return 'result';
  }
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
    ['오늘의 출결을 기록하세요','출결 메뉴에서 반별 출결부를 확인하세요. 비서에게 “학생 ID 1 출결 조회해줘”라고 하면 조회만, “학생 ID 1 결석 기록해줘”라고 하면 기록할 내용을 먼저 보여드립니다. 출결 정정은 사유 입력이 필요해 출결 화면에서 직접 처리합니다.','uaAttendance'],
    ['상담 기록을 이어가세요','상담을 작성하거나 열린 상담에 후속 메시지를 남길 수 있습니다. 학부모 알림이 전달될 수 있으니 대상과 내용을 확인해 주세요.','uaConsult'],
    ['사업체별 거래와 증빙을 확인하세요','상단에서 사업체를 선택한 뒤 거래·증빙 CSV를 올리세요. 원본과 증빙 유형을 보관하고, 장부에 저장한 수·중복·검토 대기를 구분해 보여드립니다.','uaLedger'],
    ['초안 확인 → 승인 → 결과 확인','대화창의 초안에서 대상과 내용을 검토한 뒤 승인하세요. 실제 API 실행 후 저장 결과를 다시 조회합니다. 연결 오류로 결과가 불명확하면 기존 화면에서 확인하세요.','uaReview']
  ];
  function error(e) { $('uaError').hidden=false; $('uaError').textContent=e.message || '요청을 처리할 수 없습니다.'; }
  async function json(path, options={}) {
    const res=await fetch(API+path,{credentials:'same-origin',cache:'no-store',...options});
    const body=await res.json().catch(()=>({}));
    if (!res.ok) {
      const failure = new Error(body.message || body.detail || body.error || '요청 실패 (' + res.status + '). 로그인·권한·연결 상태를 확인하세요.');
      failure.status = res.status;
      throw failure;
    }
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
    $('uaPurpose').hidden=i!==0;
    $('uaTourProgress').value=i+1; $('uaBack').disabled=i===0; $('uaNext').textContent=i===5?'안내 완료':'다음';
  }
  async function saveProgress(step,completed) {
    if(state.progress?.local){state.progress={...state.progress,step,completed};return;}
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
    state.overview=data;
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
          const activity = beginActivity('work', action === 'approve' ? 'approval' : 'cancel');
          try {
            const result = await post('/drafts/'+d.id+'/'+action, {planHash:d.planHash});
            if (generation !== state.generation) return;
            renderDraft(result);
            activity(draftPose(result));
          } catch(e) {
            if (generation !== state.generation) return;
            activity('error');
            error(e);
            const note=el('p','응답을 받지 못했다면 기록 불러오기로 상태를 먼저 확인하세요. 자동 재실행하지 않습니다.','ua-hint');
            card.append(note);
            return;
          }
          if (d.businessId) window.dispatchEvent(new Event('dashboard:refresh-widgets'));
          try { await Promise.all([overview(),loadAudit()]); }
          catch (e) { if (generation === state.generation) error(e); }
        }); actions.append(button); }
      card.append(actions);
    }
    if(d.result) { const result=el('div',null,'ua-result'); result.append(el('p',d.result.message||'결과를 확인하세요.'));
      if(d.result.collected!=null)result.append(el('p','수집 '+d.result.collected+' · 장부 저장 '+d.result.saved+' · 기존 중복 '+d.result.duplicates+' · 미분류 '+d.result.unclassified+' · 분류 완료 '+d.result.classified+' · 검토 '+d.result.review+' · 실패 '+d.result.failed));
      if(d.result.recordId)result.append(el('p','저장 레코드 ID '+d.result.recordId));card.append(result); }
    changeChat(() => { if(existing)existing.replaceWith(card);else $('uaDrafts').append(card); });
  }
  function distanceToLatest(log) {
    return log.scrollHeight - log.clientHeight - log.scrollTop;
  }
  let followingLatest = true;
  function updateJumpButton() {
    const distance = distanceToLatest(activeLog());
    followingLatest = distance <= 48;
    $('codiJumpLatest').hidden = distance <= 8;
  }
  function changeChat(change, log = $('uaDrafts')) {
    const follow = distanceToLatest(log) <= 48;
    change();
    if (follow) log.scrollTop = log.scrollHeight;
    updateJumpButton();
  }
  function bubble(role, text, log = $('uaDrafts')) {
    const node = el('div', null, 'ua-bubble ua-' + role);
    const speaker = mobileChat() ? '코디' : window.f2rAgentDashboard?.currentName?.() || '업무 비서';
    node.append(el('strong', role === 'user' ? '나' : speaker));
    for (const paragraph of String(text).split(/\n\s*\n/)) {
      const p = el('p');
      // Keep all server text as text nodes while emphasizing times and quantities.
      const parts = paragraph.split(/(\d+(?:,\d{3})*(?:시(?:\s*\d+분)?|분|번|명|개|건|원))/g);
      parts.forEach((part, index) => p.append(index % 2 ? el('b', part) : part));
      node.append(p);
    }
    changeChat(() => log.append(node), log);
    return node;
  }
  function acceptGeneralTurn(turn, log) {
    if (typeof turn?.message !== 'string' || !turn.message.trim()) {
      throw new Error('일반 대화 답변을 확인하지 못했습니다.');
    }
    generalConversationId = turn.data?.conversationId || null;
    const response = bubble('assistant', turn.message, log);
    const links = el('ul', null, 'codi-sources');
    links.setAttribute('aria-label', '답변의 출처');
    for (const source of turn.data?.sources || []) {
      let url;
      try { url = new URL(source.url); } catch { continue; }
      if (url.protocol !== 'https:' && url.protocol !== 'http:') continue;
      const item = el('li');
      const link = el('a', source.title || url.hostname);
      link.href = url.href;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      item.append(link);
      links.append(item);
    }
    if (links.childElementCount) changeChat(() => response.append(links), log);
  }

  async function acceptTurn(t) {
    if (typeof t?.message !== 'string' || !t.message.trim()) {
      throw new Error('코디의 응답을 확인할 수 없습니다. 로그인과 연결 상태를 확인해 주세요.');
    }
    const generation = state.generation;
    bubble('assistant',t.message+(t.analysis?' ('+t.analysis+')':''));
    for(const issue of t.issues||[])bubble('assistant','확인 필요: '+issue);
    if(t.suggestion?.operation&&operations[t.suggestion.operation]) { $('uaOperation').value=t.suggestion.operation;fields(t.suggestion.input);if(!t.drafts?.length)$('uaEdit').open=true; }
    for(const d of t.drafts||[])renderDraft(d);
    if(t.data?.notice) bubble('assistant',t.data.notice);
    if(t.data?.events) {const label={CHECK_IN:'등원',CHECK_OUT:'하원',ABSENT:'결석',LATE:'지각',EARLY_LEAVE:'조퇴'};bubble('assistant','학생 ID '+t.data.studentId+' 최근 출결 (최대 '+t.data.limit+'건): '+(t.data.events.length?t.data.events.map(e=>(e.occurredAt||'')+' '+(label[e.eventType]||e.eventType)).join(' / '):'기록 없음'));}
    if(t.overview) {const o=t.overview;let text='현재 현황: ';if(o.academy)text+='학생 '+o.academy.studentCount+'명 · 반 '+o.academy.classCount+'개. ';if(o.finance)text+=o.finance.businessName+' · 수입 '+money(o.finance.income)+' · 지출 '+money(o.finance.expense)+' · 미수금 '+money(o.finance.receivable);bubble('assistant',text);try {await overview();} catch(e) {if(generation===state.generation)error(e);}}
    if(generation===state.generation)$('uaMessage').textContent=t.message;
  }
  async function loadAudit() {const g=state.generation,data=await json('/audit');if(g!==state.generation)return;$('uaAudit').replaceChildren(...data.map(row=>el('div',new Date(row.createdAt).toLocaleString('ko-KR')+' · '+row.event+' · '+(row.operation?name(row.operation)+' · ':'')+(row.academyId?'학원 '+row.academyId:row.businessId?'사업체 '+row.businessId:'')+' · '+(row.draftId||''))));}
  function openingMessage() {
    if (!mobileChat()) return '무엇을 도와드릴까요? 평소 말투로 요청하거나 홀로그램 업무 버튼을 눌러 주세요. 필요한 내용을 하나씩 여쭤보겠습니다.';
    const academy = state.overview?.academy;
    const attendance = academy?.todayAttendance;
    const summary = [];
    if (academy?.studentCount != null && academy?.classCount != null) {
      summary.push('현재 학원에는 학생 ' + academy.studentCount + '명과 운영 중인 반 ' + academy.classCount + '개가 있어요.');
    }
    if (attendance?.checkInCount != null && attendance?.absentCount != null) {
      summary.push('오늘의 출결 기록은 등원 ' + attendance.checkInCount + '명, 결석 ' + attendance.absentCount + '명이에요.');
    }
    if (!summary.length) summary.push('원장님, 어떤 업무부터 함께 살펴볼까요?');
    summary.push('학생의 출결을 확인하거나 공지 초안을 준비할 수 있어요. 필요한 일을 메시지로 알려 주세요.');
    return summary.join('\n\n');
  }
  async function history() {
    const generation = state.generation;
    const revision = state.draftRevision;
    const data = await json('/drafts');
    if (generation !== state.generation || revision !== state.draftRevision) return;
    const log = $('uaDrafts');
    const drafts = data.reverse().filter(d => (!d.businessId || String(d.businessId) === String(state.business))
      && (!d.academyId || Number(d.academyId) === Number(state.academy)));
    const currentIDs = new Set(drafts.map(d => String(d.id)));
    const opening = !drafts.length && !log.querySelector('.ua-bubble');
    changeChat(() => {
      log.querySelector(':scope > .ua-hint')?.remove();
      log.querySelectorAll('[data-ua-draft]').forEach(card => {
        if (!currentIDs.has(card.dataset.uaDraft)) card.remove();
      });
      for (const draft of drafts) renderDraft(draft, false);
      if (opening) bubble('assistant', openingMessage());
    });
    if (opening && mobileChat()) {
      log.scrollTop = 0;
      updateJumpButton();
    }
    await loadAudit();
  }
  async function submitDraft(e) {
    e.preventDefault();
    if (state.busy || !state.ready) return;
    const g = state.generation;
    const operation = $('uaOperation').value;
    const data = {};
    for (const [key,value] of new FormData($('uaDraftForm'))) {
      if (value) data[key] = key.endsWith('Id') || key === 'amount' ? Number(value) : value;
    }
    try { await createDraft(operation, data, '직접 입력한 '+name(operation)+' 초안을 요청했습니다.'); }
    catch(e) { if (g === state.generation) error(e); }
  }
  // 대화 전송: 기존 /conversation API 그대로. 단계 안내(f2r_agent_flows.js)도 이 함수로 조회형 요청을 보낸다.
  async function send(message, opts={}) {
    if (!state.ready || state.busy || !String(message || '').trim()) return false;
    const g = state.generation;
    const activity = beginActivity(opts.pose || 'work', 'conversation');
    setBusy(true);
    const general = opts.mode === 'general';
    const log = $(general ? 'codiGeneralLog' : 'uaDrafts');
    if (!opts.silent) bubble('user', message, log);
    try {
      let payload;
      if (general) {
        if (!state.academy) throw new Error('메뉴에서 원장 권한이 있는 학원을 선택해 주세요.');
        payload = { mode: 'general', academyId: state.academy, message, webSearch: true };
        if (generalConversationId) payload.conversationId = generalConversationId;
      } else {
        payload = { academyId: state.academy, businessId: state.business ? Number(state.business) : null, message };
      }
      const turn = await post('/conversation', payload);
      if (g !== state.generation) return false;
      if (general) {
        acceptGeneralTurn(turn, log);
        activity('result');
      } else {
        await acceptTurn(turn);
        activity(turnPose(turn));
      }
      return true;
    } catch(e) {
      if (g === state.generation) {
        activity('error');
        if (general && e.status === 410) generalConversationId = null;
        const note = general && e.status === 410 ? ' 다시 보내면 새 일반 대화를 시작합니다.' : '';
        bubble('assistant', '요청 실패: ' + e.message + note, log);
        error(e);
      }
      return false;
    } finally { if (g === state.generation) setBusy(false); }
  }
  // 단계 안내가 모은 값으로 기존 /drafts API에 초안을 만든다. 승인 전에는 저장하지 않는다.
  async function createDraft(operation, input, requestLabel) {
    if (!state.ready || state.busy) return null;
    const g = state.generation;
    const activity = beginActivity('write', 'draft');
    setBusy(true);
    try {
      const draft = await post('/drafts', {operation,academyId:state.context?.academyId,businessId:state.business?Number(state.business):null,input});
      if (g !== state.generation) return null;
      if (requestLabel) bubble('user', requestLabel);
      renderDraft(draft);
      activity(draftPose(draft));
      return draft;
    } catch (e) {
      if (g !== state.generation) return null;
      activity('error');
      throw e;
    } finally { if (g === state.generation) setBusy(false); }
  }
  function setBusy(b) {state.busy=b;document.querySelectorAll('#uaDraftForm button,#uaUploadForm button,#uaCommandForm button').forEach(x=>x.disabled=b||!state.ready);if(b)$('uaError').hidden=true;gradingPhotos?.sync();}
  async function refreshContext() {
    const g = state.generation;
    const activity = beginActivity('read', 'context');
    state.ready = false;
    setBusy(true);
    try {
      await overview();
      if (g !== state.generation) return;
      await history();
      if (g !== state.generation) return;
      state.ready = true;
      activity('idle');
    } catch (e) {
      if (g !== state.generation) return;
      activity('error');
      throw e;
    } finally { if (g === state.generation) setBusy(false); }
  }
  async function readForUser(operation, read) {
    const g = state.generation;
    const activity = beginActivity('read', operation);
    try {
      await read();
      activity('result');
    } catch (e) {
      if (g !== state.generation) return;
      activity('error');
      error(e);
    }
  }
  function materialType(file) {
    const extension = /\.([^.]+)$/.exec(file.name)?.[1].toLowerCase();
    if (extension === 'html' || extension === 'htm') return 'HTML';
    if (extension === 'pdf') return 'PDF';
    if (extension === 'csv' || extension === 'txt') return '업무 초안';
    return null;
  }
  function materialCheckbox(text, accessibleName) {
    const label = el('label', null, 'ua-material-check');
    const input = el('input');
    input.type = 'checkbox';
    input.setAttribute('aria-label', accessibleName);
    label.append(input, el('span', text));
    return label;
  }
  function appendMerchantGuide(card, guide) {
    card.append(el('h4', '카드사별 진행표'));
    if (guide.note) card.append(el('p', guide.note));
    card.append(el('p', '직접 계약을 확인한 카드사를 체크하세요. 선택은 이 대화 화면에만 적용됩니다.', 'ua-hint'));
    const filter = materialCheckbox('확인한 카드사만 보기', '확인한 카드사만 보기');
    const table = el('table');
    table.setAttribute('aria-label', '카드사별 진행표');
    const header = table.createTHead().insertRow();
    for (const title of ['카드사', '내 계약 확인', '문의 상태']) {
      const column = el('th', title);
      column.scope = 'col';
      header.append(column);
    }
    const rows = table.createTBody();
    const drafts = el('div');
    const empty = el('p', '계약을 확인한 카드사가 없습니다.', 'ua-hint');
    const entries = [];
    function updateSelection() {
      let visible = 0;
      for (const entry of entries) {
        const selected = entry.checkbox.control.checked;
        const hide = filter.control.checked && !selected;
        entry.row.hidden = entry.detail.hidden = hide;
        entry.checkbox.lastElementChild.textContent = selected ? '계약 확인' : '계약 미확인';
        if (!hide) visible++;
      }
      empty.hidden = visible > 0;
      drafts.hidden = !visible;
    }
    for (const item of guide.rows) {
      const row = rows.insertRow();
      const issuer = el('th', item.issuer);
      issuer.scope = 'row';
      row.append(issuer);
      const checkbox = materialCheckbox('계약 미확인', item.issuer + ' 계약 확인');
      row.insertCell().append(checkbox);
      row.insertCell().textContent = item.status;
      const detail = el('details');
      detail.append(el('summary', item.issuer + ' 문의 초안'));
      const fields = el('dl');
      fields.append(el('dt', '준비 서류'), el('dd', item.documents), el('dt', '문의 문안'), el('dd', item.inquiry));
      detail.append(fields);
      drafts.append(detail);
      entries.push({ row, detail, checkbox });
      checkbox.control.addEventListener('change', () => {
        if (filter.control.checked && !checkbox.control.checked) filter.control.focus({ preventScroll: true });
        updateSelection();
      });
    }
    filter.control.addEventListener('change', updateSelection);
    card.append(filter, table, empty, drafts);
    updateSelection();
  }
  function showDocumentResult(turn) {
    const doc = turn.data?.document;
    if (!doc || typeof turn.message !== 'string' || !turn.message.trim()) throw new Error('문서 읽기 결과를 받지 못했습니다.');
    const card = el('article', null, 'ua-material-result');
    card.setAttribute('aria-label', doc.format + ' 문서 읽기 결과');
    card.tabIndex = -1;
    const pages = doc.pageCount > 0 ? ' · ' + doc.pageCount + '쪽' : '';
    card.append(el('h3', doc.format + ' 문서 읽기' + pages), el('p', turn.message));
    if (doc.status === 'READ') {
      if (turn.data.cardMerchantGuide) {
        appendMerchantGuide(card, turn.data.cardMerchantGuide);
      } else if (doc.text) {
        const detail = el('details');
        detail.append(el('summary', '본문 읽기'), el('p', doc.text, 'ua-material-text'));
        if (doc.truncated) detail.append(el('p', '본문 앞부분 1,200자까지 표시합니다.', 'ua-hint'));
        card.append(detail);
      }
    }
    changeChat(() => $('uaDrafts').append(card));
    $('uaMessage').textContent = turn.message;
    return card;
  }
  async function submitMaterial(e) {
    e.preventDefault();
    const picker = $('uaFile');
    const file = picker.files[0];
    if (!file || state.busy || !state.ready) return;
    const type = materialType(file);
    const documentFile = type === 'HTML' || type === 'PDF';
    let problem;
    if (!type) problem = 'CSV, TXT, HTML 또는 PDF 파일을 선택해 주세요.';
    else if (!file.size) problem = '빈 파일입니다. 내용이 있는 파일을 선택해 주세요.';
    else if (file.size > (documentFile ? 5 * 1024 * 1024 : 256 * 1024)) {
      problem = documentFile ? type + ' 파일은 5MB 이하로 올려주세요.' : 'CSV·TXT 파일은 256KB 이하로 올려주세요.';
    }
    const feedback = $('uaUploadFeedback');
    if (problem) {
      beginActivity('error', 'material');
      feedback.textContent = problem;
      picker.focus({ preventScroll: true });
      return;
    }
    const g = state.generation;
    const activity = beginActivity('read', 'material');
    setBusy(true);
    picker.disabled = true;
    feedback.textContent = documentFile ? '문서를 읽는 중입니다.' : '업무 자료를 분석하는 중입니다.';
    bubble('user', documentFile ? type + ' 문서 첨부' : '자료 첨부: ' + file.name);
    try {
      const form = new FormData();
      form.append('file', file);
      const query = new URLSearchParams();
      if (state.academy) query.set('academyId', state.academy);
      if (state.business) query.set('businessId', state.business);
      const turn = await json('/materials?' + query, {method:'POST',body:form});
      if (g !== state.generation) return;
      if (documentFile) {
        const openResult = document.body.dataset.view === 'all';
        if (openResult) {
          selectMode('work');
          window.f2rAgentDashboard.showConversation();
        }
        const result = showDocumentResult(turn);
        if (openResult) {
          result.focus({ preventScroll: true });
          result.scrollIntoView({ block: 'start' });
        }
        const status = turn.data.document.status;
        activity(status === 'FAILED' ? 'error' : status === 'READ' ? 'result' : 'read');
      } else {
        await acceptTurn(turn);
        if (g !== state.generation) return;
        activity(turnPose(turn));
      }
      picker.value = '';
      $('uaUploadSubmit').textContent = '자료 분석';
      feedback.textContent = '학원 업무 대화에서 결과를 확인할 수 있습니다.';
    } catch (e) {
      if (g !== state.generation) return;
      activity('error');
      feedback.textContent = e.message;
      bubble('assistant','자료 분석 실패: '+e.message);
      error(e);
    } finally {
      if (g === state.generation) {
        picker.disabled = false;
        setBusy(false);
      }
    }
  }
  function setCommand(text) {
    const input = $('uaCommand');
    input.value = text;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function selectMode(mode) {
    if (composerMode === mode) return;
    const input = $('uaCommand');
    modeInputs[composerMode] = input.value;
    if (composerMode === 'work') workPlaceholder = input.placeholder;
    composerMode = mode;
    const general = mode === 'general';
    $('uaDrafts').hidden = general;
    $('codiGeneralLog').hidden = !general;
    $('uaHistoryRefresh').hidden = general;
    $('codiAttach').hidden = general;
    if (general) gradingPhotos?.reset();
    gradingPhotos?.sync();
    $('uaError').hidden = true;
    $('codiMode').querySelectorAll('button').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.codiMode === mode));
    });
    if (general && !$('codiGeneralLog').childElementCount) {
      $('codiGeneralLog').append(el('p', '공개 정보만 입력해 주세요. 코디가 필요한 경우 웹을 검색하고 답변에 출처를 표시합니다.', 'ua-hint'));
    }
    input.placeholder = general ? '일반 질문' : workPlaceholder;
    setCommand(modeInputs[mode]);
    updateJumpButton();
  }

  function resetGeneralChat() {
    selectMode('work');
    generalConversationId = null;
    modeInputs.general = '';
    $('codiGeneralLog').replaceChildren();
  }

  function restoreUnsent(original, mode, log) {
    const input = $('uaCommand');
    // Crossing the desktop breakpoint can hide the originating composer mid-request.
    let alternate = mode === composerMode ? input.value : modeInputs[mode];
    if (mode === composerMode) setCommand(original);
    else modeInputs[mode] = original;
    if (!alternate || alternate === original) return;
    // Keep the next draft separately so neither message exceeds the server's length limit.
    const choices = el('div', null, 'ua-choices');
    const swap = el('button', '이어서 작성한 내용 불러오기', 'ua-choice');
    swap.type = 'button';
    swap.addEventListener('click', () => {
      const current = input.value;
      setCommand(alternate);
      alternate = current;
      swap.textContent = '이전 입력으로 전환';
      input.focus({ preventScroll: true });
    });
    choices.append(swap);
    changeChat(() => log.append(choices), log);
  }
  function resetContextInputs() {
    gradingPhotos?.reset();
    resetGeneralChat();
    beginActivity('idle', 'context');
    setCommand('');
    $('uaFile').value = '';
    $('uaFile').disabled = false;
    $('uaUploadFeedback').textContent = '';
    $('uaUploadSubmit').textContent = '자료 분석';
    $('uaCatalog').replaceChildren();
    $('uaDrafts').replaceChildren();
    $('uaAudit').replaceChildren();
    $('uaSummary').replaceChildren();
    $('uaMessage').textContent = '';
    $('uaEdit').open = false;
    $('uaError').hidden = true;
    state.overview = null;
    fields();
    updateJumpButton();
  }
  function bind() {
    gradingPhotos = window.bindCodiGradingPhoto({
      request: json,
      beginActivity,
      context: () => ({
        businessId: state.business,
        businessName: $('bizSelect').selectedOptions[0]?.textContent,
        work: composerMode === 'work',
        ready: state.ready && !state.busy
      })
    });
    $('uaAcademy').addEventListener('change',()=>{state.academy=Number($('uaAcademy').value);state.context=null;state.generation++;resetContextInputs();refreshContext().catch(error);});
    $('uaOperation').addEventListener('change',()=>fields());$('uaDraftForm').addEventListener('submit',submitDraft);
    const input = $('uaCommand');
    const commandForm = $('uaCommandForm');
    input.addEventListener('input', e => {
      if (e.isTrusted && state.ready && !state.busy) beginActivity(input.value.trim() ? 'input' : 'idle', 'compose');
    });
    const modeSelector = $('codiMode');
    modeSelector.addEventListener('pointerdown', e => {
      if (document.activeElement === input) e.preventDefault();
    });
    modeSelector.addEventListener('click', e => {
      const button = e.target.closest('[data-codi-mode]');
      if (button) selectMode(button.dataset.codiMode);
    });
    window.matchMedia('(max-width: 1023px)').addEventListener('change', e => {
      if (!e.matches) selectMode('work');
    });
    window.addEventListener('pagehide', resetGeneralChat);
    window.addEventListener('pageshow', e => { if (e.persisted) window.location.reload(); });
    let composing = false;
    input.addEventListener('compositionstart', () => { composing = true; commandForm.dataset.composing = 'true'; });
    input.addEventListener('compositionend', () => { composing = false; delete commandForm.dataset.composing; });
    input.addEventListener('keydown', e => {
      if (!mobileChat() || e.key !== 'Enter' || e.shiftKey || composing || e.isComposing || e.keyCode === 229) return;
      e.preventDefault();
      if (!e.repeat && state.ready && !state.busy && input.value.trim()) commandForm.requestSubmit();
    });
    commandForm.addEventListener('submit', async e => {
      e.preventDefault();
      if (composing || !state.ready || state.busy || !input.value.trim()) return;
      const generation = state.generation;
      const original = input.value;
      const mode = composerMode;
      const log = activeLog();
      const sent = send(original, { mode });
      setCommand('');
      if (!await sent && generation === state.generation) {
        restoreUnsent(original, mode, log);
      }
    });
    const logs = [$('uaDrafts'), $('codiGeneralLog')];
    logs.forEach(log => log.addEventListener('scroll', updateJumpButton, { passive: true }));
    $('codiJumpLatest').addEventListener('click', () => {
      const log = activeLog();
      log.scrollTop = log.scrollHeight;
      updateJumpButton();
    });
    if (window.ResizeObserver) {
      const observer = new ResizeObserver(() => {
        const log = activeLog();
        if (!log.clientHeight) return;
        // Keep the first line of a lone opening message in view when the keyboard appears.
        if (followingLatest && (log.scrollTop > 0 || log.childElementCount > 1)) log.scrollTop = log.scrollHeight;
        updateJumpButton();
      });
      logs.forEach(log => observer.observe(log));
    }
    document.querySelectorAll('[data-ua-example]').forEach(button=>button.addEventListener('click',()=>{selectMode('work');$('uaCommand').value=button.dataset.uaExample;$('uaCommandForm').requestSubmit();}));
    document.querySelectorAll('[data-ua-list]').forEach(button=>button.addEventListener('click',()=>readForUser('lookup',async()=>{
      const g=state.generation;
      const data=await json('/catalog/'+button.dataset.uaList+(state.academy?'?academyId='+state.academy:''));
      if(g!==state.generation)return;
      const table=el('table');
      for(const row of data){
        const tr=el('tr');
        for(const value of [row.id,row.name||row.title,row.schoolName||row.teacherName||row.role||row.status||''])tr.append(el('td',String(value??'')));
        table.append(tr);
      }
      $('uaCatalog').replaceChildren(el('p','ID · 이름/제목 · 학교/담당/상태'),table);
    })));
    $('uaFile').addEventListener('change', () => {
      const file = $('uaFile').files[0];
      if (file && (/\.(jpe?g|png)$/i.test(file.name) || ['image/jpeg','image/png'].includes(file.type))) {
        $('uaFile').value = '';
        $('uaUploadFeedback').textContent = '';
        $('uaUploadSubmit').textContent = '자료 분석';
        if (composerMode === 'work') gradingPhotos.openFile(file);
        else $('uaUploadFeedback').textContent = '학원 업무에서 채점 사진을 첨부해 주세요.';
        return;
      }
      const type = file && materialType(file);
      $('uaUploadFeedback').textContent = '';
      $('uaUploadSubmit').textContent = !type ? '자료 분석' : type === '업무 초안' ? '자료 분석하고 초안 만들기' : '문서 읽기';
    });
    $('uaUploadForm').addEventListener('submit', submitMaterial);
    // A highlighted menu click saves the next step first, so coming back resumes there.
    document.querySelectorAll('#uaMenus a').forEach(link=>link.addEventListener('click',async e=>{
      if(!state.tour||!state.progress||state.progress.completed||e.button!==0||e.ctrlKey||e.metaKey||e.shiftKey||e.altKey)return;
      e.preventDefault();const next=stepAfterMenuClick(state.progress.step,link.id);
      try{if(next!==state.progress.step)await saveProgress(next,false);}catch(_){}
      window.location.href=link.href;
    }));
    document.querySelectorAll('[data-ua-purpose]').forEach(button=>button.addEventListener('click',async()=>{
      try{await saveProgress(stepForPurpose(button.dataset.uaPurpose),false);highlight();$('uaTour').scrollIntoView({block:'nearest'});}catch(e){error(e);}
    }));
    $('uaNext').addEventListener('click',()=>advance(1).catch(error));$('uaBack').addEventListener('click',()=>advance(-1).catch(error));
    $('uaPause').addEventListener('click',()=>showTour(false));$('uaResume').addEventListener('click',async()=>{
      // 사용법 안내는 언제 눌러도 1단계부터 다시 보여준다. 진행 기록을 못 읽은 경우에도 화면 안내는 연다.
      $('uaResume').classList.remove('ua-new');
      if(!state.progress)state.progress={step:0,completed:false,version:null,local:true};
      try{await saveProgress(0,false);}catch(e){state.progress={...state.progress,step:0,completed:false};error(e);}
      showTour(true);$('uaTour').scrollIntoView({block:'center'});$('uaNext').focus({preventScroll:true});
    });
    $('uaExplain').addEventListener('click',async()=>{
      if(!state.business){error(new Error('해설할 사업체를 먼저 선택하세요.'));return;}
      const g=state.generation,b=state.business;$('uaExplain').disabled=true;
      try{const res=await fetch('/api/dashboard/assistant',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify({businessId:Number(b),message:'선택한 사업체의 이번 달 재무 현황을 요약하고 확인할 거래·미수금 업무를 알려주세요.'})});const body=await res.json();if(g!==state.generation)return;if(!res.ok)throw new Error(body.error||body.message||'AI 연결을 확인하세요.');bubble('assistant','AI 재무 해설 (조회 전용): '+body.answer);}
      catch(e){error(e);}finally{$('uaExplain').disabled=false;}
    });
    $('uaRefresh').addEventListener('click',()=>{$('refreshBtn').click();});
    $('refreshBtn').addEventListener('click',()=>readForUser('lookup',overview));
    $('uaHistoryRefresh').addEventListener('click',()=>readForUser('history',history));
    $('aiFab').addEventListener('click',()=>{$('unifiedAssistant').scrollIntoView({block:'start'});$('uaCommand').focus({preventScroll:true});});
    const change=()=>{const business=$('bizSelect').value||null;if(state.business===business)return;state.business=business;state.generation++;resetContextInputs();refreshContext().catch(error);};
    $('bizSelect').addEventListener('change',change);new MutationObserver(change).observe($('bizSelect'),{childList:true});change();
  }
  // 통합 대시보드에서 넘어온 부탁(?ask=)은 입력칸에만 채운다. 사람이 확인하고 보내도록 자동 전송하지 않는다.
  function prefillAsk(){const ask=window.HubView?window.HubView.askFromSearch(location.search):null;if(!ask)return;setCommand(ask);$('unifiedAssistant').scrollIntoView({block:'start'});$('uaCommand').focus({preventScroll:true});}
  async function init() {if(!$('unifiedAssistant'))return;try{state.user=await ensureCurrentUser();if(!state.user||!['ADMIN','TUTOR'].includes(state.user.role))return;
    try { const res=await fetch('/api/dashboard/businesses',{credentials:'same-origin',cache:'no-store'});if(res.ok){const list=await res.json(),saved=localStorage.getItem('dashboard.businessId');state.business=String(list.find(x=>String(x.businessId)===saved)?.businessId||list[0]?.businessId||'')||null;} } catch(_) {}
    fields();setBusy(true);bind();state.progress=await json('/progress');
    // 첫 방문 안내는 화면을 밀지 않도록 자동으로 펼치지 않고 '사용법 안내' 버튼에 처음 표시만 한다.
    if(!state.progress.completed)$('uaResume').classList.add('ua-new');await refreshContext();prefillAsk();document.dispatchEvent(new CustomEvent('ua:ready'));}catch(e){error(e);document.dispatchEvent(new CustomEvent('ua:failed',{detail:e.message}));}}
  window.unifiedAssistant={selectMode,generalMode:()=>composerMode==='general',bubble,changeChat,beginActivity,activityPose:()=>activityPose,send,createDraft,json,saveProgress,ready:()=>state.ready,busy:()=>state.busy,academy:()=>state.academy,role:()=>state.user&&state.user.role,ownerContext:()=>state.context?.role==='ACADEMY_OWNER',progress:()=>state.progress};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
