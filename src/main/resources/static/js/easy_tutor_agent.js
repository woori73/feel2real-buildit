(function () {
  'use strict';

  const state = { businessId: null, sessionId: null, businesses: [], transactions: [], dashboard: null, busy: false, view: 'dashboard' };
  const el = id => document.getElementById(id);
  const api = path => '/api/tutor/easy' + path;
  const won = value => Number(value || 0).toLocaleString('ko-KR') + '원';
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[ch]);
  const txnOf = row => row && row.transaction ? row.transaction : row;

  async function responseError(response, fallback) {
    try { const body = await response.json(); return body.message || body.detail || body.error || fallback; }
    catch (_) { try { return (await response.text()) || fallback; } catch (_) { return fallback; } }
  }
  async function json(url, options) {
    const response = await fetchAuth(url, options);
    if (!response.ok) throw new Error(await responseError(response, '요청을 처리하지 못했습니다.'));
    if (response.status === 204) return null;
    return response.json();
  }
  async function guardLedger() {
    const user = await ensureCurrentUser();
    if (!user || !['ADMIN', 'TUTOR'].includes(user.role)) {
      location.href = user ? dashboardPathForRole(user.role) : '/?login=required&returnUrl=%2Fledger';
      throw new Error('ADMIN 또는 TUTOR 로그인이 필요합니다.');
    }
  }

  function monthRange(offset) {
    const now = new Date();
    const first = new Date(now.getFullYear(), now.getMonth() + (offset || 0), 1);
    const last = new Date(first.getFullYear(), first.getMonth() + 1, 0);
    const iso = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    return { from: iso(first), to: iso(last), month: first.getFullYear() + '-' + String(first.getMonth() + 1).padStart(2, '0'), label: first.getFullYear() + '년 ' + (first.getMonth() + 1) + '월' };
  }
  function defaultDates() {
    const range = monthRange(0);
    ['filterFrom','journalFrom'].forEach(id => { el(id).value = range.from; });
    ['filterTo','journalTo'].forEach(id => { el(id).value = range.to; });
  }
  function query(params) {
    const value = new URLSearchParams();
    Object.entries(params).forEach(([key, item]) => { if (item !== '' && item != null) value.set(key, item); });
    return value.toString();
  }
  function incomeLabel(t) {
    const text = ((t.accountName || '') + ' ' + (t.category || '') + ' ' + (t.description || '')).toLowerCase();
    if (/교재|도서|책/.test(text)) return '교재비';
    if (/환불/.test(text)) return '환불·조정';
    if (/수강|학원|교육|매출/.test(text)) return '수강료';
    return t.accountName || t.category || '기타 수입';
  }
  function expenseLabel(t) {
    const text = ((t.accountName || '') + ' ' + (t.category || '') + ' ' + (t.description || '')).toLowerCase();
    if (/강사|급여|인건/.test(text)) return '강사료';
    if (/임차|월세|관리비/.test(text)) return '임차료·관리비';
    if (/교재|도서|상품|매입/.test(text)) return '교재·상품 매입';
    if (/광고|통신|소모|운영|수수료/.test(text)) return '운영비';
    return t.accountName || t.category || '기타 경비';
  }
  function isPurchase(t) { return t.direction === 'EXPENSE' && /교재|도서|상품|매입/.test((t.accountName || '') + ' ' + (t.category || '') + ' ' + (t.description || '')); }
  function summarize(rows, direction) {
    const map = new Map();
    rows.map(txnOf).filter(t => t.direction === direction).forEach(t => {
      const label = direction === 'INCOME' ? incomeLabel(t) : expenseLabel(t);
      map.set(label, (map.get(label) || 0) + Number(t.amount || 0));
    });
    return Array.from(map.entries()).sort((a,b) => b[1] - a[1]);
  }

  function openView(name) {
    state.view = name;
    document.querySelectorAll('.ledger-tab').forEach(button => button.classList.toggle('active', button.dataset.view === name));
    document.querySelectorAll('.ledger-view').forEach(view => view.classList.toggle('active', view.id === 'view-' + name));
    if (name === 'transactions') loadTransactions().catch(showFatal);
    if (name === 'journal') loadJournal().catch(showFatal);
    if (name === 'advanced') loadTrialBalance().catch(showFatal);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function loadBusinesses() {
    state.businesses = await json(api('/businesses'));
    const select = el('businessSelect');
    select.innerHTML = '';
    state.businesses.forEach(item => select.add(new Option(item.name, item.businessId)));
    if (!state.businesses.length) {
      setStatus('연결된 장부 없음', true);
      document.querySelector('.ledger-content').innerHTML = '<section class="panel"><h1>연결된 장부가 없습니다.</h1><p>관리자에게 학원과 Easy 사업체 연결을 요청해 주세요.</p></section>';
      return false;
    }
    state.businessId = Number(select.value);
    return true;
  }

  async function loadDashboard() {
    const range = monthRange(0);
    const [monthly, rows, reviews] = await Promise.all([
      json(api('/workspace/monthly?' + query({ businessId: state.businessId, month: range.month }))),
      json(api('/workspace/transactions?' + query({ businessId: state.businessId, from: range.from, to: range.to }))),
      json(api('/workspace/reviews?' + query({ businessId: state.businessId, status: 'OPEN' })))
    ]);
    state.dashboard = monthly;
    state.transactions = rows || [];
    const txns = state.transactions.map(txnOf);
    const income = Number(monthly.income || 0);
    const totalExpense = Number(monthly.expense || 0);
    const purchase = txns.filter(isPurchase).reduce((sum,t) => sum + Number(t.amount || 0), 0);
    const expense = Math.max(0, totalExpense - purchase);
    el('dashboardPeriod').textContent = range.label + ' · 거래 ' + txns.length + '건';
    el('summaryIncome').textContent = won(income);
    el('summaryPurchase').textContent = won(purchase);
    el('summaryExpense').textContent = won(expense);
    el('summaryProfit').textContent = won(monthly.profit);
    renderBreakdown('incomeBreakdown', summarize(state.transactions, 'INCOME'), 'INCOME');
    renderBreakdown('expenseBreakdown', summarize(state.transactions, 'EXPENSE'), 'EXPENSE');
    renderAttention(reviews || [], txns.filter(t => !t.accountId));
    renderRecent(monthly.recentTransactions || state.transactions.slice(-8).reverse());
  }

  function renderBreakdown(targetId, items, direction) {
    const target = el(targetId);
    if (!items.length) { target.innerHTML = '<p class="empty-state">이번 달 내역이 없습니다.</p>'; return; }
    target.innerHTML = items.slice(0, 6).map(([label, amount]) =>
      '<button class="breakdown-row" type="button" data-breakdown="' + esc(label) + '" data-direction="' + direction + '"><span class="breakdown-label"><strong>' + esc(label) + '</strong><small>거래 상세 보기</small></span><span class="breakdown-amount">' + esc(won(amount)) + '</span></button>'
    ).join('');
  }
  function renderAttention(reviews, unclassified) {
    const target = el('attentionList');
    const total = Math.max(reviews.length, unclassified.length);
    el('reviewCount').textContent = total + '건';
    if (!total) { target.innerHTML = '<p class="empty-state">확인할 항목이 없습니다.</p>'; return; }
    const items = unclassified.slice(0, 5);
    if (items.length) {
      target.innerHTML = items.map(t => '<button class="attention-row" type="button" data-txn-id="' + t.txnId + '"><span class="attention-copy"><strong>' + esc(t.counterparty || t.description || '미분류 거래') + '</strong><small>' + esc(t.occurredOn) + ' · 계정과목 확인 필요</small></span><span class="breakdown-amount">' + esc(won(t.amount)) + '</span></button>').join('');
    } else {
      target.innerHTML = reviews.slice(0,5).map(r => '<button class="attention-row" type="button" data-review="true"><span class="attention-copy"><strong>' + esc(r.title || r.kind || '확인 필요') + '</strong><small>' + esc(r.message || r.reason || '검토가 필요한 항목입니다.') + '</small></span></button>').join('');
    }
  }
  function renderRecent(rows) {
    const target = el('recentTransactions');
    const txns = rows.map(txnOf).filter(Boolean).slice(0, 8);
    if (!txns.length) { target.innerHTML = '<p class="empty-state">최근 거래가 없습니다.</p>'; return; }
    target.innerHTML = txns.map(t => '<button class="recent-row" type="button" data-txn-id="' + t.txnId + '"><span class="recent-copy"><strong>' + esc(t.counterparty || t.description || '거래') + '</strong><small>' + esc(t.occurredOn) + ' · ' + esc(t.accountName || '미분류') + '</small></span><span class="recent-amount ' + String(t.direction).toLowerCase() + '">' + (t.direction === 'EXPENSE' ? '-' : '+') + esc(won(t.amount)) + '</span></button>').join('');
  }

  async function loadTransactions(overrides) {
    const params = {
      businessId: state.businessId,
      from: el('filterFrom').value,
      to: el('filterTo').value,
      direction: el('filterDirection').value
    };
    if (overrides && overrides.direction) { params.direction = overrides.direction; el('filterDirection').value = overrides.direction; }
    let rows = await json(api('/workspace/transactions?' + query(params)));
    const search = el('filterSearch').value.trim().toLowerCase();
    if (search) rows = rows.filter(row => { const t=txnOf(row); return [t.counterparty,t.description,t.accountName,t.category].some(v => String(v||'').toLowerCase().includes(search)); });
    state.transactions = rows;
    renderTransactions(rows);
  }
  function statusText(row) {
    const t=txnOf(row);
    if (!t.accountId || row.journalStatus === 'NOT_POSTED') return { text:'확인 필요', cls:'review' };
    return { text:'장부 반영', cls:'ok' };
  }
  function renderTransactions(rows) {
    const body=el('transactionTableBody'), cards=el('transactionCards');
    body.innerHTML=''; cards.innerHTML='';
    el('transactionEmpty').hidden = rows.length > 0;
    rows.forEach(row => {
      const t=txnOf(row), status=statusText(row), direction=t.direction==='INCOME'?'수입':'지출';
      const tr=document.createElement('tr');
      tr.innerHTML='<td>'+esc(t.occurredOn)+'</td><td><span class="direction-pill '+String(t.direction).toLowerCase()+'">'+direction+'</span></td><td><strong>'+esc(t.counterparty||t.description||'거래')+'</strong><br><small>'+esc(t.description||'')+'</small></td><td>'+esc(t.accountName||'미분류')+'</td><td><span class="status-pill '+status.cls+'">'+status.text+'</span></td><td class="amount">'+esc(won(t.amount))+'</td><td><button type="button" data-txn-id="'+t.txnId+'">상세</button></td>';
      body.appendChild(tr);
      const card=document.createElement('article'); card.className='mobile-transaction';
      card.innerHTML='<div class="mobile-transaction-top"><strong>'+esc(t.counterparty||t.description||'거래')+'</strong><span class="amount">'+(t.direction==='EXPENSE'?'-':'+')+esc(won(t.amount))+'</span></div><p>'+esc(t.occurredOn)+' · '+esc(t.accountName||'미분류')+'</p><div class="mobile-transaction-bottom"><span class="status-pill '+status.cls+'">'+status.text+'</span><button type="button" data-txn-id="'+t.txnId+'">상세·수정 요청</button></div>';
      cards.appendChild(card);
    });
  }

  async function openDetail(txnId) {
    const row = await json(api('/workspace/transactions/' + txnId + '?' + query({ businessId: state.businessId })));
    const t=txnOf(row), direction=t.direction==='INCOME'?'수입':'지출';
    el('detailBody').innerHTML='<dl class="detail-grid"><dt>날짜</dt><dd>'+esc(t.occurredOn)+'</dd><dt>구분</dt><dd>'+direction+'</dd><dt>거래처</dt><dd>'+esc(t.counterparty||'-')+'</dd><dt>내용</dt><dd>'+esc(t.description||'-')+'</dd><dt>계정과목</dt><dd>'+esc(t.accountName||'미분류')+'</dd><dt>금액</dt><dd>'+esc(won(t.amount))+'</dd><dt>장부 상태</dt><dd>'+esc(statusText(row).text)+'</dd><dt>원본</dt><dd>'+esc(t.sourceRef||'수기 거래')+'</dd></dl><div class="detail-actions"><button class="ask-ai" type="button" data-edit-txn="'+t.txnId+'">이 거래 AI에게 질문·수정 요청</button></div>';
    el('transactionDetail').classList.add('open'); el('transactionDetail').setAttribute('aria-hidden','false'); el('detailBackdrop').hidden=false;
  }
  function closeDetail(){el('transactionDetail').classList.remove('open');el('transactionDetail').setAttribute('aria-hidden','true');el('detailBackdrop').hidden=true;}

  async function loadJournal() {
    const rows = await json(api('/accounting/journal?' + query({ businessId:state.businessId, from:el('journalFrom').value, to:el('journalTo').value })));
    const target=el('journalList');
    if(!rows.length){target.innerHTML='<p class="empty-state">해당 기간에 장부 반영된 분개가 없습니다.</p>';return;}
    target.innerHTML=rows.map(entry=>'<article class="journal-entry"><div class="journal-entry-head"><strong>'+esc(entry.date)+'</strong><div><strong>'+esc(entry.counterparty||entry.description||entry.memo||'분개 #'+entry.journalEntryId)+'</strong><small>거래 '+esc(entry.transactionId||'-')+' · 분개 '+esc(entry.journalEntryId)+'</small></div><span class="journal-balance">'+(entry.balanced?'차변·대변 일치':'불일치 확인')+'</span></div><table class="journal-lines"><thead><tr><th>계정과목</th><th>차변</th><th>대변</th></tr></thead><tbody>'+entry.lines.map(line=>'<tr><td>'+esc(line.accountName)+'</td><td>'+esc(line.debit?won(line.debit):'-')+'</td><td>'+esc(line.credit?won(line.credit):'-')+'</td></tr>').join('')+'</tbody></table></article>').join('');
  }
  async function loadTrialBalance() {
    if(!state.businessId)return;
    const report=await json(api('/accounting/trial-balance?'+query({businessId:state.businessId,from:el('journalFrom').value,to:el('journalTo').value})));
    const rows=report.rows||[];
    el('bookResult').innerHTML='<p class="'+(report.balanced?'balance-ok':'agent-error')+'">'+(report.balanced?'차변·대변이 일치합니다.':'차변·대변 차이를 확인해 주세요.')+'</p><table class="trial-table"><thead><tr><th>계정</th><th>차변</th><th>대변</th></tr></thead><tbody>'+rows.map(row=>'<tr><td>'+esc(row.accountName)+'</td><td>'+esc(won(row.totalDebit))+'</td><td>'+esc(won(row.totalCredit))+'</td></tr>').join('')+'</tbody></table>';
  }

  function sessionKey(){return'easyAgentSession:'+state.businessId;}
  async function restoreOrCreateSession(){
    state.sessionId=Number(sessionStorage.getItem(sessionKey()))||null;
    if(state.sessionId){try{const current=await json(api('/agent/sessions/'+state.sessionId+'?businessId='+state.businessId));renderHistory(current.messages||[]);return;}catch(_){sessionStorage.removeItem(sessionKey());}}
    const created=await json(api('/agent/sessions?businessId='+state.businessId),{method:'POST'});state.sessionId=created.sessionId;sessionStorage.setItem(sessionKey(),String(state.sessionId));renderHistory([]);
  }
  function setStatus(text,offline){el('connectionState').textContent=text;el('connectionState').classList.toggle('offline',Boolean(offline));}
  function setBusy(value){state.busy=value;el('chatMessages').setAttribute('aria-busy',String(value));el('sendButton').disabled=value||!state.businessId;el('chatInput').disabled=value||!state.businessId;}
  function renderHistory(messages){const log=el('chatMessages');log.innerHTML='';if(!messages.length){log.innerHTML='<div class="chat-empty">거래를 보다가 궁금한 내용을 물어보세요.</div>';return;}messages.forEach(renderMessage);scrollChat();}
  function payloadOf(message){if(!message||!message.payloadJson)return null;try{return JSON.parse(message.payloadJson);}catch(_){return null;}}
  function renderMessage(message){const log=el('chatMessages'),empty=log.querySelector('.chat-empty');if(empty)empty.remove();const bubble=document.createElement('article');bubble.className='message '+(message.role==='USER'?'user':'assistant');bubble.textContent=message.content||'';if(message.createdAt){const meta=document.createElement('span');meta.className='message-meta';meta.textContent=new Date(message.createdAt).toLocaleString('ko-KR');bubble.appendChild(meta);}log.appendChild(bubble);const payload=payloadOf(message);if(message.kind==='PROPOSAL'&&payload)renderProposal(payload);if(message.kind==='RESULT'&&Array.isArray(payload))renderAgentTransactions(payload);}
  function renderProposal(action){const card=document.createElement('section');card.className='proposal-card';card.dataset.actionId=action.actionId;card.dataset.status=action.status||'PROPOSED';const type=action.type==='SELECT_ACCOUNT'?'계정과목 변경안':'장부 반영안';card.innerHTML='<h3>'+esc(type)+'</h3><p>'+esc(action.summary)+'</p><div class="proposal-scope">금액·거래일·원본 CSV는 변경하지 않습니다.'+(action.expiresAt?' · '+esc(new Date(action.expiresAt).toLocaleString('ko-KR'))+'까지':'')+'</div><div class="proposal-actions"><button type="button" class="proposal-reject">거절</button><button type="button" class="proposal-approve">승인하고 반영</button></div><div class="proposal-result"></div>';card.querySelector('.proposal-approve').addEventListener('click',()=>decideAction(card,'approve'));card.querySelector('.proposal-reject').addEventListener('click',()=>decideAction(card,'reject'));el('chatMessages').appendChild(card);}
  async function decideAction(card,decision){if(card.dataset.status!=='PROPOSED')return;card.querySelectorAll('button').forEach(b=>b.disabled=true);const result=card.querySelector('.proposal-result');result.textContent=decision==='approve'?'승인 처리 중…':'거절 처리 중…';try{const action=await json(api('/agent/actions/'+card.dataset.actionId+'/'+decision+'?businessId='+state.businessId),{method:'POST'});card.dataset.status=action.status;result.textContent=action.status==='APPLIED'?'승인되어 장부에 반영했습니다.':'제안을 거절했습니다.';if(action.status==='APPLIED')await Promise.all([loadDashboard(),loadTransactions(),loadJournal()]);}catch(error){result.textContent=error.message;card.dataset.status='FAILED';}}
  function renderAgentTransactions(rows){if(!rows.length)return;const list=document.createElement('section');list.className='transaction-results';rows.slice(0,12).forEach(row=>{const t=txnOf(row);const card=document.createElement('article');card.className='transaction-card';card.innerHTML='<div class="transaction-top"><strong>'+esc(t.description||t.counterparty||'거래 #'+(t.rowId||t.txnId))+'</strong><span>'+esc(won(t.amount))+'</span></div><div class="transaction-sub">'+esc(t.occurredOn||'')+' · '+esc(t.matchedAccountName||t.accountName||'미분류')+'</div><button type="button">이 거래 수정 요청</button>';card.querySelector('button').addEventListener('click',()=>{el('chatInput').value='거래 '+(t.rowId||t.txnId)+'의 계정과목을 수정해 줘';el('chatInput').focus();});list.appendChild(card);});el('chatMessages').appendChild(list);}
  async function send(text){const value=String(text||'').trim();if(!value||state.busy)return;el('chatError').hidden=true;renderMessage({role:'USER',kind:'TEXT',content:value});setBusy(true);try{const result=await json(api('/agent/sessions/'+state.sessionId+'/messages?businessId='+state.businessId),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:value})});renderMessage(result.message);if(result.pendingAction&&result.message.kind!=='PROPOSAL')renderProposal(result.pendingAction);setStatus('연결됨',false);}catch(error){el('chatError').textContent=error.message;el('chatError').hidden=false;setStatus('AI 연결 오류 · 수동 장부는 사용 가능',true);}finally{setBusy(false);scrollChat();}}
  function scrollChat(){const log=el('chatMessages');log.scrollTop=log.scrollHeight;}
  function openAgent(prompt){el('agentPanel').classList.add('open');if(prompt)el('chatInput').value=prompt;setTimeout(()=>el('chatInput').focus(),0);}
  function closeAgent(){el('agentPanel').classList.remove('open');}

  async function upload(event){event.preventDefault();const file=el('csvFile').files[0];if(!file)return;const data=new FormData();data.append('file',file);el('uploadButton').disabled=true;el('uploadResult').textContent='원본을 보존하고 분류 규칙을 적용하는 중…';try{const batch=await json(api('/imports?businessId='+state.businessId),{method:'POST',body:data});el('uploadResult').textContent=batch.totalRows+'건 업로드 완료 · 승인 전';el('csvFile').value='';await loadBatches();openAgent('배치 '+batch.batchId+'에서 아직 분류하지 않은 거래 보여줘');await send(el('chatInput').value);}catch(error){el('uploadResult').textContent='업로드 실패: '+error.message;}finally{el('uploadButton').disabled=false;}}
  async function loadBatches(){const batches=await json(api('/imports?businessId='+state.businessId));const target=el('batchList');if(!batches.length){target.innerHTML='<p class="empty-state">업로드된 CSV가 없습니다.</p>';return;}target.innerHTML=batches.slice(0,8).map(batch=>'<article class="batch-card"><div class="batch-title"><span>'+esc(batch.sourceName||'CSV')+'</span><span>'+esc(batch.status)+'</span></div><div class="batch-meta">배치 '+esc(batch.batchId)+' · '+esc(batch.totalRows)+'건 · 반영 '+esc(batch.confirmedRows)+'건</div></article>').join('');}

  async function changeBusiness(){state.businessId=Number(el('businessSelect').value);setStatus('연결 중',false);await restoreOrCreateSession();await Promise.all([loadDashboard(),loadBatches()]);if(state.view==='transactions')await loadTransactions();if(state.view==='journal')await loadJournal();setStatus('연결됨',false);}
  function bind(){
    document.querySelectorAll('.ledger-tab').forEach(button=>button.addEventListener('click',()=>openView(button.dataset.view)));
    document.querySelectorAll('[data-open-view]').forEach(button=>button.addEventListener('click',()=>openView(button.dataset.openView)));
    document.querySelectorAll('[data-ledger-filter]').forEach(button=>button.addEventListener('click',()=>{openView('transactions');loadTransactions({direction:button.dataset.ledgerFilter});}));
    document.querySelectorAll('[data-summary-filter]').forEach(button=>button.addEventListener('click',()=>{const value=button.dataset.summaryFilter;openView('transactions');if(value==='INCOME'||value==='EXPENSE')loadTransactions({direction:value});}));
    document.body.addEventListener('click',event=>{const txn=event.target.closest('[data-txn-id]');if(txn)openDetail(txn.dataset.txnId);const breakdown=event.target.closest('[data-breakdown]');if(breakdown){el('filterSearch').value=breakdown.dataset.breakdown;openView('transactions');loadTransactions({direction:breakdown.dataset.direction});}const edit=event.target.closest('[data-edit-txn]');if(edit){closeDetail();openAgent('거래 '+edit.dataset.editTxn+'의 현재 분류 이유를 설명하고, 필요한 경우 계정과목 변경안을 제안해 줘.');}});
    el('businessSelect').addEventListener('change',()=>changeBusiness().catch(showFatal));
    el('ledgerFilterForm').addEventListener('submit',event=>{event.preventDefault();loadTransactions().catch(showFatal);});
    el('journalFilterForm').addEventListener('submit',event=>{event.preventDefault();loadJournal().catch(showFatal);});
    el('chatForm').addEventListener('submit',event=>{event.preventDefault();const input=el('chatInput'),value=input.value;input.value='';send(value);});
    el('chatInput').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();el('chatForm').requestSubmit();}});
    document.querySelectorAll('[data-prompt]').forEach(button=>button.addEventListener('click',()=>{openAgent(button.dataset.prompt);send(button.dataset.prompt);}));
    el('uploadForm').addEventListener('submit',upload);el('refreshBatches').addEventListener('click',()=>loadBatches().catch(showFatal));el('trialBalanceButton').addEventListener('click',()=>loadTrialBalance().catch(showFatal));
    el('agentToggle').addEventListener('click',()=>openAgent());el('agentClose').addEventListener('click',closeAgent);el('detailClose').addEventListener('click',closeDetail);el('detailBackdrop').addEventListener('click',closeDetail);
  }
  function showFatal(error){el('chatError').textContent=error.message;el('chatError').hidden=false;setStatus('오류',true);}
  async function init(){await guardLedger();defaultDates();bind();if(!await loadBusinesses())return;await changeBusiness();}
  window.EasyTutorAgent={payloadOf,monthRange,incomeLabel,expenseLabel};
  document.addEventListener('DOMContentLoaded',()=>init().catch(showFatal));
})();