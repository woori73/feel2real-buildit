(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const exam = $('ph-vocabulary');
  if (!exam) return;

  const confirmChoice = exam.dataset.confirmChoice === 'true';
  const circle = ['①', '②', '③', '④', '⑤'];
  const body = $('ph-vocab-body');
  const status = $('ph-vocab-status');
  const timer = $('ph-vocab-timer');
  const escaped = NT.esc;
  let state, deadline, pending = false, retryAt = 0, wrongOnly = false;

  function renderItem(item) {
    const instruction = item.cloze ? '빈칸에 들어갈 표현을 고르세요.' : '영어 단어의 뜻을 고르세요.';
    body.innerHTML = '<section class="nt-page"><p class="ph-grammar-instruction">' + instruction
      + '</p><h2 class="ph-grammar-sentence" lang="en">' + escaped(item.prompt) + '</h2>'
      + '<div class="ph-grammar-options">' + item.choices.map((choice, i) =>
        '<button type="button" class="nt-btn nt-btn-line" data-choice="' + (i + 1) + '"'
        + (confirmChoice ? ' aria-pressed="' + (item.selected === i + 1) + '"' : '')
        + (item.cloze ? ' lang="en"' : '') + '>' + circle[i] + ' ' + escaped(choice) + '</button>').join('')
      + '</div>' + (confirmChoice ? '<button type="button" class="nt-btn ph-vocab-next" data-action="next"'
        + (item.selected ? '' : ' disabled') + '>' + (item.number === state.count ? '채점하기' : '다음') + '</button>' : '')
      + '</section>';
    body.querySelector('[data-choice]')?.focus({ preventScroll: true });
  }

  function renderReview() {
    const wrong = state.results.filter(r => r.chosen !== r.question.answer);
    const omissions = wrong.filter(r => r.chosen === 0).length;
    const shown = wrongOnly ? wrong : state.results;
    const details = shown.map(({ number, chosen, question, completed }, i) => {
      const correctChoice = question.answer - 1;
      const verdict = chosen === question.answer ? '정답' : chosen ? '오답' : '미응답';
      const response = chosen ? '내 답 ' + circle[chosen - 1] + ' ' + escaped(question.choices[chosen - 1]) : '내 답 미응답';
      const translation = completed ? '<p><strong>완성 문장</strong><span lang="en">' + escaped(completed)
        + '</span></p><p><strong>문장 번역</strong>' + escaped(question.translation) + '</p>' : '';
      return '<details class="nt-exp"' + (i === 0 ? ' open' : '') + '><summary>' + number + '번 · '
        + escaped(question.term) + ' · ' + verdict + '</summary><div class="ph-explanation">'
        + '<p>' + response + '</p><p><strong>정답</strong>' + circle[correctChoice] + ' '
        + escaped(question.choices[correctChoice]) + '</p><p><strong>'
        + (question.idiom ? '숙어 뜻' : '단어 뜻') + '</strong>' + escaped(question.meaning) + '</p>'
        + translation + '</div></details>';
    }).join('');
    body.innerHTML = '<section class="nt-page nt-score"><div class="nt-score-pen"><b>' + state.score
      + '</b><span>/ 100</span></div><div class="nt-score-text"><h2 class="nt-score-big" tabindex="-1">채점 결과</h2>'
      + '<p>정답 ' + (state.count - wrong.length) + '개 · 오답 ' + (wrong.length - omissions) + '개 · 미응답 '
      + omissions + '개</p></div></section><div class="nt-page nt-review-actions">'
      + '<button type="button" class="nt-btn nt-btn-line" data-action="filter">'
      + (wrongOnly ? '전체 문항 보기' : '오답·미응답 보기') + '</button>'
      + '<button type="button" class="nt-btn" data-action="restart">다시 풀기</button></div>'
      + '<section class="nt-page"><h2>문항별 해설</h2>' + (details || '<p>오답과 미응답이 없습니다.</p>') + '</section>';
  }

  function tick() {
    if (!state?.item) return;
    const seconds = Math.min(state.item.seconds, Math.max(0, Math.ceil((deadline - performance.now()) / 1000)));
    timer.textContent = seconds + '초';
    $('nt-timer-box').classList.toggle('is-low', seconds <= 2);
    if (seconds === 0 && !pending && performance.now() >= retryAt) send('state');
  }

  async function send(action, choice = 0) {
    if (pending) return;
    pending = true;
    const previous = state?.item?.number;
    try {
      state = await NT.api(exam.dataset.api, {
        method: 'POST',
        body: JSON.stringify({ action, attempt: state?.id, number: previous ?? 0, choice })
      });
      retryAt = 0;
      status.hidden = true;
      exam.classList.toggle('ph-timed', Boolean(state.item));
      if (state.item) {
        const item = state.item;
        deadline = performance.now() + state.remainingMillis;
        $('ph-vocab-progress').textContent = item.number + ' / ' + state.count;
        $('ph-vocab-kind').textContent = item.cloze ? '문장 빈칸' : '뜻 고르기';
        if (previous !== item.number || action === 'start' || action === 'restart') renderItem(item);
        else if (confirmChoice) {
          body.querySelectorAll('[data-choice]').forEach(button =>
            button.setAttribute('aria-pressed', String(Number(button.dataset.choice) === item.selected)));
          body.querySelector('[data-action="next"]').disabled = !item.selected;
        }
        tick();
      } else {
        wrongOnly = false;
        $('ph-vocab-progress').textContent = '총 ' + state.count + '문항';
        $('ph-vocab-kind').textContent = '시험 완료';
        $('nt-timer-box').classList.remove('is-low');
        timer.textContent = '—';
        renderReview();
        body.querySelector('.nt-score-big')?.focus();
      }
    } catch (error) {
      retryAt = performance.now() + 1200;
      if (error.status === 409) state = undefined;
      status.textContent = error.status === 409
        ? '응시 상태가 변경되었습니다. 페이지를 새로고침한 뒤 이어 풀어 주세요.'
        : '연결을 확인해 주세요. 같은 화면에서 다시 시도할 수 있습니다.';
      status.hidden = false;
    } finally {
      pending = false;
    }
  }

  body.addEventListener('click', event => {
    const button = event.target.closest('button');
    if (!button || pending) return;
    if (button.dataset.action === 'filter') {
      wrongOnly = !wrongOnly;
      renderReview();
      body.querySelector('[data-action="filter"]')?.focus();
    } else if (button.dataset.choice && state?.item) {
      send('answer', Number(button.dataset.choice));
    } else if (button.dataset.action) {
      send(button.dataset.action);
    }
  });
  document.addEventListener('keydown', event => {
    if (state?.item && !event.repeat && !event.ctrlKey && !event.metaKey && !event.altKey && /^[1-5]$/.test(event.key)) {
      event.preventDefault();
      send('answer', Number(event.key));
    }
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });
  window.setInterval(tick, 80);
})();
