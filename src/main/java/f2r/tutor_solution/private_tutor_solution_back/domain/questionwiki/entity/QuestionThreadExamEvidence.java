package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제 학교 내신에서 이 지문/문장이 어떻게 출제됐는지에 대한 기출 증거. {@link QuestionThreadPost} 와 1:1.
 * 예상(GENERATED_PRACTICE)과 절대 같은 데이터로 취급하지 않는다.
 */
@Entity
@Table(name = "question_thread_exam_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionThreadExamEvidence extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "evidence_id")
  private Long evidenceId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id", nullable = false, unique = true)
  private QuestionThreadPost post;

  @Column(name = "school_name", length = 100)
  private String schoolName;

  @Column(name = "grade_label", length = 20)
  private String gradeLabel;

  /** 예: "2026 2학기 중간". */
  @Column(name = "exam_period", length = 60)
  private String examPeriod;

  @Lob
  @Column(name = "related_sentence", columnDefinition = "TEXT")
  private String relatedSentence;

  @Column(name = "exam_question_type", length = 40)
  private String examQuestionType;

  @Lob
  @Column(name = "exam_point", columnDefinition = "TEXT")
  private String examPoint;

  @Lob
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  /** 향후 exams 테이블 연결(선택). 지금은 free-text 필드만 사용. */
  @Column(name = "exam_id")
  private Long examId;

  @Builder
  public QuestionThreadExamEvidence(QuestionThreadPost post, String schoolName, String gradeLabel,
      String examPeriod, String relatedSentence, String examQuestionType, String examPoint,
      String description, Long examId) {
    this.post = post;
    this.schoolName = schoolName;
    this.gradeLabel = gradeLabel;
    this.examPeriod = examPeriod;
    this.relatedSentence = relatedSentence;
    this.examQuestionType = examQuestionType;
    this.examPoint = examPoint;
    this.description = description;
    this.examId = examId;
  }

  public void update(String schoolName, String gradeLabel, String examPeriod, String relatedSentence,
      String examQuestionType, String examPoint, String description) {
    this.schoolName = schoolName;
    this.gradeLabel = gradeLabel;
    this.examPeriod = examPeriod;
    this.relatedSentence = relatedSentence;
    this.examQuestionType = examQuestionType;
    this.examPoint = examPoint;
    this.description = description;
  }
}
