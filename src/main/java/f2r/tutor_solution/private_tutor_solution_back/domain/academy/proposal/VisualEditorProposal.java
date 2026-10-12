package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 시각 편집기 AI 제안. 초안·게시본과 분리된 전용 테이블.
 *
 * <p>모든 조회는 academyId를 조건에 포함한다(Repository 참고). 상태 전이는 PENDING에서만 시작한다.
 */
@Entity
@Table(name = "academy_visual_editor_proposal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisualEditorProposal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "proposal_id")
  private Long proposalId;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProposalStatus status;

  @Column(name = "base_revision")
  private Long baseRevision;

  @Column(name = "summary", nullable = false, length = 500)
  private String summary;

  /** 정제·검증을 마친 changes[] JSON. */
  @Column(name = "changes_json", nullable = false, columnDefinition = "MEDIUMTEXT")
  private String changesJson;

  /** AGENT(외부 Agent 토큰) 또는 AI(편집기 안의 AI 수정 요청). */
  @Column(name = "created_by_kind", nullable = false, length = 20)
  private String createdByKind;

  @Column(name = "created_by_ref", length = 100)
  private String createdByRef;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "applied_revision")
  private Long appliedRevision;

  @Column(name = "applied_by")
  private Long appliedBy;

  @Column(name = "applied_at")
  private LocalDateTime appliedAt;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "stale_reason", length = 200)
  private String staleReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public VisualEditorProposal(
      Long academyId, Long baseRevision, String summary, String changesJson,
      String createdByKind, String createdByRef, LocalDateTime expiresAt) {
    this.academyId = academyId;
    this.baseRevision = baseRevision;
    this.summary = summary;
    this.changesJson = changesJson;
    this.createdByKind = createdByKind;
    this.createdByRef = createdByRef;
    this.expiresAt = expiresAt;
    this.status = ProposalStatus.PENDING;
  }

  public void markApplied(long revision, long userId, LocalDateTime now) {
    requirePending();
    this.status = ProposalStatus.APPLIED;
    this.appliedRevision = revision;
    this.appliedBy = userId;
    this.appliedAt = now;
    this.decidedAt = now;
  }

  public void markRejected(LocalDateTime now) {
    requirePending();
    this.status = ProposalStatus.REJECTED;
    this.decidedAt = now;
  }

  public void markExpired(LocalDateTime now) {
    requirePending();
    this.status = ProposalStatus.EXPIRED;
    this.decidedAt = now;
  }

  public void markStale(String reason, LocalDateTime now) {
    requirePending();
    this.status = ProposalStatus.STALE;
    this.staleReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 200));
    this.decidedAt = now;
  }

  private void requirePending() {
    if (status != ProposalStatus.PENDING) {
      throw new IllegalStateException("PENDING 상태에서만 전이할 수 있습니다: " + status);
    }
  }
}
