'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SRC = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/eduholic_final_banner.js'), 'utf8');

function makeDom(hostname) {
  const created = [];
  function node(tag) {
    return {
      tag, id: '', className: '', textContent: '', children: [], attrs: {}, href: '',
      appendChild(c) { this.children.push(c); return c; },
      setAttribute(k, v) { this.attrs[k] = v; },
      insertAdjacentElement(pos, el) { created.push({ pos, el }); }
    };
  }
  const hero = node('section'); hero.id = 'sec-hero';
  const byId = { 'sec-hero': hero };
  const document = {
    readyState: 'complete',
    head: { appendChild() {} },
    body: {},
    createElement: (t) => node(t),
    createTextNode: (t) => ({ text: t }),
    getElementById: (id) => byId[id] || null,
    addEventListener() {}
  };
  const window = { location: { hostname } };
  return { document, window, created };
}

test('given eduholic host when script runs then a final-exam banner with three school links goes right after the hero', () => {
  // given
  const { document, window, created } = makeDom('eduholic.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.strictEqual(created.length, 1);
  assert.strictEqual(created[0].pos, 'afterend');
  const banner = created[0].el;
  const title = banner.children.find((c) => c.tag === 'h2');
  assert.strictEqual(title.textContent, '2026 2학기 기말고사 내신 LIVE');
  const list = banner.children.find((c) => c.className === 'efb-schools');
  assert.deepStrictEqual(list.children.map((a) => a.href), [
    'https://feel2real.co.kr/naesintest/school/apgujeong-m2',
    'https://feel2real.co.kr/naesintest/school/singu-m2',
    'https://feel2real.co.kr/naesintest/school/apgujeong-m1'
  ]);
});

test('given another academy host when script runs then nothing is inserted', () => {
  // given
  const { document, window, created } = makeDom('other.feel2real.co.kr');
  // when
  vm.runInNewContext(SRC, { document, window, MutationObserver: function () {}, setTimeout() {} });
  // then
  assert.strictEqual(created.length, 0);
});
