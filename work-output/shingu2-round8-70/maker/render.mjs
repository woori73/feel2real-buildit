// 신구중2 8회 문제지 렌더러 — FORMAT MASTER(4회 FINAL v15) 구조·CSS 계승
// 사용법:  node render.mjs draft   → out/draft.html (단일 컬럼 높이 측정용)
//         node render.mjs final out/heights.json → 문제지 HTML(print)
import fs from "node:fs";
import path from "node:path";
import { QUESTIONS } from "./questions.mjs";

const OUT = path.resolve("out");
fs.mkdirSync(OUT, { recursive: true });
const CIRCLED = ["①", "②", "③", "④", "⑤"];
const MM = 3.779528; // 1mm = px @96dpi
const TITLE = "2026학년도 2학기 중간고사 대비 8회 70문항";
const SUB = "신구중학교 2학년 · 영어 · 70문항 (5·6과)";
const RUN = "신구중학교 2학년 · 2학기 중간고사 예상문제";
// 헤더 Source of Truth: 신구중3 4회 문제지(등록본)의 EduHolic 대형 로고 헤더 — 구조·CSS·로고 그대로 사용
const LOGO_URI = fs.readFileSync(path.resolve("eduholic-logo-datauri.txt"), "utf8").trim();

export const CSS = `@page { size:A4 portrait; margin:0; }
*{box-sizing:border-box}
html,body{width:100%;margin:0;padding:0}
body{font-family:"Noto Sans KR","Malgun Gothic","Nanum Gothic",sans-serif;font-size:9.5pt;line-height:1.42;color:#111}
.page{width:210mm;height:297mm;padding:12mm 11mm 10mm 11mm;position:relative;page-break-after:always;display:flex;flex-direction:column;overflow:hidden;background:#fff}
.page:last-child{page-break-after:auto}
.cols{display:flex;gap:6.5mm;flex:1 1 auto;min-height:0;padding-bottom:4mm}
.col{width:calc(50% - 3.25mm);overflow:hidden;display:flex;flex-direction:column;gap:var(--qgap,18px)}
.exam-header{border:1.4pt solid #000;margin-bottom:4mm}
.exam-header table{width:100%;border-collapse:collapse;font-size:9.5pt}
.exam-header td,.exam-header th{border:.7pt solid #444;padding:2mm 2.5mm}
.exam-header .title-row td{text-align:center;font-size:14pt;font-weight:700;padding:3mm;letter-spacing:1px}
.exam-header .sub-row td{text-align:center;font-size:9.5pt;color:#333}
.exam-header .info-row td{font-size:9pt}
.exam-header .label{font-weight:700;background:#f2f2f2;width:16mm;text-align:center}
.logo-cell{width:25%;height:20mm;padding:0!important;text-align:center;vertical-align:middle}
.logo-cell img{width:calc(100% - 3mm);height:calc(100% - 2mm);object-fit:contain;display:block;margin:auto}
.title-cell{width:75%;text-align:center!important;vertical-align:middle;font-size:14pt;font-weight:700;padding:3mm!important;letter-spacing:1px}
.school-cell{text-align:center;vertical-align:middle;font-size:9.5pt;color:#333}
.student-info-row .label{width:12mm;text-align:center;white-space:nowrap}
.student-info-row .student-name{width:72mm}.student-info-row .student-day{width:34mm}.student-info-row .student-score{width:34mm}.student-info-row td{height:9mm;vertical-align:middle}
.run-header{display:flex;justify-content:space-between;align-items:baseline;border-bottom:.9pt solid #000;padding-bottom:1.5mm;margin-bottom:3mm;font-size:9pt;color:#333}
.qblock{margin:0;page-break-inside:avoid;break-inside:avoid;flex:0 0 auto}
.unit{display:flex;flex-direction:column;gap:18px;flex:0 0 auto}
.qhead{display:flex;gap:1.3mm;align-items:baseline;line-height:1.35}.qnum{font-weight:700;flex-shrink:0}.qprompt{font-weight:500}
.group-label{font-weight:700;margin:0 0 .6mm 0}
.passage{margin:.7mm 0 .7mm 4.6mm}.passage.box,.passage.dialog{border:.7pt solid #666;padding:1.2mm 1.8mm;background:#fbfbfb;line-height:1.42}
.passage.prose{margin-left:4.6mm;line-height:1.42}
.choices{margin-top:.9mm}.choices.row{margin-left:4.6mm;display:flex;flex-wrap:wrap;column-gap:3mm;row-gap:.5mm;align-items:flex-start}
.choice-item{display:inline-block;white-space:normal;padding-left:1.35em;text-indent:-1.35em}
.choices.vertical{display:block;margin-left:4.6mm}.choices.vertical .choice-item{display:block;width:auto;margin:0 0 .7mm 0;padding-left:1.45em;text-indent:-1.45em;white-space:normal}
.cond-box{margin:.9mm 0 0 4.6mm;border:.7pt solid #666;padding:.8mm 1.8mm;font-size:9pt;line-height:1.4;background:#fbfbfb}.cond-label{font-weight:700;margin-right:1mm}.view-title{display:block;text-align:center;margin:0 0 1.2mm 0;font-weight:700}.cond-box + .choices{margin-top:2mm}
.choice-table{width:calc(100% - 4.6mm);margin:1.2mm 0 0 4.6mm;border-collapse:collapse;font-size:9pt;text-align:center}.choice-table th,.choice-table td{padding:.4mm 1.1mm;border-bottom:.45pt solid #bbb}.choice-table th{font-weight:700;border-top:.7pt solid #666;border-bottom:.7pt solid #666}.choice-table td:first-child{width:9mm;font-weight:700}
.blank{display:inline-block;min-width:14mm;border-bottom:.7pt solid #111}.given-sentence{display:block;border:.7pt solid #666;padding:.8mm 1.8mm;margin:.7mm 0 .3mm 4.6mm;background:#fff}u{text-underline-offset:1.5px}
.qprompt,.cond-box,.choice-item,.choice-table th,.choice-table td,.given-sentence,.group-label,.exam-header td,.exam-header th{word-break:keep-all}
.page-footer{position:absolute;bottom:4mm;left:0;right:0;text-align:center;font-size:8pt;color:#555}
.edit-toolbar{position:fixed;right:14px;top:14px;z-index:9999;display:flex;gap:6px;padding:7px;background:#fff;border:1px solid #aaa;border-radius:8px;box-shadow:0 2px 10px rgba(0,0,0,.15);font-family:Arial,sans-serif}.edit-toolbar button{font-size:12px;padding:6px 9px;border:1px solid #888;background:#fff;border-radius:5px;cursor:pointer}body.editing [contenteditable="true"]{outline:1px dashed #4d78c4;outline-offset:2px;background:#fffdf0}
@media screen{body{background:#e9e9e9}.page{margin:0 auto 8mm auto}}
@media print{@page{size:A4 portrait;margin:0}html,body{width:210mm!important;margin:0!important;padding:0!important;background:#fff!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}.page{width:210mm!important;height:297mm!important;margin:0!important;padding:12mm 11mm 10mm 11mm!important;overflow:hidden!important;break-after:page!important;page-break-after:always!important}.page:last-child{break-after:auto!important;page-break-after:auto!important}.cols{display:flex!important;gap:6.5mm!important;width:100%!important;padding-bottom:4mm!important}.col{flex:0 0 calc(50% - 3.25mm)!important;width:calc(50% - 3.25mm)!important}.edit-toolbar{display:none!important}}`;

