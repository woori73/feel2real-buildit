// 신구중2 8회 정답·해설 HTML 생성기
// 사용법:
//   node render_answers.mjs draft  → out/answers-draft.html (브라우저 높이 측정용 단일 컬럼)
//   node render_answers.mjs final out/answer-heights.json → out/round8-answers-70.html (페이지별 2컬럼 고정 패킹)
import fs from "node:fs";
import path from "node:path";
import { QUESTIONS } from "./questions.mjs";
import { EXPL } from "./explanations.mjs";

const OUT = path.resolve("out");
fs.mkdirSync(OUT, { recursive: true });
const CIRCLED = ["①", "②", "③", "④", "⑤"];
const MODE = process.argv[2] === "final" ? "final" : process.argv[2] === "draft" ? "draft" : "flow";
const MEASURE_WIDTH = MODE === "draft" ? ' style="width:90.5mm"' : "";

const strip = (h) =>
  h
    .replace(/<br\s*\/?>/g, " / ")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/\s+/g, " ")
    .trim();

const CSS = `@page{size:A4 portrait;margin:12mm 11mm}
*{box-sizing:border-box}
html,body{margin:0;padding:0}
body{font-family:"Noto Sans KR","Malgun Gothic",sans-serif;font-size:9.5pt;line-height:1.45;color:#111}
.page{display:flex;flex-direction:column;gap:4mm;height:273mm;padding:12mm 11mm;overflow:hidden;background:#fff}
.page + .page{border-top:1px dashed #c6c6c6;margin-top:8mm}
.anspage + .anspage{page-break-before:always}
.cols{display:flex;gap:7mm;flex:1 1 auto;min-height:0}
.col{width:calc(50% - 3.5mm);overflow:hidden;display:flex;flex-direction:column;gap:3mm}
h1{font-size:14pt;margin:0 0 2mm 0;text-align:center;letter-spacing:1px}
h2{font-size:10pt;margin:2mm 0 2mm 0;border-bottom:1pt solid #000;padding-bottom:1mm}
.subtitle{text-align:center;font-size:9.5pt;color:#333;margin:0 0 3mm 0}
table.keys{width:100%;border-collapse:collapse;font-size:9pt}
table.keys td{border:.5pt solid #999;text-align:center;padding:1mm .3mm}
table.keys td.n{background:#f2f2f2;font-weight:700}
.ans{break-inside:avoid;page-break-inside:avoid;flex:0 0 auto;border-bottom:.4pt solid #e0e0e0;padding-bottom:2mm}
.ans .head{font-weight:700}
.ans .axis{display:inline-block;font-size:8.5pt;font-weight:400;color:#444;border:.5pt solid #999;border-radius:2mm;padding:0 1.4mm;margin-left:1.5mm}
.ans dt{float:left;clear:left;width:15mm;font-weight:700;color:#000}
.ans dd{margin:0 0 .5mm 16mm;overflow:hidden}
@media print{body{font-size:9pt}
.page{height:273mm!important;padding:12mm 11mm!important}}`;

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

const itemHtml = (q) => {
  const e = EXPL.find((x) => x.num === q.num);
  return `<div class="ans"${MODE === "draft" ? ` data-num="${q.num}"` : ""}>
<div class="head">${q.num}. 정답 ${CIRCLED[q.answer - 1]}<span class="axis">${e.axis}</span></div>
<dl>
<dt>발문</dt><dd>${strip(q.prompt)}</dd>
<dt>핵심 근거</dt><dd>${e.key}</dd>
<dt>오답 포인트</dt><dd class="tip">${e.wrong}</dd>
<dt>규칙</dt><dd>${e.rule}</dd>
<dt>지문 근거</dt><dd>${e.ev}</dd>
</dl></div>`;
};

