package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 감사회원 최초 1회 본인확인 요청 — {@code newstart_thanks_claims}.
 *
 * <p>요청은 항상 {@code PENDING}으로만 생기고, {@code THANKS_MEMBER} 부여는 ADMIN 승인 경로 한 곳에서만 일어난다.
 * 닉네임·네이버 ID 접두부·이메일 접두부 일치는 {@code emailPrefixMatch} 같은 보조 증거일 뿐 자동 부여 근거가 아니다 —
 * 같은 접두부·공개 닉네임을 가진 일반회원의 오인증을 막기 위함이다.
 */
@Entity
@Table(name = "newstart_thanks_claims",
    uniqueConstraints = @UniqueConstraint(name = "uk_newstart_thanks_claim_user_roster",
        columnNames = {"user_id", "roster_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartThanksClaim extends BaseEntity {

  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String CANCELLED = "CANCELLED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "claim_id")
  private Long claimId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "roster_id", nullable = false)
  private Long rosterId;

  /** 요청 시점 네이버 OAuth subject 스냅샷. 승인 시 현재 social_accounts 값과 같아야 한다. */
  @Column(name = "provider_user_id", nullable = false, length = 64)
  private String providerUserId;

  @Column(name = "verify_code", nullable = false, length = 16)
  private String verifyCode;

  @Column(name = "status", nullable = false, length = 12)
  private String status;

  @Column(name = "email_prefix_match", nullable = false)
  private boolean emailPrefixMatch;

  @Column(name = "decided_by", length = 50)
  private String decidedBy;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "reject_reason", length = 200)
  private String rejectReason;

  @Builder
  public NewStartThanksClaim(Long userId, Long rosterId, String providerUserId, String verifyCode,
      boolean emailPrefixMatch) {
    this.userId = userId;
    this.rosterId = rosterId;
    this.providerUserId = providerUserId;
    this.verifyCode = verifyCode;
    this.emailPrefixMatch = emailPrefixMatch;
    this.status = PENDING;
  }

  public boolean isPending() {
    return PENDING.equals(status);
  }

  public void approve(String decidedBy) {
    this.status = APPROVED;
    this.decidedBy = decidedBy;
    this.decidedAt = LocalDateTime.now();
  }

  public void reject(String decidedBy, String reason) {
    this.status = REJECTED;
    this.decidedBy = decidedBy;
    this.decidedAt = LocalDateTime.now();
    this.rejectReason = reason == null || reason.isBlank() ? null
        : (reason.length() > 200 ? reason.substring(0, 200) : reason);
  }

  public void cancel() {
    this.status = CANCELLED;
    this.decidedAt = LocalDateTime.now();
  }

  /** 취소/반려된 요청을 같은 roster로 다시 신청할 때 새 코드로 재사용한다(UNIQUE(user, roster) 때문에 row 1개를 되살린다). */
  public void reopen(String providerUserId, String verifyCode, boolean emailPrefixMatch) {
    this.providerUserId = providerUserId;
    this.verifyCode = verifyCode;
    this.emailPrefixMatch = emailPrefixMatch;
    this.status = PENDING;
    this.decidedBy = null;
    this.decidedAt = null;
    this.rejectReason = null;
  }
}
