package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAiIdempotency;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** BuildIt AI 생성 멱등성 키 Repository (P1). */
public interface AcademyAiIdempotencyRepository
    extends JpaRepository<AcademyAiIdempotency, Long> {

  Optional<AcademyAiIdempotency> findByTutorIdAndIdempotencyKey(
      String tutorId, String idempotencyKey);
}
