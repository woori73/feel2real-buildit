package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.entity.NewStartAdminPasswordReset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NewStartAdminPasswordResetRepository extends JpaRepository<NewStartAdminPasswordReset, Long> {

  /** 최근 실행 내역(ADMIN 화면의 "마지막 초기화" 표시용). */
  List<NewStartAdminPasswordReset> findByTargetUserIdOrderByResetIdDesc(Long targetUserId, Pageable pageable);

  /** 특정 회원의 최근 초기화 시각. 없으면 null — 과거 이력을 만들어 표시하지 않는다. */
  @Query("""
      select max(r.createdAt) from NewStartAdminPasswordReset r where r.targetUserId = :targetUserId
      """)
  LocalDateTime findLastResetAt(@Param("targetUserId") Long targetUserId);
}
