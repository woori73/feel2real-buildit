'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const read = (p) => fs.readFileSync(path.resolve(__dirname, p), 'utf8');
const CSS = read('../../main/resources/static/css/newstart.css');
const TOOLS_CSS = read('../../main/resources/static/css/newstart-tools.css');
const MOTION_JS = read('../../main/resources/static/js/newstart-motion.js');
const HOME_JS = read('../../main/resources/static/js/newstart.js');
const INDEX = read('../../main/resources/templates/newstart/index.html');
const FSS = read('../../main/resources/templates/newstart/fss-helper.html');
const TOOL = read('../../main/resources/templates/newstart/practical-tool.html');

/* ── 공통 레이어: 토큰 · reduced-motion 하드 게이트 ── */

test('NewStart motion tokens define FAST/NORMAL/GUIDED durations with shared easing', () => {
  assert.match(CSS, /--ns-motion-fast:\s*1[0-9]{2}ms/);
  assert.match(CSS, /--ns-motion-normal:\s*[23][0-9]{2}ms/);
  assert.match(CSS, /--ns-motion-guided:\s*[456][0-9]{2}ms/); // 사람 눈에 인지되는 진입 강도 (Phase 1 가시성 재작업)
  assert.match(CSS, /--ns-ease-out:/);
});

test('prefers-reduced-motion reduce is a hard gate that stops all NewStart motion', () => {
  const gate = CSS.match(/@media \(prefers-reduced-motion: reduce\)[\s\S]*?\n\}/);
  assert.ok(gate, 'reduce block must exist in newstart.css');
  assert.match(gate[0], /animation-duration:\s*\.01ms\s*!important/);
  assert.match(gate[0], /transition-duration:\s*\.01ms\s*!important/);
  // reveal 대상은 reduce에서 반드시 즉시 노출(콘텐츠 보존)
  assert.match(gate[0], /\[data-ns-reveal\][\s\S]*opacity:\s*1\s*!important/);
});

