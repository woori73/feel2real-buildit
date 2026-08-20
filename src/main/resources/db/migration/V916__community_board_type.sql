-- 카테고리 모델 변경: dev-topic enum(category) → 게시판종류(board_type) + 과목(subject_id).
-- V914/V915는 이미 적용됨(불변) → V916으로 in-place 전환.
-- 탭 = 전체(필터) / 자유게시판(FREE) / Q&A(QNA) / 과목(SUBJECT, subjects 동적 참조).
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

ALTER TABLE community_posts
    ADD COLUMN board_type VARCHAR(20) NOT NULL DEFAULT 'FREE' AFTER author_user_id,
    ADD COLUMN subject_id INT NULL AFTER board_type;

-- 기존 category(있다면) 이관: FREE는 FREE 유지, 그 외(개발 등)는 QNA로.
UPDATE community_posts SET board_type = CASE WHEN category = 'FREE' THEN 'FREE' ELSE 'QNA' END;

-- V915가 만든 category 컬럼/인덱스 제거.
ALTER TABLE community_posts DROP INDEX idx_community_posts_category_visible_created;
ALTER TABLE community_posts DROP COLUMN category;

-- subject FK + 목록(게시판/과목 필터) 인덱스.
ALTER TABLE community_posts
    ADD CONSTRAINT fk_community_posts_subject FOREIGN KEY (subject_id) REFERENCES subjects(subject_id),
    ADD INDEX idx_community_posts_board_subject_visible (board_type, subject_id, is_visible, created_at, post_id);
