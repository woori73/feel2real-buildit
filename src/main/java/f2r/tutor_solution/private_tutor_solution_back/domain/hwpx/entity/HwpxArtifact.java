package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * HWPX 산출물 (결과 HWPX 파일).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "hwpx_artifacts")
public class HwpxArtifact {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id", nullable = false)
  private Long jobId;

  @Column(nullable = false, length = 10)
  private String format = "hwpx";

  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public HwpxArtifact(Long jobId, String filePath, Long fileSize) {
    this.jobId = jobId;
    this.filePath = filePath;
    this.fileSize = fileSize;
    this.createdAt = LocalDateTime.now();
  }
}
