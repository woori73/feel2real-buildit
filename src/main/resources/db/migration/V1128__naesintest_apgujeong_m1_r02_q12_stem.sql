-- 내신 실전 연습(/naesintest) — 압구정중1 2학기 기말 예상문제 2회 12번 발문을 다른 회와 같게 맞춘다.
-- V1127에서 2회 12번만 「밑줄 친 부분의 쓰임이 ⓐ와 같은 것은?」이었고, 1·3~7회의 같은 유형은
-- 「밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은?」이다(출제 교사 기출 발문). 정답(⑤)·배점(3점)·선택지는 그대로다.
-- 문항 그림(static/naesin/apgujeong-m1-2026-2/r02/q12.webp)도 같은 발문으로 다시 만들었다. 여기서는 복습 화면 해설 첫 줄만 고친다.

UPDATE tp_questions q
JOIN tp_exams e ON e.exam_id = q.exam_id
SET q.explanation = REPLACE(q.explanation, '밑줄 친 부분의 쓰임이 ⓐ와 같은 것은?', '밑줄 친 곳이 ⓐ와 같은 쓰임으로 사용된 것은?')
WHERE e.exam_code = 'TP-NAESIN-APM1-2602-R02'
  AND q.order_no = 12;
