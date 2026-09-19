import { QUESTIONS } from "./questions.mjs";
import { EXPL } from "./explanations.mjs";

const dist = [0, 0, 0, 0, 0];
QUESTIONS.forEach((q) => dist[q.answer - 1]++);
console.log("n", QUESTIONS.length, "dist", dist.join(","));
console.log("badChoices", QUESTIONS.filter((q) => q.choices.length !== 5).map((q) => q.num).join(",") || "none");
console.log(
  "ansMismatch",
  EXPL.filter((e) => QUESTIONS.find((q) => q.num === e.num).answer !== e.ans).map((e) => e.num).join(",") || "none"
);
console.log("explMissing", QUESTIONS.filter((q) => !EXPL.find((e) => e.num === q.num)).map((q) => q.num).join(",") || "none");
console.log("emptyFields", EXPL.filter((e) => !e.key || !e.wrong || !e.rule || !e.axis || !e.ev).map((e) => e.num).join(",") || "none");

let run = 1;
const long = [];
for (let i = 1; i < QUESTIONS.length; i++) {
  if (QUESTIONS[i].answer === QUESTIONS[i - 1].answer) {
    run++;
    if (run >= 4) long.push(QUESTIONS[i].num);
  } else run = 1;
}
console.log("runs>=4", long.join(",") || "none");

const txt = (q) =>
  [q.passage && q.passage.html, q.cond && q.cond.html, q.cond2 && q.cond2.html, q.given, q.table && JSON.stringify(q.table), q.choices.join(" ")]
    .filter(Boolean)
    .join(" ");
QUESTIONS.forEach((q) => {
  const own = txt(q);
  const us = [...q.prompt.matchAll(/<u>(.*?)<\/u>|'([^']*?[a-zA-Z]{4}[^']*?)'/g)].map((m) => m[1] || m[2]);
  us.forEach((s) => {
    if (s.length < 4) return;
    if (!own.includes(s)) {
      const holder = QUESTIONS.filter((o) => txt(o).includes(s)).map((o) => o.num);
      console.log("underline?", q.num, JSON.stringify(s.slice(0, 40)), "in", holder.join(",") || "NOWHERE");
    }
  });
});
