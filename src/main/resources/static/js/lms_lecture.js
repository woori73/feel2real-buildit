/* 강의보기 + 수강후기 화면. courseId는 URL(/lms/{handle}/lecture/{courseId})에서 온다. */
(function () {
  'use strict';

  var courseId = window.Lms.courseId;
  var currentVideoKey = null;
  var selectedStar = 0;

  var playerBox = document.querySelector('[data-lecture-player]');
  var titleEl = document.querySelector('[data-lecture-title]');
  var noteEl = document.querySelector('[data-lecture-note]');
  var listBox = document.querySelector('[data-lecture-list]');
  var reviewListBox = document.querySelector('[data-review-list]');

  function showBlocked(title, note, message) {
    titleEl.textContent = title;
    noteEl.textContent = note;
    playerBox.innerHTML = '<div class="player-wrap"><div class="player-locked">'
      + '<div class="lock-icon">🔒</div><p></p></div></div>';
    playerBox.querySelector('p').textContent = message;
    listBox.innerHTML = '';
    reviewListBox.innerHTML = '';
  }

  function renderPlayer(course, video, unlocked) {
    titleEl.textContent = window.Lms.courseName(course) + ' · ' + video.title;
    noteEl.textContent = unlocked ? (video.desc || '') : '결제 후 시청 가능합니다.';
    window.LmsPlayer.mount(playerBox, course, video, unlocked);
  }

  function renderLectureList(course, paid) {
    listBox.innerHTML = '';
    course.videos.forEach(function (v) {
      var unlocked = window.LmsPlayer.isVideoUnlocked(v, paid);
      var icon = v.type === 'trailer' ? '🎬' : (unlocked ? '▶' : '🔒');
      var b = document.createElement('button');
      b.type = 'button';
      b.textContent = icon + ' ' + v.title;
      if (v.key === currentVideoKey) b.classList.add('active');
      b.addEventListener('click', function () {
        currentVideoKey = v.key;
        listBox.querySelectorAll('button').forEach(function (x) { x.classList.remove('active'); });
        b.classList.add('active');
        renderPlayer(course, v, unlocked);
      });
      listBox.appendChild(b);
    });
  }

  function renderReviewList() {
    var list = window.LmsState.getReviews().filter(function (r) { return r.courseId === courseId; });
    reviewListBox.innerHTML = '';
    if (list.length === 0) {
      var empty = document.createElement('p');
      empty.className = 'review-empty';
      empty.textContent = '아직 등록된 후기가 없습니다.';
      reviewListBox.appendChild(empty);
      return;
    }
    list.forEach(function (r) {
      var item = document.createElement('div');
      item.className = 'review-item';

      var stars = document.createElement('div');
      stars.className = 'review-star';
      stars.textContent = '★'.repeat(r.rating) + '☆'.repeat(5 - r.rating);
      item.appendChild(stars);

      var content = document.createElement('div');
      content.className = 'review-content';
      content.textContent = r.content;
      item.appendChild(content);

      if (r.next) {
        var next = document.createElement('div');
        next.className = 'review-next';
        next.textContent = '다음에 필요한 콘텐츠: ' + r.next;
        item.appendChild(next);
      }
      if (r.reply) {
        var reply = document.createElement('div');
        reply.className = 'review-reply';
        var label = document.createElement('strong');
        label.textContent = '학원 답변';
        reply.appendChild(label);
        reply.appendChild(document.createElement('br'));
        reply.appendChild(document.createTextNode(r.reply));
        item.appendChild(reply);
      }
      reviewListBox.appendChild(item);
    });
  }

  function resetStarSelect() {
    selectedStar = 0;
    document.querySelectorAll('[data-star-select] span').forEach(function (s) {
      s.classList.remove('selected');
    });
    document.querySelector('[data-review-content]').value = '';
    document.querySelector('[data-review-next]').value = '';
  }

  function render() {
    // 인증은 서버(SecurityConfig)가 이미 강제한다. 여기 도달했다는 건 로그인 상태라는 뜻이지만,
    // 결제 여부는 클라이언트 상태이므로 여기서 본다.
    return window.LmsData.getCourse(courseId).then(function (course) {
      if (!course) {
        showBlocked('수강 중인 강좌가 없습니다.', '', '강좌를 신청하고 강의를 시작해 보세요.');
        return;
      }
      var paid = window.LmsState.isPaidFor(course.id);
      if (currentVideoKey === null
          || !course.videos.some(function (v) { return v.key === currentVideoKey; })) {
        currentVideoKey = course.videos[0].key;
      }
      renderLectureList(course, paid);
      var current = course.videos.find(function (v) { return v.key === currentVideoKey; });
      renderPlayer(course, current, window.LmsPlayer.isVideoUnlocked(current, paid));
      renderReviewList();
      resetStarSelect();
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelector('[data-star-select]').addEventListener('click', function (e) {
      if (e.target.tagName !== 'SPAN') return;
      selectedStar = parseInt(e.target.dataset.v, 10);
      document.querySelectorAll('[data-star-select] span').forEach(function (s) {
        s.classList.toggle('selected', parseInt(s.dataset.v, 10) <= selectedStar);
      });
    });

    document.querySelector('[data-review-submit]').addEventListener('click', function () {
      var content = document.querySelector('[data-review-content]').value.trim();
      if (selectedStar === 0) {
        alert('별점을 선택해 주세요.');
        return;
      }
      if (!content) {
        alert('후기 내용을 입력해 주세요.');
        return;
      }
      window.LmsState.addReview({
        courseId: courseId,
        rating: selectedStar,
        content: content,
        next: document.querySelector('[data-review-next]').value.trim(),
        reply: ''
      });
      resetStarSelect();
      renderReviewList();
      alert('후기가 등록되었습니다.');
    });

    render();
  });
})();
