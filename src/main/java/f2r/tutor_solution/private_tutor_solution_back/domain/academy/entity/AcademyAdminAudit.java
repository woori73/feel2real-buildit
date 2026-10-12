package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

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
import org.hibernate.annotations.CreationTimestamp;

/** 관리자 홈페이지 관리 감사 기록. 추가만 하고 수정·삭제하지 않는다. 회원에게는 노출하지 않는다. */
@Entity
@Table(name = "academy_admin_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAdminAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_id")
  private Long auditId;

  @Column(name = "actor_user_id", nullable = false)
  private Long actorUserId;

  @Column(name = "actor_login_id", nullable = false, length = 100)
  private String actorLoginId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "owner_user_id")
  private Long ownerUserId;

  /** ACCESS, SAVE, PUBLISH, RESTORE, DISCARD. */
  @Column(name = "action", nullable = false, length = 30)
  private String action;

  @Column(name = "working_copy_revision")
  private Long workingCopyRevision;

  @Column(name = "published", nullable = false)
  private boolean published;

  @Column(name = "summary", length = 500)
  private String summary;

  @Column(name = "details_json", columnDefinition = "MEDIUMTEXT")
  private String detailsJson;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public AcademyAdminAudit(
      Long actorUserId, String actorLoginId, Long academyId, Long ownerUserId, String action,
      Long workingCopyRevision, boolean published, String summary, String detailsJson) {
    this.actorUserId = actorUserId;
    this.actorLoginId = actorLoginId;
    this.academyId = academyId;
    this.ownerUserId = ownerUserId;
    this.action = action;
    this.workingCopyRevision = workingCopyRevision;
    this.published = published;
    this.summary = summary;
    this.detailsJson = detailsJson;
  }
}
