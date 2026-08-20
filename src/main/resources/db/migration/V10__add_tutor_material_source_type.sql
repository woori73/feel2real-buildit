-- ============================================================
-- V10: sources.source_type ENUM에 TUTOR_MATERIAL 추가
--   · 강사/Admin이 학생에게 올리는 '제출 불필요' 시험대비 참고자료용 값.
--   · 기존 3종(STUDENT_MATERIAL / HOMEWORK_PROBLEM / HOMEWORK_ANSWER)은 모두
--     학생 업로드 또는 숙제(제출) 축이라, '강사가 주는 비숙제 자료'를 담을 값이 없었음.
--   · ENUM 끝에 값만 추가 → 기존 row의 source_type 값 변화 없음 (1-step 안전).
--     (CLAUDE.md §3단계 룰은 데이터를 건드릴 때 적용; 여기선 추가형이라 무관)
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE `sources`
    MODIFY COLUMN `source_type`
        ENUM('STUDENT_MATERIAL', 'HOMEWORK_PROBLEM', 'HOMEWORK_ANSWER', 'TUTOR_MATERIAL') NOT NULL;
