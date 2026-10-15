package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "learning_material_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningMaterialFeedback extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "feedback_id")
  private Long feedbackId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  private LearningMaterial material;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id")
  private Source source;

  @Lob
  @Column(name = "feedback_text", columnDefinition = "TEXT")
  private String feedbackText;

  @Column(name = "order_num", nullable = false)
  private Integer orderNum;

  @Builder
  public LearningMaterialFeedback(LearningMaterial material, Source source, String feedbackText, Integer orderNum) {
    this.material = material;
    this.source = source;
    this.feedbackText = feedbackText;
    this.orderNum = orderNum;
  }
}
