package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 THREAD 에 올린 변형/예상 문제의 구조화 상세. {@link QuestionThreadPost} 와 1:1.
 * 향후 우수 변형문제를 문제 DB / WORKBOOK / Educator Brain 으로 승격할 수 있도록 필드를 나눠 저장한다.
 */
@Entity
@Table(name = "question_thread_variant_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionThreadVariantDetail extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "variant_id")
  private Long variantId;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id", nullable = false, unique = true)
  private QuestionThreadPost post;

  @Lob
  @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
  private String questionText;

  @Column(name = "question_type", length = 40)
  private String questionType;

  @Lob
  @Column(name = "answer", columnDefinition = "TEXT")
  private String answer;

  @Lob
  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Lob
  @Column(name = "source_sentence", columnDefinition = "TEXT")
  private String sourceSentence;

  @Column(name = "source_question", length = 200)
  private String sourceQuestion;

  @Column(name = "difficulty", length = 20)
  private String difficulty;

  @Builder
  public QuestionThreadVariantDetail(QuestionThreadPost post, String questionText, String questionType,
      String answer, String explanation, String sourceSentence, String sourceQuestion, String difficulty) {
    this.post = post;
    this.questionText = questionText;
    this.questionType = questionType;
    this.answer = answer;
    this.explanation = explanation;
    this.sourceSentence = sourceSentence;
    this.sourceQuestion = sourceQuestion;
    this.difficulty = difficulty;
  }

  public void update(String questionText, String questionType, String answer, String explanation,
      String sourceSentence, String sourceQuestion, String difficulty) {
    this.questionText = questionText;
    this.questionType = questionType;
    this.answer = answer;
    this.explanation = explanation;
    this.sourceSentence = sourceSentence;
    this.sourceQuestion = sourceQuestion;
    this.difficulty = difficulty;
  }
}
