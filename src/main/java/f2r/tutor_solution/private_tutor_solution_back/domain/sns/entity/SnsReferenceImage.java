package f2r.tutor_solution.private_tutor_solution_back.domain.sns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 이미지 생성 때 얼굴·분위기를 맞추는 참고 이미지. file_path는 "classpath:..."(저장소에 포함된 승인 이미지)
 * 또는 FileStorage 저장 키다. 승인(approved)된 이미지만 생성에 쓴다.
 */
@Entity
@Table(name = "sns_reference_images")
@Getter
@Setter
@NoArgsConstructor
public class SnsReferenceImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "label", nullable = false, length = 100)
  private String label;

  @Column(name = "brand", length = 100)
  private String brand;

  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @Column(name = "content_type", nullable = false, length = 50)
  private String contentType;

  @Column(name = "approved", nullable = false)
  private boolean approved;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = SnsTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = SnsTime.now();
  }
}
