const fs = require('fs');
const path = require('path');

const rawDir = path.join(__dirname, 'raw');
const outDir = path.resolve(__dirname, '../../../../data/newstart-cafe/incr_20260930/articles');
fs.mkdirSync(outDir, { recursive: true });

const files = fs.readdirSync(rawDir).filter(f => /^article_\d+_raw\.txt$/.test(f));
const recs = [];
for (const f of files) {
  const text = fs.readFileSync(path.join(rawDir, f), 'utf8');
  const g = (re) => { const m = text.match(re); return m ? m[1].trim() : null; };
  const articleId = g(/ARTICLE_ID:\s*(\d+)/);
  const board = g(/BOARD:\s*([^|\n\r]+?)(?:\s*\||$)/m);
  const title = g(/TITLE:\s*([^|\n\r]+?)(?:\s*\|\s*AUTHOR|\s*$)/m);
  const author = g(/AUTHOR:\s*([^|\n\r]+?)(?:\s*\||\s*$)/m);
  const written = g(/WRITTEN_AT:\s*([^|\n\r]+?)(?:\s*\||\s*$)/m);
  const views = g(/VIEWS:\s*(\d+)/);
  const srcType = g(/SOURCE_TYPE:\s*([^|\n\r]+?)(?:\s*\||\s*$)/m);
  const sourceUrl = g(/SOURCE_URL:\s*(\S+)/);
  const srcComm = g(/SOURCE_COMMUNITY:\s*([A-Z_]+)/);
  const isCcrs = (srcComm === 'CCRS_CAFE') || /ccrs5500|27453843/.test(sourceUrl || '') || /ccrs5500|27453843/.test(text.slice(0, 400));
  const clubId = isCcrs ? '27453843' : '31312978';
  recs.push({
    articleId, board, title, author, writtenAt: written, views: views ? Number(views) : null,
    sourceType: srcType, sourceCommunity: clubId === '31312978' ? 'NEWSTART_BORROWER_CAFE' : 'CCRS_CAFE',
    cafeClubId: Number(clubId), applicationType: isCcrs ? 'B-CAFE(신복위회원카페)' : null, sourceUrl,
    collectedAt: '2026-09-30', extractionMethod: 'Qoder browser-use iframe innerText (full body + comments)',
    contentRaw: text,
  });
}

const q = (s) => s === null || s === undefined ? 'NULL'
  : "'" + String(s).replace(/\\/g, '\\\\').replace(/'/g, "''").replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/\x1a/g, '\\Z') + "'";
const toDt = (w) => {
  if (!w) return 'NULL';
  const m = w.match(/(\d{4})\.(\d{2})\.(\d{2})(?:\.\s*(\d{1,2}):(\d{2}))?/);
  if (!m) return 'NULL';
  return q(`${m[1]}-${m[2]}-${m[3]} ${(m[4] || '00').padStart(2, '0')}:${m[5] || '00'}:00`);
};

let sql = `-- NEWSTART incremental DB add 2026-09-30 (Phase 1, branch feat/newstart-case-collector-20260930)
-- INSERT-centric only. No DELETE/UPDATE of existing rows. Knowledge items forced to status='REVIEW'.
SET NAMES utf8mb4;

`;
for (const r of recs) {
  fs.writeFileSync(path.join(outDir, `article_${r.articleId}.json`), JSON.stringify(r, null, 2) + '\n');
  const normUrl = r.sourceCommunity === 'NEWSTART_BORROWER_CAFE'
    ? `https://cafe.naver.com/newhopero/${r.articleId}`
    : `https://cafe.naver.com/ArticleRead.nhn?clubid=27453843&articleid=${r.articleId}`;
  sql += `INSERT IGNORE INTO newstart_empirical_cases
  (article_id, board_name, title, author_nickname, written_at, view_count, application_type, content_raw, source_url, collected_at, created_by, created_at, updated_at)
VALUES
  (${q(r.articleId)}, ${q((r.board || '자유게시판').slice(0, 50))}, ${q((r.title || '').slice(0, 200))}, NULL, ${toDt(r.writtenAt)}, ${r.views === null ? 'NULL' : r.views}, ${q(r.applicationType)}, ${q(r.contentRaw)}, ${q(normUrl)}, NOW(6), 'CASE_COLLECTOR_20260930', NOW(6), NOW(6));

INSERT INTO newstart_knowledge_items
  (source_type, category, title, summary, content, authority, tags, source_url, source_date, status, created_by, updated_by, created_at, updated_at)
SELECT 'CAFE_ARTICLE', '부실차주 사례', ${q(`[INCR-20260930] ${r.articleId} ${r.title || ''}`.slice(0, 200))},
  ${q('회원 원자료(raw) 보존 수집분; 카페 회원 경험/질의이며 공식 규정 아님. 리뷰 후 승인 필요.')},
  ${q(r.contentRaw)},
  ${q('부실차주 카페 회원 원자료 REVIEW ingest (비공식)')}, ${q('incremental-2026-09-30;raw-preserved;REVIEW')},
  ${q(normUrl)}, ${r.writtenAt ? 'DATE(' + toDt(r.writtenAt) + ')' : 'NULL'}, 'REVIEW', 'CASE_COLLECTOR_20260930', 'CASE_COLLECTOR_20260930', NOW(6), NOW(6)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM newstart_knowledge_items k WHERE k.title = ${q(`[INCR-20260930] ${r.articleId} ${r.title || ''}`.slice(0, 200))});

`;
}
fs.writeFileSync(path.join(__dirname, 'increment_phase1.sql'), sql);
console.log('records:', recs.map(r => r.articleId).join(','));
console.log('sql bytes:', sql.length);
