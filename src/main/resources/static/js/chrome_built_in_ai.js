/** Shared browser adapter for Chrome Prompt API + Translator API. */
(function (root) {
  'use strict';
  var PRESETS = {
    similar: 'Create one similar but meaningfully transformed English exam question.',
    grammar: 'Create one five-choice English grammar question based on the source passage.',
    vocabulary: 'Create one five-choice English vocabulary-in-context question using vocabulary from the source passage.',
    blank: 'Create one five-choice blank-completion question by blanking a meaningful idea-bearing part of the source passage.',
    order: 'Create one five-choice paragraph-order question by rearranging parts of the source passage.',
    insertion: 'Create one five-choice sentence-insertion question using a sentence and positions from the source passage.',
    'title-topic': 'Create one five-choice title or main-topic question about the source passage.',
    subjective: 'Create one constructed-response question based on the source passage with explicit answer conditions.'
  };
  var MULTIPLE_CHOICE = ['similar', 'grammar', 'vocabulary', 'blank', 'order', 'insertion', 'title-topic'];
  var HANGUL = /[\u3131-\u318e\uac00-\ud7a3]/;
  var FAILURE_TEXT = /(source context lacks|no (?:actual )?(?:english )?passage|no passage (?:was )?provided|cannot generate|unable to generate|insufficient source|metadata only|원문.{0,12}(?:없|부족)|지문.{0,12}(?:없|부족))/i;
  var CHOICE_PREFIX = /^\s*(?:[①②③④⑤]|\(?[1-5]\)?[.)]?|[1-5]\s*[·:-])\s*/;

  function normalizeStatus(value) { return ['available', 'downloadable', 'downloading', 'unavailable'].indexOf(value) >= 0 ? value : 'error'; }
  async function available(call) { if (!call) return 'unavailable'; try { return normalizeStatus(await call()); } catch (_) { return 'error'; } }
  async function getRuntimeStatus(apis) {
    apis = apis || root;
    var promptOptions = { expectedInputs: [{ type: 'text', languages: ['en'] }], expectedOutputs: [{ type: 'text', languages: ['en'] }] };
    var values = await Promise.all([
      available(apis.LanguageModel && function () { return apis.LanguageModel.availability(promptOptions); }),
      available(apis.Translator && function () { return apis.Translator.availability({ sourceLanguage: 'ko', targetLanguage: 'en' }); }),
      available(apis.Translator && function () { return apis.Translator.availability({ sourceLanguage: 'en', targetLanguage: 'ko' }); })
    ]);
    return { prompt: values[0], koToEn: values[1], enToKo: values[2] };
  }
  function monitor(name, callback) { if (!callback) return undefined; return function (target) { target.addEventListener('downloadprogress', function (event) { callback(name, Number(event.loaded || 0)); }); }; }
  async function translate(api, sourceLanguage, targetLanguage, text, name, callback) {
    var translator = await api.create({ sourceLanguage: sourceLanguage, targetLanguage: targetLanguage, monitor: monitor(name, callback) });
    try { return await translator.translate(text); } finally { if (translator.destroy) translator.destroy(); }
  }
  function parseJson(raw) {
    var cleaned = String(raw || '').trim().replace(/^\`\`\`(?:json)?\s*/i, '').replace(/\s*\`\`\`$/, '');
    var start = cleaned.indexOf('{'), end = cleaned.lastIndexOf('}');
    if (start < 0 || end <= start) throw new Error('AI가 유효한 JSON 문항을 반환하지 않았습니다.');
    return JSON.parse(cleaned.slice(start, end + 1));
  }
  function cleanText(value) { return String(value == null ? '' : value).replace(/\s+/g, ' ').trim(); }
  function normalizeChoice(choice) {
    if (choice && typeof choice === 'object') {
      var copy = Object.assign({}, choice);
      if (copy.text != null) copy.text = cleanText(copy.text).replace(CHOICE_PREFIX, '');
      else if (copy.html != null) copy.html = cleanText(copy.html).replace(CHOICE_PREFIX, '');
      return copy;
    }
    return cleanText(choice).replace(CHOICE_PREFIX, '');
  }
  function normalizeQuestion(question) {
    if (!question || typeof question !== 'object') return question;
    if (Array.isArray(question.choices)) question.choices = question.choices.map(normalizeChoice);
    return question;
  }
  function buildCanonicalContext(rawContext, sourceQuestionId) {
    rawContext = rawContext || {}; var q = rawContext.question || {};
    var documents = (rawContext.materials || []).filter(function (m) { return cleanText(m.aiText); }).map(function (m) {
      return { linkId: m.linkId, label: m.label || m.kindLabel || '', kindLabel: m.kindLabel || '', title: m.title || m.fileName || '', text: cleanText(m.aiText) };
    });
    var sourceText = documents.map(function (d) { return '[' + d.label + ' · ' + d.title + ']\n' + d.text; }).join('\n\n').slice(0, 60000);
    return {
      currentQuestion: {
        questionWikiId: Number(sourceQuestionId), subject: q.subject || '', grade: q.grade, examYear: q.examYear,
        examLabel: q.examLabel || '', questionNumber: q.questionNumber, title: q.title || '', displayContext: q.displayContext || '',
        passage: sourceText, sourceText: sourceText, originalInstruction: '', originalChoices: [], originalAnswer: '', originalExplanation: '',
        sourceDocuments: documents.map(function (d) { return { linkId: d.linkId, label: d.label, kindLabel: d.kindLabel, title: d.title }; })
      },
      referenceContext: {
        sourceAnalysis: documents.map(function (d) { return { label: d.label, kindLabel: d.kindLabel, title: d.title, text: d.text }; }),
        threads: rawContext.threads || [], variants: rawContext.variants || [], examEvidence: rawContext.examEvidence || [], aiSummary: rawContext.aiSummary || null
      }
    };
  }
  function sourcePassage(context) { return cleanText(context && context.currentQuestion && context.currentQuestion.passage); }
  function assertUsableContext(context) {
    var passage = sourcePassage(context); var englishWords = passage.match(/[A-Za-z][A-Za-z'-]{2,}/g) || [];
    if (!passage || englishWords.length < 12) throw new Error('현재 문항의 원문 지문을 불러오지 못했습니다. 문항 데이터를 확인한 후 다시 시도해 주세요.');
    return passage;
  }
  function buildPrompt(request, freeRequest, schoolPatternReference) {
    freeRequest = freeRequest == null ? (request.freeRequest || '') : freeRequest;
    schoolPatternReference = schoolPatternReference == null ? (request.schoolPatternReference || '') : schoolPatternReference;
    var context = request.contextPack; assertUsableContext(context); var cloud = !!request.cloudMode;
    return [
      'Generate exactly one English exam question from CURRENT_QUESTION.', PRESETS[request.preset] || PRESETS.similar,
      'The output type MUST be exactly "' + request.preset + '".', 'The output sourceQuestionId MUST be exactly ' + Number(request.sourceQuestionId) + '.',
      freeRequest ? 'Additional user instruction: ' + freeRequest : '', '',
      'MANDATORY REASONING BEFORE OUTPUT (do this internally; do not add extra JSON keys):',
      '1. Read CURRENT_QUESTION and REFERENCE_CONTEXT.sourceAnalysis together.',
      '2. Identify the passage core claim, contrast/cause-flow, and the strongest exam-worthy point described or implied by the linked analysis.',
      '3. Choose the target for the selected type from that exam-worthy point, not merely the first convenient word or sentence.',
      '4. Build distractors from the passage logic and likely learner misconceptions. Choices must be mutually discriminating, not a loose list of near-synonyms.',
      '5. Verify that the finished question still tests comprehension of THIS question and that the Korean explanation states why the answer follows from the source logic.',
      '', 'SOURCE AND TYPE RULES:',
      '- CURRENT_QUESTION.passage is the only content source. Preserve its topic, people, facts, and logical content.',
      '- REFERENCE_CONTEXT is mandatory ANALYSIS/STRATEGY context: use it to decide what to test and how to construct distractors, but never copy it as replacement passage content.',
      '- Do not replace CURRENT_QUESTION with a newly invented passage or unrelated subject. Make only the transformation required by the selected type.',
      '- For blank, blank a key claim/contrast/cause-effect expression or idea-bearing phrase. Avoid trivial blanks whose answer is obvious without understanding surrounding logic.',
      '- For vocabulary and grammar, target an exam-worthy word/grammar point in CURRENT_QUESTION and make all five choices plausible in form.',
      '- For order and insertion, construct the mechanics from the source discourse flow and connective logic.',
      '- For title-topic, make distractors reflect partial/misread interpretations of the actual source.',
      '- SCHOOL_PATTERN_REFERENCE is PATTERN REFERENCE ONLY: mechanics, instruction shape, choice layout, answer format, word limits, and difficulty only.',
      '- Never copy its passage, topic, names, facts, answer, or choice content.',
      '- Never put choice numbers such as 1., (1), ① in choices values. choices must contain text only; the renderer supplies ①②③④⑤.',
      '- Never return a refusal, missing-context explanation, or metadata commentary as a question.',
      '- Return JSON only with keys type,instruction,passage,choices,answer,explanation,sourceQuestionId,generationMode.',
      cloud ? '- Write the instruction and explanation in Korean. Keep the English passage and English choices in English.' : '- Write the generated content and explanation in English; local translation handles the Korean explanation.',
      '', 'CURRENT_QUESTION:', JSON.stringify(context.currentQuestion), '',
      'REFERENCE_CONTEXT (MANDATORY supporting analysis/strategy; never a replacement passage):', JSON.stringify(context.referenceContext), '',
      'SCHOOL_PATTERN_REFERENCE (PATTERN ONLY):', schoolPatternReference || '(none)'
    ].filter(function (line, index, all) { return line !== '' || (index > 0 && all[index - 1] !== ''); }).join('\n');
  }
  function englishTokens(text) { var words = String(text || '').toLowerCase().match(/[a-z][a-z'-]{3,}/g) || []; return Array.from(new Set(words)); }
  function hasSourceOverlap(source, generated) {
    var src = englishTokens(source), out = new Set(englishTokens(generated)); if (src.length < 3) return false;
    var matches = src.filter(function (word) { return out.has(word); }).length; return matches >= 3 && matches / Math.min(src.length, 80) >= 0.035;
  }
  function answerInRange(answer, choices) {
    var text = cleanText(answer); var numeric = text.match(/(?:^|\D)([1-5])(?:\D|$)/); if (numeric) return Number(numeric[1]) <= choices.length;
    var circled = ['①', '②', '③', '④', '⑤'].indexOf(text.charAt(0)); if (circled >= 0) return circled < choices.length;
    return choices.some(function (choice) { var c = cleanText(typeof choice === 'object' ? (choice.text || choice.html || '') : choice); return c && (c === text || text.indexOf(c) >= 0); });
  }
  function validateQuestion(question, request) {
    var generic = 'AI가 올바른 문제 형식을 생성하지 못했습니다. 다시 생성해 주세요.'; question = normalizeQuestion(question);
    if (!question || typeof question !== 'object') throw new Error(generic);
    if (Number(question.sourceQuestionId) !== Number(request.sourceQuestionId)) throw new Error(generic);
    if (String(question.type || '') !== String(request.preset)) throw new Error(generic);
    var passage = cleanText(question.passage); var source = assertUsableContext(request.contextPack);
    var combined = [question.instruction, passage, question.explanation].concat(question.choices || []).join(' ');
    if (!passage || !cleanText(question.explanation) || FAILURE_TEXT.test(combined)) throw new Error(generic);
    if (!hasSourceOverlap(source, passage)) throw new Error('AI 생성 결과가 현재 문항 원문과 관련되지 않아 Preview에 표시하지 않았습니다. 다시 생성해 주세요.');
    if (MULTIPLE_CHOICE.indexOf(request.preset) >= 0) {
      if (!Array.isArray(question.choices) || question.choices.length !== 5 || question.choices.some(function (c) { return !cleanText(typeof c === 'object' ? (c.text || c.html || '') : c); }) || !answerInRange(question.answer, question.choices)) throw new Error(generic);
    }
    if (request.preset === 'blank' && !/(?:_{3,}|\[\s*blank\s*\]|\(\s*blank\s*\)|…{2,}|□{2,})/i.test(passage)) throw new Error(generic);
    return question;
  }
  async function generate(request, apis) {
    apis = apis || root; assertUsableContext(request.contextPack); var runtime = await getRuntimeStatus(apis);
    if (!apis.LanguageModel || runtime.prompt === 'unavailable' || runtime.prompt === 'error') throw new Error('이 Chrome 기기에서는 Prompt AI를 사용할 수 없습니다. 기존 Wiki 기능은 그대로 사용할 수 있습니다.');
    var needsRequestTranslation = !!(request.freeRequest && HANGUL.test(request.freeRequest));
    var needsPatternTranslation = !!(request.schoolPatternReference && HANGUL.test(request.schoolPatternReference));
    if ((needsRequestTranslation || needsPatternTranslation) && (!apis.Translator || runtime.koToEn === 'unavailable' || runtime.koToEn === 'error')) throw new Error('현재 Chrome 기기 내 번역 기능에서 한국어 요청을 영어로 변환할 수 없습니다. 기본 유형 또는 영어 요청은 계속 사용할 수 있습니다.');
    var freeRequest = needsRequestTranslation ? await translate(apis.Translator, 'ko', 'en', request.freeRequest, 'koToEn', request.onDownloadProgress) : (request.freeRequest || '');
    var schoolPattern = needsPatternTranslation ? await translate(apis.Translator, 'ko', 'en', request.schoolPatternReference, 'koToEn', request.onDownloadProgress) : (request.schoolPatternReference || '');
    var promptOptions = { expectedInputs: [{ type: 'text', languages: ['en'] }], expectedOutputs: [{ type: 'text', languages: ['en'] }], monitor: monitor('prompt', request.onDownloadProgress) };
    var session = await apis.LanguageModel.create(promptOptions), payload;
    try { payload = parseJson(await session.prompt(buildPrompt(request, freeRequest, schoolPattern))); } finally { if (session.destroy) session.destroy(); }
    payload = validateQuestion(payload, request);
    var explanation = String(payload.explanation || ''), explanationKo, notice;
    if (explanation && apis.Translator && runtime.enToKo !== 'unavailable' && runtime.enToKo !== 'error') explanationKo = await translate(apis.Translator, 'en', 'ko', explanation, 'enToKo', request.onDownloadProgress);
    else notice = '현재 Chrome 기기 내 번역 기능에서 한국어 번역을 사용할 수 없어 영문 해설로 표시합니다.';
    return { question: { type: payload.type, instruction: String(payload.instruction || ''), passage: payload.passage, choices: payload.choices, answer: payload.answer,
      explanation: explanation, explanationKo: explanationKo, sourceQuestionId: Number(payload.sourceQuestionId), generationMode: 'CHROME_BUILT_IN_AI' }, notice: notice };
  }
  root.ChromeBuiltInQuestionAi = { PRESETS: PRESETS, getRuntimeStatus: getRuntimeStatus, buildCanonicalContext: buildCanonicalContext,
    assertUsableContext: assertUsableContext, buildPrompt: buildPrompt, parseJson: parseJson, validateQuestion: validateQuestion, generate: generate };
})(typeof globalThis !== 'undefined' ? globalThis : window);