(function () {
  'use strict';
  var NS = {
    init: function () {
      NS.bindToggles();
      NS.bindChecklist();
      NS.bindSituationForm();
      NS.bindAiSend();
      NS.bindLibraryClicks();
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
        var btn = form.querySelector('[type="submit"]');
      });
    },

    bindAiSend: function () {
      var input = document.getElementById('ns-ai-input');
      var send = document.getElementById('ns-ai-send');
      var box = document.getElementById('ns-ai-empty');
      if (!input || !send) return;
      send.addEventListener('click', function () { NS.sendAi(input, box); });
      input.addEventListener('keydown', function (e) {
        if (e.keyCode === 13 && !e.shiftKey) {
          e.preventDefault();
          NS.sendAi(input, box);
        }
      });
    },

    sendAi: function (input, box) {
      var q = (input.value || '').trim();
      if (!q) return;
      input.value = '';
      if (box) {
        var p = document.createElement('p');
        p.className = 'ns-ai-q';
        p.textContent = '나: ' + q;
        box.parentNode.insertBefore(p, box);
      }
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

    shortTime: function () {
      var d = new Date();
      var h = String(d.getHours()).padStart(2, '0');
      var m = String(d.getMinutes()).padStart(2, '0');
      return h + ':' + m;
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', NS.init);
  } else {
    NS.init();
  }
})();
