package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "newstart_official_discovery_run", indexes = {
    @Index(name = "idx_discovery_run_source_time", columnList = "source_type, run_started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewStartOfficialDiscoveryRun {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "run_id")
  private Long runId;

  @Column(name = "run_started_at", nullable = false)
  private LocalDateTime runStartedAt;

  @Column(name = "run_finished_at")
  private LocalDateTime runFinishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private NewStartOfficialUpdate.SourceType sourceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RunStatus status;

  @Column(name = "discovered_count", nullable = false)
  private int discoveredCount = 0;

  @Column(name = "new_review_count", nullable = false)
  private int newReviewCount = 0;

  @Column(name = "duplicate_count", nullable = false)
  private int duplicateCount = 0;

  @Column(name = "irrelevant_count", nullable = false)
  private int irrelevantCount = 0;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  public NewStartOfficialDiscoveryRun(NewStartOfficialUpdate.SourceType sourceType) {
    this.sourceType = sourceType;
    this.runStartedAt = LocalDateTime.now();
    this.status = RunStatus.RUNNING;
  }

  public void complete(int discovered, int newReviews, int duplicates, int irrelevant) {
    this.runFinishedAt = LocalDateTime.now();
    this.status = RunStatus.COMPLETED;
    this.discoveredCount = discovered;
    this.newReviewCount = newReviews;
    this.duplicateCount = duplicates;
    this.irrelevantCount = irrelevant;
  }

  public void fail(String errorMessage) {
    this.runFinishedAt = LocalDateTime.now();
    this.status = RunStatus.FAILED;
    this.errorMessage = errorMessage;
  }

  /** 실패한 run 이라도 부분 성공 카운트를 관측 가능하도록 남긴다. */
  public void setCounts(int discovered, int newReviews, int duplicates, int irrelevant) {
    this.discoveredCount = discovered;
    this.newReviewCount = newReviews;
    this.duplicateCount = duplicates;
    this.irrelevantCount = irrelevant;
  }

  public enum RunStatus {
    RUNNING,
    COMPLETED,
    FAILED
  }
}