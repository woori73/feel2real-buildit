package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademyHandleHistoryRepository extends JpaRepository<AcademyHandleHistory, Long> {
  Optional<AcademyHandleHistory> findFirstByAcademy_AcademyIdAndValidToUtcIsNullOrderByValidFromUtcDesc(
      Long academyId);

  List<AcademyHandleHistory> findAllByOrderByHandleAscValidFromUtcAsc();
}
