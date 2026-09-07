/**
 * Question Wiki AI layout contract hotfix.
 * Keeps inline-number grammar/vocabulary tasks as passage-only questions and
 * strengthens constructed-response blank-count generation without changing
 * the shared AI router.
 */
(function () {
  'use strict';

  var nativeFetch = window.fetch.bind(window);
  var circled = /[①②③④⑤]/;

  function selectedType() {
    var active = document.querySelector('#qwAiPresets [data-ai-preset].active');
    return active ? active.dataset.aiPreset : '';
  }

  function generationContract(type) {
    if (type === 'grammar' || type === 'vocabulary') {
      return [
        'QUESTION_WIKI_LAYOUT_CONTRACT:',
        'For this inline-tested ' + type + ' task, put ① ② ③ ④ ⑤ immediately before the five tested words/phrases IN THE PASSAGE.',
        'Those five tested words/phrases are the choices themselves. Do not create a second, duplicate set of answer-choice sentences.',
        'Keep choices as five short tested-word values only because the transport schema requires five entries; the UI will not render them as a separate choice list.',
        'The passage must make every tested position visually identifiable and answer/explanation must point to the numbered position.'
      ].join('\n');
    }
    if (type === 'subjective') {
      return [
        'QUESTION_WIKI_LAYOUT_CONTRACT:',
        'This is constructed response only: choices must be empty.',
        'Preserve the exact number of visible answer blanks/slots required by the selected same-question reference mechanics.',
        'Every visible blank/slot must have a corresponding required answer component. Never collapse three blanks into two questions/answers.',
        'Before output, count the visible blanks and independently count the answer components; the counts must match exactly.'
      ].join('\n');
    }
    return '';
  }

  function stripDuplicatedInlineChoices(body) {
    try {
      if (!body || body.category !== 'VARIANT' || !body.variant) return body;
      var type = String(body.variant.questionType || '');
      if (type !== 'grammar' && type !== 'vocabulary') return body;
      var text = String(body.variant.questionText || '');
      if (!circled.test(text)) return body;
      var parts = text.split(/\n\n/);
      while (parts.length && /^[①②③④⑤]\s/.test(parts[parts.length - 1])) parts.pop();
      body.variant.questionText = parts.join('\n\n');
    } catch (_) {}
    return body;
  }

  window.fetch = function (input, init) {
    var url = typeof input === 'string' ? input : (input && input.url) || '';
    var next = init;
    try {
      if (url.indexOf('/problem-generator/wiki/api/ai/complete') >= 0 && init && typeof init.body === 'string') {
        var payload = JSON.parse(init.body);
        var contract = generationContract(selectedType());
        if (contract) {
          payload.prompt = String(payload.prompt || '') + '\n\n' + contract;
          next = Object.assign({}, init, { body: JSON.stringify(payload) });
        }
      } else if (/\/api\/wiki\/questions\/[^/]+\/posts(?:\?|$)/.test(url) && init && init.method === 'POST' && typeof init.body === 'string') {
        var post = stripDuplicatedInlineChoices(JSON.parse(init.body));
        next = Object.assign({}, init, { body: JSON.stringify(post) });
      }
    } catch (_) {}
    return nativeFetch(input, next);
  };

  function underlineInlineTargets(passage) {
    if (!passage || passage.dataset.qwInlineFixed === '1') return;
    var type = selectedType();
    if (type !== 'grammar' && type !== 'vocabulary') return;
    var text = passage.textContent || '';
    var matches = text.match(/[①②③④⑤]/g) || [];
    if (matches.length < 5) return;

    // The generator contract places each marker immediately before its tested
    // token/phrase. Underline the first lexical token after each marker; the
    // circled number remains outside the underline, matching exam typography.
    passage.innerHTML = passage.innerHTML.replace(/([①②③④⑤])\s*([^\s<]+)/g,
      '$1 <span class="qw-ai-tested-expression">$2</span>');
    passage.dataset.qwInlineFixed = '1';

    var choices = passage.parentElement && passage.parentElement.querySelector('.qw-ai-choices');
    if (choices) choices.hidden = true;
  }

  function normalizePreview() {
    var body = document.getElementById('qwAiPreviewBody');
    if (!body) return;
    var passage = body.querySelector('.qw-ai-passage');
    if (!passage) return;
    underlineInlineTargets(passage);
    var type = selectedType();
    if ((type === 'grammar' || type === 'vocabulary') && circled.test(passage.textContent || '')) {
      var choices = body.querySelector('.qw-ai-choices');
      if (choices) choices.hidden = true;
    }
  }

  var previewBody = document.getElementById('qwAiPreviewBody');
  if (previewBody) new MutationObserver(normalizePreview).observe(previewBody, { childList: true, subtree: true });
})();
