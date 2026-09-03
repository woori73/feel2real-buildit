package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadExamEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionThreadExamEvidenceRepository
    extends JpaRepository<QuestionThreadExamEvidence, Long> {

  Optional<QuestionThreadExamEvidence> findByPost_PostId(Long postId);

  @Query("""
      select e from QuestionThreadExamEvidence e
      where e.post.questionWiki.questionWikiId = :questionWikiId and e.post.visible = true
      order by e.post.createdAt asc, e.evidenceId asc
      """)
  List<QuestionThreadExamEvidence> findVisibleByQuestionWikiId(
      @Param("questionWikiId") Long questionWikiId);
}
