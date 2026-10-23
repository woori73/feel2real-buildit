package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Original blob metadata schema contract; storage keys are deliberately not serializable DTO
 * properties.
 */
@Entity
@Table(name = "studio_files")
@Immutable
public class StudioFile {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "project_id", length = 64, nullable = false)
  private String projectId;

  @Column(length = 64, nullable = false, columnDefinition = "char(64)")
  private String sha256;

  @Column(name = "storage_key", length = 1024, nullable = false)
  private String storageKey;

  @Column(length = 255, nullable = false)
  private String filename;

  @Column(length = 8, nullable = false)
  private String extension;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes;

  @Column(name = "uploaded_by", nullable = false)
  private Long uploadedBy;

  protected StudioFile() {}
}
