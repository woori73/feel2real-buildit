const test = require('node:test');
const assert = require('node:assert/strict');
const { init } = require('../../main/resources/static/js/home_topnav.js');

function el() {
  const classes = new Set(), attrs = {}, handlers = {};
  return { classList: { toggle: (c, on) => (on ? classes.add(c) : classes.delete(c)), contains: c => classes.has(c) },
    setAttribute: (k, v) => { attrs[k] = v; }, attrs, handlers, focused: false, focus() { this.focused = true; },
    addEventListener: (ev, fn) => { handlers[ev] = fn; } };
}
function setup() {
  const items = [0, 1].map(() => {
    const dd = el(), btn = el(), link = el();
    dd.querySelector = s => (s === '.hl-dd-btn' ? btn : null);
    dd.querySelectorAll = () => [link];
    dd.contains = t => t === dd || t === btn || t === link;
    return { dd, btn, link };
  });
  const docHandlers = {};
  const doc = { querySelectorAll: () => items.map(i => i.dd), addEventListener: (ev, fn) => { docHandlers[ev] = fn; } };
  init(doc);
  return { items, docHandlers };
}

test('given two menus when one button is pressed then only it opens and aria-expanded follows', () => {
  // given
  const s = setup();
  // when
  s.items[0].btn.handlers.click();
  s.items[1].btn.handlers.click();
  // then
  assert.equal(s.items[0].dd.classList.contains('is-open'), false);
  assert.equal(s.items[1].dd.classList.contains('is-open'), true);
  assert.equal(s.items[1].btn.attrs['aria-expanded'], 'true');
  assert.equal(s.items[0].btn.attrs['aria-expanded'], 'false');
});

test('given an open menu when Esc is pressed then it closes and focus returns to its button', () => {
  // given
  const s = setup();
  s.items[0].btn.handlers.click();
  // when
  s.docHandlers.keydown({ key: 'Escape' });
  // then
  assert.equal(s.items[0].dd.classList.contains('is-open'), false);
  assert.equal(s.items[0].btn.focused, true);
});

test('given an open menu when a link inside or the page outside is pressed then it closes', () => {
  // given
  const s = setup();
  s.items[0].btn.handlers.click();
  // when
  s.items[0].link.handlers.click();
  // then
  assert.equal(s.items[0].dd.classList.contains('is-open'), false);
  // given / when
  s.items[1].btn.handlers.click();
  s.docHandlers.click({ target: {} });
  // then
  assert.equal(s.items[1].dd.classList.contains('is-open'), false);
});
