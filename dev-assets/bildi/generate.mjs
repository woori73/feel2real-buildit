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

// 머리/몸 하나의 blob (큰 머리 · 작은 몸)
const BODY = `<path class="b-body" d="M100 30 C142 30 168 60 168 104 C168 152 142 176 100 176 C58 176 32 152 32 104 C32 60 58 30 100 30 Z" fill="url(#b-body)"/>`;
const TUFT = `<path d="M90 30 Q95 12 100 28 Q105 10 111 28 Q117 14 122 30 Q108 22 90 30 Z" fill="#f4b813"/>`;
const CHEEKS = `<ellipse cx="66" cy="120" rx="10" ry="6.5" fill="#ffa7bd" opacity="0.75"/><ellipse cx="134" cy="120" rx="10" ry="6.5" fill="#ffa7bd" opacity="0.75"/>`;
const BEAK = `<path d="M92 112 L108 112 L100 126 Z" fill="#ff9f1c"/><path d="M92 112 L108 112" stroke="#e07d0c" stroke-width="2"/>`;

// 이마 고글 (항상 이마 위로 올려 쓴 형태)
const GOGGLES = `
  <g class="b-goggles" transform="translate(100 66)">
    <path d="M-36 4 Q0 -8 36 4 L34 12 Q0 2 -34 12 Z" fill="#6f5f42"/>
    <rect x="-5" y="-1" width="10" height="6" rx="2" fill="#c9a24b"/>
    <circle cx="-15" cy="2" r="10.5" fill="#2c2e38" stroke="#e6c877" stroke-width="3.5"/>
    <circle cx="15" cy="2" r="10.5" fill="#2c2e38" stroke="#e6c877" stroke-width="3.5"/>
    <path d="M-19 -3 A8 8 0 0 1 -10 -4" stroke="#9ec5ff" stroke-width="2.6" fill="none" stroke-linecap="round"/>
    <path d="M11 -3 A8 8 0 0 1 20 -4" stroke="#9ec5ff" stroke-width="2.6" fill="none" stroke-linecap="round"/>
  </g>`;

// BuildIt-blue 스카프 + 가슴 배지
function scarf(tail = 'side') {
  const tailPath = tail === 'up'
    ? '<path d="M20 -2 Q42 -14 46 6 Q42 12 32 8 Q24 2 18 -2 Z" fill="url(#b-scarf)"/>'
    : '<path d="M18 2 Q44 8 40 34 Q36 40 26 34 Q20 18 14 6 Z" fill="url(#b-scarf)"/>';
  return `
  <g class="b-scarf" transform="translate(100 140)">
    <path d="M-32 -8 Q0 8 32 -8 L28 8 Q0 22 -28 8 Z" fill="url(#b-scarf)"/>
    ${tailPath}
    <circle cx="0" cy="1" r="12" fill="#eef3ff"/>
    <path d="M0 -8 L9 1 L0 10 L-9 1 Z" fill="#0C238A"/>
  </g>`;
}

