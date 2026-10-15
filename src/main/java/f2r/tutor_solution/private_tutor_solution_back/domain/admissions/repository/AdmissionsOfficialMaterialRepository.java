package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsOfficialMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AdmissionsOfficialMaterialRepository extends JpaRepository<AdmissionsOfficialMaterial, Long>,
        JpaSpecificationExecutor<AdmissionsOfficialMaterial> {
    List<AdmissionsOfficialMaterial> findByAcademicYear(Integer academicYear);
}
