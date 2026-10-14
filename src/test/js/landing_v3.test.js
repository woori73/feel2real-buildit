const assert = require('node:assert/strict');
const test = require('node:test');
const path = require('node:path');

const v3 = require(path.join(__dirname, '../../main/resources/static/js/landing_v3.js'));

function classList() {
  const set = new Set();
  return {
    add(c) { set.add(c); },
    toggle(c, on) { if (on) set.add(c); else set.delete(c); },
    contains(c) { return set.has(c); }
  };
}

function el(attrs = {}) {
  const listeners = {};
  return {
    attrs: { ...attrs },
    classList: classList(),
    tabIndex: 0,
    disabled: false,
    textContent: '',
    focused: false,
    setAttribute(k, v) { this.attrs[k] = v; },
    getAttribute(k) { return this.attrs[k]; },
    addEventListener(type, fn) { listeners[type] = fn; },
    fire(type, ev) { return listeners[type](ev); },
    focus() { this.focused = true; },
    get id() { return this.attrs.id; },
    querySelector() { return null; }
  };
}

test('Given 탭 3개, When 방향키·Home·End를 누르면, Then 순환·처음·끝 인덱스를 돌려준다', () => {
  // given
  const total = 3;
  // when / then
  assert.equal(v3.nextTabIndex('ArrowRight', 2, total), 0);
  assert.equal(v3.nextTabIndex('ArrowLeft', 0, total), 2);
  assert.equal(v3.nextTabIndex('Home', 2, total), 0);
  assert.equal(v3.nextTabIndex('End', 0, total), 2);
});

test('Given 처리하지 않는 키나 탭이 없을 때, When 이동을 계산하면, Then -1', () => {
  assert.equal(v3.nextTabIndex('Enter', 1, 3), -1);
  assert.equal(v3.nextTabIndex('ArrowRight', 0, 0), -1);
});

test('Given 타자 효과 시작 상태, When 한 프레임씩 진행하면, Then 글자가 늘고 끝에서 멈췄다 지운다', () => {
  // given
  const texts = ['가나', '다'];
  let state = { i: 0, j: 0, del: false };
  // when
  state = v3.typedStep(state, texts);
  assert.equal(state.text, '가');
  state = v3.typedStep(state, texts);
  // then: 다 쓰면 잠깐 멈춘 뒤 지우기 시작
  assert.equal(state.text, '가나');
  assert.equal(state.del, true);
  assert.equal(state.delay, 1600);
});

test('Given 지우는 중, When 모두 지워지면, Then 다음 문장으로 넘어간다(순환)', () => {
  const texts = ['가', '나'];
  const state = v3.typedStep({ i: 1, j: 1, del: true }, texts);
  assert.equal(state.text, '');
  assert.equal(state.i, 0);
  assert.equal(state.del, false);
});

test('Given 문의 모달과 함수가 있을 때, When 도입 문의를 점검하면, Then 버튼을 그대로 둔다', () => {
  // given
  const btn = el();
  const doc = { getElementById: (id) => (id === 'contactModal' ? {} : null), querySelectorAll: () => [btn] };
  const win = { openContactModal() {} };
  // when
  const disabled = v3.guardInquiry(doc, win);
  // then
  assert.equal(disabled, 0);
  assert.equal(btn.disabled, false);
});

test('Given 문의 모달이 없을 때, When 도입 문의를 점검하면, Then 비활성·"준비 중"으로 표시한다', () => {
  // given
  const btn = el();
  const doc = { getElementById: () => null, querySelectorAll: () => [btn] };
  const win = { openContactModal() {} };
  // when
  const disabled = v3.guardInquiry(doc, win);
  // then
  assert.equal(disabled, 1);
  assert.equal(btn.disabled, true);
  assert.equal(btn.attrs['aria-disabled'], 'true');
  assert.equal(btn.textContent, '준비 중');
});

