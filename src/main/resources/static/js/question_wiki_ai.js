/** Question Wiki common Chrome AI entry point. One implementation serves every questionWikiId. */
(function () {
  'use strict';
  var questionWikiId = null;
  var contextPack = null;
  var preview = null;
  var runtime = { prompt: 'unavailable', koToEn: 'unavailable', enToKo: 'unavailable' };
  var ai = window.ChromeBuiltInQuestionAi;

  var els = {
    toggle: document.getElementById('qwAiToggle'),
    panel: document.getElementById('qwAiPanel'),
    status: document.getElementById('qwAiStatus'),
    promptStatus: document.getElementById('qwAiPromptStatus'),
    koEnStatus: document.getElementById('qwAiKoEnStatus'),
    enKoStatus: document.getElementById('qwAiEnKoStatus'),
    presets: document.getElementById('qwAiPresets'),
    freeRequest: document.getElementById('qwAiRequest'),
    pattern: document.getElementById('qwAiPattern'),
    generate: document.getElementById('qwAiGenerate'),
    preview: document.getElementById('qwAiPreview'),
    previewBody: document.getElementById('qwAiPreviewBody'),
    regenerate: document.getElementById('qwAiRegenerate'),
    save: document.getElementById('qwAiSave')
  };

  if (!els.toggle || !els.panel) return;
  els.toggle.addEventListener('click', togglePanel);
  els.generate.addEventListener('click', generate);
  els.regenerate.addEventListener('click', generate);
  els.save.addEventListener('click', save);
  els.presets.addEventListener('click', function (event) {
    var button = event.target.closest('[data-ai-preset]');
    if (!button) return;
    els.presets.querySelectorAll('[data-ai-preset]').forEach(function (item) {
      item.classList.toggle('active', item === button);
    });
  });
  window.addEventListener('questionwiki:context', function (event) {
    questionWikiId = String(event.detail.questionWikiId);
    contextPack = event.detail.context;
  });

  async function togglePanel() {
    els.panel.hidden = !els.panel.hidden;
    els.toggle.setAttribute('aria-expanded', String(!els.panel.hidden));
    if (els.panel.hidden || !ai) return;
    setStatus('Chrome 기기 내 AI 상태를 확인하는 중입니다.');
    runtime = await ai.getRuntimeStatus();
    renderRuntime();
    setStatus(runtime.prompt === 'unavailable' || runtime.prompt === 'error'
      ? '이 브라우저에서는 Prompt AI를 사용할 수 없습니다. 기존 Wiki 기능은 그대로 사용할 수 있습니다.'
      : '현재 문항으로 문제를 만들 수 있습니다.');
  }

  function renderRuntime() {
    els.promptStatus.textContent = runtime.prompt;
    els.koEnStatus.textContent = runtime.koToEn;
    els.enKoStatus.textContent = runtime.enToKo;
    els.generate.disabled = !contextPack || runtime.prompt === 'unavailable' || runtime.prompt === 'error';
  }

  function selectedPreset() {
    var selected = els.presets.querySelector('[data-ai-preset].active');
    return selected ? selected.dataset.aiPreset : 'similar';
  }

  async function generate() {
    if (!contextPack || !questionWikiId) { setStatus('현재 문항 Context를 불러온 뒤 다시 시도해 주세요.'); return; }
    if (!ai) { setStatus('Chrome AI 모듈을 불러오지 못했습니다. 기존 Wiki 기능은 그대로 사용할 수 있습니다.'); return; }
    els.generate.disabled = true;
    els.regenerate.disabled = true;
    setStatus('Chrome 기기 내 AI로 문제를 생성하는 중입니다.');
    try {
      var result = await ai.generate({
        preset: selectedPreset(),
        freeRequest: els.freeRequest.value.trim(),
        schoolPatternReference: els.pattern.value.trim(),
        contextPack: contextPack,
        sourceQuestionId: Number(questionWikiId),
        onDownloadProgress: function (name, loaded) {
          setStatus(name + ' 모델 다운로드 ' + Math.round(loaded * 100) + '%');
        }
      });
      preview = result.question;
      renderPreview(preview);
      els.preview.hidden = false;
      setStatus(result.notice || '생성 결과를 확인하세요. 아직 저장되지 않았습니다.');
    } catch (error) {
      setStatus(error && error.message ? error.message : 'Chrome AI 문제 생성에 실패했습니다.');
    } finally {
      els.generate.disabled = runtime.prompt === 'unavailable' || runtime.prompt === 'error';
      els.regenerate.disabled = false;
    }
  }

  function renderPreview(question) {
    var choices = (question.choices || []).map(function (choice, index) {
      var value = typeof choice === 'object' ? (choice.text || choice.html || JSON.stringify(choice)) : choice;
      return '<li><b>' + (index + 1) + '.</b> ' + esc(value) + '</li>';
    }).join('');
    els.previewBody.innerHTML = '<h4>' + esc(question.instruction) + '</h4>'
      + '<div class="qw-ai-passage">' + nl2br(esc(textOf(question.passage))) + '</div>'
      + (choices ? '<ol class="qw-ai-choices">' + choices + '</ol>' : '')
      + '<dl class="qw-kv"><dt>정답</dt><dd>' + nl2br(esc(textOf(question.answer))) + '</dd>'
      + '<dt>해설</dt><dd>' + nl2br(esc(question.explanationKo || question.explanation)) + '</dd></dl>';
  }

  async function save() {
    if (!preview || !questionWikiId) return;
    var me = readUser();
    if (!me) {
      alert('저장하려면 로그인해 주세요.');
      if (typeof openLoginModal === 'function') openLoginModal();
      return;
    }
    els.save.disabled = true;
    setStatus('변형문제를 저장하는 중입니다.');
    var source = contextPack && contextPack.question ? contextPack.question : {};
    var provenance = 'Chrome built-in AI 생성 · sourceQuestionWikiId=' + questionWikiId
      + ' · generationMode=CHROME_BUILT_IN_AI · requestedType=' + selectedPreset()
      + ' · schoolPatternReferenceUsed=' + String(!!els.pattern.value.trim());
    try {
      var response = await fetchAuth('/api/wiki/questions/' + encodeURIComponent(questionWikiId) + '/posts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          category: 'VARIANT',
          dataKind: 'GENERATED_PRACTICE',
          title: 'Chrome AI 변형문제',
          content: provenance,
          variant: {
            questionText: questionText(preview),
            questionType: preview.type,
            answer: textOf(preview.answer),
            explanation: preview.explanationKo || preview.explanation,
            sourceSentence: '',
            sourceQuestion: source.displayContext || ((source.questionNumber || '') + '번'),
            difficulty: els.freeRequest.value.trim()
          }
        }),
        redirectOnAuthError: false
      });
      if (response.status === 401 || response.status === 403) {
        if (typeof openLoginModal === 'function') openLoginModal();
        setStatus('저장하려면 로그인해 주세요.');
        return;
      }
      if (!response.ok) throw new Error((await response.text()) || '저장하지 못했습니다.');
      setStatus('현재 문항의 변형문제 영역에 저장했습니다.');
      window.dispatchEvent(new CustomEvent('questionwiki:reload'));
    } catch (error) {
      setStatus(error && error.message ? error.message : '저장하지 못했습니다.');
    } finally {
      els.save.disabled = false;
    }
  }

  function questionText(question) {
    var parts = [question.instruction, textOf(question.passage)];
    (question.choices || []).forEach(function (choice, index) { parts.push((index + 1) + '. ' + textOf(choice)); });
    return parts.filter(Boolean).join('\n\n');
  }
  function textOf(value) {
    if (value == null) return '';
    if (Array.isArray(value)) return value.join(', ');
    if (typeof value === 'object') return String(value.text || value.html || JSON.stringify(value)).replace(/<[^>]*>/g, ' ');
    return String(value);
  }
  function setStatus(message) { els.status.textContent = message; }
  function readUser() { try { return JSON.parse(sessionStorage.getItem('currentUser')); } catch (_) { return null; } }
  function nl2br(value) { return String(value).replace(/\n/g, '<br>'); }
  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>'"]/g, function (char) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char];
    });
  }
})();
