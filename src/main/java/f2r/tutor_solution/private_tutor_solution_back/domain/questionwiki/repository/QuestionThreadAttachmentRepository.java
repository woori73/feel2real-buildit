package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionThreadAttachmentRepository
    extends JpaRepository<QuestionThreadAttachment, Long> {

  List<QuestionThreadAttachment> findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(Long postId);

  @Query("""
      select att from QuestionThreadAttachment att
      join fetch att.post p
      join fetch p.questionWiki q
      where att.attachmentId = :attachmentId and att.visible = true
      """)
  Optional<QuestionThreadAttachment> findVisibleDetailById(@Param("attachmentId") Long attachmentId);

  long countByPost_PostIdAndVisibleTrue(Long postId);
}
