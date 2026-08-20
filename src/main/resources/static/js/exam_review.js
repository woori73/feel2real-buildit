let currentUser = null;
let sourceId    = null;
let returnUrl   = '/stdt_dashboard';
/** 채점 결과: questionNo → true / false / null */
const answerState = new Map();
/** 배점: questionNo → 정수 (0 이상). 0이면 점수 합산에서 제외. */
const pointState = new Map();

async function init() {
  // sessionStorage 비어도 JWT 쿠키로 복원 (탭/브라우저 재시작 대응)
  currentUser = await ensureCurrentUser();
  if (!currentUser) { location.href = '/home'; return; }
  document.getElementById('userNameDisplay').textContent = `${window.userDisplayName(currentUser)}님`;

  const params = new URLSearchParams(location.search);
  sourceId = Number(params.get('id'));
  returnUrl = resolveReturnUrl(params.get('returnUrl'));
  if (!sourceId) {
    showError('잘못된 접근입니다 (source id 누락).');
    return;
  }

  await loadReview();
  document.getElementById('reviewForm').addEventListener('submit', onSubmit);
  document.getElementById('btnCancel').addEventListener('click', goBack);
  document.getElementById('backLink').addEventListener('click', (e) => {
    e.preventDefault();
    goBack();
  });
}

async function loadReview() {
  try {
    const res = await fetchAuth(`/api/sources/${sourceId}/exam-review`);
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `${res.status}`);
    }
    const data = await res.json();
    renderReview(data);
  } catch (e) {
    showError('분석 결과를 불러오지 못했어요: ' + e.message);
  }
}

function renderReview(data) {
  document.getElementById('loadingMsg').hidden = true;
  document.getElementById('reviewForm').hidden = false;

  document.getElementById('fileName').textContent = data.originalFileName || '(이름 없음)';

  const meta = data.analysisResult.meta || {};
  setValue('schoolName',   meta.school_name);
  setValue('grade',        meta.grade);
  setValue('examType',     meta.exam_type);
  setValue('examYear',     meta.exam_year);
  setValue('examSemester', meta.exam_semester);

  const questions = data.analysisResult.questions || [];
  answerState.clear();
  pointState.clear();
  for (const q of questions) {
    answerState.set(q.question_no, q.is_correct ?? null);
    pointState.set(q.question_no, normalizePoint(q.point));
  }
  renderAnswers(questions);
  updateScoreSummary();
}

/** 외부 입력을 0 이상의 정수로 정규화. 잘못된 값/음수/NaN은 0. */
function normalizePoint(v) {
  const n = Math.floor(Number(v));
  return Number.isFinite(n) && n >= 0 ? n : 0;
}

function setValue(elId, value) {
  const el = document.getElementById(elId);
  if (!el) return;
  el.value = value == null ? '' : value;
}

function renderAnswers(questions) {
  const listEl = document.getElementById('answersList');
  listEl.innerHTML = questions.map(q => answerChip(q)).join('');
  for (const q of questions) {
    const root = document.querySelector(`[data-qno="${q.question_no}"]`);
    // ◯/✕/? 토글은 mark 부분 클릭 시에만 — 배점 input 클릭과 충돌 방지
    root.querySelector('.answer-mark-btn')
      .addEventListener('click', () => toggleAnswer(q.question_no));
    const pointInput = root.querySelector('.answer-point-input');
    pointInput.addEventListener('input', () => onPointChange(q.question_no, pointInput.value));
  }
}

function answerChip(q) {
  const state = answerState.get(q.question_no);
  const cls   = state === true ? 'correct' : state === false ? 'incorrect' : 'unknown';
  const mark  = state === true ? '✓' : state === false ? '✗' : '?';
  // 시험지에 적힌 라벨 그대로 표시. 라벨이 없으면 "N번"으로 fallback.
  const label = q.question_label || `${q.question_no}번`;
  const point = pointState.get(q.question_no) ?? 0;
  return `
    <div class="answer-chip ${cls}" data-qno="${q.question_no}">
      <span class="answer-no">${escapeHtml(label)}</span>
      <span class="answer-mark-btn" role="button" tabindex="0" title="정답/오답 토글">
        <span class="answer-mark">${mark}</span>
      </span>
      <label class="answer-point">
        <input class="answer-point-input" type="number" min="0" max="100" step="1" value="${point}">
        <span class="answer-point-label">점</span>
      </label>
    </div>`;
}

/** XSS 방지 — 라벨이 시험지 텍스트에서 추출돼 신뢰 불가 */
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  }[c]));
}

