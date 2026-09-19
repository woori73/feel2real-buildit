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
