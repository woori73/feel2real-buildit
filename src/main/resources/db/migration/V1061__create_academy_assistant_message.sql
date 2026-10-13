-- BuildIt 통합 화면 2-A: 대화 기록 테이블 (신규 테이블 추가만, 기존 데이터 변경 없음)
--
-- - 학원당 대화 한 줄기(스레드)를 시간 순으로 저장한다. 학원이 삭제되면 함께 삭제된다.
-- - kind: TEXT(일반 말풍선) / RESULT(홈페이지 생성·수정 결과 카드) / QUESTION(AI가 추가 정보를 묻는 질문)
-- - payload_json: 결과 카드 등 부가 정보(작업본 revision, 프리셋 등). 비밀·개인정보는 담지 않는다.

CREATE TABLE IF NOT EXISTS academy_assistant_message (
  message_id BIGINT NOT NULL AUTO_INCREMENT,
  academy_id BIGINT NOT NULL,
  role VARCHAR(10) NOT NULL,
  kind VARCHAR(20) NOT NULL DEFAULT 'TEXT',
  content TEXT NOT NULL,
  payload_json TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (message_id),
  KEY idx_assistant_message_academy (academy_id, message_id),
  CONSTRAINT fk_assistant_message_academy FOREIGN KEY (academy_id)
    REFERENCES academies (academy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='BuildIt 통합 화면 AI 대화 기록 (본인만 접근)';
