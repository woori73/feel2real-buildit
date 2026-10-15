package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWiki;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionWikiRepository extends JpaRepository<QuestionWiki, Long> {

  @Query("""
      select q from QuestionWiki q
      where q.visible = true
        and (:subject is null or q.subject = :subject)
        and (:grade is null or q.grade = :grade)
        and (:examYear is null or q.examYear = :examYear)
        and (:examLabel is null or q.examLabel = :examLabel)
      order by q.grade asc, q.questionNumber asc, q.questionWikiId asc
      """)
  List<QuestionWiki> findVisibleNav(@Param("subject") String subject,
      @Param("grade") Integer grade,
      @Param("examYear") Integer examYear,
      @Param("examLabel") String examLabel);

  @Query("select q from QuestionWiki q where q.questionWikiId = :id and q.visible = true")
  Optional<QuestionWiki> findVisibleById(@Param("id") Long id);

  @Query("""
      select q from QuestionWiki q
      where q.visible = true and q.passageGroupKey = :key
      order by q.questionNumber asc
      """)
  List<QuestionWiki> findVisibleByPassageGroupKey(@Param("key") String key);
}
