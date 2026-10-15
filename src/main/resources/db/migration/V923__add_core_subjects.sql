-- 작업2 학원 발행: 주력 과목 확장. 영어(V1)에 더해 수학·국어·과학·논술 추가.
-- name_en / name_ko 는 UNIQUE. 영어는 이미 존재하므로 신규 4종만 INSERT 한다.
SET NAMES utf8mb4;

INSERT INTO `subjects` (`name_en`, `name_ko`) VALUES
  ('math', '수학'),
  ('korean', '국어'),
  ('science', '과학'),
  ('essay', '논술');
