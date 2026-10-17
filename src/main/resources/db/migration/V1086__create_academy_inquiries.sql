-- 학원 공개 홈페이지 상담신청 접수 (신규 테이블 추가만, 기존 데이터 변경 없음)
--
-- - academy_id로 학원별 격리. 조회·상태 변경은 항상 academy_id 조건으로만 한다.
-- - 개인정보(name/phone/message)는 NULL 허용: 향후 개인정보 삭제 요청 시 행을 지우지 않고 값만 비우는
--   익명화(anonymized_at 기록)가 가능하도록 한다. 이번 버전에는 삭제·익명화 기능을 넣지 않는다.
-- - consented_at/consent_version: 개인정보 수집·이용 동의 증빙. retention_until: 보관 만료일(접수일 + 1년).
-- - 학원이 삭제되면 접수 내역도 함께 삭제된다(ON DELETE CASCADE).

CREATE TABLE IF NOT EXISTS academy_inquiries (
  inquiry_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  name VARCHAR(50) NULL,
  phone VARCHAR(20) NULL,
  message VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  consent_version VARCHAR(20) NOT NULL,
  consented_at DATETIME NOT NULL,
  retention_until DATE NOT NULL,
  anonymized_at DATETIME NULL,
  handled_by_user_id BIGINT NULL,
  handled_at DATETIME NULL,
  created_at DATETIME NULL,
  updated_at DATETIME NULL,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  PRIMARY KEY (inquiry_id),
  KEY idx_academy_inquiries_academy_status (academy_id, status, created_at),
  KEY idx_academy_inquiries_retention (retention_until),
  CONSTRAINT fk_academy_inquiries_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='학원 공개 홈페이지 상담신청 접수 (학원별 격리, 개인정보 포함)';
