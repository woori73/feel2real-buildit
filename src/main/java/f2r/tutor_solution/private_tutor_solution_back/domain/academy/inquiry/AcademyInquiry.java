package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 학원 공개 홈페이지로 들어온 상담신청 1건. 개인정보를 담으므로 로그·예외 메시지에 값을 싣지 않는다. */
@Entity
@Table(name = "academy_inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyInquiry extends BaseEntity {

  /** 개인정보 수집·이용 동의 문구 버전. 문구가 바뀌면 올린다. */
  public static final String CONSENT_VERSION = "2026-10-v1";
  /** 보관 기간(일). 접수일로부터 1년. */
  public static final int RETENTION_DAYS = 365;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "inquiry_id")
  private Long inquiryId;

  @Column(name = "academy_id", nullable = false, updatable = false)
  private Long academyId;

  @Column(name = "name", length = 50)
  private String name;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "message", length = 1000)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private InquiryStatus status = InquiryStatus.NEW;

  @Column(name = "consent_version", nullable = false, length = 20, updatable = false)
  private String consentVersion;

  @Column(name = "consented_at", nullable = false, updatable = false)
  private LocalDateTime consentedAt;

  @Column(name = "retention_until", nullable = false, updatable = false)
  private LocalDate retentionUntil;

  @Column(name = "anonymized_at")
  private LocalDateTime anonymizedAt;

  @Column(name = "handled_by_user_id")
  private Long handledByUserId;

  @Column(name = "handled_at")
  private LocalDateTime handledAt;

  public static AcademyInquiry receive(
      Long academyId, String name, String phone, String message, LocalDateTime now) {
    AcademyInquiry i = new AcademyInquiry();
    i.academyId = academyId;
    i.name = name;
    i.phone = phone;
    i.message = message;
    i.consentVersion = CONSENT_VERSION;
    i.consentedAt = now;
    i.retentionUntil = now.toLocalDate().plusDays(RETENTION_DAYS);
    return i;
  }

  public void changeStatus(InquiryStatus next, Long userId, LocalDateTime now) {
    this.status = next;
    this.handledByUserId = userId;
    this.handledAt = now;
  }
}
