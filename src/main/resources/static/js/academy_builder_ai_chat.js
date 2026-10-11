/**
 * BuildIt P1 — Academy Builder AI 채팅 패널 (Qoder 담당).
 *
 * 계약: docs/BUILDIT_P1_API_CONTRACT_AI_CHAT.md (Muse 확정본).
 *   POST /api/tutor/academy/ai/chat                 → { reply, proposal|null, sessionId, needsApproval }
 *   POST /api/tutor/academy/ai/chat/apply            → { versionId, appliedAt }
 *   GET  /api/tutor/academy/ai/chat/versions         → [{ id, versionNo, createdBy, createdAt, note }]
 *   POST /api/tutor/academy/ai/chat/versions/{id}/restore → { versionId, appliedAt }
 * academyId는 보내지 않는다 — 서버가 인증된 principal로 결정한다.
 *
 * ⚠ 미리보기·발행·뷰 토글(PC/태블릿/모바일 390px)은 기존 구현을 그대로 쓴다
 *   (academy_builder.js + academy_builder.html 의 setView). 이 파일은 재정의하지 않는다.
 * ⚠ academy_builder.js 는 IIFE 라 내부를 호출할 수 없다. 적용/복원은 서버가 DB를 바꾸므로
 *   성공 후 새로고침으로 SSR 모델을 다시 읽는다(로컬 currentPreview 를 부분 갱신하면
 *   서버 상태와 갈라진다).
 * ⚠ XSS: 서버가 내려준 문자열은 innerHTML 에 넣지 않는다 — textContent 만 사용한다
 *   (newstart-chat.js 의 기존 규칙과 동일).
 */
