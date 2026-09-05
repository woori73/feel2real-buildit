/**
 * Shared browser adapter for Chrome Prompt API + Translator API.
 * Mirrors problem-generator/wiki-src/src/lib/chromeBuiltInAi.ts without pulling
 * the React bundle into the server-rendered Question Wiki.
 */
(function (root) {
  'use strict';

  var PRESETS = {
    similar: 'Create one similar but meaningfully transformed English exam question.',
    grammar: 'Create one five-choice English grammar question.',
    vocabulary: 'Create one five-choice English vocabulary-in-context question.',
    blank: 'Create one five-choice English blank-completion question.',
    order: 'Create one five-choice paragraph-order question.',
    insertion: 'Create one five-choice sentence-insertion question.',
    'title-topic': 'Create one five-choice English title or main-topic question.',
    subjective: 'Create one constructed-response English question with explicit answer conditions.'
  };
  var HANGUL = /[\u3131-\u318e\uac00-\ud7a3]/;

  function normalizeStatus(value) {
    return ['available', 'downloadable', 'downloading', 'unavailable'].indexOf(value) >= 0 ? value : 'error';
  }

  async function available(call) {
    if (!call) return 'unavailable';
    try { return normalizeStatus(await call()); } catch (_) { return 'error'; }
  }

  async function getRuntimeStatus(apis) {
    apis = apis || root;
    var promptOptions = {
      expectedInputs: [{ type: 'text', languages: ['en'] }],
      expectedOutputs: [{ type: 'text', languages: ['en'] }]
    };
    var values = await Promise.all([
      available(apis.LanguageModel && function () { return apis.LanguageModel.availability(promptOptions); }),
      available(apis.Translator && function () { return apis.Translator.availability({ sourceLanguage: 'ko', targetLanguage: 'en' }); }),
      available(apis.Translator && function () { return apis.Translator.availability({ sourceLanguage: 'en', targetLanguage: 'ko' }); })
    ]);
    return { prompt: values[0], koToEn: values[1], enToKo: values[2] };
  }

  function monitor(name, callback) {
    if (!callback) return undefined;
    return function (target) {
      target.addEventListener('downloadprogress', function (event) {
        callback(name, Number(event.loaded || 0));
      });
    };
  }

  async function translate(api, sourceLanguage, targetLanguage, text, name, callback) {
    var translator = await api.create({
      sourceLanguage: sourceLanguage,
      targetLanguage: targetLanguage,
      monitor: monitor(name, callback)
    });
    try { return await translator.translate(text); }
    finally { if (translator.destroy) translator.destroy(); }
  }

  function parseJson(raw) {
    var cleaned = String(raw || '').trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '');
    var start = cleaned.indexOf('{'), end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) throw new Error('Chrome AI가 유효한 JSON 문항을 반환하지 않았습니다.');
    return JSON.parse(cleaned.slice(start, end + 1));
  }

  function buildPrompt(request, freeRequest, schoolPatternReference) {
    return [
      'You are generating exactly one English exam question from CURRENT_WIKI_CONTEXT.',
      PRESETS[request.preset] || PRESETS.similar,
      freeRequest ? 'Additional user instruction: ' + freeRequest : '',
      '',
      'SECURITY AND SOURCE RULES:',
      '- CURRENT_WIKI_CONTEXT is the only content source. Do not import content, facts, passages, or subject matter from anywhere else.',
      '- SCHOOL_PATTERN_REFERENCE is PATTERN REFERENCE ONLY. Analyze only its instruction structure, item mechanics, choice layout, answer requirements, word limits, and response format.',
      '- Never copy or paraphrase the reference passage, topic, names, facts, answer, or choices into the new question.',
      '- Keep the source question unchanged. Return a new self-contained question only.',
      '- Return JSON only, with keys: type, instruction, passage, choices, answer, explanation.',
      '- Write explanation in English. Ensure passage and choices contain everything needed to solve the question.',
      '',
      'CURRENT_WIKI_CONTEXT:',
      JSON.stringify(request.contextPack),
      '',
      'SCHOOL_PATTERN_REFERENCE (PATTERN ONLY):',
      schoolPatternReference || '(none)'
    ].filter(function (line, index, all) { return line !== '' || (index > 0 && all[index - 1] !== ''); }).join('\n');
  }

  async function generate(request, apis) {
    apis = apis || root;
    var runtime = await getRuntimeStatus(apis);
    if (!apis.LanguageModel || runtime.prompt === 'unavailable' || runtime.prompt === 'error') {
      throw new Error('이 Chrome 기기에서는 Prompt AI를 사용할 수 없습니다. 기존 Wiki 기능은 그대로 사용할 수 있습니다.');
    }
    var needsRequestTranslation = !!(request.freeRequest && HANGUL.test(request.freeRequest));
    var needsPatternTranslation = !!(request.schoolPatternReference && HANGUL.test(request.schoolPatternReference));
    if ((needsRequestTranslation || needsPatternTranslation)
        && (!apis.Translator || runtime.koToEn === 'unavailable' || runtime.koToEn === 'error')) {
      throw new Error('현재 Chrome 기기 내 번역 기능에서 한국어 요청을 영어로 변환할 수 없습니다. 기본 유형 또는 영어 요청은 계속 사용할 수 있습니다.');
    }
    var freeRequest = needsRequestTranslation
      ? await translate(apis.Translator, 'ko', 'en', request.freeRequest, 'koToEn', request.onDownloadProgress)
      : (request.freeRequest || '');
    var schoolPattern = needsPatternTranslation
      ? await translate(apis.Translator, 'ko', 'en', request.schoolPatternReference, 'koToEn', request.onDownloadProgress)
      : (request.schoolPatternReference || '');
    var promptOptions = {
      expectedInputs: [{ type: 'text', languages: ['en'] }],
      expectedOutputs: [{ type: 'text', languages: ['en'] }],
      monitor: monitor('prompt', request.onDownloadProgress)
    };
    var session = await apis.LanguageModel.create(promptOptions);
    var payload;
    try { payload = parseJson(await session.prompt(buildPrompt(request, freeRequest, schoolPattern))); }
    finally { if (session.destroy) session.destroy(); }

    var explanation = String(payload.explanation || '');
    var explanationKo, notice;
    if (explanation && apis.Translator && runtime.enToKo !== 'unavailable' && runtime.enToKo !== 'error') {
      explanationKo = await translate(apis.Translator, 'en', 'ko', explanation, 'enToKo', request.onDownloadProgress);
    } else {
      notice = '현재 Chrome 기기 내 번역 기능에서 한국어 번역을 사용할 수 없어 영문 해설로 표시합니다.';
    }
    return {
      question: {
        type: String(payload.type || request.preset),
        instruction: String(payload.instruction || ''),
        passage: payload.passage || '',
        choices: Array.isArray(payload.choices) ? payload.choices : [],
        answer: payload.answer == null ? '' : payload.answer,
        explanation: explanation,
        explanationKo: explanationKo,
        sourceQuestionId: Number(request.sourceQuestionId),
        generationMode: 'CHROME_BUILT_IN_AI'
      },
      notice: notice
    };
  }

  root.ChromeBuiltInQuestionAi = {
    PRESETS: PRESETS,
    getRuntimeStatus: getRuntimeStatus,
    buildPrompt: buildPrompt,
    generate: generate
  };
})(typeof globalThis !== 'undefined' ? globalThis : window);
