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
    click(event) { return listeners.click(event); }
  };
}

function harness({ user, sessionUser }) {
  const button = startButton();
  const fetchCalls = [];
  const location = { href: '' };
  loadBrowserScript(SCRIPT, {
    location,
    sessionStorage: memoryStorage(sessionUser ? { currentUser: sessionUser } : {}),
    document: {
      addEventListener() {},
      getElementById() { return null; },
      querySelector() { return null; },
      querySelectorAll(selector) {
        return selector === '[data-buildit-start]' ? [button.el] : [];
      }
    },
    async fetch(url) {
      fetchCalls.push(url);
      if (url !== '/api/auth/me') throw new Error(`unexpected fetch: ${url}`);
      if (!user) return { ok: false };
      return { ok: true, json: async () => user };
    }
  });
  return { button, fetchCalls, location };
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
