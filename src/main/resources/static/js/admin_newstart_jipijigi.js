// admin_newstart_jipijigi.js — 지피지기 Agent V1 운영 콘솔
const API = '/api/admin/newstart/jipijigi';
const CLUB_ID = 31312978; // 새출발기금 부실차주 라이브러리 (2026-09-23 실측)
const AGENT_BOARD = '지피지기 agent';

let currentUser = null;

(async () => {
  currentUser = await ensureCurrentUser();
  if (!currentUser || currentUser.role !== 'ADMIN') { location.href = '/wiki'; return; }
  const nameEl = document.getElementById('userName');
  if (nameEl) nameEl.textContent = window.userDisplayName(currentUser);
  document.querySelectorAll('[data-cmd]').forEach(btn => {
    btn.addEventListener('click', () => runCommand(btn.dataset.cmd, presetInstruction(btn.dataset.cmd)));
  });
  document.getElementById('runCustom').addEventListener('click', () => {
    const text = document.getElementById('customCmd').value.trim();
    if (!text) return showMessage('직접 명령 문장을 입력하세요.');
    runCommand('CUSTOM', text);
  });
  document.getElementById('refreshDrafts').addEventListener('click', loadDrafts);
  document.getElementById('statusFilter').addEventListener('change', loadDrafts);
  document.getElementById('saveAgentBoard').addEventListener('click', saveAgentBoard);
  await Promise.all([loadInventory(), loadQuestions(), loadCommands(), loadDrafts(), loadBoards()]);
})();

function presetInstruction(type) {
  if (type === 'RECENT_QUESTIONS') return '최근 질문 5개 조사';
  if (type === 'CCRS_RESEARCH') return '신복위 카페 사례 조사';
  if (type === 'INTRO_POST') return 'Agent 소개글 작성';
  return '';
}

function showMessage(text, kind) {
  const box = document.getElementById('cmdMessage');
  box.style.display = 'block';
  box.textContent = text;
  box.style.background = kind === 'ok' ? '#ecfdf5' : '#fff7ed';
  box.style.color = kind === 'ok' ? '#065f46' : '#9a3412';
}

async function runCommand(type, instruction) {
  showMessage('실행 중… ' + instruction);
  try {
    const res = await fetchJsonAuth(API + '/commands', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ instruction, commandType: type })
    });
    const drafts = res.drafts || [];
    showMessage(`명령 ${res.command.commandId} ${res.command.status} — 초안 ${drafts.length}건. ${res.command.resultSummary || ''} (게시 미수행)`, 'ok');
    await Promise.all([loadCommands(), loadDrafts()]);
  } catch (e) {
    showMessage('명령 실패: ' + e.message);
  }
}

async function loadInventory() {
  try {
    const inv = await fetchJsonAuth(API + '/inventory');
    const cards = [
      ['내부 지식(ACTIVE)', inv.knowledgeByStatus?.ACTIVE ?? 0],
      ['내부 지식 전체', inv.knowledgeTotal],
      ['실제 사례(최종 감면율)', inv.empiricalCaseWithFinalRate + ' / ' + inv.empiricalCaseTotal],
      ['공식 자료(게시됨)', inv.officialUpdatePublished + ' / ' + inv.officialUpdateTotal],
      ['수집 질문', inv.questionTotal],
      ['초안', inv.draftTotal]
    ];
    document.getElementById('invCards').innerHTML = cards.map(([label, v]) =>
      `<div class="jp-card"><b>${v}</b><span>${label}</span></div>`).join('');
    document.getElementById('invGaps').innerHTML = (inv.gaps || []).map(g =>
      `<li class="jp-item">${g}</li>`).join('');
  } catch (e) {
    document.getElementById('invGaps').innerHTML = `<li class="jp-item">inventory 실패: ${e.message}</li>`;
  }
}

async function loadQuestions() {
  try {
    const list = await fetchJsonAuth(API + '/questions?limit=5');
    document.getElementById('questionList').innerHTML = list.length
      ? list.map(q => `<li class="jp-item">
          <header><a href="${q.url}" target="_blank" rel="noopener">글 ${q.articleId} — ${q.title}</a>
          <span class="jp-tag ${q.bodyStatus === 'UNREADABLE' ? 'rejected' : 'approved'}">${q.bodyStatus}</span></header>
          <small>${q.writtenAt || '작성일 미확인'} · 댓글 ${q.commentCount ?? 0} · 수집 ${q.collectedAt || '-'}</small>
        </li>`).join('')
      : '<li class="jp-item">수집된 질문 0건 — 질문게시판에서 실제 수집 후 실행하세요.</li>';
  } catch (e) {
    document.getElementById('questionList').innerHTML = `<li class="jp-item">호출 실패: ${e.message}</li>`;
  }
}

