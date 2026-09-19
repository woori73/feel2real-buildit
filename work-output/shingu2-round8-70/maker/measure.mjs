// 브라우저 내장 화면 없이 실제 Chromium으로 문항 높이를 측정한다.
// 사용법: node measure.mjs paper   |   node measure.mjs answers
//        → out/heights.json / out/answer-heights.json (+ *_raw.txt)
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const DOC = process.argv[2] === "answers" ? "answers" : "paper";
const CHROME = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const OUT = path.resolve("out");
const cfg =
  DOC === "answers"
    ? { draft: "answers-draft.html", json: "answer-heights.json", raw: "answer_heights_raw.txt", head: "#measureHeader", item: "[data-num]" }
    : { draft: "draft.html", json: "heights.json", raw: "heights_raw.txt", head: ".exam-header", item: "[data-unit-start]" };

const src = fs.readFileSync(path.join(OUT, cfg.draft), "utf8");
const probe = `<script>addEventListener('load',function(){
var col=document.getElementById('measureCol');
var head=document.querySelector('${cfg.head}');
var run=document.querySelector('.run-header');
var units=[].map.call(col.querySelectorAll('${cfg.item}'),function(e){return (e.getAttribute('data-unit-start')||e.getAttribute('data-num'))+':'+e.getBoundingClientRect().height.toFixed(2);});
document.title='MEAS|'+col.getBoundingClientRect().width.toFixed(2)+'|'+(head?head.getBoundingClientRect().height.toFixed(2):'0')+'|'+(run?run.getBoundingClientRect().height.toFixed(2):'0')+'|'+units.join(' ');
});</script>`;
const meas = src.includes("</body>") ? src.replace(/<\/body>/, probe + "</body>") : src + probe;
const measFile = path.join(OUT, `_measure-${DOC}.html`);
fs.writeFileSync(measFile, meas);

const dom = execFileSync(
  CHROME,
  ["--headless=new", "--disable-gpu", "--no-sandbox", "--allow-file-access-from-files",
   "--user-data-dir=" + path.resolve("..", "..", "..", "..", "pdfqa-profile").replace(/\\/g, "/"),
   "--virtual-time-budget=10000", "--dump-dom", "file:///" + measFile.replace(/\\/g, "/")],
  { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }
);
const m = dom.match(/<title>MEAS\|([\d.]+)\|([\d.]+)\|([\d.]+)\|([^<]*)<\/title>/);
if (!m) throw new Error("measurement title not found; chrome output starts: " + dom.slice(0, 300));
const [, colWidth, headerH, runHeaderH, list] = m;
const units = list.trim().split(/\s+/).map((t) => {
  const [start, h] = t.split(":");
  return { start: Number(start), h: Number(h) };
});
const total = units.reduce((s, u) => s + u.h, 0);
const payload = DOC === "answers"
  ? { headerH: Number(headerH), colWidth: Number(colWidth), total: Math.round(total * 100) / 100, units }
  : { gap: 14, headerH: Number(headerH), runHeaderH: Number(runHeaderH), n: units.length, units };
fs.writeFileSync(path.join(OUT, cfg.json), JSON.stringify(payload));
fs.writeFileSync(path.join(OUT, cfg.raw), units.map((u) => `${u.start}:${u.h.toFixed(2)}`).join(" ") + "\n");
console.log(DOC, "measured", units.length, "units colWidth", colWidth, "headerH", headerH, "runHeaderH", runHeaderH, "total", total.toFixed(2));
