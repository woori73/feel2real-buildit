-- ============================================================
-- V7: 어드민 임퍼소네이트 업로드의 uploaded_by 보정
--
-- 버그: /conversation/dashboard, /conversation/chat에서 어드민이 학생을
-- 임퍼소네이트한 채 업로드한 자료의 sources.uploaded_by가 학생이 아닌
-- 어드민 user_id로 저장되어 있었음. 학생이 본인 계정으로 들어와
-- exam-review를 열면 verifyOwnsSource에서 403이 떨어짐.
--
-- 프론트 fix는 conversation.js / conversation_dashboard.js에서 마쳤고,
-- 본 마이그레이션은 이미 잘못 박힌 row를 학생 user_id로 일괄 보정한다.
--
-- 안전 가드:
--   1) uploader.role = 'ADMIN' 인 row만 대상 (TUTOR/STUDENT 업로드는 손대지 않음)
--   2) conversation_sources를 통해 첨부된 대화의 학생 user_id로만 재매핑
--   3) 이미 학생 user_id로 박혀 있는 row는 WHERE에서 제외
--
-- 컬럼 추가/제약 변경이 없으므로 (Entity 영향 없음) 단일 V_파일로 처리.
-- ============================================================

UPDATE sources s
JOIN conversation_sources cs ON cs.source_id = s.source_id
JOIN conversations c         ON c.conversation_id = cs.conversation_id
JOIN students st             ON st.student_id = c.student_id
JOIN users uploader          ON uploader.user_id = s.uploaded_by
SET s.uploaded_by = st.user_id
WHERE uploader.role = 'ADMIN'
  AND s.uploaded_by <> st.user_id;
