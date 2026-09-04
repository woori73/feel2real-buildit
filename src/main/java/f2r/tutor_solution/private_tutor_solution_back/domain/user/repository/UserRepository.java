package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByLoginId(String loginId);

  Optional<User> findByLoginIdAndEmail(String loginId, String email);

  List<User> findAllByEmail(String email);

  boolean existsByLoginId(String loginId);

  /** Wiki 운영 현황 — 특정 시각 이후 가입 수(오늘 신규 가입). */
  long countByCreatedAtGreaterThanEqual(LocalDateTime from);

  /** Wiki 운영 현황 — 최근 가입 회원(가입일 내림차순). Pageable 로 개수 제한. */
  List<User> findByOrderByCreatedAtDesc(Pageable pageable);
}
