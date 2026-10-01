import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const rosterPath = join(projectRoot, 'data', 'newstart-cafe', 'thanks-members', 'roster-2026-10-01', 'roster.json');
const outPath = join(projectRoot, 'src', 'main', 'resources', 'db', 'migration', 'V1015__add_newstart_thanks_roster.sql');
const sourceFile = 'data/newstart-cafe/thanks-members/roster-2026-10-01/roster.json';
const snapshotDate = '2026-10-01';

const roster = JSON.parse(await readFile(rosterPath, 'utf8'));

const toDate = (value) => {
  const m = /^(\d{2})\.(\d{2})\.(\d{2})\.$/.exec(value ?? '');
  if (!m) return 'NULL';
  return `'20${m[1]}-${m[2]}-${m[3]}'`;
};
const sqlStr = (value) => (value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`);

const values = roster.members.map((m) => {
  const cols = [
    sqlStr(m.cafeMemberTag),
    sqlStr(m.nickname),
    sqlStr(m.grade ?? '감사멤버'),
    toDate(m.gradeChangedDate),
    150,
    "'NAVER_CAFE'",
    `'${snapshotDate}'`,
    sqlStr(roster.source?.url),
    `'${sourceFile}'`,
  ];
  return `  (${cols.join(', ')})`;
});

const sql = `-- ============================================================
-- V1015: NewStart 오픈 전 감사회원 사전자격명부 (pre-entitlement roster)
--   · newstart_member_grants(실제 THANKS_MEMBER 실행 권한)와 분리한다.
--     이 테이블은 "Cafe 에서 감사멤버였음"이라는 사실만 스냅샷으로 보존한다.
--   · 신규 테이블 + 1회성 참조 데이터 적재이므로 단일 파일로 처리한다 (WORKFLOW §4).
--   · cafe_member_tag 는 Cafe Studio 화면 식별자이며 네이버 OAuth subject 와 동일하지 않다.
--     provider_user_id 로의 확정 매칭 전에는 match_status=UNMATCHED, linked_* 는 NULL 이다.
--   · grade_changed_at 은 "최근 등급변경일"이지 최초 감사멤버 승인일이 아니다 — 신규 판정에 쓰지 않는다.
--   · 적재는 ON DUPLICATE KEY UPDATE 로 idempotent 하다.
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE \`newstart_thanks_roster\` (
    \`roster_id\` BIGINT NOT NULL AUTO_INCREMENT,
    \`cafe_member_tag\` VARCHAR(32) NOT NULL COMMENT 'Cafe Studio 멤버 태그(화면 식별자)',
    \`nickname\` VARCHAR(50) NOT NULL COMMENT '수집 시점 Cafe 닉네임',
    \`grade\` VARCHAR(30) NOT NULL COMMENT '예: 감사멤버',
    \`grade_changed_at\` DATE NULL COMMENT '최근 등급변경일 — 최초 감사멤버 승인일 아님',
    \`member_level\` INT NOT NULL DEFAULT 150 COMMENT 'Cafe memberLevel (150=감사멤버)',
    \`source\` VARCHAR(20) NOT NULL DEFAULT 'NAVER_CAFE',
    \`snapshot_date\` DATE NOT NULL COMMENT '명부 스냅샷 날짜',
    \`source_url\` VARCHAR(512) NULL,
    \`source_file\` VARCHAR(255) NULL COMMENT 'repository 원본 경로',
    \`match_status\` VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED' COMMENT 'UNMATCHED / MATCHED',
    \`linked_user_id\` BIGINT NULL COMMENT '확정 매칭된 users.user_id (미매칭 시 NULL)',
    \`linked_provider_user_id\` VARCHAR(64) NULL COMMENT '확정 매칭된 네이버 OAuth subject (미매칭 시 NULL)',
    \`created_at\` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    \`updated_at\` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    \`created_by\` VARCHAR(50) NULL,
    \`updated_by\` VARCHAR(50) NULL,
    PRIMARY KEY (\`roster_id\`),
    UNIQUE KEY \`uk_newstart_thanks_roster_snapshot_tag\` (\`snapshot_date\`, \`cafe_member_tag\`),
    KEY \`idx_newstart_thanks_roster_match\` (\`match_status\`),
    KEY \`idx_newstart_thanks_roster_user\` (\`linked_user_id\`),
    CONSTRAINT \`fk_newstart_thanks_roster_user\` FOREIGN KEY (\`linked_user_id\`) REFERENCES \`users\` (\`user_id\`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO \`newstart_thanks_roster\`
    (\`cafe_member_tag\`, \`nickname\`, \`grade\`, \`grade_changed_at\`, \`member_level\`, \`source\`, \`snapshot_date\`, \`source_url\`, \`source_file\`)
VALUES
${values.join(',\n')}
ON DUPLICATE KEY UPDATE
    \`nickname\` = VALUES(\`nickname\`),
    \`grade\` = VALUES(\`grade\`),
    \`grade_changed_at\` = VALUES(\`grade_changed_at\`),
    \`source_url\` = VALUES(\`source_url\`),
    \`source_file\` = VALUES(\`source_file\`);
`;

await writeFile(outPath, sql);
console.log(`wrote ${outPath} with ${roster.members.length} rows`);
