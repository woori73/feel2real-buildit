/*
 * 패널 레이아웃/자동화 금지 계약 테스트 (2026-09-24 REAL CHROME QA 후).
 * 소스 텍스트 기반 정적 계약: 실행 없이도 "바닥 고정 폐기/우상단 고정/가로넘침 방지/
 * 접기 유지/어떤 자동 조작 API도 없음"을 회귀로부터 보호한다.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const here = dirname(fileURLToPath(import.meta.url));
const content = readFileSync(join(here, '..', 'src', 'fssContent.js'), 'utf8');
const detector = readFileSync(join(here, '..', 'src', 'fssStepDetector.js'), 'utf8');
const background = readFileSync(join(here, '..', 'src', 'background.js'), 'utf8');
const newstart = readFileSync(join(here, '..', 'src', 'newstartContent.js'), 'utf8');
const allSrc = content + detector + background + newstart;

test('desktop: bottom 고정 폐기 — 우상단(top:90px, right:20px) 고정', () => {
  assert.ok(/position:fixed;top:90px;right:20px/.test(content), 'top-right fixed 필요');
  assert.ok(!/(?:^|[;{])(?:bottom):\s*\d+px/m.test(content), '하단 고정 좌표가 남아있으면 안 됨');
  assert.ok(/@media \(max-width:640px\)\{\.wrap\{top:8px;left:8px;right:8px;width:auto/.test(content),
    '좁은 화면 상단 full-width 전환 필요');
});

test('너비 과다 증가 없음 + viewport 밖 overflow 방지', () => {
  assert.match(content, /width:300px;max-width:calc\(100vw - 40px\)/);
});

test('접기/펼치기 유지 (collapse toggle regression)', () => {
  assert.match(content, /wrap\.classList\.toggle\('is-min'\)/);
  assert.match(content, /\.wrap\.is-min \.bd\{display:none\}/);
});

test('시각 우선순위 DOM 순서: 단계 → 안내(행동) → 근거 → 수동 안내', () => {
  const i1 = content.indexOf('bd.appendChild(refs.step)');
  const i2 = content.indexOf('bd.appendChild(refs.guide)');
  const i3 = content.indexOf('bd.appendChild(refs.ev)');
  const i4 = content.indexOf('bd.appendChild(ft)');
  assert.ok(i1 >= 0 && i1 < i2 && i2 < i3 && i3 < i4, 'appendChild 순서: step>guide>ev>ft');
  assert.match(content, /\.st\{font-weight:800;font-size:15px/);
  assert.match(content, /\.st\.is-unknown\{color:#b91c1c\}/); /* 실패 메시지를 즉시 인식 */
});

test('자동 조작 API 전면 금지 (§12) — click()/submit()/dispatchEvent/value 대입 없음', () => {
  for (const [name, src] of [['fssContent', content], ['detector', detector],
    ['background', background], ['newstartContent', newstart]]) {
    assert.ok(!/\.click\(\s*\)/.test(src), name + ': .click() 호출 금지');
    assert.ok(!/\.submit\(/.test(src), name + ': .submit() 금지');
    assert.ok(!/dispatchEvent|new (?:Mouse|Pointer|Keyboard)Event/.test(src), name + ': 이벤트 합성 금지');
    assert.ok(!/\.value\s*=[^=]/.test(src), name + ': value 대입(자동 입력) 금지');
    assert.ok(!/\.checked\s*=|\.selected\s*=/.test(src), name + ': 체크 상태 조작 금지');
    assert.ok(!/window\.open\(/.test(src), name + ': window.open 금지');
    assert.ok(!/\beval\(|new Function\(/.test(src), name + ': eval 금지');
  }
});

test('URL stage detection 회귀: STEP 1~4 라우팅 문구/근거 그대로', () => {
  assert.match(detector, /\/forinsertselect\.do/);
  assert.match(detector, /\/forinsertagre\.do/);
  assert.match(detector, /\/fss\/minwon\/search\.jsp/);
  assert.match(detector, /\/forinsertstep1\.do/); /* 신규: 개인정보 화면 인식(안내 전용) */
  assert.ok(detector.indexOf('/forinsertselect.do') < detector.indexOf('/forinsertstep1.do'),
    'STEP 3/4 판별 경로가 STEP 5보다 먼저 평가된다(순서 회귀 방지)');
});