test('scroll reveal hides content only when JS opted in (ns-motion-on) — no-JS users see everything', () => {
  assert.match(CSS, /\.ns-motion-on \[data-ns-reveal\]\s*\{\s*opacity:\s*0/);
  assert.match(CSS, /\.ns-motion-on \[data-ns-reveal\]\.ns-revealed/);
  assert.doesNotMatch(CSS, /^\s*\[data-ns-reveal\]\s*\{\s*opacity:\s*0/m);
});

test('motion bootstrap respects reduced motion, IntersectionObserver absence, and reveals once', () => {
  assert.match(MOTION_JS, /prefers-reduced-motion: reduce/);
  assert.match(MOTION_JS, /typeof IntersectionObserver !== 'function'\) return/);
  assert.match(MOTION_JS, /classList\.add\('ns-motion-on'\)/);
  assert.match(MOTION_JS, /classList\.add\('ns-revealed'\)/);
  assert.match(MOTION_JS, /io\.unobserve\(entry\.target\)/); // 1회성: 되돌아와도 재재생 금지
});

/* ── HOME: 진입 reveal + 지피지기 도착 강조 ── */

test('HOME marks hero, five practical cards, service cards, AI and updates for one-time reveal', () => {
  assert.match(INDEX, /class="ns-hero"[^>]*data-ns-reveal/);
  const revealedCards = INDEX.match(/<a class="ns-feature-card" href="\/newstart\/[^"]*"[^>]*data-ns-reveal/g) || [];
  assert.equal(revealedCards.length, 5);
  assert.match(INDEX, /ns-service-atoz" href="\/newstart\/a-to-z" data-ns-reveal/);
  assert.match(INDEX, /id="ns-ai"[^>]*data-ns-reveal/);
  assert.match(INDEX, /id="ns-updates"[^>]*data-ns-reveal/);
  // 지연은 토큰 기반 스태거, 레이아웃 이동(클릭 영역)은 불변
  assert.match(INDEX, /--ns-reveal-delay:240ms/);
  assert.match(INDEX, /--ns-reveal-delay:600ms/);
});

test('HOME keeps the 지피지기 anchor contract and loads the motion bootstrap before newstart.js', () => {
  assert.match(INDEX, /href="#ns-ai"/);
  assert.match(INDEX, /src="\/js\/newstart-motion\.js\?v=[\d-]+" defer/);
  const motionPos = INDEX.indexOf('newstart-motion.js');
  const mainPos = INDEX.indexOf('/js/newstart.js');
  assert.ok(motionPos > 0 && mainPos > motionPos, 'motion js must load before newstart.js');
});

test('지피지기 arrival emphasis is one-time and works without JS via :target', () => {
  assert.match(CSS, /#ns-ai:target,\s*\.ns-ai-arrival\s*\{\s*animation:\s*ns-ai-arrival var\(--ns-motion-guided\)[^;]*1;\s*\}/);
  assert.match(CSS, /@keyframes ns-ai-arrival/);
  assert.doesNotMatch(CSS, /animation:[^;]*infinite/);
});

test('HOME debt badge and save feedback animate only on real state changes with reduced-motion guard', () => {
  assert.match(HOME_JS, /flashOnce\(el, cls\)[\s\S]*prefers-reduced-motion: reduce/);
  assert.match(HOME_JS, /if \(prev && prev !== \(data\.status \|\| 'NOT_STARTED'\)\) flashOnce\(homeState, 'ns-debt-flash'\)/);
  assert.match(HOME_JS, /flashOnce\(stateEl, 'ns-debt-flash'\)/);
  assert.match(HOME_JS, /flashOnce\(aiSection, 'ns-ai-arrival'\)/);
  assert.match(CSS, /\.ns-debt-home-state\.ns-debt-flash\s*\{\s*animation:/);
});

test('smooth scrolling degrades to instant movement under reduced motion', () => {
  assert.match(HOME_JS, /scrollBehavior\(\)\s*\{[\s\S]*prefers-reduced-motion: reduce[\s\S]*'auto' : 'smooth'/);
  assert.doesNotMatch(HOME_JS, /behavior: 'smooth'/);
});

/* ── FSS: 3행동 히어로 구조 불변 + guided reveal ── */

test('FSS hero keeps the exact 3-action structure and 지피지기 destination', () => {
  assert.match(FSS, /id="ns-ext-install"/);
  assert.match(FSS, /id="ns-ag-open"/);
  assert.match(FSS, /href="\/newstart#ns-ai" id="ns-fss-ask-ai"/);
  assert.match(FSS, /class="ns-card ns-fss-hero"[^>]*data-ns-reveal/);
  assert.match(FSS, /src="\/js\/newstart-motion\.js\?v=[\d-]+" defer/);
});

test('FSS keeps four manual screens as ns-fss-screen and guides question changes once', () => {
  const screens = FSS.match(/<div id="ns-fss-screen\d" class="ns-fss-screen"/g) || [];
  assert.equal(screens.length, 4);
  assert.match(FSS, /guidedFlash\(\$\('ns-fss-screen' \+ n\), 'ns-fss-enter'\)/);
  assert.match(FSS, /renderInterview\(\);\s*\n\s*guidedFlash\(\$\('ns-iv-body'\), 'ns-iv-enter'\)/);
});

test('FSS motion is scoped to no-preference and never loops', () => {
  assert.match(FSS, /@media \(prefers-reduced-motion: no-preference\)/);
  assert.match(FSS, /animation:ns-fss-guided-enter var\(--ns-motion-guided\)[^;]* 1\}/);
  assert.doesNotMatch(FSS, /infinite/);
  assert.match(FSS, /function scrollGently[\s\S]*reduce[\s\S]*'smooth' : 'auto'/);
});

test('FSS privacy-critical textContent-only rendering survives the motion edits', () => {
  assert.doesNotMatch(FSS, /\.innerHTML\s*=/);
});

/* ── Credit4U: 9단계 절차 인식(문구·정보 불변) ── */

test('Credit4U keeps nine guide steps and reveals them as a staggered one-time group', () => {
  const ol = TOOL.match(/<ol class="ns-guide-steps" data-ns-reveal-group>([\s\S]*?)<\/ol>/);
  assert.ok(ol, 'guide steps must be a reveal group');
  assert.equal((ol[1].match(/<li>/g) || []).length, 9);
  ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧', '⑨'].forEach((no) => assert.ok(ol[1].includes(no)));
  assert.match(TOOLS_CSS, /\.ns-motion-on \[data-ns-reveal-group\]>li\{opacity:0/);
  assert.match(TOOLS_CSS, /\.ns-revealed>li:nth-child\(9\)\{transition-delay:480ms\}/);
  assert.match(TOOL, /src="\/js\/newstart-motion\.js\?v=[\d-]+" defer/);
});

test('Credit4U safety copy and official links are untouched', () => {
  assert.match(TOOL, /https:\/\/www\.credit4u\.or\.kr:2443\//);
  assert.match(TOOL, /\/newstart#ns-status-panel/);
  assert.match(TOOL, /NewStart는 ID, 비밀번호, 인증번호, 공동·금융인증 정보를 받거나 Credit4U에 대신 로그인하지 않습니다\./);
});

/* ── 의존성 금지: CSS/WAAPI/IntersectionObserver만 사용 ── */

test('motion phase 1 adds no third-party animation dependency', () => {
  [MOTION_JS, HOME_JS, CSS, TOOLS_CSS, INDEX, FSS, TOOL].forEach((src) => {
    assert.doesNotMatch(src, /lottie|gsap|after-effects|anime\.js|<script[^>]*src="https?:/i);
  });
});
