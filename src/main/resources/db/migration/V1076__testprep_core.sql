-- TestPrep (TOEFL iBT practice) 핵심 테이블 — buildit2/testprep V1 스키마 이식.
-- SSO: 회원 테이블 없음 — user_id는 feel2real users.user_id와 동일 값, JWT claim에서 주입(기존 인증 재사용).
-- BaseEntity(tp_exams/tp_questions/tp_attempts): created_at/updated_at/created_by/updated_by 컬럼 필수(prd ddl-auto=validate).
-- tp_attempt_answers는 BaseEntity 미상속(수동 answered_at/updated_at)이므로 audit 컬럼 없음.
-- 실제 시험 콘텐츠(문제 시드)는 2단계에서 별도 마이그레이션으로 추가.

CREATE TABLE tp_exams (
  exam_id BIGINT NOT NULL AUTO_INCREMENT,
  exam_code VARCHAR(40) NOT NULL,
  kind VARCHAR(20) NOT NULL COMMENT 'PRACTICE / VOCAB',
  title VARCHAR(150) NOT NULL,
  subtitle VARCHAR(255) NULL,
  description TEXT NULL,
  difficulty VARCHAR(10) NULL COMMENT 'VOCAB 전용: EASY/MEDIUM/HARD',
  time_limit_sec INT NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  PRIMARY KEY (exam_id),
  CONSTRAINT uk_tp_exams_code UNIQUE (exam_code),
  INDEX idx_tp_exams_kind_status (kind, status)
);

CREATE TABLE tp_questions (
  question_id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  section VARCHAR(10) NOT NULL COMMENT 'READING/LISTENING/SPEAKING/WRITING/VOCAB',
  task_type VARCHAR(40) NOT NULL COMMENT 'COMPLETE_THE_WORDS/READ_DAILY_LIFE/READ_PASSAGE/LISTEN_RESPONSE/LISTEN_CONVERSATION/LISTEN_ANNOUNCEMENT/LISTEN_TALK/BUILD_SENTENCE/WRITE_EMAIL/WRITE_DISCUSSION/SPEAK_REPEAT/SPEAK_INTERVIEW/VOCAB_E2S/VOCAB_S2E/VOCAB_CLOZE',
  prompt TEXT NOT NULL,
  body_text MEDIUMTEXT NULL COMMENT '지문/대화 대본/빈칸 문장',
  audio_mode VARCHAR(10) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/TTS — TTS는 body_text를 브라우저 음성으로 재생',
  choices_json TEXT NULL COMMENT '선지 배열 JSON. BUILD_SENTENCE는 어구 칩 배열',
  answer_key VARCHAR(500) NULL COMMENT 'MC: A~D, BUILD_SENTENCE: 공백구분 단어순, 서술형: NULL',
  auto_score TINYINT(1) NOT NULL DEFAULT 0,
  explanation TEXT NULL,
  order_no INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  PRIMARY KEY (question_id),
  CONSTRAINT fk_tp_questions_exam FOREIGN KEY (exam_id) REFERENCES tp_exams(exam_id) ON DELETE CASCADE,
  INDEX idx_tp_questions_exam_order (exam_id, order_no)
);

CREATE TABLE tp_attempts (
  attempt_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  exam_id BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/SUBMITTED/ABANDONED',
  question_ids_json TEXT NULL COMMENT '오답 다시풀기 등 커스텀 부분 응시 시 문제 id 배열',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deadline_at DATETIME NOT NULL,
  submitted_at DATETIME NULL,
  overtime TINYINT(1) NOT NULL DEFAULT 0,
  auto_accuracy DECIMAL(5,2) NULL COMMENT '자동채점 문항 정답률 %',
  overall_band DECIMAL(2,1) NULL COMMENT 'TestPrep 내부 추정(ETS 공식 점수 아님)',
  section_scores_json TEXT NULL COMMENT '[{section,autoCount,correct,accuracy,band}]',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  PRIMARY KEY (attempt_id),
  CONSTRAINT fk_tp_attempts_exam FOREIGN KEY (exam_id) REFERENCES tp_exams(exam_id) ON DELETE RESTRICT,
  INDEX idx_tp_attempts_user_started (user_id, started_at),
  INDEX idx_tp_attempts_exam (exam_id)
);

CREATE TABLE tp_attempt_answers (
  answer_id BIGINT NOT NULL AUTO_INCREMENT,
  attempt_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_text MEDIUMTEXT NULL COMMENT 'MC 선택(A~D)/문장 순서/서술/소리 base64 data-URL',
  is_correct TINYINT(1) NULL COMMENT '자동채점 불가 문항은 NULL',
  answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (answer_id),
  CONSTRAINT fk_tp_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES tp_attempts(attempt_id) ON DELETE CASCADE,
  CONSTRAINT fk_tp_attempt_answers_question FOREIGN KEY (question_id) REFERENCES tp_questions(question_id) ON DELETE RESTRICT,
  CONSTRAINT uk_tp_attempt_answers_attempt_question UNIQUE (attempt_id, question_id),
  INDEX idx_tp_attempt_answers_question (question_id)
);
