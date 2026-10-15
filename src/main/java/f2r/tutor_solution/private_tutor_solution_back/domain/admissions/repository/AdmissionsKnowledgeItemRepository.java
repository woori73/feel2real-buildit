package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsKnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AdmissionsKnowledgeItemRepository extends JpaRepository<AdmissionsKnowledgeItem, Long>,
        JpaSpecificationExecutor<AdmissionsKnowledgeItem> {
    Optional<AdmissionsKnowledgeItem> findByDedupKey(String dedupKey);
}
