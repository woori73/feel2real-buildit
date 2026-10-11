package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BuildIt AI 생성 멱등성 키 (P1).
 *
 * <p>{@code POST /api/tutor/academy/ai/chat/create/apply}의 중복 적용을 방지한다.
 * {@code (tutorId, idempotencyKey)} UNIQUE 제약으로 동일 키 재호출을 DB 레벨에서 차단한다.
 */
@Entity
@Table(
    name = "academy_ai_idempotency",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_ai_idem_tutor_key",
          columnNames = {"tutorId", "idempotencyKey"})
    },
    indexes = {@Index(name = "idx_ai_idem_academy", columnList = "academyId")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademyAiIdempotency {

  /** 처리 상태. */
  public static final String STATUS_PROCESSING = "PROCESSING";

  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_FAILED = "FAILED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String tutorId;

  @Column(nullable = false, length = 100)
  private String idempotencyKey;

  /** 생성된 Academy ID (COMPLETED 시). */
  private Long academyId;

  @Column(nullable = false, length = 20)
  private String status = STATUS_PROCESSING;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  public AcademyAiIdempotency(String tutorId, String idempotencyKey) {
    this.tutorId = tutorId;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void markCompleted(Long academyId) {
    this.academyId = academyId;
    this.status = STATUS_COMPLETED;
    this.updatedAt = LocalDateTime.now();
  }

  public void markFailed() {
    this.status = STATUS_FAILED;
    this.updatedAt = LocalDateTime.now();
  }

  public boolean isCompleted() {
    return STATUS_COMPLETED.equals(status);
  }

  public boolean isProcessing() {
    return STATUS_PROCESSING.equals(status);
  }

  public boolean isFailed() {
    return STATUS_FAILED.equals(status);
  }
}
