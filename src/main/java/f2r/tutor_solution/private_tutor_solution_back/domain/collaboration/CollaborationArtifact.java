package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ai_collaboration_artifacts")
public class CollaborationArtifact {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "task_id", length = 36, nullable = false)
  private String taskId;

  @Column(name = "file_id", length = 36)
  private String fileId;

  @Column(name = "revision_id", length = 36)
  private String revisionId;

  @Column(name = "pr_number")
  private Integer prNumber;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt;
}
