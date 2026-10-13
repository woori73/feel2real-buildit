package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 제안 Repository. <b>모든 메서드가 academyId를 요구한다</b> — ID만으로 조회하는 메서드는 두지 않는다
 * (다른 학원 제안 접근 차단). 아키텍처 테스트가 findById 사용을 막는다.
 */
public interface VisualEditorProposalRepository extends JpaRepository<VisualEditorProposal, Long> {

  Optional<VisualEditorProposal> findByProposalIdAndAcademyId(Long proposalId, Long academyId);

  /** 저장 트랜잭션에서 사용하는 행 잠금 조회(SELECT … FOR UPDATE). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from VisualEditorProposal p "
      + "where p.proposalId = :proposalId and p.academyId = :academyId")
  Optional<VisualEditorProposal> lockByProposalIdAndAcademyId(
      @Param("proposalId") Long proposalId, @Param("academyId") Long academyId);

  List<VisualEditorProposal> findByAcademyIdAndStatusInOrderByCreatedAtDesc(
      Long academyId, List<ProposalStatus> statuses);

  long countByAcademyIdAndStatus(Long academyId, ProposalStatus status);

  List<VisualEditorProposal> findByAcademyIdAndStatusAndExpiresAtBefore(
      Long academyId, ProposalStatus status, LocalDateTime now);
}
