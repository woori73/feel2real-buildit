package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집 자료 장부 반영 승인 — P1.
 *
 * <p>수집된 자료(run)가 장부에 반영되기 전 사용자 승인 절차를 관리한다.
 * 승인 전에는 장부에 반영되지 않으며, 승인·반려는 감사 로그에 기록된다.
 * 승인 재시도 시 중복 반영을 방지하기 위해 멱등성을 보장한다.
 */
@Entity
@Table(name = "easy_collection_approvals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyCollectionApproval extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "approval_id")
  private Long approvalId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_id", nullable = false, unique = true)
  private EasyCollectionRun run;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "business_id", nullable = false)
  private EasyBusiness business;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ApprovalStatus status;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "requested_by", length = 100)
  private String requestedBy;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "decided_by", length = 100)
  private String decidedBy;

  @Column(name = "decision_note", length = 500)
  private String decisionNote;

  /** 멱등성 키 — 중복 승인 요청 방지. */
  @Column(name = "idempotency_key", length = 100, unique = true)
  private String idempotencyKey;

  @Builder
  public EasyCollectionApproval(EasyCollectionRun run, EasyBusiness business,
      String requestedBy, String idempotencyKey) {
    this.run = run;
    this.business = business;
    this.status = ApprovalStatus.PENDING;
    this.requestedAt = LocalDateTime.now();
    this.requestedBy = requestedBy;
    this.idempotencyKey = idempotencyKey;
  }

  public void approve(String decidedBy, String note) {
    if (this.status != ApprovalStatus.PENDING) {
      throw new IllegalStateException("이미 처리된 승인 요청입니다: " + this.status);
    }
    this.status = ApprovalStatus.APPROVED;
    this.decidedAt = LocalDateTime.now();
    this.decidedBy = decidedBy;
    this.decisionNote = note;
  }

  public void reject(String decidedBy, String note) {
    if (this.status != ApprovalStatus.PENDING) {
      throw new IllegalStateException("이미 처리된 승인 요청입니다: " + this.status);
    }
    this.status = ApprovalStatus.REJECTED;
    this.decidedAt = LocalDateTime.now();
    this.decidedBy = decidedBy;
    this.decisionNote = note;
  }

  public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
  }
}
