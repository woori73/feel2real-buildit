/*
 * 학원 LMS 공통 영상 플레이어 + 2분 맛보기 모달.
 * 로드하는 화면: 강좌목록(모달), 강좌상세, 강의보기.
 *
 * 3단계 잠금: 트레일러=전체 공개 / OT=로그인 회원 / 본강의=결제자.
 * ⚠ 목업의 클라이언트 전역 isLoggedIn 대신 서버가 내려준 Lms.isAuthenticated를 쓴다.
 */
(function () {
  'use strict';

  var TRAILER_LIMIT_SECONDS = 120;

  function mount(container, course, video, unlocked) {
    container.innerHTML = '';
    var wrap = document.createElement('div');
    wrap.className = 'player-wrap';

    if (!unlocked) {
      wrap.innerHTML = '<div class="player-locked"><div class="lock-icon">🔒</div><p>결제 후 시청 가능합니다.</p></div>';
      container.appendChild(wrap);
      return;
    }
    if (!video || !video.url) {
      wrap.innerHTML = '<div class="player-empty"><div class="empty-icon">🎬</div><p>영상 준비중입니다.</p></div>';
      container.appendChild(wrap);
      return;
    }

    var videoEl = document.createElement('video');
    videoEl.src = video.url;
    videoEl.controls = true;
    videoEl.className = 'player-video';
    wrap.appendChild(videoEl);
    container.appendChild(wrap);

    if (video.type !== 'trailer') return;

    // 트레일러는 120초에서 멈추고 수강신청 오버레이를 띄운다.
    var overlay = document.createElement('div');
    overlay.className = 'trailer-overlay';
    overlay.innerHTML = '<p>계속 시청하려면 수강신청이 필요합니다.</p>'
      + '<button class="btn" type="button">수강신청</button>';
    wrap.appendChild(overlay);
    overlay.querySelector('button').addEventListener('click', function () {
      closeTrailerModal();
      window.Lms.go('/courses/' + course.id);
    });
    videoEl.addEventListener('timeupdate', function () {
      if (videoEl.currentTime >= TRAILER_LIMIT_SECONDS) {
        videoEl.pause();
        overlay.style.display = 'flex';
      }
    });
  }

  function isVideoUnlocked(video, paid) {
    if (video.type === 'trailer') return true;
    if (video.type === 'OT') return window.Lms.isAuthenticated;
    return paid;
  }

  function modal() {
    return document.querySelector('[data-trailer-modal]');
  }

  function openTrailerModal(course) {
    var box = modal();
    if (!box) return;
    box.querySelector('[data-trailer-modal-title]').textContent =
      window.Lms.courseName(course) + ' · 2분 맛보기';
    var trailer = course.videos.find(function (v) { return v.type === 'trailer'; });
    mount(box.querySelector('[data-trailer-modal-player]'), course, trailer, true);
    box.querySelector('[data-trailer-modal-enroll]').onclick = function () {
      closeTrailerModal();
      window.Lms.go('/courses/' + course.id);
    };
    box.classList.add('open');
  }

  function closeTrailerModal() {
    var box = modal();
    if (!box) return;
    box.classList.remove('open');
    box.querySelector('[data-trailer-modal-player]').innerHTML = '';
  }

  document.addEventListener('DOMContentLoaded', function () {
    var box = modal();
    if (!box) return;
    box.querySelector('[data-trailer-modal-close]').addEventListener('click', closeTrailerModal);
    box.addEventListener('click', function (e) {
      if (e.target === box) closeTrailerModal();
    });
  });

  window.LmsPlayer = {
    mount: mount,
    isVideoUnlocked: isVideoUnlocked,
    openTrailerModal: openTrailerModal,
    closeTrailerModal: closeTrailerModal
  };
})();
