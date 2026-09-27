(function () {
  'use strict';
  var NS = {
    init: function () {
      NS.bindToggles();
      NS.bindOpeners();
      NS.bindEntryGuards();
      NS.bindDebtStatus();
      NS.bindLibraryClicks();
      NS.bindAiChat();
      NS.openFromHash();
    },

    bindToggles: function () {
      var tops = document.querySelectorAll('[data-ns-toggle]');
      for (var i = 0; i < tops.length; i++) {
        tops[i].addEventListener('click', function (e) {
          var targetId = this.getAttribute('data-ns-toggle');
          var target = document.getElementById(targetId);
          if (!target) return;
          var open = target.getAttribute('hidden') === null;
          if (open) {
            target.setAttribute('hidden', '');
            this.setAttribute('aria-expanded', 'false');
          } else {
            target.removeAttribute('hidden');
            this.setAttribute('aria-expanded', 'true');
          }
        });
      }
    },

    bindOpeners: function () {
      var openers = document.querySelectorAll('[data-ns-open]');
      for (var i = 0; i < openers.length; i++) {
        openers[i].addEventListener('click', function () {
          var target = document.getElementById(this.getAttribute('data-ns-open'));
          if (!target) return;
          target.removeAttribute('hidden');
          this.setAttribute('aria-expanded', 'true');
        });
      }
    },

    bindEntryGuards: function () {
      var disabled = document.querySelectorAll('.ns-entry[aria-disabled="true"]');
      for (var i = 0; i < disabled.length; i++) {
        disabled[i].addEventListener('click', function (e) {
          e.preventDefault();
        });
      }
    },

    /* ── 내 채무상황 상태창 (서버 저장 SOURCE OF TRUTH) ─────────────────
     * /api/newstart/debt-status — 대상 회원은 서버 principal만 사용하므로 here에 userId가 없다.
     * 비로그인(401)에는 조회·저장 모두 화면 메시지로 안내하고 어떤 것도 localStorage에 두지 않는다. */
    DEBT_FLAGS: [
      ['credit4uChecked', 'ns-flag-credit4u'],
      ['creditorConfirmed', 'ns-flag-creditor'],
      ['personalLoanConfirmed', 'ns-flag-loan'],
      ['overdueRegisteredConfirmed', 'ns-flag-overdue'],
      ['debtGuaranteeConfirmed', 'ns-flag-guarantee'],
      ['creditorChangeConfirmed', 'ns-flag-creditor-change']
    ],
    DEBT_STATE_LABELS: { NOT_STARTED: '미확인', IN_PROGRESS: '확인 중', COMPLETED: '정리 완료' },

    bindDebtStatus: function () {
      var self = this;
      var form = document.getElementById('ns-situation-form');
      var saveBtn = document.getElementById('ns-debt-save');
      if (!form || !saveBtn) return;

      var summary = document.getElementById('ns-debt-summary');
      var stage = document.getElementById('ns-stage');
      var stateEl = document.getElementById('ns-debt-save-state');
      var homeState = document.getElementById('ns-debt-home-state');
      var inputs = {};
      this.DEBT_FLAGS.forEach(function (pair) {
        inputs[pair[0]] = document.getElementById(pair[1]);
      });

      function paintCheckbox(id, value) {
        var input = inputs[id];
        if (!input) return;
        input.checked = !!value;
        var label = input.closest('.ns-check');
        if (label) label.classList.toggle('checked', !!value);
      }

      function readPayload() {
        var payload = {
          debtSummary: summary ? summary.value : '',
          stage: stage ? stage.value : ''
        };
        self.DEBT_FLAGS.forEach(function (pair) {
          payload[pair[0]] = !!(inputs[pair[0]] && inputs[pair[0]].checked);
        });
        return payload;
      }

      function paintHomeState(data) {
        if (!homeState || !data) return;
        homeState.textContent = self.DEBT_STATE_LABELS[data.status] || '미확인';
        homeState.setAttribute('data-state', data.status || 'NOT_STARTED');
      }

      function setState(text) {
        if (stateEl) stateEl.textContent = text || '';
      }

      function hydrate(data) {
        if (!data) return;
        self.DEBT_FLAGS.forEach(function (pair) { paintCheckbox(pair[0], data[pair[0]]); });
        if (summary && data.debtSummary) summary.value = data.debtSummary;
        if (stage && data.stage) stage.value = data.stage;
        paintHomeState(data);
      }

      for (var key in inputs) {
        (function (id) {
          var input = inputs[id];
          if (!input) return;
          input.addEventListener('change', function () { paintCheckbox(id, input.checked); });
        })(key);
      }
      if (form) {
        form.addEventListener('reset', function () {
          window.setTimeout(function () {
            self.DEBT_FLAGS.forEach(function (pair) { paintCheckbox(pair[0], false); });
          }, 0);
        });
      }

      fetch('/api/newstart/debt-status/me', { credentials: 'same-origin' })
        .then(function (res) { return res.ok ? res.json() : null; })
        .then(hydrate)
        .catch(function () { /* 조회 실패 시 빈 상태로 둔다 */ });

      form.addEventListener('submit', function (e) {
        e.preventDefault();
        saveBtn.disabled = true;
        setState('저장 중…');
        fetch('/api/newstart/debt-status', {
          method: 'PUT',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(readPayload())
        })
          .then(function (res) {
            return res.json().catch(function () { return {}; }).then(function (data) {
              return { status: res.status, data: data };
            });
          })
          .then(function (result) {
            saveBtn.disabled = false;
            if (result.status === 401) {
              setState('로그인 후 저장할 수 있습니다.');
              return;
            }
            if (result.status === 200) {
              var checked = 0;
              self.DEBT_FLAGS.forEach(function (pair) { if (result.data[pair[0]]) checked++; });
              setState('저장되었습니다 (' + checked + '/6 확인)');
              hydrate(result.data);
            } else {
              setState(result.data && result.data.errorMessage ? result.data.errorMessage : '저장하지 못했습니다. 잠시 후 다시 시도해주세요.');
            }
          })
          .catch(function () {
            saveBtn.disabled = false;
            setState('저장하지 못했습니다. 잠시 후 다시 시도해주세요.');
          });
      });

      var askBtn = document.getElementById('ns-debt-ask-agent');
      var aiInput = document.getElementById('ns-ai-input');
      if (askBtn && aiInput) {
        askBtn.addEventListener('click', function () {
          var payload = readPayload();
          var checked = 0;
          self.DEBT_FLAGS.forEach(function (pair) { if (payload[pair[0]]) checked++; });
          var question = '내 채무상황 — 단계: ' + (payload.stage || '미선택')
            + ', Credit4U 확인 항목: ' + checked + '/6'
            + ', 메모: ' + (payload.debtSummary || '(없음)')
            + '. 이 상황에서 다음에 할 일을 알려주세요.';
          aiInput.value = question;
          var aiSection = document.getElementById('ns-ai');
          if (aiSection && aiSection.scrollIntoView) aiSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
          aiInput.focus();
        });
      }
    },

    openFromHash: function () {
      function openIfTarget() {
        if (window.location.hash !== '#ns-status-panel') return;
        var panel = document.getElementById('ns-status-panel');
        if (!panel) return;
        panel.removeAttribute('hidden');
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
      openIfTarget();
      window.addEventListener('hashchange', openIfTarget);
    },

    bindLibraryClicks: function () {
      var cards = document.querySelectorAll('.ns-library-card');
      for (var i = 0; i < cards.length; i++) {
        cards[i].addEventListener('click', function (e) {
          var href = this.getAttribute('data-ns-href');
          if (href) {
            e.preventDefault();
          }
        });
      }
    },

    /* ── 지피지기 AI 질문 ─────────────────────────────────────────────
     * 서버(/api/newstart/ai/chat)에 실제 질문을 보내고 답변을 표시한다.
     * 비로그인은 서버가 401로 거부한다 — 폼 자체를 숨기지 않고 서버 응답을 따른다. */
    bindAiChat: function () {
      var form = document.getElementById('ns-ai-form');
      var input = document.getElementById('ns-ai-input');
      var send = document.getElementById('ns-ai-send');
      var thread = document.getElementById('ns-ai-thread');
      if (!form || !input || !send) return;

      var busy = false;

      function setBusy(value) {
        busy = value;
        send.disabled = value;
        input.disabled = value;
        send.textContent = value ? '답변 중…' : '질문';
      }

      function appendBlock(label, text, kind) {
        if (!thread) return;
        var block = document.createElement('div');
        block.className = 'ns-ai-block' + (kind ? ' ns-ai-' + kind : '');
        var who = document.createElement('strong');
        who.className = 'ns-ai-who';
        who.textContent = label;
        var body = document.createElement('div');
        body.className = 'ns-ai-text';
        body.textContent = text;
        block.appendChild(who);
        block.appendChild(body);
        thread.appendChild(block);
        if (block.scrollIntoView) block.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }

      form.addEventListener('submit', function (e) {
        e.preventDefault();
        if (busy) return;
        var message = (input.value || '').trim();
        if (!message) {
          appendBlock('지피지기 AI', '질문을 입력해 주세요.', 'error');
          return;
        }
        setBusy(true);
        appendBlock('나', message, null);
        input.value = '';

        var controller = typeof AbortController === 'function' ? new AbortController() : null;
        var timer = window.setTimeout(function () {
          if (controller) controller.abort();
        }, 120000);

        fetch('/api/newstart/ai/chat', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message: message }),
          signal: controller ? controller.signal : undefined
        })
          .then(function (res) {
            return res.json().catch(function () { return {}; }).then(function (data) {
              return { status: res.status, data: data };
            });
          })
          .then(function (result) {
            window.clearTimeout(timer);
            var data = result.data || {};
            if (result.status === 200 && data.status === 'ok' && data.answer) {
              appendBlock('지피지기 AI', data.answer, null);
            } else if (result.status === 401) {
              appendBlock('지피지기 AI', '로그인 후 질문할 수 있습니다.', 'error');
            } else {
              var msg = data.errorMessage && typeof data.errorMessage === 'string'
                ? data.errorMessage
                : '답변을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.';
              appendBlock('지피지기 AI', msg, 'error');
            }
          })
          .catch(function () {
            window.clearTimeout(timer);
            appendBlock('지피지기 AI', '답변을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.', 'error');
          })
          .then(function () {
            setBusy(false);
          });
      });

      if (input) input.removeAttribute('disabled');
      if (input) input.removeAttribute('readonly');
      if (send) send.removeAttribute('disabled');
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', NS.init);
  } else {
    NS.init();
  }
})();
