package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 감사회원 명단 선점(1:1). 미연결(linked_user_id IS NULL) 행만 조건부 UPDATE 하므로 두 가입이 같은 명단을 동시에 가져갈 수 없다
 * (영향 행 1건일 때만 성공). 명단 원본 값(닉네임·접두부·등급 등)은 수정하지 않는다.
 */
public interface NewStartThanksRosterClaimRepository extends JpaRepository<NewStartThanksRoster, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update NewStartThanksRoster r set r.linkedUserId = :userId, r.matchStatus = 'MATCHED' "
      + "where r.rosterId = :rosterId and r.linkedUserId is null")
  int claim(@Param("rosterId") Long rosterId, @Param("userId") Long userId);
}
