ALTER TABLE lesson_products
  ADD COLUMN target_group VARCHAR(20) NULL AFTER lesson_type;

UPDATE lesson_products
SET target_group = 'HIGH'
WHERE target_group IS NULL;

ALTER TABLE lesson_products
  MODIFY COLUMN target_group VARCHAR(20) NOT NULL DEFAULT 'HIGH';