if (MODE === "flow") {
  // 고정 페이지 박스를 쓰지 않고 CSS 다단 자동 흐름으로 넘긴다. (측정 없이 잘림 위험을 원천 차단)
  const flowCss = CSS + `
.sheet{columns:2;column-gap:7mm;column-rule:.4pt solid #bbb;padding:12mm 11mm}
.sheet .col{width:auto}`;
  const html = `<!DOCTYPE html>\n<html lang="ko"><head><meta charset="utf-8"/><title>신구중2 8회 70문항 정답·해설</title><style>${flowCss}</style></head>
<body><div class="sheet">
<h1>2026학년도 2학기 중간고사 대비 8회 · 정답 및 해설</h1>
<p class="subtitle">신구중학교 2학년 · 영어 · 70문항 (Lesson 5·6) · 전 문항 객관식</p>
${keyTable()}
<h2>문항별 해설</h2>
${QUESTIONS.map(itemHtml).join("\n")}
</div></body></html>`;
  fs.writeFileSync(path.join(OUT, "round8-answers-70.html"), html);
  console.log("answers written (auto-flow)", path.join(OUT, "round8-answers-70.html"), "items", QUESTIONS.length);
} else if (MODE === "draft") {
  const headerBlock = `<h1>2026학년도 2학기 중간고사 대비 8회 · 정답 및 해설</h1>
<p class="subtitle">신구중학교 2학년 · 영어 · 70문항 (Lesson 5·6) · 전 문항 객관식</p>
${keyTable()}
<h2>문항별 해설</h2>`;
  const html = `<!DOCTYPE html>\n<html lang="ko"><head><meta charset="utf-8"/><title>answers draft</title><style>${CSS}</style></head>
<body><div id="measureHeader" style="width:188mm">${headerBlock}</div>
<div class="cols"><div class="col" id="measureCol"${MEASURE_WIDTH}>${QUESTIONS.map(itemHtml).join("")}</div><div class="col"></div></div></body></html>`;
  fs.writeFileSync(path.join(OUT, "answers-draft.html"), html);
  console.log("answers draft written", QUESTIONS.length, "items");
} else {
  const heights = JSON.parse(fs.readFileSync(path.resolve(process.argv[3]), "utf8"));
  const hmap = new Map(heights.units.map((u) => [u.start, u.h]));
  const MM = 96 / 25.4;
  const pageH = 273 * MM;
  const colFull = pageH - 24 * MM; // .page 상하 padding 12mm씩
  const headerBlock = `<h1>2026학년도 2학기 중간고사 대비 8회 · 정답 및 해설</h1>
<p class="subtitle">신구중학교 2학년 · 영어 · 70문항 (Lesson 5·6) · 전 문항 객관식</p>
${keyTable()}
<h2>문항별 해설</h2>`;
  const headerH = heights.headerH;
  const cols = [];
  let cur = null;
  const fresh = (avail) => {
    cur = { items: [], avail };
    cols.push(cur);
  };
  fresh(colFull - 4 * MM - headerH); // 1페이지 첫 컬럼은 헤더(정답표 포함)가 차지
  QUESTIONS.forEach((q) => {
    const h = hmap.get(q.num);
    if (h === undefined) throw new Error("no height for " + q.num);
    const used = cur.items.reduce((s, n) => s + hmap.get(n), 0) + Math.max(0, cur.items.length - 1) * 3 * MM;
    if (h > cur.avail) throw new Error("answer item " + q.num + " too tall " + h.toFixed(0) + " > " + cur.avail.toFixed(0));
    if (cur.items.length && used + 3 * MM + h > cur.avail) fresh(colFull);
    cur.items.push(q.num);
  });
  const pages = [];
  for (let i = 0; i < cols.length; i += 2) pages.push(cols.slice(i, i + 2));
  let first = true;
  const bodyHtml = pages
    .map((pc) => {
      const colsHtml = pc
        .map((c) => `<div class="col">${c.items.map((n) => itemHtml(QUESTIONS[n - 1])).join("")}</div>`)
        .join("");
      const head = first ? headerBlock : "";
      first = false;
      return `<div class="page anspage">${head}<div class="cols">${colsHtml}</div></div>`;
    })
    .join("\n");
  const html = `<!DOCTYPE html>\n<html lang="ko"><head><meta charset="utf-8"/><title>신구중2 8회 70문항 정답·해설</title><style>${CSS}</style></head>
<body>${bodyHtml}</body></html>`;
  fs.writeFileSync(path.join(OUT, "round8-answers-70.html"), html);
  console.log("answers written", path.join(OUT, "round8-answers-70.html"), "cols", cols.length, "pages", pages.length);
}
