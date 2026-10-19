package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import jakarta.persistence.*;
import lombok.*;

/** AI가 수정한 문항 — 원본과 별도 저장, 승인 상태 관리. */
@Entity
@Table(name = "grammar_question_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrammarQuestionVariant extends BaseEntity {

  public enum VariantStatus { PENDING, APPROVED, REJECTED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "variant_id")
  private Long variantId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "base_question_id", nullable = false)
  private Question baseQuestion;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_question_id", nullable = false, unique = true)
  private Question variantQuestion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private VariantStatus status = VariantStatus.PENDING;

  @Column(name = "change_reason", length = 500)
  private String changeReason;

  @Column(name = "review_note", length = 500)
  private String reviewNote;

  @Builder
  public GrammarQuestionVariant(Question baseQuestion, Question variantQuestion,
      String changeReason) {
    this.baseQuestion = baseQuestion;
    this.variantQuestion = variantQuestion;
    this.changeReason = changeReason;
  }

  public void review(VariantStatus status, String reviewNote) {
    this.status = status;
    this.reviewNote = reviewNote;
  }
}
