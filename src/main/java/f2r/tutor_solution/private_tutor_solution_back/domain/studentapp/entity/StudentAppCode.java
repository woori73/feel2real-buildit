package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재원생 앱 접속 코드(V1126 student_app_codes). 코드 원문은 저장하지 않고 SHA-256만 둔다. */
@Entity
@Table(name = "student_app_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAppCode {

  public static final String ACTIVE = "ACTIVE";
  public static final String REVOKED = "REVOKED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "code_id")
  private Long codeId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "code_tail", nullable = false, length = 4)
  private String codeTail;

  @Column(name = "status", nullable = false, length = 10)
  private String status;

  @Column(name = "issued_by")
  private Long issuedBy;

  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  public StudentAppCode(Long businessId, Long studentId, String codeHash, String codeTail, Long issuedBy, LocalDateTime now) {
    this.businessId = businessId;
    this.studentId = studentId;
    this.codeHash = codeHash;
    this.codeTail = codeTail;
    this.issuedBy = issuedBy;
    this.issuedAt = now;
    this.status = ACTIVE;
  }

  public boolean isActive() {
    return ACTIVE.equals(status);
  }

  public void revoke(LocalDateTime now) {
    if (!isActive()) return;
    this.status = REVOKED;
    this.revokedAt = now;
  }

  public void touch(LocalDateTime now) {
    this.lastUsedAt = now;
  }
}
