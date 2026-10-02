const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const read = (relative) => fs.readFileSync(path.resolve(__dirname, relative), 'utf8');

const scriptSrc = read('../../main/resources/static/js/admin_analytics.js');
const templateSrc = read('../../main/resources/templates/admin/analytics.html');

const period = (today, recent7, recent30, total) => ({today, recent7, recent30, total});

/** admin_analytics.js 를 실제 실행시켜 렌더 결과를 회수한다 (DOM 스텁). */
async function render(summary) {
  const elements = new Map();
  const element = (id) => {
    if (!elements.has(id)) elements.set(id, {id, innerHTML: '', textContent: ''});
    return elements.get(id);
  };
  const document = {getElementById: element};
  const fetchAuth = async () => ({ok: true, json: async () => summary});
  const ensureCurrentUser = async () => ({role: 'ADMIN'});
  const run = new Function(
      'document', 'window', 'fetchAuth', 'ensureCurrentUser', 'location', scriptSrc);
  run(document, {userDisplayName: () => '운영자'}, fetchAuth, ensureCurrentUser, {href: ''});

  for (let i = 0; i < 50 && !element('serviceTrafficBody').innerHTML.includes('</tr>'); i++) {
    await new Promise((resolve) => setImmediate(resolve));
  }
  return {
    card: element('newStartVisitorsMetrics').innerHTML,
    serviceTable: element('serviceTrafficBody').innerHTML,
  };
}

const summary = {
  siteVisitors: period(100, 200, 300, 400),
  siteViews: period(101, 201, 301, 401),
  wikiVisitors: period(102, 202, 302, 402),
  wikiViews: period(103, 203, 303, 403),
  newStartVisitors: period(5, 20, 50, 80),
  wikiDownloads: period(104, 204, 304, 404),
  botRequests: period(105, 205, 305, 405),
  statsSince: '2026-09-01',
  serviceTraffic: [
    {service: 'HOME', visitors: period(1, 2, 3, 4), views: period(5, 6, 7, 8)},
    {service: 'WIKI', visitors: period(9, 10, 11, 12), views: period(13, 14, 15, 16)},
    {service: 'NEWSTART', visitors: period(5, 20, 50, 80), views: period(6, 21, 51, 90)},
    {service: 'OTHER', visitors: period(17, 18, 19, 20), views: period(21, 22, 23, 24)},
  ],
  recentVisits: [],
  recentWikiVisits: [],
  recentDownloads: [],
  topDownloads: [],
};

test('given ADMIN 통계 화면 when 렌더 then "NewStart 방문자 (UV)" 카드가 상단에 있다', async () => {
  assert.match(templateSrc, /<h3>NewStart 방문자 \(UV\)<\/h3>\s*<div class="analytics-metrics" id="newStartVisitorsMetrics">/);
  const {card} = await render(summary);
  assert.match(card, />5</, '오늘 UV');
  assert.match(card, />20</, '7일 UV');
  assert.match(card, />50</, '30일 UV');
  assert.match(card, />80</, '누적 UV');
});

test('given 서비스별 방문 표 when NEWSTART 행이 들어오면 라벨을 "NewStart"로 렌더한다', async () => {
  const {serviceTable} = await render(summary);
  const rows = serviceTable.split('</tr>');
  const newStartRow = rows.find((row) => row.includes('NewStart'));
  assert.ok(newStartRow, 'NewStart 행이 있어야 한다');
  assert.doesNotMatch(newStartRow, /NEWSTART/, 'enum 원문이 아니라 라벨을 쓴다');
  assert.match(newStartRow, /<td class="num">5<\/td>\s*<td class="num">6<\/td>/, '오늘 UV/PV');
  assert.match(newStartRow, /<td class="num">80<\/td>\s*<td class="num">90<\/td>/, '누적 UV/PV');
});

test('given NewStart 분리 when 기존 행을 렌더 then 메인/랜딩·Wiki·기타 라벨과 수치가 그대로다', async () => {
  const {serviceTable} = await render(summary);
  assert.match(serviceTable, /메인\/랜딩/);
  assert.match(serviceTable, /Wiki/);
  assert.match(serviceTable, /기타/);
  const otherRow = serviceTable.split('</tr>').find((row) => row.includes('기타'));
  assert.match(otherRow, /<td class="num">17<\/td>/, 'OTHER 누적 UV는 NewStart와 무관');
});