async function loadCommands() {
  try {
    const page = await fetchJsonAuth(API + '/commands?page=0&size=10');
    const items = page.content || [];
    document.getElementById('commandList').innerHTML = items.length
      ? items.map(c => `<li class="jp-item">
          <header><span>#${c.commandId} ${c.commandType} <span class="jp-tag ${c.status === 'DONE' ? 'approved' : 'rejected'}">${c.status}</span></span>
          <small>${c.createdAt || ''}</small></header>
          <div>${c.instruction}</div>
          <small>${c.resultSummary || c.errorMessage || ''}</small>
        </li>`).join('')
      : '<li class="jp-item">아직 실행된 명령이 없습니다.</li>';
  } catch (e) {
    document.getElementById('commandList').innerHTML = `<li class="jp-item">호출 실패: ${e.message}</li>`;
  }
}

async function loadBoards() {
  try {
    const boards = await fetchJsonAuth(API + '/boards');
    document.getElementById('boardList').innerHTML = boards.length
      ? boards.map(b => `<li class="jp-item">
          <header><span>clubid ${b.clubId} / menuid ${b.menuId ?? '미확인'} — ${b.boardName}
            <span class="jp-tag ${b.postable ? 'approved' : 'draft'}">${b.postable ? '게시 가능' : 'menuid 확인 필요'}</span>
            <span class="jp-tag ${b.enabled ? 'ready' : 'rejected'}">${b.enabled ? 'enabled' : 'disabled'}</span></span>
            <button class="btn btn-secondary" type="button" onclick="toggleBoard(${b.boardRowId}, ${!b.enabled})">${b.enabled ? '비활성' : '활성'}</button>
          </header>
          <small>${b.cafeName || ''} ${b.memo || ''}</small>
        </li>`).join('')
      : '<li class="jp-item">allowlist가 비어 있습니다 — 게시 전에 "지피지기 agent" 게시판 menuid를 NAVER에서 확인해 등록하세요.</li>';
  } catch (e) {
    document.getElementById('boardList').innerHTML = `<li class="jp-item">호출 실패: ${e.message}</li>`;
  }
}

