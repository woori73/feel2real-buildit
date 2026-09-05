/** Question Wiki AI entry point. Chrome local first; free server fallback only. */
(function () {
  'use strict';
  var questionWikiId = null;
  var contextPack = null;
  var preview = null;
  var generationMode = null;
  var runtime = { prompt: 'unavailable', koToEn: 'unavailable', enToKo: 'unavailable' };
  var ai = window.ChromeBuiltInQuestionAi;
  var FREE_AI_URL = '/problem-generator/wiki/api/ai/complete';
  var FREE_PROVIDER = 'openrouter-free';

  var els = {
    toggle: document.getElementById('qwAiToggle'), panel: document.getElementById('qwAiPanel'),
    status: document.getElementById('qwAiStatus'), promptStatus: document.getElementById('qwAiPromptStatus'),
    koEnStatus: document.getElementById('qwAiKoEnStatus'), enKoStatus: document.getElementById('qwAiEnKoStatus'),
    presets: document.getElementById('qwAiPresets'), freeRequest: document.getElementById('qwAiRequest'),
    pattern: document.getElementById('qwAiPattern'), generate: document.getElementById('qwAiGenerate'),
    preview: document.getElementById('qwAiPreview'), previewBody: document.getElementById('qwAiPreviewBody'),
    regenerate: document.getElementById('qwAiRegenerate'), save: document.getElementById('qwAiSave')
  };

  if (!els.toggle || !els.panel) return;
  els.toggle.addEventListener('click', togglePanel);
  els.generate.addEventListener('click', generate);
  els.regenerate.addEventListener('click', generate);
  els.save.addEventListener('click', save);
  els.presets.addEventListener('click', function (event) {
    var button = event.target.closest('[data-ai-preset]');
    if (!button) return;
    els.presets.querySelectorAll('[data-ai-preset]').forEach(function (item) { item.classList.toggle('active', item === button); });
  });
  window.addEventListener('questionwiki:context', function (event) {
    questionWikiId = String(event.detail.questionWikiId);
    contextPack = ai && ai.buildCanonicalContext
      ? ai.buildCanonicalContext(event.detail.context, questionWikiId)
      : null;
    preview = null;
    generationMode = null;
    els.preview.hidden = true;
    els.save.disabled = true;
    renderRuntime();
  });

  async function togglePanel() {
    els.panel.hidden = !els.panel.hidden;
    els.toggle.setAttribute('aria-expanded', String(!els.panel.hidden));
    if (els.panel.hidden) return;
    if (!ai) {
      runtime.prompt = 'unavailable';
      renderRuntime();
      setStatus('Chrome AI를 사용할 수 없어 로그인 회원은 무료 서버 AI를 사용할 수 있습니다.');
      return;
    }
    setStatus('Chrome 기기 내 AI 상태를 확인하는 중입니다.');
    try { runtime = await ai.getRuntimeStatus(); } catch (_) { runtime.prompt = 'error'; }
    renderRuntime();
    setStatus(canUseChrome()
      ? 'Chrome 기기 내 AI를 우선 사용합니다.'
      : 'Chrome AI를 사용할 수 없어 로그인 회원은 무료 서버 AI를 사용합니다. 유료 AI로 자동 전환하지 않습니다.');
  }

  function canUseChrome() { return runtime.prompt !== 'unavailable' && runtime.prompt !== 'error'; }
  function renderRuntime() {
    els.promptStatus.textContent = runtime.prompt;
    els.koEnStatus.textContent = runtime.koToEn;
    els.enKoStatus.textContent = runtime.enToKo;
    els.generate.disabled = !contextPack;
  }
  function selectedPreset() {
    var selected = els.presets.querySelector('[data-ai-preset].active');
    return selected ? selected.dataset.aiPreset : 'similar';
  }

  async function generate() {
    if (!contextPack || !questionWikiId) { setStatus('현재 문항 Context를 불러온 뒤 다시 시도해 주세요.'); return; }
    preview = null;
    generationMode = null;
    els.preview.hidden = true;
    els.save.disabled = true;
    try {
      if (!ai || !ai.assertUsableContext) {
        throw new Error('AI Context 검증 모듈을 불러오지 못했습니다.');
      }
      ai.assertUsableContext(contextPack);
      els.generate.disabled = true;
      els.regenerate.disabled = true;
      var result;
      if (ai && canUseChrome()) {
        setStatus('Chrome 기기 내 AI로 문제를 생성하는 중입니다.');
        result = await generateChrome();
        generationMode = 'CHROME_BUILT_IN_AI';
      } else {
        setStatus('Chrome AI를 사용할 수 없어 OpenRouter 무료 AI로 생성하는 중입니다.');
        result = await generateFreeServer();
        generationMode = 'OPENROUTER_FREE';
      }
      preview = result.question;
      renderPreview(preview);
      els.preview.hidden = false;
      els.save.disabled = false;
      setStatus(result.notice || '생성 결과를 확인하세요. 아직 저장되지 않았습니다.');
    } catch (error) {
      setStatus(error && error.message ? error.message : '무료 AI 문제 생성에 실패했습니다.');
    } finally {
      els.generate.disabled = !contextPack;
      els.regenerate.disabled = false;
    }
  }

  function generateChrome() {
    return ai.generate({
      preset: selectedPreset(), freeRequest: els.freeRequest.value.trim(),
      schoolPatternReference: els.pattern.value.trim(), contextPack: contextPack,
      sourceQuestionId: Number(questionWikiId),
      onDownloadProgress: function (name, loaded) { setStatus(name + ' 모델 다운로드 ' + Math.round(loaded * 100) + '%'); }
    });
  }

  async function generateFreeServer() {
    if (!readUser()) {
      if (typeof openLoginModal === 'function') openLoginModal();
      throw new Error('Chrome AI 미지원 기기에서 무료 서버 AI를 사용하려면 로그인해 주세요.');
    }
    var request = {
      preset: selectedPreset(),
      freeRequest: els.freeRequest.value.trim(),
      schoolPatternReference: els.pattern.value.trim(),
      contextPack: contextPack,
      sourceQuestionId: Number(questionWikiId),
      cloudMode: true
    };
    var prompt = ai.buildPrompt(request);
    var response = await fetchAuth(FREE_AI_URL, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, redirectOnAuthError: false,
      body: JSON.stringify({
        provider: FREE_PROVIDER,
        system: 'Create exactly one Korean-instruction English-exam question from CURRENT_QUESTION only. Keep English source content in English and write the explanation in Korean. Return JSON only. The selected type and sourceQuestionId are mandatory. SCHOOL_PATTERN_REFERENCE is mechanics-only and never a content source.',
        prompt: prompt,
        max_output_chars: 16000
      })
    });
    if (response.status === 401 || response.status === 403) {
      if (typeof openLoginModal === 'function') openLoginModal();
      throw new Error('무료 서버 AI를 사용하려면 로그인해 주세요.');
    }
    if (!response.ok) {
      var detail = await response.text();
      if (response.status === 429) throw new Error('현재 무료 AI 사용량이 소진되었습니다. 잠시 후 다시 시도해 주세요.');
      throw new Error('현재 무료 AI를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.' + (detail ? ' (' + detail.slice(0, 120) + ')' : ''));
    }
    var payload = await response.json();
    var question = parseQuestion(payload.text || payload.content || payload.result || '');
    ai.validateQuestion(question, request);
    question.generationMode = 'OPENROUTER_FREE';
    return { question: question, notice: 'OpenRouter 무료 AI로 생성했습니다. 유료 AI는 사용하지 않았습니다. 저장 전 결과를 확인하세요.' };
  }

  function parseQuestion(raw) {
    var text = String(raw || '').trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '');
    var start = text.indexOf('{'), end = text.lastIndexOf('}');
    if (start >= 0 && end > start) text = text.slice(start, end + 1);
    var value;
    try { value = JSON.parse(text); } catch (_) { throw new Error('무료 AI 응답을 문제 형식으로 해석하지 못했습니다. 다시 시도해 주세요.'); }
    if (value.question && typeof value.question === 'object') value = value.question;
    ['type', 'instruction', 'passage', 'answer', 'explanation', 'sourceQuestionId'].forEach(function (key) {
      if (value[key] == null) throw new Error('무료 AI 응답에 ' + key + ' 항목이 없습니다. 다시 시도해 주세요.');
    });
    if (!Array.isArray(value.choices)) value.choices = [];
    return value;
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
    if (!readUser()) { alert('저장하려면 로그인해 주세요.'); if (typeof openLoginModal === 'function') openLoginModal(); return; }
    els.save.disabled = true;
    setStatus('변형문제를 저장하는 중입니다.');
    var source = contextPack && contextPack.currentQuestion ? contextPack.currentQuestion : {};
    var mode = generationMode || preview.generationMode || 'UNKNOWN';
    var provenance = (mode === 'OPENROUTER_FREE' ? 'OpenRouter 무료 AI 생성' : 'Chrome built-in AI 생성')
      + ' · sourceQuestionWikiId=' + questionWikiId + ' · generationMode=' + mode
      + ' · requestedType=' + selectedPreset() + ' · schoolPatternReferenceUsed=' + String(!!els.pattern.value.trim());
    try {
      var response = await fetchAuth('/api/wiki/questions/' + encodeURIComponent(questionWikiId) + '/posts', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, redirectOnAuthError: false,
        body: JSON.stringify({
          category: 'VARIANT', dataKind: 'GENERATED_PRACTICE',
          title: mode === 'OPENROUTER_FREE' ? '무료 AI 변형문제' : 'Chrome AI 변형문제', content: provenance,
          variant: { questionText: questionText(preview), questionType: preview.type, answer: textOf(preview.answer),
            explanation: preview.explanationKo || preview.explanation, sourceSentence: '',
            sourceQuestion: source.displayContext || ((source.questionNumber || '') + '번'), difficulty: els.freeRequest.value.trim() }
        })
      });
      if (response.status === 401 || response.status === 403) { if (typeof openLoginModal === 'function') openLoginModal(); setStatus('저장하려면 로그인해 주세요.'); return; }
      if (!response.ok) throw new Error((await response.text()) || '저장하지 못했습니다.');
      setStatus('현재 문항의 변형문제 영역에 저장했습니다.');
      window.dispatchEvent(new CustomEvent('questionwiki:reload'));
    } catch (error) { setStatus(error && error.message ? error.message : '저장하지 못했습니다.'); }
    finally { els.save.disabled = false; }
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
  function esc(value) { return String(value == null ? '' : value).replace(/[&<>'"]/g, function (char) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]; }); }
})();