test('Given openContactModal 함수가 없을 때, When 점검하면, Then 모달이 있어도 비활성', () => {
  const btn = el();
  const doc = { getElementById: () => ({}), querySelectorAll: () => [btn] };
  assert.equal(v3.guardInquiry(doc, {}), 1);
  assert.equal(btn.disabled, true);
});

test('Given 탭 3개와 패널 3개, When 두 번째 탭을 누르면, Then 해당 패널만 열리고 aria-selected가 바뀐다', () => {
  // given
  const tabs = ['web', 'chart', 'easy'].map((k, i) => el({ id: `t-${k}`, 'aria-controls': `p-${k}`, 'aria-selected': String(i === 0) }));
  const panes = ['web', 'chart', 'easy'].map((k) => el({ id: `p-${k}` }));
  const doc = { querySelectorAll: (sel) => (sel === '.v3-tab' ? tabs : sel === '.v3-pane' ? panes : []) };
  v3.initTabs(doc);
  // when
  tabs[1].fire('click');
  // then
  assert.deepEqual(tabs.map((t) => t.attrs['aria-selected']), ['false', 'true', 'false']);
  assert.deepEqual(tabs.map((t) => t.tabIndex), [-1, 0, -1]);
  assert.deepEqual(panes.map((p) => p.classList.contains('on')), [false, true, false]);
});

test('Given 첫 탭, When ArrowRight를 누르면, Then 다음 탭이 선택되고 포커스가 이동한다', () => {
  // given
  const tabs = ['a', 'b'].map((k, i) => el({ id: `t-${k}`, 'aria-controls': `p-${k}`, 'aria-selected': String(i === 0) }));
  const panes = ['a', 'b'].map((k) => el({ id: `p-${k}` }));
  const doc = { querySelectorAll: (sel) => (sel === '.v3-tab' ? tabs : sel === '.v3-pane' ? panes : []) };
  v3.initTabs(doc);
  let prevented = false;
  // when
  tabs[0].fire('keydown', { key: 'ArrowRight', preventDefault() { prevented = true; } });
  // then
  assert.equal(prevented, true);
  assert.equal(tabs[1].focused, true);
  assert.equal(tabs[1].attrs['aria-selected'], 'true');
});

test('Given IntersectionObserver가 없을 때, When 등장 연출을 초기화하면, Then v3-js를 붙이지 않아 내용이 항상 보인다', () => {
  // given
  const root = { classList: classList() };
  const doc = { querySelectorAll: () => [el()], documentElement: root };
  // when
  v3.initReveal(doc, {});
  // then
  assert.equal(root.classList.contains('v3-js'), false);
});

test('Given 행이 3개 이하일 때, When 더보기 상태를 계산하면, Then 버튼을 숨긴다', () => {
  // given/when
  const state = v3.chartMoreState(3, false);
  // then
  assert.equal(state.hidden, true);
  assert.equal(state.label, '');
});

test('Given 행이 5개일 때, When 접힌/펼친 상태를 계산하면, Then 남은 개수 라벨과 접기 라벨을 돌려준다', () => {
  // given/when
  const closed = v3.chartMoreState(5, false);
  const open = v3.chartMoreState(5, true);
  // then
  assert.deepEqual([closed.hidden, closed.expanded, closed.label], [false, false, '나머지 2개 펼치기']);
  assert.deepEqual([open.hidden, open.expanded, open.label], [false, true, '접기']);
});

test('Given TOP5 행과 더보기 버튼, When 버튼을 누르면, Then is-open과 aria-expanded가 토글된다', () => {
  // given
  const rows = [1, 2, 3, 4, 5].map(() => el());
  const root = Object.assign(el({ id: 'landingChartTop5' }), { querySelectorAll: () => rows });
  const button = el({ id: 'landingChartMore' });
  button.hidden = true;
  const doc = { getElementById: (id) => (id === 'landingChartTop5' ? root : id === 'landingChartMore' ? button : null) };
  v3.initChartMore(doc, {});
  assert.equal(button.hidden, false);
  assert.equal(button.textContent, '나머지 2개 펼치기');
  // when
  button.fire('click');
  // then
  assert.equal(root.classList.contains('is-open'), true);
  assert.equal(button.attrs['aria-expanded'], 'true');
  assert.equal(button.textContent, '접기');
});

