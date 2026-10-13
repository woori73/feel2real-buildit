package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출판 작업 스냅샷 (버전).
 *
 * <p>명령 적용·AI 수정 적용 전에 현재 {@code .designcraft} 정본을 복사해 보관한다.
 * 복원은 스냅샷 파일을 작업 파일에 덮어쓰는 방식이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "publishing_versions")
public class PublishingVersion extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id", nullable = false)
  private Long jobId;

  @Column(name = "designcraft_file", nullable = false, length = 500)
  private String designcraftFile;

  @Column(length = 200)
  private String label;

  public PublishingVersion(Long jobId, String designcraftFile, String label) {
    this.jobId = jobId;
    this.designcraftFile = designcraftFile;
    this.label = label;
  }
}
