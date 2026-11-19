package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI가 MCP로 만든 업무 초안 1건. 사람이 승인해야 서버가 기존 서비스로 실행한다. */
@Entity
@Table(name = "coord_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoordJob {

  public static final String REVIEW_REQUIRED = "review_required";
  public static final String APPROVED = "approved";
  public static final String COMPLETED = "completed";
  public static final String REJECTED = "rejected";
  public static final String EXPIRED = "expired";
  public static final String FAILED = "failed";

  @Id
  @Column(name = "job_id", length = 36)
  private String jobId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "request_id", nullable = false, length = 64)
  private String requestId;

  @Column(name = "kind", nullable = false, length = 30)
  private String kind;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "client_name", length = 100)
  private String clientName;

  @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT")
  private String planJson;

  @Column(name = "plan_hash", nullable = false, length = 64)
  private String planHash;

  @Column(name = "result_json", columnDefinition = "TEXT")
  private String resultJson;

  @Column(name = "error", length = 500)
  private String error;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  CoordJob(String jobId, Long userId, Long businessId, String requestId, String kind, String source,
      String clientName, String planJson, String planHash, LocalDateTime now, LocalDateTime expiresAt) {
    this.jobId = jobId;
    this.userId = userId;
    this.businessId = businessId;
    this.requestId = requestId;
    this.kind = kind;
    this.status = REVIEW_REQUIRED;
    this.source = source;
    this.clientName = clientName;
    this.planJson = planJson;
    this.planHash = planHash;
    this.createdAt = now;
    this.updatedAt = now;
    this.expiresAt = expiresAt;
  }

  /** 승인 대기인데 유효기간이 지났으면 true. */
  boolean isExpired(LocalDateTime now) {
    return REVIEW_REQUIRED.equals(status) && !now.isBefore(expiresAt);
  }

  void moveTo(String next, LocalDateTime now) {
    this.status = next;
    this.updatedAt = now;
  }

  void complete(String resultJson, LocalDateTime now) {
    this.resultJson = resultJson;
    moveTo(COMPLETED, now);
  }

  void fail(String error, LocalDateTime now) {
    this.error = error == null ? null : error.length() > 500 ? error.substring(0, 500) : error;
    moveTo(FAILED, now);
  }
}