test('Given 문구 3개, When 다음 순번을 계산하면, Then 마지막 다음은 처음으로 돌아간다', () => {
  // given/when/then
  assert.equal(v3.nextWordIndex(0, 3), 1);
  assert.equal(v3.nextWordIndex(2, 3), 0);
  assert.equal(v3.nextWordIndex(0, 0), 0);
});

test('Given 움직임 줄이기 설정, When 문구 순환을 시작하면, Then 순환하지 않아 세 문구가 모두 보인다', () => {
  // given
  const title = Object.assign(el(), { querySelectorAll: () => [el(), el(), el()] });
  const doc = { querySelector: () => title };
  const win = { matchMedia: () => ({ matches: true }), setInterval() { throw new Error('호출되면 안 된다'); } };
  // when
  v3.initWords(doc, win);
  // then
  assert.equal(title.classList.contains('fm-rot'), false);
});

test('Given 지역 4개, When 두 번째 지역을 누르면, Then 그 지역과 사진이 켜지고 핀 위치와 이름표가 바뀐다', () => {
  // given
  const regions = ['서울', '부산', '대구', '광주'].map((name, i) => {
    const r = el({ 'data-nation-region': String(i), 'data-x': String(10 * (i + 1)), 'data-y': String(20 * (i + 1)), 'aria-pressed': String(i === 0) });
    r.textContent = name;
    return r;
  });
  const photos = regions.map(() => el());
  const pin = Object.assign(el(), { style: {} });
  const chip = el();
  const root = Object.assign(el(), {
    querySelectorAll: (sel) => (sel === '[data-nation-region]' ? regions : sel === '.nation-photo' ? photos : []),
    querySelector: (sel) => (sel === '[data-nation-pin]' ? pin : sel === '[data-nation-chip]' ? chip : null)
  });
  const doc = { querySelector: () => root };
  const win = { matchMedia: () => ({ matches: true }), setInterval() {}, clearInterval() {} };
  v3.initNation(doc, win);
  // when
  regions[1].fire('click');
  // then
  assert.equal(regions[1].attrs['aria-pressed'], 'true');
  assert.equal(regions[0].attrs['aria-pressed'], 'false');
  assert.equal(photos[1].classList.contains('on'), true);
  assert.equal(pin.style.left, '20%');
  assert.equal(pin.style.top, '40%');
  assert.equal(chip.textContent, '부산');
});

// ---- landing_chart.js: 빌보드 형식 한 줄 (사진·지표 없음) ----
const { bbRow } = require('../../main/resources/static/js/landing_chart.js');
const escHtml = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

test('given 1위·지난주 2위 when bbRow then 이름·학원·교육 철학과 상승 화살표를 만들고 사진은 만들지 않는다', () => {
  // given
  const entry = { rank: 1, previousRank: 2, educatorId: 7, name: '홍길동', academyName: '가나다학원', identitySummary: '문법의 기본기', profileImageUrl: 'http://x/y.jpg' };
  // when
  const html = bbRow(entry, escHtml);
  // then
  assert.ok(html.includes('bb-first'));
  assert.ok(html.includes('▲'));
  assert.ok(html.includes('홍길동') && html.includes('가나다학원') && html.includes('문법의 기본기'));
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('/chart/educators/7'));
});

test('given 신규·학원 없음·특수문자 이름 when bbRow then NEW이고 빈 항목은 만들지 않으며 이름은 이스케이프된다', () => {
  // given
  const entry = { rank: 3, previousRank: null, educatorId: 'a b', name: '<b>김</b>' };
  // when
  const html = bbRow(entry, escHtml);
  // then
  assert.ok(html.includes('NEW'));
  assert.ok(!html.includes('bb-academy') && !html.includes('bb-philosophy'));
  assert.ok(!html.includes('<b>김</b>'));
  assert.ok(html.includes('a%20b'));
});
