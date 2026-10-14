const assert = require('node:assert/strict');
const test = require('node:test');
const path = require('node:path');

const { loadBrowserScript, memoryStorage } = require('./helpers/browser_script_harness');

const SCRIPT = path.join(__dirname, '../../main/resources/static/js/landing_buildit.js');

function startButton() {
  const listeners = {};
  return {
    el: {
      dataset: {},
      style: {},
      classList: { add() {}, remove() {}, toggle() {} },
      addEventListener(type, handler) { listeners[type] = handler; },
      querySelector() { return null; }
    },
    click(event) { return listeners.click(event); },
    submit(event) { return listeners.submit(event); }
  };
}

function harness({ user, sessionUser, loginModal = false, promptText = null }) {
  const button = startButton();
  const fetchCalls = [];
  const location = { href: '' };
  const storage = memoryStorage(sessionUser ? { currentUser: sessionUser } : {});
  const form = startButton();
  const signupLink = { href: '/register' };
  const loginListeners = {};
  let modalOpened = 0;
  const docListeners = [];
  const input = { value: promptText || '', focus() {} };
  const example = startButton();
  example.el.textContent = ' 화면에 보이는 문구 ';
  form.el.querySelector = (sel) => (sel === '[data-buildit-prompt]' ? input : null);
  const windowOpts = {
    addEventListener(type, handler) { loginListeners[type] = handler; },
    safeInternalReturnUrl(url) { return url; },
    scrollTo() {}
  };
  if (loginModal) windowOpts.openLoginModal = () => { modalOpened += 1; };
  loadBrowserScript(SCRIPT, {
    location,
    sessionStorage: storage,
    window: windowOpts,
    document: {
      addEventListener(type, handler, capture) { docListeners.push({ type, handler, capture }); },
      getElementById() { return null; },
      querySelector(selector) { return selector === '[data-buildit-prompt]' ? input : null; },
      querySelectorAll(selector) {
        if (selector === '[data-buildit-example]') return [example.el];
        if (selector === '[data-buildit-start]') return [button.el];
        if (selector === '[data-buildit-form]') return [form.el];
        if (selector === '[data-auth-signup]') return [signupLink];
        return [];
      }
    },
    async fetch(url) {
      fetchCalls.push(url);
      if (url !== '/api/auth/me') throw new Error(`unexpected fetch: ${url}`);
      if (!user) return { ok: false };
      return { ok: true, json: async () => user };
    }
  });
  return {
    button, fetchCalls, location, storage, signupLink, loginListeners,
    clickExample: () => example.click(), input,
    submit: (event) => form.submit(event), modalOpened: () => modalOpened,
    clickSignup() { const ev = { target: { closest: () => signupLink } }; docListeners.filter((l) => l.type === 'click').forEach((l) => l.handler(ev)); }
  };
}

const clickEvent = () => {
  let prevented = false;
  return { preventDefault() { prevented = true; }, get prevented() { return prevented; } };
};

test('Given a logged-in tutor, When 시작하기 is clicked, Then the builder opens directly', async () => {
  // given
  const { button, location, fetchCalls } = harness({ user: { role: 'TUTOR' } });

  // when
  const event = clickEvent();
  await button.click(event);

  // then
  assert.equal(event.prevented, true);
  assert.equal(location.href, '/builder');
  assert.deepEqual(fetchCalls, ['/api/auth/me']);
});

test('Given a logged-out visitor, When 시작하기 is clicked, Then the tutor signup gate keeps the builder return URL', async () => {
  // given
  const { button, location } = harness({ user: null });

  // when
  await button.click(clickEvent());

  // then
  assert.equal(location.href, '/register?returnUrl=%2Fbuilder');
});

test('Given a logged-in student, When 시작하기 is clicked, Then they are routed to the tutor signup gate, not the builder', async () => {
  // given
  const { button, location } = harness({ user: { role: 'STUDENT' } });

  // when
  await button.click(clickEvent());

  // then
  assert.equal(location.href, '/register?returnUrl=%2Fbuilder');
});

test('Given a cached session user, When 시작하기 is clicked, Then no auth request is sent', async () => {
  // given
  const { button, location, fetchCalls } = harness({ user: null, sessionUser: { role: 'TUTOR' } });

  // when
  await button.click(clickEvent());

  // then
  assert.equal(location.href, '/builder');
  assert.deepEqual(fetchCalls, []);
});

test('Given a logged-in ADMIN, When 시작하기 is clicked, Then the builder opens directly (not the signup gate)', async () => {
  // given
  const { button, location } = harness({ user: { role: 'ADMIN' } });

  // when
  await button.click(clickEvent());

  // then
  assert.equal(location.href, '/builder');
});

test('Given a typed request and a logged-in ADMIN, When the hero form is submitted, Then the request is kept and the builder opens', async () => {
  // given
  const { submit, location, storage } = harness({ user: { role: 'ADMIN' }, promptText: '  우리 영어학원 홈페이지를 만들어줘.  ' });

  // when
  const event = clickEvent();
  await submit(event);

  // then
  assert.equal(event.prevented, true);
  assert.equal(location.href, '/builder');
  const saved = storage.json('builditPendingPrompt');
  assert.equal(saved.text, '우리 영어학원 홈페이지를 만들어줘.');
  assert.equal(typeof saved.at, 'number');
});

test('Given a typed request and a logged-out visitor with the login modal, When submitted, Then the login modal opens and /builder is the post-login return', async () => {
  // given
  const { submit, location, storage, signupLink, modalOpened, clickSignup } = harness({ user: null, loginModal: true, promptText: '입시 학원 홈페이지' });

  // when
  await submit(clickEvent());

  // then
  assert.equal(modalOpened(), 1);
  assert.equal(location.href, '');
  assert.equal(storage.getItem('postLoginReturnUrl'), '/builder');
  assert.equal(storage.json('builditPendingPrompt').text, '입시 학원 홈페이지');
  clickSignup(); // 회원가입을 누르는 순간 /builder 복귀 주소가 된다
  assert.equal(signupLink.href, '/register?returnUrl=%2Fbuilder');
});

test('Given an empty input, When the hero form is submitted, Then no request is stored', async () => {
  // given
  const { submit, storage } = harness({ user: { role: 'TUTOR' }, promptText: '   ' });

  // when
  await submit(clickEvent());

  // then
  assert.equal(storage.getItem('builditPendingPrompt'), null);
});

test('Given a pending builder return, When an ADMIN or TUTOR logs in, Then the page moves to /builder', () => {
  for (const role of ['ADMIN', 'TUTOR']) {
    // given
    const { loginListeners, storage, location } = harness({ user: null, loginModal: true });
    storage.setItem('postLoginReturnUrl', '/builder');

    // when
    loginListeners['f2r:login']({ detail: { role } });

    // then
    assert.equal(location.href, '/builder', role);
  }
});

test('Given a pending builder return, When a STUDENT logs in, Then they stay on the landing and the stored request is dropped', () => {
  // given
  const { loginListeners, storage, location } = harness({ user: null, loginModal: true });
  storage.setItem('postLoginReturnUrl', '/builder');
  storage.setItem('builditPendingPrompt', JSON.stringify({ text: 'x', at: Date.now() }));

  // when
  loginListeners['f2r:login']({ detail: { role: 'STUDENT' } });

  // then
  assert.equal(location.href, '');
  assert.equal(storage.getItem('builditPendingPrompt'), null);
});
