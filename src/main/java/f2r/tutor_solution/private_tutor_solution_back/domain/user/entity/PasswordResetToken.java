package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "password_reset_token_id")
  private Long passwordResetTokenId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  public PasswordResetToken(User user, String tokenHash, LocalDateTime expiresAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(LocalDateTime now) {
    return !expiresAt.isAfter(now);
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public void markUsed(LocalDateTime usedAt) {
    this.usedAt = usedAt;
  }
}
