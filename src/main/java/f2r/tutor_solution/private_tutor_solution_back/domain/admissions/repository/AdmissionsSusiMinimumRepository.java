package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsSusiMinimum;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsSusiMinimumRepository extends JpaRepository<AdmissionsSusiMinimum, Long> {

    List<AdmissionsSusiMinimum> findByUniversityUniversityIdIn(Collection<Long> universityIds);

    List<AdmissionsSusiMinimum> findByUniversityUniversityIdAndAcademicYearAndStatus(
            Long universityId, Integer academicYear, KnowledgeStatus status);

    List<AdmissionsSusiMinimum> findByAcademicYearAndStatusIn(Integer academicYear, Collection<KnowledgeStatus> statuses);
}
