package f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAdminAudit;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyAdminAuditRepository extends JpaRepository<AcademyAdminAudit, Long> {

  /** 최근 기록부터. 학원별 조회 전용(ADMIN API에서만 쓴다). */
  List<AcademyAdminAudit> findByAcademyIdOrderByAuditIdDesc(Long academyId, Pageable pageable);
}
