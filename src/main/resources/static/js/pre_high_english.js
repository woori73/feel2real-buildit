(function () {
  'use strict';
  const $ = id => document.getElementById(id);
  const circle = ['①', '②', '③', '④', '⑤'];
  const exam = $('ph-test');
  const fileForm = $('ph-install');

  if (fileForm) fileForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = fileForm.querySelector('button');
    const status = $('ph-install-status');
    button.disabled = true;
    status.textContent = '회차 자료를 등록하는 중입니다…';
    try {
      await NT.api('/api/admin/pre-high-english/rounds', {
        method: 'POST', body: $('ph-file').files[0]
      });
      location.reload();
    } catch (error) {
      status.textContent = '등록하지 못했습니다. ' + error.message;
      button.disabled = false;
    }
  });

  if (!exam) {
    if (new URLSearchParams(location.search).get('login') === 'required') {
      NT.openLogin(new URLSearchParams(location.search).get('returnUrl'));
    }
    return;
  }

  const api = '/api/naesintest/pre-high/' + Number(exam.dataset.round);
  const shared = JSON.parse(exam.dataset.shared);
  const body = $('ph-body');
  const status = $('ph-status');
  let current, deadline = 0, active = 0, pending = false, wrongOnly = true;

  function image(number, part, defer = false) {
    return '<img class="nt-img' + (part === 'passage' ? ' nt-passage' : '') + '" src="' + api
      + '/image/' + (number + 1) + '/' + part + '" alt="' + (number + 1)
      + '번 ' + (part === 'passage' ? '공통 지문' : '독해 문항') + '"' + (defer ? ' loading="lazy"' : '') + '>';
  }

  function omrButtons(number, style) {
    return circle.map((label, index) => {
      const choice = index + 1;
      const selected = current.readingAnswers[number] === choice;
      return '<button type="button" class="' + style + (selected ? ' is-on' : '') + '" data-choice="' + choice
        + '" data-number="' + (number + 1) + '" aria-pressed="' + selected
        + '" aria-label="' + (number + 1) + '번 ' + label + '">'
        + (style === 'nt-ob' ? choice : label) + '</button>';
    }).join('');
  }

  function renderReading() {
    const rows = current.readingAnswers.map((answer, i) => '<div class="nt-omr-row'
      + (i === active ? ' is-current' : '') + '"><button class="nt-omr-no" type="button" data-go="' + i
      + '" aria-label="' + (i + 1) + '번으로">' + (i + 1) + '</button>' + omrButtons(i, 'nt-omr-b') + '</div>').join('');
    const answered = current.readingAnswers.filter(Boolean).length;
    return '<div class="nt-run-grid"><section class="nt-page"><div class="nt-qbar">'
      + '<button type="button" class="nt-nav" data-go="' + (active - 1) + '"' + (active === 0 ? ' disabled' : '') + '>이전</button>'
      + '<span class="nt-qpos"><b>' + (active + 1) + '</b> / ' + shared.length + '</span>'
      + '<button type="button" class="nt-nav" data-go="' + (active + 1) + '"' + (active + 1 === shared.length ? ' disabled' : '') + '>다음</button></div>'
      + '<div class="nt-sheet">' + (shared[active] ? image(active, 'passage') : '') + image(active, 'question') + '</div>'
      + '<div class="nt-pick"><div class="nt-pick-row"><span class="nt-pick-no">' + (active + 1) + '</span>'
      + omrButtons(active, 'nt-ob') + '</div></div></section>'
      + '<aside class="nt-omr" aria-label="OMR 답안지"><div class="nt-omr-head"><button class="nt-omr-close" type="button" data-toggle="close">닫기</button><h2>OMR 답안지</h2></div>'
      + '<div class="nt-omr-rows">' + rows + '</div><div class="nt-omr-foot"><p id="ph-omr-count">답한 문항 ' + answered + ' / ' + shared.length + '</p>'
      + '<button type="button" class="nt-btn" data-action="reading-done">독해 제출</button></div></aside></div>'
      + '<button type="button" class="nt-btn nt-omr-toggle" data-toggle="open">답안지 ' + answered + '/' + shared.length + '</button>';
  }

  function updateReadingChoices() {
    body.querySelectorAll('[data-choice]').forEach(button => {
      const answer = current.readingAnswers[Number(button.dataset.number) - 1];
      const selected = answer === Number(button.dataset.choice);
      button.classList.toggle('is-on', selected);
      button.setAttribute('aria-pressed', selected);
    });
    const count = current.readingAnswers.filter(Boolean).length;
    $('ph-omr-count').textContent = '답한 문항 ' + count + ' / ' + shared.length;
    body.querySelector('[data-toggle="open"]').textContent = '답안지 ' + count + '/' + shared.length;
  }

  function renderWords() {
    const word = current.word;
    return '<section class="nt-page"><h2>어휘 ' + word.number + ' / ' + current.wordCount + '</h2>'
      + '<p>다음 단어의 뜻으로 알맞은 것을 고르세요.</p><h3>' + NT.esc(word.text) + '</h3>'
      + '<div class="nt-qbar" style="flex-direction:column;align-items:stretch">'
      + word.choices.map((choice, i) => '<button class="nt-btn nt-btn-line" type="button" data-choice="' + (i + 1)
        + '" data-number="' + word.number + '">' + circle[i] + ' ' + NT.esc(choice) + '</button>').join('')
      + '</div></section>';
  }

  function correct(result) { return result.chosen === result.answer; }

  function renderReview() {
    const reading = current.readingResults;
    const words = current.wordResults;
    const score = results => results.filter(correct).length;
    const details = (label, results) => {
      const shown = wrongOnly ? results.filter(result => !correct(result)) : results;
      return '<section class="nt-page"><h2>' + label + ' ' + score(results) + ' / ' + results.length + '</h2>'
        + (shown.length ? shown.map((result, index) => '<details class="nt-exp"' + (index === 0 ? ' open' : '') + '><summary>' + result.number
          + '번' + (result.word ? ' · ' + NT.esc(result.word) : '') + ' · ' + (correct(result) ? '정답' : result.chosen ? '오답' : '미응답')
          + '</summary><p>내 답 ' + (circle[result.chosen - 1] || '미응답') + ' · 정답 ' + circle[result.answer - 1] + '</p>'
          + (result.explanation ? (shared[result.number - 1] ? image(result.number - 1, 'passage', true) : '')
            + image(result.number - 1, 'question', true) + NT.expHtml(result.explanation) : '')
          + (result.meaning ? '<p>' + NT.esc(result.meaning) + '</p>' : '')
          + '</details>').join('') : '<p>틀린 문항이 없습니다.</p>') + '</section>';
    };
    return '<section class="nt-page nt-score"><div class="nt-score-pen"><b>' + (score(reading) + score(words))
      + '</b><span>/ ' + (reading.length + words.length) + '</span></div><div class="nt-score-text">'
      + '<p class="nt-score-big">채점 결과</p><p>독해 ' + score(reading) + ' / ' + reading.length
      + ' · 어휘 ' + score(words) + ' / ' + words.length + '</p></div></section>'
      + '<div class="nt-page nt-review-actions"><button type="button" class="nt-btn nt-btn-line" data-toggle="filter">'
      + (wrongOnly ? '전체 문항 보기' : '틀린 문항만 보기') + '</button>'
      + '<button type="button" class="nt-btn" data-action="restart">이 회차 다시 풀기</button></div>'
      + details('독해', reading) + details('어휘', words);
  }

  function render() {
    const phase = current.phase;
    $('ph-section').textContent = { READING: '독해', WORD_READY: '어휘', WORDS: '어휘', REVIEW: '채점 결과' }[phase];
    if (phase !== 'READING') document.body.classList.remove('omr-open');
    if (phase === 'READING') body.innerHTML = renderReading();
    else if (phase === 'WORDS') body.innerHTML = renderWords();
    else if (phase === 'REVIEW') body.innerHTML = renderReview();
    else body.innerHTML = '<section class="nt-page"><h2>독해 답안 제출 완료</h2><p>어휘 ' + current.wordCount
      + '문항이 이어집니다. 각 단어의 뜻을 5초 안에 하나 고르세요.</p>'
      + '<button class="nt-btn" type="button" data-action="words-start">어휘 시작</button></section>';
    tick();
  }

  async function send(action, number = 0, choice = 0) {
    if (pending) return;
    pending = true;
    status.hidden = true;
    try {
      current = await NT.api(api, {
        method: 'POST', body: JSON.stringify({ action, attempt: current?.id, number, choice })
      });
      if (action === 'restart') { active = 0; wrongOnly = true; }
      deadline = performance.now() + current.remainingMillis;
      if (action === 'read-answer' && current.phase === 'READING') updateReadingChoices();
      else render();
    } catch (error) {
      status.textContent = error.status === 409 ? '다른 화면에서 시험을 열었습니다. 이 페이지를 새로고침해 주세요.'
        : '답안 상태를 불러오지 못했습니다. 연결을 확인해 주세요.';
      status.hidden = false;
    } finally { pending = false; }
  }

  function tick() {
    if (!current) return;
    const timed = current.phase === 'READING' || current.phase === 'WORDS';
    const remaining = Math.max(0, Math.ceil((deadline - performance.now()) / 1000));
    $('ph-time').textContent = timed ? NT.fmtTime(remaining) : '--:--';
    $('nt-timer-box').classList.toggle('is-low', timed && remaining <= (current.phase === 'WORDS' ? 2 : 60));
    if (timed && remaining === 0 && !pending) send('state');
  }

  body.addEventListener('click', event => {
    const button = event.target.closest('button');
    if (!button || pending) return;
    const data = button.dataset;
    if (data.go !== undefined) {
      active = Number(data.go);
      document.body.classList.remove('omr-open');
      render();
    }
    if (data.toggle === 'open' || data.toggle === 'close') document.body.classList.toggle('omr-open', data.toggle === 'open');
    if (data.toggle === 'filter') { wrongOnly = !wrongOnly; render(); }
    if (data.choice) send(current.phase === 'READING' ? 'read-answer' : 'word-answer', Number(data.number), Number(data.choice));
    if (data.action === 'reading-done' && !window.confirm('독해를 제출하고 어휘 퀴즈로 넘어갈까요?')) return;
    if (data.action) send(data.action);
  });
  document.addEventListener('keydown', event => {
    if (!current || pending || event.ctrlKey || event.metaKey || event.altKey || !/^[1-5]$/.test(event.key)) return;
    if (current.phase === 'READING') send('read-answer', active + 1, Number(event.key));
    else if (current.phase === 'WORDS') send('word-answer', current.word.number, Number(event.key));
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) tick(); });
  window.setInterval(tick, 200);
})();
