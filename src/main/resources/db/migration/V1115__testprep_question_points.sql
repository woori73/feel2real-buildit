-- 내신 실전 연습(/naesintest)의 문항 배점. 기존 행과 무관한 nullable 칸 추가라 1단계로 끝낸다.
-- SAT·TOEFL 문항은 NULL(채점은 기존 방식). 데이터는 V1116.
ALTER TABLE tp_questions
  ADD COLUMN points INT NULL COMMENT '문항 배점(내신 실전 연습). SAT·TOEFL 문항은 NULL';
