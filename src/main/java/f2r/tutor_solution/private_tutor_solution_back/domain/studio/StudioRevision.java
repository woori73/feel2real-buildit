package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable ORM schema contract. Commands append with JDBC under a project lock; no entity setters.
 */
@Entity
@Table(name = "studio_revisions")
@Immutable
public class StudioRevision {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "project_id", length = 64, nullable = false)
  private String projectId;

  @Column(name = "item_key", length = 100, nullable = false)
  private String itemKey;

  @Column(name = "parent_id", length = 36)
  private String parentId;

  @Column(length = 40, nullable = false)
  private String label;

  @Column(length = 16, nullable = false)
  private String kind;

  private Integer episode;

  @Column(name = "cut_number")
  private Integer cutNumber;

  @Column(length = 16, nullable = false)
  private String origin;

  @Column(length = 150, nullable = false)
  private String title;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(columnDefinition = "mediumtext", nullable = false)
  private String content;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(columnDefinition = "text", nullable = false)
  private String dialogue;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(columnDefinition = "text", nullable = false)
  private String direction;

  @Column(name = "work_status", length = 20, nullable = false)
  private String workStatus;

  @Column(name = "file_id", length = 36)
  private String fileId;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  protected StudioRevision() {}
}