async function saveAgentBoard() {
  const raw = document.getElementById('boardMenu').value.trim();
  const body = {
    clubId: CLUB_ID,
    menuId: raw === '' ? null : Number(raw),
    boardName: AGENT_BOARD,
    cafeName: '새출발기금 부실차주 라이브러리',
    enabled: true,
    memo: raw === '' ? 'menuid 미확인 — NAVER 확인 전까지 게시 대상 사용 불가' : '운영자가 NAVER에서 확인한 menuid'
  };
  try {
    await fetchJsonAuth(API + '/boards', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
    showMessage('allowlist 저장됨', 'ok');
    await loadBoards();
  } catch (e) {
    showMessage('저장 실패: ' + e.message);
  }
}

async function toggleBoard(id, enabled) {
  try {
    await fetchJsonAuth(`${API}/boards/${id}/enabled?enabled=${enabled}`, { method: 'PUT' });
    await loadBoards();
  } catch (e) {
    showMessage('실패: ' + e.message);
  }
}

async function loadDrafts() {
  const status = document.getElementById('statusFilter').value;
  try {
    const list = await fetchJsonAuth(API + '/drafts' + (status ? '?status=' + status : ''));
    document.getElementById('draftList').innerHTML = list.length
      ? list.map(d => `<li class="jp-item">
          <header><span>#${d.draftId} ${d.title}</span>
            <span class="jp-tag ${tagClass(d.status)}">${d.status}</span></header>
          <small>${d.draftType} · 대상 ${d.targetBoardName || '미지정'} · ${d.updatedAt || ''}</small>
          <div class="jp-btns" style="margin-top:6px;">
            <button class="btn btn-secondary" type="button" onclick="openDraft(${d.draftId})">열기</button>
            <button class="btn btn-primary" type="button" onclick="act(${d.draftId},'review')">검토 요청</button>
            <button class="btn btn-primary" type="button" onclick="act(${d.draftId},'approve')">승인</button>
            <button class="btn btn-secondary" type="button" onclick="act(${d.draftId},'publish')">게시</button>
            <button class="btn btn-secondary" type="button" onclick="act(${d.draftId},'hold')">보류</button>
            <button class="btn btn-secondary" type="button" onclick="act(${d.draftId},'reject')">반려</button>
          </div>
        </li>`).join('')
      : '<li class="jp-item">초안이 없습니다. 위에서 명령을 실행하세요.</li>';
  } catch (e) {
    document.getElementById('draftList').innerHTML = `<li class="jp-item">호출 실패: ${e.message}</li>`;
  }
}

function tagClass(status) {
  if (status === 'DRAFT') return 'draft';
  if (status === 'READY_FOR_REVIEW') return 'ready';
  if (status === 'APPROVED') return 'approved';
  if (status === 'REJECTED' || status === 'PUBLISHED') return 'rejected';
  return '';
}

async function act(id, action) {
  let body = {};
  if (action === 'reject' || action === 'hold') {
    const reason = prompt(action === 'reject' ? '반려 사유' : '보류 사유(추가 지시)');
    if (!reason) return;
    body.reason = reason;
  }
  if (action === 'approve') {
    const boardId = prompt('승인 대상 게시판 board_row_id (allowlist의 enabled 행)', '');
    if (!boardId) return;
    body.targetBoardId = Number(boardId);
  }
  try {
    const opts = action === 'review' || action === 'publish'
      ? { method: 'POST' }
      : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
    await fetchJsonAuth(`${API}/drafts/${id}/${action}`, opts);
    showMessage(`초안 #${id} ${action} 처리 완료`, 'ok');
    await loadDrafts();
    if (document.getElementById('draftDetail').dataset.id === String(id)) await openDraft(id);
  } catch (e) {
    showMessage(`${action} 거부됨: ${e.message}`);
  }
}

async function openDraft(id) {
  try {
    const d = await fetchJsonAuth(API + '/drafts/' + id);
    const detail = document.getElementById('draftDetail');
    detail.dataset.id = String(id);
    detail.style.display = 'block';
    const draft = d.draft;
    detail.innerHTML = `
      <h3>초안 #${draft.draftId} — ${draft.title}</h3>
      <p class="jp-hint">상태 ${draft.status} · 대상 게시판 ${draft.targetBoardName || '미지정'} · 게시 URL ${draft.publishedUrl || '없음(V1)'}
        · 근거 건수 ${Object.entries(d.hitCounts || {}).map(([k, v]) => `${k}=${v}`).join(', ') || '없음'}</p>
      <div class="jp-btns" style="margin-bottom:10px;">
        <button class="btn btn-secondary" type="button" onclick="copyDraft(${draft.draftId})">원문 복사(운영자 게시용)</button>
      </div>
      <input id="editTitle" class="jp-input" value="${escapeAttr(draft.title)}">
      <textarea id="editBody" class="jp-edit">${escapeHtml(draft.body)}</textarea>
      <div class="jp-btns" style="margin:8px 0;">
        <button class="btn btn-primary" type="button" onclick="saveDraft(${draft.draftId})">수정 저장</button>
      </div>
      <h4>조사 추적 (STEP 1→5)</h4>
      <div class="jp-traces"><table>
        <thead><tr><th>STEP</th><th>근거 분류</th><th>참조</th><th>발췌</th><th>비고</th></tr></thead>
        <tbody>${(d.traces || []).map(t => `<tr>
            <td>${t.stepNo}</td><td>${t.sourceClass}</td>
            <td>${t.refUrl ? `<a href="${t.refUrl}" target="_blank" rel="noopener">${t.refType} ${t.refId || ''}</a>` : (t.refType + ' ' + (t.refId || ''))}</td>
            <td>${escapeHtml(t.excerpt || '')}</td><td>${escapeHtml(t.note || '')}</td></tr>`).join('')}</tbody>
      </table></div>
      <h4>상태 이력</h4>
      <ul class="jp-list">${(d.statusLogs || []).map(l =>
        `<li class="jp-item">${l.fromStatus || '-'} → ${l.toStatus} · ${l.actor || '-'} · ${l.reason || ''} · ${l.createdAt || ''}</li>`).join('')}</ul>`;
    detail.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (e) {
    showMessage('초안 조회 실패: ' + e.message);
  }
}

async function saveDraft(id) {
  const title = document.getElementById('editTitle').value;
  const body = document.getElementById('editBody').value;
  try {
    await fetchJsonAuth(API + '/drafts/' + id, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title, body })
    });
    showMessage('저장됨', 'ok');
    await openDraft(id);
    await loadDrafts();
  } catch (e) {
    showMessage('저장 실패: ' + e.message);
  }
}

async function copyDraft(id) {
  const d = await fetchJsonAuth(API + '/drafts/' + id);
  await navigator.clipboard.writeText(d.draft.title + '\n\n' + d.draft.body);
  showMessage('클립보드에 복사 — 게시판에 붙여넣는 주체는 운영자입니다.', 'ok');
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/"/g, '&quot;');
}

function escapeAttr(s) {
  return String(s ?? '').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}