(function () {
  'use strict';

  var API = {
    chat: '/api/tutor/academy/ai/chat',
    apply: '/api/tutor/academy/ai/chat/apply',
    versions: '/api/tutor/academy/ai/chat/versions'
  };

  /**
   * 표시용 라벨 — 2026-10-11 계약 v2(allowlist 정합본) 기준.
   *   intro            = Academy 최상위 필드(엔티티 intro 컬럼). builderDraft 아님.
   *   principalCareer  = 단일 키(약력은 개행 결합 문자열, academy_builder.js:2383).
   *   heroAccent / brandPrimary / brandAccent = allowlist 에서 제거됨 → 제안에 안 나온다.
   * 한글명은 기존 렌더러 문구를 그대로 쓴다(HERO_STATS, academy_sections.js:745-747).
   * 여기에 없는 키는 라벨을 지어내지 않고 키명을 그대로 노출한다.
   */
  var FIELD_LABELS = {
    intro: '학원 소개',
    monthlyTuition: '월 수강료',
    scoreImprovement: '성적 향상률',
    operationYears: '운영 경력',
    principalDesc: '원장 소개',
    principalQuote: '원장 한마디',
    principalCareer: '원장 약력',
    philosophyItem1: '교육철학 1',
    philosophyItem2: '교육철학 2',
    philosophyItem3: '교육철학 3',
    philosophyItem4: '교육철학 4',
    ctaPhone: '상담 전화',
    ctaKakaoUrl: '카카오톡 상담 링크'
  };

  var state = {
    sessionId: null,
    busy: false,
    pending: null,
    restored: false
  };

  var el = {};

  function init() {
    el.panel = document.getElementById('builderAiChatPanel');
    el.launcher = document.getElementById('builderAiChatLauncher');
    if (!el.panel) return;

    el.close = document.getElementById('builderAiChatClose');
    el.newSession = document.getElementById('builderAiChatNew');
    el.tabChat = document.getElementById('builderAiChatTabChat');
    el.tabVersions = document.getElementById('builderAiChatTabVersions');
    el.chatView = document.getElementById('builderAiChatView');
    el.versionsView = document.getElementById('builderAiChatVersionsView');
    el.list = document.getElementById('builderAiChatList');
    el.empty = document.getElementById('builderAiChatEmpty');
    el.form = document.getElementById('builderAiChatForm');
    el.input = document.getElementById('builderAiChatInput');
    el.send = document.getElementById('builderAiChatSend');
    el.status = document.getElementById('builderAiChatStatus');
    el.versionList = document.getElementById('builderAiChatVersionList');
    el.versionEmpty = document.getElementById('builderAiChatVersionEmpty');
    el.versionStatus = document.getElementById('builderAiChatVersionStatus');

    if (el.launcher) {
      el.launcher.hidden = false;
      el.launcher.addEventListener('click', function () { openPanel(); });
    }
    if (el.close) el.close.addEventListener('click', closePanel);
    if (el.newSession) el.newSession.addEventListener('click', startNewSession);
    if (el.tabChat) el.tabChat.addEventListener('click', function () { switchTab('chat'); });
    if (el.tabVersions) el.tabVersions.addEventListener('click', function () { switchTab('versions'); });

    if (el.form) {
      el.form.addEventListener('submit', function (event) {
        event.preventDefault();
        sendMessage();
      });
    }
    if (el.input) {
      el.input.addEventListener('input', autoGrow);
      // Enter 전송 / Shift+Enter 줄바꿈 — newstart-chat.js 와 동일 규칙.
      el.input.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && !event.shiftKey) {
          event.preventDefault();
          sendMessage();
        }
      });
    }
  }

  function openPanel() {
    el.panel.classList.add('is-open');
    el.panel.setAttribute('aria-hidden', 'false');
    document.body.classList.add('aichat-open');
    if (el.launcher) el.launcher.setAttribute('aria-expanded', 'true');
    if (el.input) el.input.focus();
  }

  function closePanel() {
    el.panel.classList.remove('is-open');
    el.panel.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('aichat-open');
    if (el.launcher) el.launcher.setAttribute('aria-expanded', 'false');
  }

  function switchTab(which) {
    var versions = which === 'versions';
    if (el.tabChat) el.tabChat.classList.toggle('is-active', !versions);
    if (el.tabVersions) el.tabVersions.classList.toggle('is-active', versions);
    if (el.chatView) el.chatView.hidden = versions;
    if (el.versionsView) el.versionsView.hidden = !versions;
    if (versions) loadVersions();
  }

  function autoGrow() {
    if (!el.input) return;
    el.input.style.height = 'auto';
    el.input.style.height = Math.min(el.input.scrollHeight, 96) + 'px';
  }

  function setStatus(text) {
    if (el.status) el.status.textContent = text || '';
  }

  function setVersionStatus(text) {
    if (el.versionStatus) el.versionStatus.textContent = text || '';
  }

  function scrollListToBottom() {
    if (!el.list) return;
    el.list.scrollTop = el.list.scrollHeight;
  }

  function startNewSession() {
    state.sessionId = null;
    state.pending = null;
    state.restored = false;
    if (el.list) el.list.innerHTML = '';
    if (el.empty) el.empty.hidden = false;
    setStatus('');
    if (el.input) el.input.focus();
  }

  /* ── 채팅 ─────────────────────────────────────────────────────────── */

  function sendMessage() {
    if (!el.input || state.busy) return;
    var message = String(el.input.value || '').trim();
    if (!message) return;

    state.busy = true;
    if (el.send) el.send.disabled = true;
    appendMessage('mine', message);
    el.input.value = '';
    autoGrow();
    setStatus('AI가 학원 정보를 확인하고 있습니다…');

    postJson(API.chat, { message: message, sessionId: state.sessionId })
      .then(function (data) {
        if (data && data.sessionId != null) state.sessionId = String(data.sessionId);
        setStatus('');
        var proposal = data && data.proposal ? data.proposal : null;
        appendMessage('ai', (data && data.reply) || '', proposal);
        if (proposal && data && data.needsApproval) {
          state.pending = { sessionId: state.sessionId, changes: normalizeChanges(proposal.changes) };
        }
      })
      .catch(function (error) {
        setStatus('');
        appendError(error);
      })
      .then(function () {
        state.busy = false;
        if (el.send) el.send.disabled = false;
        if (el.input) el.input.focus();
      });
  }

  function normalizeChanges(changes) {
    if (!Array.isArray(changes)) return [];
    return changes.filter(function (change) {
      return change && typeof change.key === 'string' && change.key.length > 0;
    });
  }

  function appendMessage(kind, text, proposal) {
    if (!el.list) return;
    if (el.empty) el.empty.hidden = true;

    var item = document.createElement('div');
    item.className = 'aichat-msg aichat-msg-' + kind;

    var body = document.createElement('div');
    body.className = 'aichat-msg-body';
    body.textContent = text || '';
    item.appendChild(body);

    if (proposal) item.appendChild(renderProposal(proposal));

    el.list.appendChild(item);
    scrollListToBottom();
  }

  function appendError(error) {
    if (!el.list) return;
    if (el.empty) el.empty.hidden = true;
    var item = document.createElement('div');
    item.className = 'aichat-msg aichat-msg-error';
    var body = document.createElement('div');
    body.className = 'aichat-msg-body';
    body.textContent = (error && error.message) || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    item.appendChild(body);
    el.list.appendChild(item);
    scrollListToBottom();
  }

  /**
   * 제안 카드. changes[] = { key, before, after } (계약 §1).
   * before/after 를 jsdiff 로 단어 단위 비교해 무엇이 바뀌는지 보여준다.
   */
  function renderProposal(proposal) {
    var wrap = document.createElement('div');
    wrap.className = 'aichat-proposal';

    if (proposal.summary) {
      var summary = document.createElement('p');
      summary.className = 'aichat-proposal-summary';
      summary.textContent = proposal.summary;
      wrap.appendChild(summary);
    }

    var changes = normalizeChanges(proposal.changes);
    changes.forEach(function (change) {
      wrap.appendChild(renderChangeRow(change));
    });

    if (!changes.length) {
      var none = document.createElement('p');
      none.className = 'aichat-proposal-empty';
      none.textContent = '제안된 변경 항목이 없습니다.';
      wrap.appendChild(none);
      return wrap;
    }

    var actions = document.createElement('div');
    actions.className = 'aichat-proposal-actions';

    var apply = document.createElement('button');
    apply.type = 'button';
    apply.className = 'aichat-btn aichat-btn-primary';
    apply.textContent = '적용하기';
    apply.addEventListener('click', function () { applyChanges(apply, changes); });

    var dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'aichat-btn aichat-btn-ghost';
    dismiss.textContent = '적용 안 함';
    dismiss.addEventListener('click', function () {
      wrap.classList.add('is-dismissed');
      state.pending = null;
      setStatus('적용하지 않았습니다. 학원 정보는 바뀌지 않았습니다.');
    });

    actions.appendChild(apply);
    actions.appendChild(dismiss);
    wrap.appendChild(actions);
    return wrap;
  }

  function renderChangeRow(change) {
    var row = document.createElement('div');
    row.className = 'aichat-change';

    var head = document.createElement('div');
    head.className = 'aichat-change-head';
    var label = document.createElement('span');
    label.className = 'aichat-change-label';
    label.textContent = FIELD_LABELS[change.key] || change.key;
    head.appendChild(label);
    if (!FIELD_LABELS[change.key]) {
      var key = document.createElement('code');
      key.className = 'aichat-change-key';
      key.textContent = change.key;
      head.appendChild(key);
    }
    row.appendChild(head);

    var before = change.before == null ? '' : String(change.before);
    var after = change.after == null ? '' : String(change.after);
    row.appendChild(renderDiff(before, after));
    return row;
  }

  /**
   * 전후 비교. jsdiff(vendored, BSD-3-Clause)의 Diff.diffWords 를 쓰고,
   * 로드되지 않았으면 before/after 를 나란히 보여주는 것으로 대체한다(기능 중단 방지).
   */
  function renderDiff(before, after) {
    var box = document.createElement('div');
    box.className = 'aichat-diff';

    var diffLib = window.Diff;
    var parts = null;
    if (diffLib && typeof diffLib.diffWords === 'function') {
      try {
        parts = diffLib.diffWords(before, after);
      } catch (_) {
        parts = null;
      }
    }

    if (!parts) {
      var plainBefore = document.createElement('p');
      plainBefore.className = 'aichat-diff-line';
      plainBefore.appendChild(makeTag('현재'));
      plainBefore.appendChild(document.createTextNode(before === '' ? '(비어 있음)' : before));
      var plainAfter = document.createElement('p');
      plainAfter.className = 'aichat-diff-line';
      plainAfter.appendChild(makeTag('변경'));
      plainAfter.appendChild(document.createTextNode(after === '' ? '(비어 있음)' : after));
      box.appendChild(plainBefore);
      box.appendChild(plainAfter);
      return box;
    }

    var beforeLine = document.createElement('p');
    beforeLine.className = 'aichat-diff-line';
    beforeLine.appendChild(makeTag('현재'));
    var afterLine = document.createElement('p');
    afterLine.className = 'aichat-diff-line';
    afterLine.appendChild(makeTag('변경'));

    parts.forEach(function (part) {
      var text = part.value == null ? '' : String(part.value);
      if (!text) return;
      if (!part.removed) {
        afterLine.appendChild(document.createTextNode(text));
      }
      if (!part.added) {
        beforeLine.appendChild(document.createTextNode(text));
      }
      if (part.removed) {
        beforeLine.appendChild(makeMark(text, 'del'));
      }
      if (part.added) {
        afterLine.appendChild(makeMark(text, 'ins'));
      }
    });

    box.appendChild(beforeLine);
    box.appendChild(afterLine);
    return box;
  }

  function makeTag(text) {
    var tag = document.createElement('span');
    tag.className = 'aichat-diff-tag';
    tag.textContent = text;
    return tag;
  }

  function makeMark(text, kind) {
    var mark = document.createElement(kind === 'del' ? 'del' : 'ins');
    mark.className = 'aichat-diff-' + kind;
    mark.textContent = text;
    return mark;
  }

  function applyChanges(button, changes) {
    if (state.busy) return;
    var payload = {
      sessionId: state.sessionId,
      confirmedChanges: changes.map(function (change) {
        return { key: change.key, before: change.before, after: change.after };
      })
    };

    state.busy = true;
    if (button) button.disabled = true;
    setStatus('적용 중입니다…');

    postJson(API.apply, payload)
      .then(function (data) {
        setStatus('');
        state.pending = null;
        var versionId = data && data.versionId != null ? data.versionId : null;
        toast('적용했습니다.' + (versionId == null ? '' : ' (버전 ' + versionId + ' 저장)')
          + ' 미리보기를 다시 불러옵니다.', 'fa-circle-check');
        // 서버가 DB 를 바꿨으므로 SSR 모델을 다시 읽는다. 이 패널은 academy_builder.js 의
        // currentPreview 를 갱신할 방법이 없다(IIFE). reload 훅이 생기면 그쪽으로 교체 예정.
        window.setTimeout(function () { window.location.reload(); }, 900);
      })
      .catch(function (error) {
        setStatus('');
        if (button) button.disabled = false;
        appendError(error);
      })
      .then(function () {
        state.busy = false;
      });
  }

  /* ── 이력 / 복원 ──────────────────────────────────────────────────── */

  function loadVersions() {
    if (!el.versionList) return;
    setVersionStatus('불러오는 중…');
    el.versionList.innerHTML = '';
    if (el.versionEmpty) el.versionEmpty.hidden = true;

    fetch(API.versions, { credentials: 'same-origin' })
      .then(function (res) { return toData(res); })
      .then(function (data) {
        setVersionStatus('');
        var versions = Array.isArray(data) ? data : [];
        if (!versions.length) {
          if (el.versionEmpty) el.versionEmpty.hidden = false;
          return;
        }
        versions.forEach(function (version) {
          el.versionList.appendChild(renderVersionRow(version));
        });
      })
      .catch(function (error) {
        setVersionStatus((error && error.message) || '버전 목록을 불러오지 못했습니다.');
      });
  }

  function renderVersionRow(version) {
    var row = document.createElement('div');
    row.className = 'aichat-version';

    var info = document.createElement('div');
    info.className = 'aichat-version-info';

    var title = document.createElement('strong');
    title.className = 'aichat-version-no';
    title.textContent = '버전 ' + (version && version.versionNo != null ? version.versionNo : '-');
    info.appendChild(title);

    var meta = document.createElement('div');
    meta.className = 'aichat-version-meta';
    meta.textContent = [
      version && version.note ? version.note : '',
      version && version.createdBy ? version.createdBy : '',
      formatDateTime(version && version.createdAt)
    ].filter(function (part) { return !!part; }).join(' · ');
    info.appendChild(meta);

    var restore = document.createElement('button');
    restore.type = 'button';
    restore.className = 'aichat-btn aichat-btn-ghost';
    restore.textContent = '복원';
    restore.addEventListener('click', function () { restoreVersion(version, restore); });

    row.appendChild(info);
    row.appendChild(restore);
    return row;
  }

  function restoreVersion(version, button) {
    if (state.busy) return;
    if (version == null || version.id == null) return;
    if (!window.confirm('버전 ' + (version.versionNo != null ? version.versionNo : version.id)
      + ' 상태로 되돌립니다. 현재 상태도 새 버전으로 저장됩니다. 계속할까요?')) {
      return;
    }

    state.busy = true;
    if (button) button.disabled = true;
    setVersionStatus('복원 중입니다…');

    postJson(API.versions + '/' + encodeURIComponent(version.id) + '/restore', {})
      .then(function (data) {
        setVersionStatus('');
        var versionId = data && data.versionId != null ? data.versionId : null;
        toast('복원했습니다.' + (versionId == null ? '' : ' (복원 전 상태가 버전 ' + versionId + '로 저장)')
          + ' 미리보기를 다시 불러옵니다.', 'fa-clock-rotate-left');
        window.setTimeout(function () { window.location.reload(); }, 900);
      })
      .catch(function (error) {
        setVersionStatus('');
        if (button) button.disabled = false;
        setVersionStatus((error && error.message) || '복원하지 못했습니다.');
      })
      .then(function () {
        state.busy = false;
      });
  }

  /* ── 공통 ─────────────────────────────────────────────────────────── */

  function postJson(url, body) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body || {})
    }).then(function (res) { return toData(res); });
  }

  /**
   * 계약 §오류 코드: 400(Academy 없음 / 세션 없음 / 허용 외 필드 / 위험 콘텐츠), 403(타 학원 세션).
   * 서버 메시지를 그대로 보여준다(메시지가 사용자 안내 문구로 설계돼 있음).
   */
  function toData(res) {
    return res.json().catch(function () { return null; }).then(function (data) {
      if (res.ok) return data;
      var message = data && (data.message || data.errorMessage) ? (data.message || data.errorMessage) : null;
      if (!message) {
        if (res.status === 403) message = '이 학원의 정보를 수정할 권한이 없습니다.';
        else if (res.status === 401) message = '로그인이 필요합니다. 새로고침 후 다시 시도해 주세요.';
        else message = '요청을 처리하지 못했습니다. (HTTP ' + res.status + ')';
      }
      throw new Error(message);
    });
  }

  function formatDateTime(iso) {
    if (!iso) return '';
    var date = new Date(iso);
    if (isNaN(date.getTime())) return String(iso);
    var pad = function (value) { return String(value).padStart(2, '0'); };
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
      + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  /** academy_builder.html 의 전역 showToast 를 쓴다. 없으면 상태줄로 대체한다. */
  function toast(message, icon) {
    if (typeof window.showToast === 'function') {
      window.showToast(message, icon || 'fa-circle-check');
      return;
    }
    setStatus(message);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