const EDITOR_SCRIPT = `var TOOLBAR;(function(){var toolbar=document.getElementById('editToolbar');var toggle=document.getElementById('toggleEdit');var save=document.getElementById('saveHtml');var editing=false;
var SELECTORS='.qprompt,.passage,.cond-box,.choice-table,.given-sentence,.choices,.exam-header table,.run-header';
toggle.addEventListener('click',function(){editing=!editing;document.body.classList.toggle('editing',editing);toggle.textContent=editing?'편집모드 끄기':'편집모드 켜기';
document.querySelectorAll(SELECTORS).forEach(function(el){el.setAttribute('contenteditable',editing?'true':'false')})});
save.addEventListener('click',function(){var clone=document.documentElement.cloneNode(true);clone.querySelectorAll('[contenteditable]').forEach(function(el){el.removeAttribute('contenteditable')});
var blob=new Blob(['<!DOCTYPE html>\\n'+clone.outerHTML],{type:'text/html'});var a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=document.title+'.html';a.click()});})();`;

function choicesHtml(q) {
  const layout = q.choiceLayout === "vertical" ? "vertical" : "row";
  const items = q.choices
    .map((c, i) => `<span class="choice-item">${CIRCLED[i]} ${c}</span>`)
    .join("");
  return `<div class="choices ${layout}">${items}</div>`;
}

