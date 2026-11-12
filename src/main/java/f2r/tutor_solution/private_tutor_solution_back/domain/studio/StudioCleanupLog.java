package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** 폐지 자료 보관·되살림·영구 삭제 기록(append-only). 버전 행을 지운 뒤에도 남는다. */
@Entity
@Table(name = "studio_cleanup_log")
@Immutable
public class StudioCleanupLog {
  @Id private Long id;

  @Column(name = "project_id", length = 64, nullable = false)
  private String projectId;

  @Column(length = 16, nullable = false)
  private String action;

  @Column(name = "revision_id", length = 36, nullable = false)
  private String revisionId;

  @Column(name = "item_key", length = 100, nullable = false)
  private String itemKey;

  @Column(length = 40, nullable = false)
  private String label;

  @Column(name = "file_id", length = 36)
  private String fileId;

  @Column(name = "bytes_freed", nullable = false)
  private Long bytesFreed;

  @Column(length = 300, nullable = false)
  private String reason;

  @Column(length = 500, nullable = false)
  private String note;

  @Column(name = "actor_id", nullable = false)
  private Long actorId;

  protected StudioCleanupLog() {}
}
