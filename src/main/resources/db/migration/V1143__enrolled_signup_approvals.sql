CREATE TABLE enrolled_signup_approvals (
    student_id BIGINT PRIMARY KEY,
    business_id BIGINT NOT NULL,
    approved_by BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    notice_version VARCHAR(20) NOT NULL,
    approved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    code_hash CHAR(64) UNIQUE,
    email_key CHAR(64),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    used_by BIGINT,
    FOREIGN KEY (used_by) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES easy_students(student_id) ON DELETE CASCADE,
    FOREIGN KEY (business_id) REFERENCES easy_businesses(business_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE CASCADE
);
