package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 요청 상태 이동 이력 ({@code newstart_request_status_logs}) — 첨부·삭제가 아닌 검토 기록.
 *
 * <p>첫 접수(from=null)부터 현재까지의 흐름을 보여 운영자가 "어디까지 검토됐는지"를 게시판에서
 * 직접 판단할 수 있게 한다. 거절·중복 사유(reason)는 회원에게 공개되는 문구라는 전제로 남긴다.
 */
@Entity
@Table(name = "newstart_request_status_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewStartRequestStatusLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "log_id")
  private Long logId;

  @Column(name = "request_id", nullable = false)
  private Long requestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private NewStartRequestStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 20)
  private NewStartRequestStatus toStatus;

  @Column(nullable = false, length = 50)
  private String actor;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public NewStartRequestStatusLog(Long requestId, NewStartRequestStatus fromStatus,
      NewStartRequestStatus toStatus, String actor, String reason) {
    this.requestId = requestId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.actor = actor;
    this.reason = reason;
  }
}
