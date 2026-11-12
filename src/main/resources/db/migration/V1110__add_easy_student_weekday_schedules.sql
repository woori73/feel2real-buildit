-- 원장이 직접 고친 등원 요일. 원본 진행표·출결 기록은 변경하지 않는다.
CREATE TABLE easy_student_weekday_schedules (
  schedule_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  business_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  weekdays VARCHAR(40) NULL COMMENT 'NULL=기존 자료 기준, 빈 문자열=정기 등원 요일 미정',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(50),
  updated_by VARCHAR(50),
  CONSTRAINT uk_easy_student_weekdays UNIQUE (business_id, student_id),
  CONSTRAINT fk_easy_student_weekdays_business FOREIGN KEY (business_id) REFERENCES easy_businesses(business_id),
  CONSTRAINT fk_easy_student_weekdays_student FOREIGN KEY (student_id) REFERENCES easy_students(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
