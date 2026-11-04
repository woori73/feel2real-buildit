package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsJeongsiFormula;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsJeongsiFormulaRepository extends JpaRepository<AdmissionsJeongsiFormula, Long> {

    /** 적재 중복 판정용: 이번 시드에 나온 대학의 계산식만 읽는다. */
    List<AdmissionsJeongsiFormula> findByUniversityUniversityIdIn(Collection<Long> universityIds);

    /** 계산용: 한 대학·학년도의 승인된 계산식. */
    List<AdmissionsJeongsiFormula> findByUniversityUniversityIdAndAcademicYearAndStatus(
            Long universityId, Integer academicYear, KnowledgeStatus status);

    List<AdmissionsJeongsiFormula> findByAcademicYearAndStatusIn(Integer academicYear, Collection<KnowledgeStatus> statuses);
}
