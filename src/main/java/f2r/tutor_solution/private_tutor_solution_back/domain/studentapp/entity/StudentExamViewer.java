package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원장이 지정한, 응시 기록을 볼 수 있는 사람(V1126 student_exam_viewers). 해제하면 revokedAt만 채운다. */
@Entity
@Table(name = "student_exam_viewers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentExamViewer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "viewer_id")
  private Long viewerId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "granted_by")
  private Long grantedBy;

  @Column(name = "granted_at", nullable = false)
  private LocalDateTime grantedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  public StudentExamViewer(Long businessId, Long userId, Long grantedBy, LocalDateTime now) {
    this.businessId = businessId;
    this.userId = userId;
    this.grantedBy = grantedBy;
    this.grantedAt = now;
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public void revoke(LocalDateTime now) {
    if (revokedAt == null) revokedAt = now;
  }

  public void restore(Long by, LocalDateTime now) {
    this.revokedAt = null;
    this.grantedBy = by;
    this.grantedAt = now;
  }
}
