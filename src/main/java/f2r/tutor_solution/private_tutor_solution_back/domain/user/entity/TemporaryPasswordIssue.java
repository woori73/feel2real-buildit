package f2r.tutor_solution.private_tutor_solution_back.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 임시 비밀번호 발급 기록(누가·언제·어느 회원에게). 비밀번호는 담지 않는다. */
@Entity
@Table(name = "temporary_password_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemporaryPasswordIssue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "issue_id")
  private Long issueId;

  @Column(name = "target_user_id", nullable = false)
  private Long targetUserId;

  @Column(name = "target_login_id", nullable = false, length = 50)
  private String targetLoginId;

  @Column(name = "issued_by_user_id", nullable = false)
  private Long issuedByUserId;

  @Column(name = "issued_by_login_id", nullable = false, length = 50)
  private String issuedByLoginId;

  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  public TemporaryPasswordIssue(Long targetUserId, String targetLoginId, Long issuedByUserId, String issuedByLoginId,
      LocalDateTime issuedAt, LocalDateTime expiresAt) {
    this.targetUserId = targetUserId;
    this.targetLoginId = targetLoginId;
    this.issuedByUserId = issuedByUserId;
    this.issuedByLoginId = issuedByLoginId;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }
}
