package f2r.tutor_solution.private_tutor_solution_back.domain.question.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "question_id")
  private Long questionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subject_id", nullable = false)
  private Subject subject;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private QuestionCategory category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "passage_id")
  private Passage passage;

  @Column(name = "exam_id")
  private Long examId;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false, length = 20)
  private QuestionType questionType;

  @Lob
  @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
  private String questionText;

  @Column(name = "choices", columnDefinition = "JSON")
  private String choices;

  @Column(name = "correct_answer", length = 500)
  private String correctAnswer;

  @Lob
  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private QuestionSource source;

  @Column(name = "order_num", nullable = false)
  private Integer orderNum = 1;

  /**
   * 시험지에 적힌 그대로의 문항 라벨 (예: "1", "24", "[서답형 1] (1)").
   * 정렬은 {@link #orderNum}이 담당하고, 본 컬럼은 검증 페이지/UI 표시용이다.
   * LLM이 라벨을 못 뽑은 경우 NULL.
   */
  @Column(name = "question_label", length = 50)
  private String questionLabel;

  /**
   * 문항 배점. LLM이 시험지에서 추출하고 학생 검증 단계에서 수정 가능하다.
   * 0이면 "배점 미명시 문제" — 점수 합산에서 제외하고 정답률만 노출한다.
   */
  @Column(name = "point", nullable = false)
  private Integer point = 0;

  @Builder
  public Question(Subject subject, QuestionCategory category, Passage passage,
      Long examId,
      QuestionType questionType, String questionText, String choices,
      String correctAnswer, String explanation,
      QuestionSource source, Integer orderNum, String questionLabel, Integer point) {
    this.subject = subject;
    this.category = category;
    this.passage = passage;
    this.examId = examId;
    this.questionType = questionType != null ? questionType : QuestionType.SHORT_ANSWER;
    this.questionText = questionText;
    this.choices = choices;
    this.correctAnswer = correctAnswer;
    this.explanation = explanation;
    this.source = source != null ? source : QuestionSource.READFILE;
    this.orderNum = orderNum != null ? orderNum : 1;
    this.questionLabel = questionLabel;
    this.point = point != null ? point : 0;
  }

  public void updateContent(String questionText, String correctAnswer, String explanation) {
    this.questionText = questionText;
    this.correctAnswer = correctAnswer;
    this.explanation = explanation;
  }

  public void updateChoices(String choices) {
    this.choices = choices;
  }

  public void updatePassage(Passage passage) {
    this.passage = passage;
  }

  public void updateProblemGeneratorFields(QuestionType questionType, String questionText, String choices,
      String correctAnswer, String explanation, String questionLabel, Integer point) {
    this.questionType = questionType != null ? questionType : QuestionType.SHORT_ANSWER;
    this.questionText = questionText;
    this.choices = choices;
    this.correctAnswer = correctAnswer;
    this.explanation = explanation;
    this.questionLabel = questionLabel;
    this.point = (point == null || point < 0) ? 0 : point;
  }

  /** 학생 검증 단계에서 배점 보정. 음수는 0으로 클램프. */
  public void updatePoint(Integer point) {
    this.point = (point == null || point < 0) ? 0 : point;
  }
}
