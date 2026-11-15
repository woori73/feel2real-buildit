-- TESTPREP 문항 한글 도움 자료(해설란) — Digital SAT 연습 화면의 "해설" 버튼에서 보여 준다.
-- JSON: {"translation": 한글 해석, "vocab": [[영어, 뜻], ...], "solution": 풀이 설명, "strategy": 풀이 전략}
-- 기존 행과 무관한 nullable 컬럼 추가라 1단계로 끝낸다(docs/WORKFLOW.md §4 데이터 마이그레이션 3단계 대상 아님).
ALTER TABLE tp_questions
  ADD COLUMN ko_help_json MEDIUMTEXT NULL COMMENT '한글 해석·어휘·풀이·전략 JSON (SAT 해설란)';
