// 신구중2 8회 정답·해설 HTML 생성기 — 문제지와 같은 A4 페이지 박스(210×297mm, @page margin 0)를 쓴다.
// 사용법:
//   node render_answers.mjs draft      → out/answers-draft.html (브라우저에서 .ans 높이 측정용)
//   node render_answers.mjs final <json> → out/round8-answers-70.html (고정 2컬럼 페이지 패킹 = 최종 산출물)
//   node render_answers.mjs            → out/answers-flow.html (CSS 다단 자동 흐름, 검토용)
import fs from "node:fs";
import path from "node:path";
import { QUESTIONS } from "./questions.mjs";
import { EXPL } from "./explanations.mjs";

const OUT = path.resolve("out");
fs.mkdirSync(OUT, { recursive: true });
const CIRCLED = ["①", "②", "③", "④", "⑤"];
const MM = 3.779528;
const MODE = process.argv[2] === "final" ? "final" : process.argv[2] === "draft" ? "draft" : "flow";

const CSS = `@page{size:A4 portrait;margin:0}
*{box-sizing:border-box}
html,body{width:100%;margin:0;padding:0}
body{font-family:"Noto Sans KR","Malgun Gothic",sans-serif;font-size:9.5pt;line-height:1.42;color:#111}
.page{width:210mm;height:297mm;padding:12mm 11mm;position:relative;display:flex;flex-direction:column;overflow:hidden;background:#fff;page-break-after:always}
.page:last-child{page-break-after:auto}
.cols{display:flex;gap:7mm;flex:1 1 auto;min-height:0;padding-bottom:4mm}
.col{flex:0 0 calc(50% - 3.5mm);overflow:hidden;display:flex;flex-direction:column;gap:3mm}
h1{font-size:14pt;margin:0 0 2mm 0;text-align:center;letter-spacing:1px}
h2{font-size:10pt;margin:2mm 0 2mm 0;border-bottom:1pt solid #000;padding-bottom:1mm}
.subtitle{text-align:center;font-size:9.5pt;color:#333;margin:0 0 3mm 0}
table.keys{width:100%;border-collapse:collapse;font-size:9pt}
table.keys td{border:.5pt solid #999;text-align:center;padding:1mm .3mm}
table.keys td.n{background:#f2f2f2;font-weight:700}
.ans{break-inside:avoid;page-break-inside:avoid;flex:0 0 auto;border-bottom:.4pt solid #e0e0e0;padding-bottom:2mm;word-break:keep-all}
.ans .head{font-weight:700}
.ans .axis{display:inline-block;font-size:8.5pt;font-weight:400;color:#444;border:.5pt solid #999;border-radius:2mm;padding:0 1.4mm;margin-left:1.5mm}
.ans dt{float:left;clear:left;width:18mm;font-weight:700;color:#000}
.ans dd{margin:0 0 .5mm 19mm;overflow:hidden}
.page-footer{position:absolute;bottom:4mm;left:0;right:0;text-align:center;font-size:8pt;color:#555}
@media print{html,body{width:210mm!important;background:#fff}
.page{width:210mm!important;height:297mm!important;margin:0!important;padding:12mm 11mm!important;overflow:hidden!important;break-after:page!important}
.page:last-child{break-after:auto!important}.cols{display:flex!important}.col{flex:0 0 calc(50% - 3.5mm)!important}}
@media screen{body{background:#e9e9e9}.page{margin:0 auto 8mm auto;box-shadow:0 0 6px rgba(0,0,0,.2)}}`;

function keyTable() {
  const rows = [];
  for (let i = 0; i < 70; i += 10) {
    const ns = [], as = [];
    for (let j = i; j < Math.min(i + 10, 70); j++) {
      ns.push(`<td class="n">${j + 1}</td>`);
      as.push(`<td>${CIRCLED[QUESTIONS[j].answer - 1]}</td>`);
    }
    rows.push(`<tr>${ns.join("")}</tr><tr>${as.join("")}</tr>`);
  }
  return `<table class="keys">${rows.join("")}</table>`;
}

const headerBlock = `<h1>2026학년도 2학기 중간고사 대비 8회 · 정답 및 해설</h1>
<p class="subtitle">신구중학교 2학년 · 영어 · 70문항 (Lesson 5·6) · 전 문항 객관식</p>
${keyTable()}
<h2>문항별 해설</h2>`;

const itemHtml = (q) => {
  const e = EXPL.find((x) => x.num === q.num);
  return `<div class="ans" data-num="${q.num}">
<div class="head">${q.num}. 정답 ${CIRCLED[q.answer - 1]}<span class="axis">${e.axis}</span></div>
<dl>
<dt>핵심 근거</dt><dd>${e.key}</dd>
<dt>오답 포인트</dt><dd class="tip">${e.wrong}</dd>
<dt>규칙</dt><dd>${e.rule}</dd>
<dt>지문 근거</dt><dd>${e.ev}</dd>
</dl></div>`;
};

