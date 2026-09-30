// P1→P3 순서대로 원문 수집 큐(JSONL). articleId는 브라우저 매핑 후 채움 — 그 전 상태 NEEDS_ORIGINAL.
const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, 'scan');
const src = fs.readFileSync(path.join(dir, 'june_relevance_pass2_draft.txt'), 'utf8').split('\n');
const cmt = l => { const m = l.match(/댓글 (\d+)/); return m ? +m[1] : 0; };
const rows = [];
let bucket = null;
for (const l of src) {
  if (l.startsWith('[P1]')) { bucket = 'P1'; continue; }
  if (l.startsWith('[P3]')) { bucket = 'P3'; continue; }
  if (l.startsWith('[LOW_PENDING]')) { bucket = null; continue; }
  if (!bucket || !/^26\.06\./.test(l)) continue;
  const [date, title, cm, author] = l.split('|');
  rows.push({
    provisional_key: `${date}|${title}|${author}`,
    written_at: `2026-${date.slice(3, 5)}-${date.slice(6, 8)}`,
    title: title.trim(), author_nickname: author.trim(), comment_count: cmt(l),
    bucket, article_id: null, original_checked: false,
    status: 'NEEDS_ORIGINAL', source_community: 'CCRS_CAFE', club_id: 27453843, period: '2026-06'
  });
}
rows.sort((a, b) => (a.bucket === b.bucket ? b.comment_count - a.comment_count : a.bucket === 'P1' ? -1 : 1));
fs.writeFileSync(path.join(dir, 'june_collection_queue.jsonl'), rows.map(r => JSON.stringify(r)).join('\n') + '\n');
console.log('queued', rows.length, 'P1', rows.filter(r => r.bucket === 'P1').length, 'P3', rows.filter(r => r.bucket === 'P3').length);