// 눈 (표정별)
const EYES = {
  open: `
    <ellipse cx="82" cy="98" rx="12.5" ry="15.5" fill="#23242c"/>
    <ellipse cx="118" cy="98" rx="12.5" ry="15.5" fill="#23242c"/>
    <circle cx="77" cy="91" r="4.6" fill="#fff"/><circle cx="113" cy="91" r="4.6" fill="#fff"/>
    <circle cx="86" cy="103" r="2.4" fill="#fff" opacity="0.7"/><circle cx="122" cy="103" r="2.4" fill="#fff" opacity="0.7"/>`,
  wide: `
    <circle cx="82" cy="97" r="16" fill="#23242c"/>
    <circle cx="118" cy="97" r="16" fill="#23242c"/>
    <circle cx="76" cy="90" r="5.4" fill="#fff"/><circle cx="112" cy="90" r="5.4" fill="#fff"/>`,
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

// 발
const FEET = {
  down: `<ellipse cx="86" cy="176" rx="10" ry="5" fill="#ff9f1c"/><ellipse cx="114" cy="176" rx="10" ry="5" fill="#ff9f1c"/>`,
  wide: `<ellipse cx="74" cy="178" rx="12" ry="5" fill="#ff9f1c"/><ellipse cx="126" cy="178" rx="12" ry="5" fill="#ff9f1c"/>`,
  tuck: `<ellipse cx="92" cy="168" rx="8" ry="4" fill="#ef8f12"/><ellipse cx="108" cy="168" rx="8" ry="4" fill="#ef8f12"/>`,
  hop: `<ellipse cx="88" cy="182" rx="9" ry="4.5" fill="#ff9f1c"/><ellipse cx="112" cy="182" rx="9" ry="4.5" fill="#ff9f1c"/>`
};

// 날개 (state별 — fly 에서 크게 펼쳐진다)
const WINGS = {
  tuck: {
    l: `<path d="M0 0 C-16 -4 -24 12 -16 26 C-8 34 6 26 8 8 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M0 0 C16 -4 24 12 16 26 C8 34 -6 26 -8 8 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    lt: 'translate(36 118)', rt: 'translate(164 118)'
  },
  half: {
    l: `<path d="M0 0 C-26 -10 -44 2 -46 20 C-32 24 -12 22 6 6 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M0 0 C26 -10 44 2 46 20 C32 24 12 22 -6 6 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    lt: 'translate(40 108)', rt: 'translate(160 108)'
  },
  spread: {
    l: `<path d="M2 6 C-24 -20 -54 -34 -78 -22 C-70 -4 -48 16 -6 20 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>
        <path d="M-20 2 C-38 -6 -54 -14 -66 -18" stroke="#f2b415" stroke-width="2" fill="none" opacity="0.6"/>`,
    r: `<path d="M-2 6 C24 -20 54 -34 78 -22 C70 -4 48 16 6 20 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>
        <path d="M20 2 C38 -6 54 -14 66 -18" stroke="#f2b415" stroke-width="2" fill="none" opacity="0.6"/>`,
    lt: 'translate(44 96)', rt: 'translate(156 96)'
  },
  cheer: {
    l: `<path d="M0 4 C-20 -26 -40 -34 -54 -24 C-46 -6 -28 12 -4 14 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>`,
    r: `<path d="M0 4 C20 -26 40 -34 54 -24 C46 -6 28 12 4 14 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>`,
    lt: 'translate(50 96)', rt: 'translate(150 96)'
  },
  pointRaise: {
    l: `<path d="M0 0 C-16 -4 -24 12 -16 26 C-8 34 6 26 8 8 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M0 0 C22 -20 30 -34 22 -46 C12 -50 -2 -40 -4 -20 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2.5"/>`,
    lt: 'translate(36 118)', rt: 'translate(162 108)'
  },
  chin: {
    l: `<path d="M0 0 C-16 -4 -24 12 -16 26 C-8 34 6 26 8 8 Z" fill="#ffcf3a" stroke="#f2b415" stroke-width="2"/>`,
    r: `<path d="M0 0 C14 -2 20 8 14 20 C8 26 -6 20 -6 8 Z" fill="#ffd23f" stroke="#f2b415" stroke-width="2"/>`,
    lt: 'translate(36 118)', rt: 'translate(118 128)'
  }
};

function wingGroup(w) {
  return `<g class="b-wing b-wing-l" transform="${w.lt}">${w.l}</g>
          <g class="b-wing b-wing-r" transform="${w.rt}">${w.r}</g>`;
}

// ── state 정의 ────────────────────────────────────────────────────────────────
const STATES = {
  idle:       { wings: 'tuck',       eyes: 'open',   mouth: 'smile',     feet: 'down', tilt: 0,   dy: 0 },
  wings_open: { wings: 'half',       eyes: 'wide',   mouth: 'openSmall', feet: 'down', tilt: -3,  dy: -2 },
  fly:        { wings: 'spread',     eyes: 'happy',  mouth: 'openBig',   feet: 'tuck', tilt: -12, dy: -6,
                extra: `<g opacity="0.5" stroke="#bcd6ff" stroke-width="4" stroke-linecap="round"><path d="M150 150 h26"/><path d="M156 166 h20"/><path d="M150 134 h16"/></g>` },
  land:       { wings: 'half',       eyes: 'squint', mouth: 'smile',     feet: 'wide', tilt: 2,   dy: 8,
                extra: `<g fill="#e9e2d0" opacity="0.75"><circle cx="52" cy="180" r="7"/><circle cx="148" cy="180" r="7"/><circle cx="40" cy="186" r="4"/><circle cx="160" cy="186" r="4"/></g>` },
  point:      { wings: 'pointRaise', eyes: 'lookUp', mouth: 'smile',     feet: 'down', tilt: 0,   dy: 0 },
  talk:       { wings: 'half',       eyes: 'open',   mouth: 'openBig',   feet: 'down', tilt: 0,   dy: 0 },
  think:      { wings: 'chin',       eyes: 'lookUp', mouth: 'flat',      feet: 'down', tilt: -6,  dy: 0,
                extra: `<g fill="#8aa0c8"><circle cx="150" cy="52" r="4"/><circle cx="162" cy="40" r="6"/><circle cx="176" cy="26" r="8"/></g>` },
  happy:      { wings: 'cheer',      eyes: 'happy',  mouth: 'openBig',   feet: 'hop',  tilt: 0,   dy: -12,
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
      ${GOGGLES}
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
