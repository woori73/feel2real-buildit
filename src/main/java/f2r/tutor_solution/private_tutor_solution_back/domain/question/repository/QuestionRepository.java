package f2r.tutor_solution.private_tutor_solution_back.domain.question.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

  /** dedup 매치된 exam의 기존 questions 재사용용 (MVP §4 핵심 설계 원칙 5). */
  List<Question> findByExamIdOrderByOrderNumAsc(Long examId);
}
