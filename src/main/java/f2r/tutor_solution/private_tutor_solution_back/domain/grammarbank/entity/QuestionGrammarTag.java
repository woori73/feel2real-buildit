package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** 문제-문법 유형 다중 태그. */
@Entity
@Table(name = "question_grammar_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionGrammarTag extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", nullable = false)
  private GrammarTag tag;

  @Column(name = "is_primary", nullable = false)
  private boolean primary = false;

  @Column(name = "is_auto", nullable = false)
  private boolean auto = true;

  @Column(precision = 3, scale = 2)
  private BigDecimal confidence;

  @Builder
  public QuestionGrammarTag(Question question, GrammarTag tag, boolean primary, boolean auto,
      BigDecimal confidence) {
    this.question = question;
    this.tag = tag;
    this.primary = primary;
    this.auto = auto;
    this.confidence = confidence;
  }
}
