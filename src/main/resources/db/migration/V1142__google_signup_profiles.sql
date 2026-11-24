CREATE TABLE google_signup_profiles (
  user_id BIGINT NOT NULL PRIMARY KEY,
  kind VARCHAR(12) NOT NULL,
  school VARCHAR(80), grade VARCHAR(12), academy VARCHAR(80), subject VARCHAR(80),
  area VARCHAR(80), contact VARCHAR(100), business VARCHAR(3),
  notice_version VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_google_signup_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
