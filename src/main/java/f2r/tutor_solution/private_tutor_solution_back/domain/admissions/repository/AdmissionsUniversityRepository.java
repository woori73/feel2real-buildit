package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsUniversity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AdmissionsUniversityRepository extends JpaRepository<AdmissionsUniversity, Long>,
        JpaSpecificationExecutor<AdmissionsUniversity> {
    Optional<AdmissionsUniversity> findByName(String name);
}
