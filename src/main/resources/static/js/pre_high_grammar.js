(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const exam = $('ph-grammar');
  if (!exam) return;

  const circle = ['①', '②', '③', '④', '⑤'];
  const body = $('ph-grammar-body');
  const status = $('ph-grammar-status');
  const api = '/api/naesintest/pre-high/grammar/' + Number(exam.dataset.chapter);
  const maxSeconds = Number(exam.dataset.seconds);
  let current, deadline, pending = false, wrongOnly = true, retryAfter = 0;

  function content(question) {
    return '<p class="ph-grammar-instruction">' + NT.esc(question.instruction) + '</p>'
      + (question.text ? '<p class="ph-grammar-sentence">' + NT.esc(question.text) + '</p>' : '');
  }

  function renderQuestion() {
    const item = current.item;
    body.innerHTML = '<section class="nt-page"><div class="nt-qbar">'
      + '<span class="nt-qpos"><b>' + item.number + '</b> / ' + current.count + '</span></div>'
      + content(item) + '<div class="ph-grammar-options">'
      + item.choices.map((choice, i) => '<button class="nt-btn nt-btn-line" type="button" data-choice="' + (i + 1)
        + '">' + circle[i] + ' ' + NT.esc(choice) + '</button>').join('')
      + '</div></section>';
  }

  function renderReview() {
    const isCorrect = result => result.chosen === result.question.answer;
    const score = current.results.filter(isCorrect).length;
    const shown = wrongOnly ? current.results.filter(result => !isCorrect(result)) : current.results;
    const details = shown.map(({ number, chosen, question }, index) => {
      const verdict = chosen === question.answer ? '정답' : chosen ? '오답' : '미응답';
      return '<details class="nt-exp"' + (index === 0 ? ' open' : '') + '><summary>' + number
        + '번 · ' + NT.esc(question.topic) + ' · ' + verdict + '</summary>'
        + content(question)
        + '<p>내 답 ' + (circle[chosen - 1] || '미응답') + ' · 정답 ' + circle[question.answer - 1] + '</p>'
        + '<div class="ph-explanation"><p><strong>완성 문장</strong>' + NT.esc(question.completed) + '</p>'
        + '<p><strong>문장 해석</strong>' + NT.esc(question.translation) + '</p>'
        + '<p><strong>어법 원리</strong>' + NT.esc(question.principle) + '</p>'
        + '<p><strong>유형별 풀이 전략</strong>' + NT.esc(question.strategy) + '</p>'
        + '<p><strong>이 문항의 판단 근거</strong>' + NT.esc(question.evidence) + '</p></div>'
        + '<ol class="ph-grammar-review-options">' + question.choices.map((choice, i) =>
          '<li><b>' + circle[i] + ' ' + NT.esc(choice) + (i + 1 === question.answer ? ' · 정답' : '')
          + '</b>' + (question.choiceTranslations[i] ? '<p>' + NT.esc(question.choiceTranslations[i]) + '</p>' : '')
          + '<p>' + NT.esc(question.reasons[i]) + '</p></li>').join('') + '</ol></details>';
    }).join('');
    body.innerHTML = '<section class="nt-page nt-score"><div class="nt-score-pen"><b>' + score
      + '</b><span>/ ' + current.count + '</span></div><div class="nt-score-text">'
      + '<p class="nt-score-big">채점 결과</p><p>맞힌 문항 ' + score + '개 · 미응답 '
      + current.results.filter(result => !result.chosen).length + '개</p></div></section>'
      + '<div class="nt-page nt-review-actions"><button class="nt-btn nt-btn-line" type="button" data-action="filter">'
      + (wrongOnly ? '전체 문항 보기' : '틀린 문항만 보기') + '</button>'
      + '<button class="nt-btn" type="button" data-action="restart">이 단원 다시 풀기</button></div>'
      + '<section class="nt-page"><h2>문항별 해설</h2>'
      + (details || '<p>틀린 문항이 없습니다.</p>') + '</section>';
  }

  function tick() {
    if (!current) return;
    const seconds = current.item
      ? Math.min(maxSeconds, Math.max(0, Math.ceil((deadline - performance.now()) / 1000)))
      : Math.ceil(current.elapsedMillis / 1000);
    const clock = $('ph-grammar-time');
    const display = NT.fmtTime(seconds);
    if (clock.textContent !== display) clock.textContent = display;
    $('nt-timer-box').classList.toggle('is-low', Boolean(current.item) && seconds <= 5);
    if (current.item && seconds === 0 && !pending && performance.now() >= retryAfter) send('state');
  }

  async function send(action, choice = 0) {
    if (pending) return;
    pending = true;
    try {
      const previous = current?.item?.number;
      const focusWasInExam = body.contains(document.activeElement);
      current = await NT.api(api, {
        method: 'POST',
        body: JSON.stringify({ action, attempt: current?.id, number: previous || 0, choice })
      });
      deadline = performance.now() + current.remainingMillis;
      retryAfter = 0;
      status.hidden = true;
      if (action === 'restart') wrongOnly = true;
      exam.classList.toggle('ph-timed', Boolean(current.item));
      $('ph-grammar-time-label').textContent = current.item ? '현재 문항 남은 시간' : '총 풀이 시간';
      if (current.item) {
        if (!body.querySelector('[data-choice]') || previous !== current.item.number || action === 'restart') {
          renderQuestion();
          if (focusWasInExam || action === 'start' || action === 'restart' || action === 'answer') {
            body.querySelector('[data-choice]')?.focus({ preventScroll: true });
          }
        }
      } else {
        renderReview();
        window.scrollTo(0, 0);
      }
      tick();
    } catch (error) {
      retryAfter = performance.now() + 2500;
      status.textContent = error.status === 409 ? '다른 화면에서 시험 상태가 바뀌었습니다. 페이지를 새로고침해 주세요.'
        : '시험 상태를 불러오지 못했습니다. 연결을 확인한 뒤 다시 시도해 주세요.';
      status.hidden = false;
      if (error.status === 409) current = null;
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
    } else if (button.dataset.choice && current?.item) {
      send('answer', Number(button.dataset.choice));
    } else if (button.dataset.action) {
      send(button.dataset.action);
    }
  });
  document.addEventListener('keydown', event => {
    if (!current?.item || event.repeat || event.ctrlKey || event.metaKey || event.altKey || !/^[1-5]$/.test(event.key)) return;
    send('answer', Number(event.key));
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });
  window.setInterval(tick, 200);
})();
