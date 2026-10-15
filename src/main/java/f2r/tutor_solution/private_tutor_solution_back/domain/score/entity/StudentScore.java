package f2r.tutor_solution.private_tutor_solution_back.domain.score.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.Exam;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MVP §5.0 6단계 — 학생 시험지 확정 시 INSERT되는 점수 행.
 * (exam_id, source_id) 조합으로 학생이 어느 raw 파일로 어느 시험을 풀었는지 추적.
 */
@Entity
@Table(name = "student_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentScore extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "score_id")
  private Long scoreId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", nullable = false)
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exam_id", nullable = false)
  private Exam exam;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", nullable = false)
  private Source source;

  @Column(name = "total_questions", nullable = false)
  private Integer totalQuestions;

  @Column(name = "correct_count", nullable = false)
  private Integer correctCount;

  /**
   * 배점 합계. 신규 시험지부터 채워진다.
   * NULL이면 구 데이터 → 정답률(correctCount/totalQuestions)로 폴백 표시.
   * 0이면 모든 문제 배점이 0(=배점 미명시 시험)이라 점수가 없음.
   */
  @Column(name = "total_points")
  private Integer totalPoints;

  @Column(name = "earned_points")
  private Integer earnedPoints;

  @Column(name = "confirmed_at", nullable = false)
  private LocalDateTime confirmedAt;

  @Builder
  public StudentScore(Student student, Exam exam, Source source,
      Integer totalQuestions, Integer correctCount,
      Integer totalPoints, Integer earnedPoints) {
    this.student = student;
    this.exam = exam;
    this.source = source;
    this.totalQuestions = totalQuestions;
    this.correctCount = correctCount;
    this.totalPoints = totalPoints;
    this.earnedPoints = earnedPoints;
    this.confirmedAt = LocalDateTime.now();
  }
}
