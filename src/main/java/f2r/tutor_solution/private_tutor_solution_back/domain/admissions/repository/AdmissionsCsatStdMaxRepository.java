package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCsatStdMax;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsCsatStdMaxRepository extends JpaRepository<AdmissionsCsatStdMax, Long> {

    List<AdmissionsCsatStdMax> findByAcademicYear(Integer academicYear);

    List<AdmissionsCsatStdMax> findByAcademicYearAndStatus(Integer academicYear, KnowledgeStatus status);

    List<AdmissionsCsatStdMax> findByAcademicYearAndStatusIn(Integer academicYear, Collection<KnowledgeStatus> statuses);
}
