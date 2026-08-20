package f2r.tutor_solution.private_tutor_solution_back.domain.community.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.community.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

  /** 상세: 질문의 숨김 아닌 답변 + 작성자 fetch(N+1 방지). */
  @Query("""
      select a from Answer a
      join fetch a.author au
      where a.question.postId = :questionId and a.visible = true
      order by a.createdAt asc, a.answerId asc
      """)
  List<Answer> findVisibleByQuestionId(@Param("questionId") Long questionId);

  /** 목록 카드 답변수(배치) — [questionId, count]. */
  @Query("""
      select a.question.postId, count(a.answerId)
      from Answer a
      where a.question.postId in :questionIds and a.visible = true
      group by a.question.postId
      """)
  List<Object[]> countVisibleByQuestionIds(@Param("questionIds") Collection<Long> questionIds);
}
