import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const prefixPath = join(projectRoot, 'data', 'newstart-cafe', 'thanks-members', 'naver-id-prefix-2026-10-01', 'naver-id-prefix.json');
const outPath = join(projectRoot, 'src', 'main', 'resources', 'db', 'migration', 'V1016__add_naver_id_prefix_to_thanks_roster.sql');
const sourceFile = 'data/newstart-cafe/thanks-members/naver-id-prefix-2026-10-01/naver-id-prefix.json';
const snapshotDate = '2026-10-01';

const doc = JSON.parse(await readFile(prefixPath, 'utf8'));
const records = doc.records;

const sqlStr = (value) => `'${String(value).replace(/'/g, "''")}'`;

const updates = records.map((r) => (
  'UPDATE `newstart_thanks_roster`\r\n'
  + `    SET \`naver_id_prefix\` = ${sqlStr(r.naverIdPrefix)}\r\n`
  + `    WHERE \`snapshot_date\` = '${snapshotDate}' AND \`cafe_member_tag\` = ${sqlStr(r.cafeMemberTag)};`
));

const sql = `-- ============================================================\r\n`
  + `-- V1016: newstart_thanks_roster 에 네이버 ID 접두부(보조 식별정보) 추가\r\n`
  + `--   · 기존 V1015 / RAW 503 / roster 는 수정하지 않는다 — 컬럼 추가 + 값 반영만 한다.\r\n`
  + `--   · naver_id_prefix 는 Cafe Studio 화면에서 마스킹되어 노출되는 네이버 ID 접두부다\r\n`
  + `--     (maskedMemberId 에서 후행 '*' 제거). 전체 네이버 ID 가 아니며, 네이버 OAuth subject 와도 다르다.\r\n`
  + `--   · 이 값만으로 THANKS_MEMBER 를 자동 부여하지 않는다 — 본인확인 보조정보로만 쓴다.\r\n`
  + `--     같은 접두부가 여러 네이버 ID 에 존재할 수 있어 단독 확정 식별자가 아니다.\r\n`
  + `--   · 적재는 기존 row 를 (snapshot_date, cafe_member_tag) 로 찾아 갱신하므로 idempotent 하다.\r\n`
  + `--   · source: ${sourceFile}\r\n`
  + `-- ============================================================\r\n\r\n`
  + `SET NAMES utf8mb4;\r\n`
  + `SET CHARACTER SET utf8mb4;\r\n\r\n`
  + `ALTER TABLE \`newstart_thanks_roster\`\r\n`
  + `    ADD COLUMN \`naver_id_prefix\` VARCHAR(64) NULL COMMENT '네이버 ID 접두부(maskedMemberId 에서 후행 * 제거). 전체 ID 아님 — 본인확인 보조정보' AFTER \`cafe_member_tag\`;\r\n\r\n`
  + `${updates.join('\r\n')}\r\n`;

await writeFile(outPath, sql);
console.log(`wrote ${outPath} with ${records.length} updates`);
