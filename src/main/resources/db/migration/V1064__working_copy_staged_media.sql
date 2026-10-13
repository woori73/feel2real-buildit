-- BuildIt S3: 이미지 업로드·삭제를 작업본에만 담아 두고 '게시' 때 공개 슬롯에 반영한다.
--
-- - academy_working_copy에 nullable 컬럼 1개만 추가한다(새 테이블 없음, 기존 행은 NULL = 변경 없음).
-- - 값: {"principal":"<storage key>","textbook1":""} 형태의 JSON. 키가 있고 값이 비어 있지 않으면 '이 이미지로 교체',
--   값이 빈 문자열이면 '비우기', 키가 없으면 공개본 그대로. storage key는 응답 DTO로 내보내지 않는다.
-- - 공개 슬롯(academies.*_storage_key)은 게시 트랜잭션에서만 바뀐다.
ALTER TABLE academy_working_copy
  ADD COLUMN staged_media_json TEXT NULL COMMENT '게시 전 이미지 교체/삭제 예약(JSON slot→storage key, 빈 문자열=비우기)';
