-- 공개 커뮤니티 Q&A 단순화: 댓글 폐기, 답변만 유지(질문→답변 2계층).
-- 답변(Tiptap 서식/이미지)과 댓글(plain text)이 기능상 겹쳐 댓글 계층 제거.
-- community_comments는 posts/answers/users로만 FK가 나가므로 단독 DROP 안전.
-- (lesson_community_comments는 별개 도메인 — 영향 없음.)
DROP TABLE IF EXISTS community_comments;
