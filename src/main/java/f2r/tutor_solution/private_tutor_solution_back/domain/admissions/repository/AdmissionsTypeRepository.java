package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AdmissionsTypeRepository extends JpaRepository<AdmissionsType, Long>,
        JpaSpecificationExecutor<AdmissionsType> {
    List<AdmissionsType> findByUniversity_UniversityIdAndAcademicYear(Long universityId, Integer academicYear);
}
