package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByLoginId(String loginId);

  Optional<User> findByLoginIdAndEmail(String loginId, String email);

  List<User> findAllByEmail(String email);

  boolean existsByLoginId(String loginId);

  /** Wiki 운영 현황 — 특정 시각 이후 가입 수(오늘 신규 가입). */
  long countByCreatedAtGreaterThanEqual(LocalDateTime from);

  /** Wiki 운영 현황 — 최근 가입 회원(가입일 내림차순). Pageable 로 개수 제한. */
  List<User> findByOrderByCreatedAtDesc(Pageable pageable);

  /** 공개 Wiki 메인 "새로 함께한 회원" — 특정 역할(ADMIN) 제외 최근 가입 회원. */
  List<User> findByRoleNotOrderByCreatedAtDesc(Role role, Pageable pageable);

  /**
   * ADMIN 회원 검색 — 로그인 ID 또는 카페 닉네임 부분 일치(대소문자 무시).
   * NewStart 가입 계정은 {@code Role.STUDENT}로 만들어지므로 역할로 범위를 좁힌다
   * (별도 NewStart 회원 플래그가 없다 — docs/newstart 참고).
   */
  @Query("""
      select u from User u
      where u.role = :role
        and (lower(u.loginId) like concat('%', lower(:query), '%')
          or lower(u.nickname) like concat('%', lower(:query), '%'))
      """)
  Page<User> searchByRoleAndLoginIdOrNickname(@Param("role") Role role,
                                              @Param("query") String query,
                                              Pageable pageable);

  /** 검색어 없이 목록만 열 때의 기본 범위(같은 역할 범위 보정). */
  Page<User> findByRole(Role role, Pageable pageable);

  /** ADMIN 초기화 요구 상태(강제 비밀번호 변경 게이트가 매 요청 읽는 단일 판정). */
  boolean existsByUserIdAndPasswordResetRequired(Long userId, boolean passwordResetRequired);
}
