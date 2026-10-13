/* 입시상담 가이드 — 채팅 로직 */
(function () {
  'use strict';

  var welcomeSection = document.getElementById('ag-welcome');
  var chatSection = document.getElementById('ag-chat');
  var welcomeForm = document.getElementById('ag-welcome-form');
  var welcomeInput = document.getElementById('ag-welcome-input');
  var chatForm = document.getElementById('ag-form');
  var chatInput = document.getElementById('ag-input');
  var messagesEl = document.getElementById('ag-messages');
  var backBtn = document.getElementById('ag-back');
  var yearToggle = document.getElementById('ag-year-toggle');
  var yearLabel = document.getElementById('ag-year-label');

  // 학년도: null=전체, 2027, 2028
  var academicYear = null;
  var sessionToken = null;
  var sending = false;

  // 세션 토큰 (localStorage)
  try {
    sessionToken = localStorage.getItem('ag_session_token');
    if (!sessionToken) {
      sessionToken = 'xxxxxxxx'.replace(/x/g, function () {
        return Math.floor(Math.random() * 16).toString(16);
      }) + Date.now().toString(16);
      localStorage.setItem('ag_session_token', sessionToken);
    }
  } catch (e) { /* 무시 */ }

  function showChat() {
    welcomeSection.hidden = true;
    chatSection.hidden = false;
    chatInput.focus();
  }

  function showWelcome() {
    chatSection.hidden = true;
    welcomeSection.hidden = false;
    welcomeInput.focus();
  }

  function scrollBottom() {
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function addMessage(text, isUser) {
    var div = document.createElement('div');
    div.className = 'ag-msg ' + (isUser ? 'ag-msg-user' : 'ag-msg-ai');
    div.textContent = text;
    messagesEl.appendChild(div);
    scrollBottom();
    return div;
  }

  function addTyping() {
    var div = document.createElement('div');
    div.className = 'ag-msg ag-msg-ai ag-msg-typing';
    div.textContent = '입시 정보를 검색하고 있어요...';
    messagesEl.appendChild(div);
    scrollBottom();
    return div;
  }

  function escapeHtml(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
  }

  function renderAnswer(container, data) {
    container.classList.remove('ag-msg-typing');
    container.innerHTML = '';

    // 답변 텍스트
    var textDiv = document.createElement('div');
    textDiv.textContent = data.answer || '';
    container.appendChild(textDiv);

    // 2028 계획 경고
    if (data.academicYear === 2028 || data.yearConfirmed === false) {
      var warn = document.createElement('div');
      warn.className = 'ag-warning';
      warn.textContent = '⚠️ 2028학년도는 계획 단계로 변경될 수 있습니다.';
      container.appendChild(warn);
    }

    // 출처
    if (data.sources && data.sources.length > 0) {
      var srcDiv = document.createElement('div');
      srcDiv.className = 'ag-sources';
      var title = document.createElement('div');
      title.className = 'ag-sources-title';
      title.textContent = '[관련 링크 / 출처]';
      srcDiv.appendChild(title);
      data.sources.forEach(function (s) {
        var a = document.createElement('a');
        a.className = 'ag-source';
        a.href = s.url;
        a.target = '_blank';
        a.rel = 'noopener';
        var kind = document.createElement('span');
        kind.className = 'ag-source-kind';
        kind.textContent = s.kind;
        a.appendChild(kind);
        a.appendChild(document.createTextNode(s.title));
        srcDiv.appendChild(a);
      });
      container.appendChild(srcDiv);
    }
    scrollBottom();
  }

  function send(question) {
    question = (question || '').trim();
    if (!question || sending) return;
    sending = true;

    showChat();
    addMessage(question, true);
    var typingEl = addTyping();

    fetch('/api/admissions/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: question,
        academicYear: academicYear,
        sessionToken: sessionToken
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (data.status === 'ok') {
          renderAnswer(typingEl, data);
        } else {
          typingEl.classList.remove('ag-msg-typing');
          typingEl.textContent = data.errorMessage || '오류가 발생했습니다.';
        }
      })
      .catch(function () {
        typingEl.classList.remove('ag-msg-typing');
        typingEl.textContent = '연결에 실패했습니다. 잠시 후 다시 시도해 주세요.';
      })
      .finally(function () {
        sending = false;
        chatInput.value = '';
        chatInput.focus();
      });
  }

  // 첫 화면 폼
  welcomeForm.addEventListener('submit', function (e) {
    e.preventDefault();
    send(welcomeInput.value);
    welcomeInput.value = '';
  });

  // 대화 폼
  chatForm.addEventListener('submit', function (e) {
    e.preventDefault();
    send(chatInput.value);
  });

  // 예시 질문
  document.querySelectorAll('.ag-example').forEach(function (btn) {
    btn.addEventListener('click', function () {
      send(btn.getAttribute('data-q'));
    });
  });

  // 뒤로
  backBtn.addEventListener('click', showWelcome);

  // 학년도 토글: 전체 → 2027 → 2028 → 전체
  yearToggle.addEventListener('click', function () {
    if (academicYear === null) {
      academicYear = 2027;
      yearLabel.textContent = '2027 확정';
    } else if (academicYear === 2027) {
      academicYear = 2028;
      yearLabel.textContent = '2028 계획';
    } else {
      academicYear = null;
      yearLabel.textContent = '전체';
    }
  });

  welcomeInput.focus();
})();
