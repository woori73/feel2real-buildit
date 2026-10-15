package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AdmissionsConflictRepository extends JpaRepository<AdmissionsConflict, Long>,
        JpaSpecificationExecutor<AdmissionsConflict> {
    List<AdmissionsConflict> findByStatus(AdmissionsConflict.ConflictStatus status);
}
