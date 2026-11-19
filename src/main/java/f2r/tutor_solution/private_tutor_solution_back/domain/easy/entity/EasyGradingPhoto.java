package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 채점 답지 사진 한 장. 파일은 FileStorage에, 여기에는 저장 키만. */
@Entity
@Table(name = "easy_grading_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EasyGradingPhoto extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "photo_id")
  private Long photoId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "grading_id", nullable = false)
  private EasyGradingRecord grading;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "content_type", nullable = false, length = 40)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  public EasyGradingPhoto(EasyGradingRecord grading, String storageKey, String contentType, long sizeBytes) {
    this.grading = grading;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
  }
}
