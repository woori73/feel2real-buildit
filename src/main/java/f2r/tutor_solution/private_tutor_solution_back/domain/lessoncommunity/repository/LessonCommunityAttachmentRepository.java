package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonCommunityAttachmentRepository extends JpaRepository<LessonCommunityAttachment, Long> {
  List<LessonCommunityAttachment> findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(Long postId);
  List<LessonCommunityAttachment> findByComment_CommentIdAndVisibleTrueOrderByAttachmentIdAsc(Long commentId);
  long countByPost_PostIdAndVisibleTrue(Long postId);

  @Query("""
      select a from LessonCommunityAttachment a
      left join fetch a.post p
      left join fetch p.lesson pl
      left join fetch pl.academy pla
      left join fetch a.comment c
      left join fetch c.post cp
      left join fetch cp.lesson cpl
      left join fetch cpl.academy cpla
      where a.attachmentId = :attachmentId and a.visible = true
      """)
  Optional<LessonCommunityAttachment> findReadableById(@Param("attachmentId") Long attachmentId);
}
