package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsPriorResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionsPriorResultRepository extends JpaRepository<AdmissionsPriorResult, Long> {

    List<AdmissionsPriorResult> findByUniversityUniversityIdInAndStatus(Collection<Long> universityIds, KnowledgeStatus status);
}
