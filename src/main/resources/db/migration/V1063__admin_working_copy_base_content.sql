-- 관리자 작업본 충돌 보호(PR-C): 작업본을 만든(또는 마지막으로 공개본에 맞춘) 시점의 공개 내용을 보관한다.
-- 그 사이 공개 홈페이지가 바뀌었을 때(회원 게시·다른 관리자 게시), 관리자가 바꾼 필드만 새 공개 내용 위에 얹고
-- 양쪽이 같은 필드를 바꾼 경우는 공개 쪽 값을 유지하며 충돌로 알린다. 컬럼 추가만 하고 기존 데이터는 바꾸지 않는다.
-- (V1062로 만든 신규 테이블의 컬럼 추가. 기존 행은 NULL이며 이 경우 병합은 공개 내용을 우선한다.)

ALTER TABLE academy_admin_working_copy
  ADD COLUMN base_intro TEXT NULL AFTER base_fingerprint,
  ADD COLUMN base_page_settings_json MEDIUMTEXT NULL AFTER base_intro;
