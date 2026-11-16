-- 임시 비밀번호(관리자 발급 · 계정 찾기 재설정). 비밀번호 자체는 users.password에 해시로만 저장하고, 기록 표에는 넣지 않는다.
-- password_change_required=1이면 로그인 직후 새 비밀번호 설정 화면만 쓸 수 있다. temp_password_expires_at이 지나면 임시 비밀번호로 로그인할 수 없다.
ALTER TABLE users
  ADD COLUMN password_change_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '임시 비밀번호 상태(로그인 후 변경 강제)',
  ADD COLUMN temp_password_expires_at DATETIME(6) NULL COMMENT '임시 비밀번호 유효기간(서버 시간대)';

-- 관리자 임시 비밀번호 발급 기록: 누가·언제·어느 회원에게. 회원이 탈퇴해도 기록은 남도록 FK를 두지 않고 아이디를 함께 적는다.
CREATE TABLE temporary_password_issues (
  issue_id BIGINT NOT NULL AUTO_INCREMENT,
  target_user_id BIGINT NOT NULL,
  target_login_id VARCHAR(50) NOT NULL,
  issued_by_user_id BIGINT NOT NULL,
  issued_by_login_id VARCHAR(50) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (issue_id),
  KEY idx_temporary_password_issues_target (target_user_id, issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
