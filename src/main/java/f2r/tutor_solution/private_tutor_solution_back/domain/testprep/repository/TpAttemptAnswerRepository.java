package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TpAttemptAnswerRepository extends JpaRepository<TpAttemptAnswer, Long> {

  List<TpAttemptAnswer> findByAttemptId(Long attemptId);

  Optional<TpAttemptAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

  @Query("""
      select a from TpAttemptAnswer a
      join TpAttempt t on t.attemptId = a.attemptId
      where t.userId = :userId and a.isCorrect = false
      order by a.answeredAt desc
      """)
  List<TpAttemptAnswer> findWrongAnswers(@Param("userId") Long userId);
}
