-- 상담 피드백 자료가 일반/오답/숙제 source와 섞이지 않도록 sources.source_type에 전용 값을 추가한다.
-- 기존 row 무관(값 추가만) → 1단계 마이그레이션. V904(WRONG_NOTE 추가) 패턴 동일.
ALTER TABLE `sources`
    MODIFY COLUMN `source_type`
        ENUM('STUDENT_MATERIAL','HOMEWORK_PROBLEM','HOMEWORK_ANSWER','TUTOR_MATERIAL','HOMEWORK_FEEDBACK','WRONG_NOTE','CONSULTATION') NOT NULL;
