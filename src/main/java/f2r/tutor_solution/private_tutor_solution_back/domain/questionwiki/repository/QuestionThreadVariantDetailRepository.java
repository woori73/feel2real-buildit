package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadVariantDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionThreadVariantDetailRepository
    extends JpaRepository<QuestionThreadVariantDetail, Long> {

  Optional<QuestionThreadVariantDetail> findByPost_PostId(Long postId);

  @Query("""
      select v from QuestionThreadVariantDetail v
      where v.post.questionWiki.questionWikiId = :questionWikiId and v.post.visible = true
      order by v.post.createdAt asc, v.variantId asc
      """)
  List<QuestionThreadVariantDetail> findVisibleByQuestionWikiId(
      @Param("questionWikiId") Long questionWikiId);
}
