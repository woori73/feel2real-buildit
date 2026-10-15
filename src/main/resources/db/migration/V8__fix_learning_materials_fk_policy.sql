-- V8: Fix learning material FK delete policies for account withdrawal.

ALTER TABLE learning_materials
  DROP FOREIGN KEY fk_learning_materials_assigned_by;

ALTER TABLE learning_materials
  MODIFY COLUMN assigned_by BIGINT NULL COMMENT 'user_id (Admin or TUTOR). Set NULL on withdrawal';

ALTER TABLE learning_materials
  ADD CONSTRAINT fk_learning_materials_assigned_by
    FOREIGN KEY (assigned_by) REFERENCES users(user_id) ON DELETE SET NULL;

ALTER TABLE learning_materials
  DROP FOREIGN KEY fk_learning_materials_problem_source;

ALTER TABLE learning_materials
  ADD CONSTRAINT fk_learning_materials_problem_source
    FOREIGN KEY (problem_source_id) REFERENCES sources(source_id) ON DELETE CASCADE;

ALTER TABLE learning_material_answers
  DROP FOREIGN KEY fk_learning_material_answers_source;

ALTER TABLE learning_material_answers
  ADD CONSTRAINT fk_learning_material_answers_source
    FOREIGN KEY (source_id) REFERENCES sources(source_id) ON DELETE CASCADE;
