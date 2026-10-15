/**
 * /admin/landing 베타 의견 모더레이션 섹션.
 *
 * 로그인/ADMIN 가드와 헤더 사용자명은 같은 화면의 admin_landing.js가 이미 처리하므로 여기서
 * 중복 체크하지 않는다 (중복 redirect 방지). 권한 이탈은 fetchAuth의 401/403 처리에 맡긴다.
 */
(function () {
  'use strict';

  const API_BASE = '/api/admin/beta-comments';
  // PENDING은 현재 등록 경로에서 생기지 않는다(등록 즉시 노출). 사전 모더레이션으로 되돌릴 때를
  // 대비해 큐와 승인 액션은 남겨둔다 — 그때 이 그룹이 다시 채워진다.
  const GROUPS = [
    { status: 'APPROVED', title: '노출 중', hint: '랜딩에 표시됩니다. 뱃지를 지정하면 카드에 함께 표시됩니다.', key: 'approved' },
    { status: 'HIDDEN', title: '숨김', hint: '랜딩에서 내려간 의견입니다. 다시 노출할 수 있습니다.', key: 'hidden' },
    { status: 'PENDING', title: '승인 대기', hint: '사전 모더레이션을 켰을 때만 쌓입니다. 현재는 비어 있는 게 정상입니다.', key: 'pending' }
  ];

  const groupsHost = document.getElementById('betaCommentGroups');
  const summaryHost = document.getElementById('betaCommentSummary');
  if (!groupsHost) return;

  let view = null;

  load();

  async function load() {
    groupsHost.setAttribute('aria-busy', 'true');
    try {
      const response = await fetchAuth(API_BASE);
      if (!response.ok) throw new Error(await responseMessage(response));
      view = await response.json();
      render();
    } catch (error) {
      groupsHost.innerHTML =
        `<p class="landing-video-load-error" role="alert">${escapeHtml(error.message || '베타 의견을 불러오지 못했습니다.')}</p>`;
    } finally {
      groupsHost.setAttribute('aria-busy', 'false');
    }
  }

  function render() {
    renderSummary();
    groupsHost.innerHTML = GROUPS.map(renderGroup).join('');
    groupsHost.querySelectorAll('[data-status-action]').forEach(button => {
      button.addEventListener('click', updateStatus);
    });
    groupsHost.querySelectorAll('[data-highlight-select]').forEach(select => {
      select.addEventListener('change', updateHighlight);
    });
  }

  /** 0건 / 1~4건 / 5건 이상에서 랜딩 렌더가 달라지므로 현재 구간을 명시한다. */
  function renderSummary() {
    if (!summaryHost) return;
    const count = view.approvedCount;
    const min = view.animationMinCards;
    const max = view.carouselMaxCards;
    let message;
    if (count === 0) {
      message = '노출 중 0건 — 랜딩에는 임시 예시 카드가 표시됩니다. 의견이 1건이라도 등록되면 실제 의견으로 바뀝니다.';
    } else if (count < min) {
      message = `노출 중 ${count}건 — 실제 의견 ${count}장이 자동 스크롤 없이 가운데 정렬로 표시됩니다. ${min}건부터 자동 스크롤이 켜집니다.`;
    } else {
      message = `노출 중 ${count}건 — 최신 ${Math.min(count, max)}장이 자동 스크롤로 표시됩니다. (상한 ${max}장)`;
    }
    summaryHost.textContent = message;
  }

  function renderGroup(group) {
    const items = view[group.key] || [];
    const body = items.length === 0
      ? `<p class="beta-admin-empty">해당 상태의 의견이 없습니다.</p>`
      : `<ul class="beta-admin-list">${items.map(item => renderItem(item, group.status)).join('')}</ul>`;
    return `
      <article class="beta-admin-group" data-status="${group.status}">
        <header class="beta-admin-group-header">
          <h3>${escapeHtml(group.title)} <span class="beta-admin-count">${items.length}</span></h3>
          <p>${escapeHtml(group.hint)}</p>
        </header>
        ${body}
      </article>`;
  }

  function renderItem(item, status) {
    return `
      <li class="beta-admin-item" id="beta-item-${item.id}" aria-busy="false">
        <div class="beta-admin-item-meta">
          <strong>${escapeHtml(item.nickname)}</strong>
          <span>${escapeHtml(item.participantLabel)}</span>
          <time>${escapeHtml(formatDate(item.createdAt))}</time>
        </div>
        <p class="beta-admin-item-comment">${escapeHtml(item.comment)}</p>
        <div class="beta-admin-item-actions">
          ${renderHighlightSelect(item, status)}
          ${renderStatusButtons(item, status)}
        </div>
        <p class="beta-admin-item-message" id="beta-message-${item.id}" role="status" aria-live="polite"></p>
      </li>`;
  }

  /** 뱃지는 노출 중인 의견에만 의미가 있어 APPROVED에서만 노출한다. */
  function renderHighlightSelect(item, status) {
    if (status !== 'APPROVED') return '';
    const options = [{ value: '', label: '뱃지 없음' }].concat(view.highlightOptions);
    const rendered = options.map(option => {
      const selected = (item.highlightLabel || '') === option.value ? ' selected' : '';
      return `<option value="${escapeAttr(option.value)}"${selected}>${escapeHtml(option.label)}</option>`;
    }).join('');
    return `
      <label class="beta-admin-highlight">
        <span>뱃지</span>
        <select data-highlight-select data-id="${item.id}">${rendered}</select>
      </label>`;
  }

  function renderStatusButtons(item, status) {
    const buttons = [];
    if (status !== 'APPROVED') {
      // 숨긴 걸 되돌리는 것과 대기 큐를 통과시키는 것은 같은 전이지만 운영자가 읽는 의미가 다르다.
      const label = status === 'HIDDEN' ? '다시 노출' : '승인';
      buttons.push(`<button type="button" class="beta-admin-approve" data-status-action="APPROVED" data-id="${item.id}">${label}</button>`);
    }
    if (status !== 'HIDDEN') {
      buttons.push(`<button type="button" class="beta-admin-hide" data-status-action="HIDDEN" data-id="${item.id}">숨김</button>`);
    }
    return buttons.join('');
  }

  async function updateStatus(event) {
    const button = event.currentTarget;
    const id = Number(button.dataset.id);
    const status = button.dataset.statusAction;
    await mutate(id, '상태를 저장하는 중입니다.', () => request(`${API_BASE}/${id}/status`, { status: status }));
  }

  async function updateHighlight(event) {
    const select = event.currentTarget;
    const id = Number(select.dataset.id);
    const value = select.value === '' ? null : select.value;
    await mutate(id, '뱃지를 저장하는 중입니다.', () => request(`${API_BASE}/${id}/highlight`, { highlightLabel: value }));
  }

  async function request(url, body) {
    const response = await fetchAuth(url, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!response.ok) throw new Error(await responseMessage(response));
    return response.json();
  }

  /**
   * 상태·뱃지 변경은 그룹 소속과 승인 건수를 함께 바꾸므로 부분 갱신하지 않고 전체를 다시 읽는다.
   * 목록이 수십 건 규모라 재조회 비용보다 상태 동기화 오류를 막는 편이 낫다.
   */
  async function mutate(id, busyMessage, run) {
    setBusy(id, true);
    setMessage(id, busyMessage, false);
    try {
      await run();
      await load();
    } catch (error) {
      setBusy(id, false);
      setMessage(id, error.message || '변경 사항을 저장하지 못했습니다.', true);
    }
  }

  function setBusy(id, busy) {
    const item = document.getElementById(`beta-item-${id}`);
    if (!item) return;
    item.setAttribute('aria-busy', String(busy));
    item.querySelectorAll('button, select').forEach(control => {
      control.disabled = busy;
    });
  }

  function setMessage(id, message, error) {
    const output = document.getElementById(`beta-message-${id}`);
    if (!output) return;
    output.textContent = message;
    output.classList.toggle('is-error', Boolean(error));
    output.setAttribute('role', error ? 'alert' : 'status');
  }

  function formatDate(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const pad = number => String(number).padStart(2, '0');
    return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  /** 서버 검증 실패는 GlobalExceptionHandler가 평문으로 내리므로 JSON 파싱에 의존하지 않는다. */
  async function responseMessage(response) {
    try {
      const text = (await response.text()).trim();
      if (!text) return '요청을 처리하지 못했습니다.';
      try {
        const payload = JSON.parse(text);
        return payload.message || payload.error || text;
      } catch (_) {
        return text;
      }
    } catch (_) {
      return '요청을 처리하지 못했습니다.';
    }
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>'"]/g, character => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[character]));
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }
})();
