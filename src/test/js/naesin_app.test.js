const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const STATIC = path.resolve(__dirname, '../../main/resources/static');
const TEMPLATES = path.resolve(__dirname, '../../main/resources/templates');
const NA = require(path.join(STATIC, 'js/naesin_app.js'));

test('given QR 주소의 #code when 읽으면 then 대문자 8글자, 틀린 글자·길이는 null', () => {
  // given / when / then
  assert.equal(NA.codeFromHash('#code=ab2c-de3f'), 'AB2CDE3F');
  assert.equal(NA.codeFromHash('#x=1&code=AB2CDE3F'), 'AB2CDE3F');
  assert.equal(NA.codeFromHash('#code=AB0CDE3F'), null, '0은 쓰지 않는 글자');
  assert.equal(NA.codeFromHash(''), null);
  assert.equal(NA.normalize(' ab2c de3f '), 'AB2CDE3F');
});

test('given 배정 시험 when 목록을 그리면 then 처음·다시·이어서 버튼 이름이 상태에 맞고 글자는 이스케이프된다', () => {
  // given
  const base = { examCode: 'TP-1', title: '<b>1회</b>', questionCount: 40, attempts: 0, lastScore: null, lastMax: null, inProgressAttemptId: null };
  // when
  const first = NA.examItem(base);
  const again = NA.examItem(Object.assign({}, base, { attempts: 2, lastScore: 80, lastMax: 100 }));
  const resume = NA.examItem(Object.assign({}, base, { inProgressAttemptId: 5 }));
  // then
  assert.match(first, />시험 보기</);
  assert.match(again, />다시 풀기</);
  assert.match(again, /2번 응시 · 최근 80 \/ 100점/);
  assert.match(resume, />이어서 풀기</);
  assert.ok(!first.includes('<b>1회</b>') && first.includes('&lt;b&gt;'));
});

test('given 내 응시 기록 when 그리면 then 몇 번째 응시·정답 수·걸린 시간과 앱 해설 주소가 나온다', () => {
  // given / when
  const html = NA.resultItem({ attemptId: 9, examTitle: '1회', score: 80, maxScore: 100, attemptNo: 2,
    submittedAt: '2026-11-26T16:12:00', correctCount: 30, totalCount: 40, durationSec: 754 });
  // then
  assert.match(html, /2번째 응시 · 11월 26일 16:12 · 30\/40 정답 · 12분 34초/);
  assert.match(html, /href="\/naesintest\/app\/review\?attempt=9"/);
});

test('given 학생용 manifest when 읽으면 then 원장 앱과 id·이름·아이콘이 겹치지 않고 범위는 /naesintest/', () => {
  // given
  const app = JSON.parse(fs.readFileSync(path.join(STATIC, 'manifest-naesintest.json'), 'utf8'));
  const admin = JSON.parse(fs.readFileSync(path.join(STATIC, 'manifest.json'), 'utf8'));
  // when / then
  assert.equal(app.id, '/naesintest/app');
  assert.equal(app.start_url, '/naesintest/app');
  assert.equal(app.scope, '/naesintest/');
  assert.equal(app.name, 'F2R 디지털 모의고사');
  assert.notEqual(app.id, admin.id);
  assert.notEqual(app.short_name, admin.short_name);
  for (const icon of app.icons) {
    assert.ok(fs.existsSync(path.join(STATIC, icon.src)), icon.src);
    assert.ok(!admin.icons.some(a => a.src === icon.src), '원장 앱 아이콘과 다른 파일');
  }
});

test('given 학생 앱 화면 when 읽으면 then 학생용 manifest·코드 입력만 있고 이름·연락처 입력 칸이 없으며 안드로이드·아이폰 설치 안내가 따로 있다', () => {
  // given
  const html = fs.readFileSync(path.join(TEMPLATES, 'naesintest/app.html'), 'utf8');
  // when / then
  assert.match(html, /<link rel="manifest" href="\/manifest-naesintest.json">/);
  assert.ok(!/manifest\.json"/.test(html.replace('manifest-naesintest.json', '')), '원장 앱 manifest를 쓰지 않는다');
  assert.equal((html.match(/<input /g) || []).length, 1, '입력 칸은 접속 코드 하나');
  assert.ok(!/name="(name|phone|tel)"|type="tel"/.test(html));
  assert.match(html, /안드로이드\(크롬\)/);
  assert.match(html, /아이폰\(사파리\)/);
});
