package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionThreadPostRepository extends JpaRepository<QuestionThreadPost, Long> {

  /** 문항의 모든 visible 글(최상위 + 답글). 서비스에서 트리로 조립한다. */
  @Query("""
      select p from QuestionThreadPost p
      join fetch p.author a
      left join fetch p.parentPost parent
      where p.questionWiki.questionWikiId = :questionWikiId
        and p.visible = true
      order by p.createdAt asc, p.postId asc
      """)
  List<QuestionThreadPost> findVisibleByQuestionWikiId(@Param("questionWikiId") Long questionWikiId);

  @Query("""
      select p from QuestionThreadPost p
      join fetch p.author a
      join fetch p.questionWiki q
      left join fetch p.parentPost parent
      where p.postId = :postId
      """)
  Optional<QuestionThreadPost> findDetailById(@Param("postId") Long postId);

  long countByQuestionWiki_QuestionWikiIdAndVisibleTrue(Long questionWikiId);
}
