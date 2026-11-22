-- 내신 실전 연습(/naesintest) — 압구정중 2학년 2학기 기말 16회 40번 해설 정리.
-- V1116에서 해설 PDF의 마지막 문항(16회 40번) 뒤에 이어지던 「기말고사 총정리 · 교사용」 글(약 3만 7천 자)이
-- 해설 칸에 함께 들어갔다. 해설 본문("근거: L8 본문 B ⑨"까지)만 남기고 뒤의 글을 잘라 낸다.
-- 정답(answer_key)·배점(points)·문항 그림은 바꾸지 않는다. 표식 문자열이 없으면 값이 그대로라 다시 실행해도 안전하다.

UPDATE tp_questions q
  JOIN tp_exams e ON e.exam_id = q.exam_id
   SET q.explanation = SUBSTRING_INDEX(q.explanation, ' F2R Feel to Real', 1)
 WHERE e.exam_code = 'TP-NAESIN-APM2-2602-R16'
   AND q.order_no = 40
   AND q.explanation LIKE '% F2R Feel to Real%';