export function qblockHtml(q) {
  let h = `<div class="qblock" data-qnum="${q.num}">`;
  if (q.groupLabel) h += `<div class="group-label">${q.groupLabel}</div>`;
  h += `<div class="qhead"><span class="qnum">${q.num}.</span><span class="qprompt">${q.prompt}</span></div>`;
  if (q.passage) h += `<div class="passage ${q.passage.kind || "box"}">${q.passage.html}</div>`;
  if (q.cond) h += `<div class="cond-box"><div class="cond-label view-title">&lt;${q.cond.title}&gt;</div>${q.cond.html}</div>`;
  if (q.given) h += `<div class="given-sentence">${q.given}</div>`;
  if (q.cond2) h += `<div class="cond-box"><div class="cond-label view-title">&lt;${q.cond2.title}&gt;</div>${q.cond2.html}</div>`;
  if (q.table) {
    const cols = q.table.widths ? `<colgroup>${q.table.widths.map((w) => `<col style="width:${w}%"/>`).join("")}</colgroup>` : "";
    h += `<table class="choice-table">${cols}<tr>${q.table.head.map((x) => `<th>${x}</th>`).join("")}</tr>`;
    h += q.table.rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join("")}</tr>`).join("");
    h += `</table>`;
  }
  h += choicesHtml(q);
  h += `</div>`;
  return h;
}

// 공통지문 단위(group)로 묶어 패킹한다.
export function units() {
  const list = [];
  for (const q of QUESTIONS) {
    if (!q.cont || !list.length) list.push([q]);
    else list[list.length - 1].push(q);
  }
  return list;
}

const headerHtml = `<div class="exam-header"><table>
<tr class="title-row"><td class="logo-cell" rowspan="2"><img alt="EduHolic academy" src="${LOGO_URI}"/></td><td class="title-cell" colspan="5">${TITLE}</td></tr>
<tr class="sub-row"><td class="school-cell" colspan="5">${SUB}</td></tr>
<tr class="info-row student-info-row">
<td class="label">이름</td><td class="student-name"> </td>
<td class="label">요일</td><td class="student-day"> </td>
<td class="label">점수</td><td class="student-score"> </td>
</tr>
</table></div>`;
const runHeaderHtml = `<div class="run-header"><span>${RUN}</span><span>영어</span></div>`;

if (process.argv[2] === "draft") {
  const html = `<!DOCTYPE html>\n<html lang="ko"><head><meta charset="utf-8"/><title>${TITLE}</title><style id="clean-final-style">${CSS}</style></head><body>
<div class="page" id="draftPage">${headerHtml}<div class="run-header" id="runRef"><span>${RUN}</span><span>영어</span></div>
<div class="cols"><div class="col" id="measureCol" style="--qgap:18px">${units()
    .map(
      (u) =>
        `<div class="unit" data-unit-start="${u[0].num}">${u.map(qblockHtml).join("")}</div>`
    )
    .join("")}</div><div class="col" style="--qgap:18px"></div></div><div class="page-footer">draft</div></div>
<script>${EDITOR_SCRIPT}</script></body></html>`;
  fs.writeFileSync(path.join(OUT, "draft.html"), html);
  console.log("draft written", path.join(OUT, "draft.html"), "units", units().length);
} else if (process.argv[2] === "final") {
  const data = JSON.parse(fs.readFileSync(path.resolve(process.argv[3]), "utf8"));
  const GAP_MIN = 14;                        // 화면 표시 최소 간격과 패킹 회계를 일치시킨다
  const colFull = 271 * MM;                       // 인쇄 기준 유효 높이(297 - 12 - 12 - 4)
  const page1Avail = colFull - data.headerH - 4 * MM - 6; // exam-header + 하단 여유
  const otherAvail = colFull - data.runHeaderH - 3 * MM - 6;
  const UNITS = units();
  const hmap = new Map(data.units.map((u) => [u.start, u.h]));
  const H = (i) => hmap.get(UNITS[i][0].num);
  UNITS.forEach((u, i) => {
    if (H(i) === undefined) throw new Error("no height for unit " + u[0].num);
  });
  const availOf = (colIndex) => (Math.floor(colIndex / 2) === 0 ? page1Avail : otherAvail);
  const n = UNITS.length;
  const prefix = [0];
  for (let i = 0; i < n; i++) prefix.push(prefix[i] + H(i));
  const load = (i, i2) => prefix[i2] - prefix[i] + (i2 - i - 1) * GAP_MIN; // 문항 i..i2-1을 한 컬럼에 넣을 때 높이
  // 컬럼 수를 고정하고 '가장 비어 보이는 컬럼의 하단 여백'을 최소화하는 순차 분할을 DP로 찾는다.
  const balance = (N, maxSlack) => {
    const parents = [];
    let reach = new Set([0]);
    for (let j = 0; j < N; j++) {
      const avail = availOf(j);
      const lo = avail - maxSlack;
      const next = new Map();
      for (const i of reach) {
        for (let i2 = i + 1; i2 <= n; i2++) {
          const L = load(i, i2);
          if (L > avail) break;
          if (L >= lo) {
            if (!next.has(i2)) next.set(i2, i);
            if (j === N - 1 && i2 === n) break;
          }
        }
      }
      reach = new Set(next.keys());
      parents.push(next);
      if (!reach.size) return null;
    }
    if (!reach.has(n)) return null;
    const cuts = [];
    let at = n;
    for (let j = N - 1; j >= 0; j--) {
      const prev = parents[j].get(at);
      if (prev === undefined) return null;
      cuts.unshift([prev, at]);
      at = prev;
    }
    return cuts.map(([a, b], j) => ({ items: Array.from({ length: b - a }, (_, k) => a + k), avail: availOf(j) }));
  };
  // 최대 채움: 컬럼 수의 하한(=페이지 수의 하한)을 구한다.
  const greedy = () => {
    const out = [];
    let cur = null;
    for (let i = 0; i < n; i++) {
      if (!cur) cur = { items: [], avail: availOf(out.length) }, out.push(cur);
      if (load(cur.items[0], i + 1) > cur.avail) {
        cur = { items: [], avail: availOf(out.length) };
        out.push(cur);
      }
      cur.items.push(i);
    }
    return out;
  };
  const g = greedy();
  const N = g.length % 2 ? g.length + 1 : g.length; // 홀수 컬럼이면 마지막 페이지의 오른쪽이 비므로 하나 더 둥근다
  let cols = null;
  for (let s = 120; s <= 600 && !cols; s += 10) cols = balance(N, s);
  if (!cols) throw new Error("no balanced packing for " + N + " columns (greedy " + g.length + ")");
  if (N / 2 > 10) console.log("NOTE: pages =", N / 2, "(목표 10페이지 초과) — 문항 텍스트 추가 축소가 필요");
  cols.forEach((c, ci) => {
    const used = c.items.reduce((s, j) => s + H(j), 0) + Math.max(0, c.items.length - 1) * GAP_MIN;
    if (used > c.avail) throw new Error("col " + (ci + 1) + " overflows: " + used.toFixed(0) + " > " + c.avail.toFixed(0));
  });
  if (cols.reduce((s, c) => s + c.items.length, 0) !== UNITS.length) throw new Error("lost units in packing");
  const totalPages = Math.ceil(cols.length / 2);
  let body = "";
  for (let p = 0; p < totalPages; p++) {
    const pc = cols.slice(p * 2, p * 2 + 2);
    const colsHtml = pc
      .map((c) => {
        const used = c.items.reduce((s, j) => s + H(j), 0);
        const gap = c.items.length > 1 ? Math.max(GAP_MIN, Math.min(90, (c.avail - used) / (c.items.length - 1))) : 18;
        const inner = c.items
          .map((j) => `<div class="unit">${UNITS[j].map(qblockHtml).join("")}</div>`)
          .join("");
        return `<div class="col spread" style="--qgap:${gap.toFixed(1)}px">${inner}</div>`;
      })
      .join("");
    body += `<div class="page">${p === 0 ? headerHtml : runHeaderHtml}<div class="cols">${colsHtml}</div><div class="page-footer">${p + 1} / ${totalPages}</div></div>`;
  }
  const html = `<!DOCTYPE html>
<html lang="ko"><head><meta charset="utf-8"/><title>${TITLE}</title><style id="clean-final-style">${CSS}</style></head><body><div class="edit-toolbar" id="editToolbar"><button id="toggleEdit" type="button">편집모드 켜기</button><button id="saveHtml" type="button">수정 HTML 저장</button></div>${body}<script>${EDITOR_SCRIPT}</script></body></html>`;
  const file = path.join(OUT, `round8-paper-${QUESTIONS.length}of70.html`);
  fs.writeFileSync(file, html);
  console.log("final written", file, "cols", cols.length, "pages", totalPages);
  console.log("fill", cols.map((c, ci) => c.items.length + ":" + Math.round(c.avail - c.items.reduce((s, j) => s + H(j), 0) - Math.max(0, c.items.length - 1) * GAP_MIN)).join(" "));
} else {
  console.log("usage: node render.mjs draft | final <heights.json>");
}
