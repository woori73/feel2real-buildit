package f2r.tutor_solution.private_tutor_solution_back.domain.hwpx.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * HWPX 편집 작업.
 *
 * <p>1개 작업 = 1개 HWP/HWPX 파일의 표 셀 수정. HwpxWorker가
 * {@code QUEUED} 작업을 폴링해 Python CLI(hwpx_cli.py)로 처리한다.
 *
 * <p>기존 publishing 패키지와 독립적이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "hwpx_jobs")
public class HwpxJob {

  public enum Status {
    QUEUED, RUNNING, DONE, FAILED, CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "source_format", nullable = false, length = 10)
  private String sourceFormat; // hwp | hwpx

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status = Status.QUEUED;

  @Column(nullable = false)
  private int progress = 0;

  @Column(name = "current_step", length = 100)
  private String currentStep;

  @Column(name = "work_dir", length = 500)
  private String workDir;

  @Column(name = "input_file", length = 500)
  private String inputFile;

  @Column(name = "output_file", length = 500)
  private String outputFile;

  @Column(name = "edits_json", length = 2000)
  private String editsJson;

  @Column(columnDefinition = "TEXT")
  private String error;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public HwpxJob(Long tutorId, String title, String sourceFormat, String editsJson) {
    this.tutorId = tutorId;
    this.title = title;
    this.sourceFormat = sourceFormat;
    this.editsJson = editsJson;
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void markRunning(String workDir, String inputFile) {
    this.status = Status.RUNNING;
    this.workDir = workDir;
    this.inputFile = inputFile;
    this.progress = 5;
    this.currentStep = "작업 시작";
    this.updatedAt = LocalDateTime.now();
  }

  public void updateProgress(int progress, String step) {
    this.progress = progress;
    this.currentStep = step;
    this.updatedAt = LocalDateTime.now();
  }

  public void markDone(String outputFile) {
    this.status = Status.DONE;
    this.outputFile = outputFile;
    this.progress = 100;
    this.currentStep = "완료";
    this.updatedAt = LocalDateTime.now();
  }

  public void markFailed(String error) {
    this.status = Status.FAILED;
    this.error = error;
    this.updatedAt = LocalDateTime.now();
  }

  public void markCancelled() {
    this.status = Status.CANCELLED;
    this.updatedAt = LocalDateTime.now();
  }
}
