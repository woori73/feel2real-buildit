import { readdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const baseDir = join(projectRoot, 'data', 'newstart-cafe', 'thanks-members', 'roster-2026-10-01');
const rawDir = join(baseDir, 'raw');

const files = (await readdir(rawDir)).filter((f) => f.endsWith('.json')).sort();
const batches = [];
for (const file of files) {
  batches.push(JSON.parse(await readFile(join(rawDir, file), 'utf8')));
}

// Preserve collection order: page asc, then chunk asc.
batches.sort((a, b) => a.page - b.page || a.chunk - b.chunk);

const rows = [];
for (const batch of batches) {
  for (const record of batch.records) {
    rows.push({ page: batch.page, chunk: batch.chunk, ...record });
  }
}

const byTag = new Map();
const duplicates = [];
const untagged = [];
for (const row of rows) {
  if (!row.cafeMemberTag) {
    untagged.push(row);
    continue;
  }
  if (byTag.has(row.cafeMemberTag)) {
    duplicates.push({ cafeMemberTag: row.cafeMemberTag, first: byTag.get(row.cafeMemberTag).nickname, again: row.nickname, page: row.page });
  } else {
    byTag.set(row.cafeMemberTag, row);
  }
}

const capturedAt = batches.map((b) => b.capturedAt).sort()[0];
const sourceUrl = batches[0]?.url ?? null;

const sha256 = (value) => createHash('sha256').update(value).digest('hex');

const roster = {
  source: {
    site: 'Naver Cafe Studio (studio.cafe.naver.com)',
    cafe: '새출발기금 부실차주 라이브러리',
    cafeId: '31312978',
    memberLevelFilter: 150,
    memberLevelLabel: '감사멤버',
    perPage: 200,
    url: sourceUrl,
    capturedAt,
    collectedBy: '지피지기자산 (cafe operator session)',
  },
  fields: ['page', 'chunk', 'nickname', 'grade', 'cafeMemberTag', 'activityStop', 'nicknameChanged', 'memo', 'gradeChangedDate', 'autoLevelExcluded', 'accumulatedReward', 'lastVisitDate', 'joinedDate', 'visitCount', 'postCount', 'commentCount', 'likeCount'],
  notes: [
    'cafeMemberTag is the Cafe screen member identifier (멤버 태그). Naver login ID is masked on screen and was intentionally NOT captured.',
    'gradeChangedDate is the most recent grade promotion/demotion date, NOT the date the member first became a 감사멤버.',
    'This roster is a point-in-time snapshot and must not be merged by addition with the 2026-10-01 21-row increment file.',
  ],
  stats: {
    totalPages: Math.max(...batches.map((b) => b.page)),
    rawRows: rows.length,
    uniqueThanksMembers: byTag.size,
    duplicateRows: duplicates.length,
    untaggedRows: untagged.length,
    firstMember: rows[0] ? { nickname: rows[0].nickname, cafeMemberTag: rows[0].cafeMemberTag } : null,
    lastMember: rows.at(-1) ? { nickname: rows.at(-1).nickname, cafeMemberTag: rows.at(-1).cafeMemberTag } : null,
    collectionStatus: 'COMPLETE',
  },
  members: rows,
};

const rosterJson = `${JSON.stringify(roster, null, 2)}\n`;
await writeFile(join(baseDir, 'roster.json'), rosterJson);

const manifest = {
  ...roster.stats,
  sourceUrl,
  capturedAt,
  rawChunkFiles: files,
  rawChunkCount: files.length,
  rosterSha256: sha256(rosterJson),
  duplicates,
  untagged: untagged.map((r) => ({ page: r.page, nickname: r.nickname })),
};
await writeFile(join(baseDir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

console.log(JSON.stringify(manifest, null, 2));
