package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 제작 작업 하나의 디지털 모의고사 시험 묶음(V1139 coord_factory_exam_packages). 작업마다 하나, 다시 제출하면 바뀐다. */
@Entity
@Table(name = "coord_factory_exam_packages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FactoryExamPackage {

  @Id
  @Column(name = "job_id", length = 36)
  private String jobId;

  @Column(name = "track", nullable = false, length = 10)
  private String track;

  @Column(name = "exam_code", nullable = false, length = 40)
  private String examCode;

  @Column(name = "package_json", nullable = false, columnDefinition = "MEDIUMTEXT")
  private String packageJson;

  @Column(name = "package_hash", nullable = false, length = 64)
  private String packageHash;

  @Column(name = "question_count", nullable = false)
  private int questionCount;

  @Column(name = "submitted_by", nullable = false, length = 100)
  private String submittedBy;

  @Column(name = "submitted_at", nullable = false)
  private LocalDateTime submittedAt;

  @Column(name = "exam_id")
  private Long examId;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  public FactoryExamPackage(String jobId) {
    this.jobId = jobId;
  }

  /** 새로 제출(또는 다시 제출). 게시된 뒤에는 서비스가 막는다. */
  void submit(String track, String examCode, String packageJson, String packageHash, int questionCount, String by,
      LocalDateTime now) {
    this.track = track;
    this.examCode = examCode;
    this.packageJson = packageJson;
    this.packageHash = packageHash;
    this.questionCount = questionCount;
    this.submittedBy = by;
    this.submittedAt = now;
  }

  void published(Long examId, LocalDateTime now) {
    this.examId = examId;
    this.publishedAt = now;
  }

  boolean isPublished() {
    return examId != null;
  }
}
