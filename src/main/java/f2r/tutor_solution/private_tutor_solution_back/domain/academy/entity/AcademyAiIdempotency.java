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

  /**
   * PROCESSING 만료 시간 (분).
   * 이 시간을 초과한 PROCESSING 레코드는 고착된 것으로 간주하고 재시도를 허용한다.
   */
  public static final long PROCESSING_TIMEOUT_MINUTES = 30;

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

  /**
   * PROCESSING 상태가 만료되었는지 확인한다.
   * 만료된 PROCESSING은 영구 고착이 아니라 재시도 가능한 상태로 간주한다.
   */
  public boolean isProcessingExpired() {
    if (!isProcessing()) {
      return false;
    }
    return updatedAt.isBefore(
        LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES));
  }
}
