package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Agent 토큰 Repository. 학원 조건 없는 조회는 인증(해시 조회)에만 쓴다. */
public interface AgentTokenRepository extends JpaRepository<AgentToken, Long> {

  Optional<AgentToken> findByTokenHash(String tokenHash);

  List<AgentToken> findByAcademyIdOrderByCreatedAtDesc(Long academyId);

  Optional<AgentToken> findByTokenIdAndAcademyId(Long tokenId, Long academyId);

  long countByAcademyIdAndRevokedAtIsNullAndExpiresAtAfter(Long academyId, LocalDateTime now);
}
