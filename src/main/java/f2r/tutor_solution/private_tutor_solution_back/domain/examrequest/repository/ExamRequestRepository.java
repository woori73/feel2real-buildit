package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.entity.ExamRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRequestRepository extends JpaRepository<ExamRequest, Long> {

  List<ExamRequest> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

  List<ExamRequest> findTop200ByOrderByCreatedAtDesc();
}
