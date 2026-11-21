package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재원생 앱 응시 기록(V1126 student_exam_results). 제출 한 번에 한 줄, 삭제 기능 없음. checkedAt이 비면 '새 응시 기록'. */
@Entity
@Table(name = "student_exam_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentExamResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "result_id")
  private Long resultId;

  @Column(name = "business_id", nullable = false)
  private Long businessId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "attempt_id", nullable = false)
  private Long attemptId;

  @Column(name = "grading_id")
  private Long gradingId;

  @Column(name = "exam_code", nullable = false, length = 64)
  private String examCode;

  @Column(name = "exam_title", nullable = false, length = 200)
  private String examTitle;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "submitted_at", nullable = false)
  private LocalDateTime submittedAt;

  @Column(name = "duration_sec", nullable = false)
  private Integer durationSec;

  @Column(name = "score")
  private Integer score;

  @Column(name = "max_score")
  private Integer maxScore;

  @Column(name = "total_count", nullable = false)
  private Integer totalCount;

  @Column(name = "correct_count", nullable = false)
  private Integer correctCount;

  @Column(name = "wrong_items", columnDefinition = "TEXT")
  private String wrongItems;

  @Column(name = "checked_by")
  private Long checkedBy;

  @Column(name = "checked_at")
  private LocalDateTime checkedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private StudentExamResult(Long businessId, Long studentId, Long attemptId, Long gradingId, String examCode,
      String examTitle, Integer attemptNo, LocalDateTime startedAt, LocalDateTime submittedAt, Integer durationSec,
      Integer score, Integer maxScore, Integer totalCount, Integer correctCount, String wrongItems, LocalDateTime createdAt) {
    this.businessId = businessId;
    this.studentId = studentId;
    this.attemptId = attemptId;
    this.gradingId = gradingId;
    this.examCode = examCode;
    this.examTitle = examTitle;
    this.attemptNo = attemptNo;
    this.startedAt = startedAt;
    this.submittedAt = submittedAt;
    this.durationSec = durationSec;
    this.score = score;
    this.maxScore = maxScore;
    this.totalCount = totalCount;
    this.correctCount = correctCount;
    this.wrongItems = wrongItems;
    this.createdAt = createdAt;
  }

  public void check(Long by, LocalDateTime now) {
    if (checkedAt != null) return;
    this.checkedBy = by;
    this.checkedAt = now;
  }
}
