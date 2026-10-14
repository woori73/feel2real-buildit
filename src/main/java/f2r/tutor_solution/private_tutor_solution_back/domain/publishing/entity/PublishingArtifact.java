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
 * 출판 작업 산출물 (PDF/EPUB/PNG).
 *
 * <p>파일은 {@code PublishingProperties.storageRoot} 하위에 저장되며
 * DB에는 메타데이터만 보관한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "publishing_artifacts")
public class PublishingArtifact extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id", nullable = false)
  private Long jobId;

  /** pdf | epub | png */
  @Column(nullable = false, length = 10)
  private String format;

  /** x4 | a2b | none (pdf만) */
  @Column(length = 10)
  private String standard;

  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "page_count")
  private Integer pageCount;

  public PublishingArtifact(Long jobId, String format, String standard,
      String filePath, Long fileSize, Integer pageCount) {
    this.jobId = jobId;
    this.format = format;
    this.standard = standard;
    this.filePath = filePath;
    this.fileSize = fileSize;
    this.pageCount = pageCount;
  }
}
