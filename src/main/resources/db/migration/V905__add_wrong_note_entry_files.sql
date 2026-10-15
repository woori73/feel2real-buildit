CREATE TABLE wrong_note_entry_files (
  entry_file_id BIGINT NOT NULL AUTO_INCREMENT,
  entry_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  order_num INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  PRIMARY KEY (entry_file_id),
  CONSTRAINT fk_wrong_note_entry_files_entry FOREIGN KEY (entry_id) REFERENCES wrong_note_entries(entry_id) ON DELETE CASCADE,
  CONSTRAINT fk_wrong_note_entry_files_source FOREIGN KEY (source_id) REFERENCES sources(source_id) ON DELETE CASCADE,
  UNIQUE KEY uk_wrong_note_entry_files_entry_order (entry_id, order_num),
  INDEX idx_wrong_note_entry_files_entry (entry_id),
  INDEX idx_wrong_note_entry_files_source (source_id)
);

INSERT INTO wrong_note_entry_files (entry_id, source_id, order_num, created_at, updated_at, created_by, updated_by)
SELECT entry_id, source_id, 1, created_at, updated_at, created_by, updated_by
FROM wrong_note_entries;