/** 정답 ↔ 오답 ↔ 모름(null) 순환 */
function toggleAnswer(questionNo) {
  const cur  = answerState.get(questionNo);
  const next = cur === true ? false : cur === false ? null : true;
  answerState.set(questionNo, next);

  const el = document.querySelector(`[data-qno="${questionNo}"]`);
  el.classList.remove('correct', 'incorrect', 'unknown');
  el.classList.add(next === true ? 'correct' : next === false ? 'incorrect' : 'unknown');
  el.querySelector('.answer-mark').textContent =
    next === true ? '✓' : next === false ? '✗' : '?';

  updateScoreSummary();
}

function onPointChange(questionNo, rawValue) {
  pointState.set(questionNo, normalizePoint(rawValue));
  updateScoreSummary();
}

function updateScoreSummary() {
  let total = 0, correct = 0, totalPoints = 0, earnedPoints = 0;
  for (const [qno, v] of answerState.entries()) {
    total++;
    const p = pointState.get(qno) ?? 0;
    totalPoints += p;
    if (v === true) {
      correct++;
      earnedPoints += p;
    }
  }
  const accuracyText = total ? `${correct} / ${total}` : '';
  const scoreText    = totalPoints > 0 ? ` · ${earnedPoints} / ${totalPoints}점` : '';
  document.getElementById('scoreSummary').textContent = accuracyText + scoreText;
}

async function onSubmit(e) {
  e.preventDefault();
  const btn = document.getElementById('submitBtn');
  btn.disabled = true;
  btn.textContent = '확정 중...';

  // questionNo의 합집합 (정/오답이 토글된 적 있는 것 + 배점이 들어와 있는 것)
  const questionNos = new Set([...answerState.keys(), ...pointState.keys()]);
  const answers = [];
  for (const questionNo of questionNos) {
    const isCorrect = answerState.get(questionNo) ?? null;
    const point     = pointState.get(questionNo) ?? null;
    if (isCorrect === null && point === null) continue;
    answers.push({ questionNo, isCorrect, point });
  }

  const body = {
    meta: {
      schoolName:   strOrNull('schoolName'),
      grade:        intOrNull('grade'),
      examType:     strOrNull('examType'),
      examYear:     intOrNull('examYear'),
      examSemester: intOrNull('examSemester')
    },
    answers
  };

  try {
    const res = await fetchAuth(
      `/api/sources/${sourceId}/exam-confirm`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `${res.status}`);
    }
    const result = await res.json();
    // 점수가 있으면 함께 표시 — UI summary에서 합산해 둔 값을 그대로 사용
    let total = 0, totalPoints = 0, earnedPoints = 0;
    for (const [qno, v] of answerState.entries()) {
      total++;
      const p = pointState.get(qno) ?? 0;
      totalPoints += p;
      if (v === true) earnedPoints += p;
    }
    const accuracy = `${result.correctCount} / ${result.totalQuestions}`;
    const scoreLine = totalPoints > 0 ? `\n절대점수: ${earnedPoints} / ${totalPoints}점` : '';
    alert(`확정 완료!\n정답률: ${accuracy}${scoreLine}`
      + (result.dedupMatched ? '\n(기존 시험과 매칭되어 재사용)' : ''));
    goBack();
  } catch (err) {
    alert('확정에 실패했어요: ' + err.message);
    btn.disabled = false;
    btn.textContent = '전체 확정';
  }
}

function strOrNull(id) {
  const v = document.getElementById(id).value.trim();
  return v === '' ? null : v;
}

function intOrNull(id) {
  const v = document.getElementById(id).value.trim();
  if (v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function showError(msg) {
  document.getElementById('loadingMsg').hidden = true;
  const el = document.getElementById('errorMsg');
  el.hidden = false;
  el.textContent = msg;
}

function goBack() {
  location.href = returnUrl;
}

function resolveReturnUrl(rawReturnUrl) {
  const fromQuery = normalizeInternalUrl(rawReturnUrl);
  if (fromQuery) return fromQuery;

  const fromReferrer = normalizeInternalUrl(document.referrer);
  if (fromReferrer) return fromReferrer;

  return '/stdt_dashboard';
}

function normalizeInternalUrl(rawUrl) {
  if (!rawUrl) return null;
  try {
    const url = new URL(rawUrl, location.origin);
    if (url.origin !== location.origin) return null;
    if (url.pathname === location.pathname) return null;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch (_) {
    return null;
  }
}

init();
