/**
 * 모의고사 학습 자료 전체 목록(/wiki/learning).
 * 카드 클릭 = 즉시 다운로드가 아니라 Wiki 안의 상세/미리보기 화면(/wiki/learning/{id})으로 이동.
 * 다운로드·삭제는 상세 화면에서 처리한다.
 */
(function () {
  var grade = '3';
  var tabs = document.getElementById('gradeTabs');
  var list = document.getElementById('list');

  var TYPE_LABEL = { VOCAB: '어휘', WORKBOOK: 'Workbook', VARIANT: '변형문제', WEEKLY: 'WEEKLY', ETC: '기타' };
  var WEEKLY_STANDARD_URL = '/wiki/weekly/WEEKLY_GENERATION_STANDARD.md';
  var WEEKLY_REFERENCE_URL = '/wiki/weekly/WEEKLY_REFERENCE.html';

  // WEEKLY 제작 기준은 사용자가 펼치거나 선택하지 않아도 최초 렌더링부터 DOM과
  // sessionStorage에 존재한다. Browser/Chrome AI가 WEEKLY를 만들 때 이 두 자료를
  // 항상 Context로 읽고, 이번 HALF/원문은 그 다음 순위의 입력으로 사용한다.
  loadWeeklyAiContext();

  tabs.addEventListener('click', function (e) {
    var b = e.target.closest('.grade-tab');
    if (!b) return;
    tabs.querySelectorAll('.grade-tab').forEach(function (t) { t.classList.toggle('active', t === b); });
    grade = b.dataset.grade;
    load();
  });

  window.wikiLearningListReload = load;
  load();

  async function loadWeeklyAiContext() {
    try {
      var responses = await Promise.all([fetch(WEEKLY_STANDARD_URL), fetch(WEEKLY_REFERENCE_URL)]);
      if (!responses[0].ok || !responses[1].ok) throw new Error('WEEKLY 기준 자료를 불러오지 못했습니다.');
      var values = await Promise.all([responses[0].text(), responses[1].text()]);
      var payload = {
        kind: 'WEEKLY_STANDARD',
        priority: ['generation_standard', 'visual_reference', 'current_half_source'],
        generation_standard: values[0],
        visual_reference_html: values[1],
        source_urls: { generation_standard: WEEKLY_STANDARD_URL, visual_reference: WEEKLY_REFERENCE_URL }
      };
      sessionStorage.setItem('buildit_weekly_ai_context', JSON.stringify(payload));
      var node = document.getElementById('weeklyAiContext');
      if (!node) {
        node = document.createElement('section');
        node.id = 'weeklyAiContext';
        node.setAttribute('data-ai-context', 'WEEKLY_STANDARD');
        node.setAttribute('aria-label', 'WEEKLY AI 제작 기준');
        node.style.position = 'absolute';
        node.style.left = '-10000px';
        node.style.width = '1px';
        node.style.height = '1px';
        node.style.overflow = 'hidden';
        document.body.appendChild(node);
      }
      node.innerHTML = '<h2>BUILD IT WEEKLY AI CONTEXT</h2>'
        + '<h3>1. WEEKLY_GENERATION_STANDARD.md</h3><pre></pre>'
        + '<h3>2. WEEKLY_REFERENCE.html</h3><div data-weekly-reference></div>';
      node.querySelector('pre').textContent = values[0];
      node.querySelector('[data-weekly-reference]').innerHTML = values[1];
      window.builditWeeklyAiContext = payload;
      window.dispatchEvent(new CustomEvent('buildit-weekly-ai-context-ready', { detail: payload }));
    } catch (error) {
      console.warn('[WEEKLY AI Context]', error);
    }
  }

  async function load() {
    list.innerHTML = '<p class="muted">불러오는 중...</p>';
    try {
      var res = await fetch('/api/wiki/learning-materials' + (grade ? '?grade=' + grade : ''));
      if (!res.ok) throw new Error();
      var items = await res.json();
      if (!items.length) { list.innerHTML = '<p class="list-empty">등록된 학습 자료가 없습니다.</p>'; return; }
      list.innerHTML = items.map(card).join('');
    } catch (_) {
      list.innerHTML = '<p class="error">목록을 불러오지 못했습니다.</p>';
    }
  }

  function card(it) {
    var label = it.dataTypeLabel || TYPE_LABEL[it.dataType] || '기타';
    return '<a class="learning-card" href="/wiki/learning/' + encodeURIComponent(it.wikiLearningMaterialId) + '">'
      + '<span class="learning-badge">' + escapeHtml(label) + '</span>'
      + '<b>' + escapeHtml(it.title) + '</b>'
      + '<span class="learning-meta">고' + it.grade + ' · ' + escapeHtml(it.uploaderName || '회원') + ' · ' + meta(it) + '</span>'
      + '</a>';
  }

  function meta(it) {
    var ext = (it.originalFileName || '').split('.').pop().toUpperCase();
    var mb = it.fileSize ? (it.fileSize / (1024 * 1024)).toFixed(1) + 'MB' : '';
    return [ext, mb].filter(Boolean).join(' · ');
  }
  function escapeHtml(v) { return String(v || '').replace(/[&<>'\"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '\"': '&quot;' })[c]; }); }
})();
