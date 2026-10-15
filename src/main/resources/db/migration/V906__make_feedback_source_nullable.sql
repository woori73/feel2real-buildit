-- 피드백도 숙제 부여처럼 첨부 파일 없이 텍스트만으로 등록할 수 있도록 확장한다.
ALTER TABLE `learning_material_feedbacks`
    MODIFY COLUMN `source_id` BIGINT NULL COMMENT 'sources.source_type=HOMEWORK_FEEDBACK. 첨부 없는 피드백은 NULL';

ALTER TABLE `learning_material_feedbacks`
    ADD COLUMN `feedback_text` TEXT NULL AFTER `source_id`;
