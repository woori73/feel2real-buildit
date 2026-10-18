package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "grammar_collection_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrammarCollectionItem extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id")
  private Long itemId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "collection_id", nullable = false)
  private GrammarCollection collection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(name = "order_num", nullable = false)
  private Integer orderNum = 1;

  @Builder
  public GrammarCollectionItem(GrammarCollection collection, Question question, Integer orderNum) {
    this.collection = collection;
    this.question = question;
    this.orderNum = orderNum != null ? orderNum : 1;
  }

  public void updateOrderNum(Integer orderNum) {
    this.orderNum = orderNum;
  }
}
