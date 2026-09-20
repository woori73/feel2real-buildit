(function () {
  'use strict';
  var NS = {
    init: function () {
      NS.bindToggles();
      NS.bindOpeners();
      NS.bindEntryGuards();
      NS.bindChecklist();
      NS.bindSituationForm();
      NS.bindLibraryClicks();
      NS.bindAiChat();
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

    bindChecklist: function () {
      var checks = document.querySelectorAll('[data-ns-check]');
      for (var i = 0; i < checks.length; i++) {
        checks[i].addEventListener('click', function () {
          var id = this.getAttribute('data-ns-check');
          var item = document.getElementById(id);
          if (!item) return;
          var now = item.getAttribute('data-ns-checked') === 'true' ? '' : 'true';
          item.setAttribute('data-ns-checked', now);
          if (now === 'true') {
            item.classList.add('checked');
            var done = item.querySelector('.ns-check-time');
            if (done) done.textContent = '완료 ' + NS.shortTime();
          } else {
            item.classList.remove('checked');
            var done = item.querySelector('.ns-check-time');
            if (done) done.textContent = '미완료';
          }
        });
      }
    },

    bindSituationForm: function () {
      var form = document.getElementById('ns-situation-form');
      if (!form) return;
      form.addEventListener('submit', function (e) {
        e.preventDefault();
      });
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

    /* ── NewStart AI 질문 ─────────────────────────────────────────────
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
          appendBlock('NewStart AI', '질문을 입력해 주세요.', 'error');
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
              appendBlock('NewStart AI', data.answer, null);
            } else if (result.status === 401) {
              appendBlock('NewStart AI', '로그인 후 질문할 수 있습니다.', 'error');
            } else {
              var msg = data.errorMessage && typeof data.errorMessage === 'string'
                ? data.errorMessage
                : '답변을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.';
              appendBlock('NewStart AI', msg, 'error');
            }
          })
          .catch(function () {
            window.clearTimeout(timer);
            appendBlock('NewStart AI', '답변을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.', 'error');
          })
          .then(function () {
            setBusy(false);
          });
      });

      if (input) input.removeAttribute('disabled');
      if (input) input.removeAttribute('readonly');
      if (send) send.removeAttribute('disabled');
    },

    shortTime: function () {
      var d = new Date();
      var h = String(d.getHours()).padStart(2, '0');
      var m = String(d.getMinutes()).padStart(2, '0');
      return h + ':' + m;
    },

    /* ── 새출발 최신정보 피드 ───────────────────────────────────────── */
    initFeed: function () {
      var tabs = document.querySelectorAll('.ns-feed-tab');
      console.log('initFeed: tabs found', tabs.length);
      if (!tabs.length) return;
      var panels = {
        official: document.querySelector('[data-feed-panel="official"]'),
        cafe: document.querySelector('[data-feed-panel="cafe"]'),
        ccrs: document.querySelector('[data-feed-panel="ccrs"]')
      };
      var feedUrl = '/data/newstart/feeds/latest_links.json';
      var allItems = null;

      function loadFeed() {
        if (allItems) { renderAll(); return; }
        fetch(feedUrl, { credentials: 'same-origin' })
          .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
          .then(function (data) { allItems = data.items || []; renderAll(); })
          .catch(function () { /* silent fail */ });
      }

      function renderAll() {
        if (!allItems) return;
        var bySource = { official: [], cafe: [], ccrs: [] };
        for (var i = 0; i < allItems.length; i++) {
          var item = allItems[i];
          if (bySource[item.sourceType]) bySource[item.sourceType].push(item);
        }
        renderPanel('official', bySource['OFFICIAL_NEWSTART'] || []);
        renderPanel('cafe', bySource['NEWSTART_CAFE'] || []);
        renderPanel('ccrs', bySource['OFFICIAL_CCRS'] || []);
      }

      function renderPanel(key, items) {
        var panel = panels[key];
        if (!panel) return;
        panel.innerHTML = '';
        if (!items.length) {
          panel.innerHTML = '<p class="ns-feed-empty">표시할 글이 없습니다.</p>';
          return;
        }
        var wrap = document.createElement('div');
        wrap.className = 'ns-feed-cards';
        for (var i = 0; i < items.length; i++) {
          var it = items[i];
          var a = document.createElement('a');
          a.className = 'ns-feed-card ns-ext';
          a.href = it.url;
          a.target = '_blank';
          a.rel = 'noopener';
          var badgeClass = 'ns-badge-' + key;
          var tagsHtml = '';
          if (it.topicTags && it.topicTags.length) {
            tagsHtml = '<div class="ns-feed-card-tags">' + it.topicTags.map(function(t){ return '<span class="ns-feed-tag">'+escapeHtml(t)+'</span>'; }).join('') + '</div>';
          }
          a.innerHTML =
            '<div class="ns-feed-card-header">' +
              '<span class="ns-badge ' + badgeClass + '">' + escapeHtml(it.sourceName) + '</span>' +
              '<span class="ns-feed-date">' + escapeHtml(it.publishedDate) + '</span>' +
            '</div>' +
            '<h3 class="ns-feed-card-title">' + escapeHtml(it.title) + '</h3>' +
            '<p class="ns-feed-card-summary">' + escapeHtml(it.summary) + '</p>' +
            tagsHtml +
            '<div class="ns-feed-card-footer">' +
              '<span class="ns-feed-read">원문 보기</span>' +
            '</div>';
          wrap.appendChild(a);
        }
        panel.appendChild(wrap);
      }

      function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function(c){
          return {'&':'&','<':'<','>':'>','"':'"',"'":"'"}[c];
        });
      }

      // 탭 전환
      console.log('initFeed: binding click handlers');
      tabs.forEach(function(tab){
        tab.addEventListener('click', function(){
          console.log('TAB CLICK', this.getAttribute('data-feed-tab'));
          var key = this.getAttribute('data-feed-tab');
          tabs.forEach(function(t){ t.classList.remove('ns-active'); t.setAttribute('aria-selected','false'); });
          this.classList.add('ns-active'); this.setAttribute('aria-selected','true');
          Object.values(panels).forEach(function(p){ p.classList.remove('ns-active'); });
          if (panels[key]) panels[key].classList.add('ns-active');
        });
      });

      // 초기 로드
      loadFeed();
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function(){
      NS.init();
      NS.initFeed();
    });
  } else {
    NS.init();
    NS.initFeed();
  }
})();
