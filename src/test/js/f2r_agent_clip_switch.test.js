const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// AI 비서 영상 전환(홈·대시보드 공용): 전환 시간 규칙, 첫 화면이 그려진 뒤에만 겹쳐 바꾸기, 검은 화면 방지.
const JS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/f2r_agent_clip_switch.js'), 'utf8');

function load() {
  const timers = [];
  const window = {};
  vm.runInNewContext(JS, { window, setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; } });
  // 지금 대기 중인 화면 확인(poll) 타이머를 한 차례 실행한다. 정리 타이머(ms > 20)는 남겨 둔다.
  const tick = () => {
    const due = timers.filter((t) => t.ms <= 20);
    due.forEach((t) => timers.splice(timers.indexOf(t), 1));
    due.forEach((t) => t.fn());
  };
  return { sw: window.f2rClipSwitch, timers, tick };
}

// 영상 요소 흉내: src 속성, class 목록, 재생/정지, 다음 화면 콜백.
function fakeVideo(parent, src) {
  const attrs = src ? { src } : {};
  const classes = new Set();
  const v = {
    parentNode: parent, muted: true, loop: true, readyState: src ? 4 : 0, currentTime: 3, paused: true, style: {}, frameCbs: [],
    getAttribute: (n) => (n in attrs ? attrs[n] : null),
    removeAttribute: (n) => { delete attrs[n]; },
    set src(x) { attrs.src = x; v.readyState = 0; },
    get src() { return attrs.src; },
    set poster(x) { attrs.poster = x; },
    classList: { add: (c) => classes.add(c), remove: (c) => classes.delete(c), contains: (c) => classes.has(c) },
    play: () => { v.paused = false; return { catch: () => {} }; },
    pause: () => { v.paused = true; },
    seeking: false, handlers: {},
    addEventListener: (e, fn) => { (v.handlers[e] = v.handlers[e] || []).push(fn); },
    removeEventListener: (e, fn) => { v.handlers[e] = (v.handlers[e] || []).filter((x) => x !== fn); },
    emit: (e) => (v.handlers[e] || []).slice().forEach((fn) => fn()),
    drawFrame: () => { v.readyState = 4; v.seeking = false; }
  };
  return v;
}

function stage(curSrc) {
  const children = [];
  const parent = {
    insertBefore: (node, ref) => {
      children.splice(children.indexOf(node), 1);
      const i = ref ? children.indexOf(ref) : children.length;
      children.splice(i < 0 ? children.length : i, 0, node);
    }
  };
  const a = fakeVideo(parent, curSrc), b = fakeVideo(parent, null);
  children.push(b, a);
  Object.defineProperty(a, 'nextSibling', { get: () => children[children.indexOf(a) + 1] || null });
  Object.defineProperty(b, 'nextSibling', { get: () => children[children.indexOf(b) + 1] || null });
  a.classList.add('is-on');
  return { a, b, children };
}

test('given 전환 종류 when 시간을 고르면 then 말하기 끝→대기 160ms, 말하기 시작 220ms, 그 밖 300ms다', () => {
  // given
  const { sw } = load();
  // when / then
  assert.equal(sw.fadeMs('speak', 'idle'), 160);
  assert.equal(sw.fadeMs('idle', 'speak'), 220);
  assert.equal(sw.fadeMs(null, 'speak'), 220);
  assert.equal(sw.fadeMs('idle', 'think'), 300);
  assert.equal(sw.fadeMs('idle', 'idle'), 300);
});

test('given 영상 주소 when 이름을 뽑으면 then 파일 이름(확장자 제외)만 돌려준다', () => {
  // given
  const { sw } = load();
  // when / then
  assert.equal(sw.clipName('/dashboard/agent-media/suni/idle.mp4'), 'idle');
  assert.equal(sw.clipName('/agent-media/public/seven/speak.mp4?v=2'), 'speak');
  assert.equal(sw.clipName(null), null);
  assert.equal(sw.clipName('/poster.jpg'), null);
});

test('given 대기 영상이 보이는 중 when 말하기로 바꾸면 then 첫 화면이 그려지기 전에는 기존 영상을 그대로 두고, 그려진 뒤 위에서 서서히 나타낸다', () => {
  // given
  const { sw, timers, tick } = load();
  const { a, b, children } = stage('/m/suni/idle.mp4');
  const st = { front: 1, token: 0 };
  const pair = [b, a];
  // when
  sw.show(pair, st, '/m/suni/speak.mp4', { muted: false, loop: false, restart: true, fromClip: 'idle', toClip: 'speak' });
  // then: 아직 화면이 없으므로 기존 대기 영상만 보인다(검은 화면 없음)
  assert.equal(children[children.length - 1], b, '나타날 영상은 현재 영상보다 위(뒤 순서)에 놓인다');
  assert.equal(a.classList.contains('is-on'), true);
  assert.equal(b.classList.contains('is-on'), false);
  assert.equal(b.paused, false);
  assert.equal(b.muted, false);
  assert.equal(b.loop, false);
  assert.equal(b.style.transitionDuration, '220ms');
  tick();
  assert.equal(b.classList.contains('is-on'), false, '데이터가 없으면 계속 기다린다');
  // when: 첫 화면을 그릴 수 있게 됨
  b.drawFrame();
  tick();
  // then: 새 영상이 나타나고, 기존 영상은 전환이 끝난 뒤에만 내린다
  assert.equal(b.classList.contains('is-on'), true);
  assert.equal(a.classList.contains('is-on'), true);
  assert.equal(st.front, 0);
  const done = timers.find((t) => t.ms === 280);
  assert.ok(done, '전환 시간 + 60ms 뒤 정리');
  done.fn();
  assert.equal(a.classList.contains('is-on'), false);
  assert.equal(a.paused, true);
  assert.equal(a.getAttribute('src'), '/m/suni/idle.mp4', '대기 영상은 다시 쓰도록 불러 둔 채 둔다');
});

