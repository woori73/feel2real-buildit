package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 감사회원 셀프 인증 시도 기록 — {@code newstart_thanks_verifications}.
 *
 * <p>outcome:
 * <ul>
 *   <li>{@code AUTO_APPROVED}: 자동 인증 성공(운영자에게 알리지 않는다). status=DONE.</li>
 *   <li>{@code FAILED}: 닉네임/이메일 불일치 등 단순 실패. status=DONE. 반복되면 예외로 승격한다.</li>
 *   <li>{@code EXCEPTION}: 자동으로 안전하게 끝낼 수 없는 경우. status=OPEN → 운영자 처리(RESOLVED/DISMISSED).</li>
 * </ul>
 */
@Entity
@Table(name = "newstart_thanks_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NewStartThanksVerification extends BaseEntity {

  public static final String AUTO_APPROVED = "AUTO_APPROVED";
  public static final String FAILED = "FAILED";
  public static final String EXCEPTION = "EXCEPTION";

  public static final String DONE = "DONE";
  public static final String OPEN = "OPEN";
  public static final String RESOLVED = "RESOLVED";
  public static final String DISMISSED = "DISMISSED";

  public static final String EMAIL_SENT = "SENT";
  public static final String EMAIL_FAILED = "FAILED";
  public static final String EMAIL_NOT_CONFIGURED = "NOT_CONFIGURED";
  public static final String EMAIL_SKIPPED = "SKIPPED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "verification_id")
  private Long verificationId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "roster_id")
  private Long rosterId;

  @Column(name = "nickname_input", nullable = false, length = 50)
  private String nicknameInput;

  @Column(name = "provider_user_id", nullable = false, length = 64)
  private String providerUserId;

  @Column(name = "outcome", nullable = false, length = 16)
  private String outcome;

  @Column(name = "exception_reason", length = 32)
  private String exceptionReason;

  @Column(name = "status", nullable = false, length = 12)
  private String status;

  @Column(name = "email_status", length = 16)
  private String emailStatus;

  @Column(name = "email_sent_at")
  private LocalDateTime emailSentAt;

  @Column(name = "resolved_by", length = 50)
  private String resolvedBy;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Column(name = "resolution_note", length = 200)
  private String resolutionNote;

  @Builder
  public NewStartThanksVerification(Long userId, Long rosterId, String nicknameInput, String providerUserId,
      String outcome, String exceptionReason) {
    this.userId = userId;
    this.rosterId = rosterId;
    this.nicknameInput = nicknameInput;
    this.providerUserId = providerUserId;
    this.outcome = outcome;
    this.exceptionReason = exceptionReason;
    this.status = EXCEPTION.equals(outcome) ? OPEN : DONE;
  }

  public boolean isOpenException() {
    return EXCEPTION.equals(outcome) && OPEN.equals(status);
  }

  public void markEmail(String emailStatus) {
    this.emailStatus = emailStatus;
    if (EMAIL_SENT.equals(emailStatus)) this.emailSentAt = LocalDateTime.now();
  }

  public void resolve(String by, String note) {
    this.status = RESOLVED;
    stamp(by, note);
  }

  public void dismiss(String by, String note) {
    this.status = DISMISSED;
    stamp(by, note);
  }

  private void stamp(String by, String note) {
    this.resolvedBy = by;
    this.resolvedAt = LocalDateTime.now();
    this.resolutionNote = note == null || note.isBlank() ? null : (note.length() > 200 ? note.substring(0, 200) : note);
  }
}
