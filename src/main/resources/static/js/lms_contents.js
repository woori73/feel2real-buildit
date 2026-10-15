/*
 * 콘텐츠 관리 화면(강사 전용) — 재원생 복습영상 실 API 연결(에듀홀릭 LMS Phase 1 §4).
 * LMS_MOCK/LmsState는 건드리지 않는다 — 이 화면의 데이터는 lms_contents 실 API다.
 */
(function () {
  'use strict';

  var handle = window.Lms.handle;

  var form = document.querySelector('[data-content-form]');
  var lessonSelect = form.querySelector('[data-field-lesson]');
  var idField = form.querySelector('[data-field-lms-content-id]');
  var titleField = form.querySelector('[data-field-title]');
  var descField = form.querySelector('[data-field-description]');
  var schoolField = form.querySelector('[data-field-school]');
  var gradeField = form.querySelector('[data-field-grade]');
  var examField = form.querySelector('[data-field-exam]');
  var unitField = form.querySelector('[data-field-unit]');
  var videoField = form.querySelector('[data-field-video]');
  var videoRow = form.querySelector('[data-field-video-row]');
  var submitBtn = form.querySelector('[data-form-submit]');
  var cancelBtn = form.querySelector('[data-form-cancel]');
  var statusEl = form.querySelector('[data-form-status]');
  var listBox = document.querySelector('[data-content-list]');

  var lessons = [];
  var contents = [];

  function lessonLabel(lesson) {
    var parts = [lesson.schoolName, lesson.gradeLabel].filter(Boolean);
    return (parts.length ? parts.join(' ') + ' · ' : '') + lesson.name;
  }

  function statusLabel(status) {
    return {
      READY: '업로드 완료(비공개)',
      PUBLISHED: '공개 중',
      HIDDEN: '비공개',
      UPLOADING: '업로드 중',
      FAILED: '업로드 실패'
    }[status] || status;
  }

  function renderLessonOptions() {
    lessonSelect.innerHTML = '';
    if (lessons.length === 0) {
      var opt = document.createElement('option');
      opt.value = '';
      opt.textContent = '등록된 강좌가 없습니다 — 먼저 강좌관리에서 강좌를 등록해 주세요.';
      lessonSelect.appendChild(opt);
      lessonSelect.disabled = true;
      return;
    }
    lessonSelect.disabled = false;
    lessons.forEach(function (lesson) {
      var opt = document.createElement('option');
      opt.value = String(lesson.lessonId);
      opt.textContent = lessonLabel(lesson);
      lessonSelect.appendChild(opt);
    });
  }

  function formatDuration(sec) {
    if (!sec) return '';
    var m = Math.floor(sec / 60);
    var s = sec % 60;
    return m + '분 ' + (s < 10 ? '0' : '') + s + '초';
  }

  function renderContentList() {
    listBox.innerHTML = '';
    if (contents.length === 0) {
      var empty = document.createElement('p');
      empty.className = 'content-empty';
      empty.textContent = '등록된 콘텐츠가 없습니다. 위에서 복습영상을 업로드해 보세요.';
      listBox.appendChild(empty);
      return;
    }
    contents.forEach(function (item) {
      var card = document.createElement('div');
      card.className = 'content-card';

      var head = document.createElement('div');
      head.className = 'content-card-head';
      var title = document.createElement('h3');
      title.textContent = item.title;
      var badge = document.createElement('span');
      badge.className = 'status-badge status-' + item.status.toLowerCase();
      badge.textContent = statusLabel(item.status);
      head.appendChild(title);
      head.appendChild(badge);
      card.appendChild(head);

      var meta = document.createElement('p');
      meta.className = 'content-card-meta';
      var metaParts = [item.lessonName, item.schoolName, item.gradeLabel, item.examName, item.unitLabel]
        .filter(Boolean);
      if (item.durationSec) metaParts.push(formatDuration(item.durationSec));
      meta.textContent = metaParts.join(' · ');
      card.appendChild(meta);

      if (item.description) {
        var desc = document.createElement('p');
        desc.className = 'content-card-desc';
        desc.textContent = item.description;
        card.appendChild(desc);
      }

      var actions = document.createElement('div');
      actions.className = 'content-card-actions';

      var publishBtn = document.createElement('button');
      publishBtn.type = 'button';
      publishBtn.className = 'btn-flat';
      publishBtn.textContent = item.status === 'PUBLISHED' ? '비공개로 전환' : '공개하기';
      publishBtn.disabled = !item.hasVideo;
      publishBtn.addEventListener('click', function () {
        publishBtn.disabled = true;
        window.LmsData.setAdminContentPublished(handle, item.lmsContentId, item.status !== 'PUBLISHED')
          .then(loadAll)
          .catch(function () {
            alert('공개 상태를 변경하지 못했습니다.');
            publishBtn.disabled = false;
          });
      });
      actions.appendChild(publishBtn);

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn-flat';
      editBtn.textContent = '수정';
      editBtn.addEventListener('click', function () { enterEditMode(item); });
      actions.appendChild(editBtn);

      var deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'delete';
      deleteBtn.textContent = '삭제';
      deleteBtn.addEventListener('click', function () {
        if (!confirm('삭제하시겠습니까? 되돌릴 수 없습니다.')) return;
        deleteBtn.disabled = true;
        window.LmsData.deleteAdminContent(handle, item.lmsContentId).then(loadAll).catch(function () {
          alert('콘텐츠를 삭제하지 못했습니다.');
          deleteBtn.disabled = false;
        });
      });
      actions.appendChild(deleteBtn);

      card.appendChild(actions);
      listBox.appendChild(card);
    });
  }

  function enterEditMode(item) {
    idField.value = String(item.lmsContentId);
    lessonSelect.value = String(item.lessonId);
    titleField.value = item.title || '';
    descField.value = item.description || '';
    schoolField.value = item.schoolName || '';
    gradeField.value = item.gradeLabel || '';
    examField.value = item.examName || '';
    unitField.value = item.unitLabel || '';
    videoField.value = '';
    videoField.required = false;
    videoRow.querySelector('.field-hint').textContent = '영상은 그대로 유지됩니다(교체는 지원하지 않습니다).';
    submitBtn.textContent = '수정 저장';
    cancelBtn.hidden = false;
    statusEl.textContent = '';
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function resetForm() {
    form.reset();
    idField.value = '';
    videoField.required = true;
    videoRow.querySelector('.field-hint').textContent = '새 업로드일 때만 필요합니다. 수정 시 영상은 그대로 유지됩니다.';
    submitBtn.textContent = '업로드 · 저장';
    cancelBtn.hidden = true;
    statusEl.textContent = '';
  }

  function loadAll() {
    return window.LmsData.listAdminContents(handle).then(function (view) {
      lessons = view.lessons || [];
      contents = view.contents || [];
      renderLessonOptions();
      renderContentList();
    }).catch(function () {
      statusEl.textContent = '목록을 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.';
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    cancelBtn.addEventListener('click', resetForm);

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var editingId = idField.value;
      statusEl.textContent = '저장 중입니다…';
      submitBtn.disabled = true;

      var details = {
        lessonId: lessonSelect.value ? Number(lessonSelect.value) : null,
        title: titleField.value.trim(),
        description: descField.value.trim(),
        schoolName: schoolField.value.trim(),
        gradeLabel: gradeField.value.trim(),
        examName: examField.value.trim(),
        unitLabel: unitField.value.trim(),
        displayOrder: 0
      };

      var action;
      if (editingId) {
        action = window.LmsData.updateAdminContent(handle, editingId, details);
      } else {
        if (!videoField.files || videoField.files.length === 0) {
          statusEl.textContent = '영상 파일을 선택해 주세요.';
          submitBtn.disabled = false;
          return;
        }
        var fd = new FormData();
        fd.append('video', videoField.files[0]);
        Object.keys(details).forEach(function (key) {
          if (details[key] !== null && details[key] !== '') fd.append(key, details[key]);
        });
        action = window.LmsData.createAdminContent(handle, fd);
      }

      action.then(function () {
        resetForm();
        return loadAll();
      }).catch(function (error) {
        statusEl.textContent = (error && error.message) || '저장하지 못했습니다. 다시 시도해 주세요.';
      }).finally(function () {
        submitBtn.disabled = false;
      });
    });

    loadAll();
  });
})();
