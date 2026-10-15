package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AdmissionsDepartmentRepository extends JpaRepository<AdmissionsDepartment, Long>,
        JpaSpecificationExecutor<AdmissionsDepartment> {
    List<AdmissionsDepartment> findByUniversity_UniversityIdAndAcademicYear(Long universityId, Integer academicYear);
}
