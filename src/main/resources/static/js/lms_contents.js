/* 콘텐츠 관리 화면 (운영자 전용 — 접근 통제는 서버가 이미 끝냈다). */
(function () {
  'use strict';

  var currentCourseId = null;
  var selectBox = document.querySelector('[data-content-course-select]');
  var listBox = document.querySelector('[data-video-manage-list]');

  function renderCourseSelect(courses) {
    selectBox.innerHTML = '';
    courses.forEach(function (c) {
      var b = document.createElement('button');
      b.type = 'button';
      b.textContent = c.school + ' ' + c.grade;
      if (c.id === currentCourseId) b.classList.add('active');
      b.addEventListener('click', function () {
        currentCourseId = c.id;
        render();
      });
      selectBox.appendChild(b);
    });
  }

  function videoCard(video) {
    var accessLabel = video.type === 'trailer' ? '전체 공개'
      : video.type === 'OT' ? '로그인 회원' : '결제자만';
    var thumbIcon = video.type === 'trailer' ? '🎬' : video.type === 'OT' ? '📘' : '🔐';

    var card = document.createElement('div');
    card.className = 'video-card';
    card.innerHTML = '<div class="video-thumb"></div>'
      + '<div class="video-card-body">'
      + '<input type="text" class="v-title">'
      + '<div class="video-badges">'
      + '<span class="badge badge-length"></span>'
      + '<span class="badge badge-access"></span>'
      + '</div>'
      + '<textarea class="v-desc" rows="2"></textarea>'
      + '<div class="video-meta"></div>'
      + '<div class="video-card-actions">'
      + '<label class="btn-flat upload-label">'
      + '<span class="upload-text"></span>'
      + '<input type="file" accept="video/mp4" class="v-file" style="display:none">'
      + '</label>'
      + '<button class="btn-flat v-delete" type="button">삭제</button>'
      + '</div></div>';

    card.querySelector('.video-thumb').textContent = thumbIcon;
    card.querySelector('.v-title').value = video.title;
    card.querySelector('.badge-length').textContent = video.length || '';
    card.querySelector('.badge-access').textContent = accessLabel;
    card.querySelector('.v-desc').value = video.desc;
    card.querySelector('.video-meta').textContent = video.fileName
      ? ('업로드: ' + video.fileName + ' · ' + video.uploadDate)
      : '업로드된 영상 없음';
    card.querySelector('.upload-text').textContent = video.fileName ? '영상 교체' : 'MP4 업로드';
    card.querySelector('.v-delete').disabled = !video.fileName;

    card.querySelector('.v-title').addEventListener('blur', function (e) {
      window.LmsData
        .updateVideo(currentCourseId, video.key, { title: e.target.value.trim() || video.title })
        .then(render);
    });
    card.querySelector('.v-desc').addEventListener('blur', function (e) {
      window.LmsData.updateVideo(currentCourseId, video.key, { desc: e.target.value.trim() });
    });
    card.querySelector('.v-file').addEventListener('change', function (e) {
      var file = e.target.files[0];
      if (!file) return;
      // 1단계는 업로드 API가 없다. blob URL로 브라우저 안에서만 미리 본다.
      if (video.url) URL.revokeObjectURL(video.url);
      window.LmsData.updateVideo(currentCourseId, video.key, {
        url: URL.createObjectURL(file),
        fileName: file.name,
        uploadDate: new Date().toISOString().slice(0, 10)
      }).then(render);
    });
    card.querySelector('.v-delete').addEventListener('click', function () {
      if (!confirm('영상을 삭제하시겠습니까?')) return;
      if (video.url) URL.revokeObjectURL(video.url);
      window.LmsData
        .updateVideo(currentCourseId, video.key, { url: null, fileName: null, uploadDate: null })
        .then(render);
    });

    return card;
  }

  function render() {
    return window.LmsData.listCourses({}).then(function (courses) {
      if (currentCourseId === null && courses.length) currentCourseId = courses[0].id;
      renderCourseSelect(courses);
      return window.LmsData.listVideos(currentCourseId);
    }).then(function (videos) {
      listBox.innerHTML = '';
      videos.forEach(function (v) { listBox.appendChild(videoCard(v)); });
    });
  }

  document.addEventListener('DOMContentLoaded', render);
})();
