package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

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

/** 작업 상태 변경 감사 기록. 지우거나 고치지 않는다. */
@Entity
@Table(name = "coord_job_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoordJobEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  private Long eventId;

  @Column(name = "job_id", nullable = false, length = 36)
  private String jobId;

  @Column(name = "at", nullable = false)
  private LocalDateTime at;

  @Column(name = "actor", nullable = false, length = 20)
  private String actor;

  @Column(name = "actor_login_id", length = 50)
  private String actorLoginId;

  @Column(name = "from_status", length = 20)
  private String fromStatus;

  @Column(name = "to_status", nullable = false, length = 20)
  private String toStatus;

  @Column(name = "message", length = 500)
  private String message;

  CoordJobEvent(String jobId, LocalDateTime at, String actor, String actorLoginId, String fromStatus,
      String toStatus, String message) {
    this.jobId = jobId;
    this.at = at;
    this.actor = actor;
    this.actorLoginId = actorLoginId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.message = message == null ? null : message.length() > 500 ? message.substring(0, 500) : message;
  }
}
