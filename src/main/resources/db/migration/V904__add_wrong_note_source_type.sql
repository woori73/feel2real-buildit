-- 오답노트 전용 업로드 파일이 일반 학습자료(STUDENT_MATERIAL)에 섞이지 않도록 sources.source_type에 전용 값을 추가한다.
ALTER TABLE `sources`
    MODIFY COLUMN `source_type`
        ENUM('STUDENT_MATERIAL', 'HOMEWORK_PROBLEM', 'HOMEWORK_ANSWER', 'TUTOR_MATERIAL', 'HOMEWORK_FEEDBACK', 'WRONG_NOTE') NOT NULL;
