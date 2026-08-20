(function(){
  var lastGeneration=null;
  var lastSavedSetId=null;
  var selectedSetId=null;
  var selectedMaterial=null;
  function $(s){return document.querySelector(s)}
  function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
  function apiError(body,raw,status,fallback){return body&&body.detail?body.detail:(raw?raw.slice(0,300):fallback+' HTTP '+status)}
  function status(m,e){var el=$('#materialStatus');if(el){el.textContent=m;el.style.color=e?'#b42318':'#067647'}}
  function genStatus(m,e){var el=$('#generationStatus');if(el){el.textContent=m||'';el.style.color=e?'#b42318':'#067647'}}
  function saveStatus(html,type){var el=$('#saveGeneratedStatus');if(!el)return;el.className='save-generated-status '+(type||'');el.innerHTML=html||''}

  function renderGenerated(text, model){
    var out=$('#generationOutput');if(!out)return;var parsed=null;try{parsed=JSON.parse(text)}catch(e){}
    if(parsed&&Array.isArray(parsed.questions)){
      out.innerHTML='<div class="result-toolbar"><div><div class="result-title">Gemini 생성 결과 · '+esc(model||'gemini')+'</div><div id="saveGeneratedStatus" class="save-generated-status"></div></div><div class="result-actions"><button class="btn-ghost" type="button" data-copy-result>JSON 복사</button><button class="btn-ghost save-db-button" type="button" data-save-generated>DB 저장</button></div></div><div class="exam-sheet"><div class="exam-meta-box"><div class="meta-row"><span>요약</span><b>'+esc(parsed.summary||'생성 완료')+'</b></div></div><div class="question-list">'+parsed.questions.map(function(q,i){return '<article class="q-card"><div class="q-head"><span class="q-num">'+(i+1)+'.</span><b>'+esc(q.type||'문항')+'</b><span class="q-score">'+esc(q.score||'')+'점</span></div><p class="q-stem">'+esc(q.stem||'')+'</p>'+(Array.isArray(q.choices)?'<ol class="opt-list">'+q.choices.map(function(c){return '<li>'+esc(c)+'</li>'}).join('')+'</ol>':'')+'<div class="answer-format">정답: '+esc(q.answer||'')+'</div><div class="grading-rule">'+esc(q.explanation||'')+'</div></article>'}).join('')+'</div><pre id="rawGeminiResult" class="raw-result">'+esc(text)+'</pre></div>';
      return;
    }
    out.innerHTML='<div class="result-toolbar"><div><div class="result-title">Gemini 생성 결과 · '+esc(model||'gemini')+'</div><div id="saveGeneratedStatus" class="save-generated-status"></div></div><div class="result-actions"><button class="btn-ghost" type="button" data-copy-result>텍스트 복사</button></div></div><pre id="rawGeminiResult" class="raw-result">'+esc(text)+'</pre>';
  }

  async function saveGenerated(btn){
    if(!lastGeneration||!lastGeneration.text){genStatus('먼저 Gemini 문제를 생성해 주세요.',true);return}
    btn.disabled=true;btn.textContent='저장 중...';saveStatus('DB에 저장 중입니다...', 'pending');genStatus('생성 문제 DB 저장 중...',false);
    try{
      var title=(lastGeneration.grammar||'Gemini 생성 문제').trim().slice(0,80)||'Gemini 생성 문제';
      var r=await fetch('/api/problem-generator/question-sets',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title:title,materialId:lastGeneration.materialId,grammar:lastGeneration.grammar,promptPassage:lastGeneration.passage,model:lastGeneration.model,rawResponseJson:lastGeneration.text})});
      var raw=await r.text();var body=null;try{body=raw?JSON.parse(raw):null}catch(e){}
      if(!r.ok)throw new Error(apiError(body,raw,r.status,'DB 저장 실패'));
      lastSavedSetId=body.setId;btn.textContent='저장 완료 ✓';btn.classList.add('saved');
      saveStatus('<b>DB 저장 완료</b><span>강사 문제 DB에서 수정/삭제할 수 있습니다.</span><a href="/problem-generator/questions?setId='+encodeURIComponent(lastSavedSetId)+'">저장된 세트 열기 →</a>', 'success');
      genStatus('DB 저장 완료 · 강사 문제 DB에서 확인할 수 있습니다.',false);
    }catch(err){btn.textContent='DB 저장';saveStatus('<b>DB 저장 실패</b><span>'+esc(err&&err.message?err.message:'DB 저장 실패')+'</span>','error');genStatus(err&&err.message?err.message:'DB 저장 실패',true)}finally{btn.disabled=false}
  }

  document.addEventListener('click',function(e){
    var copy=e.target.closest('[data-copy-result]');if(copy){var raw=$('#rawGeminiResult');if(raw&&navigator.clipboard)navigator.clipboard.writeText(raw.textContent||'');return}
    var save=e.target.closest('[data-save-generated]');if(save){saveGenerated(save);return}
    var del=e.target.closest('[data-delete-set]');if(del){deleteQuestionSet(del.getAttribute('data-delete-set'));return}
    var open=e.target.closest('[data-open-set]');if(open){toggleQuestionSet(open.getAttribute('data-open-set'), true);return}
    var detailHead=e.target.closest('.inline-detail-header');if(detailHead&&!e.target.closest('a,button,input,textarea,select')){closeQuestionSet();return}
    var upd=e.target.closest('[data-update-question]');if(upd){updateSavedQuestion(upd);return}
    var b=e.target.closest('[data-action="mock"]');if(!b)return;e.preventDefault();alert('준비 중입니다. 실제 Gemini 생성은 문제 생성기 탭의 생성 버튼에서 테스트할 수 있습니다.')
  });

  loadGeneratorMaterials();

  var key=$('#pgApiKey');if(key){try{key.value=sessionStorage.getItem('f2r_msc_gemini_key')||''}catch(e){} key.addEventListener('input',function(){try{sessionStorage.setItem('f2r_msc_gemini_key',key.value)}catch(e){}})}
  var toggle=$('#toggleGeminiKey');if(toggle&&key){toggle.addEventListener('click',function(){key.type=key.type==='password'?'text':'password'})}
  var save=$('#saveGeminiKey');if(save&&key){save.addEventListener('click',function(){try{sessionStorage.setItem('f2r_msc_gemini_key',key.value)}catch(e){}})}

  function assistantStatus(m,e){var el=$('#assistantStatus');if(el){el.textContent=m||'';el.style.color=e?'#b42318':'#667085'}}
  function addChat(role,text){var log=$('#assistantChatLog');if(!log)return;var p=document.createElement('p');p.className=role;p.textContent=text;log.appendChild(p);log.scrollTop=log.scrollHeight}
  async function sendAssistant(){var input=$('#assistantMessage');var msg=input&&input.value?input.value.trim():'';if(!msg)return;var apiKey='';try{apiKey=sessionStorage.getItem('f2r_msc_gemini_key')||''}catch(e){}if(!apiKey&&$('#pgApiKey'))apiKey=$('#pgApiKey').value||'';if(!apiKey.trim()){assistantStatus('먼저 왼쪽 Gemini API 키를 입력해 주세요.',true);return}var context=document.querySelector('.ai-context span');addChat('user',msg);input.value='';assistantStatus('박원장 AI 응답 생성 중...',false);try{var r=await fetch('/api/problem-generator/gemini/chat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({apiKey:apiKey.trim(),message:msg,context:context?context.textContent:''})});var raw=await r.text();var body=null;try{body=raw?JSON.parse(raw):null}catch(e){}if(!r.ok)throw new Error(apiError(body,raw,r.status,'Gemini 채팅 실패'));addChat('ai',body.text||'응답이 비어 있습니다.');assistantStatus('',false)}catch(err){var m=err&&err.message?err.message:'Gemini 채팅 실패';addChat('ai',m);assistantStatus(m,true)}}
  var assistantSend=$('#assistantSend');if(assistantSend)assistantSend.addEventListener('click',sendAssistant);
  var assistantMsg=$('#assistantMessage');if(assistantMsg)assistantMsg.addEventListener('keydown',function(e){if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing&&e.keyCode!==229){e.preventDefault();sendAssistant()}});
  var assistantClear=$('#assistantClear');if(assistantClear)assistantClear.addEventListener('click',function(){var log=$('#assistantChatLog');if(log)log.innerHTML='<p class="ai">대화 기록을 정리했어요. 현재 작업 기준으로 다시 도와드릴게요.</p>';assistantStatus('',false)});

  var gen=$('#generateGeminiQuestions');if(gen){gen.addEventListener('click',async function(){var apiKey=($('#pgApiKey')||{}).value||'';var grammar=($('#pgGrammar')||{}).value||'';var passage=($('#pgPassage')||{}).value||'';var count=Number(($('#pgCount')||{}).value||3);var difficulty=($('#pgDifficulty')||{}).value||'학교 평균';var materialTitle=selectedMaterial?selectedMaterial.title:'';if(!apiKey.trim()){genStatus('Gemini API 키를 입력해 주세요.',true);return}if(!grammar.trim()&&!passage.trim()){genStatus('핵심 문법 또는 새 지문을 입력해 주세요.',true);return}gen.disabled=true;genStatus('Gemini API 호출 중...',false);try{var r=await fetch('/api/problem-generator/gemini/generate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({apiKey:apiKey.trim(),grammar:grammar,passage:passage,count:count,difficulty:difficulty,materialTitle:materialTitle})});var raw=await r.text();var body=null;try{body=raw?JSON.parse(raw):null}catch(e){}if(!r.ok)throw new Error(apiError(body,raw,r.status,'Gemini 호출 실패'));lastGeneration={text:body.text,model:body.model,grammar:grammar,passage:passage,materialId:selectedMaterial?selectedMaterial.materialId:null,materialTitle:materialTitle};lastSavedSetId=null;renderGenerated(body.text,body.model);genStatus('생성 완료 · DB 저장 버튼으로 보관할 수 있습니다.',false)}catch(err){genStatus(err&&err.message?err.message:'생성 실패',true)}finally{gen.disabled=false}})}


  function renderGeneratorMaterials(items){
    var sel=$('#pgMaterialSelect');if(!sel)return;
    if(!items.length){sel.innerHTML='<option value="">업로드된 학교 없음</option>';selectedMaterial=null;updateSelectedMaterialPreview();return}
    sel.innerHTML='<option value="">Digital Twin 선택</option>'+items.map(function(m){return '<option value="'+esc(m.materialId)+'">'+esc(m.title)+'</option>'}).join('');
    var stored='';try{stored=sessionStorage.getItem('f2r_msc_material_id')||''}catch(e){}
    if(stored&&items.some(function(m){return String(m.materialId)===String(stored)}))sel.value=stored;
    selectedMaterial=items.find(function(m){return String(m.materialId)===String(sel.value)})||null;
    updateSelectedMaterialPreview();
    sel.addEventListener('change',function(){selectedMaterial=items.find(function(m){return String(m.materialId)===String(sel.value)})||null;try{sessionStorage.setItem('f2r_msc_material_id',sel.value||'')}catch(e){}updateSelectedMaterialPreview()});
  }
  function updateSelectedMaterialPreview(){
    var hint=$('#pgMaterialHint');var output=$('#generationOutput');
    if(hint)hint.textContent=selectedMaterial?('선택됨: '+selectedMaterial.title+' · '+selectedMaterial.visibility+' · 저장 시 문제 묶음에 연결됩니다.'):'업로드한 Digital Twin 자료를 선택하면 생성/저장에 연결됩니다.';
    var badge=document.querySelector('.f2r-gen .header-badge');if(badge)badge.innerHTML='<span class="dot"></span>'+(selectedMaterial?'Digital Twin 적용 중':'Digital Twin 선택 필요');
    var eyebrow=document.querySelector('.f2r-gen .eyebrow');if(eyebrow)eyebrow.textContent=(selectedMaterial?'1':'0')+'개 학교 Digital Twin 연동';
    if(output&&!lastGeneration&&selectedMaterial){output.innerHTML='<div class="selected-material-preview"><div><p class="kicker">SELECTED DIGITAL TWIN</p><h2>'+esc(selectedMaterial.title)+'</h2><p>'+esc(selectedMaterial.originalFileName||'자료')+' · '+esc(selectedMaterial.visibility||'PRIVATE')+' · '+esc(selectedMaterial.pageCount||1)+'p</p></div><img src="/api/problem-generator/materials/'+encodeURIComponent(selectedMaterial.materialId)+'/file?page=1" alt="선택 Digital Twin 미리보기"></div><div class="empty-state compact-empty"><div class="empty-title">선택한 Digital Twin으로 생성 준비 완료</div><div class="empty-desc">왼쪽에 Gemini 키·핵심 문법·새 지문을 입력하고 생성 버튼을 누르세요.</div></div>'}
  }
  async function loadGeneratorMaterials(){
    if(!$('#pgMaterialSelect'))return;
    try{var r=await fetch('/api/problem-generator/materials?page=0&size=50');var body=await r.json();if(!r.ok)throw new Error('자료 조회 실패');renderGeneratorMaterials(body.content||[])}catch(e){var hint=$('#pgMaterialHint');if(hint)hint.textContent='Digital Twin 자료 목록을 불러오지 못했습니다.'}
  }

  function qsStatus(m,e){var el=$('#questionSetStatus');if(el){el.textContent=m||'';el.style.color=e?'#b42318':'#067647'}}
  function selectedParam(){try{return new URLSearchParams(location.search).get('setId')}catch(e){return null}}
  function renderQuestionSetList(items){
    var list=$('#questionSetList');if(!list)return;
    if(!items.length){list.innerHTML='<div class="empty-state compact-empty"><b>저장된 문항이 없습니다.</b><p>문제 생성기에서 만든 문항을 저장하면 이곳에서 관리할 수 있어요.</p><a class="primary-link" href="/problem-generator">문제 생성으로 이동</a></div>';return}
    list.innerHTML='<div class="question-set-list inline-list" aria-label="저장 문제 묶음 목록">'+items.map(function(s){var active=String(s.setId)===String(selectedSetId);return '<article class="question-set-card '+(active?'selected':'')+'" data-set-card="'+s.setId+'"><button class="set-main" type="button" data-open-set="'+s.setId+'"><span class="set-badge">'+(active?'선택됨':'저장 세트')+'</span><b>'+esc(s.title)+'</b><small>'+esc(s.model||'model 없음')+' · '+esc(s.visibility||'PRIVATE')+'</small><time>'+esc((s.createdAt||'').replace('T',' ').slice(0,16))+'</time></button><button class="set-delete" type="button" data-delete-set="'+s.setId+'">삭제</button></article><div class="inline-set-detail" data-inline-detail="'+s.setId+'">'+(active?'<div class="selected-loading">상세 불러오는 중...</div>':'')+'</div>'}).join('')+'</div>';
  }
  function closeQuestionSet(){
    var previous=selectedSetId;selectedSetId=null;
    document.querySelectorAll('[data-set-card]').forEach(function(card){card.classList.remove('selected');var badge=card.querySelector('.set-badge');if(badge)badge.textContent='저장 세트'});
    document.querySelectorAll('[data-inline-detail]').forEach(function(el){el.innerHTML=''});
    qsStatus(previous?'선택 해제 · 상세를 보려면 카드를 다시 선택하세요':'상세를 보려면 카드를 선택하세요',false);
    try{if(previous&&history.replaceState){var url=new URL(location.href);url.searchParams.delete('setId');history.replaceState(null,'',url.pathname+(url.search?url.search:''))}}catch(e){}
  }
  function toggleQuestionSet(id,scroll){
    var panel=document.querySelector('[data-inline-detail="'+CSS.escape(String(id))+'"]');
    if(String(selectedSetId)===String(id)&&panel&&panel.innerHTML.trim()){closeQuestionSet();return}
    openQuestionSet(id,scroll);
  }
  async function loadQuestionSets(autoOpenId){var list=$('#questionSetList');if(!list)return;qsStatus('문제 DB 불러오는 중...',false);try{var r=await fetch('/api/problem-generator/question-sets?page=0&size=30');var raw=await r.text();var body=null;try{body=raw?JSON.parse(raw):null}catch(e){}if(!r.ok)throw new Error(apiError(body,raw,r.status,'문제 DB 조회 실패'));var items=body.content||[];if(!selectedSetId)selectedSetId=autoOpenId||selectedParam();renderQuestionSetList(items);qsStatus('저장 묶음 '+items.length+'개 · 상세를 보려면 카드를 선택하세요',false);if(selectedSetId&&items.some(function(x){return String(x.setId)===String(selectedSetId)}))openQuestionSet(selectedSetId,false)}catch(err){qsStatus(err&&err.message?err.message:'문제 DB 조회 실패',true)}}
  async function openQuestionSet(id,scroll){
    selectedSetId=id;
    document.querySelectorAll('[data-set-card]').forEach(function(card){var active=String(card.getAttribute('data-set-card'))===String(id);card.classList.toggle('selected',active);var badge=card.querySelector('.set-badge');if(badge)badge.textContent=active?'선택됨':'저장 세트'});
    document.querySelectorAll('[data-inline-detail]').forEach(function(el){if(String(el.getAttribute('data-inline-detail'))!==String(id))el.innerHTML=''});
    var panel=document.querySelector('[data-inline-detail="'+CSS.escape(String(id))+'"]')||$('#questionSetDetail');if(!panel)return;
    panel.innerHTML='<div class="selected-loading">상세 불러오는 중...</div>';qsStatus('상세 불러오는 중...',false);
    try{var r=await fetch('/api/problem-generator/question-sets/'+id);var raw=await r.text();var body=null;try{body=raw?JSON.parse(raw):null}catch(e){}if(!r.ok)throw new Error(apiError(body,raw,r.status,'상세 조회 실패'));
      panel.innerHTML='<div class="inline-detail-header"><span class="set-badge selected">선택됨</span><div><p class="kicker">SAVED SET</p><h2>'+esc(body.title)+'</h2><p>'+(body.questions||[]).length+'문항 · '+(body.questions||[]).filter(function(q){return q.passageContent}).length+'개 지문 연결 · '+esc(body.visibility||'PRIVATE')+'</p></div><a class="secondary-button" href="/problem-generator/questions?setId='+encodeURIComponent(body.setId)+'">이 세트 링크</a></div><div class="inline-question-stack">'+(body.questions||[]).map(function(q,i){return '<article class="edit-q-card" data-q="'+q.questionId+'"><div class="edit-q-head"><b>'+(i+1)+'. 문항 편집</b><span>'+esc(q.questionType||'')+' · '+esc(q.point||0)+'점</span></div><label>공유 지문<textarea data-field="passageContent">'+esc(q.passageContent||'')+'</textarea></label><label>문항<textarea data-field="questionText">'+esc(q.questionText||'')+'</textarea></label><div class="edit-grid"><label>정답<input data-field="correctAnswer" value="'+esc(q.correctAnswer||'')+'"></label><label>배점<input data-field="point" type="number" min="0" value="'+esc(q.point||0)+'"></label></div><label>선택지(줄바꿈)<textarea data-field="choices">'+esc((q.choices||[]).join('\n'))+'</textarea></label><label>해설<textarea data-field="explanation">'+esc(q.explanation||'')+'</textarea></label><button class="primary-action" type="button" data-update-question="'+q.questionId+'" data-set-id="'+body.setId+'">문항 수정 저장</button></article>'}).join('')+'</div>';
      qsStatus('선택한 묶음: '+body.title,false);if(scroll)panel.scrollIntoView({behavior:'smooth',block:'nearest'});
    }catch(err){qsStatus(err&&err.message?err.message:'상세 조회 실패',true);panel.innerHTML='<div class="selected-empty error">'+esc(err&&err.message?err.message:'상세 조회 실패')+'</div>'}}
  async function updateSavedQuestion(btn){var card=btn.closest('[data-q]');if(!card)return;var field=function(n){var el=card.querySelector('[data-field="'+n+'"]');return el?el.value:''};btn.disabled=true;btn.textContent='저장 중...';qsStatus('문항 수정 중...',false);try{var choices=field('choices').split('\n').map(function(x){return x.trim()}).filter(Boolean);var r=await fetch('/api/problem-generator/question-sets/'+btn.getAttribute('data-set-id')+'/questions/'+btn.getAttribute('data-update-question'),{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({questionType:choices.length?'MULTIPLE_CHOICE':'SHORT_ANSWER',questionText:field('questionText'),choices:choices,correctAnswer:field('correctAnswer'),explanation:field('explanation'),point:Number(field('point')||0),passageContent:field('passageContent')})});var raw=await r.text();var body=null;try{body=raw?JSON.parse(raw):null}catch(e){}if(!r.ok)throw new Error(apiError(body,raw,r.status,'문항 수정 실패'));btn.textContent='수정 완료 ✓';qsStatus('문항 수정 완료',false);setTimeout(function(){btn.textContent='문항 수정 저장';btn.disabled=false},1200)}catch(err){btn.textContent='문항 수정 저장';btn.disabled=false;qsStatus(err&&err.message?err.message:'문항 수정 실패',true)}}
  async function deleteQuestionSet(id){if(!confirm('저장된 문제 묶음을 삭제할까요?'))return;qsStatus('삭제 중...',false);try{var r=await fetch('/api/problem-generator/question-sets/'+id,{method:'DELETE'});if(!r.ok){var raw=await r.text();throw new Error(raw||('삭제 실패 HTTP '+r.status))}if(String(selectedSetId)===String(id))selectedSetId=null;qsStatus('삭제 완료',false);loadQuestionSets()}catch(err){qsStatus(err&&err.message?err.message:'삭제 실패',true)}}
  if($('#questionSetList')){loadQuestionSets();var refresh=$('#refreshQuestionSets');if(refresh)refresh.addEventListener('click',function(){selectedSetId=null;loadQuestionSets()})}

  async function load(){var list=$('#materialList');if(!list)return;var r=await fetch('/api/problem-generator/materials?page=0&size=20');if(!r.ok){list.innerHTML='<div class="pg-placeholder">자료 목록을 불러오지 못했습니다.</div>';return}var p=await r.json(),items=p.content||[];if(items.length===0){list.innerHTML='<div class="pg-placeholder">아직 업로드된 자료가 없습니다.</div>';return}list.innerHTML=items.map(function(m){return '<div class="pg-material"><div><b>'+esc(m.title)+'</b><br><small>'+esc(m.originalFileName)+' · '+m.visibility+'</small></div><button class="pg-btn" data-delete="'+m.materialId+'">삭제</button></div>'}).join('')}
  var form=$('#materialUploadForm');if(form){load();form.addEventListener('submit',async function(e){e.preventDefault();var fd=new FormData(form);var url=form.publicUpload&&form.publicUpload.checked?'/api/admin/problem-generator/materials':'/api/problem-generator/materials';status('업로드 중...',false);var r=await fetch(url,{method:'POST',body:fd});if(!r.ok){status(await r.text()||('업로드 실패 HTTP '+r.status),true);return}form.reset();status('업로드 완료',false);load()});document.addEventListener('click',async function(e){var b=e.target.closest('[data-delete]');if(!b)return;var r=await fetch('/api/problem-generator/materials/'+b.getAttribute('data-delete'),{method:'DELETE'});if(!r.ok){status(await r.text()||'삭제 실패',true);return}status('삭제 완료',false);load()})}
})();
