package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/** JDBC owns state transitions under a row lock; this entity documents the schema contract. */
@Entity
@Immutable
@Getter
@Table(name = "ai_collaboration_tasks")
public class CollaborationTask {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "project_id", length = 64, nullable = false)
  private String projectId;

  @Column(name = "workspace_id", length = 64)
  private String workspaceId;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(length = 150, nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String instruction;

  @Column(length = 100, nullable = false)
  private String participants;

  @Column(length = 24, nullable = false)
  private String status;

  @Column(name = "phase_index", nullable = false)
  private int phaseIndex;

  @Column(name = "max_calls", nullable = false)
  private int maxCalls;

  @Column(name = "calls_used", nullable = false)
  private int callsUsed;

  @Column(name = "budget_microusd", nullable = false)
  private long budgetMicrousd;

  @Column(name = "reserved_microusd", nullable = false)
  private long reservedMicrousd;

  @Column(nullable = false)
  private long version;

  @Column(name = "active_run", length = 36)
  private String activeRun;

  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private java.time.Instant updatedAt;
}
