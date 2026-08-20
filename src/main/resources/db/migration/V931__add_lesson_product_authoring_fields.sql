ALTER TABLE lesson_products
  ADD COLUMN tab_guidance_label VARCHAR(120) NULL,
  ADD COLUMN tab_short_name VARCHAR(80) NULL,
  ADD COLUMN kicker VARCHAR(200) NULL,
  ADD COLUMN highlight VARCHAR(300) NULL,
  ADD COLUMN learner_need VARCHAR(300) NULL,
  ADD COLUMN core_detail_title VARCHAR(200) NULL,
  ADD COLUMN explanation_1 VARCHAR(500) NULL,
  ADD COLUMN explanation_2 VARCHAR(500) NULL,
  ADD COLUMN explanation_3 VARCHAR(500) NULL,
  ADD COLUMN theme_key VARCHAR(20) NULL,
  ADD COLUMN creation_key VARCHAR(36) NULL,
  ADD COLUMN authoring_revision BIGINT NULL,
  ADD CONSTRAINT uk_lesson_products_academy_creation_key
    UNIQUE (academy_id, creation_key);
