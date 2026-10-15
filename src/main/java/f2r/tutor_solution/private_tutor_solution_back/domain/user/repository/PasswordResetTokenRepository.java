package f2r.tutor_solution.private_tutor_solution_back.domain.user.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.PasswordResetToken;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update PasswordResetToken token
         set token.usedAt = :usedAt
       where token.user = :user
         and token.usedAt is null
      """)
  int markUnusedTokensUsed(@Param("user") User user, @Param("usedAt") LocalDateTime usedAt);
}
