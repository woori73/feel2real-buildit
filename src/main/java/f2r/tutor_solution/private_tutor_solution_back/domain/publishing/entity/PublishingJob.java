package f2r.tutor_solution.private_tutor_solution_back.domain.publishing.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DesignCraft 출판 작업.
 *
 * <p>1개 작업 = 1개 {@code .designcraft} 정본. Publishing Worker가
 * {@code QUEUED} 작업을 폴링해 designcraft-cli로 처리한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "publishing_jobs")
public class PublishingJob extends BaseEntity {

  public enum Status {
    QUEUED, RUNNING, DONE, FAILED, CANCELLED
  }

  public enum Template {
    KO_TEXTBOOK_A4("ko-textbook-a4"),
    KO_WORKBOOK_B5("ko-workbook-b5"),
    KO_BOOK_46("ko-book-46");

    private final String code;

    Template(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    public static Template fromCode(String code) {
      for (Template t : values()) {
        if (t.code.equals(code)) {
          return t;
        }
      }
      throw new IllegalArgumentException("Unknown template: " + code);
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 30)
  private String template;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status = Status.QUEUED;

  @Column(nullable = false)
  private int progress = 0;

  @Column(name = "current_step", length = 100)
  private String currentStep;

  @Column(name = "work_dir", length = 500)
  private String workDir;

  @Column(name = "designcraft_file", length = 500)
  private String designcraftFile;

  @Column(columnDefinition = "TEXT")
  private String error;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount = 0;

  public PublishingJob(Long userId, String title, Template template) {
    this.userId = userId;
    this.title = title;
    this.template = template.getCode();
  }

  public void incrementAttempt() {
    this.attemptCount++;
  }

  public void markRunning(String workDir, String designcraftFile) {
    this.status = Status.RUNNING;
    this.workDir = workDir;
    this.designcraftFile = designcraftFile;
    this.progress = 0;
    this.error = null;
  }

  public void updateProgress(int progress, String currentStep) {
    this.progress = Math.max(0, Math.min(100, progress));
    this.currentStep = currentStep;
  }

  public void markDone() {
    this.status = Status.DONE;
    this.progress = 100;
    this.currentStep = null;
    this.error = null;
  }

  public void markFailed(String error) {
    this.status = Status.FAILED;
    this.error = error;
  }

  public void markCancelled() {
    this.status = Status.CANCELLED;
  }

  public void rename(String title) {
    this.title = title;
  }
}