test('given 말하기가 끝남 when 불러 둔 대기 영상으로 돌아가면 then 다시 불러오지 않고 처음(0초)부터 보여 준다', () => {
  // given
  const { sw } = load();
  const { a, b } = stage('/m/suni/speak.mp4');
  b.getAttribute = ((orig) => (n) => (n === 'src' ? '/m/suni/idle.mp4' : orig(n)))(b.getAttribute);
  let reassigned = false;
  Object.defineProperty(b, 'src', { set: () => { reassigned = true; }, get: () => '/m/suni/idle.mp4' });
  const st = { front: 1, token: 0 };
  // when
  sw.show([b, a], st, '/m/suni/idle.mp4', { restart: true, fromClip: 'speak', toClip: 'idle' });
  // then
  assert.equal(reassigned, false);
  assert.equal(b.currentTime, 0);
  assert.equal(b.muted, true);
  assert.equal(b.loop, true);
  assert.equal(b.style.transitionDuration, '160ms');
});

test('given 다음 영상이 끝내 그려지지 않음 when 기다림이 끝나면 then 기존 화면을 유지하고 실패를 알린다', () => {
  // given
  const { sw, tick } = load();
  const { a, b } = stage('/m/seven/idle.mp4');
  const st = { front: 1, token: 0 };
  let failed = null;
  // when: 20초(20ms × 1000회) 넘게 기다림
  sw.show([b, a], st, '/m/seven/speak.mp4', { muted: false, loop: false, onFail: (r) => { failed = r; } });
  for (let i = 0; i < 1010 && !failed; i++) tick();
  // then
  assert.equal(failed, 'timeout');
  assert.equal(a.classList.contains('is-on'), true);
  assert.equal(b.classList.contains('is-on'), false);
  assert.equal(st.front, 1);
});

test('given 전환 중 when 다른 영상으로 다시 바꾸면 then 늦게 끝난 앞 전환은 무시하고 마지막 요청만 한 번 바꾼다', () => {
  // given
  const { sw, tick } = load();
  const { a, b } = stage('/m/suni/idle.mp4');
  const st = { front: 1, token: 0 };
  sw.show([b, a], st, '/m/suni/speak.mp4', { muted: false, loop: false });
  // when
  sw.show([b, a], st, '/m/suni/think.mp4', {});
  b.drawFrame();
  tick();
  // then
  assert.equal(st.token, 2);
  assert.equal(b.getAttribute('src'), '/m/suni/think.mp4');
  assert.equal(b.classList.contains('is-on'), true);
  assert.equal(st.front, 0, '한 번만 바뀐다');
});

test('given 처음으로 되감는 중 when 화면 확인을 하면 then 되감기가 끝날 때까지 기다린다', () => {
  // given
  const { sw, tick } = load();
  const { a, b } = stage('/m/suni/speak.mp4');
  b.getAttribute = ((orig) => (n) => (n === 'src' ? '/m/suni/idle.mp4' : orig(n)))(b.getAttribute);
  b.readyState = 4;
  const st = { front: 1, token: 0 };
  // when
  sw.show([b, a], st, '/m/suni/idle.mp4', { restart: true });
  b.seeking = true;
  tick();
  // then
  assert.equal(b.classList.contains('is-on'), false);
  b.seeking = false;
  tick();
  assert.equal(b.classList.contains('is-on'), true);
});

test('given 타이머가 늦어지는 백그라운드 탭 when 되감기 완료 이벤트가 오면 then 타이머를 기다리지 않고 바로 바꾼다', () => {
  // given
  const { sw } = load();
  const { a, b } = stage('/m/suni/speak.mp4');
  b.getAttribute = ((orig) => (n) => (n === 'src' ? '/m/suni/idle.mp4' : orig(n)))(b.getAttribute);
  b.readyState = 4;
  const st = { front: 1, token: 0 };
  sw.show([b, a], st, '/m/suni/idle.mp4', { restart: true });
  // when: 타이머 실행 없이 이벤트만 옴
  b.emit('seeked');
  // then
  assert.equal(b.classList.contains('is-on'), true);
  assert.equal(st.front, 0);
  assert.deepEqual(b.handlers.seeked, [], '끝나면 이벤트 연결을 푼다');
});

test('given 다음 영상 불러오기 실패 when error 이벤트가 오면 then 기다리지 않고 바로 실패를 알리고 기존 화면을 둔다', () => {
  // given
  const { sw } = load();
  const { a, b } = stage('/m/suni/idle.mp4');
  const st = { front: 1, token: 0 };
  let failed = null;
  sw.show([b, a], st, '/m/suni/broken.mp4', { onFail: (r) => { failed = r; } });
  // when
  b.emit('error');
  // then
  assert.equal(failed, 'error');
  assert.equal(b.classList.contains('is-on'), false);
  assert.equal(a.classList.contains('is-on'), true);
  assert.deepEqual(b.handlers.error, []);
});
