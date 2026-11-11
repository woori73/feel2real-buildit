-- 학생명단(시간표) 자료의 이름 ↔ AcademyCraft 학생(rea_students.id) 연결을 원장이 확인한 기록.
-- 이름만 같은 학생은 자동으로 연결하지 않는다. 원장이 학생을 골라 확인(LINKED)하거나 명단에서 제외(EXCLUDED)한 행만 남긴다.
-- 신규 테이블이라 기존 row와 무관(1단계 마이그레이션).
CREATE TABLE academy_roster_student_links (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  academy_id BIGINT NOT NULL,
  roster_year INT NOT NULL,
  roster_name VARCHAR(40) NOT NULL,
  link_status VARCHAR(16) NOT NULL,
  rea_student_id BIGINT NULL,
  confirmed_by BIGINT NOT NULL,
  confirmed_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_academy_roster_link (academy_id, roster_year, roster_name),
  KEY idx_academy_roster_link_student (academy_id, rea_student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
