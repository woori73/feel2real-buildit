/*
 * 복습 강의보기 화면(에듀홀릭 LMS Phase 1 §5). lessonId는 URL(/lms/{handle}/review/{lessonId})에서 온다.
 * 기존 8개 MOCK 화면과 데이터가 완전히 분리된 실 API 전용 화면이다 — LMS_MOCK/LmsState를 쓰지 않는다.
 */
(function () {
  'use strict';

  var layout = document.querySelector('.lms-layout');
  var lessonId = layout && layout.dataset.lessonId ? Number(layout.dataset.lessonId) : null;
  var currentContentId = null;

  var playerBox = document.querySelector('[data-lecture-player]');
  var titleEl = document.querySelector('[data-lecture-title]');
  var noteEl = document.querySelector('[data-lecture-note]');
  var listBox = document.querySelector('[data-review-content-list]');
  var lessonTitleEl = document.querySelector('[data-review-lesson-title]');

  function formatDuration(sec) {
    if (!sec) return '';
    var m = Math.floor(sec / 60);
    var s = sec % 60;
    return m + ':' + (s < 10 ? '0' : '') + s;
  }

  function renderPlayer(lessonName, item) {
    titleEl.textContent = lessonName + ' · ' + item.title;
    noteEl.textContent = item.description || '';
    window.LmsPlayer.mount(playerBox, null, { url: item.streamUrl, type: 'full' }, true);
  }

  function renderContentList(detail) {
    listBox.innerHTML = '';
    if (!detail.contents || detail.contents.length === 0) {
      var empty = document.createElement('p');
      empty.className = 'lecture-empty';
      empty.textContent = '아직 공개된 복습영상이 없습니다.';
      listBox.appendChild(empty);
      return;
    }
    detail.contents.forEach(function (item) {
      var b = document.createElement('button');
      b.type = 'button';
      var meta = [item.unitLabel, item.examName].filter(Boolean).join(' · ');
      var duration = formatDuration(item.durationSec);
      b.textContent = '▶ ' + item.title + (meta ? ' (' + meta + ')' : '') + (duration ? ' · ' + duration : '');
      if (item.lmsContentId === currentContentId) b.classList.add('active');
      b.addEventListener('click', function () {
        currentContentId = item.lmsContentId;
        listBox.querySelectorAll('button').forEach(function (x) { x.classList.remove('active'); });
        b.classList.add('active');
        renderPlayer(detail.lessonName, item);
      });
      listBox.appendChild(b);
    });
  }

  function render() {
    if (!lessonId) return;
    return window.LmsData.getReviewLesson(window.Lms.handle, lessonId).then(function (detail) {
      lessonTitleEl.textContent = detail.lessonName + ' 복습영상';
      renderContentList(detail);
      if (detail.contents && detail.contents.length > 0) {
        currentContentId = detail.contents[0].lmsContentId;
        renderPlayer(detail.lessonName, detail.contents[0]);
      }
    }).catch(function () {
      titleEl.textContent = '복습영상을 불러오지 못했습니다.';
      noteEl.textContent = '새로고침 후 다시 시도해 주세요.';
    });
  }

  document.addEventListener('DOMContentLoaded', render);
})();