const doc = (title, css, body) =>
  `<!DOCTYPE html>\n<html lang="ko"><head><meta charset="utf-8"/><title>${title}</title><style>${css}</style></head><body>${body}</body></html>`;

const colFull = (297 - 12 - 10 - 4) * MM; // .page padding(상12/하10mm) + .cols padding-bottom(4mm)
const GAP = 3 * MM;                       // .col 사이 간격

if (MODE === "draft") {
  const body = `<div class="page"><div id="measureHeader">${headerBlock}</div>
<div class="cols"><div class="col" id="measureCol">${QUESTIONS.map(itemHtml).join("")}</div><div class="col"></div></div></div>`;
  fs.writeFileSync(path.join(OUT, "answers-draft.html"), doc("answers draft", CSS, body));
  console.log("answers draft written", QUESTIONS.length, "items");
} else if (MODE === "final") {
  const heights = JSON.parse(fs.readFileSync(path.resolve(process.argv[3]), "utf8"));
  const hmap = new Map(heights.units.map((u) => [u.start, u.h]));
  const n = QUESTIONS.length;
  const prefix = [0];
  for (let i = 0; i < n; i++) {
    const h = hmap.get(QUESTIONS[i].num);
    if (h === undefined) throw new Error("no height for " + QUESTIONS[i].num);
    prefix.push(prefix[i] + h);
  }
  const load = (i, i2) => prefix[i2] - prefix[i] + (i2 - i - 1) * GAP;
  // 헤더(제목+정답표)는 .cols 위에 전폭으로 놓이므로 1페이지의 두 컬럼 모두 높이를 잃는다.
  const availOf = (colIndex) => (Math.floor(colIndex / 2) === 0 ? colFull - heights.headerH - GAP : colFull);
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
          if (L >= lo && !next.has(i2)) next.set(i2, i);
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
  const greedy = () => {
    const out = [];
    let cur = null;
    for (let i = 0; i < n; i++) {
      if (!cur) { cur = { items: [], avail: availOf(out.length) }; out.push(cur); }
      if (cur.items.length && load(cur.items[0], i + 1) > cur.avail) {
        cur = { items: [], avail: availOf(out.length) };
        out.push(cur);
      }
      cur.items.push(i);
    }
    return out;
  };
  const g = greedy();
  const N = g.length % 2 ? g.length + 1 : g.length;
  let cols = null;
  for (let s = 40; s <= 600 && !cols; s += 10) cols = balance(N, s);
  if (!cols) throw new Error("no balanced packing for " + N + " answer columns");
  cols.forEach((c, ci) => {
    const used = load(c.items[0], c.items[c.items.length - 1] + 1);
    if (used > c.avail) throw new Error("answer col " + (ci + 1) + " overflows " + used.toFixed(0) + " > " + c.avail.toFixed(0));
  });
  if (cols.reduce((s, c) => s + c.items.length, 0) !== n) throw new Error("lost answer items");
  const pages = [];
  for (let i = 0; i < cols.length; i += 2) pages.push(cols.slice(i, i + 2));
  const bodyHtml = pages
    .map((pc, pi) => {
      const colsHtml = pc
        .map((c) => `<div class="col">${c.items.map((k) => itemHtml(QUESTIONS[k])).join("")}</div>`)
        .join("");
      return `<div class="page">${pi === 0 ? headerBlock : ""}<div class="cols">${colsHtml}</div><div class="page-footer">${pi + 1} / ${pages.length}</div></div>`;
    })
    .join("\n");
  fs.writeFileSync(path.join(OUT, "round8-answers-70.html"), doc("신구중2 8회 70문항 정답·해설", CSS, bodyHtml));
  console.log("answers written", path.join(OUT, "round8-answers-70.html"), "cols", cols.length, "pages", pages.length);
  console.log("fill", cols.map((c) => c.items.length + ":" + Math.round(c.avail - load(c.items[0], c.items[c.items.length - 1] + 1))).join(" "));
} else {
  const flowCss = CSS + `\n.sheet{columns:2;column-gap:7mm;column-rule:.4pt solid #bbb;width:210mm;padding:12mm 11mm;margin:0 auto}`;
  const body = `<div class="sheet">${headerBlock}\n${QUESTIONS.map(itemHtml).join("\n")}</div>`;
  fs.writeFileSync(path.join(OUT, "answers-flow.html"), doc("신구중2 8회 70문항 정답·해설(자동 흐름)", flowCss, body));
  console.log("answers flow written", path.join(OUT, "answers-flow.html"), "items", QUESTIONS.length);
}
