/*
 * AI 비서(세븐·서수니) 영상 전환 — 홈(/)과 AI 비서 화면(/dashboard)이 같은 방식으로 쓴다.
 * - 영상 요소 두 개를 번갈아 쓴다. 다음 영상의 첫 화면이 실제로 그려진 뒤에만 위에 겹쳐 서서히 나타낸다.
 *   아래 영상은 전환이 끝날 때까지 그대로 보이므로 검은 화면이나 빈 화면이 나오지 않는다.
 * - 대기 영상은 말하는 동안에도 다른 요소에 불러 둔 채 멈춰 두고, 말하기가 끝나면 처음(0초)부터 다시 보여 준다.
 *   서수니 말하기 보정본은 마지막 몇 프레임이 대기 영상 첫 프레임과 같아지도록 만들어져 있어 바뀌는 순간이 거의 보이지 않는다.
 * - 전환 시간은 실제 화면 비교로 정했다(2026-10-25):
 *   말하기 끝 → 대기 160ms: 두 화면이 같아 짧게 해도 튀지 않고, 길게 하면 겹침만 늘어난다.
 *   대기 → 말하기 220ms: 화면 차이가 말하는 중 한 프레임 변화의 약 5배라 6~7프레임에 나눠 바꾼다.
 *   그 밖(생각 중 등) 300ms.
 */
(function () {
  'use strict';

  var FADE_MS = { speakToIdle: 160, toSpeak: 220, other: 300 };
  // 느린 휴대폰 회선에서도 기다린다. 실제 실패(error)는 바로 알린다.
  var FRAME_WAIT_MS = 20000;
  var POLL_MS = 20;

  function fadeMs(fromClip, toClip) {
    if (fromClip === 'speak' && toClip === 'idle') return FADE_MS.speakToIdle;
    if (toClip === 'speak') return FADE_MS.toSpeak;
    return FADE_MS.other;
  }

  function reducedMotion() {
    return typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  /** '/dashboard/agent-media/suni/idle.mp4' → 'idle' */
  function clipName(src) {
    var m = /([a-z]+)\.mp4(?:[?#].*)?$/.exec(String(src || ''));
    return m ? m[1] : null;
  }

  /**
   * 영상의 현재 화면을 그릴 수 있게 되면(데이터 있음, 되감기 끝) cb(true), 불러오기 실패면 바로 cb(false, 'error'),
   * 20초 안에 준비되지 않으면 cb(false, 'timeout'). 한 번만 부른다.
   * 투명(opacity 0)한 영상에는 requestVideoFrameCallback이 오지 않을 수 있어 쓰지 않는다(실측: 2.5초 지연).
   */
  function whenFrame(video, cb) {
    var done = false, waited = 0, start = Date.now();
    var EVENTS = ['loadeddata', 'canplay', 'playing', 'seeked'];
    function ready() { return video.readyState >= 2 && !video.seeking; }
    function finish(ok, why) {
      if (done) return;
      done = true;
      EVENTS.forEach(function (e) { if (video.removeEventListener) video.removeEventListener(e, check); });
      if (video.removeEventListener) video.removeEventListener('error', failed);
      cb(ok, why);
    }
    function failed() { finish(false, 'error'); }
    // 백그라운드 탭에서는 타이머가 1초 단위로 늦어지므로 영상 이벤트로도 바로 확인한다(실측).
    function check() { if (!done && ready()) finish(true); }
    function poll() {
      if (done) return;
      if (ready()) { finish(true); return; }
      if (video.error) { failed(); return; }
      waited += POLL_MS;
      if (waited >= FRAME_WAIT_MS || Date.now() - start >= FRAME_WAIT_MS) { finish(false, 'timeout'); return; }
      setTimeout(poll, POLL_MS);
    }
    EVENTS.forEach(function (e) { if (video.addEventListener) video.addEventListener(e, check); });
    if (video.addEventListener) video.addEventListener('error', failed);
    setTimeout(poll, 0);
  }

  /**
   * pair: [video, video], st: { front: 0|1, token: number } (호출 측이 보관)
   * opts: { muted, loop, restart, fromClip, toClip, poster, onFail(reason), onSwapped() }
   * 같은 영상이 이미 앞에 보이면 아무것도 하지 않는다(restart면 처음으로 되감기만).
   */
  function show(pair, st, src, opts) {
    var o = opts || {};
    var cur = pair[st.front], next = pair[1 - st.front];
    if (!cur || !next) return false;
    var token = st.token = (st.token || 0) + 1;
    if (cur.getAttribute('src') === src && cur.classList.contains('is-on')) {
      next.pause && next.pause();
      return true;
    }
    var ms = reducedMotion() ? 0 : fadeMs(o.fromClip || clipName(cur.getAttribute('src')), o.toClip || clipName(src));
    // 나타날 영상을 현재 영상 바로 뒤(DOM 순서상 위)로 옮긴다. 옮긴 뒤에 재생해야 멈추지 않는다.
    if (cur.nextSibling !== next && cur.parentNode) cur.parentNode.insertBefore(next, cur.nextSibling);
    next.muted = o.muted !== false;
    next.loop = o.loop !== false;
    if (o.poster) next.poster = o.poster; else if (o.poster === null) next.removeAttribute('poster');
    if (next.getAttribute('src') !== src) next.src = src;
    else if (o.restart) { try { next.currentTime = 0; } catch (_) { /* 메타데이터 전 */ } }
    next.style.transitionDuration = ms + 'ms';
    // 소리 있는 재생은 버튼을 누른 처리 안에서 바로 시작해야 모바일에서 막히지 않는다(여기서 즉시 play).
    var p = next.play && next.play();
    if (p && p.catch) p.catch(function (e) { if (st.token === token && o.onFail) o.onFail(e && e.name === 'NotAllowedError' ? 'blocked' : 'play'); });
    whenFrame(next, function (ok, why) {
      if (st.token !== token) return;
      if (!ok) { if (o.onFail) o.onFail(why); return; }
      next.classList.add('is-on');
      st.front = 1 - st.front;
      setTimeout(function () {
        if (st.token !== token) return;
        cur.classList.remove('is-on');
        if (cur.pause) cur.pause();
        if (o.onSwapped) o.onSwapped();
      }, ms + 60);
    });
    return true;
  }

  var api = { FADE_MS: FADE_MS, fadeMs: fadeMs, clipName: clipName, show: show };
  if (typeof window !== 'undefined') window.f2rClipSwitch = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})();
