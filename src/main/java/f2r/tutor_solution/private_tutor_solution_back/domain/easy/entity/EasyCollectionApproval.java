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
 * 수집 자료 장부 반영 승인 — P1/P3.
 *
 * <p>수집된 자료(run)가 장부에 반영되기 전 사용자 승인 절차를 관리한다.
 * 승인 전에는 장부에 반영되지 않으며, 승인·반려·반영 이력은 감사 로그에 기록된다.
 * 승인 재시도 시 중복 반영을 방지하기 위해 멱등성을 보장한다.
 *
 * <p>상태 전이 (P3):
 * <pre>
 * PENDING → APPROVED → COMMITTED
 *                 ↘ COMMIT_FAILED → (재시도) → COMMITTED
 * PENDING → REJECTED (종료)
 * </pre>
 * APPROVED는 "승인됨"을, COMMITTED는 "장부 반영 완료"를 의미한다.
 * 승인만 되고 반영이 실패하면 COMMIT_FAILED로 남으며 정상 완료로 표시하지 않는다.
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

  /** 장부 반영 완료 시각 (COMMITTED일 때만 설정). */
  @Column(name = "committed_at")
  private LocalDateTime committedAt;

  /** 장부 반영 실패 사유 (COMMIT_FAILED일 때만 설정, 재시도 가능). */
  @Column(name = "commit_error", length = 1000)
  private String commitError;

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

  /**
   * 장부 반영 완료 표시 — APPROVED 또는 COMMIT_FAILED 상태에서만 가능하다.
   * COMMITTED는 최종 상태이며 재반영하지 않는다 (멱등성).
   */
  public void markCommitted() {
    if (this.status == ApprovalStatus.COMMITTED) {
      return; // 이미 반영됨 — 멱등성 보장
    }
    if (this.status != ApprovalStatus.APPROVED && this.status != ApprovalStatus.COMMIT_FAILED) {
      throw new IllegalStateException("반영할 수 없는 상태입니다: " + this.status);
    }
    this.status = ApprovalStatus.COMMITTED;
    this.committedAt = LocalDateTime.now();
    this.commitError = null;
  }

  /**
   * 장부 반영 실패 기록 — 재시도 가능한 상태로 남긴다.
   * 실패를 정상 완료로 표시하지 않는다.
   */
  public void markCommitFailed(String error) {
    if (this.status == ApprovalStatus.COMMITTED) {
      throw new IllegalStateException("이미 반영 완료된 건입니다.");
    }
    if (this.status != ApprovalStatus.APPROVED && this.status != ApprovalStatus.COMMIT_FAILED) {
      throw new IllegalStateException("반영 실패를 기록할 수 없는 상태입니다: " + this.status);
    }
    this.status = ApprovalStatus.COMMIT_FAILED;
    this.commitError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
  }

  public boolean isCommitted() {
    return this.status == ApprovalStatus.COMMITTED;
  }

  public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMMITTED,
    COMMIT_FAILED
  }
}
