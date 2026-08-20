package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learning_material_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningMaterialAnswer extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "answer_id")
  private Long answerId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  private LearningMaterial material;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", nullable = false)
  private Source source;

  @Column(name = "order_num", nullable = false)
  private Integer orderNum;

  @Builder
  public LearningMaterialAnswer(LearningMaterial material, Source source, Integer orderNum) {
    this.material = material;
    this.source = source;
    this.orderNum = orderNum;
  }
}
