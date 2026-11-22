-- TOEIC 기출 유사 1회(TP-TOEIC-FULL-01) Part 1(1~6번) 그림을 선 그림(SVG)에서 사진(WEBP)으로 바꾼다(대표 지시 2026-11-23: 실제 시험처럼 사진).
-- 사진은 F2R이 AI 이미지 생성으로 만든 것이다(실제 장면·실존 인물이 아니다). ETS 시험 사진은 쓰지 않았다.
-- 사진마다 V1135 해설에 적힌 배치(정답 동작, 오답 사물의 위치)와 맞는 것만 골랐다. 보기 문장·정답 글자는 그대로다.
-- 스키마 변경 없음. 바꾸는 것: Part 1 6문항의 그림 줄 경로(.svg → .webp), 해설의 '그림' → '사진', 세트 설명 문장.
-- 그림 파일: src/main/resources/static/testprep/img/toeic_full01_p1_01~06.webp (선 그림 .svg 파일은 되돌릴 때를 위해 남긴다).

SET @toeic_full01_exam_id = (SELECT exam_id FROM tp_exams WHERE exam_code = 'TP-TOEIC-FULL-01');

UPDATE tp_questions
SET body_text = REPLACE(body_text, CONCAT('/testprep/img/toeic_full01_p1_0', order_no, '.svg]]'), CONCAT('/testprep/img/toeic_full01_p1_0', order_no, '.webp]]')),
    explanation = REPLACE(explanation, '그림', '사진')
WHERE exam_id = @toeic_full01_exam_id AND task_type = 'TOEIC_P1' AND order_no BETWEEN 1 AND 6;

UPDATE tp_exams
SET description = REPLACE(REPLACE(description,
    'Part 1 uses simple line drawings instead of photographs',
    'Part 1 uses photographs made by F2R with AI image generation (not real scenes)'),
    'Part 1의 그림은 사진이 아니라 F2R이 그린 선 그림입니다.',
    'Part 1의 사진은 F2R이 AI로 만든 사진입니다(실제 장면이 아닙니다).')
WHERE exam_code = 'TP-TOEIC-FULL-01';
