package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_generator_materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemGeneratorMaterial extends BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "material_id")
  private Long materialId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private User uploadedBy;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_type", nullable = false, length = 10)
  private String fileType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MaterialVisibility visibility;

  @Builder
  public ProblemGeneratorMaterial(User uploadedBy, String title, String originalFileName,
      String storageKey, String fileType, long fileSize, MaterialVisibility visibility) {
    this.uploadedBy = uploadedBy;
    this.title = title;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileType = fileType;
    this.fileSize = fileSize;
    this.visibility = visibility != null ? visibility : MaterialVisibility.PRIVATE;
  }

  public boolean isPublic() { return visibility == MaterialVisibility.PUBLIC; }
  public boolean isOwnedBy(Long userId) {
    return uploadedBy != null && uploadedBy.getUserId() != null && uploadedBy.getUserId().equals(userId);
  }
  public void updateVisibility(MaterialVisibility visibility) {
    this.visibility = visibility != null ? visibility : MaterialVisibility.PRIVATE;
  }
}
