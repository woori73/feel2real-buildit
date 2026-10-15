package f2r.tutor_solution.private_tutor_solution_back.domain.community.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.CommunityPostAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostAttachmentRepository extends JpaRepository<CommunityPostAttachment, Long> {

  List<CommunityPostAttachment> findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(Long postId);

  long countByPost_PostIdAndVisibleTrue(Long postId);

  @Query("""
      select a from CommunityPostAttachment a
      join fetch a.post p
      where a.attachmentId = :attachmentId and a.visible = true
      """)
  Optional<CommunityPostAttachment> findVisibleById(@Param("attachmentId") Long attachmentId);

  @Query("""
      select a from CommunityPostAttachment a
      join fetch a.post p
      join fetch a.uploader u
      where a.attachmentId = :attachmentId
      """)
  Optional<CommunityPostAttachment> findWithPostById(@Param("attachmentId") Long attachmentId);
}
