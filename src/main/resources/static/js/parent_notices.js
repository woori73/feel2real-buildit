(function () {
  'use strict';

  const api = '/api/dashboard/parent-notices';
  const homepage = 'https://feel2real.co.kr/';
  const sdkUrl = 'https://t1.kakaocdn.net/kakao_js_sdk/2.8.5/kakao.min.js';
  const sdkIntegrity = 'sha384-v6JKY3z4FUkWq21oZDHZe74YvuPk/WUrVLrDHTBojHXClOSa3iXy7sCXPY1jkFbj';
  const $ = id => document.getElementById(id);
  const dateFormat = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', dateStyle: 'short', timeStyle: 'short'
  });
  let shareState = 'unconfigured';
  let sdkLoaded = false;
  let savedRequest = null;
  let saving = false;
  let focusDraftId = null;
  let pendingFocus = null;

  function report(text) { $('pnStatus').textContent = text; }

  function currentMessage() {
    const body = $('pnBody').value.trim();
    if ($('pnKind').value === 'PROMOTION' && !body.startsWith('(광고)')) return '(광고) ' + body;
    return body;
  }

  function readyToShare() {
    return Boolean($('pnBody').value.trim()) && currentMessage().length <= 200
      && ($('pnKind').value !== 'PROMOTION' || $('pnConsent').checked);
  }

  function renderPreview() {
    const body = $('pnBody').value.trim();
    const message = body ? currentMessage() : '';
    $('pnLength').textContent = message.length + ' / 200';
    $('pnPreview').textContent = message || '작성한 안내문이 여기에 나타납니다.';
    $('pnPromoConfirm').hidden = $('pnKind').value !== 'PROMOTION';
    $('pnCopyBody').disabled = !readyToShare();
    $('pnKakao').disabled = !readyToShare() || shareState !== 'ready';
    $('pnNative').disabled = !readyToShare();
    $('pnSave').disabled = saving || message.length > 200;
  }

  function setupKakao(key) {
    if (sdkLoaded || !key) return;
    sdkLoaded = true;
    shareState = 'loading';
    const script = document.createElement('script');
    script.src = sdkUrl;
    script.integrity = sdkIntegrity;
    script.crossOrigin = 'anonymous';
    const unavailable = () => {
      shareState = 'failed';
      $('pnShareStatus').textContent = '카카오톡 수신자 선택 화면을 연결하지 못했습니다. 휴대전화 공유 또는 문구 복사를 이용하세요.';
      renderPreview();
    };
    script.onerror = unavailable;
    script.onload = () => {
      try {
        window.Kakao.init(key);
        shareState = 'ready';
        $('pnKakao').hidden = false;
        $('pnShareStatus').textContent = '';
        renderPreview();
      } catch (_) {
        unavailable();
      }
    };
    document.head.appendChild(script);
  }

  async function getDrafts() {
    const response = await fetchAuth(api, { cache: 'no-store' });
    if (!response.ok) {
      throw new Error(response.status === 403 ? '학원 원장만 이용할 수 있습니다.' : '초안을 불러오지 못했습니다.');
    }
    const data = await response.json();
    renderDrafts(data.drafts);
    setupKakao(data.javascriptKey);
    if (shareState === 'unconfigured') {
      $('pnShareStatus').textContent = '카카오톡 직접 연결 설정이 필요합니다. 휴대전화 공유 또는 문구 복사를 이용하세요.';
    }
    $('pnWorkspace').hidden = false;
    report('');
    // AI(MCP)가 알려 준 링크(?draft=…)로 들어오면 그 초안을 바로 편집·공유 칸에 띄운다. 보내기는 사람이 공유 버튼을 눌러야 한다.
    if (pendingFocus) {
      const load = pendingFocus;
      pendingFocus = null;
      focusDraftId = null;
      load();
      report('AI가 만든 초안을 불러왔습니다. 문구를 확인하고 공유 버튼을 눌러 보낼 학부모를 고르세요.');
    }
  }

  function renderDrafts(drafts) {
    const rows = $('pnDrafts');
    rows.replaceChildren();
    if (!drafts.length) {
      const empty = document.createElement('li');
      empty.textContent = '저장된 초안이 없습니다.';
      rows.append(empty);
      return;
    }
    for (const draft of drafts) {
      const row = document.createElement('li');
      const button = document.createElement('button');
      button.type = 'button';
      const title = document.createElement('strong');
      title.textContent = draft.title;
      const meta = document.createElement('small');
      meta.textContent = (draft.kind === 'PROMOTION' ? '홍보' : '공지')
        + ' · ' + (draft.source === 'MCP' ? '외부 AI' : '대시보드')
        + ' · ' + dateFormat.format(new Date(draft.createdAt));
      button.append(title, meta);
      const load = () => {
        $('pnKind').value = draft.kind;
        $('pnTitle').value = draft.title;
        $('pnBody').value = draft.body;
        $('pnConsent').checked = false;
        savedRequest = null;
        renderPreview();
        $('pnBody').focus();
      };
      button.addEventListener('click', load);
      row.append(button);
      rows.append(row);
      if (focusDraftId && draft.id === focusDraftId) pendingFocus = load;
    }
  }

  async function saveDraft(event) {
    event.preventDefault();
    if (saving) return;
    const data = {
      kind: $('pnKind').value, title: $('pnTitle').value.trim(), body: currentMessage()
    };
    if (!data.title || !$('pnBody').value.trim() || data.body.length > 200) {
      report('제목과 200자 이내 문구를 입력하세요.');
      return;
    }
    const signature = JSON.stringify(data);
    if (savedRequest?.signature !== signature) {
      savedRequest = { signature, requestId: crypto.randomUUID() };
    }
    saving = true;
    renderPreview();
    try {
      const response = await fetchAuth(api, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...data, requestId: savedRequest.requestId })
      });
      if (!response.ok) throw new Error('초안을 저장하지 못했습니다.');
      try {
        await getDrafts();
        report('초안을 저장했습니다.');
      } catch (_) {
        report('초안은 저장됐습니다. 초안 목록을 새로 고쳐 주세요.');
      }
    } catch (error) {
      report(error.message);
    } finally {
      saving = false;
      renderPreview();
    }
  }

  async function copy(text, confirmation) {
    try {
      await navigator.clipboard.writeText(text);
      report(confirmation);
    } catch (_) {
      report('자동 복사를 완료하지 못했습니다. 문구를 선택해 복사해 주세요.');
      $('pnBody').focus();
      $('pnBody').select();
    }
  }

  function copyPrompt() {
    const brief = $('pnBrief').value.trim();
    if (!brief) {
      report('AI에 줄 안내 사항을 입력하세요.');
      $('pnBrief').focus();
      return;
    }
    const kind = $('pnKind').value;
    const prompt = [
      '다음 학원 안내를 학부모 카카오톡용으로 작성해 주세요.',
      '분류는 ' + kind + '입니다. 주어진 날짜, 대상, 장소만 사용하고 부족한 사실은 확인 질문을 해 주세요.',
      '학생 실명이나 연락처는 요청하거나 넣지 마세요. 제목 80자, 본문 200자 이내로 작성하세요.',
      ...(kind === 'PROMOTION' ? ['본문은 (광고)로 시작하고, 안내 사항에서 제공한 발신 학원과 수신거부 방법을 포함하세요.'] : []),
      '내가 저장을 요청하면 연결된 F2R MCP의 save_parent_notice_draft를 호출해 주세요.',
      'requestId는 새 UUID를 쓰고, 재시도에는 같은 UUID를 사용하세요. 제목, 분류, 본문만 저장하세요.',
      '', '안내 사항', brief
    ].join('\n');
    void copy(prompt, 'AI 요청문을 복사했습니다. 연결된 ChatGPT 또는 Claude에 붙여넣으세요.');
  }

  function shareKakao() {
    if (!readyToShare() || shareState !== 'ready') return;
    try {
      window.Kakao.Share.sendDefault({
        objectType: 'text',
        text: currentMessage(),
        link: { mobileWebUrl: homepage, webUrl: homepage },
        buttonTitle: '학원 홈페이지'
      });
      $('pnShareStatus').textContent = '카카오톡 화면에서 보낼 학부모를 선택하고 공유를 완료하세요.';
    } catch (_) {
      $('pnShareStatus').textContent = '카카오톡을 열지 못했습니다. 휴대전화 공유 또는 문구 복사를 이용하세요.';
    }
  }

  async function shareNative() {
    if (!readyToShare()) return;
    try {
      await navigator.share({ text: currentMessage() });
      $('pnShareStatus').textContent = '수신자와 전송 결과를 카카오톡에서 확인하세요.';
    } catch (error) {
      if (error.name !== 'AbortError') $('pnShareStatus').textContent = '공유를 열지 못했습니다. 문구를 복사해 주세요.';
    }
  }

  function init() {
    if (typeof location !== 'undefined') {
      const id = new URLSearchParams(location.search || '').get('draft');
      if (id && /^[0-9a-f-]{36}$/.test(id)) focusDraftId = id;
    }
    $('pnForm').addEventListener('submit', saveDraft);
    $('pnReload').addEventListener('click', () => getDrafts().catch(error => report(error.message)));
    $('pnKind').addEventListener('change', () => { $('pnConsent').checked = false; renderPreview(); });
    $('pnBody').addEventListener('input', () => { $('pnConsent').checked = false; renderPreview(); });
    $('pnConsent').addEventListener('change', renderPreview);
    $('pnCopyPrompt').addEventListener('click', copyPrompt);
    $('pnCopyBody').addEventListener('click', () => void copy(currentMessage(), '문구를 복사했습니다.'));
    $('pnKakao').addEventListener('click', shareKakao);
    $('pnNative').hidden = typeof navigator.share !== 'function';
    $('pnNative').addEventListener('click', shareNative);
    renderPreview();
    getDrafts().catch(error => report(error.message));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
