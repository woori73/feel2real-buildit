package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface ParentNoticeDraftRepository extends JpaRepository<ParentNoticeDraft, String> {
  List<ParentNoticeDraft> findTop30ByOwnerIdAndOwnerRoleOrderByCreatedAtDescIdDesc(
      Long ownerId, String ownerRole);

  // The actor check can establish a repeatable-read snapshot before a concurrent writer commits.
  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<ParentNoticeDraft> findByIdAndOwnerIdAndOwnerRole(
      String id, Long ownerId, String ownerRole);
}
