package f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem_generator_set_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemGeneratorSetQuestion extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "set_question_id")
  private Long setQuestionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "set_id", nullable = false)
  private ProblemGeneratorQuestionSet set;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(name = "order_num", nullable = false)
  private Integer orderNum = 1;

  @Builder
  public ProblemGeneratorSetQuestion(ProblemGeneratorQuestionSet set, Question question, Integer orderNum) {
    this.set = set;
    this.question = question;
    this.orderNum = orderNum != null ? orderNum : 1;
  }
}
