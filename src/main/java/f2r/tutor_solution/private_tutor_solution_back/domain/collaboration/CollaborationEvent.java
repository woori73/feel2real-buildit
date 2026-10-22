package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ai_collaboration_events")
public class CollaborationEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "task_id", length = 36, nullable = false)
  private String taskId;

  @Column(length = 20, nullable = false)
  private String actor;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "event_type", length = 32, nullable = false)
  private String eventType;

  @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
  private String body;

  @Column(name = "run_id", length = 36)
  private String runId;

  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt;
}
