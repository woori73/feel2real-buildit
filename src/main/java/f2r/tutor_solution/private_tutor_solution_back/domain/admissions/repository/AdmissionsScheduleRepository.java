package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AdmissionsScheduleRepository extends JpaRepository<AdmissionsSchedule, Long>,
        JpaSpecificationExecutor<AdmissionsSchedule> {
    List<AdmissionsSchedule> findByAcademicYear(Integer academicYear);
}
