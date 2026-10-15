package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.entity.TpExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TpExamRepository extends JpaRepository<TpExam, Long> {

  List<TpExam> findByKindAndStatusOrderByExamIdAsc(String kind, String status);

  Optional<TpExam> findByExamCodeAndStatus(String examCode, String status);

  Optional<TpExam> findByExamIdAndStatus(Long examId, String status);
}
