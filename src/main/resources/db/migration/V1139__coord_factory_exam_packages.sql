-- 제작 공장 → 학생 풀이 페이지 게시 연결 (2026-11-22). 제작 세션이 디지털 모의고사로 올릴 시험 묶음(JSON)을 제출하면 여기에 둔다.
-- 검수 세션 3곳은 같은 묶음을 읽어 대조하고, 원장이 제작 현황판에서 「게시 승인」하면 서버가 tp_exams·tp_questions(status=PUBLISHED)에 넣는다.
-- 게시 내림은 tp_exams.status를 ARCHIVED로 바꾼다(응시 기록이 있으므로 행을 지우지 않는다). 설계: docs/lesson-prep/FACTORY.md §5
CREATE TABLE coord_factory_exam_packages (
  job_id         CHAR(36)     NOT NULL,
  track          VARCHAR(10)  NOT NULL COMMENT '과목·시험 구분: NAESIN/KOREAN/MATH/SOCIAL/SCIENCE',
  exam_code      VARCHAR(40)  NOT NULL COMMENT '게시할 tp_exams.exam_code',
  package_json   MEDIUMTEXT   NOT NULL COMMENT '시험 묶음(제목·문항). 검증을 통과한 것만 저장',
  package_hash   CHAR(64)     NOT NULL COMMENT 'package_json의 SHA-256 — 검수 세션이 대조한 판',
  question_count INT          NOT NULL,
  submitted_by   VARCHAR(100) NOT NULL,
  submitted_at   DATETIME(6)  NOT NULL COMMENT '마지막 제출 시각. 이 뒤의 검수 PASS만 게시에 센다',
  exam_id        BIGINT       NULL COMMENT '게시한 tp_exams.exam_id(게시 전 NULL)',
  published_at   DATETIME(6)  NULL,
  PRIMARY KEY (job_id),
  KEY idx_coord_factory_exam_packages_code (exam_code),
  CONSTRAINT fk_coord_factory_exam_packages_job FOREIGN KEY (job_id) REFERENCES coord_jobs (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
