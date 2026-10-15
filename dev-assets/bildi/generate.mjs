/**
 * Bildi(빌디) 캐릭터 state asset 생성기.
 *
 * 한 벌의 base 캐릭터(reference 시안: 노란 병아리 · 큰 눈 · 이마 고글 · BuildIt-blue 스카프+배지 ·
 * 비행 시 크게 펼쳐지는 날개)를 state별 pose delta 로 변형해 8개 transparent SVG 를 만든다.
 *
 *   node dev-assets/bildi/generate.mjs
 *   → src/main/resources/static/img/bildi/bildi-<state>.svg  (idle/wings_open/fly/land/point/talk/think/happy)
 *
 * 이 SVG 들은 academy_world.js createBildi() 가 <img> 로 state 별로 swap 한다. 파일이 없거나
 * 로드 실패하면 인라인 fallback SVG(BILDI_SVG)로 떨어진다. 아트 패스에서 같은 파일명의
 * WebP/PNG(@2x, 투명)로 교체하면 코드 변경 없이 반영된다(BILDI_WORLD_ENGINE.md §7).
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/static/img/bildi');

// ── 공용 파트 ──────────────────────────────────────────────────────────────────
const DEFS = `
  <defs>
    <radialGradient id="b-body" cx="40%" cy="32%" r="75%">
      <stop offset="0%" stop-color="#ffe487"/><stop offset="55%" stop-color="#ffd23f"/><stop offset="100%" stop-color="#f4b813"/>
    </radialGradient>
    <linearGradient id="b-scarf" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#5A8FE0"/><stop offset="100%" stop-color="#0C238A"/>
    </linearGradient>
  </defs>`;

// 머리/몸 하나의 blob — 큰 둥근 머리 → 작게 오므린 몸(egg). 실루엣만으로 인식되게 하는 게 목표.
// 위쪽(머리)이 넓고 둥글고, 아래(몸)로 가면서 좁아진다. 예전보다 통통하고 짧다.
const BODY = `<path class="b-body" d="M100 26 C144 26 170 58 170 100 C170 140 146 170 116 174 Q100 176 84 174 C54 170 30 140 30 100 C30 58 56 26 100 26 Z" fill="url(#b-body)"/>
  <path class="b-belly" d="M100 96 C124 96 138 112 138 134 C138 156 120 170 100 170 C80 170 62 156 62 134 C62 112 76 96 100 96 Z" fill="#ffe08a" opacity="0.55"/>`;
// 정수리 털 — 3갈래로 뚜렷하게(개성 강화). 살짝 휘어 부드럽게.
const TUFT = `<path d="M86 30 Q88 8 98 24 Q101 4 108 24 Q116 8 118 30 Q102 20 86 30 Z" fill="#f4b813"/>
  <path d="M98 24 Q100 14 102 24 Z" fill="#e8a90c"/>`;
const CHEEKS = `<ellipse cx="62" cy="116" rx="11" ry="7" fill="#ffa7bd" opacity="0.8"/><ellipse cx="138" cy="116" rx="11" ry="7" fill="#ffa7bd" opacity="0.8"/>`;
const BEAK = `<path d="M91 108 Q100 104 109 108 L100 120 Z" fill="#ff9f1c"/><path d="M91 108 Q100 111 109 108" stroke="#e07d0c" stroke-width="1.8" fill="none"/>`;

// 이마 고글 — reference 시안의 보조 요소. 항상 착용하지 않는다(비행 관련 state 에서만).
// 억지로 덮어씌운 느낌을 줄이려 더 얇고 이마 곡률에 붙게 그린다.
const GOGGLES = `
  <g class="b-goggles" transform="translate(100 62)">
    <path d="M-34 5 Q0 -7 34 5 L33 11 Q0 0 -33 11 Z" fill="#5b4f3a"/>
    <rect x="-4" y="0" width="8" height="5" rx="2" fill="#c9a24b"/>
    <ellipse cx="-15" cy="3" rx="10" ry="9" fill="#2c2e38" stroke="#e6c877" stroke-width="3"/>
    <ellipse cx="15" cy="3" rx="10" ry="9" fill="#2c2e38" stroke="#e6c877" stroke-width="3"/>
    <path d="M-19 -1 A7 7 0 0 1 -11 -2" stroke="#9ec5ff" stroke-width="2.4" fill="none" stroke-linecap="round"/>
    <path d="M11 -1 A7 7 0 0 1 19 -2" stroke="#9ec5ff" stroke-width="2.4" fill="none" stroke-linecap="round"/>
  </g>`;

// BuildIt-blue 스카프 + 가슴 배지
function scarf(tail = 'side') {
  const tailPath = tail === 'up'
    ? '<path d="M20 -2 Q42 -14 46 6 Q42 12 32 8 Q24 2 18 -2 Z" fill="url(#b-scarf)"/>'
    : '<path d="M18 2 Q44 8 40 34 Q36 40 26 34 Q20 18 14 6 Z" fill="url(#b-scarf)"/>';
  return `
  <g class="b-scarf" transform="translate(100 130)">
    <path d="M-30 -9 Q0 6 30 -9 L26 7 Q0 19 -26 7 Z" fill="url(#b-scarf)"/>
    ${tailPath}
    <circle cx="0" cy="0" r="10.5" fill="#eef3ff"/>
    <path d="M0 -7 L8 0 L0 8 L-8 0 Z" fill="#0C238A"/>
  </g>`;
}

// 눈 (표정별)
const EYES = {
  open: `
    <ellipse cx="80" cy="96" rx="13.5" ry="16" fill="#2a2b33"/>
    <ellipse cx="120" cy="96" rx="13.5" ry="16" fill="#2a2b33"/>
    <circle cx="74.5" cy="88" r="5.2" fill="#fff"/><circle cx="114.5" cy="88" r="5.2" fill="#fff"/>
    <circle cx="85" cy="102" r="2.8" fill="#fff" opacity="0.75"/><circle cx="125" cy="102" r="2.8" fill="#fff" opacity="0.75"/>`,
  wide: `
    <ellipse cx="80" cy="95" rx="16" ry="17" fill="#2a2b33"/>
    <ellipse cx="120" cy="95" rx="16" ry="17" fill="#2a2b33"/>
    <circle cx="73.5" cy="87" r="6" fill="#fff"/><circle cx="113.5" cy="87" r="6" fill="#fff"/>`,
  happy: `
    <path d="M70 100 Q82 84 94 100" stroke="#23242c" stroke-width="7" fill="none" stroke-linecap="round"/>
    <path d="M106 100 Q118 84 130 100" stroke="#23242c" stroke-width="7" fill="none" stroke-linecap="round"/>`,
  squint: `
    <path d="M70 98 Q82 106 94 98" stroke="#23242c" stroke-width="7" fill="none" stroke-linecap="round"/>
    <path d="M106 98 Q118 106 130 98" stroke="#23242c" stroke-width="7" fill="none" stroke-linecap="round"/>`,
  lookUp: `
    <ellipse cx="82" cy="96" rx="12.5" ry="15.5" fill="#23242c"/>
    <ellipse cx="118" cy="96" rx="12.5" ry="15.5" fill="#23242c"/>
    <circle cx="84" cy="86" r="5" fill="#fff"/><circle cx="120" cy="86" r="5" fill="#fff"/>`
};

const MOUTH = {
  smile: `<path d="M92 120 Q100 128 108 120" stroke="#c2790f" stroke-width="3.5" fill="none" stroke-linecap="round"/>`,
  openSmall: `<ellipse cx="100" cy="122" rx="5" ry="6" fill="#c2790f"/>`,
  openBig: `<path d="M88 118 Q100 138 112 118 Q100 128 88 118 Z" fill="#b5680a"/>`,
  flat: `<path d="M93 122 L107 122" stroke="#c2790f" stroke-width="3.5" stroke-linecap="round"/>`
};

// 발 — 작고 단순(짧은 삼발가락 힌트만).
const FEET = {
  down: `<path d="M82 170 l-5 8 m5 -8 l0 9 m0 -9 l5 8" stroke="#ef8f12" stroke-width="3" stroke-linecap="round" fill="none"/>
         <path d="M118 170 l-5 8 m5 -8 l0 9 m0 -9 l5 8" stroke="#ef8f12" stroke-width="3" stroke-linecap="round" fill="none"/>`,
  wide: `<path d="M74 172 l-6 8 m6 -8 l0 9 m0 -9 l6 8" stroke="#ef8f12" stroke-width="3" stroke-linecap="round" fill="none"/>
         <path d="M126 172 l-6 8 m6 -8 l0 9 m0 -9 l6 8" stroke="#ef8f12" stroke-width="3" stroke-linecap="round" fill="none"/>`,
  tuck: `<ellipse cx="92" cy="164" rx="6" ry="3.5" fill="#ef8f12"/><ellipse cx="108" cy="164" rx="6" ry="3.5" fill="#ef8f12"/>`,
  hop: `<path d="M84 174 l-5 8 m5 -8 l0 9 m0 -9 l5 8" stroke="#ef8f12" stroke-width="3" stroke-linecap="round" fill="none"/>
        <path d="M116 174 l-5 8 m5 -8 l0 9 m0 -9 l5 8" stroke="#ef8f12" stroke-width="3" stroke-linecap="round" fill="none"/>`
};

// 날개 — 평소엔 작고 통통한 물방울(귀엽게 단순화). fly 에서만 "예상보다 크게" 펼쳐지는 게 signature.
const WINGS = {
  tuck: {
    l: `<path d="M6 -2 C-14 -6 -26 6 -24 20 C-22 30 -6 30 4 22 C10 16 12 4 6 -2 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M-6 -2 C14 -6 26 6 24 20 C22 30 6 30 -4 22 C-10 16 -12 4 -6 -2 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    lt: 'translate(40 110)', rt: 'translate(160 110)'
  },
  half: {
    l: `<path d="M0 0 C-24 -8 -40 4 -42 20 C-28 24 -10 20 6 6 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M0 0 C24 -8 40 4 42 20 C28 24 10 20 -6 6 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    lt: 'translate(38 106)', rt: 'translate(162 106)'
  },
  spread: {
    l: `<path d="M4 8 C-30 -24 -66 -40 -94 -24 C-84 -2 -58 20 -8 24 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>
        <path d="M-24 4 C-46 -6 -66 -16 -80 -20" stroke="#f2b415" stroke-width="2" fill="none" opacity="0.55"/>
        <path d="M-16 14 C-34 8 -50 2 -62 -2" stroke="#f2b415" stroke-width="2" fill="none" opacity="0.4"/>`,
    r: `<path d="M-4 8 C30 -24 66 -40 94 -24 C84 -2 58 20 8 24 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>
        <path d="M24 4 C46 -6 66 -16 80 -20" stroke="#f2b415" stroke-width="2" fill="none" opacity="0.55"/>
        <path d="M16 14 C34 8 50 2 62 -2" stroke="#f2b415" stroke-width="2" fill="none" opacity="0.4"/>`,
    lt: 'translate(42 94)', rt: 'translate(158 94)'
  },
  cheer: {
    l: `<path d="M0 4 C-20 -26 -40 -34 -54 -24 C-46 -6 -28 12 -4 14 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>`,
    r: `<path d="M0 4 C20 -26 40 -34 54 -24 C46 -6 28 12 4 14 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>`,
    lt: 'translate(50 96)', rt: 'translate(150 96)'
  },
  pointRaise: {
    l: `<path d="M6 -2 C-14 -6 -26 6 -24 20 C-22 30 -6 30 4 22 C10 16 12 4 6 -2 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M-4 8 C6 -14 20 -30 40 -34 C48 -24 46 -6 26 8 C16 14 2 16 -4 8 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>`,
    lt: 'translate(40 110)', rt: 'translate(158 104)'
  },
  chin: {
    l: `<path d="M6 -2 C-14 -6 -26 6 -24 20 C-22 30 -6 30 4 22 C10 16 12 4 6 -2 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M-4 0 C10 -4 20 4 18 16 C16 24 4 24 -4 16 C-8 10 -8 4 -4 0 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2"/>`,
    lt: 'translate(40 110)', rt: 'translate(120 126)'
  }
};

function wingGroup(w) {
  return `<g class="b-wing b-wing-l" transform="${w.lt}">${w.l}</g>
          <g class="b-wing b-wing-r" transform="${w.rt}">${w.r}</g>`;
}

// ── state 정의 ────────────────────────────────────────────────────────────────
// goggles: 항상 착용하지 않는다 — 비행 관련 state 에서만 이마로 올려 쓴다. 나머지는 맨 이마
// (실루엣 = 큰 머리 + 3갈래 정수리 털 + 스카프로 인식).
const STATES = {
  idle:       { wings: 'tuck',       eyes: 'open',   mouth: 'smile',     feet: 'down', tilt: 0,   dy: 0,  goggles: false },
  wings_open: { wings: 'half',       eyes: 'wide',   mouth: 'openSmall', feet: 'down', tilt: -3,  dy: -2, goggles: true },
  fly:        { wings: 'spread',     eyes: 'happy',  mouth: 'openBig',   feet: 'tuck', tilt: -12, dy: -6, goggles: true,
                extra: `<g opacity="0.5" stroke="#bcd6ff" stroke-width="4" stroke-linecap="round"><path d="M150 150 h26"/><path d="M156 166 h20"/><path d="M150 134 h16"/></g>` },
  land:       { wings: 'half',       eyes: 'squint', mouth: 'smile',     feet: 'wide', tilt: 2,   dy: 8,  goggles: true,
                extra: `<g fill="#e9e2d0" opacity="0.75"><circle cx="52" cy="180" r="7"/><circle cx="148" cy="180" r="7"/><circle cx="40" cy="186" r="4"/><circle cx="160" cy="186" r="4"/></g>` },
  point:      { wings: 'pointRaise', eyes: 'lookUp', mouth: 'smile',     feet: 'down', tilt: 0,   dy: 0,  goggles: false },
  talk:       { wings: 'half',       eyes: 'open',   mouth: 'openBig',   feet: 'down', tilt: 0,   dy: 0,  goggles: false },
  think:      { wings: 'chin',       eyes: 'lookUp', mouth: 'flat',      feet: 'down', tilt: -6,  dy: 0,  goggles: false,
                extra: `<g fill="#8aa0c8"><circle cx="150" cy="52" r="4"/><circle cx="162" cy="40" r="6"/><circle cx="176" cy="26" r="8"/></g>` },
  happy:      { wings: 'cheer',      eyes: 'happy',  mouth: 'openBig',   feet: 'hop',  tilt: 0,   dy: -12, goggles: false,
                extra: `<g fill="#ffd23f"><path d="M40 60 l3 8 8 3 -8 3 -3 8 -3 -8 -8 -3 8 -3 z"/><path d="M164 74 l2 6 6 2 -6 2 -2 6 -2 -6 -6 -2 6 -2 z"/></g>` }
};

function buildSvg(name, s) {
  const scarfTail = (name === 'fly' || name === 'happy') ? 'up' : 'side';
  const inner = `
    ${TUFT}
    ${wingGroup(WINGS[s.wings])}
    ${FEET[s.feet]}
    <g transform="rotate(${s.tilt} 100 110) translate(0 ${s.dy})">
      ${BODY}
      ${CHEEKS}
      ${EYES[s.eyes]}
      ${BEAK}
      ${MOUTH[s.mouth]}
      ${s.goggles ? GOGGLES : ''}
      ${scarf(scarfTail)}
    </g>
    ${s.extra || ''}`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200" role="img" aria-label="빌디 ${name}">${DEFS}${inner}\n</svg>\n`
    .replace(/\n\s+/g, '\n  ');
}

mkdirSync(OUT_DIR, { recursive: true });
const written = [];
for (const [name, s] of Object.entries(STATES)) {
  const file = resolve(OUT_DIR, `bildi-${name.replace(/_/g, '-')}.svg`);
  writeFileSync(file, buildSvg(name, s));
  written.push(`bildi-${name.replace(/_/g, '-')}.svg`);
}
console.log('wrote ' + written.length + ' assets to ' + OUT_DIR);
written.forEach((f) => console.log('  ' + f));
