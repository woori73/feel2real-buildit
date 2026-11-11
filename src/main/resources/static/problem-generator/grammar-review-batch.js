(function (root) {
  'use strict';
  const formats = new Set(['MULTIPLE_CHOICE', 'CORRECT_SENTENCE', 'INCORRECT_SENTENCE', 'UNDERLINE', 'BLANK_FILL', 'SENTENCE_ORDER', 'WRITING', 'SENTENCE_TRANSFORM', 'ERROR_CORRECTION', 'OTHER']);
  const grades = new Set(['M1', 'M2', 'M3', 'H1', 'H2', 'H3', 'UNKNOWN']);
  const contentFields = ['questionText', 'choices', 'correctAnswer', 'explanation'];
  const profileFields = ['questionFormat', 'recommendedGrade'];
  const supportedFields = new Set([...contentFields, ...profileFields, 'status']);
  const equal = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const grade = value => value || 'UNKNOWN';

  async function fingerprint(question) {
    const data = JSON.stringify([question.questionText, question.choices || [], question.correctAnswer || '', question.explanation || '']);
    const bytes = await root.crypto.subtle.digest('SHA-256', new TextEncoder().encode(data));
    return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('');
  }

  function validatePlan(raw) {
    if (!Array.isArray(raw) || raw.length === 0 || raw.length > 10000) throw new Error('검수 결과 배열은 1~10,000건이어야 합니다.');
    const seen = new Set();
    for (const entry of raw) {
      if (!entry || typeof entry.externalRef !== 'string' || !entry.externalRef.trim() || seen.has(entry.externalRef)) throw new Error('외부 ID가 없거나 중복됩니다.');
      seen.add(entry.externalRef);
      if (!/^[0-9a-f]{64}$/.test(entry.expectedHash || '') || !/^[0-9a-f]{64}$/.test(entry.resultHash || '')) throw new Error(entry.externalRef + ' 원문 대조 해시가 필요합니다.');
      if (!['NEEDS_REVIEW', 'AUTO', 'APPROVED', 'REJECTED'].includes(entry.expectedStatus)) throw new Error(entry.externalRef + ' 기존 검수 상태가 필요합니다.');
      const patch = entry.patch;
      if (!patch || typeof patch !== 'object' || Array.isArray(patch) || Object.keys(patch).length === 0) throw new Error(entry.externalRef + ' 수정 항목이 없습니다.');
      if (Object.keys(patch).some(key => !supportedFields.has(key))) throw new Error(entry.externalRef + ' 지원하지 않는 수정 항목입니다.');
      if (patch.questionFormat !== undefined && !formats.has(patch.questionFormat)) throw new Error(entry.externalRef + ' 문항 형식이 잘못됐습니다.');
      if (patch.recommendedGrade !== undefined && !grades.has(patch.recommendedGrade)) throw new Error(entry.externalRef + ' 권장 학년이 잘못됐습니다.');
      if (patch.status !== undefined && !['APPROVED', 'REJECTED'].includes(patch.status)) throw new Error(entry.externalRef + ' 판정이 잘못됐습니다.');
      for (const key of ['questionText', 'correctAnswer', 'explanation']) {
        if (patch[key] !== undefined && typeof patch[key] !== 'string') throw new Error(entry.externalRef + ' 문항 내용은 문자열이어야 합니다.');
      }
      if (patch.questionText !== undefined && !patch.questionText.trim()) throw new Error(entry.externalRef + ' 발문이 비었습니다.');
      if (patch.correctAnswer !== undefined && (!patch.correctAnswer.trim() || patch.correctAnswer.length > 500)) throw new Error(entry.externalRef + ' 정답 길이를 확인해 주세요.');
      if (patch.choices !== undefined && (!Array.isArray(patch.choices) || patch.choices.some(value => typeof value !== 'string'))) throw new Error(entry.externalRef + ' 선택지는 문자열 배열이어야 합니다.');
    }
    return raw;
  }

  async function questionIndex(request, requiredIds) {
    const wanted = new Set(requiredIds);
    const index = new Map();
    for (const status of ['', 'REJECTED']) {
      let page = 0;
      let pageCount = 1;
      while (page < pageCount) {
        const response = await request('/questions?size=200&page=' + page + (status ? '&reviewStatus=' + status : ''));
        if (!Array.isArray(response.content) || !Number.isFinite(response.totalPages)) throw new Error('문항 목록 응답을 확인해 주세요.');
        for (const item of response.content) {
          if (!wanted.has(item.externalRef)) continue;
          const previous = index.get(item.externalRef);
          if (previous && previous !== item.questionId) throw new Error(item.externalRef + '가 여러 문항에 연결됐습니다.');
          index.set(item.externalRef, item.questionId);
        }
        pageCount = response.totalPages;
        page++;
      }
    }
    return index;
  }

  function targetMatches(question, patch) {
    return Object.entries(patch).every(([key, value]) => {
      if (key === 'status') return question.reviewStatus === value;
      if (key === 'recommendedGrade') return grade(question[key]) === value;
      return equal(question[key], value);
    });
  }

  async function applyOne(entry, questionId, request) {
    if (!questionId) throw new Error('외부 ID에 해당하는 문항이 없습니다.');
    let question = await request('/questions/' + questionId);
    if (question.externalRef !== entry.externalRef) throw new Error('문항의 외부 ID가 바뀌었습니다.');
    const hash = await fingerprint(question);
    if (hash !== entry.expectedHash && hash !== entry.resultHash) throw new Error('원문·정답·해설이 검수 이후 변경됐습니다.');
    const patch = entry.patch;
    if (targetMatches(question, patch) && hash === entry.resultHash) return { externalRef: entry.externalRef, questionId, outcome: 'UNCHANGED' };
    if (question.reviewStatus !== entry.expectedStatus && question.reviewStatus !== patch.status && question.reviewStatus !== 'NEEDS_REVIEW') throw new Error('검수 상태가 다른 작업에서 변경됐습니다.');
    for (const key of profileFields) {
      if (entry.expectedProfile && Object.hasOwn(entry.expectedProfile, key)) {
        const current = key === 'recommendedGrade' ? grade(question[key]) : question[key];
        if (!equal(current, entry.expectedProfile[key]) && !equal(current, patch[key])) throw new Error('분류가 다른 작업에서 변경됐습니다.');
      }
    }
    const content = {};
    for (const key of contentFields) if (Object.hasOwn(patch, key) && !equal(question[key], patch[key])) content[key] = patch[key];
    if (Object.keys(content).length) {
      question = await request('/questions/' + questionId, { method: 'PATCH', body: JSON.stringify(content) });
    }
    if (await fingerprint(question) !== entry.resultHash) throw new Error('수정 후 문항 내용이 검수 결과와 일치하지 않습니다.');
    const profile = {};
    for (const key of profileFields) {
      const current = key === 'recommendedGrade' ? grade(question[key]) : question[key];
      if (Object.hasOwn(patch, key) && !equal(current, patch[key])) profile[key] = patch[key];
    }
    if (Object.keys(profile).length) {
      question = await request('/questions/' + questionId + '/profile', { method: 'PATCH', body: JSON.stringify(profile) });
    }
    if (patch.status && question.reviewStatus !== patch.status) {
      question = await request('/questions/' + questionId + '/review', { method: 'POST', body: JSON.stringify({ status: patch.status }) });
    }
    if (!targetMatches(question, patch)) throw new Error('저장 결과가 검수 결과와 일치하지 않습니다.');
    return { externalRef: entry.externalRef, questionId, outcome: 'APPLIED', status: question.reviewStatus };
  }

  async function applyPlan(plan, request, onProgress, isStopped) {
    validatePlan(plan);
    const index = await questionIndex(request, plan.map(entry => entry.externalRef));
    const results = [];
    let next = 0;
    let blocked = false;
    async function worker() {
      while (next < plan.length && !blocked && !isStopped()) {
        const entry = plan[next++];
        let result;
        try {
          result = await applyOne(entry, index.get(entry.externalRef), request);
        } catch (error) {
          if (error.status === 401 || error.status === 403) blocked = true;
          result = { externalRef: entry.externalRef, questionId: index.get(entry.externalRef), outcome: 'FAILED', error: error.message };
        }
        results.push(result);
        onProgress(result, results.length);
      }
    }
    await Promise.all([worker(), worker(), worker()]);
    return results;
  }

  const api = { fingerprint, validatePlan, applyPlan };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GrammarBatchReview = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
