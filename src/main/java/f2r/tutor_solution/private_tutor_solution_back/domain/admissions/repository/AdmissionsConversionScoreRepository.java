package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsConversionScore;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsConversionScoreRepository extends JpaRepository<AdmissionsConversionScore, Long> {

    List<AdmissionsConversionScore> findByUniversityUniversityIdIn(Collection<Long> universityIds);

    List<AdmissionsConversionScore> findByUniversityUniversityIdAndAcademicYearAndStatus(
            Long universityId, Integer academicYear, KnowledgeStatus status);

    List<AdmissionsConversionScore> findByAcademicYearAndStatusIn(Integer academicYear, Collection<KnowledgeStatus> statuses);
}
